# Platform v2 R5C29 — Final staged nonproduction acceptance rehearsal

Status: **ACCEPTED IN REHEARSAL; MIGRATION CODE FREEZE CANDIDATE**

The final nonproduction process rehearsal ran from accepted main `c90ae2df5c0d978b823182760a16eddff7306585`.

The disposable supervisor observed the complete forward sequence `LEGACY -> NONE -> V2` and rollback sequence `V2 -> NONE -> LEGACY`. Both no-writer intervals were observable, no overlap occurred, v2 became observable only after legacy death, and legacy recovery succeeded only after v2 death.

Focused operator/cutover/rehearsal/operability validation passed 35/35. R5C28 had already closed the full orchestrator sweep at 960/960. The production unit contract remains separately named, activation-evidence-bound, boot-disabled during staging, and conflicting with the legacy unit. Rollback still requires legacy startup reconciliation before dispatch.

No live systemd, Caddy, provider, writer, sudo, or production GitHub effect was exercised. Production authority remains `LEGACY`. DR-70 remains `DEFERRED_NOT_PASSED`.

## Freeze disposition

If exact-head CI and Orchestrator Smoke accept this evidence, migration implementation is frozen unless a concrete defect is discovered. There is no planned R5C30 implementation tranche.

The remaining operation is production cutover, not architecture work:

1. synchronize the live checkout to the final accepted migration SHA without changing writer authority;
2. stage the v2 unit and final activation facts from that exact clean checkout;
3. run the read-only operator preflight;
4. explicitly clear the deferred DR-70 production hold;
5. obtain explicit privileged operator authorization;
6. execute `LEGACY -> NONE -> V2`;
7. observe production v2 and retain the tested rollback path through the initial observation window.

R5C29 acceptance does not itself authorize steps 4-7.
