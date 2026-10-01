# Skyforge — flagship project page

**Audience:** technical recruiters and hiring managers first; technical interviewers and procedural-generation reviewers second  
**Project status:** pre-release  
**Claim source:** `docs/presentation/claims/current-claims.md`  
**Demo narrative:** `docs/presentation/demos/flagship-90-second-demo.md`  
**Visual enrichment:** issue #348

## The project in one sentence

**Skyforge is a deterministic, backend-neutral procedural world-synthesis engine that starts from the meaning of a place, compiles that intent into exact three-dimensional terrain, and realizes the result through Minecraft 1.21.1 / NeoForge as its first runtime backend.**

## Recommended personal role wording

For portfolio, resume, and interview use:

> **Project architect / technical lead — architected and led development of Skyforge using a repository-first, AI-assisted multi-agent engineering workflow.**

This wording is intentionally different from implying that every line of implementation was personally typed by one developer. The stronger and more accurate authorship claim is architectural and technical leadership: defining the system boundaries, directing milestone work, making integration and acceptance decisions, and holding the project to explicit evidence and human-review gates.

Short form:

> **Architected and led an AI-assisted multi-agent implementation of a deterministic procedural world-synthesis engine.**

## Why Skyforge is technically interesting

### 1. The generator starts above the noise layer

Skyforge does not begin from chunks or blocks. Backend-neutral semantic descriptors express what kind of place is being authored. Versioned recipes compile that intent into immutable procedural graphs and deterministic scalar fields.

The practical consequence is that visible terrain can remain tied to explicit world meaning rather than becoming an opaque stack of unrelated random functions.

### 2. Terrain is an exact three-dimensional owned object

A Skyforge island is a finite 3-D volume with an upper surface, underside geometry, interior space, and explicit boundaries.

Accepted runtime work also allows separate exact volumes to share horizontal X/Z coordinates at different heights without collapsing into one global terrain column.

This makes floating-world generation a volumetric ownership problem rather than a height-map trick.

### 3. Minecraft is a backend, not the world model

Minecraft 1.21.1 / NeoForge is the first production runtime backend, while the mathematical and semantic engine remains free of Minecraft ontology.

The backend already carries accepted Skyforge terrain through real lifecycle concerns including:

- native surface and biome population;
- caves and post-cave interior population;
- lakes, ores, underground decoration, and springs;
- structure admission/support machinery;
- deferred stable-chunk realization;
- generated-fluid ownership/boundary fencing;
- save/reload and mutation-inert actual-client reopen.

The engineering point is not merely that the terrain can be displayed in Minecraft. It is that the runtime must preserve the neutral engine's ownership and determinism contracts while interacting with a large existing game lifecycle.

### 4. Reproducibility is part of the architecture

Skyforge uses canonical serialization, fixed-seed evidence, deterministic graph/field evaluation, exact ownership checks, geometry identities/digests, and retained acceptance records.

Screenshots are useful for qualitative review, but they are not the sole proof that generation behaved correctly.

### 5. The project treats validation as an engineering resource

Cheap deterministic evidence is kept broad; expensive Minecraft lifecycle and client evidence is selected by risk-equivalence class. Human gates are used for questions such as morphology quality and ecology legibility that machine metrics cannot settle.

That validation model is part of the project architecture rather than an afterthought.

## What is already demonstrable

Current accepted boundaries include:

- five primary built-in morphology families: **Massif, Tableland, Spine, Basin, and Lobed**;
- exact Minecraft carriers for all five at the accepted SMALL / `seed-skyforge` boundary;
- human-reviewed family viability and underside/silhouette coherence at that boundary;
- exact finite-volume ownership and vertically stacked isolation;
- persistent forest/taiga ecology through the normal Minecraft-facing production seams;
- deterministic persistence/reopen evidence;
- accepted backend-neutral hydrology, ecology, geology/resource-opportunity, regional, and site/access evidence with explicit provenance;
- a substantial retained gameplay-integration substrate across mobility, atmosphere, avionics/network/GPS, turtle logistics/mining, Nether behavior, and initial resource-availability policy.

Not all of those semantics are yet combined into one final production world. That convergence is the current program direction.

## 15-second explanation

> Skyforge is a procedural world engine that starts from semantic terrain intent instead of random blocks, compiles exact three-dimensional worlds, and proves those worlds can survive a real Minecraft runtime without giving Minecraft ownership of the underlying world model.

## 30-second explanation

> I architected and led development of Skyforge, a deterministic procedural world-synthesis engine. It starts from semantic descriptions of a place, compiles them through a backend-neutral graph and field system into exact finite 3-D terrain, then realizes those worlds through a separate Minecraft/NeoForge backend. The project already has five accepted landform families, volumetric ownership, native ecology and cave/interior integration, persistence, and reproducible evidence. The current roadmap is to make those pieces converge into one coherent playable region.

