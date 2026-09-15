---
handoff_schema: skyforge.compiler-handoff.v1
scope: wby-structure-integration
status: superseded-by-compiler-program-reorganization
feature_freeze: true
repository: ni-da-ba/skyforge
issue: 488
parent_pr: 489
handoff_branch: handoff/wby-structure-shutdown-20260914
main_observed_sha: 3b6a16c4f300738cbbcffd5829fd71a65006a390
pinned_runtime:
  minecraft: 1.21.1
  neoforge: 21.1.249
  create: 6.0.10+mc1.21.1
  create_coordinate: maven.modrinth:LNytGWDc:UjX6dr61
  create_source_commit: 79b5d3b37e2d1970818dd97ca460b649cd0a456c
source_stack:
  - pr: 489
    branch: asset-compiler-proof
    head: 304e32dbb810043b4ce70b27da3360e39d613498
    base: main
    disposition: retained-open-parent-proof
  - pr: 595
    branch: wby-structure-integration
    head: 439a967d4dbec07b64367c6a0e7ab646fd2adc50
    base: asset-compiler-proof
    disposition: archival-snapshot-close
  - pr: 605
    branch: wby-structure-topology
    head: ceee763df8a8994b08c20ff8c9610992b71fc71e
    base: wby-structure-integration
    disposition: archival-snapshot-close
  - pr: 606
    branch: wby-structure-ladder
    head: cf535bc3ad8f0b4a3a0d5abf589adbe52f3953eb
    base: wby-structure-topology
    disposition: archival-snapshot-close
  - pr: 610
    branch: wby-structure-scaffolding
    head: e0c009fb148ff8bb2a4d7c569af5ac86fc427c6b
    base: wby-structure-ladder
    disposition: archival-snapshot-close
  - pr: 611
    branch: wby-structure-girder
    head: 9971ba964b18c584a79eec659955c0a869424e61
    base: wby-structure-scaffolding
    disposition: archival-snapshot-close
proof_runs:
  pr_489:
    asset_compiler_proof: "#110 success"
    canonical_ci: "#2455 success"
  pr_595:
    wave_c21_create_resource_authority: "#41 success"
    asset_compiler_proof: "#143 success"
    canonical_ci: "#2834 success"
  pr_605:
    asset_compiler_proof: "#151 success"
    canonical_ci: "#2843 success"
  pr_606:
    asset_compiler_proof: "#159 success"
    canonical_ci: "#2856 success"
  pr_610:
    asset_compiler_proof: "#167 success"
    canonical_ci: "#2865 success"
  pr_611:
    asset_compiler_proof: "#175 success"
    canonical_ci: "#2875 success"
active_create_resources:
  - create:andesite_casing
  - create:brass_casing
  - create:framed_glass_pane
cataloged_nonactive_create_resources:
  - create:copper_casing
  - create:industrial_iron_block
  - create:weathered_iron_block
  - create:industrial_iron_window_pane
  - create:ornate_iron_window_pane
  - create:andesite_bars
  - create:brass_bars
  - create:copper_bars
  - create:andesite_ladder
  - create:brass_ladder
  - create:copper_ladder
  - create:andesite_scaffolding
  - create:brass_scaffolding
  - create:copper_scaffolding
  - create:metal_girder
observation_only_structural_resources_at_tip: 0
ownership: relinquished
---

# Issue #488 / WBY Structure Compiler — successor handoff

This file is the durable shutdown checkpoint for the WBY structure-integration stream. It records the frozen feature state. The replacement compiler program should reconstruct from these branches and artifacts, then establish fresh working branches rather than extending the archived WBY stack indefinitely.

## A. Accepted compiler architecture

The accepted lowering boundary at the structure-compiler tip is:

```text
AssetSpec / semantic architecture
  -> geometry / voxel semantics
  -> backend-neutral realization intent
  -> Minecraft target capability resolution
  -> topology / support correction
  -> block states / explicit block-entity NBT
  -> structure NBT
```

Architecture authority ends before concrete Minecraft resource identity. `asset_realization_ir.py` / Guild realization intent carries semantic material families, required capabilities, orientation and geometry properties, but not target resource names. Minecraft adapters and target profiles own concrete resource selection, block-state normalization, neighbor topology, support checks, bounded target-medium repairs, block-entity payloads and structure-template serialization.

