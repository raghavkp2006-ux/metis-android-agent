"""Template previews/fixture signatures are not real generated dataset rows."""
import base64
from collections import Counter, defaultdict
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from ml.nlu_v1.dataset_v0 import template_review as review
from ml.nlu_v1.data_management.records import DataError


class TemplateReviewTests(unittest.TestCase):
    def setUp(self):
        self.ctx=review.context()

    def fixture_key(self):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives.serialization import Encoding,PublicFormat
        key=Ed25519PrivateKey.generate()
        public=base64.b64encode(key.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw)).decode()
        return key,{'registry_version':'nlu_v1_group_reviewers_001','reviewers':{'fixture-human':{'public_key_ed25519_base64':public}}}

    def approval(self,decision='APPROVE'):
        template=next(iter(self.ctx['templates'].values()))
        record=review.draft(template,self.ctx)
        key,registry=self.fixture_key()
        record.update(decision=decision,reviewer_id='fixture-human',reviewed_at='2026-10-17T00:00:00Z',reviewer_note='fixture review only',reviewer_registry_sha256=review.packet.file_hash(review.semantic.REVIEWERS))
        if decision=='CHANGE':record['requested_change']='fixture requested edit'
        record['signature_ed25519_base64']=base64.b64encode(key.sign(review.semantic.signed_bytes(record))).decode()
        return record,registry

    def test_exact_pending_scope_no_rows_or_automatic_approvals(self):
        self.assertEqual(len(self.ctx['templates']),26)
        self.assertEqual(sum(t['planned_max_rows'] for t in self.ctx['templates'].values()),165)
        for t in self.ctx['templates'].values():
            record=review.draft(t,self.ctx)
            self.assertEqual(record['decision'],'PENDING')
            self.assertIsNone(record['reviewer_id'])
            self.assertIsNone(record['reviewed_at'])
            self.assertIsNone(record['signature_ed25519_base64'])

    def test_three_schema_span_validated_illustrations_each(self):
        for t in self.ctx['templates'].values():
            for index in range(3):
                preview=review.preview(t,index)
                self.assertNotIn('id',preview)
                self.assertNotIn('source',preview)
                self.assertEqual(preview['meaning']['domain'],t['domain'])
                self.assertEqual(sorted({s['type'] for s in preview['meaning']['slots']}),t['allowed_slots'])

    def test_future_budget_projects_missing_groups_without_claiming_actual_coverage(self):
        counts=Counter();families=defaultdict(set);scenarios=defaultdict(set)
        for t in self.ctx['templates'].values():
            counts[t['capability_group']]+=t['planned_max_rows']
            families[t['capability_group']].add(t['family'])
            scenarios[t['capability_group']].add(t['scenario_id'])
        for group in ('MEMORY','PROMISE','GOAL_PROJECT','PLANNING_FOCUS_HABIT'):
            self.assertEqual(counts[group],30)
            self.assertGreaterEqual(len(families[group]),3)
            self.assertGreaterEqual(len(scenarios[group]),4)

    def test_all_requested_domain_labels_represented(self):
        domains={t['domain'] for t in self.ctx['templates'].values()}
        self.assertLessEqual({'MEMORY','PROMISE','GOAL','PROJECT','PLANNING','FOCUS','HABIT','TASK','CALENDAR','PERSON','COMMUNICATION','PREFERENCE'},domains)

    def test_patterns_and_bindings_and_labels_are_immutable_in_packet(self):
        text=review.document(self.ctx)
        ident=next(iter(self.ctx['templates']))
        self.assertEqual(set(review.choices(text,self.ctx).values()),{'PENDING'})
        for replacement in (text.replace('Fixed seed:','Changed seed:',1),text.replace(ident,'modified-template',1)):
            with self.assertRaises(DataError):review.choices(replacement,self.ctx)

    def test_explicit_choice_changes_only_decision_column(self):
        text=review.document(self.ctx)
        changed=text.replace('| PENDING |','| APPROVE |',1)
        choices=review.choices(changed,self.ctx)
        self.assertEqual(Counter(choices.values()),{'PENDING':25,'APPROVE':1})

    def test_slot_label_mutation_rejected(self):
        catalog=copy.deepcopy(self.ctx['catalog'])
        catalog['templates'][0]['allowed_slots']=['UNKNOWN_SLOT']
        with self.assertRaises(DataError):review.validate_catalog(catalog,self.ctx['policy'])

    def test_unsafe_format_or_private_input_rejected(self):
        for change in ('format','private'):
            catalog=copy.deepcopy(self.ctx['catalog'])
            if change=='format':catalog['templates'][0]['forms'][0]='bad {subject!r}'
            else:catalog['templates'][0]['external_or_private_inputs']=True
            with self.assertRaises(DataError):review.validate_catalog(catalog,self.ctx['policy'])

    def test_human_signature_reuses_existing_registry_and_serialization(self):
        record,registry=self.approval()
        with patch.object(review.semantic,'load_reviewers',return_value=registry):
            result=review.verify_approval(record,self.ctx,require_approve=True)
        self.assertTrue(result['template_semantic_authority'])
        self.assertEqual(result['dataset_rows_approved'],0)
        self.assertEqual(result['individual_rows_reviewed'],0)

    def test_unsigned_pending_cannot_authorize_inheritance(self):
        record=review.draft(next(iter(self.ctx['templates'].values())),self.ctx)
        with self.assertRaises(DataError):review.verify_approval(record,self.ctx,require_approve=True)

    def test_template_hash_change_invalidates_real_fixture_signature(self):
        record,registry=self.approval()
        record['binding']['template_sha256']='0'*64
        with patch.object(review.semantic,'load_reviewers',return_value=registry):
            with self.assertRaises(DataError):review.verify_approval(record,self.ctx)

    def test_stale_context_cannot_replay_prior_approval(self):
        record,_=self.approval()
        ctx=copy.deepcopy(self.ctx);ctx['templates'][record['binding']['template_id']]['generation_seed']+=1
        with self.assertRaises(DataError):review.verify_approval(record,ctx)

    def test_reject_and_change_never_authorize_inheritance(self):
        for decision in ('REJECT','CHANGE'):
            record,registry=self.approval(decision)
            with patch.object(review.semantic,'load_reviewers',return_value=registry):
                with self.assertRaises(DataError):review.verify_approval(record,self.ctx,require_approve=True)

    def test_modified_decision_cannot_keep_existing_signature(self):
        record,registry=self.approval()
        record['decision']='REJECT'
        with patch.object(review.semantic,'load_reviewers',return_value=registry):
            with self.assertRaises(DataError):review.verify_approval(record,self.ctx)

    def test_independent_template_cannot_inherit_other_template_signature(self):
        record,registry=self.approval()
        record['binding']['template_id']=list(self.ctx['templates'])[1]
        with patch.object(review.semantic,'load_reviewers',return_value=registry):
            with self.assertRaises(DataError):review.verify_approval(record,self.ctx)

    def test_prepare_sync_and_sign_verify_fixture_only(self):
        key,registry=self.fixture_key()
        with tempfile.TemporaryDirectory() as directory,patch.object(review,'require_local'):
            folder=Path(directory)/'packet'
            review.prepare(self.ctx,folder)
            path=folder/'TEMPLATE_DECISIONS.md'
            path.write_bytes(path.read_bytes().replace(b'| PENDING |',b'| APPROVE |'))
            review.sync(folder)
            with patch.object(review.semantic,'load_reviewers',return_value=registry):
                result=review.sign(folder,'fixture',key,'fixture-human','2026-10-17T00:00:00Z','fixture only')
                verified=review.verify_batch(folder,'fixture')
            self.assertEqual(result['signed_template_decisions'],26)
            self.assertTrue(verified['all_templates_approved'])
            self.assertEqual(verified['dataset_rows_generated'],0)

    def test_pending_sign_refused(self):
        key,_=self.fixture_key()
        with tempfile.TemporaryDirectory() as directory,patch.object(review,'require_local'):
            folder=Path(directory)/'packet';review.prepare(self.ctx,folder)
            with self.assertRaises(DataError):review.sign(folder,'fixture',key,'fixture-human','2026-10-17T00:00:00Z','fixture only')

    def test_interactive_quit_does_not_decide_anything(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(review,'require_local'):
            folder=Path(directory)/'packet';review.prepare(self.ctx,folder)
            original=(folder/'TEMPLATE_DECISIONS.md').read_bytes()
            review.review(folder,key_input=lambda:'Q',output=lambda _:None)
            self.assertEqual(original,(folder/'TEMPLATE_DECISIONS.md').read_bytes())

    def test_grouping_siblings_share_parent_and_seeds_are_fixed_distinct(self):
        parents=defaultdict(set);seeds=[]
        for t in self.ctx['templates'].values():
            parents[t['capability_group']].add(t['semantic_parent']);seeds.append(t['generation_seed'])
        self.assertTrue(all(len(p)==1 for p in parents.values()))
        self.assertEqual(len(seeds),len(set(seeds)))


if __name__=='__main__':unittest.main()
