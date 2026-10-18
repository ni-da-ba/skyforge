# Proposed development roadmap — 27 September 2026

**Reference only; dispatch disabled.** Source snapshot: `a50dc8f22eb4b03b588962739db45d5b9966f904`.
Read [persistence limits](PERSISTENCE.md) and [decisions](DECISIONS.md). The full machine-readable contract in [ROADMAP.json](ROADMAP.json) carries exact acceptance criteria, scoped candidates, validation sources, stop conditions, completion records and dispatch requirements. [Schema](ROADMAP.schema.json).

Hydrology and atmosphere remain with their developers. This dated graph does not replace their active issues, source or current evidence. Source-bound adoption remains gated at M01. A node is not schedulable merely because it appears here: verify dependencies, human gates, exclusive ownership, bounded issue and exact validation surface.

## Dependency and ownership map

| Node | Phase | Lane | Prerequisites | Observed state | Work |
| --- | --- | --- | --- | --- | --- |
| G01 | P2 | Audit | None | NEEDS_RECONCILIATION | Repair startup authority and current frontiers |
| G02 | P2 | Audit | None | NEEDS_RECONCILIATION | Recover verifiable ownership and durable producer handoffs |
| I01 | P2 | Implementation | None | OPEN_EXISTING | Close ecology scheduling nondeterminism |
| H01 | P2 | Authorship | None | RUNNING_EXTERNAL | Make refined hydrology authority exact |
| H02 | P2 | Authorship | H01 | PLANNED | Discretize the solved water plan |
| H03 | P2 | Implementation | H01, H02, I01 | PLANNED | Exercise reset hydrology through the real lifecycle |
| H04 | P2 | Authorship | H01 | PLANNED | Resolve remaining hydrology capability classes |
| H05 | P2 | Implementation | H03, H04 | PLANNED | Prepare a reviewable DR-70 material delta |
| HG-DR70 | P2 | Director | H05 | WAIT_DEPENDENCY | Accept the reset physical-world specimen |
| A01 | P2 | Implementation | G02 | LOCAL_PORT_PENDING_DURABILITY | Complete glider/hawk consumer A/B |
| AG-A4MC | P25 | Director | None | WAIT_EXTERNAL | Select a legitimate production terrain-provider dependency |
| A02 | P25 | Implementation | AG-A4MC | PLANNED | Qualify the production atmosphere artifact |
| A03 | P25 | Content | A01, A02 | PLANNED | Close atmosphere consumer behavior |
| M01 | P25 | Audit | HG-DR70, G01, G02 | WAIT_DEPENDENCY | Adopt the coherent source-bound roadmap |
| W01 | P25 | Content | M01 | PLANNED | Define the minimum intentional alpha stack |
| HG-TARGETS | P25 | Director | W01 | WAIT_DEPENDENCY | Set supported environment and measurable product budgets |
| W02 | P25 | Implementation | W01, A02, HG-DR70 | PLANNED | Converge selected adapters and authority ownership |
| W03 | P25 | Implementation | W02 | PLANNED | Prove one coherent island |
| W04 | P25 | Implementation | W03, A03 | PLANNED | Prove a cluster |
| W05 | P25 | Content | W04 | PLANNED | Prove a province substrate |
| W06 | P25 | Implementation | W05 | PLANNED | Prove coarse-world continuation |
| W07 | P25 | Implementation | W06, HG-TARGETS | PLANNED | Accept the combined-stack compatibility/performance baseline |
| W08 | P25 | Audit | W07 | PLANNED | Close Cohesive World and admit Bootstrap |
| B01 | P3 | Content | W08 | PLANNED | Close survival-to-first-flight resources and recovery |
| B02 | P3 | Implementation | None | OPEN_EXISTING | Qualify minimum practical aircraft |
| B03 | P3 | Implementation | None | OPEN_EXISTING | Close the selected workshop interaction |
| B04 | P3 | Implementation | None | OPEN_EXISTING | Qualify physical freight custody |
| B05 | P3 | Implementation | B01, B02, B03, B04, A03 | PLANNED | Assemble the complete minimum player slice |
| HG-PLAY | P3 | Director | B05 | WAIT_DEPENDENCY | Accept Bootstrap play and recovery |
| V01 | P4 | Implementation | HG-PLAY | PLANNED | Converge the supported sky/render path |
| V02 | P4 | Authorship | HG-PLAY, A03 | PLANNED | Converge regional atmosphere semantics |
| HG-AUDIO | P4 | Director | W08 | WAIT_DEPENDENCY | Choose alpha audio presentation scope |
| V03 | P4 | Music | HG-AUDIO | PLANNED | Deliver the required audio slice |
| V04 | P4 | Presentation | V01, V02, V03 | PLANNED | Prepare truthful alpha presentation |
| L01 | P45 | Implementation | V04, HG-TARGETS | PLANNED | Create the reproducible alpha candidate |
| L02 | P45 | Implementation | L01 | PLANNED | Qualify release integration and resilience |
| HG-ALPHA | P45 | Director | L02 | WAIT_DEPENDENCY | Accept systems-complete, content-light alpha |
| X51 | P5 | Content | HG-ALPHA | DORMANT | Regional economy and civilization breadth |
| X61 | P6 | Content | HG-ALPHA | DORMANT | Mature industry and computing |
| X71 | P7 | Content | HG-ALPHA | DORMANT | Adventure and ecology breadth |
| X81 | P8 | Content | HG-ALPHA | DORMANT | High sky and End progression |
| X91 | P9 | Content | HG-ALPHA | DORMANT | Full-experience release convergence |
| XX1 | PX | Content | HG-ALPHA | DORMANT | Optional Lower Sea, Deep and second backend |
| O01 | P2 | Audit | G02 | PLANNED | Reduce CI/reconstruction cost without weakening evidence |
| O02 | P25 | Audit | G02 | PLANNED | Verify merge policy and supported contributor workflow |
| O03 | P25 | Audit | G01 | PLANNED | Maintain architecture and evidence affordably |
| HG-RIVER-PREVIEW | P2 | Director | H03 | WAIT_DEPENDENCY | Early ordinary-river visual feedback |

## Exact acceptance and dispatch

The complete checklist is in [ROADMAP.json](ROADMAP.json): each node has `objective`, `acceptance` with stable criterion IDs, `scope_candidates`, `stop_conditions`, `validation`, `dispatch_rule` and issue/evidence references. Its top-level selection and completion contracts govern decomposition. Read those fields before forming a bounded issue. The map above is a navigation view, not sufficient dispatch authority.
