import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_platform_v2_scope_promotion import prepare, candidate_bundle, runner_for
from v2.context_package import ContextPackageStore
from v2.context_retrieval import ContextRetrievalStore
from v2.effects import EffectStatus
from v2.objective_promotion_effect import (
    ObjectivePromotionPostDisposition,
    _effect_scope,
    execute_frozen_promotion_post,
)
from v2.ordinary_effects import OrdinaryEffectStore
from v2.ordinary_remote import OrdinaryEffectBinding, comment_payload
from v2.scope_promotion import PromotionStore, validate_promotion
from v2.task_event_composition import TaskAuthorityEventStore


class FakeRemote:
    def __init__(self, *, root: Path, accepted_main: str, issue_payload: dict):
        self.root = root
        self.accepted_main = accepted_main
        self.issue_payload = issue_payload
        self.comments = []
        self.comment_exec_count = 0
        self.unknown_comments = False

    def __call__(self, args, **kwargs):
        command = tuple(args)
        if command == (
            'gh','issue','view','756','--repo','ni-da-ba/skyforge',
            '--json','number,title,body,comments,updatedAt,state','--jq=.'
        ):
            return subprocess.CompletedProcess(args,0,stdout=json.dumps(self.issue_payload),stderr='')
        if command == (
            'gh','api','repos/ni-da-ba/skyforge/commits/main','--jq','.sha'
        ):
            return subprocess.CompletedProcess(args,0,stdout=self.accepted_main+'\n',stderr='')
        if command == (
            'gh','api','repos/ni-da-ba/skyforge/issues/756/comments?per_page=100',
            '--paginate','--jq','.[]'
        ):
            if self.unknown_comments:
                raise subprocess.CalledProcessError(1,args,stderr='github unavailable')
            out=''.join(json.dumps(item)+'\n' for item in self.comments)
            return subprocess.CompletedProcess(args,0,stdout=out,stderr='')
        if len(command) >= 7 and command[:4] == ('gh','issue','comment','756'):
            self.comment_exec_count += 1
            body = command[command.index('--body')+1]
            self.comments.append({'id':9000+self.comment_exec_count,'body':body})
            return subprocess.CompletedProcess(args,0,stdout='https://github.com/ni-da-ba/skyforge/issues/756#issuecomment-x\n',stderr='')
        raise AssertionError(command)


def freeze_ready_promotion(root: Path):
    _prop, package, retrieval, payload = candidate_bundle(root)
    result = validate_promotion(
        root=root, repo='ni-da-ba/skyforge', package=package, retrieval=retrieval,
        gh_runner=runner_for(payload),
    )
    PromotionStore.for_root(root).capture(result)
    ContextPackageStore.for_root(root).capture(package)
    ContextRetrievalStore.for_root(root).capture(retrieval)
    return result, package, retrieval, payload


