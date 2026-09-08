# Skyforge Music / Audio State

**Status:** MERGED / ACCEPTED — MUS-0002  
**Updated:** 2026-09-07 (America/Chicago)  
**Accepted merge:** `30b5202fec3ae07b0720b42eb8d7770c3c463843`

## Authority

This file is the concise durable handoff for the Music / Audio lane.

Authoritative detail lives in:

- current repository source and Git history;
- `docs/music/`;
- exact persisted MIDI/checksum manifests under `assets/music/source/`;
- accepted BBCSO render/master records and explicit human listening gates.

Conversation is supplementary only.

## Highest repository-level accepted milestone

`MUS-0002` / PR #304 is **MERGED / ACCEPTED** as
`30b5202fec3ae07b0720b42eb8d7770c3c463843`.

Acceptance evidence:

- synchronized merge candidate `df779c6cf11b83caf00f16d941f8037cdd6febba` was zero commits behind current `main`;
- exact-head normal CI run `34173135366` passed, including the Music source-integrity step;
- the squash merge landed on `main` as `30b5202fec3ae07b0720b42eb8d7770c3c463843`;
- post-merge `main` CI run `34173486633` executed and passed the Music source-integrity step.

MUS-0002 turns the MUS-0001 persistence/library rules into an executable repository gate while
preserving the Audit lane's CI evidence-economy policy.

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
4. only then resume principal-theme composition.

## Cross-lane boundary

Music / Audio owns score authorship, exact source identity, library/preset provenance, renders/masters,
and listening gates.

It does not own world/environment semantics, gameplay/progression semantics, or Minecraft runtime
playback/adaptive-state implementation. Those remain Authorship, Content / Experience, and
Implementation responsibilities respectively.


## MUS-0002 — automated source verification

**Status:** ACCEPTED

MUS-0002 converts the 2026-09-07 source/library audit from documentation-only policy into executable repository health.

The verifier at `scripts/music/verify_music_sources.py` is intentionally standard-library-only so normal CI can run it immediately after checkout. It checks every repository MIDI archive for parseability and every canonical cue manifest for:

- gzip/source existence and exact uncompressed SHA-256 identity;
- optional compressed-gzip identity where recorded;
- Standard MIDI File format-1 / conductor + 19-lane structure;
- canonical lane-name ordering;
- PPQ when pinned;
- tick-0 tempo, meter, and key-signature metadata when declared;
- BBCSO Discover ordinary-instrument playable ranges, with only exact machine-declared frozen exceptions allowed;
- Track 11 HC plugin-state provenance presence whenever that lane contains notes;
- Track 12 PERC preset / absolute-note / attack-count mapping;
- Track 13 TP used-state and preset declaration;
- no accidental large WAV assets under ordinary Git music assets.

Known frozen exceptions are data, not suppressions: Track 00's horn/viola defects and Track 02's two C#6 trumpet events must match their manifest exactly or CI fails.


### MUS-0002 accepted boundary

The repository now automatically rejects canonical Music source drift that machines can prove:
invalid MIDI/gzip artifacts, manifest/source hash mismatch, track-order or conductor metadata drift,
undeclared BBCSO ordinary-lane range violations, missing special-lane provenance, PERC/TP mapping
mismatch, and accidental ordinary-Git WAV storage.

The verifier is impact-gated inside normal CI. It runs for Music source/verifier changes and
conservative manual/unknown-base cases rather than creating unrelated CI fan-out.
