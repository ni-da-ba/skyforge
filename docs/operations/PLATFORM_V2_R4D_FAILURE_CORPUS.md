# Platform v2 R4D — Required failure/replay corpus

Parent migration: #767  
Execution tranche: #843

This document maps every minimum failure/replay scenario required by section 16 of the durable Platform-v2 migration plan to explicit deterministic or live-canary evidence.

The companion machine-readable matrix is:

`docs/agent-state/PLATFORM_V2_FAILURE_CORPUS.json`

## Release-4 interpretation

The first live mutation canary (#833 / PR #840) demonstrated the real fenced exact-SHA lifecycle, separate-process restart boundary, exact CI acceptance, merge, and post-merge idempotence.

Dangerous failure cases are exercised deterministically rather than by deliberately corrupting live GitHub state. R4D adds explicit named contract tests where prior coverage was only implicit, especially:

- webhook ordering cannot supersede current repository truth;
- reconciliation can classify current truth even when the triggering webhook was missed;
- provider/local quota exhaustion blocks without consuming an attempt;
- push/comment remote success followed by local completion-save failure reconciles without duplication;
- a second mutating controller cannot acquire the writer fence;
- stale restored acceptance state is invalidated by current head truth;
- a changed frozen task spec produces a different attempt and remote-effect identity.

## Exit rule

Release 4 is ready to close only after:

1. every matrix row is `PASS`;
2. the focused R4D contract test passes;
3. full CI passes;
4. Orchestrator Smoke passes;
5. the repository mutation gate remains disabled.

R4D itself grants no production mutation authority.
