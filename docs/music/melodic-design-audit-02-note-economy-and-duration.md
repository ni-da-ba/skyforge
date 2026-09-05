# MUS-0001 — Melodic design audit 02: note economy, holds, and structural duration

**Status:** Adopted as a design constraint for the next Track 03 pass  
**Scope:** First-Principles Theme 04 — Afterimage Hook  
**Reason:** Theme 04 is the first Track 03 pass with clear thematic promise, but its lead line over-articulates several places where the ear wants a structural tone to persist.

## Diagnosis

Theme 04 improved memorability by adding a real fingerprint:

- repeated-note hesitation;
- characteristic leap;
- immediate reversal;
- E-natural catch;
- piano afterimage.

But character was purchased partly by adding too many note events.

The result is a useful theme that sometimes refuses to let its strongest notes matter. Instead of arriving and dwelling, the cello frequently converts arrival into another short connector.

The problem is therefore **not insufficient complexity**. It is insufficient hierarchy between structural tones, characteristic ornaments, and connective motion.

## Quantitative evidence

Theme 04 contains **80 cello note attacks across 20 bars / 60 quarter-note beats**.

Duration distribution:

- **22** notes shorter than 0.4 beats;
- **48** notes from 0.4 to <0.75 beats;
- **8** notes from 0.75 to <1.25 beats;
- **0** notes from 1.25 to <2 beats;
- only **2** notes at 2 beats or longer.

Therefore **70 / 80 = 87.5%** of cello notes are shorter than 0.75 beat.

Outside the two final B-flat cadences in bars 8 and 20, the melody contains **no cello note longer than 1.25 beats**.

Theme 03 had **68** cello attacks. Theme 04 increased this to **80**, while the number of very short notes (<0.4 beat) rose from **3 to 22**.

The character pass therefore created real identity, but also reintroduced excessive surface activity.

## Cadential regression

Melodic Design Audit 01 required phrase-final lengthening and lower onset density.

Theme 04's design notes say that rule remains active, but the MIDI only partly obeys it.

Examples:

- bar 4 ends G4 -> F4 -> D4 -> C4; final C4 lasts ~0.54 beat;
- bar 12 ends G4 -> F4 -> D4 -> C4; final C4 lasts ~0.64 beat;
- bar 16 ends F4 -> E4 -> D4 -> C4; final C4 lasts ~0.64 beat.

Those are important open cadences over dominant harmony, yet the cello barely occupies the arrival.

By comparison, Theme 03 allowed its bar-4 and bar-16 C4 arrivals to last about **1.7 beats**.

Theme 04 therefore solved character while partially regressing the earlier duration fix.

## Where the melody most wants to hold

### Opening leap arrival — bar 1

Signature head:

D4 -> D4 -> F4 -> Bb4

The B-flat is the first major destination.

Current behavior: ~0.82 beat before the line immediately continues.

Next-pass implication:
- lengthen the B-flat;
- simplify the following A-F-G-E answer;
- retain the E-natural catch, but expose it rather than burying it in a four-note chain.

### Phrase-1 interior apex — bar 3

C5 is the local apex but lasts ~0.62 beat before a short B-flat and four-note descent.

Next-pass implication:
- lengthen C5 or the following B-flat;
- delete at least one descent event;
- thin the cadence.

### Phrase-2 apex — bar 6

D5 is a clear expressive arrival.

Current behavior:
D5 -> C5 -> Bb4 -> G4, followed by five more attacks in bar 7.

Next-pass implication:
- hold D5 substantially longer;
- simplify the descent;
- reduce bar 7 to the minimum needed to preserve the E-natural catch and reach the cadence.

Bar 7 is a primary flutter zone.

### Phrase-3 divergence — bar 10

A4 -> F4 -> E4 -> F4 -> G4

The E-natural is useful identity, but E-F-G becomes ornamental traffic.

Next-pass implication:
- choose one structural tone to dwell on;
- keep E-natural as a brief catch;
- do not require every connector to receive a separate attack.

### Development — bars 13-14

Increased density is formally justified here, but the global F5 apex lasts only ~0.72 beat.

Next-pass implication:
- keep some developmental acceleration;
- reduce the number of attacks modestly;
- **hold the global apex**.

The global high point should be one of the longest emotionally active notes in the theme.

### Phrase-4 cadence — bar 16

F4 -> E4 -> D4 -> C4 is characteristic but over-articulated.

Next-pass implication:
- keep the E-natural catch;
- then simplify toward a long C;
- restore phrase-final lengthening.

### Final answer — bar 19

F4 -> D4 -> C4 -> D4 -> C4 is more active than the final phrase requires.

Next-pass implication:
- reduce to the essential descent;
- allow detail to fall away before the final low B-flat.

## Structural hierarchy

The next pass should distinguish three note classes.

### Signature notes
Pitches/rhythms whose identity should remain recognizable:
- repeated D;
- F -> B-flat leap;
- E-natural catch;
- principal phrase apexes.

### Structural arrivals
Notes the listener should be allowed to inhabit:
- B-flat after the opening leap;
- C5 / D5 local apexes;
- low C open-cadence arrivals;
- global F5 apex;
- final low B-flat.

### Connective notes
Passing or neighboring notes that may be deleted if they blur hierarchy.

The rewrite should begin by **deleting connective notes**, not by adding new material.

## Density targets for the next pass

These are engineering targets, not rigid laws:

- reduce cello attacks from **80** toward roughly **58–65** across the same 20 bars;
- cut <0.4-beat notes by at least half;
- introduce several meaningful **1.25–2.0 beat holds** outside final cadences;
- give the global apex an extended duration;
- restore long phrase-final arrivals at bars 4, 12, and 16;
- preserve higher density mainly in the developmental approach, not uniformly.

## Governing rule

> Character should come from which notes we choose, not from refusing to stop choosing notes.

Or more practically:

> When the melody reaches a note the listener wants to hear, let them hear it.

The next pass should preserve Theme 04's fingerprint while removing ornamental traffic around its best arrivals.
