# ASSET-001 exact Guild Hall Minecraft gate

Status: **ACCEPTED** for the issue #488 primary Guild Hall proof.

This packet records the accepted first-principles v0.14 Guild Hall architecture and Minecraft adapter v0.5 realization on the current successor path. The frozen WBY / PR #489 history remains evidence only.

## Authority and accepted identity

- Governing issue: #488
- Frozen proof source: PR #489 / `asset-compiler-proof` @ `304e32dbb810043b4ce70b27da3360e39d613498`
- Accepted successor tranche: PR #612 / `compiler/488-guild-gate`
- Specimen: `tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json`
- Architecture digest: `042720a9c1b0720d351863141a53dce59f43c0ef7b1ba1546f39a6622ad25c97`
- Architecture / realization-intent cells: 1,857
- Realized Minecraft cells after bounded v0.5 repairs: 1,869
- Adapter: `minecraft-adapter-0.5`, target `minecraft-java-1.21.1`
- Operational-clearance repair count: 0
- Palette state: **deferred**; no Guild color palette is accepted.

The accepted realization intentionally contains no `minecraft:blue_wool`, `minecraft:yellow_terracotta`, or `minecraft:blue_carpet`. Identity surfaces are neutral dark timber / ordinary masonry hardware until a dedicated identity-material gate establishes canon.

## Human-gate history

The first exact Minecraft review established that the architecture worked in principle: the Hall clearly read as a clerical/institutional Guild building, with the roof and overall architectural character judged positively. It also found a calcite dead-end at the staff connection and a floating freight-manifest block.

The next review found the building sane and readable but identified an unnecessary calcite warehouse divider and nonfunctional glazing over the freight portal. The compiler was changed at the architectural source: the working wing became open-plan and freight transom glazing was removed.

The following review found a remaining repair-bay transom and a decorative stone-slab plinth obstructing the public threshold. Both were removed at source; the public approach is now clear across the two-door entrance plus one-cell shoulders.

Final human result from Nicholas: **“no obvious structural problems remain.”** The only requested follow-up was removal of the provisional blue/yellow Guild colors, which was completed as the neutral palette deferral above.

## Accepted invariants

1. The Hall reads as a credible clerical/institutional Skyfarers' Guild building.
2. Staff-to-working circulation does not dead-end into a partition.
3. The repair / warehouse working wing is open-plan rather than divided by decorative masonry.
4. Neither working bay door has nonfunctional transom glazing.
5. The public customer threshold is unobstructed.
6. No freestanding freight-manifest detail is forced into the open warehouse.
7. Unsupported waiting-bench back detail is omitted by the bounded target-realization repair policy.
8. Doors, lanterns, containers, recessed windows, roof attachments, and working portals pass the current machine gates.
9. Provisional Guild blue/yellow identity colors are not present in the accepted current realization.

## Regeneration

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

To load it in the development client:

```powershell
New-Item -ItemType Directory -Force `
  skyforge-neoforge-1211/src/development/resources/data/skyforge/structure | Out-Null

Copy-Item `
  build/asset-compiler-v014-first-principles-detail/guild_branch_bootstrap_temperate_first_principles_detail_v0_14.nbt `
  skyforge-neoforge-1211/src/development/resources/data/skyforge/structure/guild_branch_bootstrap_temperate_first_principles_detail_v0_14.nbt `
  -Force

.\gradlew.bat :skyforge-neoforge-1211:runClient
```

Then:

```mcfunction
/place template skyforge:guild_branch_bootstrap_temperate_first_principles_detail_v0_14 0 120 0
```

## Decision and next boundary

The primary Guild Hall proof is accepted. Furniture / richer furnishing vocabulary and the final Guild identity-material palette remain later concerns; neither is allowed to reopen accepted architecture without a new concrete requirement.

The next issue #488 tranche is the **current-path contrasting sibling / ROI proof**. It must reuse the accepted compiler, quantify reuse versus sibling-specific overrides, and demonstrate that producing a useful second building is materially cheaper than hand-authoring it. Do not begin Create functional machinery merely because resources exist; that follows only after #488 generality is established and Agent A has runtime-qualified the shared integration path.
