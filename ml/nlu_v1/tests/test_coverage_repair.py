"""Search fixtures cannot become new semantic/privacy approvals."""
import unittest

from ml.nlu_v1.dataset_v0 import coverage_repair as repair
from ml.nlu_v1.data_management.records import DataError


class CoverageRepairTests(unittest.TestCase):
    def test_protected_splits_rejected_before_search(self):
        for split in ('test','val','oos_test','oos_val','FINAL_INDEPENDENT_TEST'):
            with self.assertRaises(DataError):
                repair.native_fields('clinc150_full',split,'0',['fixture','reminder'])

    def test_synthetic_and_future_sources_rejected(self):
        for source in ('metis_controlled_synthetic','future_source'):
            with self.assertRaises(DataError):
                repair.native_fields(source,'train','0',['fixture','reminder'])

    def test_massive_native_source_identity_and_split_preserved(self):
        native={'partition':'train','id':1,'utt':'fixture remembers nothing','intent':'general_quirky'}
        row=repair.native_fields('massive_1_1_en_us','train','1',native)
        self.assertEqual(row['text'],native['utt'])
        self.assertEqual(row['original_intent'],'general_quirky')
        self.assertEqual(row['source_id'],'1')
        with self.assertRaises(DataError):
            repair.native_fields('massive_1_1_en_us','train','2',native)

    def test_financial_owe_does_not_become_promise(self):
        self.assertEqual(repair.promise_screen('what tax do i owe','taxes'),'FINANCIAL_OR_STATUTORY_OBLIGATION_NOT_RECORDED_PERSONAL_PROMISE')

    def test_fraud_committed_does_not_become_promise(self):
        self.assertEqual(repair.promise_screen('fraud committed on my account','report_fraud'),'FRAUD_COMMITTED_NOT_PROMISE')

    def test_thanks_and_dialogue_agreement_are_not_promise_records(self):
        self.assertEqual(repair.promise_screen('i owe you one','thank_you'),'SOCIAL_IDIOM_NOT_SPECIFIC_PROMISE')
        self.assertEqual(repair.promise_screen('agreed that is valid','yes'),'DIALOGUE_AGREEMENT_NOT_PROMISE')

    def test_explicit_promise_is_not_rejected_just_because_source_intent_differs(self):
        self.assertEqual(repair.promise_screen('what did i promise sam','general_quirky'),'EXPLICIT_PROMISE_CUE_REQUIRES_MEANING_AND_MAPPING_VALIDATION')

    def test_keyword_hit_is_not_a_mapping_or_authorization(self):
        sources={'clinc150_full':{'lookup':{('oos_train','0'):['remember this fixture note','oos']}}}
        result=repair.search(sources)
        self.assertEqual(result['scanned_training_rows'],{'clinc150_full':1})
        self.assertEqual(len(result['search_hits']),1)
        self.assertNotIn('domain',result['search_hits'][0])
        self.assertEqual(result['search_hits'][0]['original_intent'],'oos')
        self.assertEqual(result['search_hits'][0]['status'],'RETRIEVAL_ONLY_NOT_CANONICAL_OR_TRAINING_APPROVAL')

    def test_both_sources_and_oos_training_are_searched(self):
        sources={'clinc150_full':{'lookup':{('oos_train','0'):['what did i promise','oos']}},
                 'massive_1_1_en_us':{'lookup':{('train','1'):{'id':1,'partition':'train','utt':'some fixture','intent':'general_quirky'}}}}
        result=repair.search(sources)
        self.assertEqual(sum(result['scanned_training_rows'].values()),2)
        self.assertEqual(result['promise_screen_counts']['EXPLICIT_PROMISE_CUE_REQUIRES_MEANING_AND_MAPPING_VALIDATION'],1)

    def test_empty_search_is_not_fake_positive_coverage(self):
        result=repair.search({'clinc150_full':{'lookup':{('train','0'):['set fixture alarm','alarm']}}})
        self.assertEqual(result['search_hits'],[])
        self.assertEqual(result['cue_counts'],{})


if __name__=='__main__':
    unittest.main()
