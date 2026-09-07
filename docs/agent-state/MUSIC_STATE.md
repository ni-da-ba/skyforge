# Skyforge Music / Audio State

**Status:** MERGED / ACCEPTED — MUS-0001  
**Updated:** 2026-09-07 (America/Chicago)  
**Accepted merge:** `0b3386ad74ef610f49d9d3e8050e5a701f5e2cbd`

## Authority

This file is the concise durable handoff for the Music / Audio lane.

Authoritative detail lives in:

- current repository source and Git history;
- `docs/music/`;
- exact persisted MIDI/checksum manifests under `assets/music/source/`;
- accepted BBCSO render/master records and explicit human listening gates.

Conversation is supplementary only.

## Highest repository-level accepted milestone

`MUS-0001` / PR #159 is **MERGED / ACCEPTED** as
`0b3386ad74ef610f49d9d3e8050e5a701f5e2cbd`.

The exact-head PR CI passed before the squash merge. This is the first repository-level accepted
Music / Audio milestone.

## Current accepted musical state on `main`

- Track 00 — **A Windborne Fantasia**: frozen original; 2026-09-07 audit found BBCSO Horn/Viola
  range defects. Two non-canonical repair candidates are persisted; human BBCSO A/B required before
  any source promotion.
- Track 01 — **Rambling Through the Gentle Blue**: frozen; source checksum/order/tempo/range audit
  passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered from the original CWP.
- Track 02 — **The Lord of Empty Miles**: frozen; BBCSO Untuned Percussion repair accepted and
  canonical source re-persisted after the prior Track-00/Track-02 binary collision. Two Trumpet C#6
  events remain a logged non-blocking range exception.
- Track 03 — **Count the Leagues**: frozen / complete; 72-bar Second Horizon source checksum/order/
  tempo/range audit passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered.
- Track 06 — storm cue: composition frozen at Draft 02.3; exact accepted full MIDI is not currently
  persisted and must be recovered before canonical source promotion.
- Principal-theme candidate: exact user-approved voice-derived motif source is persisted; new
  composition work is paused until prior-cue maintenance is closed.

## Significant hazards / debt

1. **Plugin-state portability:** Track 11 HC, Track 12 PERC, and Track 13 TP are not completely
   self-describing from generic MIDI lane names. Cue manifests must record actual BBCSO state.
2. **Library range alignment:** every frozen MIDI must pass actual BBCSO Discover playable-range
   checks; no silent octave/name inference.
3. **Canonical persistence:** filenames are never source identity. Frozen `.mid.gz` writes require
   decompression + checksum verification after repository write.
4. **Track 06 persistence gap:** do not reconstruct the accepted storm MIDI from documentation or
   conversation; recover the exact accepted source.
5. **Large audio:** WAV masters remain outside ordinary Git. A later explicit policy may retain
   compressed listening MP3s for accepted flagship cues.

## Human/manual gates

- Track 00 V2F2A vs V2F2B BBCSO listening A/B, bars 55–61.
- Recovery/inspection of original Track 00/01/03 Sonar CWP plugin state where required.
- Track 06 final listening/title/master disposition after exact MIDI recovery.

## Next technically prudent work

1. close Track 00 repair through human A/B;
2. recover HC state for frozen cues;
3. recover and audit exact Track 06 Draft 02.3 MIDI;
4. add automated music-source verification where it can mechanically enforce manifests/checksums;
5. only then resume principal-theme composition.

## Cross-lane boundary

Music / Audio owns score authorship, exact source identity, library/preset provenance, renders/masters,
and listening gates.

It does not own world/environment semantics, gameplay/progression semantics, or Minecraft runtime
playback/adaptive-state implementation. Those remain Authorship, Content / Experience, and
Implementation responsibilities respectively.
