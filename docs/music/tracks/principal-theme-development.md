# Principal Skyforge Theme — development record

**Status:** motif captured; composition not yet begun  
**Role candidate:** principal / franchise-level Skyforge theme  
**Source:** user-authored sung/hummed melodic idea, transcribed through NeuralNote and manually corrected by ear  
**Orchestral identity:** violins first; brass apotheosis later

## Why this exists

A long-standing melodic payoff idea that the user originally associated with the ascent sequence of *A Windborne Fantasia* was finally captured accurately through a voice-to-MIDI workflow.

The important creative conclusion is not yet that this motif must replace *A Windborne Fantasia*. Instead, it is strong enough to receive its own composition study before deciding the score-wide hierarchy.

Possible eventual relationship:

- new piece = principal Skyforge theme;
- *A Windborne Fantasia* = first-ascent / revelation cue;
- or, if later evidence favors it, the new motif may be integrated into Fantasia.

Do not force integration merely because the motif originated as an imagined Fantasia payoff.

## Canonical captured motif

Approved note sequence from the corrected voice transcription:

`D3 - C#3 - D3 - A2 - A2 - Ab3 - A3 - A2 - A2 - A3 - G3 - F#3 - G3`

The exact register is the captured vocal/transcription register, not a binding orchestration register.

The important identity includes:

- opening lower-neighbor gesture: `D-C#-D`;
- repeated A anchors;
- chromatic `Ab-A` approach;
- closing lower-neighbor gesture: `G-F#-G`;
- the large register displacements between the low A anchors and upper chromatic gestures.

## Capture workflow lesson

The successful workflow was:

```text
user sings / hums
    ->
NeuralNote produces approximate MIDI
    ->
preserve the raw result as much as possible
    ->
remove obvious simultaneous octave errors
    ->
merge obvious duplicate segmentation
    ->
user validates by ear
```

A key lesson from this test:

> Do not over-interpret the raw NeuralNote result.

The first broad "musical cleanup" changed too much. The accepted version was obtained by minimally editing the raw transcription according to the user's ear.

This should become the default workflow for future user-authored melodic material.

## First orchestration rule

The user hears the motif **with violins first**.

Therefore the first canonical orchestral statement should not be horn-led.

Preferred hierarchy:

1. Violins I establish the theme;
2. strings / selective winds widen it;
3. brass ownership is reserved for a later thunderous / bombastic transformation, either later in the same piece or in a future cue.

This creates an explicit semantic transformation:

```text
violin form = discovery / lift / possibility
brass form  = apotheosis / scale / power
```

## Development boundary

Do not build the full principal-theme cue until:

1. Track 02 percussion maintenance is auditioned;
2. Track 06 is formally closed/frozen.

When development begins, start with a 30-60 second study rather than immediately forcing a complete song.

The first study should answer:

- Can the motif carry a composition rather than merely function as a payoff?
- What harmony best supports its chromatic neighbor gestures?
- What tempo/meter makes the captured rhythm feel inevitable?
- How much of the voice-derived timing should remain unquantized?
- Does a violin-first statement naturally grow into a brass apotheosis?
- Does it want any relationship to *A Windborne Fantasia*'s waltzy material, or should the pieces remain separate?
