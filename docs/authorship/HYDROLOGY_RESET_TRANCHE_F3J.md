# Hydrology reset tranche F3J — terminal CASCADE coupling at explicit edge outlets

**Status:** machine-evidence tranche in progress under issue #1084  
**Depends on:** accepted F3C/F3D/F3E/F3H/F3I boundaries  
**Terrain, water, voxel, and Minecraft mutation:** none

## Purpose

F3H/F3I can jointly solve an authored CASCADE that begins at a confluence leg when its remote boundary is adjacent to an ordinary profile. A generated key-632 reach (710→225) instead starts at a confluence and runs to a semantic terminal explicitly classified as an `EDGE_OUTLET`. Treating that outlet as an unknown transition indefinitely prevents the existing shared-node solve from reaching F3D/F3E, even though the outlet is already an authored free boundary.

F3J carries this narrowly supported terminal case through the existing compatibility, ordinary-span, and assembly evidence.

## Boundary and equations

The upstream confluence node remains the shared head variable used by F3H. Ordinary incident legs retain their existing D2 pointwise head envelopes and grade constraints. The terminal CASCADE's downstream endpoint uses a CASCADE-class D2 pointwise head envelope at the authored edge outlet.

`0 <= H_node - H_edge <= D_authored`

where `D_authored` is the existing sum of positive watershed surface-potential drops over the owned CASCADE profiles, converted by the descriptor relief budget. The existing deterministic bounded QP remains the solver. No ordinary grade is imposed across the authored drop itself.

F3J admits this remote endpoint only when the transition skeleton's terminal-fate planner returns the exact reach end as `EDGE_OUTLET`, and the cascade interval reaches that semantic end. Retained-water, wetland, unresolved, missing, or otherwise unsupported terminal ownership remains fail-closed. Source-side CASCADEs and all other coupled boundaries remain deferred unless an existing joint contract handles them.

## Generated regression

The production-derived key-632 fixture checks the confluence/CASCADE/edge-outlet outcome, authored drop bound, QP residual, admission provenance, and that F3D/F3E consume the admitted transition rather than leaving the reach transition-deferred. The fixed corpus and the full hydrology reset workflow remain the machine acceptance boundary; test results are pending until exact-head GitHub Actions completes.

## Limits

F3J does not relax corridor, D2, grade, basin, or geomorphic qualification; change watershed semantics; authorize terrain or Minecraft realization; or make key 287 reviewable. The downstream transition-terrain and DR-70 product gates remain in force.
