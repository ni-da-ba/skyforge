# Track 02 — The Lord of Empty Miles

**Status:** Frozen as successful first draft  
**Role:** legendary dragon territory / reveal / awe / panic  
**Meter:** 4/4  
**Tempo:** 132 BPM  
**Center:** E-minor / altered-E language  
**Length:** 92 bars

## Dramatic function

The Lord of Empty Miles is the first major dark-range experiment in the score.

Its target is not ordinary boss music. It represents an exceptionally rare legendary dragon whose presence changes the meaning of the sky itself.

The central dramatic sentence was:

> something impossibly dangerous is sharing the sky with me.

The cue deliberately combines:

- awe;
- dread;
- enormous scale;
- predatory movement;
- catastrophic danger.

## Dragon identity

The principal dragon motif family is:

```text
E -> F -> B -> Bb / A -> G -> F -> E
```

Its identity comes from:

- semitone pressure;
- tritone distance;
- chromatic recoil;
- return toward E.

The motif supports several transformations:

- distant territory omen;
- full encounter statement;
- brass attack;
- slow ancient chorale;
- quiet aftermath.

## Rhythmic identity

Track 01’s 3+3+2 walking groove was transformed into a predatory motor.

Here:

```text
3 + 3 + 2 = wingbeat / controlled dragon motion
```

This was a useful range proof because it preserved project-level rhythmic DNA while completely changing emotional meaning.

## Final large-scale form

| Bars | Function |
| --- | --- |
| 1–8 | territory / menace |
| 9–16 | first sight |
| 17–28 | full reveal / awe |
| 29–36 | approach / wingbeat |
| 37–44 | first attack |
| 45–52 | circling / suspense |
| 53–60 | fire / maximum pressure |
| 61–68 | panic / bearing down |
| 69–76 | legendary awe / chorale |
| 77–84 | pursuit |
| 85–92 | aftermath |

## Key compositional discovery

The panic layer improved when the dragon and player were treated as psychologically different musical systems:

> The dragon does not panic. The player does.

The low motor remains controlled while upper strings and percussion express human exposure and urgency.

An early flute panic layer was repeatedly perceived as obtrusive and was ultimately removed from the entire bars 61–68 panic span.

That failure became an important project rule:

> semantic instrument labels do not outrank what the listener actually perceives.

## BBCSO correction history

### Awe / panic expansion

The first draft established menace and scale but did not spend enough time in awe or panic. The cue was expanded from 72 to 92 bars.

### Panic hierarchy

Later drafts refined:

- awe-to-wingbeat transition;
- fire-to-panic transition;
- panic contour;
- panic-to-legendary-awe release.

### Low-brass mass

The real BBCSO render showed that the low-brass writing approached opacity.

Draft 4 reduced selected bass-trombone and tuba attacks in bars 61–68, then deliberately delayed the reconstruction of full low-brass mass after the panic break.

The principle was:

```text
panic peaks
-> dragon passes
-> space opens
-> scale becomes comprehensible again
```

### Horn transition defect

A strange cut around bar 69 was traced to two implementation details:

1. overlapping same-pitch horn E events could allow an old note-off to choke a new retrigger;
2. horn and tenor-trombone CC curves dropped too abruptly across the transition.

The final correction replaced the overlap with one continuous E and smoothed the controller ramp.

## Canonical MIDI identity

SHA-256:

```text
73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28
```

Canonical authoring source at MUS-0001 record time:

```text
The_Lord_of_Empty_Miles_Draft4_2_FULL_HORN_TRANSITION_SMOOTHED.mid
```

## Stereo master

The final stereo pass retained the cue’s unusually large macro-dynamic range.

Shared tonal refinement:

- low shelf near 140 Hz: approximately -0.5 dB;
- broad reduction near 430 Hz: approximately -1.3 dB, Q ~0.85;
- high shelf near 4.8 kHz: approximately +1.1 dB.

The dark orchestral color was intentionally preserved rather than brightened into the exploration-cue tonal profile.

### GAME master

- no broadband compression;
- approximately -19.0 LUFS;
- approximately 20.2 LU loudness range;
- approximately -3.2 dB true peak.

### OST master

- gentle 1.22:1 compression;
- approximately -17.5 LUFS;
- approximately 19.9 LU loudness range;
- approximately -2.2 dB true peak.

## Range-experiment evaluation

| Category | Score |
| --- | ---: |
| Intended-function fit | **8.7** |
| Thematic identity | **8.1** |
| Harmony | 7.9 |
| Rhythm / propulsion | **8.4** |
| Form / pacing | 7.8 |
| Orchestration | 7.8 |
| Timbral identity | **8.4** |
| Production / mockup | 7.9 |
| Distinctiveness | **8.3** |
| Adaptive-gameplay utility | **8.9** |
| Range-experiment success | **9.2** |
| Overall composition | **8.1 / 10** |
| Overall for intended role | **8.7 / 10** |

## Freeze decision

The Lord of Empty Miles is accepted as a successful first draft rather than endlessly polished toward finality.

Its main value is twofold:

1. it is already a strong rare-encounter cue;
2. it proves that Skyforge’s musical identity can survive substantial movement into menace, panic, percussion, and low-brass mass.

Adaptive decomposition remains deferred until runtime encounter states and transition semantics are concrete enough to justify loop/stem boundaries.


## Post-freeze percussion-map audit

A later Track 06 percussion-mapping defect prompted an audit of the frozen Track 02 MIDI.

The canonical Track 02 Untuned Percussion track contains:

- MIDI 36: 97 attacks — BBCSO Bass Drum, valid;
- MIDI 41: 26 attacks — BBCSO Suspended Cymbal, valid;
- MIDI 49: 14 attacks — not part of the intended BBCSO Discover Untuned Percussion white-key map and consistent with an earlier GM-style crash-cymbal assumption.

The MIDI-49 events occur at bars 27, 28, 37, 41, 44, 53, 57, 60, 61, 65, 68, 77, 81, and 84.

This is a latent implementation defect in the authored percussion layer, not a discovered failure of the accepted composition. The frozen BBCSO render/master was reviewed and accepted with those events effectively absent or nonfunctional.

Do not silently alter the frozen Track 02 master. If Track 02 is remastered or re-rendered in the future, create an explicit A/B correction candidate for these 14 accents and judge whether restoring them improves the cue before replacing the canonical source.
