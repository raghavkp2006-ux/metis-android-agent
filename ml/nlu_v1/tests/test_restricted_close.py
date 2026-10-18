"""Closure preparation stays pending; tests do not render or release dataset rows."""
import copy
import tempfile
import unittest
from pathlib import Path

from ml.nlu_v1.dataset_v0 import closure_review, restricted_close
from ml.nlu_v1.data_management.records import DataError, sha_json


class RestrictedClosureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context=closure_review.context()

    def test_exact_two_finite_templates(self):
        t=self.context['templates']
        self.assertEqual(len(t),2)
        self.assertEqual({x['capability_group']:x['planned_max_rows'] for x in t.values()},
                         {'CALENDAR':4,'GOAL_PROJECT':20})
        self.assertTrue(all(x['disposition']=='SUPPORTED_COMPLETE' and x['privacy']=='RESTRICTED'
                            and x['publication_allowed'] is False and x['external_or_private_inputs'] is False for x in t.values()))

    def test_first_party_definition_is_local_restricted(self):
        s=self.context['source']
        self.assertEqual(s['definition']['acquisition'],'LOCAL_DETERMINISTIC_GENERATION_NO_EXTERNAL_DOWNLOAD')
        self.assertFalse(s['definition']['publication_allowed'])
        self.assertEqual(len(s['catalogs']),2)
        self.assertEqual(set(s['catalogs'][self.context['catalog_version']]['template_sha256']),set(self.context['templates']))

    def test_review_packet_has_ten_distinct_pending_targets(self):
        b=closure_review.targets(self.context)
        self.assertEqual(len(b),10)
        self.assertEqual(len([x for x in b if x.startswith('scope:')]),8)
        self.assertEqual(set(closure_review.parse_document(closure_review.document(self.context),self.context)),set(b))
        self.assertTrue(all(x['publication_allowed'] is False for x in b.values() if x['kind']=='OPERATOR_AUTHORIZATION'))

    def test_packet_description_and_scope_cannot_be_changed(self):
        doc=closure_review.document(self.context)
        with self.assertRaises(DataError):closure_review.parse_document(doc.replace('publication prohibited','publication permitted'),self.context)
        with self.assertRaises(DataError):closure_review.parse_document(doc.replace('scope:FIRST_PARTY_INTAKE','scope:PUBLIC_INTAKE'),self.context)

    def test_unsigned_decision_is_not_valid_signature(self):
        ctx=self.context
        r=closure_review.draft(next(iter(closure_review.targets(ctx).values())),ctx)
        r['decision']='APPROVE'
        with self.assertRaises(DataError):closure_review.verify_record(r,ctx,approved=True)

    def test_each_operator_scope_required_independently(self):
        records={'scope:'+name:{'decision':'APPROVE','binding':{'scope':name}} for name in closure_review.SCOPES}
        for name in closure_review.SCOPES:
            reduced={k:v for k,v in records.items() if k!='scope:'+name}
            with self.assertRaises(DataError):restricted_close.scope(reduced,name)

    def test_four_partitioner_rejects_unapproved_or_empty(self):
        with self.assertRaises(DataError):restricted_close.partition([])

    def test_freeze_verifier_rejects_missing_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises((DataError,FileNotFoundError)):
                restricted_close.verify_manifest(Path(temp))

    def test_profile_never_consumes_final(self):
        p=self.context['profile']
        self.assertEqual(p['partitions'],list(restricted_close.TUNING))
        self.assertEqual(p['independent_final'],'RESERVED_NOT_CONSUMED')
        self.assertFalse(p['publication_allowed'])
        self.assertTrue(p['protected_splits_prohibited'])

    def test_context_binds_exact_candidate_membership(self):
        self.assertEqual(len(self.context['current_row_sha256']),1901)
        self.assertEqual(self.context['current_public_rows'],1762)
        self.assertEqual(self.context['current_synthetic_rows'],139)
        self.assertEqual(len(set(self.context['current_row_sha256'])),1901)


if __name__=='__main__':unittest.main()
