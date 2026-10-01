# PRES-0005 explanatory graphics source brief

**Issue:** #383  
**Audience:** nontechnical viewers first; recruiters/hiring managers second; technical reviewers third  
**Authority:** current `main`, Presentation claim registry, README, cross-lane contracts, producer ledgers

## Design intent

The explanatory graphics suite exists to make Skyforge understandable before production-quality runtime imagery is mature.

Every graphic should:

- communicate one idea in roughly 10–20 seconds;
- use current accepted architecture and producer boundaries;
- avoid implying roadmap systems are already physically realized;
- prefer vector/source-first construction so text and structure stay editable;
- use restrained labels rather than dense implementation detail;
- remain usable independently in a portfolio page, interview discussion, PDF, or future slide deck.

## Graphic 1 — How Skyforge works

**Message:** Skyforge begins with world meaning, compiles it through a deterministic neutral engine, then realizes the result through a backend.

### Core flow

```text
Semantic descriptors
        ↓
Versioned recipes
        ↓
Immutable procedural graph
        ↓
Deterministic scalar fields
        ↓
Exact finite 3-D world volumes
        ↓
Runtime realization
(Minecraft / NeoForge first)
```

Parallel branch from the neutral engine:

```text
graphs + fields + volumes
        ↓
canonical serialization / fixed-seed evidence / diagnostics
        ↓
acceptance and reproducibility
```

### Required labels

- **Meaning**
- **Construction**
- **Geometry**
- **Backend**
- **Evidence**

### Explicit non-claim

Do not imply that multiple production backends currently exist.

---

## Graphic 2 — Who owns what

**Message:** Skyforge separates world meaning, neutral construction, runtime realization, gameplay policy, and communication.

### Layer model

```text
AUTHORSHIP
world meaning, morphology, geology, hydrology, ecology,
regional/site opportunity and provenance
        ↓
NEUTRAL SKYFORGE ENGINE
recipes, graphs, fields, exact ownership, deterministic evidence
        ↓
IMPLEMENTATION
Minecraft/NeoForge realization, lifecycle, persistence,
native feature integration, structures, fluids
        ↓
CONTENT / EXPERIENCE
gameplay policy, availability/guarantees, mobility,
industry/progression requirements
        ↓
PRESENTATION
claim compression, diagrams, demos, reviewer paths
```

### Cross-lane rule to show visually

Each downstream layer **consumes** upstream evidence but does not retroactively redefine the upstream authority.

### Explicit non-claims

- Authorship opportunity is not automatically a Minecraft deposit/resource/structure.
- Content policy is not automatically physical realization.
- Presentation never creates technical truth.

---

## Graphic 3 — An island is a volume, not a heightmap

**Message:** Skyforge terrain is an exact finite 3-D owned volume.

### Visual structure

Show a simplified floating island cross-section with:

- upper surface;
- underside;
- interior/cave space;
- exact outer boundary;
- air above/below;
- second vertically stacked island sharing the same X/Z footprint.

### Key annotation

```text
Same X/Z does not mean same terrain column.
Ownership is three-dimensional.
```

### Comparison inset

**Heightmap model:**
one Y value per X/Z

**Skyforge model:**
owned 3-D volume with independent upper/underside geometry

### Explicit non-claim

This graphic explains ownership semantics, not a claim that every authored subsystem is already visually realized.

---

## Graphic 4 — Evidence is part of the architecture

**Message:** Skyforge does not use screenshots as the sole proof of correctness.

### Evidence stack

```text
CHEAP / BROAD
canonical serialization
fixed-seed outputs
exact ownership checks
numerical diagnostics
deterministic identities

        ↓ select risk-equivalent representatives

EXPENSIVE / RUNTIME
Minecraft lifecycle
deferred generation
save / reload
actual-client reopen

        ↓ where machines cannot answer the question

HUMAN GATES
morphology quality
ecology legibility
visual plausibility
```

### Key annotation

```text
Evidence width decreases as execution cost rises.
Human review is reserved for questions metrics cannot settle.
```

### Explicit non-claim

Do not imply every expensive runtime path is tested exhaustively across the full Cartesian input space.

---

## Graphic 5 — Current capability vs convergence roadmap

**Message:** Skyforge already has substantial accepted pieces, but the next goal is coherent convergence rather than claiming a finished world.

### Three-column structure

#### ACCEPTED NOW

- deterministic neutral graph/field architecture;
- exact finite-volume ownership;
- five primary morphology families;
- exact SMALL / seed-skyforge Minecraft carriers for all five families;
- accepted native ecology/caves/interior/lifecycle/persistence seams;
- accepted authored hydrology/ecology/geology/resource/site/regional evidence;
- retained gameplay-integration substrate.

#### ACTIVE CONVERGENCE

- remaining multi-seed/multi-scale morphology confidence;
- producer-side physical realization of newer authored semantics;
- infrastructure/resource-world integration;
- coherent region-level experience assembly.

#### ROADMAP / STRATEGIC

- Bootstrap Province as a coherent playable vertical slice;
- broader polished regional/world experience;
- second independent backend as empirical proof of backend neutrality.

### Explicit visual rule

Use distinct visual treatment for:
- accepted;
- in progress;
- roadmap.

Never blend them into one capability column.

## Styling direction

Preferred visual language:

- technical but not schematic-overload;
- dark or neutral background with one accent family;
- rounded rectangular nodes;
- thin connectors;
- large whitespace;
- one concise caption per graphic;
- no faux-code aesthetic unless it materially clarifies structure;
- avoid raw test-count walls.

## Human gates

No human gate is required for factual diagram structure if all labels are grounded in current repository authority.

A human-eye gate is only warranted for:

- final visual hierarchy;
- whether a diagram is understandable to a nontechnical viewer;
- whether the final portfolio composition feels polished rather than merely correct.

Those gates can be deferred until the first 3–5 graphics exist as a coherent set.
