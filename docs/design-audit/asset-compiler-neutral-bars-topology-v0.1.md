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

The compiler must derive north/east/south/west connectivity from realized neighbor geometry. A bars cell connects to another bars cell and to a neighbor that explicitly provides solid/full support. Waterlogging remains a target-state concern with the resource default unless semantic input explicitly requires otherwise.

## Boundary rules

This capability does not change any current Guild semantic role. Existing `window`, `seating_detail`, and timber `structural_frame` intents remain unchanged. In particular, `fence` is not silently reinterpreted as `bars` and a timber frame is not reinterpreted as a metal girder.

Create bars remain non-selectable until both this generic topology contract is implemented/tested and an upstream architecture actually emits a neutral railing/bars intent. Target-specific selection belongs in the WBY Minecraft profile only after that point.

## Validation

The first implementation step should use a synthetic bars capability in adapter tests. It must prove cardinal neighbor lowering and coarse partial-collision classification without adding a concrete block to the ordinary Guild palette. Only after that generic proof should the WBY catalog consider promotion from evidence-only state contracts.
