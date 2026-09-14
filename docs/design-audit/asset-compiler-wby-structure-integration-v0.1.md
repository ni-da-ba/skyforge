# Asset Compiler — Wild Blue Yonder Structure Integration v0.1

## Decision

Wild Blue Yonder content belongs at the Minecraft realization boundary, not in the architectural compiler or backend-neutral realization IR.

The current Guild v0.14 compiler already provides the required seam:

```text
semantic architecture
  -> resource-name-free realization intent
  -> Minecraft target resolver
  -> concrete block states / structure NBT
```

This integration therefore adds an opt-in WBY target profile while leaving the ordinary vanilla Minecraft profile unchanged.

## Audited retained stack

The repository's immutable Wave C1 pins establish the first defensible WBY construction vocabulary for Minecraft 1.21.1 / NeoForge 21.1.249:

- Create 6.0.10+mc1.21.1
- RPL 2.1.2
- Create Big Cannons 5.11.7
- Create Crafts & Additions 1.6.0
- Create Metallurgy 1.0.3-1.21.1
- Sable 2.0.5+mc1.21.1
- Create Aeronautics 1.3.2+mc1.21.1
- Create Propulsion 1.1.5
- JEI 19.50.0.414

Later isolated waves validate additional WBY capabilities such as Reliable Gliders, Aerodynamics4MC, CC:Tweaked / Create: Avionics, and Create: Diesel Generators. Those are not automatically admitted into structure realization merely because they are present in the broader mod stack.

## v0.1 active realization subset

The first automatic overlay is intentionally conservative:

| Block | Semantic use | Status |
|---|---|---|
| `create:andesite_casing` | generic industrial hardware / masonry hardware | active |
| `create:brass_casing` | warm/brass Guild hardware accent | active |
| `create:copper_casing` | future copper hardware/detail vocabulary | cataloged, not auto-selected |

The three casing blockstates are state-free in the audited Create 1.21.1 source line. The WBY resolver keeps the vanilla capability registry as a complete fallback and adds explicit target preferences only when semantic families match.

## Deliberately deferred

`create:framed_glass_pane` is a strong glazing candidate, but it is neighbor-sensitive and its exact state/default contract must be checked against the pinned Create 6.0.10 runtime before it becomes an automatic replacement for vanilla panes.

`create:ornate_iron_window` is visually useful but needs an explicit architectural/detail role. It should not globally replace ordinary glazing merely because the block exists.

Create machinery, storage devices, block entities, Aeronautics/Propulsion components, Metallurgy machinery, avionics, and other functional blocks require semantic structure capabilities and lifecycle/NBT ownership before compiler use. Their existence in WBY is not sufficient authority to place them.

## Integration shape

`minecraft_wby_profile.py` owns the bounded WBY capability overlay and target preference policy. `WbyC1MinecraftAdapter.resolve_intent()` applies those preferences to already target-neutral intent, so concrete mod resource IDs do not travel upstream into architecture.

`minecraft_wby_structure.py` realizes the current Guild v0.14 IR through that profile and preserves the existing explicit block-entity NBT coverage check.

`compile_wby.py` is the opt-in proof entry point:

```bash
python tools/asset_compiler/compile_wby.py \
  tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json \
  --out build/asset-compiler-wby
```

The ordinary `compile.py --minecraft-structure` path is intentionally unchanged.

## Acceptance gates

Before broadening the automatic WBY palette:

1. the existing asset-compiler unit suite remains green;
2. WBY profile tests prove vanilla-registry fallback and deterministic Create casing selection;
3. full Guild v0.14 lowering proves concrete Create resource IDs appear only after the resource-name-free IR boundary;
4. WBY NBT export is byte-deterministic;
5. stateful blocks are admitted only after exact pinned-runtime registry/state validation;
6. functional mod blocks require explicit semantic roles instead of palette-wide opportunistic substitution.

## Aircraft compiler concurrency boundary

This work does not alter the `aircraft-compiler-proof` branch, aircraft route fixtures, yaw contracts, or occupancy/player-interaction work. The common architectural rule is only that both compilers should consume WBY capabilities through explicit Minecraft-side contracts rather than allowing mod implementation details to become upstream semantic authority.