The destructive boundary test that erases upstream Minecraft resource locations before target lowering must remain. A target catalog is realization evidence, not authority to invent architectural intent.

## B. WBY target state

### Active realization resources

| Neutral intent | Create 6.0.10 resource | Status | Rule / remaining gate |
|---|---|---|---|
| generic industrial hardware / masonry hardware | `create:andesite_casing` | active | state-free full cube / solid support; target preference only |
| warm/brass hardware | `create:brass_casing` | active | state-free full cube / solid support; target preference only |
| generic pane glazing | `create:framed_glass_pane` | active | existing pane topology derives cardinal state; active because generic pane-shaped glazing already exists upstream |

These substitutions affect only the opt-in `wby-c1-create` target profile. The ordinary vanilla Guild compile path remains unchanged.

### Cataloged, non-active material/glazing resources

| Resource | Neutral capability | Status | Activation requirement |
|---|---|---|---|
| `create:copper_casing` | full cube / solid support; copper detail/hardware family | cataloged | explicit semantic selection reason; availability alone is insufficient |
| `create:industrial_iron_block` | full cube / solid support; industrial metal/hardware family | cataloged | explicit semantic selection reason |
| `create:weathered_iron_block` | full cube / solid support; weathered/history hardware family | cataloged | explicit semantic selection reason |
| `create:industrial_iron_window_pane` | pane / neighbor-sensitive / thin | cataloged | upstream industrial-glazing distinction |
| `create:ornate_iron_window_pane` | pane / neighbor-sensitive / thin | cataloged | upstream institutional/ornate-glazing distinction |

`create:framed_glass` remains deferred: the current Guild glazing intent is pane-shaped, so a full glass cube must not be selected merely for palette variety.

### Implemented neutral structural-detail semantics whose Create resources remain catalog-only

**Bars / railing — #605.** Neutral capability `bars` is thin and cardinal-connectable. Cardinal state is derived only from adjacent bars or explicit full/solid support; glazing does not cross-connect and `fence` is not silently reinterpreted. `create:andesite_bars`, `create:brass_bars`, and `create:copper_bars` are cataloged only. Activation requires explicit upstream railing/bars intent.

**Ladder / climbable — #606.** Neutral capabilities include `ladder`, `climbable`, `directional`, `attachment_sensitive`, `thin`. `facing` is outward; compiler validity requires sturdy backing on the opposite side. The generic contract intentionally does not depend on Create `MetalLadderBlock`'s same-ladder-above survival relaxation. The three Create metal ladders are cataloged only. Activation requires upstream access/climbable intent.

**Scaffolding — #610.** Neutral capabilities include `scaffolding`, `climbable`, `neighbor_sensitive`. Authored `bottom` and `distance` are non-authoritative. Support distance is solved over the whole scaffold cluster: sturdy support seed = 0, vertical inheritance, horizontal neighbor +1, cap at 7; distance 7 / unreachable cells are rejected for portable validity. `bottom` is derived from the cell below. This is intentionally stricter than Create 6.0.10, whose metal scaffold disables vanilla survival/falling. The three Create metal scaffolds are cataloged only. Activation requires upstream scaffolding/access intent.

**Girder / brace — #611.** Neutral capabilities include `girder`, `axis_orientable`, `neighbor_sensitive`, `thin`. Authored `axis` is authoritative; `x`, `z`, `top`, `bottom` are derived. Horizontal beam flags come from the primary axis and compatible neighboring girders; vertical flags come only from neighboring girders or ordinary sturdy support contact. Create-specific connectivity to tracks, brackets, nixie tubes, placards, chutes, shafts, walls, lanterns and other gameplay blocks is deliberately excluded. `create:metal_girder` is cataloged only. Activation requires upstream girder/brace intent.

At #611 there are **zero observation-only resources** in the bounded stateful structural set: bars, ladders, scaffolds and the girder all have generic compiler semantics plus exact runtime contracts, but none have placement authority for the current Guild specimen.

### Exact Create runtime evidence

