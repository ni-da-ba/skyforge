# Skyforge Music / Audio State

**Status:** MUS-0005 acceptance record — effective when merged to `main`  
**Updated:** 2026-09-07 (America/Chicago)

## Authority

Repository source, current `main`, tests/workflows, merged history, `docs/music/`, exact MIDI manifests, and accepted render records are authoritative. Conversation is supplementary.

## Highest repository-level accepted milestone

On `main`, presence of this record means **MUS-0005** is MERGED / ACCEPTED.

MUS-0005 accepts the corrected 104 BPM BBCSO render of Track 00 **A Windborne Fantasia** as the canonical qualitative/reference proof for the V2F2A peak-handoff repair.

Accepted external WAV identity:

`c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`

Machine-readable record:

`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`

The exact WAV remains outside ordinary Git. MUS-0005 does not claim a final GAME or OST master.

## Current accepted musical state

- Track 00 — **A Windborne Fantasia**: V2F2A peak handoff is the canonical MIDI source and its corrected 104 BPM BBCSO reference render is qualitatively accepted. The repaired bars 55–61 contain no dropout or broken handoff; the trumpet peaks remain integrated with the horn-led crest. The reference render measures approximately -20.4 LUFS, 16.6 LU LRA, and -2.3 dBTP, so final GAME/OST mastering remains distinct.
- Track 01 — **Rambling Through the Gentle Blue**: frozen; source checksum/order/tempo/range audit passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered from the original CWP.
- Track 02 — **The Lord of Empty Miles**: frozen; BBCSO Untuned Percussion repair accepted. Two Trumpet C#6 events remain a logged non-blocking range exception.
- Track 03 — **Count the Leagues**: frozen / complete; 72-bar Second Horizon source checksum/order/tempo/range audit passes. Track 11 Harp/Celeste plugin-state provenance remains to be recovered.
- Track 06 — storm cue: composition frozen at Draft 02.3; exact accepted full MIDI is not currently persisted and must be recovered before canonical source promotion.
- Principal-theme candidate: exact user-approved voice-derived motif source is persisted; composition remains paused until prior-cue maintenance closes.

## Significant hazards / debt

1. **Plugin-state portability:** original Track 00/01/03 Harp/Celeste CWP state is not fully recovered.
2. **Track 06 persistence gap:** do not reconstruct Draft 02.3 from documentation or conversation; recover the exact accepted source.
3. **Large-audio persistence:** accepted WAV identities can be recorded, but WAV masters remain outside ordinary Git until an explicit large-artifact policy exists.
4. **Mastering distinction:** an accepted reference render is not automatically a GAME or OST master.

## Human/manual gates remaining

- Recovery/inspection of original Track 00/01/03 Sonar/Cakewalk CWP plugin state where required.
- Track 06 final listening/title/master disposition after exact MIDI recovery.
- Optional Track 00 GAME/OST master creation and level verification.

The Track 00 repair-selection and repaired-render listening gates are closed.

## Next technically prudent work

1. recover Harp/Celeste source-CWP state for frozen cues;
2. recover and audit exact Track 06 Draft 02.3 MIDI;
3. then resume principal-theme composition;
4. create final Track 00 GAME/OST masters only when release/showcase packaging requires them.

## Cross-lane boundary

Music / Audio owns score authorship, exact source identity, library/preset provenance, renders/masters, and listening gates. World/environment semantics remain Authorship-owned, gameplay/progression semantics Content-owned, and Minecraft playback/transition/persistence/runtime integration Implementation-owned.

No cross-lane contract changes are introduced by MUS-0005.

## MUS-0005 — Track 00 repaired reference-render acceptance

**Status on main:** ACCEPTED when merged

Accepted external reference render:

- supplied filename: `Patched_Fantasia(1).wav`;
- SHA-256: `c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`;
- stereo 44.1 kHz, 32-bit float;
- duration: 127.733151927 seconds;
- canonical musical endpoint: 124.718733371 seconds;
- clean reverb tail: 3.014418557 seconds;
- no clipped or invalid samples.

The prior 120 BPM render was rejected and is not an accepted artifact.

## Prior accepted milestones

- **MUS-0004 / PR #370** — V2F2A peak-handoff canonical MIDI promotion.
- **MUS-0003 / PR #331** — deterministic repair-candidate contracts and mutation-scope/range verification.
- **MUS-0002** — normal-CI canonical Music source/library verification.
- **MUS-0001** — initial soundtrack source/authorship persistence foundation.
