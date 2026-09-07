# SF-IMP-0083 — AUTH-0083 built-in seed/scale Minecraft matrix

## Exact support profile

The first SF-IMP-0083 engineering gate compiled all 20 remaining built-in AUTH-0083 members through
the production provider-neutral morphology compiler and derived tight integer Minecraft voxel
support. The profiling run was repository CI **34140463898** on commit
`dc46f54adbd8865067c823f44d7e720de8275e01`.

This was a diagnostic gate, not a new morphology policy. It preserved exact AUTH-0083 IDs, seeds,
scales, full bounded detail, and full provider secondary morphology.

| Member | Span Y | Fits vanilla 384? | Footprint chunks | Occupied columns |
| --- | ---: | :---: | ---: | ---: |
| builtin-massif-medium-seed-min | 320 | yes | 1292 | 224825 |
| builtin-massif-medium-seed-zero | 336 | yes | 1216 | 217231 |
| builtin-massif-medium-seed-skyforge | 330 | yes | 1292 | 230023 |
| builtin-massif-large-seed-skyforge | 494 | **no** | 2800 | 517511 |
| builtin-tableland-medium-seed-min | 209 | yes | 1088 | 210543 |
| builtin-tableland-medium-seed-zero | 213 | yes | 1088 | 204109 |
| builtin-tableland-medium-seed-skyforge | 212 | yes | 1088 | 204063 |
| builtin-tableland-large-seed-skyforge | 319 | yes | 2400 | 459151 |
| builtin-spine-medium-seed-min | 326 | yes | 1040 | 147077 |
| builtin-spine-medium-seed-zero | 329 | yes | 1160 | 152671 |
| builtin-spine-medium-seed-skyforge | 326 | yes | 1092 | 159103 |
| builtin-spine-large-seed-skyforge | 489 | **no** | 2480 | 358011 |
| builtin-basin-medium-seed-min | 222 | yes | 1088 | 206241 |
| builtin-basin-medium-seed-zero | 228 | yes | 1088 | 213283 |
| builtin-basin-medium-seed-skyforge | 230 | yes | 1088 | 201371 |
| builtin-basin-large-seed-skyforge | 345 | yes | 2400 | 453075 |
| builtin-lobed-medium-seed-min | 244 | yes | 1156 | 214913 |
| builtin-lobed-medium-seed-zero | 252 | yes | 1224 | 225935 |
| builtin-lobed-medium-seed-skyforge | 250 | yes | 1190 | 211301 |
| builtin-lobed-large-seed-skyforge | 375 | yes | 2600 | 475409 |

The standard Minecraft 1.21.1 Overworld interval is 384 blocks tall. Exact LARGE Massif and LARGE
Spine therefore cannot be represented there by any pure Y translation. Clipping or rescaling would
change the authored specimen and is not allowed.

## Runtime packaging decision

SF-IMP-0083 uses a **development-only tall family atlas** rather than changing production world
height or authored morphology.

For each built-in family, the four remaining members are stacked vertically at identical X/Z
coordinates:

1. MEDIUM / seed-min;
2. MEDIUM / seed-zero;
3. MEDIUM / seed-skyforge;
4. LARGE / seed-skyforge.

The first exact solid begins above the maximum vanilla Overworld generation interval and every
neighbor is separated by a 32-block air gap. Each relocation is a proven integer Y translation.
The review dimension is development-only (`min_y=320`, `height=1616`, interval
`[320, 1936)`). The exact stack still starts at Y=336 and therefore preserves every previously
proved integer translation. The interval begins at the vanilla Overworld noise ceiling, so the
carrier remains disjoint from native noise terrain instead of spending acceptance time generating
irrelevant base terrain. The existing `skyforge:noise_overlay` lifecycle remains in use; only this
explicit SF-IMP-0083 development fixture suppresses native structures/surface decoration before
exact Skyforge realization. This is an evidence-carrier optimization, not a production world-height
or production generator policy.

This packaging has two advantages:

- all 20 exact specimens remain representable without clipping or rescaling;
- the four same-family specimens share their horizontal admission footprint, reducing persistence
  and human-review packaging from 20 separate worlds to five family worlds;
- the development carrier does not generate native Overworld terrain that is provably below the
  entire review stack.

The existing exact-volume physical admission, vertically stacked isolation, deferred realization,
and mutation-inert actual-client reopen contracts remain the authority. No new Authorship contract
is introduced.

## Acceptance still required

Per family, machine acceptance must prove for all four exact members:

- exact AUTH-0083 identity, seed, scale, provider, full detail, and full secondary morphology;
- pure integer translation and build-range fit in the development review dimension;
- whole-volume physical admission with complete finite evidence;
- zero pending catch-up;
- exact sampled top/underside boundaries with air above/below;
- deterministic sampled geometry digest;
- unchanged digest after mutation-inert actual-client reopen.

Human #214 review must then compare seed variation and MEDIUM/LARGE scale behavior, with explicit
attention to Massif issue #267 and Tableland issue #283. Full #214 remains open afterward for
hybrids/providers and regional/material/ecology/hydrology contexts.