Pinned target: Minecraft 1.21.1, NeoForge 21.1.249, Create `6.0.10+mc1.21.1`, immutable coordinate `maven.modrinth:LNytGWDc:UjX6dr61`. Source audit provenance is Create commit `79b5d3b37e2d1970818dd97ca460b649cd0a456c`; source inspection is evidence, not a substitute for runtime validation.

The exact-artifact `SkyforgeWbyC1StructureRegistryProbe` at #611 validates 18 catalog blocks live and requires:

- 3 pane contracts: `north/east/south/west/waterlogged` booleans, all false by default;
- 3 bars contracts: same five booleans, all false by default;
- 3 ladder contracts: `facing={north,east,south,west}`, `waterlogged` boolean; defaults north/false;
- 3 scaffolding contracts: `bottom` boolean, `distance=0..7`, `waterlogged` boolean; defaults false/7/false;
- 1 girder contract: `axis={x,y,z}`, `bottom/top/waterlogged/x/z` booleans; defaults axis=y and booleans false;
- state-free presence/default surfaces for the casing/iron blocks.

The probe writes PASS/FAIL evidence and the workflow asserts `catalogBlocksValidated=18`, pane/bars/ladder/scaffolding/girder counts `3/3/3/3/1`, and `deferredStructuralContractsObserved=0`.

### Wave C1 authority boundary

Only Create owns structure capability-catalog authority in this tranche. RPL and JEI have no structure contract. Create Big Cannons, Create Crafts & Additions and Create Metallurgy remain deferred pending explicit functional semantics. Sable, Create Aeronautics and Create Propulsion are aircraft-owned-deferred and must not be pulled into the structure compiler by this handoff.

Target availability alone is never semantic authority.

## C. Issue #488 proof state

### Proven

- bounded semantic `AssetSpec`/family grammar and deterministic voxel lowering exist;
- v0.13/v0.14 first-principles Guild architecture is geometry authority; v0.14 adds bounded downstream detail;
- resource-name-free realization intent is established and regression-tested;
- Minecraft capability/state lowering, pane/fence/stair topology, support validation and bounded target-medium repair exist;
- exact Java 1.21.1 capability/state validation exists;
- deterministic structure-template NBT export exists;
- explicit block-entity NBT exists for the current two barrels and two chests (`id` plus empty `Items`), with coverage checks;
- QA outputs and deterministic digest are generated;
- a four-bay sibling exists in earlier v0.2/v0.3 specimens, demonstrating initial parameter leverage rather than a single fixed script.

The current v0.5 target result preserves the architecture digest `cbb02ebaea269bbb836d96fe11edf2ac2716fbfd0e7bac8b963fa9430b2991b8`, lowers 1,885 architecture/IR cells to 1,897 target cells after bounded repairs, and reports zero realized-geometry issues in automated validation.

### Still outstanding

1. **Minecraft human visual/usability gate:** regenerate and place the exact v0.14/v0.5 Guild Hall and recheck window closure, freight/repair openings, unsupported/floating detail, roof-edge attachments, doors, lanterns and containers. The first in-engine inspection accepted the architectural composition but exposed target-medium defects; v0.5 repaired them automatically, and that repaired exact artifact still needs the repeat visual gate. Palette finalization is not the current gate.
2. **ROI / sibling qualification:** earlier four-bay siblings are useful evidence, but Stage 7 of #488 is not treated as formally closed. The replacement program should qualify a meaningful sibling/revision against the current accepted compiler path and compare authoring/revision leverage against a manual equivalent.
3. **Functional Create mechanisms:** machinery, storage/gameplay block entities, Create/addon mechanisms and their lifecycle/NBT policies are not authorized by the current structure work. Introduce them only when a real asset demands them.
4. **Shared package convergence:** current structure code is still proof-branch infrastructure; the replacement compiler program owns convergence toward the shared compiled-asset package.

## D. Exact stacked PR / branch map

```text
main @ 3b6a16c4f300738cbbcffd5829fd71a65006a390   (observed at shutdown reconstruction)
  \
   asset-compiler-proof @ 304e32dbb810043b4ce70b27da3360e39d613498  PR #489
      \
       wby-structure-integration @ 439a967d4dbec07b64367c6a0e7ab646fd2adc50  PR #595
          \
           wby-structure-topology @ ceee763df8a8994b08c20ff8c9610992b71fc71e  PR #605
              \
               wby-structure-ladder @ cf535bc3ad8f0b4a3a0d5abf589adbe52f3953eb  PR #606
                  \
                   wby-structure-scaffolding @ e0c009fb148ff8bb2a4d7c569af5ac86fc427c6b  PR #610
                      \
                       wby-structure-girder @ 9971ba964b18c584a79eec659955c0a869424e61  PR #611
```

