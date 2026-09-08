# MUS-0005 — Track 00 repaired reference-render acceptance

**Status:** Merge candidate

## Scope

MUS-0005 closes the remaining qualitative render gate for the canonical Track 00 V2F2A peak-handoff repair.

The accepted artifact is an externally retained BBCSO reference render. The WAV itself remains outside ordinary Git; the repository persists its exact identity, technical measurements, timing evidence, and disposition.

## Accepted artifact

Supplied filename:

`Patched_Fantasia(1).wav`

SHA-256:

`c43d9d7425627d75d8c76c5fa627185f3dadf9c0075acdab1672f5efded59664`

Technical properties:

- WAV, stereo, 44.1 kHz;
- 32-bit floating-point samples;
- 5,633,032 frames;
- 127.733151927 seconds total duration;
- no clipped or invalid samples.

The machine-readable record is:

`assets/music/render-records/track-00-a-windborne-fantasia-v2f2a-reference.render.json`

## Canonical-source alignment

The render corresponds to canonical MIDI:

`assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz`

Uncompressed MIDI SHA-256:

`c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73`

Timing evidence:

- canonical tempo: 104 BPM;
- canonical meter: 6/8;
- canonical form: 72 bars;
- MIDI musical endpoint: 124.718733371 seconds;
- render tail after musical endpoint: 3.014418557 seconds;
- duration and onset alignment agree with the canonical 104 BPM source.

The previous supplied render, SHA-256 `a92b3eede389f24eb9a7b8ffe7efbd805c7aa7f5059db18a9b76b3835ed3314f`, was rejected because its 112.0-second performance corresponds to 120 BPM rather than 104 BPM.

## Listening disposition

The repaired passage at bars 55–61 is accepted.

Findings:

- no missing event, dropout, or broken transition is present;
- the transferred trumpet peaks read as reinforcement within the horn-led crest rather than as a wholesale orchestral recoloring;
- the space between the two peak clusters remains supported by the continuing horn/orchestral bridge;
- the horn-to-flute departure remains intact;
- the coda and final reverberant decay complete cleanly.

This closes the qualitative Track 00 peak-handoff repair gate opened by MUS-0003 and source-promoted by MUS-0004.

## Loudness and master status

Measured render properties:

- integrated loudness: approximately -20.4 LUFS;
- loudness range: approximately 16.6 LU;
- true peak: approximately -2.3 dBTP;
- sample peak: approximately -2.33 dBFS.

The render is accepted as the canonical **reference/listening proof**, not as a final GAME or OST master.

The existing GAME target is approximately -19.1 LUFS / -1 dBTP with no broadband compression. This render is about 1.3 dB below that level. A later master may raise level and remeasure the result, but that mastering step is not required to establish that the repaired orchestration works.

## Non-claims

MUS-0005 does not claim:

- ordinary-Git storage of the WAV;
- a finalized GAME or OST master;
- recovery of original Harp/Celeste CWP plugin state;
- any change to cue role, form, harmony, or runtime playback contracts.

## Acceptance boundary

MUS-0005 is accepted when:

1. the exact external render record and checksum are merged;
2. Track 00 and Music-lane state identify the repaired render as qualitatively accepted;
3. normal repository CI passes on the exact PR head.
