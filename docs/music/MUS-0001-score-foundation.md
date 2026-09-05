# MUS-0001 — Score foundation and persistence record

**Status:** In review

## Scope

MUS-0001 establishes a persistent repository record for Skyforge soundtrack authorship through the first three frozen cues and the active Track 03 contemplative-development boundary.

This milestone is documentation/source-persistence work. It does not alter terrain generation, world authorship, Minecraft integration, build behavior, or runtime contracts.

## Problem

The soundtrack lane had accumulated substantial accepted musical and production knowledge outside the repository:

- frozen cue identities;
- orchestration semantics;
- repeatable BBCSO/Cakewalk workflow;
- mastering measurements;
- cue-specific failure/revision history;
- project-level musical language;
- adaptive-scoring design principles;
- active Track 03 melodic experiments.

Leaving that work only in conversational history made it fragile relative to the repository’s evidence-driven engineering record.

## Invariants

MUS-0001 preserves the following project expectations:

1. backend-neutral engine code remains untouched;
2. no Minecraft/NeoForge behavior changes;
3. no generated worlds or runtime output is committed;
4. no third-party music, notation, MIDI, samples, or game assets are committed;
5. reference listening is analytical only;
6. large derived WAV masters are not silently added to ordinary Git history;
7. frozen cue source identities are recorded with SHA-256 hashes;
8. rejected musical approaches remain visible where they materially explain current decisions.

## Persisted records

The milestone adds:

- the active Track 03 Sketch 5 MIDI source under `assets/music/source/`;

- a soundtrack-lane index and status table;
- the canonical 19-track BBCSO authoring schema;
- the current composition/orchestration/render/master workflow;
- project-level musical vocabulary and anti-patterns;
- Track 00 acceptance record;
- Track 01 acceptance record;
- Track 02 first-draft freeze / range-experiment record;
- Track 03 development history through the current melodic-gate method.

## Frozen cue identities

| Cue | Canonical authoring identity |
| --- | --- |
| Track 00 — A Windborne Fantasia | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` |
| Track 01 — Rambling Through the Gentle Blue | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` |
| Track 02 — The Lord of Empty Miles | `73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28` |

Active Track 03 Sketch 5 identity:

```text
013fc776b59c3ae9d7f356d4f65f8423794d64d87514545918f6e6deaa393e19
```

## Verification

This is a documentation-only milestone.

Verification requirements:

- Markdown files render coherently;
- all repository paths remain documentation-only;
- frozen cue names, roles, tempos/meters, major form data, and mastering measurements match the accepted working record;
- source hashes match the exact local canonical MIDI files used when MUS-0001 was authored;
- no third-party reference assets are present.

A full Gradle terrain/runtime evidence regeneration is not semantically required because MUS-0001 does not change executable code or canonical terrain identity. Normal repository CI may still run on the pull request.

## Current development boundary

Track 03 remains intentionally unfrozen.

The next musical decision is whether Sketch 5’s phrase-scale string melody is memorable enough to pass the melodic gate. Full orchestration is deferred until that gate passes.

## Deferred work

MUS-0001 checksum-pins but does not yet import the frozen Track 00–02 MIDI sources. Their identities are preserved above so a later source-ingest pass can verify exact bytes.

MUS-0001 also does not yet:

- commit large WAV masters;
- define a soundtrack packaging/release format;
- implement runtime audio playback;
- implement adaptive music state machines;
- choose final loop/stem boundaries;
- establish night/cave/storm/home/faction vocabularies;
- declare a project-wide leitmotif.

Those decisions should follow successful musical material and concrete runtime requirements rather than be invented prematurely.
