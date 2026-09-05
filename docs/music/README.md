# Skyforge music and soundtrack authorship

This directory records the project’s original soundtrack lane as an engineering/authorship artifact alongside terrain, runtime, and world-authorship work.

The soundtrack is not treated as detached promotional media. Its purpose is to develop a coherent musical language for exploration, scale, environmental identity, rare encounters, and eventually adaptive runtime scoring.

## Lane identifier

The initial persistence milestone is **MUS-0001**.

MUS-0001 captures the score-development process through the first three frozen cues and the active Track 03 contemplative experiment. The lane uses the same evidence-first principles as the rest of the repository:

- preserve accepted source identity instead of silently rewriting history;
- distinguish synthetic diagnostics from authoritative BBCSO renders;
- record exact musical roles and rejected approaches;
- freeze successful cues rather than polishing indefinitely;
- keep third-party reference material analytical only;
- do not commit third-party game audio, MIDI, notation, sample content, or copyrighted assets.

## Current cue slate

| Track | Title / working role | Status | Primary proof |
| --- | --- | --- | --- |
| 00 | **A Windborne Fantasia** — first ascent / panorama / rare revelation | Frozen | 72-bar BBCSO-orchestrated source and GAME/OST masters |
| 01 | **Rambling Through the Gentle Blue** — ordinary daylight exploration | Frozen | 56-bar BBCSO-orchestrated source and GAME/OST masters |
| 02 | **The Lord of Empty Miles** — legendary dragon territory / awe / panic | Frozen as successful first draft | 92-bar BBCSO-orchestrated source and GAME/OST masters |
| 03 | contemplative / lonely / ponderous cue | Active development | melodic-gate sketches; current direction is song-like strings over a preserved piano/harmonic bed |

## Canonical source identities

The current canonical MIDI sources outside the repository were hashed before this record was created:

| Cue | SHA-256 |
| --- | --- |
| Track 00 V2F.1 | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` |
| Track 01 Draft 5.1 | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` |
| Track 02 Draft 4.2 | `73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28` |
| Track 03 Sketch 5 | `013fc776b59c3ae9d7f356d4f65f8423794d64d87514545918f6e6deaa393e19` |

Rendered WAV masters are intentionally not added in MUS-0001. They are large derived artifacts rather than compact authoring sources. Their mastering measurements and accepted roles are preserved in the track records.

## Documents

- [Production workflow](production-workflow.md)
- [Musical language](musical-language.md)
- [Track 00 — A Windborne Fantasia](tracks/track-00-a-windborne-fantasia.md)
- [Track 01 — Rambling Through the Gentle Blue](tracks/track-01-rambling-through-the-gentle-blue.md)
- [Track 02 — The Lord of Empty Miles](tracks/track-02-the-lord-of-empty-miles.md)
- [Track 03 — contemplative development record](tracks/track-03-contemplative-development.md)

## Current development boundary

The immediate musical boundary is Track 03.

Three early full-cue attempts established what does **not** solve “lonely, ponderous, thoughtful”:

1. sparse Skyforge ambience alone reads as familiar atmosphere rather than thought;
2. silence plus isolated statements can become underwritten;
3. continuous accompaniment without a memorable lead idea becomes wallpaper.

The accepted current method is therefore a **melodic gate**: a short piano/string sketch must work as music before it is expanded into a complete cue. The active Sketch 5 replaces bar-by-bar string construction with a recognizable phrase-scale A / A′ / B / A″ melodic architecture.

## Future runtime use

Adaptive implementation remains downstream of accepted linear compositions. Expected later runtime concepts include:

- horizontal resequencing;
- vertical stems;
- reveal / state stingers;
- musical quantization of state transitions;
- hysteresis and minimum-state durations to avoid state thrashing;
- semantic distinction between awe, menace, panic, relief, and ordinary exploration.

The score should interpret gameplay meaning rather than react mechanically to simulation ticks.
