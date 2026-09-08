# MUS-0002 — automated soundtrack source verification

**Status:** Accepted

## Scope

MUS-0002 turns the accepted MUS-0001 persistence/library rules into a deterministic repository-health gate.

It adds a standard-library Python verifier and invokes it from normal GitHub CI before the Java/Gradle build.

## Claims

The verifier must fail closed when any of these repository facts drift:

1. a persisted MIDI or MIDI.GZ artifact is structurally unreadable;
2. a canonical manifest points to a missing or non-MIDI.GZ source;
3. decompression does not reproduce the manifest-pinned MIDI SHA-256;
4. optional pinned gzip identity changes;
5. canonical MIDI is not format 1 with conductor + 19 ordered instrument lanes;
6. pinned PPQ / tempo / meter / key-signature metadata disagrees with the file;
7. an ordinary BBCSO Discover lane contains undeclared out-of-range note-ons;
8. a declared frozen range exception does not match the exact actual note/count set;
9. Track 11 HC contains authored notes without manifest plugin-state provenance;
10. Track 12 PERC or Track 13 TP use is not accompanied by the required cue-level preset/mapping state;
11. large WAV masters are accidentally added to ordinary Git music assets.

## Non-claims

MUS-0002 does not:

- determine whether a musical phrase is good;
- replace BBCSO listening gates;
- infer Harp versus Celeste from MIDI;
- infer a missing Track 06 source;
- promote Track 00 repair candidates;
- rewrite accepted note data to satisfy range checks;
- implement runtime audio.

Human listening and original-CWP recovery remain explicit separate gates.

## Current machine-readable exceptions

The accepted frozen sources intentionally retain only the previously audited range defects:

- Track 00 Horns: MIDI 78 x4, 81 x4, 83 x2;
- Track 00 Violas: MIDI 47 x3;
- Track 02 Trumpets: MIDI 85 x2.

CI must fail if those counts/pitches change or if any additional ordinary-lane range defect appears.

## Verification command

```shell
python3 scripts/music/verify_music_sources.py
```

Acceptance requires exact-head normal CI on the final PR head.


## Acceptance evidence

- PR: #304
- synchronized merge-candidate head: `df779c6cf11b83caf00f16d941f8037cdd6febba`
- exact-head normal CI: run `34173135366` — PASS
- exact-head Music source-integrity step — PASS
- accepted squash merge: `30b5202fec3ae07b0720b42eb8d7770c3c463843`
- post-merge main CI: run `34173486633` — Music source-integrity step PASS

The CI integration follows the repository evidence-economy policy: the Music verifier runs when
`assets/music/`, the verifier itself, or the normal CI workflow changes, plus conservative
manual/unknown-base cases. Unrelated documentation changes do not pay this verification cost.
