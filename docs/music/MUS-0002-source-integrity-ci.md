# MUS-0002 — canonical music-source integrity CI

**Status:** In progress

## Purpose

MUS-0002 converts the source-persistence lessons from MUS-0001 into an executable repository gate.

The Track 02 persistence incident demonstrated that a correct-looking filename/path is not sufficient evidence that a stored binary is the intended cue. MUS-0002 therefore makes canonical soundtrack source identity machine-checkable on every relevant pull request and `main` update.

## Scope

The verifier at `tools/verify_music_sources.py` uses only Python's standard library and checks the four currently canonical persisted cues:

- Track 00 — A Windborne Fantasia;
- Track 01 — Rambling Through the Gentle Blue;
- Track 02 — The Lord of Empty Miles;
- Track 03 — Count the Leagues.

For each cue it verifies:

1. canonical source and manifest paths exist;
2. gzip decompression succeeds;
3. decompressed SHA-256 equals the accepted cue identity and manifest identity;
4. the file is Standard MIDI File format 1;
5. the file has conductor + 19 instrument tracks at 480 PPQ;
6. conductor tempo and meter match the accepted cue;
7. instrument-lane names remain in canonical order;
8. lanes declared unused for PERC/TP remain note-empty;
9. Track 02 PERC remains exactly the accepted BBCSO Untuned Percussion map/counts;
10. the documented Track 00 and Track 02 range-exception counts do not silently drift.

The dedicated workflow is `.github/workflows/music-source-integrity.yml`.

## Deliberate boundary

The verifier does **not** infer DAW/plugin state that MIDI cannot prove. In particular, it does not guess Harp versus Celeste on Track 11 HC, nor does it pretend a generic MIDI lane can select a BBCSO preset.

Those facts remain explicit source-session/manually recovered provenance gates.

The verifier also does not silently "fix" known range exceptions. Track 00 remains behind its human BBCSO repair A/B, while the two Track 02 Trumpet C#6 events remain a documented frozen-source exception unless that cue is deliberately reopened.

## Acceptance

MUS-0002 is accepted when:

- the dedicated workflow passes at exact PR head;
- normal repository CI also passes at exact PR head;
- the workflow is merged to `main`;
- a post-merge run proves the gate executes successfully on the accepted repository state;
- the Music lane ledger records MUS-0002 as the highest accepted repository-health milestone.
