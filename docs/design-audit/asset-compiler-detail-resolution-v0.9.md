# Asset Compiler Detail Resolution v0.9

Status: bounded implementation proof; in-game acceptance still required.

v0.9 is deliberately downstream of the v0.8 architectural-articulation stage. It does not
re-open Guild massing, room layout, proportion profile, history semantics, or the provisional
blue/brass palette. It resolves accepted geometry into a more coherent Minecraft-scale block
vocabulary after the v0.8 in-game review.

## Review findings addressed

1. The west/secondary wall read as an unresolved flat plane.
2. Public lanterns consumed cells that should remain part of the structural wall/post system.
3. Working-bay doors and the glass directly above them lacked a visual separator.
4. Several exposed block terminations wanted bounded stair/slab smoothing rather than additional
   decorative density.

## v0.9 treatments

- Existing west-side windows are recessed one block and receive complete reveal logic.
- The west face receives a quiet base/body/crown hierarchy: shallow masonry plinth, projected
  structural pilasters on the existing rhythm, and a continuous eave band.
- Public facade lanterns are moved below the entrance canopy; the structural cells they previously
  replaced are restored.
- Working service portals become a single vertical composition:
  doors -> shallow timber transom rail -> recessed transom -> structural head.
- Dark-oak and stone-brick stairs are used only at exposed termination/corner conditions:
  public plinth corners, the southwest plinth transition, and public/working canopy corners.

## Invariants

- No change to semantic anchors or interior circulation.
- No change to v0.8 reference-profile or controlled-history semantics.
- Guild blue/brass realization remains provisional.
- Stair usage is bounded to explicit corner/termination modules; it is not random decoration.
- Exterior lighting may not replace required wall/post structure.

## Human gate

Inspect the generated v0.9 structure in Minecraft. Prioritize:

- west-side elevation at eye level and three-quarter angle;
- public entrance lantern mounts and restored posts;
- repair/freight portal door-rail-transom relationship;
- stair-resolved plinth and canopy corners;
- whether added detail remains restrained rather than noisy.
