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

The Create target is pinned twice: repository Wave C1 identifies Modrinth coordinate `maven.modrinth:LNytGWDc:UjX6dr61`, and the audited upstream 1.21.1 source commit `79b5d3b37e2d1970818dd97ca460b649cd0a456c` is the commit that bumps Create to 6.0.10. The latter gives stronger source provenance for catalogued block/state contracts than a floating branch audit. Runtime promotion is separately gated by the exact pinned artifact and live registry, so source provenance is not treated as runtime proof by itself.

## Wave C1 structure-authority audit

`minecraft_data/wby_wave_c1_structure_audit.json` mirrors every exact Wave C1 version/coordinate pin and records whether that mod currently has any structure-compiler authority. Runtime presence is not placement authority.

Only Create owns an architectural capability catalog in this tranche. RPL and JEI have no declared structure contract; Create Big Cannons, Create Crafts & Additions, and Create Metallurgy remain deferred pending explicit structure semantics; Sable, Create Aeronautics, and Create Propulsion are explicitly held outside structure authority to preserve the aircraft-compiler concurrency boundary.

The audit is tested directly against `skyforge-neoforge-1211/wave-c1-mods.properties`. Adding, removing, or repinning a Wave C1 mod therefore makes the structure audit stale and fails its test until the disposition is reviewed. Deferred mods cannot declare a capability catalog or automatic structure resources.

## v0.1 active realization subset

The automatic overlay remains intentionally conservative:

| Block | Semantic use | Status |
|---|---|---|
| `create:andesite_casing` | generic industrial hardware / masonry hardware | active |
| `create:brass_casing` | warm/brass Guild hardware accent | active |
| `create:framed_glass_pane` | generic pane-shaped WBY glazing | active |
| `create:copper_casing` | future copper hardware/detail vocabulary | cataloged |
| `create:industrial_iron_block` | future structural/industrial metal detail | cataloged |
| `create:weathered_iron_block` | future repair/history metal detail | cataloged |
| `create:industrial_iron_window_pane` | industrial glazing candidate | cataloged |
| `create:ornate_iron_window_pane` | institutional/ornate glazing candidate | cataloged |

The two active casing blockstates are state-free. All three pane resources are represented with `east`, `north`, `south`, `west`, and `waterlogged` boolean state domains and all-false defaults. Those contracts were first source-audited and then checked against the live Create 6.0.10 registry before any stateful resource was promoted.

`create:framed_glass_pane` is now active because its generic `glazing` family and `pane` capability match the existing target-neutral Guild window intent without inventing a new architectural distinction. Industrial and ornate panes remain catalog-only because their visual specialization still lacks an upstream semantic reason for automatic selection.

## Exact pinned-runtime registry proof

The asset-compiler workflow now closes the stateful-block promotion gate in the actual modded runtime rather than relying only on source inspection.

It first runs `:skyforge-neoforge-1211:waveC9ResolvePinnedMods --no-configuration-cache`, whose Create dependency is sourced through the immutable Wave C1 coordinate. The resolver must observe the pinned artifact token `LNytGWDc-UjX6dr61` before the runtime step proceeds.

The workflow then launches the existing isolated C21 Create-capable baseline server with the opt-in WBY probe enabled. `SkyforgeWbyC1StructureRegistryProbe` confirms Create reports version 6.0.10, resolves all eight currently catalogued structure resources in `BuiltInRegistries.BLOCK`, and checks the three pane resources against the exact expected property domains and defaults. The observed pane implementation is Create's `ConnectedGlassPaneBlock` for framed, industrial, and ornate panes.

This probe is acceptance-only. It is inert unless its explicit system property is set, does not change ordinary Skyforge launches, does not grant procedural authority to the wider mod stack, and terminates its isolated server after writing PASS/FAIL evidence.

## Fail-closed catalog authority

A catalog entry is not equivalent to placement authority.

`minecraft_wby_profile.py` accepts only two explicit statuses:

- `active`: parsed, validated, and admitted to the WBY resolver registry;
- `cataloged`: parsed and validated, but omitted from the resolver registry.

