"""Versioned regression safety, provenance and offline classifier isolation."""
import base64
import copy
import csv
import json
import tempfile
import unittest
from unittest.mock import patch
import numpy as np

from v12_data import HERE,ROOT,LABELS,USER_TEXTS,user_rows,development_rows,aggregate,canonical,digest
from v12_candidate import resolve,READ_LABELS,units
from v12_grammar import kotlin
from v12_integrity import check_manifest
from v12_training import authored,SLOTS
from v12_model import fit,calibrate,select,predict,export,exported_predict,bind,calibration_metrics
from v12_evaluate import classifier_parts,evaluate,check_reproduction


class CandidateV12Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=aggregate()
        cls.parts,cls.old_test=classifier_parts()
        cls.sample=sum(([r for r in cls.parts['train'] if r['intent']==label][:15] for label in LABELS),[])
        cls.model=fit(cls.sample,dict(c=1.0,cues=True))
        cls.settings=dict(temperature=.8,threshold=.90,abstain=False)

    def test_current_and_retained_integrity_generated_grammar(self):
        manifest=check_manifest()
        self.assertGreater(len(manifest['retained_hashes']),77)
        self.assertEqual(kotlin(),(ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV12.kt').read_text(encoding='utf-8'))

    def test_user_verbatim_separate_context_draft_and_no_fitting(self):
        data=json.loads((HERE / 'user-development.json').read_text(encoding='utf-8'))
        self.assertEqual(USER_TEXTS,[r['utterance'] for r in data['rows']])
        self.assertEqual(50,len(USER_TEXTS))
        self.assertEqual(user_rows(),data['rows'])
        self.assertIn('separate',json.dumps(data).lower())
        for row in data['rows']:
            self.assertFalse(row['human_approved']);self.assertFalse(row['fit_eligible'])
            self.assertEqual('assistant_draft',row['annotation_provenance'])
            self.assertEqual('user_submitted',row['provenance'])
        human={canonical(t) for t in USER_TEXTS}
        for rows in self.parts.values():
            self.assertFalse(human & {canonical(r['utterance']) for r in rows})

    def test_projections_and_device_fixture_preserve_full_subject(self):
        inputs=list(csv.DictReader((HERE / 'regression-input.tsv').read_text().splitlines(),delimiter='\t'))
        expected=list(csv.DictReader((HERE / 'regression-expectations.tsv').read_text().splitlines(),delimiter='\t'))
        self.assertEqual(6603,len(inputs));self.assertEqual(6603,len(expected))
        for row,input_row,annotation in zip(self.rows,inputs,expected,strict=True):
            self.assertEqual(row['id'],input_row['id']);self.assertEqual(row['id'],annotation['id'])
            self.assertEqual(row['utterance'],base64.b64decode(input_row['text_base64']).decode())
            self.assertEqual(row.get('query_text',''),base64.b64decode(annotation['query_base64']).decode())
        device=list(csv.DictReader((ROOT / 'app/src/androidTest/assets/phase15-v12.tsv').read_text().splitlines(),delimiter='\t'))
        self.assertEqual(90,len(device));self.assertEqual(44,sum(r['disposition']=='read' for r in device))
        by_id={r['id']:r for r in self.rows}
        for fixture in device:
            row=by_id[fixture['id']]
            self.assertEqual(row['utterance'],base64.b64decode(fixture['text_base64']).decode())
            self.assertEqual(row.get('query_text',''),base64.b64decode(fixture['query_base64']).decode())

    def test_entire_corpus_correct_scope_and_forced_labels_zero_forbidden_reads(self):
        reads={};attempts=0
        for row in self.rows:
            plan=resolve(row['utterance'],source=row['input_source'])
            if plan['reads']:
                self.assertEqual('read',row['disposition'],row['id'])
                self.assertEqual(row['intent'],plan['intent'],row['id'])
                self.assertEqual(row['entities'],plan['entities'],row['id'])
                self.assertEqual(row['query_text'],plan['query_text'],row['id'])
                reads[row['section']]=reads.get(row['section'],0)+1
            if row['disposition']!='read':
                for label in READ_LABELS:
                    attempts+=1
                    self.assertEqual(0,resolve(row['utterance'],label,row['input_source'])['reads'],row['id'])
        self.assertEqual(dict(development=78,exposed=23,v9=299,v8=18,v7=27,v11=30,v12=30,user=4),reads)
        self.assertGreater(attempts,22000)

    def test_articles_unicode_attribution_and_calendar_boundaries(self):
        cases=[('Remind me of my notes about The Who','Who','The Who'),
            ('Remind me of my notes about café 🚀 tuning','café 🚀 tuning','café 🚀 tuning'),
            ('So, what can you recall from what I said to Zoë O’Neil?','Zoë O’Neil','what I said to Zoë O’Neil'),
            ('Did I leave any notes behind for a meeting with Asha Bedi?','Asha Bedi','meeting with Asha Bedi')]
        for text,raw,query in cases:
            plan=resolve(text);self.assertEqual(1,plan['reads'],text)
            self.assertEqual(query,plan['query_text']);self.assertEqual(raw,plan['entities'][0]['rawValue'])
            span=plan['entities'][0]
            self.assertEqual(raw,text.encode('utf-16-le')[span['startOffset']*2:span['endOffset']*2].decode('utf-16-le'))
        for row in user_rows()[22:26]:
            self.assertEqual('unsupported',resolve(row['utterance'])['disposition'])
            for suffix in (' and email Rohit',' from last month',' only the first two'):
                self.assertNotEqual('unsupported',resolve(row['utterance'].rstrip('?')+suffix)['disposition'])
        for text in ('Remind me of my notes about this','Remind me of the promises I made to him',
            'Do not remind me of my notes about café tuning','So, what can you recall from what I said to Vikram and email him?',
            'Remind me of my notes about café\u200b tuning'):
            self.assertEqual(0,resolve(text)['reads'],text)

    def test_256_utf16_query_boundary_no_truncation(self):
        # V10 descriptive attribution allows a complete subject longer than its name annotation.
        prefix='what the doctor at the '+'c'*180+' said about '
        query=prefix+'g'*(256-units(prefix))
        text='Did I write down '+query
        plan=resolve(text)
        self.assertEqual(1,plan['reads']);self.assertEqual(256,units(plan['query_text']))
        self.assertEqual(query,plan['query_text'])
        self.assertEqual(0,resolve(text+'g')['reads'])
        article_query='The '+'x'*200
        article_plan=resolve('Remind me of my notes about '+article_query)
        self.assertEqual(1,article_plan['reads']);self.assertEqual(article_query,article_plan['query_text'])
        self.assertEqual(200,units(article_plan['entities'][0]['rawValue']))
        self.assertEqual(0,resolve('Remind me of my notes about '+article_query+'x')['reads'])
        self.assertEqual(0,resolve('Remind me of my notes about '+'x'*257)['reads'])
        for source in ('NOTIFICATION','SYSTEM'):
            self.assertEqual(0,resolve('Remind me of my notes about copper tuning',source=source)['reads'])

    def test_classifier_family_split_and_no_exposed_duplicates(self):
        data=json.loads((HERE / 'classifier-development.json').read_text(encoding='utf-8'))
        self.assertEqual(digest(HERE / 'protocol.json'),data['protocol_sha256'])
        exclusions=set(data['exclusions'])
        self.assertEqual([r for r in authored() if r['id'] not in exclusions],data['rows'])
        exposed={canonical(r['utterance']) for r in self.rows}
        seen=set();families={}
        for row in data['rows']:
            text=canonical(row['utterance'])
            self.assertNotIn(text,exposed);self.assertNotIn(text,seen);seen.add(text)
            self.assertEqual('assistant_authored',row['provenance']);self.assertFalse(row['human_approved'])
            self.assertEqual(row['split'],families.setdefault(row['family_id'],row['split']))
            for split,slots in SLOTS.items():
                if split!=row['split']:
                    self.assertFalse(any(slot.lower() in text for group in slots for slot in group))
        self.assertEqual(898,len(data['rows']))

    def test_partition_family_and_human_contamination_rejected(self):
        with self.assertRaises(ValueError): fit(self.parts['test'],dict(c=1.0,cues=True))
        with self.assertRaises(ValueError): calibrate(self.model,self.parts['train'])
        for split in ('train','calibration','selection'):
            parts=copy.deepcopy(self.parts);parts[split][0]['provenance']='user_submitted'
            with patch('v12_model.head') as trainer,self.assertRaises(ValueError):
                select(parts['train'],parts['calibration'],parts['selection'])
            trainer.assert_not_called()
        parts=copy.deepcopy(self.parts);parts['selection'][0]['family_id']=parts['train'][0]['family_id']
        with patch('v12_model.head') as trainer,self.assertRaises(ValueError):
            select(parts['train'],parts['calibration'],parts['selection'])
        trainer.assert_not_called()

    def test_hierarchy_numeric_export_and_no_annotation_features(self):
        selected=dict(configuration=dict(c=1.0,cues=True),settings=self.settings)
        payload=export(self.model,selected,{'fixture':'unit-test'})
        probes=[dict(utterance=t) for t in ('What did I promise Zoë D’Souza?','Ｓｈｏｗ my tasks','Recall café 🚀 tuning',
            "Don't read my memory",'Create a task to purchase copper')]+self.parts['test']
        labels,probabilities=predict(self.model,probes,self.settings)
        actual,restored=exported_predict(json.loads(json.dumps(payload)),probes)
        self.assertEqual(labels,actual);np.testing.assert_allclose(probabilities,restored,atol=1e-12,rtol=1e-12)
        changed=copy.deepcopy(probes)
        for row in changed: row.update(id='forged',intent='UNKNOWN',entities=['forged'],query_text='forged')
        self.assertEqual(labels,predict(self.model,changed,self.settings)[0])
        for change in ('weights','binding','flags','temperature','threshold'):
            altered=copy.deepcopy(payload)
            if change=='weights': altered['heads']['binary']['coefficients'][0][0]+=.1
            elif change=='binding': altered['bindings']['fixture']='changed'
            elif change=='flags': altered['ml_enabled']=True;altered['payload_sha256']=bind(altered)
            else:
                altered['selected']['settings'][change]=0;altered['payload_sha256']=bind(altered)
            with self.assertRaises(ValueError): exported_predict(altered,probes)

    def test_selection_abstains_when_no_safe_cutoff_exists(self):
        class Wrong:
            def decision_function(self,texts): return np.tile([100,0,0,0,0],(len(texts),1))
        parts={s:[dict(id=s+label,utterance=label,intent=label,split=s,family_id=s+label) for label in LABELS]
            for s in ('train','calibration','selection')}
        with patch('v12_model.fit',return_value=Wrong()),patch('v12_model.calibrate',return_value=1.0):
            model,selected,_=select(parts['train'],parts['calibration'],parts['selection'])
        self.assertTrue(selected['settings']['abstain'])
        self.assertEqual(['UNKNOWN']*5,predict(model,parts['selection'],selected['settings'])[0])

    def test_output_protection_and_calibration_diagnostics(self):
        with self.assertRaisesRegex(ValueError,'ignored'): evaluate(HERE / 'missing.tsv',HERE / 'results.json')
        rows=[dict(intent=label) for label in LABELS]
        score=calibration_metrics(rows,np.eye(5))
        self.assertEqual(0,score['nll']);self.assertEqual(0,score['brier']);self.assertEqual(0,score['ece_10_bins'])

    def test_scored_candidate_cannot_silently_change_weights_data_or_protocol(self):
        selected=dict(configuration=dict(c=1.0,cues=True),settings=self.settings)
        payload=export(self.model,selected,dict(data_sha256='data',model_sha256='model',protocol_sha256='protocol',manifest_sha256='manifest'))
        with tempfile.TemporaryDirectory() as directory:
            path=HERE.__class__(directory) / 'export.json'
            path.write_text(json.dumps(payload),encoding='utf-8')
            check_reproduction(payload,path)
            for key in ('data_sha256','model_sha256','protocol_sha256'):
                changed=copy.deepcopy(payload);changed['bindings'][key]='changed'
                with self.assertRaisesRegex(ValueError,'new candidate'): check_reproduction(changed,path)
            changed=copy.deepcopy(payload);changed['heads']['binary']['coefficients'][0][0]+=.1
            with self.assertRaisesRegex(ValueError,'new candidate'): check_reproduction(changed,path)
            changed=copy.deepcopy(payload);changed['bindings']['manifest_sha256']='updated report docs only'
            check_reproduction(changed,path)


if __name__=='__main__': unittest.main()