class ObjectivePromotionEffectTest(unittest.TestCase):
    def test_no_frozen_ready_promotion_cannot_execute(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            remote=FakeRemote(root=root,accepted_main='a'*40,issue_payload={})
            result=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(result.disposition,ObjectivePromotionPostDisposition.BLOCKED)
            self.assertIn('no frozen objective promotion',result.reason)
            self.assertEqual(remote.comment_exec_count,0)

    def test_posting_module_cannot_write_task_authority_store_directly(self):
        source=(Path(__file__).parent/'v2/objective_promotion_effect.py').read_text(encoding='utf-8')
        self.assertNotIn('from .task_event_composition',source)
        self.assertNotIn('TaskAuthorityEventStore.for_root',source)

    def test_absent_executes_one_exact_comment_and_completes(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            remote=FakeRemote(root=root,accepted_main=package.accepted_main_sha,issue_payload=payload)
            result=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(result.disposition,ObjectivePromotionPostDisposition.EXECUTED)
            self.assertEqual(remote.comment_exec_count,1)
            self.assertEqual(len(remote.comments),1)
            self.assertIn(promotion.draft.body,remote.comments[0]['body'])
            self.assertIn('<!-- skyforge:v2-effect:',remote.comments[0]['body'])
            ledger=OrdinaryEffectStore.for_root(root).load()
            records=[r for r in ledger.records if r.identity.effect_id==result.effect_id]
            self.assertEqual(len(records),1)
            self.assertEqual(records[0].status,EffectStatus.COMPLETE)
            self.assertEqual(records[0].remote_identity,'comment:9001')
            self.assertEqual(len(TaskAuthorityEventStore.for_root(root).load().records),0)

    def test_crash_after_remote_execute_reconciles_without_duplicate(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            _promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            remote=FakeRemote(root=root,accepted_main=package.accepted_main_sha,issue_payload=payload)
            with self.assertRaisesRegex(RuntimeError,'injected crash'):
                execute_frozen_promotion_post(
                    root=root,repo='ni-da-ba/skyforge',runner=remote,crash_after_execute=True
                )
            self.assertEqual(remote.comment_exec_count,1)
            pending=OrdinaryEffectStore.for_root(root).load().records[-1]
            self.assertEqual(pending.status,EffectStatus.PENDING)
            second=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(second.disposition,ObjectivePromotionPostDisposition.RECONCILED)
            self.assertEqual(remote.comment_exec_count,1)
            self.assertEqual(OrdinaryEffectStore.for_root(root).load().records[-1].status,EffectStatus.COMPLETE)

    def test_already_complete_is_noop_even_if_remote_becomes_unavailable(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            _promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            remote=FakeRemote(root=root,accepted_main=package.accepted_main_sha,issue_payload=payload)
            first=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(first.disposition,ObjectivePromotionPostDisposition.EXECUTED)
            remote.unknown_comments=True
            second=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(second.disposition,ObjectivePromotionPostDisposition.ALREADY_COMPLETE)
            self.assertEqual(remote.comment_exec_count,1)

    def test_pending_present_conflict_blocks_without_repost(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            scope=_effect_scope(repo='ni-da-ba/skyforge',promotion=promotion,accepted_main_sha=package.accepted_main_sha)
            identity=scope.comment_identity(promotion.draft.body)
            store=OrdinaryEffectStore.for_root(root)
            store.save(store.load().begin(identity))
            remote=FakeRemote(root=root,accepted_main=package.accepted_main_sha,issue_payload=payload)
            exact=comment_payload(identity,promotion.draft.body)
            remote.comments=[{'id':9911,'body':exact+'\nconflict'}]
            result=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(result.disposition,ObjectivePromotionPostDisposition.BLOCKED)
            self.assertEqual(remote.comment_exec_count,0)
            self.assertEqual(store.load().get(identity).status,EffectStatus.PENDING)

    def test_pending_unknown_blocks_without_repost(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            scope=_effect_scope(repo='ni-da-ba/skyforge',promotion=promotion,accepted_main_sha=package.accepted_main_sha)
            identity=scope.comment_identity(promotion.draft.body)
            store=OrdinaryEffectStore.for_root(root); store.save(store.load().begin(identity))
            remote=FakeRemote(root=root,accepted_main=package.accepted_main_sha,issue_payload=payload)
            remote.unknown_comments=True
            result=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(result.disposition,ObjectivePromotionPostDisposition.BLOCKED)
            self.assertEqual(remote.comment_exec_count,0)
            self.assertEqual(store.load().get(identity).status,EffectStatus.PENDING)

    def test_remote_main_move_blocks_before_comment_execution(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            _promotion, package, _retrieval, payload=freeze_ready_promotion(root)
            remote=FakeRemote(root=root,accepted_main='f'*40,issue_payload=payload)
            result=execute_frozen_promotion_post(root=root,repo='ni-da-ba/skyforge',runner=remote)
            self.assertEqual(result.disposition,ObjectivePromotionPostDisposition.BLOCKED)
            self.assertIn('remote main moved',result.reason)
            self.assertEqual(remote.comment_exec_count,0)

    def test_frozen_body_is_exact_binding_and_deterministic_identity(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare(root)
            promotion, package, _retrieval, _payload=freeze_ready_promotion(root)
            first=_effect_scope(repo='ni-da-ba/skyforge',promotion=promotion,accepted_main_sha=package.accepted_main_sha)
            second=_effect_scope(repo='ni-da-ba/skyforge',promotion=promotion,accepted_main_sha=package.accepted_main_sha)
            self.assertEqual(first.attempt_id,second.attempt_id)
            self.assertEqual(first.comment_identity(promotion.draft.body),second.comment_identity(promotion.draft.body))
            binding=OrdinaryEffectBinding(
                scope=first,identity=first.comment_identity(promotion.draft.body),comment_body=promotion.draft.body
            )
            self.assertEqual(binding.comment_body,promotion.draft.body)


if __name__=='__main__': unittest.main()
