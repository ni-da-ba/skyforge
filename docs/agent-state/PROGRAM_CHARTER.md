# Skyforge Program Charter

**Status:** Canonical program-level agent charter  
**Updated:** 2026-09-07 (America/Chicago)

## Durable-memory rule

Skyforge development state lives in the repository.

```text
Conversation = active working session
Repository   = durable project memory
Git/tests    = authoritative evidence
```

A fresh agent must reconstruct state from this charter, its lane state, cross-lane contracts, recent
PRs/commits, and current source/tests. Conversational recollection may help during a session but does
not supersede repository evidence.

## Long-range objective

Skyforge is a deterministic, backend-neutral procedural world-synthesis system whose first complete
game realization is Minecraft 1.21.1 / NeoForge.

The Minecraft realization succeeds when a new player can naturally discover that:

- geography and verticality materially change survival and travel;
- atmosphere is a shared world system;
- cheap personal mobility exists without replacing aircraft logistics;
- regional specialization creates routes, freight, and infrastructure;
- ecology, structures, civilizations, threats, and dimensions reflect authored world semantics;
- mature industry and computing unlock capabilities rather than redundant material tiers;
- distant negative space remains meaningful;
- the result remains recognizably Minecraft while enabling experiences ordinary Minecraft does not.

## World-realization convergence

The intended production flow is:

```text
regional intent
-> island morphology
-> geology / materials
-> hydrology
-> caves / structures
-> soils / ecology
-> Minecraft realization
-> persistent gameplay
```

Correctness is mandatory but not sufficient. Work should increasingly be judged by whether it brings
the actual production Minecraft world closer to a fast, visually legible, semantically coherent,
playable Skyforge experience.

## Major lanes

### Authorship

Owns backend-neutral world meaning:

- semantic geography and morphology;
- ecology/hydrology/geology/environmental fields;
- region/province/island composition;
- deterministic authored descriptors and constraints.

Authorship must not encode Minecraft implementation details merely to simplify the adapter.

### Implementation

Owns backend realization and runtime correctness:

- Minecraft/NeoForge lifecycle integration;
- exact-volume realization and ownership;
- native-content adaptation;
- performance, persistence, save/reload, synchronization, and acceptance fixtures.

Implementation consumes authored semantics through explicit contracts rather than inventing competing
world meaning.

### Content / Experience

Owns the Minecraft game assembled on top of authored/implemented worlds:

- mod/content selection and normalization;
- progression, mobility, freight, industry, computing, ecology, threats, structures, civilizations;
- Bootstrap Province and onboarding;
- executable gameplay acceptance.

Content source priority is mandatory:

1. vanilla Minecraft;
2. existing mods/libraries;
3. datapacks/configuration/integration;
4. thin Skyforge adapters;
5. bespoke Skyforge content only for demonstrated gaps.

Skyforge owns **meaning**; it does not need to author every asset.

### Music / Audio

Owns original score/audio authorship and production evidence assigned to the lane:

- musical language, themes, motifs, cue roles, form, orchestration, and arrangement;
- exact authoring-source identity and persistence;
- library/instrument alignment plus plugin/patch-state provenance needed for reproducibility;
- render/master acceptance records and human listening gates;
- soundtrack source/artifact storage policy.

The current MUS lane is soundtrack-focused. Ambience, sound effects, or other audio domains enter this
lane only when explicitly assigned rather than by assumption.

Music / Audio does not invent world or gameplay truth merely to choose a cue. Authorship owns world
semantics; Content / Experience owns gameplay/progression meaning; Implementation owns concrete
Minecraft playback, transition, persistence, and client/runtime integration. Adaptive scoring must
consume explicit accepted cross-lane state and accepted audio-source identities rather than
reverse-discovering either.

### Audit / Program Health

Owns repository-wide convergence assurance and agent/workflow health:

- reconstruct authoritative state from current `main`, source/tests, merged history, lane ledgers, and cross-lane contracts;
- independently check producer acceptance evidence, manual gates, stale state, and cross-lane drift;
- monitor execution health using repository-visible signals such as branch divergence, repeated failure/rework without new information, contradictory or lagging handoffs, abandoned branch growth, repeated resynchronization churn, and long commit sequences without a meaningful acceptance boundary;
- flag when a producer should stop extending a stale execution branch, checkpoint durable state, and restart from a fresh session/current-main reconstruction;
- distinguish intentional dormant/reserved work from an actively stuck or looping agent.

Audit cannot directly inspect another agent's private context window. "Context blowup" is therefore treated as an evidence-based process-risk inference from repository behavior and explicit user/session reports, not as hidden-state knowledge.

Audit does not seize producer-lane technical ownership. Authorship still owns world meaning, Implementation runtime realization, Content game experience, and Music / Audio source authorship. Audit may request synchronization, fresh execution, narrower scope, or state repair; producer lanes own their technical fixes and producer acceptance unless a genuine cross-lane integration change requires otherwise.

## Working method

Prefer:

```text
DESIGN -> EXECUTABLE SPECIMEN -> TEST/PLAY -> DECISION
```

over indefinite audit expansion.

Sparse, coherent world composition is preferred to solving weak geography with content density.

## Acceptance vocabulary

Agent-state files must distinguish:

- **MERGED / ACCEPTED** — landed and supported by the required evidence for its claim;
- **IN PROGRESS** — active branch/PR/issue, not yet accepted;
- **PROPOSED** — design direction or merged design record without runtime acceptance;
- **MANUAL VERIFICATION REQUIRED** — machine evidence is insufficient for the remaining claim.

Existing milestone numbering and historical acceptance records remain intact.

## State-update rule

Each lane updates its state document when it:

- reaches a meaningful merge/acceptance boundary;
- changes an architectural or cross-lane contract;
- discovers a significant defect/hazard/technical debt;
- hands work to another lane or agent instance.

Detailed evidence belongs in source, tests, PRs, commits, reviews, or dedicated technical docs; state
files should remain concise pointers.
