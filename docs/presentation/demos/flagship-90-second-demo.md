# Skyforge flagship 90-second demo

**Milestone:** PRES-0003 / issue #347  
**Audience:** nontechnical reviewer first; optional technical branches  
**Core objective:** make a viewer understand that Skyforge is an engineered world-synthesis system rather than a collection of Minecraft terrain tricks.  
**Claim source:** `docs/presentation/claims/current-claims.md`  
**Visual acquisition issue:** #348

## Narrative rule

The core sequence must communicate consequences before mechanism:

```text
MEANING
-> RECOGNIZABLE WORLD FORM
-> COMPLETE 3-D OBJECT
-> REAL GAME REALIZATION
-> LIVING / PERSISTENT WORLD
-> REPRODUCIBLE ENGINEERING PROOF
-> CLEARLY LABELED ROADMAP SCALE
```

The viewer should understand the thesis even with audio muted. Technical vocabulary may appear only as optional secondary text.

## 90-second core cut

| Time | Visual | Narration | On-screen text | Evidence / capture source |
| --- | --- | --- | --- | --- |
| 0:00-0:08 | Black or minimal title card, then a clean transition into a strong Massif hero/orbit shot. | “Most procedural terrain starts by deciding where to put terrain. Skyforge starts one level higher: what kind of place is this supposed to be?” | **SKYFORGE** / *Worlds with reasons, not just randomness.* | Massif accepted human review; use SF-IMP-0081 viewer. |
| 0:08-0:20 | Rapid five-family sequence/contact sheet: Massif, Tableland, Spine, Basin, Lobed. Use comparable camera distance and horizon angle. | “That intent produces different families of landform - not five skins on the same noise pattern, but five authored identities compiled through the same production system.” | **Massif · Tableland · Spine · Basin · Lobed** | SF-IMP-0081/0082; all five SMALL / `seed-skyforge` families accepted for exact Minecraft carrier viability. |
| 0:20-0:33 | Approach one island, then move below/orbit underneath it. Hold long enough for the underside to read. Optional thin outline around the owned volume. | “And these islands are not just surfaces. Each one is a finite three-dimensional object, with an upper world, an underside, interior space, and exact boundaries.” | **A complete 3-D world volume** | `/skyforge_morphology_atlas approach`, `below`, `orbit` or Massif equivalents. |
| 0:33-0:45 | Match cut from a clean morphology shot to in-game terrain at player scale. If possible, briefly show the same family from above and ground level. | “Minecraft is the first runtime backend. The authored terrain is carried into a real game engine without making Minecraft the owner of the world model.” | **Authored once. Realized through Minecraft.** | SF-IMP-0081/0082 exact carrier parity; pure integer-Y translation only. |
| 0:45-0:58 | Ecology panorama, then lower forest / upper taiga. Trees, foliage, plants, broad stacked surfaces. | “The terrain also survives real world systems: native land surfaces, vegetation, caves, fluids, structures, and deferred world generation all have to obey the same ownership rules.” | **Not a render. A running world.** | SF-IMP-0080 ecology showcase plus accepted Implementation lifecycle. Keep structures/caves as text unless current visuals are available. |
| 0:58-1:10 | Before-save / after-reopen matched frame or a split-screen using the same camera stop. Overlay a minimal identical-geometry indicator. | “Then the world is saved, reopened in an actual client, and checked against the same deterministic geometry. The screenshot is evidence - not the entire proof.” | **Save → reopen → same world identity** | SF-IMP-0081/0082 persistence/reopen; ecology reopen. Capture matched views if feasible. |
| 1:10-1:22 | Simple semantic flow graphic over restrained world footage: Place identity → morphology → geology/water/ecology evidence → exact volume → Minecraft. | “Behind the visible terrain is a backend-neutral system that keeps world meaning, procedural construction, and game realization separate enough to test and reason about each one.” | **Meaning → world model → exact volume → runtime** | Root README architecture; Authorship/Implementation contracts. |
| 1:22-1:30 | Pull back to a sparse multi-island conceptual composition or clean typography over an accepted island panorama. Do **not** fake a finished province. | “The next goal is convergence: a coherent Bootstrap Province where geography, resources, ecology, travel, industry, and civilization become consequences of the same underlying world.” | **NEXT: Bootstrap Province** / *Roadmap - not yet complete* | Program Charter / issue #224. Must remain explicitly roadmap. |

## Spoken script - core cut

> Most procedural terrain starts by deciding where to put terrain. Skyforge starts one level higher: what kind of place is this supposed to be?
>
> That intent produces different families of landform - Massif, Tableland, Spine, Basin, and Lobed - compiled through the same production system.
>
> And these islands are not just surfaces. Each one is a finite three-dimensional object, with an upper world, an underside, interior space, and exact boundaries.
>
> Minecraft is the first runtime backend. The authored terrain is carried into a real game engine without making Minecraft the owner of the world model.
>
> The terrain also survives real world systems: native land surfaces, vegetation, caves, fluids, structures, and deferred world generation all have to obey the same ownership rules.
>
> Then the world is saved, reopened in an actual client, and checked against the same deterministic geometry. The screenshot is evidence - not the entire proof.
>
> Behind the visible terrain is a backend-neutral system that keeps world meaning, procedural construction, and game realization separate enough to test and reason about each one.
>
> The next goal is convergence: a coherent Bootstrap Province where geography, resources, ecology, travel, industry, and civilization become consequences of the same underlying world.

