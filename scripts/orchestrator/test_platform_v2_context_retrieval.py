import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from v2.context_package import build_context_package
from v2.context_retrieval import (
    ContextRetrievalDisposition,
    ISSUE_FIELDS,
    build_retrieval_record,
    validate_issue_read_command,
)
from v2.objective_ingress import ObjectiveProposalRecord,ObjectiveSourceReference
from v2.objective_intake import compile_continue_objective,load_manifest,parse_objective
from v2.roadmap_shadow import ShadowRoadmapState

REPO_ROOT=Path(__file__).resolve().parents[2]
BASE_FILES=(
 'docs/agent-state/ORCHESTRATOR_ROADMAP.json','docs/agent-state/CURRENT_PROJECT_STATE.md',
 'docs/agent-state/PROGRAM_CHARTER.md','docs/agent-state/CROSS_LANE_CONTRACTS.md',
 'docs/agent-state/EXECUTION_BOUNDARIES.md','docs/agent-state/VALIDATION_POLICY.md',
 'docs/agent-state/IMPLEMENTATION_STATE.md','docs/agent-state/AUTHORSHIP_STATE.md',
)

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
  '.github/workflows/hydrology.yml':'name: hydrology channel retained water\n',
  'skyforge-model/src/main/java/Unrelated.java':'class Unrelated {}\n',
 }
 for rel,text in files.items():
  p=root/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text)
 git(root,'init','-q'); git(root,'config','user.email','test@example.com'); git(root,'config','user.name','Test')
 git(root,'add','.'); git(root,'commit','-qm','fixture')


def write_legacy_state(root, manifest, completed, blocked):
 state={
  'paused':False,'blocked_kind':None,'pending_worker':None,'pending_decision':None,
  'managed':{},'pending_events':[],'external_producer_claims':{},'human_gate_records':{},
  'roadmap':{
   'roadmap_id':manifest.roadmap_id,'manifest_fingerprint':manifest.fingerprint,
   'completed_runs':completed,
   'blocked_nodes':{key:{'reason':'fixture'} for key in blocked},
   'active':None,'claims_day':'2026-09-18','claims_today':0,
  },
 }
 d=root/'.skyforge-orchestrator'; d.mkdir(parents=True,exist_ok=True)
 text=json.dumps(state,sort_keys=True,indent=2)+'\n'
 (d/'state.json').write_text(text); (d/'state.json.bak').write_text(text)

def candidate_proposal(root):
 manifest=load_manifest(root)
 completed={
  'dr-00-canonical-specimen-lock':1,'dr-10-starting-cluster-closure':1,
  'dr-20-visible-hydrology':1,'dr-30-structure-reintegration':1,
  'dr-40-production-ecology':1,'dr-50-integrated-dressed-region':1,
  'dr-60-exploration-review-packet':1,
 }
 blocked=('dr-human-exploration-review',)
 write_legacy_state(root,manifest,completed,blocked)
 state=ShadowRoadmapState(manifest.roadmap_id,manifest.fingerprint,tuple(sorted(completed.items())),blocked,None)
 compiled=compile_continue_objective(parse_objective('Continue DR-70').request,manifest=manifest,state=state)
 return ObjectiveProposalRecord(
  ObjectiveSourceReference('ni-da-ba/skyforge',933,5001,'ni-da-ba','2026-09-18T22:30:00Z','2026-09-18T22:30:00Z','Continue DR-70'),
  'delivery-1',compiled,
 )

def human_gate_proposal(root):
 manifest=load_manifest(root)
 completed={node.node_id:node.max_runs for node in manifest.nodes if node.kind.value=='task'}
 blocked=('dr-human-exploration-review','dr-human-exploration-rereview')
 write_legacy_state(root,manifest,completed,blocked)
 state=ShadowRoadmapState(manifest.roadmap_id,manifest.fingerprint,tuple(sorted(completed.items())),blocked,None)
 compiled=compile_continue_objective(parse_objective('Continue DR-70').request,manifest=manifest,state=state)
 return ObjectiveProposalRecord(
  ObjectiveSourceReference('ni-da-ba/skyforge',929,5002,'ni-da-ba','2026-09-18T22:31:00Z','2026-09-18T22:31:00Z','Continue DR-70'),
  'delivery-2',compiled,
 )

