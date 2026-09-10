# Skyforge Music / Audio State

**Status:** MUS-0005 accepted; #464 source-recovery maintenance gate closed provisionally  
**Updated:** 2026-09-10 (America/Chicago)

## Authority

Repository source, current `main`, tests/workflows, merged history, `docs/music/`, exact MIDI manifests, accepted render records, and the #464 recovered-source manifest are authoritative. Conversation is supplementary.

## Highest repository-level accepted milestone

On `main`, **MUS-0005** remains the highest numbered Music milestone.

MUS-0005 accepts the corrected 104 BPM BBCSO render of Track 00 **A Windborne Fantasia** as the canonical qualitative/reference proof for the V2F2A peak-handoff repair.

Accepted external WAV identity:

`c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`

Machine-readable record:

`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`

The exact WAV remains outside ordinary Git. MUS-0005 does not claim a final GAME or OST master.

Source-recovery maintenance closure:

- issue #464 is CLOSED / completed;
- PR #469 merged as `82bd8b5238f64600dedd5944c653973bf59c2926`;
- `docs/agent-state/ISSUE-464-RECOVERED-CWP-MANIFEST.md` records the owner-recovered Cakewalk/Sonar source bundle, exact SHA-256 fingerprints, recovered BBCSO state, and remaining archival caveats;
- the unchanged binaries are preserved in the persistent Project Skyforge Library under `/Project Skyforge/Music/Recovered CWP/`.

## Current accepted musical state

- Track 00 — **A Windborne Fantasia**: V2F2A peak handoff is the canonical MIDI source and its corrected 104 BPM BBCSO reference render is qualitatively accepted. The repaired bars 55–61 contain no dropout or broken handoff; the trumpet peaks remain integrated with the horn-led crest. The reference render measures approximately -20.4 LUFS, 16.6 LU LRA, and -2.3 dBTP, so final GAME/OST mastering remains distinct. Owner-recovered `Skyforge_v2.cwp` and `Skyforge_v1_FINAL.cwp` both preserve Track 11 HC as Harp active / Celeste inactive; exact accepted CWP identity remains an archival caveat rather than a development gate.
- Track 01 — **Rambling Through the Gentle Blue**: frozen; source checksum/order/tempo/range audit passes. Owner-recovered `Skyforge_01_Rambling_Through_the_Gentle_Blue.cwp` directly identifies the cue and resolves Track 11 HC provenance as Harp active / Celeste inactive.
- Track 02 — **The Lord of Empty Miles**: frozen; BBCSO Untuned Percussion repair accepted. Two Trumpet C#6 events remain a logged non-blocking range exception.
- Track 03 — **Count the Leagues**: frozen / complete; 72-bar Second Horizon source checksum/order/tempo/range audit passes. Owner-recovered `Skyforge_03_Count_the_Leagues.cwp` directly identifies the cue and resolves Track 11 HC provenance as Harp active / Celeste inactive.
- Track 06 — storm cue: composition remains frozen at Draft 02.3. Owner-recovered `Skyforge_Storm.cwp` is preserved as strong original-session evidence and directly matches the documented discriminating BBCSO state (Untuned Percussion + Glockenspiel). The checksum-identifiable exact accepted Draft 02.3 full MIDI remains unproven; this is an archival provenance caveat and must not be filled by reconstruction.
- Principal-theme candidate: exact user-approved voice-derived motif source is persisted. Prior-cue source-recovery maintenance no longer blocks resuming composition when Music priority permits.

## Significant hazards / debt

1. **Track 00 archival identity:** the surviving Track-00-family CWP candidates agree on HC instrumentation, but neither binary alone proves exact accepted-session identity for *A Windborne Fantasia*.
2. **Track 06 archival identity:** do not reconstruct or relabel Draft 02.3 from documentation, memory, or the historical Draft 02 MIDI. If the exact accepted MIDI is discovered later, record it as a provenance upgrade.
3. **Large-audio persistence:** accepted WAV identities can be recorded, but WAV masters remain outside ordinary Git until an explicit large-artifact policy exists.
4. **Mastering distinction:** an accepted reference render is not automatically a GAME or OST master.

These are not Bootstrap blockers under the project-owner #464 disposition.

## Human/manual gates remaining

- **No mandatory legacy Music source-recovery human gate remains open.** Issue #464 is closed under the project owner's provisional-source decision.
- Optional Track 00 GAME/OST master creation and level verification remains deferred/non-blocking.
- New listening/product gates may be raised only when new Music work produces an actual reviewable composition/render decision.

The Track 00 repair-selection and repaired-render listening gates remain closed and must not be reopened without contradictory evidence.

## Next technically prudent work

1. resume principal-theme / next accepted Music composition work when Music lane priority permits;
2. preserve the recovered CWP bundle and manifest unchanged;
3. if a checksum-identifiable exact Track 06 Draft 02.3 MIDI or stronger Track 00 CWP identity is discovered later, upgrade provenance without reopening settled musical decisions unless contradictory evidence requires it;
4. create final Track 00 GAME/OST masters only when release/showcase packaging requires them.

## Cross-lane boundary

Music / Audio owns score authorship, exact source identity, library/preset provenance, renders/masters, and listening gates. World/environment semantics remain Authorship-owned, gameplay/progression semantics Content-owned, and Minecraft playback/transition/persistence/runtime integration Implementation-owned.

No cross-lane contract changes are introduced by this maintenance closure.

## MUS-0005 — Track 00 repaired reference-render acceptance

**Status on main:** ACCEPTED

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
