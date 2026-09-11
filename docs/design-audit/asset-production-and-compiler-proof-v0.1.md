# Asset Production and Compiler Proof v0.1

**Status:** Owner-authorized feasibility strategy; executable proof active  
**Date:** 2026-09-11

## Purpose

Persist the asset-production workflow developed during the September 11 design session and define the smallest proof needed before Skyforge invests in an asset compiler.

The governing production idea is:

```text
purpose-built visual source
    -> structured intermediate representation
    -> deterministic transcription / compilation
    -> real Minecraft asset
    -> automated QA
    -> representative in-game human acceptance
```

This is not authorization to build a universal procedural architect. The compiler is justified only if a narrow real specimen demonstrates that the intermediate representation lowers repeated authoring/revision cost without sacrificing visual quality.

## 1. NPC texture workflow — proof established, production deferred

The NPC experiment demonstrated that concept-to-texture transcription is viable when the source image is deliberately generated for the target representation rather than cropped from arbitrary perspective art.

### Proven direction

1. Define NPC role, region, affiliation, and visual constraints.
2. Generate a purpose-built frontal portrait/head source:
   - straight-on camera;
   - centered head;
   - neutral or tightly controlled lighting;
   - full visible hair silhouette;
   - minimal occlusion;
   - consistent Minecraft/voxel-adjacent visual language.
3. Quantize/transcribe the source into actual head texture space.
4. Merge the head with a controlled body/outfit template.
5. Produce a valid Minecraft skin atlas.
6. Render deterministic UV-derived QA previews from the exact texture.
7. Human-review face readability, role readability, palette coherence, and cast consistency.
8. Revise only where needed.

### Resolution finding

- 8x8 front head face / 64x64 atlas remains the vanilla baseline.
- Approximately 16x16 front head face / 128x128 atlas is a promising quality sweet spot if renderer compatibility proves acceptable.
- Approximately 32x32 / 256x256 is useful as an upper-bound experiment but may exceed the desired Minecraft texture-density language.

The resolution choice is not locked. The important result is that the **source/transcription method itself works**.

### Production principle

Use generative tools to create standardized source material, not trusted final UVs. Equivalent sophistication across the NPC cast should come from a shared portrait-source specification, reusable body/occupation templates, deterministic transcription, and a common QA gate.

Bulk NPC production is deferred until renderer/texture-density decisions are needed.

## 2. Structure and aircraft problem

Skyforge still needs similarly repeatable methods for architecture / structures and aircraft / physical vehicles.

The intended solution is not literal image-to-block copying. Instead, purpose-built concept sources should be interpreted into a structured design representation, which can then be deterministically lowered into Minecraft geometry.

## 3. Compiler role

The proposed asset compiler sits between **design intent** and **explicit blocks**.

```text
AssetSpec
    -> resolved parameters
    -> Layout / Geometry IR
    -> VoxelModel
    -> BlockStateModel
    -> semantic anchors / metadata
    -> validation
    -> .schem / structure NBT + metadata + QA artifacts
```

The compiler does not decide what is beautiful. Art direction, concept generation, and human review remain responsible for taste. The compiler's value is preserving accepted design logic consistently, cheaply, and reproducibly across many related assets.

## 4. Inputs

A compiled asset may consume four input classes:

1. **Instance specification** — the requested asset and bounded family parameters.
2. **Family grammar** — reusable visual/structural rules.
3. **Module library** — reusable functional/geometric components.
4. **Context / physical constraints** — site envelope/access for structures or engineering constraints for aircraft.

## 5. Compiler process

The intended passes remain:

1. parameter resolution;
2. layout / geometry IR;
3. geometry generation;
4. voxelization;
5. material and block-state assignment;
6. functional realization / semantic anchors;
7. static validation;
8. exact Minecraft export only after offline geometry proves visually credible.

## 6. Expected outputs

A successful compilation should eventually emit:

- concrete Minecraft asset;
- Skyforge semantic metadata;
- QA artifacts: orthographic/isometric previews, dimensions, counts, validation, deterministic digest.

## 7. Scope guardrail

Do not build a universal Minecraft architect, freeform text-to-building generator, generalized CAD framework, or procedural-city system.

Prefer:

```text
known asset family
+ known grammar
+ known modules
+ bounded parameters
= valid realization
```

## 8. First proof — Bootstrap Guild branch

The first specimen remains a small temperate Guild branch associated with the Bootstrap civilization slice.

### v0.1 / v0.2 result

The first executable passes established that a compact spec can deterministically lower into:

- dimensions and bay rhythm;
- public hall / working wing / warehouse / repair semantic volumes;
- explicit voxel/block-state geometry;
- public and working openings;
- machine-readable activity/service anchors;
- deterministic digest and static reachability/volume validation;
- front/east/top, semantic floorplan, and isometric QA views.

A four-bay sibling compiles through the same structural code path, providing the first leverage evidence rather than merely scripting one fixed structure.

### v0.3 visual-grammar pass

Owner review of v0.2 concluded the result was better than expected and authorized continuation.

The next bounded pass therefore adds visual grammar **without** jumping immediately to Minecraft export:

- stable structural v0.2 lowering remains intact;
- `guild_branch_detail.py` performs a second pass over the compiled model;
- masonry base-course articulation;
- repeated window sill/lintel treatment;
- directional stair/slab roof states;
- roof overhang and closed gable ends;
- stronger public entrance hierarchy;
- restrained navy/brass Guild identity treatment;
- public canopy / approach apron;
- separate repair and freight canopies/aprons;
- working-side task lighting;
- airfield-interface anchor moved beyond the physical working canopies.

A v0.3 four-bay sibling uses the same detail pass.

The architectural reason for layering rather than rewriting is deliberate: **stable structural grammar first, visual/functional detailing second**. This keeps the compiler understandable and makes it easier to determine whether a future rule belongs to layout authority or presentation/detail authority.

### Current gate

The v0.3 output is now at the human visual gate. If accepted as a credible direction, the next implementation step is one exact Minecraft export path and in-game inspection. Do not continue adding offline polish indefinitely once the representation is good enough to expose runtime/placement problems.

## 9. Second proof — utility cargo aircraft

Only after the structure proof demonstrates useful leverage, apply the same architecture to one non-Bellanca utility/cargo aircraft appropriate for the first Guild destination.

The aircraft proof must add genuinely physical acceptance:

- silhouette fidelity to purpose-built orthographic source;
- internal/functional usability;
- real Create/Aeronautics assembly;
- actual flight;
- docking/cargo compatibility where applicable.

Do not begin by parameterizing the Bellanca hero asset.

## 10. Evaluation / kill criteria

The compiler hypothesis is supported only if the proof demonstrates most of the following:

- compiled result is visually credible enough to continue;
- structured representation is easier to revise than raw block editing;
- one meaningful sibling variant is materially cheaper than rebuilding;
- repeated modules/rules remain consistent automatically;
- validation catches real authoring errors;
- exported geometry remains comprehensible and manually editable;
- compiler code stays narrow rather than accumulating specimen-specific hacks.

Stop or redesign if visual quality remains inferior to ordinary manual authoring, the spec approaches raw block enumeration, every asset needs exceptions, output becomes difficult to hand-correct, or the project starts becoming a generic CAD/framework effort.

## 11. Decision principle

The project is **confident enough to prototype, not confident enough to overbuild**.

Use the same evidence discipline that proved the NPC texture method:

```text
smallest representative specimen
    -> executable/inspectable result
    -> compare against manual workflow
    -> decide whether to productionize
```
