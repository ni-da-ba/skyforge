# Skyforge music and soundtrack authorship

This directory records the project’s original soundtrack lane as an engineering/authorship artifact alongside terrain, runtime, and world-authorship work.

The soundtrack is not treated as detached promotional media. Its purpose is to develop a coherent musical language for exploration, scale, environmental identity, rare encounters, and eventually adaptive runtime scoring.

## Lane identifier

The initial persistence milestone is **MUS-0001**.

MUS-0001 captures score development through the first three frozen cues and the active Track 03 contemplative experiment. The lane uses the same evidence-first principles as the rest of the repository:

- preserve accepted source identity instead of silently rewriting history;
- distinguish synthetic diagnostics from authoritative BBCSO renders;
- record exact musical roles and rejected approaches;
- freeze successful cues rather than polishing indefinitely;
- keep third-party reference material analytical only;
- do not commit third-party game audio, MIDI, notation, sample content, or copyrighted assets.

## Current cue slate

| Track | Title / working role | Status | Primary proof |
| --- | --- | --- | --- |
| 00 | **A Windborne Fantasia** — first ascent / panorama / rare revelation | Frozen | persisted authoring MIDI + BBCSO GAME/OST master record |
| 01 | **Rambling Through the Gentle Blue** — ordinary daylight exploration | Frozen | persisted authoring MIDI + BBCSO GAME/OST master record |
| 02 | **The Lord of Empty Miles** — legendary dragon territory / awe / panic | Frozen as successful first draft | persisted authoring MIDI + BBCSO GAME/OST master record |
| 03 | contemplative / lonely / ponderous cue | Active development | melodic-gate sketches; current boundary is Sketch 6 developmental A/B argument |

## Canonical source identities

The accepted Track 00–02 MIDI sources and current Track 03 source are persisted under `assets/music/source/`.

| Cue | Uncompressed MIDI SHA-256 |
| --- | --- |
| Track 00 V2F.1 | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` |
| Track 01 Draft 5.1 | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` |
| Track 02 Draft 4.2 | `73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28` |
| Track 03 Sketch 6 | `e41498d0c6fa0b29dc4f4ec5b5cf4a7d2dd44967dd707b15e905fff8c26cfec8` |

Frozen source paths:

```text
assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.mid.gz
assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz
assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz
```

Active source path:

```text
assets/music/source/track-03-sketch-06-developing-melody.mid.gz
```

The larger MIDI sources are gzip-compressed only for compact Git storage. Decompression reproduces the exact authoring MIDI represented by the hashes above.

Rendered WAV masters remain outside ordinary Git history pending an explicit large-artifact strategy; their mastering measurements and accepted roles remain preserved in the cue records.

## Documents

- [Production workflow](production-workflow.md)
- [Musical language](musical-language.md)
- [Track 00 — A Windborne Fantasia](tracks/track-00-a-windborne-fantasia.md)
- [Track 01 — Rambling Through the Gentle Blue](tracks/track-01-rambling-through-the-gentle-blue.md)
- [Track 02 — The Lord of Empty Miles](tracks/track-02-the-lord-of-empty-miles.md)
- [Track 03 — contemplative development record](tracks/track-03-contemplative-development.md)

## Current development boundary

The immediate musical boundary is Track 03.

Early experiments established what does **not** solve “lonely, ponderous, thoughtful”:

1. sparse Skyforge ambience alone reads as familiar atmosphere rather than thought;
2. silence plus isolated statements can become underwritten;
3. continuous accompaniment without a memorable lead becomes wallpaper;
4. a formally structured line can still feel arbitrary;
5. one memorable sentence repeatedly transposed is not sufficient development.

The accepted current method remains a **melodic gate**, but Sketch 6 strengthens it:

> Theme A -> Theme B -> development/synthesis -> Theme A return -> Theme B changes the answer.

The cue should feel as if it has considered and revised an idea, not merely repeated it.

## Future runtime use

Adaptive implementation remains downstream of accepted linear compositions. Expected later runtime concepts include:

- horizontal resequencing;
- vertical stems;
- reveal / state stingers;
- musical quantization of state transitions;
- hysteresis and minimum-state durations to avoid state thrashing;
- semantic distinction between awe, menace, panic, relief, and ordinary exploration.

The score should interpret gameplay meaning rather than react mechanically to simulation ticks.
