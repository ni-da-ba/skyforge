# Wave C21 — Create resource-worldgen authority A/B

**Status:** MERGED / ACCEPTED when PR #315 lands after exact-head gates  
**Issue:** #306  
**Predecessor:** C20 / PR #302  
**Authorship inputs:** AUTH-0093 and AUTH-0094  
**Parent vertical slice:** #224

## Purpose

C21 proves a narrow integration fact before concrete resource realization:

> retained Create Zinc and striated-material assets can remain available while Create's generic
> Overworld placement authority is removed from an isolated control environment.

This is not the production exact-volume suppression implementation.

## Upstream authority under test

Pinned Create 6.0.10 contributes:

```text
create:zinc_ore
    NeoForge biome modifier
    -> create:zinc_ore placed feature
    -> #minecraft:is_overworld
    -> underground_ores

create:striated_ores_overworld
    NeoForge biome modifier
    -> create:striated_ores_overworld placed feature
    -> #minecraft:is_overworld
    -> underground_ores
```

The accepted Skyforge direction remains:

```text
Create Zinc blocks/items/processing     KEEP
Create striated material block palette  KEEP
generic Create placement in Skyforge    SUPPRESS / REDIRECT
AUTH-0093/0094                          geology/provenance evidence
C20                                     availability/guarantee policy
Implementation                          exact physical realization/lifecycle
```

## A/B control

### Baseline

The pinned retained stack must prove:

- both Create modifier ids resolve in the final NeoForge biome-modifier registry;
- both resolve to active add-feature modifiers;
- `create:zinc_ore`, `create:deepslate_zinc_ore`, and representative striated material
  `create:crimsite` remain registered.

### Suppressed validation control

A disposable C21 run world receives a world-local datapack overriding only:

```text
data/create/neoforge/biome_modifier/zinc_ore.json
data/create/neoforge/biome_modifier/striated_ores_overworld.json
```

with:

```json
{"type":"neoforge:none"}
```

The runtime must prove the two registry entries are no longer active add-feature modifiers while the
same retained Create block assets remain registered.

## Production-scope boundary

The no-op datapack is **validation-only**.

It is deliberately generated under the disposable C21 world directory and is absent from
`src/main/resources`.

A packaged global override would also change BASE_WORLD. That is not accepted by C21.

Current Implementation already exposes the appropriate downstream ownership seam:

```text
no SkyforgeGenerationDomainStage island scope
    -> BASE_WORLD

explicit exact-volume population scope
    -> SkyforgeNativeBiomePopulationRunner
    -> enumerate final biome PlacedFeature keys
    -> SkyforgeNativePlacedFeatureRunner
```

Therefore production filtering does not require a new worldgen architecture. Implementation may add
a narrow feature-admission policy at the exact-volume population enumeration/execution seam while
leaving ordinary BASE_WORLD generation unchanged.

C21 itself does not make that Implementation change.

## Resource-realization handoff

Once C21 proves modifier/asset separation, the downstream contract is:

1. Content keeps vanilla Iron/Copper and Create Zinc identities unless later executable evidence
   rejects them.
2. Content uses C20 + AUTH-0094 to select/re-plan eligible gameplay scopes.
3. Implementation suppresses competing generic feature execution only inside Skyforge-owned
   exact-volume population.
4. Implementation authors concrete deposits from AUTH-0093/0094 + C20 policy.
5. Deposit count, geometry, grade, quantity, accessibility, block palette, persistence, and lifecycle
   remain Implementation-owned.

The 48-64 accessible iron-equivalent bootstrap band remains an engineering estimate rather than a
worldgen constant.

## Acceptance

C21 is accepted only if:

1. baseline runtime resolves both pinned Create modifiers as active add-feature modifiers;
2. the isolated suppressed runtime resolves both as no-op/non-add-feature modifiers;
3. Zinc ore, deepslate Zinc ore, and Crimsite remain registered in both modes;
4. production resources contain no global C21 override;
5. dedicated C21 workflow passes on the exact synchronized head;
6. repository CI and the targeted C17 retained regression pass on the synchronized merge candidate;
7. shared state records the exact-volume-only Implementation handoff.

The retained C21 workflow triggers directly on C21-owned files and pins. Broad shared build/entrypoint
changes retrigger it once after landing on `main`, avoiding retained compatibility fan-out across
unrelated pull requests.

No human visual gate is required for this authority-control milestone.
