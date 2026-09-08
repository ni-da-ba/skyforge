# MUS-0004 — Track 00 peak-handoff canonical promotion

**Status:** Accepted on `main` when merged via PR #370; pre-merge branch copy is a merge candidate

## Scope

MUS-0004 closes the Track 00 range-repair decision by promoting V2F2A — peak handoff — from a MUS-0003 machine-qualified repair candidate to the sole canonical MIDI identity for **A Windborne Fantasia**.

## Human decision

On 2026-09-07 the project owner explicitly directed the Music lane to finalize the peak-handoff version.

This is the qualitative authority for choosing V2F2A over V2F2B. No separate BBCSO comparison bounce was supplied to the repository in this milestone, so the claim is deliberately limited to source selection, deterministic persistence, and the already-proven repair properties.

## Canonical identity

Canonical source:

`assets/music/source/frozen/track-00-a-windborne-fantasia-v2f2a-peak-handoff.mid.gz`

Uncompressed MIDI SHA-256:

`c8de531cba73d058ca02a8b58b444617596a09d888bd7cf7132601141121ca73`

MUS-0003 already proves that this MIDI:

- is Standard MIDI File format 1 with conductor + 19 canonical lanes;
- remains 480 PPQ, 104 BPM, 6/8;
- has no ordinary-lane BBCSO Discover range exception;
- differs from V2F.1 only in HN (6), TPT (7), VLA (17), and VLC (18).

## Promotion mechanics

MUS-0004:

1. places the exact V2F2A gzip blob under the frozen canonical Track 00 path;
2. installs a canonical manifest with an empty range-exception set;
3. removes V2F2A from the repair-candidate pool so the same source is not represented as both candidate and canonical;
4. retires the V2F.1 canonical manifest while preserving its exact MIDI and a structured historical-reference record;
5. retains V2F2B as a noncanonical alternate;
6. updates Track 00 and Music-lane documentation to the new canonical identity.

## Non-claims

MUS-0004 does **not** claim:

- a newly rendered or mastered BBCSO GAME/OST bounce;
- recovery of the Track 11 Harp/Celeste source-CWP plugin state;
- any change to Track 00's role, form, harmony, or previously accepted mastering measurements;
- any cross-lane runtime/adaptive playback contract.

## Acceptance

PR #370 requires exact-head normal CI before merge, including:

```shell
python3 scripts/music/verify_music_sources.py
```

The source verifier must see exactly one canonical Track 00 manifest, a range-clean V2F2A canonical hash, the retained V2F.1 historical MIDI as parseable but noncanonical, and only V2F2B as a verified repair candidate.

Detailed workflow evidence remains attached to PR #370 rather than copied into this document.
