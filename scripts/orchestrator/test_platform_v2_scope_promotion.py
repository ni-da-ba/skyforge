import json
from dataclasses import replace
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from v2.context_package import build_context_package
from v2.context_retrieval import ExactPathScopeProposal, build_retrieval_record
from v2.events import DurableEvent
from v2.hosted_state import HostedIngressState, HostedStateStore
from v2.inbox import InboxState
from v2.objective_ingress import ObjectiveProposalStore, ObjectiveSourceReference
from v2.objective_intake import load_manifest
from v2.scope_promotion import PromotionDisposition, validate_promotion
from v2.task_authority import TaskAuthorityDisposition, parse_typed_task_directive

REPO_ROOT=Path(__file__).resolve().parents[2]
BASE_FILES=(
 'docs/agent-state/ORCHESTRATOR_ROADMAP.json','docs/agent-state/CURRENT_PROJECT_STATE.md',
 'docs/agent-state/PROGRAM_CHARTER.md','docs/agent-state/CROSS_LANE_CONTRACTS.md',
 'docs/agent-state/EXECUTION_BOUNDARIES.md','docs/agent-state/VALIDATION_POLICY.md',
 'docs/agent-state/IMPLEMENTATION_STATE.md','docs/agent-state/AUTHORSHIP_STATE.md',
)
ISSUE_FIELDS='number,title,body,comments,updatedAt,state'

def git(root,*args):
 return subprocess.check_output(['git','-C',str(root),*args],text=True).strip()

def prepare(root):
 for rel in BASE_FILES:
  dst=root/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(REPO_ROOT/rel,dst)
 files={
  'skyforge-world/src/main/java/HydrologyChannel.java':'class HydrologyChannel { // canonical retained water channel edge discharge\n}\n',
  'skyforge-world/src/test/java/HydrologyChannelTest.java':'class HydrologyChannelTest { // hydrology channel retained water provenance\n}\n',
  'docs/authorship/hydrology-channel.md':'canonical hydrology channel retained water provenance outlet discharge\n',
  'scripts/orchestrator/hydrology_admin.py':'# hydrology channel retained water\n',
 }
 for rel,text in files.items():
  p=root/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text)
 git(root,'init','-q'); git(root,'config','user.email','test@example.com'); git(root,'config','user.name','Test')
 git(root,'add','.'); git(root,'commit','-qm','fixture')

def write_state(root, *, gate=False):
 manifest=load_manifest(root)
 if gate:
  completed={node.node_id:node.max_runs for node in manifest.nodes if node.kind.value=='task'}
  blocked=('dr-human-exploration-review','dr-human-exploration-rereview')
 else:
  completed={
   'dr-00-canonical-specimen-lock':1,'dr-10-starting-cluster-closure':1,
   'dr-20-visible-hydrology':1,'dr-30-structure-reintegration':1,
   'dr-40-production-ecology':1,'dr-50-integrated-dressed-region':1,
   'dr-60-exploration-review-packet':1,
  }
  blocked=('dr-human-exploration-review',)
 state={
  'paused':False,'blocked_kind':None,'pending_worker':None,'pending_decision':None,
  'managed':{},'pending_events':[],'external_producer_claims':{},'human_gate_records':{},
  'roadmap':{
   'roadmap_id':manifest.roadmap_id,'manifest_fingerprint':manifest.fingerprint,
   'completed_runs':completed,'blocked_nodes':{key:{'reason':'fixture'} for key in blocked},
   'active':None,'claims_day':'2026-09-18','claims_today':0,
  },
 }
 d=root/'.skyforge-orchestrator'; d.mkdir(parents=True,exist_ok=True)
 text=json.dumps(state,sort_keys=True,indent=2)+'\n'; (d/'state.json').write_text(text); (d/'state.json.bak').write_text(text)

def capture_proposal(root, *, gate=False):
 write_state(root,gate=gate)
 source=ObjectiveSourceReference(
  'ni-da-ba/skyforge',935,7001 if not gate else 7002,'ni-da-ba',
  '2026-09-18T22:40:00Z','2026-09-18T22:40:00Z','Continue DR-70'
 )
 return ObjectiveProposalStore.for_root(root).capture(source=source,delivery_id='delivery-promotion',root=root).record

def issue_payload(*, state='OPEN', updated='2026-09-18T22:40:00Z'):
 return {
  'number':756,'title':'Authorize canonical coherent hydrology channel',
  'body':'Use exact accepted hydrology channel. Source is `skyforge-world/src/main/java/HydrologyChannel.java` and add tests.',
  'comments':[{'body':'Preserve retained water provenance and channel behavior.'}],
  'updatedAt':updated,'state':state,
 }

