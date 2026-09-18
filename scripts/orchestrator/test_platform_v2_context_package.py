import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from v2.context_package import (
    ContextPackageDisposition,
    ContextPackageStore,
    build_context_package,
    package_proposal,
)
from v2.objective_ingress import (
    ObjectiveProposalRecord,
    ObjectiveProposalStore,
    ObjectiveSourceReference,
)
from v2.objective_intake import (
    compile_continue_objective,
    load_manifest,
    parse_objective,
)
from v2.roadmap_shadow import ShadowRoadmapState


REPO_ROOT = Path(__file__).resolve().parents[2]
CONTEXT_FILES = (
    'docs/agent-state/ORCHESTRATOR_ROADMAP.json',
    'docs/agent-state/CURRENT_PROJECT_STATE.md',
    'docs/agent-state/PROGRAM_CHARTER.md',
    'docs/agent-state/CROSS_LANE_CONTRACTS.md',
    'docs/agent-state/EXECUTION_BOUNDARIES.md',
    'docs/agent-state/VALIDATION_POLICY.md',
    'docs/agent-state/IMPLEMENTATION_STATE.md',
    'docs/agent-state/AUTHORSHIP_STATE.md',
)


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git','-C',str(root),*args], text=True).strip()


def prepare_repo(root: Path) -> None:
    for rel in CONTEXT_FILES:
        dst=root/rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO_ROOT/rel,dst)
    git(root,'init','-q')
    git(root,'config','user.email','test@example.com')
    git(root,'config','user.name','Test')
    git(root,'add','docs')
    git(root,'commit','-qm','fixture')


def write_legacy_roadmap(root: Path, *, completed: dict[str,int], blocked: dict[str,dict]) -> None:
    manifest=load_manifest(root)
    state={
        'paused':False,'blocked_kind':None,'pending_worker':None,'pending_decision':None,
        'managed':{},'pending_events':[],'external_producer_claims':{},'human_gate_records':{},
        'roadmap':{
            'roadmap_id':manifest.roadmap_id,
            'manifest_fingerprint':manifest.fingerprint,
            'completed_runs':completed,
            'blocked_nodes':blocked,
            'active':None,
            'claims_day':'2026-09-18','claims_today':0,
        },
    }
    d=root/'.skyforge-orchestrator'; d.mkdir(parents=True,exist_ok=True)
    text=json.dumps(state,sort_keys=True,indent=2)+'\n'
    (d/'state.json').write_text(text)
    (d/'state.json.bak').write_text(text)


def proposal(root: Path, *, completed: dict[str,int], blocked: tuple[str,...]) -> ObjectiveProposalRecord:
    manifest=load_manifest(root)
    state=ShadowRoadmapState(
        roadmap_id=manifest.roadmap_id,
        manifest_fingerprint=manifest.fingerprint,
        completed_runs=tuple(sorted(completed.items())),
        blocked_nodes=blocked,
        active=None,
    )
    request=parse_objective('Continue DR-70').request
    compiled=compile_continue_objective(request,manifest=manifest,state=state)
    return ObjectiveProposalRecord(
        source=ObjectiveSourceReference(
            repo='ni-da-ba/skyforge',issue_number=931,comment_id=1001,actor='ni-da-ba',
            created_at='2026-09-18T22:20:00Z',updated_at='2026-09-18T22:20:00Z',
            objective_text='Continue DR-70',
        ),
        delivery_id='delivery-context',
        compiled=compiled,
    )


