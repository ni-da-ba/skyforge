# MUS-0001 — soundtrack production workflow

**Status:** Active project workflow

## Scope

This document records the repeatable authoring and production path used for Skyforge orchestral cues through Track 03 development.

The workflow is deliberately split between composition, orchestration, real-library rendering, and mastering so that a successful musical idea is not confused with a successful sample-library mockup.

## Primary toolchain

Current workstation path:

- Cakewalk Sonar Free
- Spitfire Audio application
- BBC Symphony Orchestra Discover
- MIDI generation / inspection through Python and `mido`
- stereo mastering and analysis through offline tooling

The project’s BBCSO content has been used through a reusable Sonar template rather than ad hoc instrument loading for each cue.

## Canonical 19-track BBCSO schema

A separate conductor/meta track precedes the canonical instrument order:

1. `01 PICC | Piccolo`
2. `02 FLT | Flutes`
3. `03 OBO | Oboes`
4. `04 CL | Clarinets`
5. `05 BSN | Bassoons`
6. `06 HN | Horns`
7. `07 TPT | Trumpets`
8. `08 TBN | Tenor Trombones`
9. `09 BTBN | Bass Trombones`
10. `10 TUBA | Tuba`
11. `11 HC | Harp & Celeste`
12. `12 PERC | Percussion`
13. `13 TP | Tuned Percussion`
14. `14 PNO | Piano`
15. `15 V1 | Violins 1`
16. `16 V2 | Violins 2`
17. `17 VLA | Violas`
18. `18 VLC | Celli`
19. `19 CB | Basses`

Generated type-1 MIDI must keep the conductor/meta track at index 0 so the 19 instrument tracks map correctly when imported into the template.

## Template conventions

Current template conventions:

- winds, brass, and strings default to long articulations;
- harp/celeste, piano, tuned percussion, and untuned percussion use corresponding BBCSO patches;
- visual folders:
  - WW 01–05
  - BR 06–10
  - PERC+COLOR 11–14
  - STR 15–19
- audio buses:
  - WW
  - BR
  - CLR
  - STR
  - ORCH
  - Master

The pristine template is not used as a composition project. A new instance is created and saved separately before cue work begins.

## Authoring sequence

The current accepted sequence is:

```text
semantic / emotional role
        ↓
melodic or reduction-level proof
        ↓
orchestration
        ↓
synthetic diagnostic
        ↓
BBCSO render
        ↓
real-render correction
        ↓
composition / orchestration freeze
        ↓
stereo mix refinement
        ↓
GAME and OST masters
        ↓
role and range evaluation
```

The synthetic preview is diagnostic only. It can expose note, register, timing, and structural errors, but it is not authoritative for BBCSO balance, attack behavior, low-mid buildup, or transition seams.

## Composition gate

A recurring project lesson is that orchestration must not rescue weak composition.

The strongest cues developed from material that remained convincing when reduced:

- Track 00 retained clear arrival, threshold, and wonder gestures before final orchestral color.
- Track 01 retained a strong clarinet protagonist and walking groove before full world-response orchestration.
- Track 02 retained a recognizable dragon motif and predatory motor before the final mass/awe/panic balance.
- Track 03 moved to an explicit melodic gate after several atmospheric drafts failed to produce memorable thoughtfulness.

For melody-led cues, the current gate is:

> If the principal melody is not worth following over piano or a small chamber reduction, do not expand the cue.

## BBCSO performance rules learned in practice

### CC1 and CC11 are not interchangeable

BBCSO CC1 can affect timbral/dynamic layer, not only apparent level. A useful technique is:

- higher CC1 for brighter or broader sample character;
- lower CC11 to keep that character from simply becoming louder.

### Same-pitch overlaps can be dangerous

Overlapping same-pitch note events can create sample-library note-off races. A late note-off from the first event may choke the retriggered event.

Where one voice should sound continuous, prefer:

- one tied/continuous MIDI note; or
- carefully staggered releases that cannot terminate a newer event.

Track 02’s bar-69 horn transition required exactly this repair.

### Real renders govern seam decisions

A MIDI event list can look continuous while the rendered sample attacks, releases, room tails, or controller cliffs sound discontinuous.

Therefore:

- transition acceptance is made from the BBCSO render;
- major joins are auditioned in full musical context;
- support-family CC curves are evaluated independently.

## Perceptual hierarchy rules

The project currently treats orchestration as a foreground-allocation problem:

1. every section has one foreground owner;
2. at most one secondary interesting line should usually compete with it;
3. other parts double, sustain, pulse simply, or rest;
4. responses belong in gaps or under held foreground notes;
5. an idea should be taught before countermelody is added;
6. scale should increase through register, timbre, and mass without losing hierarchy.

A major recurring rule is:

> clarity before density.

## Transition rules

Transitions are considered compositional material, not cleanup.

Useful techniques include:

- pickups into the next section;
- shared pitches;
- phrase overlap;
- staggered family entrances/exits;
- a previous section seeding the next section’s motif;
- subtraction immediately before or after a major impact.

Barlines are not assumed to be phrase boundaries.

## Mastering split

Frozen cues receive separate targets:

### GAME master

- retain more macro-dynamic range;
- avoid broadband compression unless a cue genuinely requires it;
- leave headroom for runtime mixing and other game audio.

### OST master

- slightly denser presentation;
- very gentle compression may be used;
- preserve the cue’s intended dramatic architecture rather than normalize every section toward one loudness.

Track-specific measurements are retained in the corresponding records.

## Artifact policy

Compact source and engineering records belong in the repository.

Large derived WAV masters are not part of MUS-0001 because they add substantial repository weight and are reproducible from the source session/render chain. If the soundtrack later needs distributable binaries in version control, use a deliberate release or large-file strategy rather than silently bloating ordinary Git history.

Third-party sample content and copyrighted reference works must never be committed.
