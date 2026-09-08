# Skyforge music and soundtrack authorship

This directory records the project’s original soundtrack lane as an engineering/authorship artifact alongside terrain, runtime, and world-authorship work.

The soundtrack is not treated as detached promotional media. Its purpose is to develop a coherent musical language for exploration, scale, environmental identity, rare encounters, and eventually adaptive runtime scoring.

## Lane identifier

The initial persistence milestone is **MUS-0001**. **MUS-0002** adds executable source/library verification to normal repository CI.

The lane follows the same evidence-first principles as the rest of the repository:

- preserve accepted source identity instead of silently rewriting history;
- distinguish synthetic diagnostics from authoritative BBCSO renders;
- record exact musical roles and rejected approaches;
- freeze successful cues rather than polishing indefinitely;
- keep third-party reference material analytical only;
- do not commit third-party game audio, MIDI, notation, sample content, or copyrighted assets;
- verify library range, preset state, track alignment, and persisted source identity before a MIDI is declared canonical.

## Current cue slate

| Track | Title / working role | Status | Primary proof |
| --- | --- | --- | --- |
| 00 | **A Windborne Fantasia** — first ascent / panorama / rare revelation | Frozen; library-range repair candidate identified | persisted authoring MIDI + BBCSO GAME/OST master record |
| 01 | **Rambling Through the Gentle Blue** — ordinary daylight exploration | Frozen; structural/range audit passed | persisted authoring MIDI + BBCSO GAME/OST master record |
| 02 | **The Lord of Empty Miles** — legendary dragon territory / awe / panic | Frozen; BBCSO percussion repair accepted | corrected persisted authoring MIDI + accepted repaired BBCSO render |
| 03 | **Count the Leagues** — lonely / ponderous / thoughtful exploration | Frozen / complete | accepted 72-bar Second Horizon MIDI + BBCSO GAME/OST master record |
| 06 | storm / weather traversal | Composition frozen at Draft 02.3; canonical source persistence pending | accepted BBCSO render + development record; exact final MIDI not yet in repository |
| — | principal Skyforge theme candidate | Motif captured; composition pending | user-approved voice transcription source |

## Canonical source identities

| Cue | Uncompressed MIDI SHA-256 | Repository source |
| --- | --- | --- |
| Track 00 V2F.1 | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` | `assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.mid.gz` |
| Track 01 Draft 5.1 | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` | `assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz` |
| Track 02 D4.2 BBCSO percussion repair | `79be46fd4ca2d712a727a571265d8521740148a550e05c04288cd14d5b9bf50d` | `assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz` |
| Track 03 Count the Leagues — Second Horizon | `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1` | `assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz` |

Track 06 is deliberately **not** assigned a canonical source hash until the exact accepted Draft 02.3 MIDI is recovered and verified. Do not reconstruct it from documentation or conversational memory.

The larger MIDI sources are gzip-compressed only for compact Git storage. Decompression must reproduce the exact authoring MIDI represented by the documented hash.

Rendered WAV masters remain outside ordinary Git history pending an explicit large-artifact strategy; their mastering measurements and accepted roles remain preserved in the cue records.

## Documents

- [Production workflow](production-workflow.md)
- [Musical language](musical-language.md)
- [Library / MIDI audit](library-midi-audit-2026-09-07.md)
- [Track 00 — A Windborne Fantasia](tracks/track-00-a-windborne-fantasia.md)
- [Track 01 — Rambling Through the Gentle Blue](tracks/track-01-rambling-through-the-gentle-blue.md)
- [Track 02 — The Lord of Empty Miles](tracks/track-02-the-lord-of-empty-miles.md)
- [Track 03 — Count the Leagues](tracks/track-03-contemplative-development.md)
- [Track 06 — storm development](tracks/track-06-storm-development.md)
- [Principal-theme development](tracks/principal-theme-development.md)

## Current development boundary

Before further composition, the library/MIDI audit must be closed:

1. Track 00 has a controlled BBCSO range-repair candidate to audition rather than a silent canonical rewrite.
2. Track 01 and Track 03 MIDI structure/ranges are clean, but the actual Track 11 Harp/Celeste plugin technique remains project-state dependent and should be recorded when the source CWP is available.
3. Track 02 is repaired and accepted; two legacy trumpet C#6 notes remain one semitone beyond the documented Discover trumpet range and are retained as a known non-blocking frozen-source issue pending any future A/B.
4. Track 06 needs the exact accepted Draft 02.3 MIDI recovered before canonical persistence or final closeout.
5. The principal-theme motif remains protected but undeveloped until the maintenance boundary is complete.

Adaptive implementation remains downstream of accepted linear compositions.


## Automated source verification

Run the Music / Audio repository-health gate locally with:

```shell
python3 scripts/music/verify_music_sources.py
```

Normal GitHub CI runs the same verifier immediately after change-impact classification and before Gradle/JDK setup whenever Music source/verifier state is affected. Manual dispatches and unknown comparison bases fail safe to verification; unrelated documentation-only changes do not pay the Music gate cost. Canonical manifest/source mismatches, structural MIDI misalignment, undeclared BBCSO range violations, and special-lane provenance/mapping errors therefore fail relevant pull requests instead of remaining review-only findings.
