# Skyforge music and soundtrack authorship

This directory records Skyforge’s original soundtrack lane as an engineering and authorship artifact alongside world synthesis, runtime implementation, and content design.

The score exists to develop a coherent musical language for exploration, altitude, scale, environmental identity, rare encounters, and eventual adaptive runtime scoring.

## Lane milestones

- **MUS-0001** — initial soundtrack source and authorship persistence.
- **MUS-0002** — executable MIDI/source/library verification in normal CI.
- **MUS-0003** — deterministic Track 00 repair-candidate qualification.
- **MUS-0004** — V2F2A peak-handoff repair promoted to canonical Track 00 MIDI.
- **MUS-0005** — corrected 104 BPM Track 00 BBCSO reference render accepted.

## Working principles

- preserve accepted source identity rather than silently rewriting history;
- distinguish MIDI/source acceptance, qualitative render acceptance, and final mastering;
- distinguish synthetic diagnostics from authoritative BBCSO renders;
- record exact musical roles, rejected approaches, hashes, and plugin-state dependencies;
- freeze successful cues rather than polishing indefinitely;
- do not commit third-party game audio, MIDI, notation, sample content, or copyrighted assets;
- do not place large WAV masters in ordinary Git without an explicit artifact policy.

## Current cue slate

| Track | Title / role | Status | Primary proof |
| --- | --- | --- | --- |
| 00 | **A Windborne Fantasia** — first ascent / panorama / rare revelation | Frozen; V2F2A MIDI canonical; repaired BBCSO reference render accepted | exact MIDI manifest + external WAV checksum/render record |
| 01 | **Rambling Through the Gentle Blue** — ordinary daylight exploration | Frozen; structural/range audit passed | persisted authoring MIDI + accepted BBCSO master record |
| 02 | **The Lord of Empty Miles** — legendary dragon territory / awe / panic | Frozen; BBCSO percussion repair accepted | corrected MIDI + repaired BBCSO render record |
| 03 | **Count the Leagues** — lonely / ponderous exploration | Frozen / complete | accepted 72-bar Second Horizon MIDI + BBCSO master record |
| 06 | storm / weather traversal | Composition frozen at Draft 02.3; exact canonical MIDI missing | accepted BBCSO-render review + development record |
| — | principal Skyforge theme candidate | Motif captured; composition pending | user-approved voice-transcription source |

## Canonical MIDI identities

| Cue | Uncompressed MIDI SHA-256 | Repository source |
| --- | --- | --- |
| Track 00 V2F2A peak handoff | `c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73` | `assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz` |
| Track 01 Draft 5.1 | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` | `assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz` |
| Track 02 D4.2 BBCSO percussion repair | `79be46fd4ca2d712a727a571265d8521740148a550e05c04288cd14d5b9bf50d` | `assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz` |
| Track 03 Second Horizon | `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1` | `assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz` |

Track 00 V2F.1 remains preserved as the pre-repair historical reference. Track 06 deliberately has no canonical source hash until the exact accepted Draft 02.3 MIDI is recovered.

## Accepted render identities

### Track 00 V2F2A reference render

- external WAV SHA-256: `c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`;
- stereo 44.1 kHz, 32-bit float;
- 127.733151927 seconds;
- approximately -20.4 LUFS, 16.6 LU LRA, -2.3 dBTP;
- accepted as repaired-orchestration listening proof;
- not yet a final GAME or OST master.

Machine-readable record:

`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`

## Documents

- [Production workflow](production-workflow.md)
- [Musical language](musical-language.md)
- [Library / MIDI audit](library-midi-audit-2026-09-07.md)
- [MUS-0005 Track 00 reference-render acceptance](MUS-0005-track00-reference-render-acceptance.md)
- [Track 00 — A Windborne Fantasia](tracks/track-00-a-windborne-fantasia.md)
- [Track 01 — Rambling Through the Gentle Blue](tracks/track-01-rambling-through-the-gentle-blue.md)
- [Track 02 — The Lord of Empty Miles](tracks/track-02-the-lord-of-empty-miles.md)
- [Track 03 — Count the Leagues](tracks/track-03-contemplative-development.md)
- [Track 06 — storm development](tracks/track-06-storm-development.md)
- [Principal-theme development](tracks/principal-theme-development.md)

## Current development boundary

Track 00 source repair and qualitative repaired-render acceptance are closed.

Remaining pre-composition maintenance:

1. recover Track 11 Harp/Celeste plugin state for the frozen cues when original CWP files are available;
2. recover and verify the exact accepted Track 06 Draft 02.3 MIDI;
3. leave Track 01 and Track 03 note data untouched;
4. create final Track 00 GAME/OST masters only when packaging or release work requires them;
5. resume principal-theme composition after the persistence boundary is closed.

Adaptive implementation remains downstream of accepted linear compositions.

## Automated source verification

Run:

```shell
python3 scripts/music/verify_music_sources.py
```

Normal CI verifies canonical MIDI/source identity, structure, conductor metadata, BBCSO range declarations, special-lane provenance, and accidental ordinary-Git WAV storage. External render records preserve exact WAV identity and disposition; they do not place the corresponding audio bytes in ordinary Git.
