# Asset Production and Compiler Proof v0.1

**Status:** Owner-authorized feasibility strategy; executable proof beginning
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

Skyforge still needs similarly repeatable methods for:

- architecture / structures;
- aircraft / physical vehicles.

The intended solution is not literal image-to-block copying. Instead, purpose-built concept sources should be interpreted into a structured design representation, which can then be deterministically lowered into Minecraft geometry.

## 3. Compiler role

The proposed asset compiler sits between **design intent** and **explicit blocks**.

Conceptually:

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

A compiled asset may consume four input classes.

### 4.1 Instance specification

Describes the particular requested asset.

Example structure intent:

```json
{
  "type": "guild_branch",
  "scale": "small",
  "region": "temperate",
  "bayCount": 3,
  "modules": [
    "public_hall",
    "service_counter",
    "warehouse",
    "light_repair"
  ]
}
```

Example aircraft intent:

```json
{
  "type": "utility_cargo_aircraft",
  "length": 25,
  "wingspan": 31,
  "crew": 2,
  "cargoClass": "medium",
  "engineCount": 2
}
```

### 4.2 Family grammar

Reusable knowledge defining what a family means visually and structurally.

For Guild architecture, accepted precommit direction already includes:

- strong durable base;
- expressed structural frame;
- prominent roofline;
- disciplined repeated bays;
- engineered additions;
- public face / working face distinction;
- institutional recognition through more than color alone;
- regional adaptation of materials, climate response, roof forms, and local construction tradition.

The compiler must preserve these rules rather than reducing regionalization to palette swaps.

### 4.3 Module library

Reusable functional or geometric components such as:

```text
PUBLIC_ENTRANCE
WINDOW_BAY
SERVICE_COUNTER
WAREHOUSE
FREIGHT_DOOR
REPAIR_SHED
SIGNAL_MAST
AIRCRAFT_BERTH
```

Aircraft modules may include:

```text
COCKPIT
CARGO_BAY
WING
ENGINE_NACELLE
TAILPLANE
VERTICAL_STABILIZER
LANDING_GEAR
PROPELLER_CLEARANCE
DOCKING_INTERFACE
```

Modules may carry both geometry and constraints.

### 4.4 Context / physical constraints

For structures, site evidence may provide an allowed envelope and meaningful approach/access directions without becoming final concrete admission authority.

Example:

```json
{
  "siteEnvelope": [27, 14, 22],
  "settlementAccess": "south",
  "airfieldDirection": "east",
  "freightAccess": "east",
  "downslopeDirection": "north"
}
```

For aircraft, constraints are more directly physical:

```text
maximum dimensions
required cargo volume
propeller clearance
cockpit access
required Aeronautics/Create functional components
assembly/connectivity constraints
```

## 5. Compiler process

### Pass 1 — parameter resolution

Convert semantic parameters to exact dimensions while preserving deterministic identity.

Example:

```text
small branch + 3 bays
    -> 5-block bay width
    -> 6-block wall height
    -> 16/17-block main frontage
    -> explicit roof plate and service-wing dimensions
```

### Pass 2 — layout / geometry IR

Resolve abstract spatial volumes and relationships before individual Minecraft block choice.

For a building:

```text
public hall volume
warehouse volume
working yard edge
entrance axis
freight door
structural frame
roof envelope
service anchors
```

For an aircraft:

```text
centerline
fuselage stations / cross-sections
wing root / tip / planform
cockpit volume
cargo volume
engine centers
tail geometry
landing gear
functional hardpoints
```

### Pass 3 — geometry generation

Generate the actual surfaces/volumes implied by the IR.

### Pass 4 — voxelization

Convert geometry to discrete Minecraft-space occupancy.

The intermediate voxel model should distinguish at least:

```text
EMPTY
SOLID
OPENING
WINDOW
STRUCTURAL
FUNCTIONAL
ANCHOR
```

or equivalent semantic categories.

### Pass 5 — material and block-state assignment

Resolve semantic material roles into concrete block IDs and exact block states, including orientation-dependent states such as stairs, doors, logs, panes, or retained-mod blocks.

### Pass 6 — functional realization

Emit gameplay-relevant blocks and/or metadata such as:

```text
SERVICE_COUNTER
CONTRACT_BOARD
NPC_WORK_POINT
FREIGHT_PICKUP
FREIGHT_DROPOFF
AIRCRAFT_BERTH
REPAIR_BAY
```

For aircraft, this includes actual required Create/Aeronautics components rather than producing only a sculpture.

### Pass 7 — validation

Static validation should catch cheap errors before Minecraft is launched.

Examples:

- structure exceeds site envelope;
- entrance does not connect outside to inside;
- inaccessible room/service anchor;
- freight door lacks required clearance;
- roof/module intersection;
- invalid orientation/block-state assignment;
- disconnected aircraft geometry;
- asymmetric geometry where symmetry is required;
- propeller/nacelle/fuselage collision;
- inaccessible cockpit/cargo area;
- missing required Aeronautics component or interface.

