# Issue #464 — bounded Music owner handoff

**Scope:** review-material preparation only. This packet does not authorize reconstruction,
composition, arrangement, mastering, or reopening a closed Track 00 decision.

## Stop boundary

Track 00 repair selection and repaired-reference listening are closed. Do not revisit V2F2A,
V2F2B, the repaired reference render, or any Track 00 orchestration choice. Do not infer missing
plugin state or MIDI from note data, prose, filenames, or conversation. If an exact source cannot
be supplied, stop and record the blocker below.

## Gate A — Track 00 / 01 / 03 original CWP plugin state

The repository identifies the MIDI cues and the unresolved state, but does **not** record an
original Sonar/Cakewalk `.cwp` filename, local path, or checksum for any of these gates. The owner
must supply the original local Cakewalk/Sonar project file(s) that contain the accepted sessions:

| Cue | Accepted MIDI identity | State to inspect |
| --- | --- | --- |
| Track 00 — *A Windborne Fantasia* | `assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz`; uncompressed SHA-256 `c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73` | Track 11 `HC`: exact BBCSO Harp/Celeste technique or intentional switching scheme |
| Track 01 — *Rambling Through the Gentle Blue* | `assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz`; uncompressed SHA-256 `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` | Track 11 `HC`: exact BBCSO Harp/Celeste technique or intentional switching scheme |
| Track 03 — *Count the Leagues* / Second Horizon | `assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz`; uncompressed SHA-256 `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1` | Track 11 `HC`: exact BBCSO Harp/Celeste technique or intentional switching scheme |

For each supplied CWP, the owner should record the exact local filename/path, file checksum, cue
association, BBCSO library/plugin instance, loaded preset/technique, and any keyswitch/UACC or
automation mechanism. The result must be a cue-level provenance record; the MIDI lane name and
register are not evidence of Harp versus Celeste. Leave all MIDI note data untouched.

**Current external-file blocker:** no precise repository-known CWP file can be named. Supply the
original accepted Track 00, Track 01, and Track 03 Sonar/Cakewalk project file(s), or explicitly
record that the corresponding original file is unavailable. Do not create or rebuild a CWP.

## Gate B — Track 06 Draft 02.3 exact-source recovery and listening

Repository-known evidence:

- Cue: Track 06 storm / weather traversal; composition frozen at Draft 02.3.
- No exact accepted Draft 02.3 full MIDI is present under `assets/music/source/`.
- Historical working artifact recorded in the Draft 02 section: `SF_Track06_Storm_Draft02_THUNDERBREAK_72bar.mid`, with SHA-256 `22e83d65ed0baefbc425a2ccca4e00c381858ce3559dee7f940f6a06af6850a8`. This is Draft 02, not an identity for accepted Draft 02.3, and must not be promoted.
- Accepted render review names `Skyforge_Storm.wav` (approximately 119.96 seconds), but records no checksum or repository path. Its review supports the Draft 02.3 percussion-level acceptance; it cannot substitute for the missing MIDI.
- Documented source/session expectations are 144 BPM, 4/4, the canonical 19-track layout, Track 12 `PERC` = BBCSO `Percussion -> Untuned Percussion`, Track 13 `TP` = `Tuned Percussion -> Glockenspiel`, and PERC absolute MIDI notes 48/50/52/53 for Bass Drum/Tenor Drum/Snare Drum/Suspended Cymbal.

**Exact-source blocker:** repository evidence does not identify a filename, path, or checksum for
the accepted Draft 02.3 full MIDI. The owner must supply that exact accepted MIDI file from the
external local authoring/session storage. Do not derive it from `SF_Track06_Storm_Draft02_THUNDERBREAK_72bar.mid`, the development record, the render, or memory. If the exact file cannot be located, stop; no canonical replacement may be written.

After supply, the minimum owner packet is:

1. Preserve the supplied file unchanged and record filename, local source path, byte SHA-256, and
   how it is associated with accepted Draft 02.3.
2. Verify decompressed/source identity, type-1 MIDI, 19 instrument tracks in canonical order,
   144 BPM / 4/4, all note ranges, PERC absolute mapping, and TP Glockenspiel state. Record any
   discrepancy; do not silently repair it.
3. Render the exact source through the accepted BBCSO session and record source-to-render
   relationship, render filename/checksum, and whether the known Draft 02.3 listening target is
   reproduced (storm architecture, false clearing, Thunderbreak impact, eyewall contrast, and
   recession/coda).
4. Owner judgment only after exact identity and render relationship are established: choose the
   final title (current leading candidate is *A Province of Thunder*, not locked), then decide
   reference-vs-master disposition. An accepted reference/render is not automatically a GAME or
   OST master.

Reconstructing, re-composing, re-arranging, changing percussion levels, or mastering to fill the
persistence gap is outside this issue.

## Optional Track 00 GAME/OST master check

Non-blocking and explicitly deferred. The accepted Track 00 reference render is
`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`, external
WAV SHA-256 `c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`. Its approximate
levels are documented as -20.4 LUFS, 16.6 LU LRA, and -2.3 dBTP. Optional GAME/OST creation or
level verification may wait for packaging/release work and must not block the CWP or Track 06
handoff, or reopen Track 00 listening decisions.
