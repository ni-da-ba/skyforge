# Skyforge Music / Audio State

**Status:** MUS-0004 acceptance record — effective when merged to `main`  
**Updated:** 2026-09-07 (America/Chicago)

## Authority

Repository source, current `main`, tests/workflows, merged history, `docs/music/`, and exact persisted MIDI/checksum records are authoritative. Conversation is supplementary.

## Highest repository-level accepted milestone

On `main`, presence of this record means **MUS-0004 / PR #370** is MERGED / ACCEPTED. Before PR #370 merges, this branch copy is only a merge candidate.

MUS-0004 promotes the project-owner-selected Track 00 **V2F2A — peak handoff** repair to the sole canonical MIDI identity for **A Windborne Fantasia**.

Acceptance basis:

- MUS-0003 already machine-qualified V2F2A as format-1 / 20-track, 480 PPQ, 104 BPM / 6/8, BBCSO ordinary-lane range-clean, with raw-track changes limited to HN (6), TPT (7), VLA (17), and VLC (18);
- on 2026-09-07 the project owner explicitly directed the Music lane to finalize the peak-handoff version, closing the Track 00 candidate-selection gate;
- PR #370 must pass exact-head normal CI, including `python3 scripts/music/verify_music_sources.py`, before merge.

No new BBCSO bounce/master is claimed by MUS-0004.

## Current accepted musical state

- Track 00 — **A Windborne Fantasia**: V2F2A peak handoff is canonical after MUS-0004. Uncompressed MIDI SHA-256: `c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73`. V2F.1 remains a superseded historical reference; V2F2B remains a noncanonical alternate. The canonical MIDI has no ordinary-lane BBCSO range exception.
- Track 01 — **Rambling Through the Gentle Blue**: frozen; source checksum/order/tempo/range audit passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered from the original CWP.
- Track 02 — **The Lord of Empty Miles**: frozen; BBCSO Untuned Percussion repair accepted. Two Trumpet C#6 events remain a logged non-blocking range exception.
- Track 03 — **Count the Leagues**: frozen / complete; 72-bar Second Horizon source checksum/order/tempo/range audit passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered.
- Track 06 — storm cue: composition frozen at Draft 02.3; exact accepted full MIDI is not currently persisted and must be recovered before canonical source promotion.
- Principal-theme candidate: exact user-approved voice-derived motif source is persisted; composition remains paused until prior-cue maintenance closes.

## Significant hazards / debt

1. **Plugin-state portability:** original Track 00/01/03 Harp/Celeste CWP state is not fully recovered.
2. **Canonical persistence:** filenames are never source identity; hashes/manifests remain authoritative.
3. **Track 06 persistence gap:** do not reconstruct Draft 02.3 from documentation or conversation; recover the exact accepted source.
4. **Large audio:** WAV masters remain outside ordinary Git pending explicit artifact policy.

## Human/manual gates remaining

- Recovery/inspection of original Track 00/01/03 Sonar/Cakewalk CWP plugin state where required.
- Track 06 final listening/title/master disposition after exact MIDI recovery.

The Track 00 V2F2A/V2F2B selection gate is closed by the project owner's explicit V2F2A disposition.

## Next technically prudent work

1. recover Harp/Celeste source-CWP state for frozen cues;
2. recover and audit exact Track 06 Draft 02.3 MIDI;
3. then resume principal-theme composition.

## Cross-lane boundary

Music / Audio owns score authorship, exact source identity, library/preset provenance, renders/masters, and listening gates. World/environment semantics remain Authorship-owned, gameplay/progression semantics Content-owned, and Minecraft playback/transition/persistence/runtime integration Implementation-owned.

No cross-lane contract changes in MUS-0004.

## MUS-0004 — Track 00 peak-handoff canonical promotion

**Status on main:** ACCEPTED via PR #370

Canonical source:

`assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz`

Canonical uncompressed SHA-256:

`c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73`

V2F.1 is retained as historical reference with its exact original MIDI/hash. V2F2A is removed from the repair-candidate pool after promotion. V2F2B remains available only as a noncanonical alternate.

## Prior accepted milestones

- **MUS-0003 / PR #331** — deterministic repair-candidate contracts and mutation-scope/range verification.
- **MUS-0002** — normal-CI canonical Music source/library verification.
- **MUS-0001** — initial soundtrack source/authorship persistence foundation.