Runtime validation remains authoritative for behavior the compiler cannot prove statically.

## 6. Outputs

A successful compilation should eventually emit:

1. **Concrete Minecraft asset**
   - Sponge `.schem`, Minecraft structure NBT, or another exact interchange chosen by Implementation.
2. **Skyforge semantic metadata**
   - asset identity;
   - bounds;
   - entrances/interfaces;
   - service/activity anchors;
   - role/capability markers;
   - deterministic source/spec provenance.
3. **QA artifacts**
   - front/side/top/isometric previews;
   - dimensions;
   - material counts;
   - validation report;
   - deterministic digest where useful.

## 7. Expected utility

The compiler is expected to provide leverage primarily through:

- cheap sibling variants after the first family is authored;
- consistent architectural/aircraft grammar;
- cheap global revisions to repeated modules or rules;
- deterministic site adaptation;
- semantic anchors emitted alongside geometry;
- automated validation;
- reuse of accepted visual/functional modules;
- ability to analyze donor assets into reusable grammar later.

The compiler is **not** expected to outperform human visual judgment. Its quality advantage should come from preserving already-approved design decisions consistently across a larger corpus.

## 8. Scope guardrail

Do not build:

- a universal Minecraft architect;
- freeform text-to-building generation;
- a generalized CAD framework;
- broad procedural-city generation;
- abstractions without a demonstrated need from the current specimen.

Prefer:

```text
known asset family
+ known grammar
+ known modules
+ bounded parameters
= valid realization
```

Any abstraction added during the proof must be justified by the first real specimen or an immediately following sibling variant.

## 9. First proof — Bootstrap Guild branch

The first specimen should be a **small temperate Guild branch associated with the Bootstrap civilization slice**.

It is a good benchmark because accepted design already constrains its meaning while leaving enough geometric freedom to test the compiler.

Required conceptual characteristics:

- compact branch / Hall, not a monumental regional headquarters;
- practical aviation/logistics site relationship;
- one concentrated Guild destination rather than scattered services;
- public entrance/readable Open Sky identity;
- universal service core consolidated through a compact interaction surface;
- working-side freight/storage relationship;
- light repair capability;
- signal/navigation presence where useful;
- public face and working face distinguishable but coherent;
- temperate Guild Mercantile Functionalism;
- enough historical/functional detail to feel operated, not decorative.

The first compiler proof should not attempt to solve full settlement placement or exact procedural town integration.

### First proof inputs

At minimum:

```text
scale = small
region = temperate
bay count = 3
public access direction = south
working / airfield direction = east
services = universal core
additional capabilities = warehouse + light repair
```

### First proof outputs

Before Minecraft runtime integration, produce:

- resolved layout IR;
- voxel/block occupancy representation;
- deterministic material/block-state plan;
- semantic anchor manifest;
- automated validation report;
- inspectable orthographic/isometric preview.

Then, if the representation is credible, add one exact Minecraft export path and inspect the specimen in-game.

## 10. Second proof — utility cargo aircraft

Only after the structure proof demonstrates useful leverage, apply the same architecture to one **non-Bellanca utility/cargo aircraft** appropriate for the first Guild destination.

The aircraft proof must add genuinely physical acceptance:

- silhouette fidelity to purpose-built orthographic source;
- internal/functional usability;
- real Create/Aeronautics assembly;
- actual flight;
- docking/cargo compatibility where applicable.

Do not begin by parameterizing the Bellanca hero asset.

## 11. Evaluation / kill criteria

The compiler hypothesis is supported only if the proof demonstrates most of the following:

- compiled result is visually credible enough to continue, not merely technically valid;
- the structured representation is easier to revise than raw block editing;
- one meaningful sibling variant is materially cheaper than building another structure from scratch;
- repeated modules/rules remain consistent automatically;
- validation catches real authoring errors;
- exported geometry is comprehensible and manually editable when needed;
- compiler code remains narrow rather than accumulating specimen-specific hacks.

Stop or redesign if:

- visual quality is persistently inferior to ordinary manual authoring;
- the specification becomes nearly as verbose as enumerating blocks;
- every asset requires extensive one-off exceptions;
- generated output is difficult to hand-correct;
- runtime/mod constraints dominate the compiler to the point that little reusable logic remains.

## 12. Decision principle

The project is **confident enough to prototype, not confident enough to overbuild**.

Use the same evidence discipline that proved the NPC texture method:

```text
smallest representative specimen
    -> executable/inspectable result
    -> compare against manual workflow
    -> decide whether to productionize
```

If the Guild branch proof succeeds, the next investment is a sibling-variant/revision bake-off before expanding the abstraction surface.