Target read: approximately 80-90 seconds at a deliberate pace. Cut adjectives before accelerating delivery.

## Exact capture plan

### Morphology hero / family comparison

Accepted viewer choreography already exists.

Massif: use the SF-IMP-0081 production morphology viewer and capture equivalent:

```text
above
approach
below
orbit
```

Tableland / Spine / Basin / Lobed:

```text
/skyforge_morphology_atlas above
/skyforge_morphology_atlas approach
/skyforge_morphology_atlas below
/skyforge_morphology_atlas orbit
```

Use the same FOV, weather/time-of-day where practical, HUD state, render distance, and visual settings for the five-family comparison. Do not alter terrain or add decoration to improve one family relative to another.

Preferred selects:

- **Hero:** Massif `orbit` or `approach`, whichever shows both silhouette and underside most clearly.
- **Five-family contact sheet:** same `approach` or `above` framing for all five.
- **3-D proof:** one `below` plus one `orbit` sequence.

### Ecology

Launch accepted SF-IMP-0080 ecology fixture and use:

```text
/skyforge_ecology panorama
/skyforge_ecology lower_forest
/skyforge_ecology upper_taiga
```

Preferred sequence:

1. `panorama` to establish two distinct broad land volumes;
2. `lower_forest` for ordinary forest identity;
3. `upper_taiga` for a visibly different native ecology.

Do not claim this is complete authored ecology translation. It proves visible native land ecology through accepted exact-volume lifecycle seams.

### Persistence / reopen

Best presentation proof is a matched camera before save and after mutation-inert actual-client reopen.

Required frame discipline:

- same family/member;
- same guided stop / coordinates / yaw / pitch where practical;
- no edits to the saved world between frames;
- caption the pair as **persistence/reopen evidence**, not as proof of all determinism by itself.

If exact matched screenshots are inconvenient, use one accepted world frame plus a concise numeric/digest overlay sourced from the accepted properties. Do not manufacture a fake visual diff.

## Existing retained artifact inspection

The accepted SF-IMP-0082 `Skyforge Showcase Acceptance` run retains per-family workflow artifacts for Massif, Tableland, Spine, Basin, Lobed, the technical showcase, and the ecology showcase. The inspected Massif archive contains acceptance properties and logs but **no image captures**. Therefore Presentation still needs #348 for canonical still acquisition; no existing CI artifact was found that can substitute for the requested screenshots.

This is intentionally not a reason to rerun the full expensive morphology matrix. Use existing accepted local viewer worlds/commands or the cheapest current supported capture path.

## Visual design system

The demo should look engineered, not “AI concept art.”

- Use actual Minecraft captures for every current-capability world claim.
- Minimal typography; one statement per frame.
- Prefer clean dark or neutral negative space around diagrams/captions.
- No fantasy matte painting, fake island render, or generated Minecraft approximation as evidence.
- Diagrams may be stylized, but must be explicitly explanatory graphics rather than masquerading as runtime output.
- Keep Minecraft UI hidden unless the UI itself proves something.
- Avoid raw test counters in the core cut. If a number appears, explain what risk it retires.

## Optional technical branch - +60 seconds

For technical reviewers, append:

1. semantic descriptor / recipe example;
2. immutable procedural graph excerpt;
3. exact ownership / stacked-volume diagram;
4. deterministic geometry digest / canonical evidence identity;
5. neutral-module dependency boundary showing Minecraft isolated in the backend module;
6. one concise persistence/admission invariant.

Suggested technical one-liner:

> Semantic descriptors compile through versioned recipes into immutable procedural graphs and deterministic scalar fields; those fields define exact finite 3-D ownership, and the NeoForge adapter realizes that output without introducing Minecraft ontology into the neutral engine.

## Claims deliberately omitted from the core cut

Do not visually imply current completion of:

- multiple production backends;
- full multi-seed/multi-scale/hybrid/regional morphology closure;
- literal authored petroleum or metal deposits in Minecraft;
- completed authored waterfalls/channels;
- final settlements, runways, docks, civilizations, or regional infrastructure;
- a finished Bootstrap Province;
- final player-facing release quality.

Those belong to future revisions only after their owning lanes accept the corresponding production boundary.

## Definition of done

PRES-0003 is presentation-complete when:

- the 90-second narrative and shot order remain correct against the current claim registry;
- at least one canonical actual Skyforge capture exists for morphology, underside/3-D volume, Minecraft player-scale realization, and ecology;
- persistence/reopen is represented without a fabricated visual claim;
- roadmap content is visibly labeled as roadmap;
- the sequence works for a nontechnical viewer without narration-dependent jargon;
- a technical reviewer can follow evidence pointers for every substantive present-tense claim.
