# Track 00 — A Windborne Fantasia

**Status:** Frozen — V2F2A peak-handoff repair canonical  
**Role:** first ascent / panorama / rare revelation / thesis statement  
**Meter:** 6/8  
**Tonal center:** D major  
**Authored MIDI tempo:** 104 BPM  
**Experienced tempo:** approximately 108 BPM  
**Length:** 72 bars

## Dramatic function

A Windborne Fantasia is the score’s large-scale statement of hopeful aerial wonder.

It is intentionally **not** ordinary exploration rotation. Its best use is for:

- first arrival or first flight;
- major altitude transition;
- spectacular generated landmark;
- legendary island;
- title or thesis-sequence use;
- rare revelation.

The core image used during composition was:

> breaking through the clouds and into some kind of wonder.

## Formal arc

| Bars | Function |
| --- | --- |
| 1–16 | opening / first panorama |
| 17–24 | exhale |
| 25–32 | renewed ascent / build |
| 33–36 | threshold |
| 37–47 | revelation |
| 48–51 | triumph / peace |
| 52–56 | bridge into horn departure |
| 57–60 | horn crest / peak handoff |
| 61–64 | horn-to-flute handoff |
| 65–72 | coda / afterglow |

## Principal identities

### Opening / propulsion

The opening woodwind vocabulary is built around upward travel and curiosity.

The core opening motif family includes:

```text
D -> F# -> A -> B
```

with an answering descent.

Flute functions here as propulsion and curiosity rather than as generic “sky color.”

### Threshold

The threshold architecture was abstracted as:

```text
up 3 -> down 3 -> up 3 -> up 3
```

Its dramatic function is:

```text
lift -> recoil -> renewed lift -> intensified lift
```

The final high point is intentionally unresolved because it leads directly into the revelation.

### Arrival / revelation

A protected authored arrival gesture developed into the revelation section. The important compositional principle is that the foreground melody remains relatively direct while the harmony supplies the wonder.

A distinctive protected harmonic landmark is:

```text
Cmaj7(#11)/E -> B7/D# -> Em9
```

The full cue uses a broader vocabulary including Dadd9, Gmaj7, Bm7, Asus4, inversions, suspended dominant color, and occasional D-Lydian inflection.

## Orchestral semantics

- flute: upward travel / curiosity;
- horns: breadth, horizon, confidence;
- trumpets: peak reinforcement and the repaired high-point handoff, not a wholesale martial recoloring;
- V1: emotional realization;
- low voices: support and scale, not independent narrative foreground.

The first horn declaration remains deliberately preserved because it establishes the panorama without turning the cue martial.

## Accepted production findings

The real BBCSO render showed that orchestral mockup quality depended heavily on controller design and low-mid management.

Important retained lessons:

- higher CC1 with lower CC11 can brighten the BBCSO timbral layer without merely increasing loudness;
- inner voices should be written horizontally rather than as barline chord blocks;
- coda mass should be shed rather than maintained;
- the payoff should simplify perceptually after a busy approach.

A late V2F.1 correction smoothed the first horn entry after the real render exposed an attack that was too abrupt.

## Canonical MIDI identity

Canonical revision after MUS-0004:

```text
V2F2A — peak handoff
```

Uncompressed MIDI SHA-256:

```text
c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73
```

Repository source:

```text
assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz
```

Historical V2F.1 reference:

```text
beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695
assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.mid.gz
```

The original MUS-0001 authoring filename was `SF_V2F1_01_FULL_72bar_HORN_ENTRY_SMOOTHED.mid`. That identity is retained for provenance but is no longer canonical.

## Mastering

Shared tonal refinement:

- low shelf near 140 Hz: approximately -0.4 dB;
- broad reduction near 550 Hz: approximately -1.2 dB, Q ~0.8;
- high shelf near 5 kHz: approximately +1.0 dB.

### GAME master

- no broadband compression;
- approximately -19.1 LUFS;
- approximately 15.3 LU loudness range;
- approximately -1 dB true peak.

### OST master

- gentle 1.25:1 compression;
- approximately -17.5 LUFS;
- approximately 15.0 LU loudness range;
- approximately -1 dB true peak.

The existing master measurements describe the accepted pre-repair production record. MUS-0004 promotes MIDI source identity only; it does not claim that new GAME/OST bounces have been mastered or measured.

## Final evaluation at freeze

| Category | Score |
| --- | ---: |
| Intended-function fit | 8.4 |
| Melodic identity | 7.4 |
| Harmony | 8.1 |
| Rhythm / motion | 7.6 |
| Form / pacing | 8.2 |
| Orchestration | 7.8 |
| Timbral identity | 7.5 |
| Production / mockup | 7.6 |
| Distinctiveness | 7.3 |
| Repeated-gameplay suitability | 6.7 |
| Overall composition | **7.8 / 10** |
| Overall for intended role | **8.3 / 10** |

## Freeze decision

No further local polishing is justified unless a later soundtrack-wide audit exposes a concrete cross-cue problem.

This cue established that Skyforge’s orchestral identity could support large-scale wonder, but its rarity and dynamic shape make it unsuitable as ordinary background rotation.

## 2026-09-07 library-range audit and MUS-0004 resolution

V2F.1 contained two verified BBCSO Discover alignment defects:

- Track 06 HN: ten notes above the Horns F5 ceiling in bars 57–60;
- Track 17 VLA: three B2 note-ons below the Viola C3 floor.

MUS-0003 generated and machine-qualified two repairs. Both preserve 104 BPM / 6/8, format-1 / 20-track structure and canonical lane order; both are ordinary-lane range-clean; and only HN, TPT, VLA and VLC differ from V2F.1.

### Selected repair — V2F2A peak handoff

MUS-0004 promotes the conservative peak-handoff repair:

- remove only the ten Horn events above Discover's F5 ceiling;
- transfer seven previously uncovered high peaks to Trumpets;
- leave three already-covered Trumpet reinforcements unduplicated;
- preserve all in-range Horn melody;
- move the three out-of-range Viola B2 notes to Celli at identical pitch, timing, duration and velocity.

This retains the horn-led identity while giving the impossible peaks to an instrument that can actually play them.

### Retained alternate — V2F2B continuous brass crest

V2F2B remains a noncanonical alternate at:

`assets/music/source/repair-candidates/track-00-v2f2b-continuous-brass-crest-range-repair.mid.gz`

SHA-256:

`79d3ae690cf8aaf785ad0e31bef2e85f92ddfa3a3ac8cd2fe7c81b5d3c74d375`

It moves the complete upper Horn melody in bars 57–60 to Trumpets and is therefore intentionally more interventionist.

### Human disposition

On 2026-09-07 the project owner explicitly directed the Music lane to finalize the peak-handoff version. That direct qualitative disposition closes the Track 00 candidate-selection gate for MUS-0004.

No new BBCSO bounce was supplied to the repository as part of this decision. MUS-0004 therefore claims canonical MIDI/source repair and range correctness, not a newly measured or remastered audio render.