class ContextPackageTest(unittest.TestCase):
    def test_human_gate_package_is_reproducible_and_has_no_mutation_scope(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare_repo(root)
            manifest=load_manifest(root)
            completed={node.node_id:node.max_runs for node in manifest.nodes if node.kind.value=='task'}
            blocked={
                'dr-human-exploration-review':{'reason':'historical'},
                'dr-human-exploration-rereview':{'reason':'current human gate'},
            }
            write_legacy_roadmap(root,completed=completed,blocked=blocked)
            prop=proposal(root,completed=completed,blocked=tuple(blocked))
            package=build_context_package(root=root,proposal=prop)
            self.assertEqual(package.disposition,ContextPackageDisposition.HUMAN_GATE)
            self.assertEqual(package.path_scope.disposition,'NOT_REQUIRED')
            self.assertEqual(package.node_id,'dr-human-exploration-rereview')
            self.assertTrue(package.tracked_clean)
            self.assertEqual(package.accepted_main_sha,git(root,'rev-parse','HEAD'))
            self.assertFalse(package.as_dict()['executable_task_authority'])
            paths={ref.path for ref in package.context_references}
            self.assertIn('docs/agent-state/IMPLEMENTATION_STATE.md',paths)
            self.assertIn('docs/agent-state/CROSS_LANE_CONTRACTS.md',paths)
            self.assertEqual(package.package_id,build_context_package(root=root,proposal=prop).package_id)

    def test_candidate_task_fails_closed_without_repository_owned_path_scope(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare_repo(root)
            completed={
                'dr-00-canonical-specimen-lock':1,
                'dr-10-starting-cluster-closure':1,
                'dr-20-visible-hydrology':1,
                'dr-30-structure-reintegration':1,
                'dr-40-production-ecology':1,
                'dr-50-integrated-dressed-region':1,
                'dr-60-exploration-review-packet':1,
            }
            blocked={'dr-human-exploration-review':{'reason':'historical changes required'}}
            write_legacy_roadmap(root,completed=completed,blocked=blocked)
            prop=proposal(root,completed=completed,blocked=tuple(blocked))
            self.assertEqual(prop.compiled.disposition.value,'CANDIDATE_TASK')
            self.assertEqual(prop.compiled.candidate_task.node_id,'dr-65-canonical-hydrology-authorship')
            package=build_context_package(root=root,proposal=prop)
            self.assertEqual(package.disposition,ContextPackageDisposition.SCOPE_UNRESOLVED)
            self.assertEqual(package.path_scope.disposition,'UNRESOLVED')
            self.assertEqual(package.path_scope.allowed_paths,())
            self.assertIn('broad lane/whole-repo inference is forbidden',package.path_scope.reason)
            self.assertEqual(package.issue_number,756)
            self.assertEqual(package.lane,'Authorship')
            self.assertTrue(package.objective)
            self.assertTrue(package.stop_boundary)
            paths={ref.path for ref in package.context_references}
            self.assertIn('docs/agent-state/AUTHORSHIP_STATE.md',paths)

    def test_package_capture_is_idempotent_and_frozen_by_proposal(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare_repo(root)
            manifest=load_manifest(root)
            completed={node.node_id:node.max_runs for node in manifest.nodes if node.kind.value=='task'}
            blocked={'dr-human-exploration-rereview':{'reason':'current'}}
            write_legacy_roadmap(root,completed=completed,blocked=blocked)
            prop=proposal(root,completed=completed,blocked=tuple(blocked))
            ObjectiveProposalStore.for_root(root).capture(source=prop.source,delivery_id=prop.delivery_id,root=root)
            first,created1=package_proposal(root=root)
            second,created2=package_proposal(root=root,proposal_id=first.proposal_id)
            self.assertTrue(created1)
            self.assertFalse(created2)
            self.assertEqual(first,second)
            self.assertEqual(len(ContextPackageStore.for_root(root).load().records),1)

    def test_tracked_dirty_checkout_blocks_authoritative_package(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); prepare_repo(root)
            manifest=load_manifest(root)
            completed={node.node_id:node.max_runs for node in manifest.nodes if node.kind.value=='task'}
            blocked={'dr-human-exploration-rereview':{'reason':'current'}}
            write_legacy_roadmap(root,completed=completed,blocked=blocked)
            prop=proposal(root,completed=completed,blocked=tuple(blocked))
            target=root/'docs/agent-state/CURRENT_PROJECT_STATE.md'
            target.write_text(target.read_text()+'\ndirty\n')
            package=build_context_package(root=root,proposal=prop)
            self.assertEqual(package.disposition,ContextPackageDisposition.BLOCKED)
            self.assertFalse(package.tracked_clean)


if __name__=='__main__':
    unittest.main()
