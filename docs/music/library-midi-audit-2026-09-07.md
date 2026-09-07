# Skyforge sound-library / MIDI audit — 2026-09-07

**Scope:** accepted/frozen score sources, BBCSO Discover alignment, source persistence, and plugin-state portability  
**Result:** mixed; no widespread corruption, but several concrete defects/gaps were found before Track 06 closeout

## Audit method

The audit checked:

- repository source inventory;
- gzip/source identity where canonical hashes exist;
- Standard MIDI File type and ticks-per-beat;
- conductor tempo and meter;
- 20-track structure: conductor + canonical 19 instrument lanes;
- track-name/order alignment;
- note ranges against the documented BBCSO Discover instrument ranges;
- mapped percussion absolute MIDI numbers;
- use of plugin-state-sensitive HC/PERC/TP lanes;
- repository documentation against the actual accepted cue boundary.

No accepted musical source was silently rewritten as part of this audit.

## Status matrix

| Cue | Source identity | Track/order | Tempo/meter | BBCSO range | Color/percussion state | Disposition |
| --- | --- | --- | --- | --- | --- | --- |
| Track 00 — A Windborne Fantasia | PASS | PASS | PASS with doc clarification: MIDI 104 BPM, 6/8 | **FAIL / repair candidate** | HC state not self-describing | controlled A/B before declaring reproduction-clean |
| Track 01 — Rambling Through the Gentle Blue | PASS | PASS | PASS: 96 BPM, 4/4 | PASS | HC state not self-describing | musically clean; record HC preset when CWP available |
| Track 02 — The Lord of Empty Miles | PASS after repaired persistence | PASS | PASS: 132 BPM, 4/4 | PASS except 2 Trumpet C#6 events | PERC verified: Untuned Percussion, 48/50/71 map | frozen; 2-note exception logged |
| Track 03 — Count the Leagues | PASS | PASS | PASS: 76 BPM, 3/4 | PASS | HC state not self-describing | musically clean; record HC preset when CWP available |
| Track 06 — storm | **MISSING canonical source** | cannot verify exact accepted final MIDI | documented 144 BPM, 4/4 | cannot complete | documented PERC Untuned + TP Glockenspiel | recover exact Draft 02.3 MIDI before persistence |
| Principal-theme motif | source JSON present | not orchestrated | capture-grid metadata only | n/a | n/a | protected source; no orchestral audit yet |

## Track 00 — concrete range defects

The persisted source itself is the documented V2F.1 MIDI and its checksum matches.

However:

- ten Horn note-ons in bars 57-60 exceed the BBCSO Discover Horns a4 ceiling F5;
- three Viola B2 note-ons fall one semitone below the BBCSO Discover Viola floor C3.

The horn events are concentrated in the formal **horn crest**, so this is not harmless dead metadata. The likely correct repair is orchestration-specific, not mechanical. A blind octave shift is prohibited.

The source remains frozen until a controlled A/B preserves the accepted contour and role.

## Track 01 — clean MIDI, incomplete plugin-state provenance

Track 01 verifies as:

- canonical checksum correct;
- type-1 MIDI;
- 20 tracks in canonical order;
- 96 BPM / 4/4;
- no PERC or TP events;
- all ordinary orchestral notes within documented Discover ranges.

Track 11 HC is active, but the MIDI contains no explicit Harp/Celeste technique selection. The accepted render proves the session sounded as intended, but the repository MIDI alone cannot recreate that choice without the original plugin state.

## Track 02 — repaired and mostly clean

The Track 02 source-persistence error and silent percussion map were repaired before this audit.

Canonical PERC is now:

- MIDI 48 x97 — Bass Drum;
- MIDI 50 x26 — Tenor Drum;
- MIDI 71 x14 — Piatti;
- BBCSO preset: Percussion -> Untuned Percussion.

The only additional range finding is two Trumpet C#6 note-ons, one semitone above the documented Discover Trumpets a3 ceiling C6, approximately bars 56 and 60.

Because the repaired real render was accepted, this is logged as a minor frozen-source exception rather than an automatic revision.

## Track 03 — clean final source, incomplete HC provenance

The accepted 72-bar Second Horizon source verifies as:

- checksum `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1`;
- type-1 MIDI;
- 20 tracks in canonical order;
- 76 BPM / 3/4;
- no PERC or TP events;
- ordinary orchestral ranges clean.

Track 11 HC contains a small high-register afterimage layer, but the MIDI does not encode whether the Sonar BBCSO instance was using Harp or Celeste. The cue record semantically suggests an afterimage color, but the exact plugin technique must be recovered from the CWP rather than inferred.

## Track 06 — persistence blocker

The repository contains the Track 06 development record and accepted BBCSO-render review, but **no exact accepted Draft 02.3 full MIDI** under `assets/music/source/`.

This is now a hard persistence gate:

> do not create a Track 06 "canonical" MIDI by reconstructing it from notes in documentation or conversational memory.

Recover the exact accepted MIDI first, then audit:

- 144 BPM / 4/4 conductor state;
- canonical track order;
- PERC = Untuned Percussion with absolute 48/50/52/53 map as actually accepted;
- TP = Glockenspiel;
- all note ranges;
- checksum and post-write decompression identity.

## Library-state conclusion

The BBCSO Discover library itself is not the problem. The failures came from assuming that MIDI note names, General MIDI percussion conventions, or generic lane labels were sufficient to identify a BBCSO sound.

Three lanes are now treated as explicitly plugin-state-sensitive:

- 11 HC — Harp / Celeste;
- 12 PERC — percussion preset plus mapped absolute note numbers;
- 13 TP — actual tuned-percussion preset.

The reusable 19-track schema remains valid, but it is a routing schema, not a complete patch manifest.

## Repository/documentation corrections made by this audit

- global music index updated from obsolete Track 03 Sketch 6 state to frozen Count the Leagues;
- Track 02 current corrected canonical hash replaces the historical pre-repair hash in current-state tables;
- Track 06 missing-source state made explicit;
- Track 00 exact MIDI tempo recorded as 104 BPM;
- HC plugin-state guardrail added;
- per-instrument playable-range gate added;
- no accepted MIDI note data changed during the audit.

## Required maintenance before new composition

1. Build and audition a controlled Track 00 range-repair A/B.
2. Recover/record Track 11 Harp/Celeste state from original CWP files when available; this is a reproducibility task, not a reason to rewrite accepted music.
3. Recover the exact accepted Track 06 Draft 02.3 MIDI and persist it only after the full verification gate.
4. Leave Track 01 and Track 03 musical note data untouched.
5. Leave Track 02 untouched unless a future re-render justifies the two-note trumpet A/B.
