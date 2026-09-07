# Skyforge music authoring sources

This directory stores original Skyforge soundtrack authoring MIDI that is important to the accepted musical record.

## Storage policy

- Small active sketches may be stored directly as `.mid`.
- Larger accepted sources may be stored as `.mid.gz` to reduce ordinary Git history size.
- Gzip is storage-only. Decompressing a `.mid.gz` must reproduce the exact authoring MIDI represented by the documented SHA-256.
- Large rendered GAME/OST WAV masters are not stored in ordinary Git history until the project adopts an explicit large-artifact strategy such as Git LFS or versioned release assets.
- No third-party MIDI, score, sample, or reference-audio material belongs here.

## Canonical accepted sources

| Cue | Path | Uncompressed MIDI SHA-256 |
| --- | --- | --- |
| Track 00 — A Windborne Fantasia V2F.1 | `frozen/track-00-a-windborne-fantasia-v2f1.mid.gz` | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` |
| Track 01 — Rambling Through the Gentle Blue D5.1 | `frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz` | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` |
| Track 02 — The Lord of Empty Miles D4.2 — BBCSO percussion repaired | `frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz` | `79be46fd4ca2d712a727a571265d8521740148a550e05c04288cd14d5b9bf50d` |
| Track 03 — Count the Leagues — Second Horizon | `track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz` | `7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1` |

Example verification:

```shell
gzip -dc frozen/track-00-a-windborne-fantasia-v2f1.mid.gz | sha256sum
```

## Track 03 development lineage

The many Track 03 sketches and development-lab files remain as authorship evidence. They are **historical lineage**, not the current Track 03 source identity.

The accepted final source is the 72-bar Second Horizon MIDI listed above.

## Track 06 persistence gap

Track 06 / storm is compositionally frozen at Draft 02.3 and has an accepted BBCSO render, but the exact accepted Draft 02.3 full MIDI is not currently present under `assets/music/source/`.

Do not reconstruct or synthesize a replacement from the development record. Recover the exact accepted MIDI, verify it, then persist it with a checksum and cue manifest.

## Frozen-source verification guardrail

A prior Track 02 persistence error caused a file path and documented cue identity to disagree. Frozen-source writes require verification before acceptance:

1. decompress the stored `.mid.gz`;
2. compare the decompressed SHA-256 with the documented canonical MIDI SHA-256;
3. verify conductor tempo and meter;
4. verify the canonical instrument-track count/order and track names;
5. verify every authored note lies within the intended BBCSO Discover patch range, unless an explicit exception is documented;
6. for HC/PERC/TP, verify cue-level BBCSO preset/technique state and absolute MIDI note map;
7. for surgical repairs, prove non-target tracks and controller/timing data are unchanged;
8. only then mark the repository artifact canonical.

Track 02 was repaired and re-persisted under this rule on 2026-09-07.
