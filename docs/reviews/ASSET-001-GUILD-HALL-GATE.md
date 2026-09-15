# ASSET-001 exact Guild Hall Minecraft gate

Status: **repeat human visual/usability gate required** for issue #488 after the first in-game review identified two bounded defects.

This packet does not redesign the Guild Hall. It carries the accepted first-principles v0.14 architectural language + Minecraft adapter v0.5 onto current `main`, with the smallest corrections required by the first human review.

## Authority and expected identity

- Governing issue: #488
- Frozen proof source: PR #489 / `asset-compiler-proof` @ `304e32dbb810043b4ce70b27da3360e39d613498`
- Current bounded repair source: PR #612 / `compiler/488-guild-gate`
- Specimen: `tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json`
- Architecture digest expected: `fb70c468ba0ba2a429542702844c71116099b73d42e6b966b27c29bb43b3526d`
- Architecture / realization-intent cells expected: 1,882
- Realized Minecraft cells expected after bounded v0.5 repairs: 1,894
- Palette remains provisional and is **not** the current acceptance question.

## Human-gate history

The first exact Minecraft review established that the architecture/readability works in principle: the building clearly read as a clerical/institutional Guild building, and the roof/overall architectural character were specifically judged positively.

Two concrete defects prevented full acceptance:

1. a calcite working-partition column visually/functionally dead-ended the staff-side doorway toward the warehouse/working rooms;
2. a freight-manifest detail block was visibly floating in the warehouse.

The bounded repair opens the working partition one column farther toward the staff connection and mounts the freight manifest against retained partition support. Regression tests lock both conditions. Furniture vocabulary may improve later richness, but it is not used to waive geometry defects in this gate.

## 1. Regenerate the exact artifact

From repository root in PowerShell:

```powershell
python tools/asset_compiler/compile.py `
  tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json `
  --out build/asset-compiler-v014-first-principles-detail `
  --minecraft-structure
```

Expected structure template:

```text
build/asset-compiler-v014-first-principles-detail/guild_branch_bootstrap_temperate_first_principles_detail_v0_14.nbt
```

The same output directory also contains the QA bundle, validation summary, realization intent, and Minecraft adapter report. Do not substitute an older v0.4-v0.13 structure or the pre-repair v0.14 artifact.

## 2. Make it available to the development client

Create the target directory if needed and copy the generated template:

```powershell
New-Item -ItemType Directory -Force `
  skyforge-neoforge-1211/src/development/resources/data/skyforge/structure | Out-Null

Copy-Item `
  build/asset-compiler-v014-first-principles-detail/guild_branch_bootstrap_temperate_first_principles_detail_v0_14.nbt `
  skyforge-neoforge-1211/src/development/resources/data/skyforge/structure/guild_branch_bootstrap_temperate_first_principles_detail_v0_14.nbt `
  -Force
```

Launch the ordinary NeoForge 1.21.1 development client:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runClient
```

Use a disposable creative, cheats-enabled world. Place the structure in clear space, for example:

```mcfunction
/place template skyforge:guild_branch_bootstrap_temperate_first_principles_detail_v0_14 0 120 0
```

Coordinate convention:

```text
+X east  = working / airfield face
+Y up
+Z south = public entrance face
```

## 3. Repeat-gate focus

The second review can be focused. Confirm all three of these before accepting:

1. **Staff-to-working circulation:** walk from the service/staff side through the east-facing staff connection toward the repair/warehouse side. The doorway must no longer visually or physically dead-end into calcite.
2. **Freight manifest/support:** inspect the warehouse detail. The previously floating freight-manifest block must now read as mounted against the working partition, with no new floating-detail defect nearby.
3. **Regression sanity:** make one exterior/interior pass to confirm the already-positive Guild/clerical readability, roof character, doors, freight/repair openings, and general editability remain intact.

If desired, the full original review matrix remains below:

| Gate | Question |
|---|---|
| Windows | Are recessed panes visibly closed against jambs/returns, with no accidental holes or bad pane topology? |
| Freight / repair openings | Are the working openings visibly clear and usable, especially the repaired freight-side margin? |
| Unsupported detail | Do benches, partial-height elements, brackets, signs, or trim avoid floating/unsupported reads? |
| Roof-edge attachments | Are overhang, ridge/signal, fascia, brackets, stairs/slabs, and attachment points coherent after neighbor updates? |
| Doors | Are both public and working doors correctly oriented, paired, unobstructed, and usable? |
| Lanterns | Are lights visibly supported, sensibly located, and stable after updates? |
| Containers | Do the two barrels and two chests load correctly, orient correctly, and remain valid block entities? |
| Overall Guild readability | Does the structure still read as a credible small Skyfarers' Guild branch rather than a generic house/warehouse? |
| Public vs working faces | Is the south public approach distinct and legible from the east working/airfield face? |
| Editability / usefulness | Does the realized structure look practical to hand-adjust locally without fighting opaque generated geometry? |

## 4. Decision

The gate passes if the two reported defects are corrected, no material regression is visible, and the repaired Hall remains visually/usably acceptable.

If another defect is target-medium-specific, record it precisely for the narrow Minecraft realization layer. If the architecture itself is clearly inferior or impractical, stop and reassess #488 rather than hiding the problem in adapter hacks.

After this human gate passes, the next #488 tranche is the **current-path sibling/ROI proof**. Do not begin functional Create machinery merely because exact Create resources are available.
