# Platform v2 R5C6 — Ordinary hosted-control pipeline

Status: **COMPOSED — PRODUCTION ACTIVATION STILL DISABLED**

Parent migration: #767  
R5C6 authority: #862  
Predecessor: R5C5 #860 / PR #861

## Purpose

R5C6 composes the accepted Release-5 components into one restart-safe ordinary-task pipeline. It does not yet wire that pipeline into the production hosted HTTP runtime.

The composed path is:

```text
semantic repository input
  -> durable ephemeral classifier proposal (R5C5)
  -> repository-authoritative admission (R5C4)
  -> exact frozen task / attempt / worker identity
  -> v2 writer fence
  -> deterministic isolated worktree (R5C3)
  -> durable disposable worker provider (R5C3)
  -> bounded workspace commit (R5C2)
  -> durable push / draft PR / optional controller comment (R5C1 + R5C2)
  -> managed draft-PR handoff
```

Managed merge remains separate and still requires the accepted exact evidence/review/CI transition to `MERGE_ELIGIBLE`.

## Authority

The classifier does not create task authority. Every pipeline request carries a `RepositoryTaskAuthority` owned by repository state.

A DISPATCH proposal reaches a worker only if R5C4 proves:

- classifier proposal freshness;
- exact lane/objective/stop-boundary agreement;
- issue authority agreement;
- classifier path scope is equal to or narrower than repository authority;
- no governing issue is externally/manually owned;
- provider/local quota permits one attempt.

NOOP, HUMAN_GATE, MERGE, stale proposals, ownership conflicts, and quota blocks never cross the worktree/provider boundary.

## Durable composition record

R5C6 adds:

`.skyforge-platform-v2/ordinary-pipelines.json(.bak)`

Each record binds:

- classifier request/run identity;
- repository authority digest;
- classifier decision digest;
- admission digest;
- frozen task-spec hash;
- task-attempt identity;
- worker-spec digest;
- worker-run identity;
- handoff digest and PR number when complete.

Lower layers retain ownership of their more detailed durable ledgers. R5C6 references those identities rather than duplicating their effect state.

## Restart semantics

- completed classifier proposals are reused;
- classifier RUNNING after process loss becomes recovery-required without another call;
- classifier failure remains durable;
- R5C3 worker state owns provider interruption/recovery;
- HANDOFF_READY/ALREADY_READY worker state proceeds to R5C2 without another provider call;
- R5C1/R5C2 effects reconcile exact remote truth rather than duplicate push/PR/comment effects;
- completed pipeline identity is stable across repeated orchestration calls.

## Writer exclusion

Classifier/admission work is read-only and happens outside the writer fence.

Before any worktree/provider/remote-effect stage, R5C6 acquires the same dedicated Platform-v2 writer-fence namespace used by the accepted canary design. Fence contention returns BLOCKED before worker handoff.

Production activation still requires the accepted Release-5 `LEGACY -> NONE -> V2` handoff. The existence of R5C6 does not let v2 race the current legacy controller.

## Production boundary

`platform_v2_hosted_runtime.py` is intentionally unchanged by R5C6 and does not import `ordinary_pipeline`.

Therefore:

- hosted production worker dispatch remains disabled;
- hosted production remote effects remain disabled;
- ordinary v2 production mutation authority remains false;
- #613/#754 external ownership and DR-70 human gates remain untouched.

The next tranche must explicitly compose this pipeline into the hosted runtime, define repository task-authority hydration/event selection, and pass cutover readiness before activation.