def runner_for(payload):
 def runner(args,**kwargs):
  expected=['gh','issue','view','756','--repo','ni-da-ba/skyforge','--json',ISSUE_FIELDS,'--jq=.']
  if args!=expected: raise AssertionError(args)
  return subprocess.CompletedProcess(args,0,stdout=json.dumps(payload),stderr='')
 return runner

def candidate_bundle(root, payload=None):
 prop=capture_proposal(root)
 package=build_context_package(root=root,proposal=prop)
 payload=payload or issue_payload()
 retrieval=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=runner_for(payload))
 return prop,package,retrieval,payload

class ScopePromotionTest(unittest.TestCase):
 def test_positive_candidate_freezes_existing_typed_authority_syntax(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); prop,package,retrieval,payload=candidate_bundle(root)
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(payload))
   self.assertEqual(result.disposition,PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST)
   self.assertEqual(result.blockers,())
   self.assertIsNotNone(result.draft)
   self.assertEqual(result.draft.issue_number,756)
   self.assertFalse(result.draft.auto_merge_eligible)
   self.assertTrue(result.draft.allowed_paths)
   parsed=parse_typed_task_directive(result.draft.body)
   self.assertEqual(parsed.disposition,TaskAuthorityDisposition.EXECUTABLE_V2)
   self.assertEqual(parsed.directive,result.draft.directive())
   self.assertIn(package.package_id,result.draft.body)
   self.assertIn(retrieval.retrieval_id,result.draft.body)

 def test_closed_or_changed_issue_revision_blocks(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); _,package,retrieval,payload=candidate_bundle(root)
   closed=issue_payload(state='CLOSED')
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(closed))
   self.assertEqual(result.disposition,PromotionDisposition.BLOCKED)
   self.assertIn('candidate issue is not open',result.blockers)
   self.assertIn('candidate issue revision changed since retrieval',result.blockers)

 def test_file_change_and_dirty_checkout_block(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); _,package,retrieval,payload=candidate_bundle(root)
   path=root/'skyforge-world/src/main/java/HydrologyChannel.java'; path.write_text(path.read_text()+'// drift\n')
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(payload))
   self.assertEqual(result.disposition,PromotionDisposition.BLOCKED)
   self.assertIn('tracked checkout is dirty',result.blockers)
   self.assertTrue(any('digest changed since retrieval' in item for item in result.blockers))

 def test_wildcard_or_protected_path_never_promotes(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); _,package,retrieval,payload=candidate_bundle(root)
   wildcard=replace(retrieval,scope_proposal=ExactPathScopeProposal(('skyforge-world/src/**',),'test wildcard'))
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=wildcard,gh_runner=runner_for(payload))
   self.assertEqual(result.disposition,PromotionDisposition.BLOCKED)
   self.assertTrue(any('exact paths only' in item for item in result.blockers))
   protected=replace(retrieval,scope_proposal=ExactPathScopeProposal(('scripts/orchestrator/hydrology_admin.py',),'test protected'))
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=protected,gh_runner=runner_for(payload))
   self.assertTrue(any('protected control-plane' in item for item in result.blockers))

 def test_exact_external_claim_conflict_blocks_but_other_issue_claim_does_not(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); _,package,retrieval,payload=candidate_bundle(root)
   store=HostedStateStore.for_root(root)
   store.save(HostedIngressState(legacy_projection={'external_claims':[{'issue_number':613,'state':'active','lane':'Implementation'}]}))
   ok=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(payload))
   self.assertEqual(ok.disposition,PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST)
   store.save(HostedIngressState(legacy_projection={'external_claims':[{'issue_number':756,'state':'active','lane':'Authorship'}]}))
   blocked=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(payload))
   self.assertIn('candidate issue already has an active external ownership claim',blocked.blockers)

 def test_pending_protected_task_blocks_promotion(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); _,package,retrieval,payload=candidate_bundle(root)
   event=DurableEvent(True,'other task','issue_comment','audit_signal',pr_number=999,source_id='x',signal_kind='task',signal_text='AUDIT NEW TASK')
   HostedStateStore.for_root(root).save(HostedIngressState(inbox=InboxState(pending_events=(event,))))
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=runner_for(payload))
   self.assertIn('another protected task authority is pending',result.blockers)

 def test_human_gate_rejects_before_remote_read(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); prop=capture_proposal(root,gate=True)
   package=build_context_package(root=root,proposal=prop)
   def forbidden(*args,**kwargs): raise AssertionError('remote issue read should not occur')
   retrieval=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=forbidden)
   result=validate_promotion(root=root,repo='ni-da-ba/skyforge',package=package,retrieval=retrieval,gh_runner=forbidden)
   self.assertEqual(result.disposition,PromotionDisposition.BLOCKED)
   self.assertIn('context package is not a promotable candidate task',result.blockers)

if __name__=='__main__': unittest.main()
