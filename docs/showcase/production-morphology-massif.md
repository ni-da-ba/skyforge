# SF-IMP-0081 — First production morphology Minecraft carrier

## Scope

SF-IMP-0081 is the first Minecraft realization tranche for issue #214.

It does **not** define or tune a new island shape. The specimen is the exact AUTH-0083 member:

```text
builtin-massif-small-seed-skyforge
```

That member carries:

- built-in `skyforge:massif` morphology;
- canonical Skyforge geometry seed `0x534b59464f524745`;
- AUTH-0083 SMALL scale;
- nominal radius 160;
- upper elevation 60;
- underside depth 80;
- coastal falloff 40;
- full bounded detail;
- full provider secondary morphology;
- provider-neutral `SkyIslandMorphologySpecCompiler` compilation.

The AUTH-0083 source specimen uses suspension Y 512, which is outside Minecraft 1.21.1's build
range. The adapter derives the source specimen's exact integer voxel support, applies one **integer Y
translation only**, recompiles the otherwise identical descriptor, and proves that the translated
integer support is exactly the source support shifted by the same delta.

## Backend support bounds

The generic provider certificate is deliberately conservative. Using it directly as Minecraft
physical-admission bounds would force many empty perimeter chunks into the finite proof.

SF-IMP-0081 therefore adds a backend-local exact integer-support derivation:

1. retain the provider's certified horizontal support as the finite completeness proof;
2. evaluate compiled upper/underside surfaces only at integer Minecraft X/Z columns inside it;
3. derive the exact first/last integer solid Y for every occupied column;
4. shrink the runtime `WorldBounds` to the extrema of actual integer voxel support;
5. fail if occupied support touches the certified scan boundary.

This changes no morphology semantics. It is a realization optimization and a reusable bridge for the
remaining AUTH-0083 specimens.

## First-carrier lifecycle

The first morphology carrier deliberately isolates shape from later visual systems:

```text
native/base world
  -> native surface snapshot
  -> exact AUTH-0083 Massif occupancy
  -> whole-volume physical admission
  -> deferred stable-chunk catch-up
  -> save
  -> mutation-inert actual-client reopen
```

Native-surface adaptation remains active so the carrier has readable Minecraft land material.
Caves, native ecology population, interior population, and biome-presentation mutation are
deliberately absent in this tranche. SF-IMP-0080 already proved the ecology lifecycle, while #214
first needs an unobscured macro/meso/underside morphology judgment.

Machine acceptance checks only objective realization facts:

- exact AUTH-0083 member identity and parameters;
- translated integer support fits Minecraft;
- the tight footprint fits the bounded acceptance harness;
- whole-volume physical admission reaches ADMITTED with complete evidence;
- deferred catch-up reaches zero;
- sampled compiled top heights match runtime exact-volume height claims;
- sampled top and underside boundary blocks are present;
- immediate air above/below those boundaries remains air;
- native land material is present on sampled tops;
- a deterministic sampled geometry digest survives save/reopen;
- mutation bindings remain inert in the viewer.

No aesthetic threshold is introduced.

## Automated commands

Windows PowerShell:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyMassifPrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyMassifViewerVerify --no-configuration-cache
```

Linux/macOS:

```text
./gradlew :skyforge-neoforge-1211:productionMorphologyMassifPrepareVerify --no-configuration-cache
./gradlew :skyforge-neoforge-1211:productionMorphologyMassifViewerVerify --no-configuration-cache
```

## Human #214 review

After both automated gates pass:

Windows:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:launchProductionMorphologyMassif --no-configuration-cache
```

Use:

```text
/skyforge_morphology above
/skyforge_morphology approach
/skyforge_morphology below
/skyforge_morphology orbit
```

Review this as the **first tranche**, not as final #214 acceptance.

### Above

Judge:

- planform and long-range Massif identity;
- macro asymmetry;
- whether large and medium forms read before micro noise;
- upper-surface hierarchy.

### Approach

Judge:

- horizon silhouette;
- rim/coast transition;
- macro/meso readability;
- whether the form looks deliberately authored rather than generic noise.

### Below

Judge:

- underside taper and asymmetry;
- whether the underside reads as part of the same geological object;
- detached spikes, implausibly thin shelves, pinches, or random-stalactite appearance.

### Orbit

Fly around the rim, descend, and pass underneath. Judge the shape from realistic movement rather
than only static orthographic-like stops.

Record concrete observations. Do **not** encode aesthetic thresholds until Minecraft observations are
correlated with AUTH-0083 diagnostics.

## Next tranche

If this carrier mechanism is technically green, expand the exact same path to the other four built-in
AUTH-0083 families before hybrids/provider axes and AUTH-0084 regional scenes. Human findings from
those five family carriers determine whether current underside morphology is sufficient or whether
Authorship needs a genuine underside-secondary vocabulary.
