# MUS-0001 — Score foundation and persistence record

**Status:** In review

## Scope

MUS-0001 establishes a persistent repository record for Skyforge soundtrack authorship through the first three frozen cues and the active Track 03 contemplative-development boundary.

This milestone is documentation/source-persistence work. It does not alter terrain generation, world authorship, Minecraft integration, build behavior, or runtime contracts.

## Problem

The soundtrack lane had accumulated substantial accepted musical and production knowledge outside the repository:

- frozen cue identities;
- exact authoring MIDI sources;
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
7. accepted cue source identities are checksum-pinned;
8. rejected musical approaches remain visible where they materially explain current decisions.

## Persisted records

The milestone includes:

- frozen Track 00–02 authoring MIDI sources under `assets/music/source/frozen/`;
- active Track 03 source sketches under `assets/music/source/`;
- a soundtrack-lane index and status table;
- the canonical 19-track BBCSO authoring schema;
- the current composition/orchestration/render/master workflow;
- project-level musical vocabulary and anti-patterns;
- Track 00 acceptance record;
- Track 01 acceptance record;
- Track 02 first-draft freeze / range-experiment record;
- Track 03 development history through the current melodic-gate method.

The larger MIDI files are stored as `.mid.gz` to keep exact source bytes compact in normal Git history. Their uncompressed SHA-256 identities remain the canonical verification boundary.

## Frozen cue identities

| Cue | Canonical uncompressed MIDI SHA-256 | Repository source |
| --- | --- | --- |
| Track 00 — A Windborne Fantasia | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` | `assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.mid.gz` |
| Track 01 — Rambling Through the Gentle Blue | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` | `assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz` |
| Track 02 — The Lord of Empty Miles | `73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28` | `assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz` |

Active Track 03 Sketch 6 uncompressed identity:

```text
e41498d0c6fa0b29dc4f4ec5b5cf4a7d2dd44967dd707b15e905fff8c26cfec8
```

Repository source:

```text
assets/music/source/track-03-sketch-06-developing-melody.mid.gz
```

## Verification

This milestone does not alter executable terrain/runtime behavior.

Verification requirements:

- Markdown files render coherently;
- source archives decompress to valid MIDI files;
- uncompressed MIDI SHA-256 values match the documented identities;
- cue names, roles, tempos/meters, major form data, and mastering measurements match the accepted working record;
- no third-party reference assets are present.

A full Gradle terrain/runtime evidence regeneration is not semantically required because MUS-0001 does not change executable code or canonical terrain identity. Normal repository CI may still run on the pull request.

## Current development boundary

Track 03 remains intentionally unfrozen.

Sketch 6 is the current melodic gate. It tests a stronger developmental model:

```text
Theme A
-> genuinely different Theme B
-> fragment / exchange / synthesis
-> Theme A return
-> Theme B changes the ending
```

Full orchestration is deferred until that musical argument passes by ear.

## Audio-master persistence boundary

The accepted GAME/OST WAV masters are important evidence, but they are large derived artifacts. MUS-0001 intentionally keeps them out of ordinary Git object history.

Current policy:

- source MIDI: persist in Git;
- master identities / measurements / roles: persist in documentation;
- large rendered audio: retain externally until the project deliberately adopts Git LFS, GitHub Release assets, or another versioned artifact store.

This avoids repository bloat without treating the audio masters as disposable.

## Deferred work

MUS-0001 does not yet:

- establish a versioned large-audio artifact store;
- implement runtime audio playback;
- implement adaptive music state machines;
- choose final loop/stem boundaries;
- establish night/cave/storm/home/faction vocabularies;
- declare a project-wide leitmotif.

Those decisions should follow successful musical material and concrete runtime requirements rather than be invented prematurely.
