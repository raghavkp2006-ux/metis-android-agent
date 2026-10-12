"""Integrity, fitting isolation, confidence/export and fixed safety-boundary tests."""
import base64
import copy
import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np

from v11_data import HERE, ROOT, LABELS, PARTITIONS, SLOTS, authored_rows, canonical, digest, earlier_texts, validate
from v11_model import PROTOCOL, fit, calibrate, select, predict, metrics, cue_rows, export, exported_predict, apply_thresholds
from v11_integrity import check_manifest
from v11_evaluate import output_path, resolve, section, check_reproduction


class ClassifierV11Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads((HERE / 'development-data.json').read_text(encoding='utf-8'))
        cls.rows=cls.data['rows']
        cls.train=[r for r in cls.rows if r['split']=='train']
        cls.calibration=[r for r in cls.rows if r['split']=='calibration']
        cls.selection=[r for r in cls.rows if r['split']=='selection']
        cls.held=[r for r in cls.rows if r['split']=='test']
        cls.sample=[r for label in LABELS for r in [r for r in cls.train if r['intent']==label][:20]]
        cls.model=fit(cls.sample,dict(c=1.0,cues=True))
        cls.settings=dict(temperature=.8,thresholds={label:.90 for label in LABELS},mode='global',abstain=False)

    def test_authored_families_projections_and_current_historical_integrity(self):
        check_manifest()
        self.assertEqual(digest(HERE / 'protocol.json'),self.data['protocol_sha256'])
        earlier,seen,rows,excluded=earlier_texts(),set(),[],[]
        for row in authored_rows():
            text=canonical(row['utterance'])
            if text in earlier or text in seen: excluded.append(row['id']); continue
            seen.add(text); rows.append(row)
        self.assertEqual(rows,self.rows)
        self.assertEqual(excluded,self.data['exclusions'])
        validate(rows,earlier)
        self.assertEqual(150,len(self.data['families']))
        self.assertEqual(dict(train=90,calibration=20,selection=20,test=20),
            {s:list(self.data['families'].values()).count(s) for s in PARTITIONS})
        inputs=list(csv.DictReader((HERE / 'development-input.tsv').read_text().splitlines(),delimiter='\t'))
        expected=list(csv.DictReader((HERE / 'development-expectations.tsv').read_text().splitlines(),delimiter='\t'))
        for row,input_row,annotation in zip(rows,inputs,expected,strict=True):
            self.assertEqual(row['utterance'],base64.b64decode(input_row['text_base64']).decode())
            self.assertEqual(row['id'],annotation['id'])
            self.assertEqual(row['intent'],annotation['intent'])
            self.assertEqual(row['disposition'],annotation['disposition'])

    def test_duplicate_exposure_family_slot_span_and_provenance_corruption(self):
        for change in ('duplicate','exposure','family','slot','span','provenance'):
            rows=copy.deepcopy(self.rows)
            row=next(r for r in rows if r['split']=='train' and r['entities'])
            earlier=set()
            if change=='duplicate': rows.append(copy.deepcopy(row))
            elif change=='exposure': earlier.add(canonical(row['utterance']))
            elif change=='family': row['split']='test'
            elif change=='slot': row['utterance']+=' '+SLOTS['test'][0]
            elif change=='span': row['entities'][0]['startOffset']+=1
            else: row['human_authored']=True
            with self.subTest(change=change),self.assertRaises(ValueError): validate(rows,earlier)

    def test_fit_calibration_selection_reject_wrong_partitions_before_fitting(self):
        with self.assertRaisesRegex(ValueError,'only train'): fit(self.held,dict(c=1.0,cues=True))
        with self.assertRaisesRegex(ValueError,'only calibration'): calibrate(self.model,self.train)
        with patch('v11_model.fit') as trainer:
            with self.assertRaisesRegex(ValueError,'only selection'): select(self.train,self.calibration,self.held)
            trainer.assert_not_called()
        contaminated=copy.deepcopy(self.selection)
        contaminated[0]['family_id']=self.train[0]['family_id']
        with patch('v11_model.fit') as trainer:
            with self.assertRaisesRegex(ValueError,'Overlapping families'): select(self.train,self.calibration,contaminated)
            trainer.assert_not_called()
        vocabulary=self.model.named_steps['features'].transformer_list[0][1].vocabulary_
        self.assertNotIn('chronometer',vocabulary)
        self.assertNotIn('herbarium',vocabulary)

    def test_features_use_only_request_never_expected_labels_or_annotations(self):
        altered=copy.deepcopy(self.held[:20])
        for row in altered:
            row.update(intent='UNKNOWN',entities=[{'forged':'annotation'}],query_text='injected',family_id='fake',id='fake')
        before,probabilities=predict(self.model,self.held[:20],self.settings)
        after,values=predict(self.model,altered,self.settings)
        self.assertEqual(before,after)
        np.testing.assert_array_equal(probabilities,values)
        self.assertGreater(cue_rows(['Do not show my tasks'])[0]['outer_negation'],0)
        self.assertEqual(0,cue_rows(['Retrieve the tasks I have not completed'])[0]['outer_negation'])

    def test_numeric_export_parity_unicode_apostrophes_and_tampering(self):
        selected=dict(configuration=dict(c=1.0,cues=True),settings=self.settings)
        payload=export(self.model,selected,{'fixture':'test-only'})
        probes=[dict(utterance=text) for text in ('What did I promise Zoë D’Souza?',
            'Show notes about The Who','Ｓｈｏｗ my tasks','Recall café 🚀 tuning',
            "Don't read my memory",'Remind me of not eating after 9pm')]+self.held
        expected,probabilities=predict(self.model,probes,self.settings)
        restored,values=exported_predict(json.loads(json.dumps(payload)),probes)
        self.assertEqual(expected,restored)
        np.testing.assert_allclose(probabilities,values,rtol=1e-12,atol=1e-12)
        for key in ('ml_enabled','coefficients','bindings'):
            changed=copy.deepcopy(payload)
            if key=='ml_enabled': changed[key]=True
            elif key=='coefficients': changed[key][0][0]+=.01
            else: changed[key]['fixture']='changed'
            with self.assertRaisesRegex(ValueError,'drift'): exported_predict(changed,probes)

    def test_threshold_floor_per_intent_and_abstention(self):
        probabilities=np.array([[.01,.94,.01,.02,.02],[.01,.91,.02,.03,.03]])
        settings=copy.deepcopy(self.settings)
        settings['thresholds']['CHECK_MEMORY']=.92
        self.assertEqual(['CHECK_MEMORY','UNKNOWN'],apply_thresholds(['CHECK_MEMORY']*2,probabilities,settings))
        settings['abstain']=True
        self.assertEqual(['UNKNOWN']*2,apply_thresholds(['CHECK_MEMORY']*2,probabilities,settings))
        settings['thresholds']['CHECK_MEMORY']=.80
        with self.assertRaises(ValueError): apply_thresholds(['CHECK_MEMORY']*2,probabilities,settings)
        settings['temperature']=0
        with self.assertRaises(ValueError): predict(self.model,self.held[:1],settings)

    def test_selection_can_choose_per_intent_cutoffs_without_unknown_accepts(self):
        classes=np.array(LABELS)
        matrix=np.full((5,5),.0025)
        for index in range(4): matrix[index,index]=.99
        matrix[1]=[.0225,.91,.0225,.0225,.0225]
        matrix[4]=[.95,.0125,.0125,.0125,.0125]
        class FixedModel:
            classes_=classes
            def decision_function(self,texts): return np.log(matrix)
        parts={split:[dict(utterance=label,intent=label,split=split,family_id=split+label) for label in LABELS]
               for split in ('train','calibration','selection')}
        with patch('v11_model.fit',return_value=FixedModel()),patch('v11_model.calibrate',return_value=1.0):
            _,selected,_=select(parts['train'],parts['calibration'],parts['selection'])
        self.assertEqual('per_intent',selected['settings']['mode'])
        self.assertEqual(.96,selected['settings']['thresholds']['CHECK_TASKS'])
        self.assertEqual(.90,selected['settings']['thresholds']['CHECK_MEMORY'])
        self.assertEqual(1.0,selected['metrics']['macro_f1'])
        self.assertEqual(0,selected['metrics']['unknown_false_accepts'])

    def test_selection_abstains_if_no_safe_threshold_exists(self):
        class ConfidentWrongModel:
            classes_=np.array(LABELS)
            def decision_function(self,texts): return np.tile([100,0,0,0,0],(len(texts),1))
        parts={split:[dict(utterance=label,intent=label,split=split,family_id=split+label) for label in LABELS]
               for split in ('train','calibration','selection')}
        with patch('v11_model.fit',return_value=ConfidentWrongModel()),patch('v11_model.calibrate',return_value=1.0):
            model,selected,_=select(parts['train'],parts['calibration'],parts['selection'])
        self.assertFalse(selected['eligible'])
        self.assertTrue(selected['settings']['abstain'])
        predictions,_=predict(model,parts['selection'],selected['settings'])
        self.assertEqual(['UNKNOWN']*5,predictions)

    def test_every_prohibited_new_request_rejects_every_read_label(self):
        attempts=0
        for row in self.rows:
            if row['disposition']=='read': continue
            for label in LABELS[:-1]:
                attempts+=1
                plan=resolve(row['utterance'],label,row['input_source'])
                self.assertEqual(0,plan['reads'],row['id'])
                self.assertEqual(row['utterance'],plan['request_text'])
        self.assertEqual(13200,attempts)

    def test_report_output_protection_and_unknown_metric(self):
        with self.assertRaisesRegex(ValueError,'ignored'): output_path(HERE / 'report.json')
        values=metrics([dict(intent=label) for label in LABELS],['UNKNOWN']*len(LABELS))
        self.assertEqual(4,values['known_abstentions'])
        self.assertEqual(0,values['unknown_false_accepts'])
        self.assertEqual(0,values['per_class']['CHECK_MEMORY']['recall'])

    def test_positive_port_call_is_not_automatically_a_correctly_scoped_read(self):
        text='Remind me of my notes about copper terrarium'
        actual=resolve(text)
        start=text.index('copper terrarium')
        entity=dict(type='MEMORY',rawValue='copper terrarium',startOffset=start,endOffset=len(text))
        row=dict(id='scope-fixture',utterance=text,intent='CHECK_MEMORY',disposition='read',
            input_source='TEXT',entities=[entity],query_text='copper terrarium',filters={})
        encode=lambda value:base64.b64encode(value.encode()).decode()
        spans=';'.join(':'.join((e['type'],str(e['startOffset']),str(e['endOffset']),encode(e['rawValue']),'')) for e in actual['entities'])
        native={row['id']:dict(intent='CHECK_MEMORY',reads='1',contexts='1',proposals='0',completed='0',status='ANSWER',
            entities=spans,query_base64=encode(actual['query_text']),message_base64=encode('Synthetic memory'))}
        with patch('v11_evaluate.predict',return_value=(['CHECK_MEMORY'],np.array([[1,0,0,0,0]]))),\
             patch('v11_evaluate.calibration_metrics',return_value={}):
            result=section([row],native,None,dict(settings=self.settings))
        self.assertEqual(1,result['counts']['admitted_positive_read_calls'])
        self.assertEqual(0,result['counts']['admitted_valid_reads'])
        self.assertEqual(1,result['counts']['wrong_scope_positive_reads'])

    def test_previously_scored_model_cannot_be_silently_changed(self):
        selected=dict(configuration=dict(c=1.0,cues=True),settings=self.settings)
        payload=export(self.model,selected,dict(data_sha256='data',model_source_sha256='model',protocol_sha256='protocol'))
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory) / 'candidate.json'
            path.write_text(json.dumps(payload),encoding='utf-8')
            check_reproduction(payload,path)
            changed=copy.deepcopy(payload)
            changed['bindings']['data_sha256']='different'
            with self.assertRaisesRegex(ValueError,'new version'): check_reproduction(changed,path)
            changed=copy.deepcopy(payload)
            changed['coefficients'][0][0]+=.01
            with self.assertRaisesRegex(ValueError,'numeric candidate'): check_reproduction(changed,path)
            path.write_text(json.dumps(changed),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'payload drift'): check_reproduction(payload,path)


if __name__=='__main__': unittest.main()
