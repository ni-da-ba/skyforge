from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from platform_v2_production_runtime import ProductionHostedRuntime
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import (
    LiveTruthRunner,
    signed,
    task_payload,
)
from v2.production_execution import (
    ProductionExecutionPermit,
    ProductionExecutionPermitStore,
)
from v2.production_coordinator import ProductionCoordinatorDisposition
from v2.provider_usage import ProviderSpendKind, ProviderUsageStore
from v2.ordinary_effects import OrdinaryEffectStore


DISPATCH=json.dumps({
    "decision":"DISPATCH",
    "lane":"Implementation",
    "objective":"Implement bounded feature",
    "stop_boundary":"merge boundary",
    "worker_tier":"LUNA",
    "allowed_paths":["docs/operations/**"],
    "reason":"bounded production-runtime fixture",
},separators=(",",":"))


def git(root: Path,*args: str) -> str:
    return subprocess.run(
        ["git",*args],cwd=root,check=True,text=True,capture_output=True
    ).stdout.strip()


def make_repo(root: Path) -> str:
    git(root,"init","-b","main")
    git(root,"config","user.email","test@example.invalid")
    git(root,"config","user.name","Skyforge R5C24 Test")
    (root/".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",encoding="utf-8"
    )
    p=root/"docs"/"operations"
    p.mkdir(parents=True)
    (p/"base.txt").write_text("base\n",encoding="utf-8")
    git(root,"add",".gitignore","docs/operations/base.txt")
    git(root,"commit","-m","base")
    return git(root,"rev-parse","HEAD")


class FakeClassifier:
    def __init__(self): self.calls=0
    def classify(self,*,request,root,config):
        from v2.classifier_provider import parse_classifier_response
        self.calls+=1
        return DISPATCH,parse_classifier_response(DISPATCH)


class FakeWorker:
    def __init__(self): self.calls=0
    def run(self,*,spec,worktree,config):
        self.calls+=1
        p=Path(worktree)/"docs"/"operations"/"r5c24-runtime.txt"
        p.write_text("production-runtime fixture\n",encoding="utf-8")
        return "bounded worker complete"


class Result:
    def __init__(self,stdout=""):
        self.stdout=stdout
        self.stderr=""


class CompositeRemote:
    def __init__(self,root:Path,base:str):
        self.root=root
        self.base=base
        self.live=LiveTruthRunner(main=base)
        self.remote_branch=None
        self.pr=None
        self.mutations=[]

    def __call__(self,args,**kwargs):
        args=list(args)
        # Permit checkout verification.
        if args==["git","rev-parse","HEAD"]:
            return Result(git(self.root,"rev-parse","HEAD")+"\n")

        # Fresh signed task authority + accepted main reads.
        if args[:2]==["gh","api"] and (
            args[2]=="repos/ni-da-ba/skyforge/issues/900"
            or args[2]=="repos/ni-da-ba/skyforge/issues/comments/12345"
            or args[2]=="repos/ni-da-ba/skyforge/commits/main"
        ):
            return self.live(args,**kwargs)

        if args[:2]==["gh","api"] and "/commits/" in args[2]:
            ref=args[2].split("/commits/",1)[1]
            from urllib.parse import unquote
            ref=unquote(ref)
            if ref=="main":
                return Result(self.base+"\n")
            if self.remote_branch is None:
                raise subprocess.CalledProcessError(
                    1,args,stderr="HTTP 422: No commit found for SHA"
                )
            return Result(self.remote_branch+"\n")

        if args[:2]==["git","push"]:
            spec=args[-1]
            sha,ref=spec.split(":refs/heads/",1)
            self.remote_branch=sha
            self.mutations.append(("push",ref,sha))
            return Result("")

        if args[:3]==["gh","pr","list"]:
            if self.pr is None:
                return Result("[]")
            return Result(json.dumps([self.pr]))

        if args[:3]==["gh","pr","create"]:
            def after(flag):
                return args[args.index(flag)+1]
            self.pr={
                "number":990,
                "state":"OPEN",
                "mergedAt":None,
                "headRefName":after("--head"),
                "headRefOid":self.remote_branch,
                "baseRefName":after("--base"),
                "title":after("--title"),
                "body":after("--body"),
            }
            self.mutations.append(("create_pr",990))
            return Result("https://example.invalid/pr/990\n")

        raise AssertionError(f"unexpected command: {args}")


def install_permit(root:Path,base:str):
    permit=ProductionExecutionPermit(
        accepted_main_sha=base,
        activation_decision_digest="a"*64,
        activation_disposition="READY_FOR_OPERATOR_REVIEW",
        operator_approved=True,
        legacy_writer_revoked=True,
        writer_authority="V2",
    )
    ProductionExecutionPermitStore.for_root(root).save(permit)
    return permit


def make_runtime(root:Path,base:str,remote,*,classifier=None,worker=None):
    return ProductionHostedRuntime(
        root,
        repo="ni-da-ba/skyforge",
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=SECRET,
        trusted_actors=("ni-da-ba",),
        classifier_daily_limit=2,
        worker_daily_limit=2,
        classifier_provider=classifier or FakeClassifier(),
        worker_provider=worker or FakeWorker(),
        runner=remote,
        day_provider=lambda:"2026-09-18",
    )


class ProductionPermitTest(unittest.TestCase):
    def test_production_runtime_refuses_missing_permit(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); base=make_repo(root); write_legacy(root)
            remote=CompositeRemote(root,base)
            with self.assertRaisesRegex(RuntimeError,"operator execution permit"):
                make_runtime(root,base,remote)

    def test_permit_checkout_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); base=make_repo(root); write_legacy(root)
            install_permit(root,"f"*40)
            remote=CompositeRemote(root,base)
            with self.assertRaisesRegex(RuntimeError,"does not match runtime checkout"):
                make_runtime(root,base,remote)


