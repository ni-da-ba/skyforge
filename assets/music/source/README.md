# Skyforge music authoring sources

This directory stores original Skyforge soundtrack authoring MIDI that is important to the accepted musical record.

## Storage policy

- Small active sketches may be stored directly as `.mid`.
- Larger accepted sources may be stored as `.mid.gz` to reduce ordinary Git history size.
- Gzip is storage-only. Decompressing a `.mid.gz` must reproduce the exact authoring MIDI represented by the documented SHA-256.
- Large rendered GAME/OST WAV masters are not stored in ordinary Git history until the project adopts an explicit large-artifact strategy such as Git LFS or versioned release assets.
- No third-party MIDI, score, sample, or reference-audio material belongs here.

## Frozen sources

| Cue | Path | Uncompressed MIDI SHA-256 |
| --- | --- | --- |
| Track 00 — A Windborne Fantasia V2F.1 | `frozen/track-00-a-windborne-fantasia-v2f1.mid.gz` | `beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695` |
| Track 01 — Rambling Through the Gentle Blue D5.1 | `frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz` | `5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65` |
| Track 02 — The Lord of Empty Miles D4.2 | `frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz` | `73aa596990090b22d250d994714e24bc3162eba5a04b5a70bf7c36ace8a5ef28` |

Example verification:

```shell
gzip -dc frozen/track-00-a-windborne-fantasia-v2f1.mid.gz | sha256sum
```

## Active Track 03 lineage

The melodic-gate process is intentionally retained as development evidence.

- `track-03-sketch-05-songlike-strings.mid` — phrase-scale melodic-sentence predecessor.
- `track-03-sketch-06-developing-melody.mid.gz` — current A/B/development/synthesis boundary.

Sketch 6 uncompressed SHA-256:

```text
e41498d0c6fa0b29dc4f4ec5b5cf4a7d2dd44967dd707b15e905fff8c26cfec8
```

Track 03 is not frozen. New sketches should be retained only when they materially explain the accepted lineage or remain candidates for future use.
