# Issue #464 — bounded Music owner handoff

**Scope:** review-material preparation only. This packet does not authorize reconstruction,
composition, arrangement, mastering, or reopening a closed Track 00 decision.

## Owner recovery update — 2026-09-10

The project owner subsequently supplied six original Cakewalk/Sonar `.cwp` candidates. They were
preserved unchanged in the persistent Project Skyforge Library at
`/Project Skyforge/Music/Recovered CWP/`, and their exact fingerprints and recovered BBCSO plugin
state are recorded in `ISSUE-464-RECOVERED-CWP-MANIFEST.md`.

That evidence supersedes the earlier statement below that no external CWP source had been supplied.
Track 01 and Track 03 HC provenance is directly recovered; Track 00 HC instrumentation is
corroborated by both surviving Track-00-family candidates; and `Skyforge_Storm.cwp` strongly matches
the documented accepted Storm-session percussion configuration. The project owner has authorized
provisional closure of the manual source-recovery gate with exact Track 00 accepted-session identity
and exact Track 06 Draft 02.3 full-MIDI identity retained as archival caveats rather than development
blockers. No missing MIDI or plugin state was reconstructed.

## Stop boundary

Track 00 repair selection and repaired-reference listening are closed. Do not revisit V2F2A,
V2F2B, the repaired reference render, or any Track 00 orchestration choice. Do not infer missing
plugin state or MIDI from note data, prose, filenames, or conversation.

## Gate A — Track 00 / 01 / 03 original CWP plugin state

The repository originally identified the MIDI cues and unresolved state without an original CWP
identity. The owner-recovered source bundle now supplies the relevant surviving Cakewalk sessions;
see `ISSUE-464-RECOVERED-CWP-MANIFEST.md` for exact SHA-256 values and recovered plugin state.

| Cue | Accepted MIDI identity | Recovered CWP disposition |
| --- | --- | --- |
| Track 00 — *A Windborne Fantasia* | `assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz`; uncompressed SHA-256 `c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73` | `Skyforge_v2.cwp` is the strongest surviving candidate and `Skyforge_v1_FINAL.cwp` corroborates the same HC state; exact accepted-session identity remains unproven |
| Track 01 — *Rambling Through the Gentle Blue* | `assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz`; uncompressed SHA-256 `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` | `Skyforge_01_Rambling_Through_the_Gentle_Blue.cwp`; direct project-name match |
| Track 03 — *Count the Leagues* / Second Horizon | `assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz`; uncompressed SHA-256 `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1` | `Skyforge_03_Count_the_Leagues.cwp`; direct project-name match |

Direct binary inspection of the embedded BBC Symphony Orchestra state records Track 11 `HC` as
`Harp - Short Sustained Discover` active and `Celeste - Short Sustained Discover` inactive in all
six supplied sessions. Gate A is therefore **PASS / CLOSED** under the owner's provisional-source
disposition; the Track 00 exact-session caveat is retained in the manifest.

## Gate B — Track 06 Draft 02.3 recovery/listening

Repository-known evidence before owner recovery:

- Cue: Track 06 storm / weather traversal; composition frozen at Draft 02.3.
- Historical working artifact: `SF_Track06_Storm_Draft02_THUNDERBREAK_72bar.mid`, SHA-256 `22e83d65ed0baefbc425a2ccca4e00c381858ce3559dee7f940f6a06af6850a8`. This is Draft 02 and must not be promoted as Draft 02.3.
- Accepted render review names `Skyforge_Storm.wav` (approximately 119.96 seconds), but records no checksum or repository path.
- Documented session expectations are 144 BPM, 4/4, canonical 19-track layout, Track 12 `PERC` = BBCSO `Percussion -> Untuned Percussion`, Track 13 `TP` = `Tuned Percussion -> Glockenspiel`, and PERC absolute MIDI notes 48/50/52/53.

Owner recovery supplied `Skyforge_Storm.cwp`, SHA-256
`7a34aa708fe3f25be72a82b50711e6d4a92db306b3300334fa6ea74f22af04e7`. Its embedded project
identity is `Skyforge_Storm`, it contains 19 BBCSO plugin-state documents, and the discriminating
plugin state matches the documented accepted Storm session: Untuned Percussion active on PERC and
Glockenspiel active on tuned percussion.

The CWP does not itself prove that an embedded/source MIDI is the checksum-identifiable accepted
Draft 02.3 full MIDI. Therefore that exact archival identity remains **unproven**. It is forbidden
to reconstruct a replacement or promote Draft 02 to Draft 02.3 merely to erase the provenance gap.

Per project-owner instruction, Gate B is **PROVISIONALLY CLOSED FOR DEVELOPMENT** on the recovered
original-session evidence. Future recovery of the exact accepted 02.3 MIDI may upgrade archival
provenance without blocking Bootstrap or reopening settled composition/percussion decisions.

## Optional Track 00 GAME/OST master check

Non-blocking and explicitly deferred. The accepted Track 00 reference render is
`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`, external
WAV SHA-256 `c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`. Its approximate
levels are documented as -20.4 LUFS, 16.6 LU LRA, and -2.3 dBTP. Optional GAME/OST creation or
level verification may wait for packaging/release work and must not reopen Track 00 listening
choices.

## Exit

The #464 manual source-recovery human gate is considered cleared for ongoing development under the
owner's provisional-source decision. Preserve the recovered source bundle and caveats; do not
manufacture stronger provenance than the surviving evidence supports.
