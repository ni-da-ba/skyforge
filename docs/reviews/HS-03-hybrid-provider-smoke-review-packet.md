# HS-03 — AUTH-0083 hybrid/provider smoke review packet

## Scope and decision boundary

This is the smallest issue #214 owner-review packet: exactly two isolated AUTH-0083 specimens.
It deliberately excludes the remaining hybrid/provider atlas, regional contexts, materials,
hydrology, ecology, and any morphology retune.  The owner should judge only coherence,
macro/meso readability, rim and underside quality, and visible procedural artifacts.  A PASS
hands the next work to integrated world-system realization; it does not accept a production atlas.

## Exact repository boundary

- branch/ref: `codex/implementation-20260910-210159` at
  `e0abd822fc19b2f9e4e5f5caf8ad3369c3547bde`;
- authoritative semantic corpus: `production-morphology-visual-review-v1`;
- geometry seed: `0x534b59464f524745` (decimal `6001124484107689797`);
- scale: `MEDIUM` (`1.0`); source suspension: `512.0`;
- descriptor values: center `(0, 0)`, nominal radius `160.0`, upper elevation `60.0`, underside
  depth `80.0`, coastal falloff `40.0`, ridge azimuth `pi/6`, ridge strength `0.65`, underside
  taper `0.60`, underside asymmetry `0.25`, signal amplitude `0.0`, signal scale `20.0`.

| Review role | Exact AUTH-0083 member | Provider/property values |
| --- | --- | --- |
| Built-in pairwise hybrid | `hybrid-massif-spine-midpoint` | `skyforge:massif + skyforge:spine`, second weight `0.5`; detail `1.0`; secondary morphology `1.0` |
| External-provider axis | `provider-crescent-to-lobed-midpoint` | `reference:crescent + skyforge:lobed`, second weight `0.5`; detail `1.0`; secondary morphology `1.0` |

The two midpoint blends use the accepted provider-neutral
`SkyIslandMorphologySpecCompiler`.  The crescent member is specifically an external-provider
*axis* specimen, not a claim that `reference:crescent` is shipping content.

## Cheap objective gate and supplemental reference artifact

Run this on Windows PowerShell to regenerate the accepted semantic reference package:

```powershell
.\gradlew.bat :skyforge-reference:productionMorphologyVisualReviewCorpus --no-configuration-cache
```

The useful outputs are:

```text
skyforge-reference\build\evidence\production-morphology-visual-review-v1\index.html
skyforge-reference\build\evidence\production-morphology-visual-review-v1\summary.csv
skyforge-reference\build\evidence\production-morphology-visual-review-v1\minecraft-handoff.csv
skyforge-reference\build\evidence\production-morphology-visual-review-v1\hybrid-massif-spine-midpoint\
skyforge-reference\build\evidence\production-morphology-visual-review-v1\provider-crescent-to-lobed-midpoint\
```

For the corresponding cheap deterministic test gate:

```powershell
.\gradlew.bat :skyforge-reference:test --tests io.github.nidaba.skyforge.reference.volume.ProductionMorphologyVisualReviewCorpusTest --no-configuration-cache
```

The corpus test checks all 41 accepted members compile through the production spec compiler,
including both selected IDs, full detail/secondary settings, and the existing hard corpus topology
machinery.  It intentionally adds no aesthetic threshold.

## Minecraft launch and review status — BLOCKED

There is **no executable current-main Minecraft carrier** for either selected member.  Current
NeoForge launch tasks and run directories cover only the earlier built-in specimens; they cannot
accept a hybrid ID or register `reference:crescent`.  Therefore no honest Windows PowerShell
Minecraft launch command, world directory, or in-game route can be supplied for these two exact
members at this ref.

Do not substitute a built-in atlas world or rename it as either specimen.  The missing carrier is
the machine-repair handoff: it must retain the exact IDs/properties above, register the accepted
reference provider only in its development fixture, prove pure integer-Y relocation, exact finite
admission, top/underside boundary storage with air above/below, and mutation-inert reopen before
owner visual review.

Once that carrier exists, use separate, explicitly named run directories (for example
`run-skyforge-hs-03-hybrid-massif-spine` and `run-skyforge-hs-03-provider-crescent-lobed`) and
provide a launch task per member.  These names are requirements for the successor implementation,
not commands available at this ref.

## Guided owner route after the carrier gate

For each member, begin in spectator and capture/inspect these stops:

1. `above` — distant planform, top elevation, macro/meso hierarchy.
2. `approach` — horizon silhouette and low-angle rim/coast transition.
3. `below` — dedicated underside, taper/shelves, rim continuity, spikes or pinches.
4. `orbit` — circle the rim, descend, and make one under-island pass; look for repeated or noisy
   procedural signatures.

Record PASS/FAIL separately for each specimen.  PASS means only that this hybrid/provider smoke
does not expose a blocker in the stated criteria; it pivots #214 to integrated world-system work.
Any family/control/realization diagnosis remains owned by its respective lane.