Unknown or missing statuses fail closed. Duplicate resource names fail closed. Property defaults must name declared properties and belong to the declared domains. Consequently a newly researched WBY block cannot become selectable merely because its capability tags happen to match an existing intent. Promotion from `cataloged` to `active` is an explicit reviewed operation.

This corrects an early v0.1 weakness where the catalog carried a `status` field but the loader admitted every listed entry. Tests assert both sides of the authority boundary: active Create casings and generic framed glazing resolve normally, while copper/iron blocks and the specialized panes remain absent from both automatic selection and canonical Guild structure output.

## Deliberately deferred

`create:industrial_iron_window_pane` and `create:ornate_iron_window_pane` are runtime-validated as concrete pane contracts but still need explicit semantic distinctions before automatic use; decorative variety alone is not sufficient authority to pick them.

Create 6.0.10 also exposes useful architectural families that remain deliberately deferred. Andesite/brass/copper bars need a target-neutral railing/bars role and generic cardinal-connectable topology rather than masquerading as glazing. Metal ladders need explicit climbable and attachment/survival semantics. Metal scaffolding needs its richer support/state contract. `create:metal_girder` needs a dedicated girder/brace role and exact property validation before admission.

Create machinery, storage devices, block entities, Aeronautics/Propulsion components, Metallurgy machinery, avionics, and other functional blocks require semantic structure capabilities and lifecycle/NBT ownership before compiler use. Their existence in WBY is not sufficient authority to place them.

## Integration shape

`minecraft_wby_profile.py` owns the bounded WBY capability overlay and target preference policy. `WbyC1MinecraftAdapter.resolve_intent()` applies those preferences to already target-neutral intent, so concrete mod resource IDs do not travel upstream into architecture.

Generic pane-shaped glazing now prefers `create:framed_glass_pane` only at that target boundary. The ordinary vanilla profile still resolves the same semantic intent to `minecraft:glass_pane`.

`minecraft_wby_structure.py` realizes the current Guild v0.14 IR through that profile and preserves the existing explicit block-entity NBT coverage check.

`compile_wby.py` is the opt-in proof entry point:

```bash
python tools/asset_compiler/compile_wby.py \
  tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json \
  --out build/asset-compiler-wby
```

The dedicated asset-compiler workflow executes this WBY compile path explicitly, resolves the exact Create artifact, and runs the live registry probe in addition to discovering unit/integration tests. The ordinary `compile.py --minecraft-structure` path is intentionally unchanged.

## Acceptance gates

Before broadening the automatic WBY palette:

1. the existing asset-compiler unit suite remains green;
2. the machine-readable Wave C1 audit exactly matches the immutable runtime pin file;
3. WBY profile tests prove deterministic active Create selection while preserving the vanilla profile;
4. catalogued/non-active blocks are mechanically excluded from the resolver registry;
5. catalog state/default schemas fail closed when malformed;
6. full Guild v0.14 lowering proves concrete Create resource IDs appear only after the resource-name-free IR boundary;
7. the dedicated CI workflow executes the WBY compile entry point directly;
8. WBY NBT export is byte-deterministic;
9. stateful blocks are promoted only after exact pinned-runtime registry/state validation;
10. decorative variants require a semantic reason to choose them;
11. functional mod blocks require explicit semantic roles, lifecycle ownership, and NBT policy instead of palette-wide opportunistic substitution.

## Aircraft compiler concurrency boundary

This work does not alter the `aircraft-compiler-proof` branch, aircraft route fixtures, yaw contracts, or occupancy/player-interaction work. Sable, Create Aeronautics, and Create Propulsion remain explicitly `aircraft-owned-deferred` in the Wave C1 structure audit.

The only NeoForge-side additions in this tranche are a property-gated WBY structure registry acceptance probe and a call from the existing Create-capable C21 acceptance fixture. They exist solely to observe the exact modded block registry during CI; they do not consume aircraft semantics, alter flight/runtime ownership, or run during ordinary gameplay. The common architectural rule remains that both compilers consume WBY capabilities through explicit Minecraft-side contracts rather than allowing mod implementation details to become upstream semantic authority.
