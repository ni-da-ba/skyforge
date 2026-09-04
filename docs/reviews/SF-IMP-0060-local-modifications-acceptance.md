# SF-IMP-0060 — Exact-volume local modifications acceptance

Status: **ACCEPTED**

Issue: #99  
Pull request: #101  
Accepted feature head: `ad40e4b62ee2577c213bb50881a5910093e5ca76`  
Merge commit: `15ee5c1999d4c54043060ac2d51f113082787857`

## Objective

Extend the accepted SF-IMP-0059 volume-local vertical placement architecture from
`UNDERGROUND_ORES` to Minecraft's registry-native `LOCAL_MODIFICATIONS` phase without
introducing feature-ID compatibility logic, column-wide ownership, or BASE_WORLD leakage.

Vanilla amethyst geodes were used as the acceptance specimen because they exercise native absolute
height placement and bounded interior geological modification without requiring an accepted cave
topology.

## Accepted behavior

- `UNDERGROUND_ORES` retains the SF-IMP-0059 finite volume-envelope vertical mapping unchanged.
- `LOCAL_MODIFICATIONS` is explicitly admitted to the local vertical frame.
- Native `HeightRangePlacement` consumes its ordinary RNG first; Skyforge transforms only the
  resulting Y coordinate.
- For `LOCAL_MODIFICATIONS`, the mapped target span is the exact solid Skyforge ownership span at
  the already-selected X/Z column when such a column exists.
- X/Z are never relocated to manufacture support.
- If the selected X/Z contains no owner terrain, mapping falls back to the finite volume envelope and
  the existing exact-volume write fence remains authoritative.
- Registry feature identity, placement order, rarity, configured-feature behavior, and production RNG
  rules remain native.
- No feature IDs appear in production authorization policy.
- Vertically stacked Skyforge domains remain independent and foreign-volume writes fail closed.
- Vertically unrelated BASE_WORLD terrain remains unchanged.

## Runtime discovery and repair

The first SF-IMP-0060 runtime on
`56ecfebd1de43252c894c2686d4eba7e60126c6b` failed closed after the unchanged native
`minecraft:amethyst_geode` rarity gate fired but no persistent geode formed.

That result exposed an important distinction from ore placement: the finite volume bounding envelope
was `Y=[196,268]`, while actual owner-solid terrain in the central tableland column occupied a
narrower vertical support span. A sparse one-shot geological placement could therefore map into air
inside the conservative bounding box.

The accepted repair maps `LOCAL_MODIFICATIONS` against the exact owner-solid span of its selected
X/Z column while leaving the already accepted ore mapping untouched.

## Runtime acceptance evidence

### Native geode realization and repeat

Fresh disposable worlds produced:

`SF-IMP-0060 LOCAL MODIFICATIONS PASS`

Accepted repeat invariants:

- admission: 25 / 25 required chunks
- pending catch-up: 0
- proof chunks: 21
- attempted geode features: 21
- geode successes: 2
- height-range samples: 3
- native samples outside volume envelope: 3
- mapped samples outside volume envelope: 0
- mapped sample Y range: `[202,233]`
- transformed height samples: 3
- transform digest: `4fe92d09d07f8002`
- persistent amethyst / budding amethyst / calcite / smooth basalt material present
- vertically unrelated BASE_WORLD proof columns preserved

The exact persistent geode block totals differed between disposable Minecraft worlds because those
worlds did not share a native Minecraft world seed/environment. The accepted deterministic contract
for this milestone is the Skyforge coordinate transform and ownership decision stream; its digest
reproduced exactly.

### Stacked-volume isolation

The final stacked runtime produced:

`SF-IMP-0060 LOCAL MODIFICATIONS STACKED PASS`

At the same native X/Z and native Y sample:

- lower volume mapped to `Y=124`
- upper volume mapped to `Y=224`
- both mapped positions were solid-owned by their active exact volume
- neither mapped position belonged to the other stacked volume
- owner preflight accepted
- foreign preflight rejected in both directions

This proves that local-modification vertical virtualization is volume-scoped rather than X/Z-column
scoped.

### SF-IMP-0059 regression

The final ore regression produced:

`SF-IMP-0059 UNDERGROUND PLACEMENT PASS`

with the previously accepted invariants unchanged:

- transform digest: `3397c516a115d6e4`
- mapped samples outside volume: 0
- successful registry-native underground features: 9
- BASE_WORLD preserved

Therefore SF-IMP-0060 did not silently rewrite the accepted SF-IMP-0059 ore coordinate contract.

## CI

Exact-head CI passed compilation, tests, required Mixins, backend checks, and NeoForge/FML bootstrap.
The repository-wide shared authorship evidence-entry-point check remained red after the successful
Gradle build; that known workflow-wiring issue is outside this implementation milestone.

## Architectural consequence

Minecraft local geological modifications can now be expressed inside a vertically translated
Skyforge world volume while preserving exact 3-D ownership. The backend is no longer limited to
surface vegetation, persistent biome identity, ores, and native surface phases: bounded native
geological features such as amethyst geodes can execute against actual Skyforge-owned interior
support.

Cave-surface decoration and fluid/spring semantics remain intentionally unaccepted pending a separate
interior/cave topology milestone.