class ProductionCoordinatorIntegrationTest(unittest.TestCase):
    def test_exact_gated_chain_reaches_managed_draft_pr(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); base=make_repo(root); write_legacy(root)
            install_permit(root,base)
            classifier=FakeClassifier(); worker=FakeWorker()
            remote=CompositeRemote(root,base)
            app=make_runtime(
                root,base,remote,classifier=classifier,worker=worker
            )

            raw,headers=signed(task_payload(),delivery="r5c24-production")
            status,response=app.handle_webhook(headers=headers,raw=raw)
            self.assertEqual(status,202)
            self.assertTrue(response["task_authority_recorded"])

            phases=[]
            for _ in range(8):
                result=app.advance_production_once()
                phases.append((result.phase,result.disposition.value))
                if result.phase=="REMOTE_HANDOFF":
                    break

            self.assertEqual(
                [phase for phase,_ in phases],
                [
                    "TASK_CLAIM",
                    "PREFLIGHT",
                    "CLASSIFIER",
                    "ADMISSION",
                    "WORKER",
                    "LOCAL_COMMIT",
                    "REMOTE_HANDOFF",
                ],
            )
            self.assertEqual(classifier.calls,1)
            self.assertEqual(worker.calls,1)
            self.assertEqual(remote.mutations[0][0],"push")
            self.assertEqual(remote.mutations[1],("create_pr",990))

            from v2.production_execution import HostedManagedHandoffStore
            handoff=HostedManagedHandoffStore.for_root(root).load().handoff
            self.assertIsNotNone(handoff)
            self.assertEqual(handoff.pr_number,990)
            self.assertEqual(handoff.scope.base_sha,base)
            self.assertEqual(handoff.scope.expected_head_sha,remote.remote_branch)
            self.assertEqual(
                handoff.changed_paths,
                ("docs/operations/r5c24-runtime.txt",),
            )

            usage=ProviderUsageStore.for_root(root).load()
            self.assertEqual(
                usage.count(day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER),1
            )
            self.assertEqual(
                usage.count(day="2026-09-18",kind=ProviderSpendKind.WORKER),1
            )
            effects=OrdinaryEffectStore.for_root(root).load()
            self.assertEqual(len(effects.records),2)

            health=app.health_snapshot()
            self.assertEqual(health["runtime_mode"],"platform-v2-production-gated")
            self.assertTrue(health["mutation_authority"])
            self.assertTrue(health["worker_dispatch_enabled"])
            self.assertTrue(health["remote_effect_execution_enabled"])
            self.assertEqual(health["production_accepted_main"],base)

    def test_restart_after_classifier_does_not_spend_classifier_twice(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); base=make_repo(root); write_legacy(root)
            install_permit(root,base)
            classifier=FakeClassifier(); worker=FakeWorker()
            remote=CompositeRemote(root,base)
            first=make_runtime(
                root,base,remote,classifier=classifier,worker=worker
            )
            raw,headers=signed(task_payload(),delivery="r5c24-restart")
            first.handle_webhook(headers=headers,raw=raw)
            self.assertEqual(first.advance_production_once().phase,"TASK_CLAIM")
            self.assertEqual(first.advance_production_once().phase,"PREFLIGHT")
            self.assertEqual(first.advance_production_once().phase,"CLASSIFIER")
            self.assertEqual(classifier.calls,1)

            restarted=make_runtime(
                root,base,remote,classifier=classifier,worker=worker
            )
            result=restarted.advance_production_once()
            self.assertEqual(result.phase,"ADMISSION")
            self.assertEqual(classifier.calls,1)
            usage=ProviderUsageStore.for_root(root).load()
            self.assertEqual(
                usage.count(day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER),1
            )

    def test_daily_classifier_limit_blocks_without_provider_call(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); base=make_repo(root); write_legacy(root)
            install_permit(root,base)
            classifier=FakeClassifier(); remote=CompositeRemote(root,base)
            app=ProductionHostedRuntime(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
                classifier_daily_limit=1,
                worker_daily_limit=1,
                classifier_provider=classifier,
                worker_provider=FakeWorker(),
                runner=remote,
                day_provider=lambda:"2026-09-18",
            )
            usage=ProviderUsageStore.for_root(root)
            usage.reserve(
                day="2026-09-18",
                kind=ProviderSpendKind.CLASSIFIER,
                spend_id="different-request",
                daily_limit=1,
            )
            raw,headers=signed(task_payload(),delivery="r5c24-budget")
            app.handle_webhook(headers=headers,raw=raw)
            app.advance_production_once()
            app.advance_production_once()
            result=app.advance_production_once()
            self.assertEqual(result.phase,"CLASSIFIER")
            self.assertEqual(result.disposition,ProductionCoordinatorDisposition.WAIT)
            self.assertEqual(classifier.calls,0)


class IsolationTest(unittest.TestCase):
    def test_read_only_hosted_runtime_remains_worker_effect_incapable(self):
        root=Path(__file__).resolve().parent
        source=(root/"platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        for token in (
            "production_coordinator",
            "production_execution",
            "dormant_worker",
            "dormant_handoff_commit",
            "CodexWorkerProvider",
            "advance_committed_remote_handoff",
        ):
            self.assertNotIn(token,source)

    def test_production_entrypoint_cannot_create_operator_permit(self):
        root=Path(__file__).resolve().parent
        source=(root/"platform_v2_production_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn(".save(permit",source)
        self.assertNotIn("ProductionExecutionPermit(",source)
        self.assertNotIn("systemctl",source)


if __name__=="__main__":
    unittest.main()