Each stacked PR base SHA exactly matched the predecessor head when reconstructed. The handoff branch was forked from the frozen #611 code head so the source branch HEADs remain unchanged.

## E. Proofs and workflows

Final recorded checks on source heads:

| PR | Dedicated / exact-stack proof | Canonical CI |
|---|---|---|
| #489 | Asset compiler proof #110 — success | CI #2455 — success |
| #595 | Wave C21 Create Resource Authority #41 — success; Asset compiler proof #143 — success | CI #2834 — success |
| #605 | Asset compiler proof #151 — success | CI #2843 — success |
| #606 | Asset compiler proof #159 — success | CI #2856 — success |
| #610 | Asset compiler proof #167 — success | CI #2865 — success |
| #611 | Asset compiler proof #175 — success | CI #2875 — success |

The current asset-compiler proof workflow runs the Python unit suite, compiles v0.12/v0.13/v0.14, compiles v0.14 through WBY, resolves the exact Create artifact with `:skyforge-neoforge-1211:waveC9ResolvePinnedMods --no-configuration-cache`, then launches the isolated C21 Create-capable server with the WBY registry probe enabled. The job has a 20-minute workflow timeout.

## F. Integration lessons / successor constraints

1. **Demand-driven semantics beat vocabulary accumulation.** Bars, ladders, scaffolds and girders were implemented only as neutral semantics and kept catalog-only because the current Guild does not request them. Do not activate them to showcase Create content.
2. **`cataloged` must be mechanically non-selectable.** An early loader bug parsed status but admitted catalog entries anyway. The corrected loader is fail-closed; keep tests that prove catalog-only resources cannot enter resolver output.
3. **Source audit is not runtime proof.** Exact Create source was useful for behavior, but stateful promotion required the immutable Wave C1 artifact and live registry/default-state evidence.
4. **Neutral contracts should be narrower than target-specific convenience behavior.** Ladder support, scaffold support-distance and girder connectivity deliberately avoid Create-only survival/attachment shortcuts so generated structures remain semantically portable.
5. **Do not alias semantics for appearance.** `fence != bars`, `window != bars`, timber `structural_frame != girder`, and pane glazing != full-cube glazing. Semantic aliasing created the main ambiguity risk in this work.
6. **The stacked PR model preserved reviewable increments but is now exhausted.** Do not keep extending this chain; reconstruct/cherry-pick into the redesigned compiler program.
7. **Exact-stack validation is heavier than unit validation.** The current workflow runs complete Guild specimens plus an exact Create server for every matching compiler change. The replacement program should separate L1 compiler/unit validation, L2 minimal exact-stack fixtures and L3 complete-specimen qualification.
8. **Runtime waits need tighter classification.** No reproducible hang is left open, but the exact-stack Gradle/server step is currently bounded mainly by the job-level 20-minute timeout. The replacement L2 harness should use bounded waits and classify artifact-resolution, boot, registry-probe and shutdown failures separately.
9. **Current work already fits much of the new model:** synthetic topology and fail-closed resolver tests are L1; the pinned registry probe is the core of L2; full v0.14 Guild compilation plus in-game placement is L3. The main mismatch is that L2 and L3 are still bundled in one heavyweight workflow.
10. **Exact registry proof is not aesthetic or gameplay proof.** It establishes resource/state contracts only. Human inspection remains authoritative for readability, contact, opening usability and overall composition.

## G. Ownership and recovery

This WBY/structure-integration agent has ended feature ownership. No new capability tranche was started during shutdown. Source branches and commits are intentionally preserved for cherry-pick/rebase/reference. The WBY draft PRs are archival snapshots and are to be closed as superseded by the compiler-program reorganization, not merged. PR #489 remains the parent #488 proof snapshot unless separately reorganized by the replacement structure agent.

Issue #488 has no GitHub assignee at this checkpoint. No stale WBY agent claim should block the replacement compiler/structure agent.
