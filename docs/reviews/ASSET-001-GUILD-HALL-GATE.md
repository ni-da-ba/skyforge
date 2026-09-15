# ASSET-001 exact Guild Hall Minecraft gate

Status: **human visual/usability gate required** for issue #488.

This packet deliberately does not redesign the Guild Hall. It extracts the frozen accepted v0.14 architecture + Minecraft adapter v0.5 from PR #489 onto current `main` so the repaired target artifact can be regenerated and reviewed exactly.

## Authority and expected identity

- Governing issue: #488
- Frozen proof source: PR #489 / `asset-compiler-proof` @ `304e32dbb810043b4ce70b27da3360e39d613498`
- Specimen: `tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json`
- Architecture digest expected: `cbb02ebaea269bbb836d96fe11edf2ac2716fbfd0e7bac8b963fa9430b2991b8`
- Architecture / realization-intent cells expected: 1,885
- Realized Minecraft cells expected after bounded v0.5 repairs: 1,897
- Palette remains provisional and is **not** the current acceptance question.

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

The same output directory also contains the QA bundle, validation summary, realization intent, and Minecraft adapter report. Do not substitute an older v0.4-v0.13 structure.

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

## 3. Exact review questions

Review at player eye height and from useful three-quarter views. Record **PASS / FAIL + note** for each item:

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

Also walk the public entrance -> service counter -> staff/working side circulation and inspect warehouse/repair clear floor space.

## 4. Minimum screenshot set

Capture:

1. south/public approach;
2. east/working-airfield face;
3. public interior + service counter;
4. staff/counter reverse side;
5. warehouse/freight opening;
6. repair bay;
7. one roof-edge / signal three-quarter view;
8. any defect close-up.

## 5. Decision

The gate passes only if the repaired v0.5 realization is visually/usably acceptable without reopening the accepted v0.14 architecture merely to avoid a human decision.

If a defect is target-medium-specific, record it precisely for the narrow Minecraft realization layer. If the architecture itself is clearly inferior or impractical, stop and reassess #488 rather than hiding the problem in adapter hacks.

After this human gate passes, the next #488 tranche is the **current-path sibling/ROI proof**. Do not begin functional Create machinery merely because exact Create resources are available.
