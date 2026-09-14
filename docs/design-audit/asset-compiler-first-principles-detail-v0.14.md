# ASSET-001 v0.14 — first-principles detail/articulation pass

## Human-gate input

The v0.12 mathematically regularized structure and v0.13 clean-room first-principles structure both read strongly in Minecraft. The important result was that v0.13 reached substantially better structural and spatial coherence on its first realization than the manually evolved lineage did on its first draft. The next pass therefore keeps v0.13 as the geometry authority and adds bounded richness downstream instead of reintroducing hand-authored corrective geometry.

## Mathematical authorities

The detail stage uses three established ideas where they fit the discrete Minecraft domain.

1. **Linear assignment.** Public and working fixtures are assigned to distinct structurally supported candidate sites using the Hungarian minimum-cost assignment algorithm (Kuhn, 1955). The cost matrix is semantic Manhattan distance from desired task locations to legal attachment sites. This prevents greedy fixture placement from colliding or producing globally poor assignments.
2. **Farthest-first / maximin spacing.** Sparse brackets and controlled-history samples use deterministic minimum-distance maximin selection. This is the finite-grid analogue of blue-noise / Poisson-disk spacing and is closely related to Gonzalez's farthest-first traversal for metric k-center, which has a 2-approximation guarantee for the k-center objective under a metric. The compiler additionally enforces an explicit minimum spacing threshold rather than claiming stochastic Poisson-disk statistics.
3. **Graph revalidation.** The detail pass is accepted only if the public circulation/visibility graph remains valid and roof-frame targets remain connected to grounded structural support. These are architectural-legibility constraints, not load-capacity or FEA claims.

## Lowering boundary

```text
v0.13 semantic program + optimized first-principles geometry
    -> layered facade articulation
    -> minimum-distance sparse history / brackets
    -> minimum-cost fixture assignment
    -> bounded interior furnishing detail
    -> roof identity detail derived from the accepted roof field
    -> circulation + visibility + structural revalidation
    -> Minecraft blocks / NBT / QA
```

The pass does **not** re-solve bay count, bay width, wall height, hall depth, wing width, roof rise, roof overhang, or window width. Those remain outputs of the v0.13 optimizer.

## v0.14 additions

- Continuous plinth and eave-fascia layers that only occupy free detail planes and therefore cannot consume glazing/doors.
- Deterministically spaced eave brackets at structural stations.
- Sparse north/west repair-history material substitutions with a four-block minimum Euclidean spacing.
- One restrained working-face service marker.
- Public and working hanging lights globally assigned to legal supported sites by Hungarian optimization.
- Counter cap, bench backs, records caps, repair/freight wall details and a passable institutional rug.
- Compact roof signal placed from the solved public ridge station and actual roof height.

## Acceptance invariants

- Base first-principles generation authority remains unchanged.
- Detail block growth is bounded by `detailPass.maxAddedBlockFraction` (0.14 in the bootstrap specimen).
- Controlled history obeys its minimum spacing contract.
- Fixture assignments are unique legal sites.
- Entrance-side public graph remains fully connected, the service counter remains reachable/visible, and path stretch stays bounded.
- Roof-frame target graph remains grounded.
- Minecraft structure NBT and the standard visual QA bundle export deterministically.

## Scope / non-claims

The maximin selector is intentionally deterministic and finite-domain; it is *blue-noise / Poisson-disk-inspired*, not a claim of exact stochastic Poisson-disk sampling. Structural connectivity checks legibility only. Palette remains provisional. The purpose of v0.14 is to test whether mathematically coherent first-principles structure can accept materially richer architectural detail without regressing into patch-on-patch geometry.
