# Asset Compiler Neutral Bars Topology v0.1

## Scope

This tranche introduces a backend-neutral realization primitive for thin cardinal-connectable bars/railings. It exists so WBY Create bars can eventually be selected for an explicit railing/bars semantic role without masquerading as glazing, wooden fencing, or structural timber.

## Semantic contract

A bars-capable target resource is:

- thin and neighbor-sensitive;
- cardinal-connectable in the horizontal plane;
- not glazing;
- not a fence by implication;
- not a full-cube support merely because it can connect to a supporting block.

The compiler derives north/east/south/west connectivity from realized neighbor geometry. A bars cell connects to another bars cell and to a neighbor that explicitly provides solid/full support. Waterlogging remains a target-state concern with the resource default unless semantic input explicitly requires otherwise.

`StructuralDetailMinecraftAdapter` preserves `bars` through semantic round-trip, performs this topology lowering, and reports bars as a distinct coarse partial-collision/neighbor-dependent shape class.

## WBY catalog state

The exact Wave C1 Create runtime already established the five-boolean state surface for `create:andesite_bars`, `create:brass_bars`, and `create:copper_bars`. With generic bars topology implemented, those three resources are now present in the WBY capability catalog with `status: cataloged` and capabilities `bars`, `neighbor_sensitive`, and `thin`.

They are still non-selectable. The live exact-artifact registry proof now requires all 11 catalog resources to match their declared contracts, including all three bars, while ladders/scaffolds/girder remain observation-only.

## Boundary rules

This capability does not change any current Guild semantic role. Existing `window`, `seating_detail`, and timber `structural_frame` intents remain unchanged. In particular, `fence` is not silently reinterpreted as `bars` and a timber frame is not reinterpreted as a metal girder.

Activation of a Create bar variant remains gated on an upstream architecture explicitly emitting a neutral railing/bars intent. Until then, `wby_c1_create_registry()` excludes all three bar resources and current Guild output cannot change because of this tranche.

## Validation

Synthetic adapter tests prove bars-to-bars and bars-to-solid-support connectivity, no glazing cross-connection, distinct partial-collision classification, and preservation of the `bars` capability through semantic round-trip. WBY catalog tests separately prove the three Create bars remain excluded from the active resolver registry.

No ordinary vanilla Guild palette entry was added, and no aircraft-owned contract is touched.