def fake_issue(updated='2026-09-18T22:00:00Z'):
 payload={
  'number':756,'title':'Authorize canonical coherent hydrology channel',
  'body':'Use exact accepted hydrology channel and retained water. Relevant source is `skyforge-world/src/main/java/HydrologyChannel.java` and preserve outlet provenance.',
  'comments':[{'body':'Add deterministic hydrology channel tests and avoid incidental edge discharge.'}],
  'updatedAt':updated,'state':'CLOSED',
 }
 def runner(args,**kwargs):
  expected=['gh','issue','view','756','--repo','ni-da-ba/skyforge','--json',ISSUE_FIELDS,'--jq=.']
  if args!=expected: raise AssertionError(args)
  return subprocess.CompletedProcess(args,0,stdout=json.dumps(payload),stderr='')
 return runner

class ContextRetrievalTest(unittest.TestCase):
 def test_exact_issue_read_shape_is_allowlisted(self):
  expected=('gh','issue','view','756','--repo','ni-da-ba/skyforge','--json',ISSUE_FIELDS,'--jq=.')
  self.assertEqual(validate_issue_read_command(expected,repo='ni-da-ba/skyforge',issue_number=756),expected)
  with self.assertRaises(ValueError):
   validate_issue_read_command(('gh','issue','view','755','--repo','ni-da-ba/skyforge','--json',ISSUE_FIELDS,'--jq=.'),repo='ni-da-ba/skyforge',issue_number=756)

 def test_candidate_retrieval_is_deterministic_and_proposes_exact_safe_paths(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root)
   package=build_context_package(root=root,proposal=candidate_proposal(root))
   self.assertEqual(package.disposition.value,'SCOPE_UNRESOLVED')
   first=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=fake_issue())
   second=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=fake_issue())
   self.assertEqual(first,second); self.assertEqual(first.retrieval_id,second.retrieval_id)
   self.assertEqual(first.disposition,ContextRetrievalDisposition.SCOPE_PROPOSED)
   self.assertTrue(first.slices)
   paths=[s.path for s in first.slices]
   self.assertEqual(paths[0],'skyforge-world/src/main/java/HydrologyChannel.java')
   self.assertTrue(first.slices[0].explicit_issue_path)
   scope=set(first.scope_proposal.paths)
   self.assertIn('skyforge-world/src/main/java/HydrologyChannel.java',scope)
   self.assertIn('skyforge-world/src/test/java/HydrologyChannelTest.java',scope)
   self.assertNotIn('scripts/orchestrator/hydrology_admin.py',scope)
   self.assertNotIn('.github/workflows/hydrology.yml',scope)
   self.assertFalse(first.scope_proposal.as_dict()['authoritative'])
   self.assertFalse(first.as_dict()['executable_task_authority'])

 def test_issue_revision_changes_retrieval_identity(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); package=build_context_package(root=root,proposal=candidate_proposal(root))
   a=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=fake_issue('2026-09-18T22:00:00Z'))
   b=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=fake_issue('2026-09-18T22:01:00Z'))
   self.assertNotEqual(a.issue_observation.digest,b.issue_observation.digest)
   self.assertNotEqual(a.retrieval_id,b.retrieval_id)

 def test_human_gate_is_inspection_only_and_does_not_read_issue(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); package=build_context_package(root=root,proposal=human_gate_proposal(root))
   def forbidden(*args,**kwargs): raise AssertionError('gh must not be called for human gate')
   record=build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=forbidden)
   self.assertEqual(record.disposition,ContextRetrievalDisposition.INSPECTION_ONLY)
   self.assertEqual(record.scope_proposal.paths,())
   self.assertIsNone(record.issue_observation)

 def test_malformed_issue_truth_fails_closed(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td); prepare(root); package=build_context_package(root=root,proposal=candidate_proposal(root))
   def bad(args,**kwargs): return subprocess.CompletedProcess(args,0,stdout='{bad',stderr='')
   with self.assertRaisesRegex(ValueError,'malformed JSON'):
    build_retrieval_record(root=root,repo='ni-da-ba/skyforge',package=package,gh_runner=bad)

if __name__=='__main__': unittest.main()
