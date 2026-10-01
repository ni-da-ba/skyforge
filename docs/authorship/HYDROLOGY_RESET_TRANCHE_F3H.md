# Hydrology reset tranche F3H — joint confluence/CASCADE head compatibility

**Status:** machine-evidence tranche; CI validation pending  
**Governing authority:** issue #1084  
**Depends on:** F3B confluence head compatibility, F3C CASCADE head compatibility, F3E terminal-component accounting  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F3B correctly deferred confluence legs whose finite transition boundary is owned by an authored CASCADE. F3H adds a bounded joint solve for one eligible overlap: a single CASCADE run crosses an outgoing/incoming confluence finite boundary, while its opposite boundary is adjacent to an ordinary profile. The key-632 fixture does not exercise this case: its outgoing CASCADE ends at the semantic reach terminal, so F3H correctly leaves it deferred. The regression searches the existing confluence corpus in stable order for a genuine supported case and fails if none exists.

## Mathematical contract

The solve uses one shared confluence head variable (h_n), one head variable for each remaining ordinary confluence leg, and one head at the ordinary-side CASCADE boundary. Every ordinary endpoint remains inside the same D2-derived pointwise envelope used by F3B/F3C. Ordinary confluence-leg head differences retain their directional maximum-grade bounds.

For the CASCADE run, the downstream head may not exceed its upstream head, and the total drop is bounded by the exact authored watershed surface-potential drop multiplied by the existing relief budget. The bounded weighted least-squares problem is solved by the existing deterministic QP solver. The outcome constructor independently checks that the reported head difference equals the solved drop and remains within the authored bound.

No D2 limit, corridor, profile, watershed, relief, terrain, basin, or backend contract is relaxed. The result is evidence only and is not consumed as terrain or Minecraft authority.

## Fail-closed boundary

F3H does not compose multiple CASCADE overlaps, a CASCADE spanning both semantic reach endpoints, missing ordinary-side endpoints, additional CASCADE ownership on another incident leg, or basin/terminal coupling. These cases remain explicitly deferred. Empty shared envelopes are reported infeasible; solver numerical failure is surfaced, never converted to success. Unsolved outcomes expose no partial solved heads.

## Regression evidence

The deterministic regression probes the existing confluence corpus (islands 241, 632, 287, 649, 83, 77, and 512) until it finds a supported solved overlap, then repeats that exact solve and compares reported variables and residuals. It checks shared-node bounds, non-negative authored-bounded CASCADE drop, and QP primal residual. Key 632 is expected to remain deferred because its CASCADE is terminal-ended. GitHub Actions is the only authority for this test and build.

## Acceptance boundary

Passing this tranche establishes only that the bounded joint head compatibility case is mathematically solved under existing F3B/F3C constraints. It does not admit the complete terminal component, authorize terrain changes, qualify a rendered specimen, or satisfy human visual review.
