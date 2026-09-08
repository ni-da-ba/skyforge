# MUS-0003 — Track 00 repair-candidate integrity

**Status:** In progress

## Scope

MUS-0003 makes the two existing Track 00 BBCSO range-repair A/B inputs deterministic repository artifacts without promoting either one to canonical source.

The milestone is deliberately machine-only. It retires candidate-integrity risk before the remaining human listening gate.

## Problem

Track 00 V2F.1 is frozen but contains accepted, explicitly logged BBCSO Discover range defects:

- Horns: MIDI 78 x4, 81 x4, 83 x2 above the Discover Horns a4 F5 ceiling;
- Violas: MIDI 47 x3 below the Discover Viola C3 floor.

Two non-canonical repair candidates already exist:

- V2F2A — conservative peak handoff;
- V2F2B — continuous brass-crest handoff.

Their hashes and intended orchestration strategies are documented, but MUS-0002 only parses non-canonical MIDI archives. It does not pin repair-candidate identity, prove that the original defect set is the one being repaired, prove the candidate is range-clean, or constrain accidental changes to the intended lanes.

## Contract

Each Track 00 repair candidate receives a machine-readable `.candidate.json` contract.

Verification must prove:

1. base V2F.1 and candidate gzip sources exist and decompress as MIDI;
2. base and candidate uncompressed SHA-256 identities match their pinned values;
3. both remain format-1, conductor + 19-lane files with the same PPQ and canonical lane order;
4. tempo and meter remain 104 BPM / 6/8;
5. the base source has exactly the already-accepted Horn/Viola range defects;
6. the candidate has no ordinary-lane BBCSO Discover range exception;
7. only Horns (6), Trumpets (7), Violas (17), and Celli (18) differ at raw MIDI-track payload level.

The last rule is intentionally narrow: it prevents an A/B repair artifact from silently changing unrelated orchestration, conductor data, Harp/Celeste state, percussion, tuned percussion, piano, winds, other brass, or other strings.

## Non-claims

MUS-0003 does **not** prove:

- that Candidate A or Candidate B sounds better;
- that either repair preserves the accepted revelation/departure character;
- that unresolved Track 11 Harp/Celeste CWP state is recovered;
- that the Track 00 canonical source should change.

Those remain human/source-recovery gates.

## Acceptance

Run:

```shell
python3 scripts/music/verify_music_sources.py
```

Then require exact-head normal CI before merge.

After machine acceptance, the remaining Track 00 decision is the existing BBCSO A/B at bars 55-61.