## Resume bullets

Use the subset that best fits the role.

- **Architected and led development of a backend-neutral procedural world-synthesis engine**, compiling semantic landform descriptors through deterministic procedural graphs/scalar fields into exact finite 3-D terrain volumes.
- **Led Minecraft/NeoForge runtime integration** for exact-volume terrain ownership, native biome/surface population, caves/interior features, structures, deferred realization, generated-fluid boundaries, and save/reopen persistence.
- **Designed reproducible verification architecture** around canonical serialization, fixed-seed evidence, deterministic geometry identities, exact ownership checks, representative full-runtime characterization, and explicit human visual gates.
- **Structured development as a repository-first AI-assisted multi-agent program**, with lane ownership, cross-lane contracts, milestone ledgers, CI evidence, and bounded acceptance criteria so parallel work remains reconstructable and auditable.

## Interview opener

> Skyforge came from a question I had about procedural generation: what changes if the generator begins with the meaning of a place rather than with noise or chunks? I separated semantic world description from procedural construction and from game realization. The neutral engine compiles descriptors into deterministic graphs and scalar fields that define exact 3-D island volumes; a separate NeoForge backend then has to preserve those ownership contracts through Minecraft's actual lifecycle. That led to interesting systems problems in determinism, spatial ownership, deferred generation, persistence, native-feature adaptation, and validation. Minecraft is useful because it is a demanding real backend, not because the architecture is defined around Minecraft.

## Technical-review branches

A reviewer who wants to go deeper can follow the part most relevant to the role.

### Architecture / software design

Read:

1. root `README.md`;
2. `docs/architecture/Skyforge_Current_Runtime_Architecture.md`;
3. `docs/agent-state/CROSS_LANE_CONTRACTS.md`.

Look for:

- semantic model vs recipe/compiler vs field/runtime separation;
- neutral-module dependency direction;
- exact ownership;
- backend responsibilities and non-authority.

### Numerical / procedural generation

Read:

1. Authorship state;
2. AUTH-0083/0084 morphology review material;
3. reference evidence tasks and fixed-seed/suspended-volume evidence.

Look for:

- deterministic scalar-field evaluation;
- provider-neutral morphology;
- five-family identity;
- upper/underside volumetric construction;
- scale/seed evidence and explicit provenance.

### Runtime / systems integration

Read:

1. Implementation state;
2. SF-IMP-0052 through current accepted runtime review records;
3. `docs/showcase/production-morphology-atlas.md`;
4. `docs/showcase/ecology-showcase.md`.

Look for:

- BASE_WORLD vs Skyforge ownership;
- whole-volume physical admission;
- stable-chunk/deferred lifecycle;
- native Minecraft feature adaptation;
- save/reopen and actual-client persistence.

### Verification / engineering process

Read:

1. `docs/agent-state/VALIDATION_POLICY.md`;
2. `AGENTS.md`;
3. representative acceptance PRs and CI workflows.

Look for:

- exhaustive cheap evidence vs representative expensive evidence;
- widening after sampled failures;
- evidence portability;
- human visual gates;
- repository-first disposable-agent workflow.

## Two-minute reviewer path

If a reviewer has only a few minutes:

1. **Read this page** — project thesis, role, current boundary.
2. **Read the root README architecture diagram** — semantic descriptors to exact volume to backend.
3. **Inspect one morphology acceptance record** — proof that the abstract model reaches Minecraft exactly.
4. **Inspect the ecology showcase** — proof that the world participates in real native game systems.
5. **Open the PRES-0003 demo narrative** — see the intended 90-second show-don't-tell sequence and evidence map.

When issue #348 supplies the canonical runtime still set, the final visual package should slot into this reviewer path without changing the underlying claims.

## Current boundary — what I would not claim yet

Skyforge is not yet:

- a finished player-facing Minecraft release;
- a stable public engine/API;
- empirically proven across multiple independent production backends;
- a completed multi-seed/multi-scale/hybrid/regional morphology atlas;
- a world where every authored geology, hydrology, ecology, resource, structure, and civilization semantic is already physically realized;
- a finished Bootstrap Province.

Those are important because the project is strongest when the current engineering boundary is stated precisely.

## Roadmap significance

The near-term convergence target is the **Bootstrap Province**: one deterministic playable region where morphology, geology/materials, hydrology, caves, ecology, resources, structures, mobility, industry, and progression begin reinforcing one another.

If that vertical slice closes successfully, the project stops being merely a collection of individually proven world systems and becomes a coherent end-to-end world-synthesis demonstration.

Longer term, a second independent backend would be the strongest empirical test of the backend-neutral architecture. That remains strategic rather than current capability.
