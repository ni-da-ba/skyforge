# Staged Mod Integration Strategy

**Snapshot:** 2026-10-02  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.249  
**Selection authority:** [Canonical Mod Ledger](canonical-mod-ledger.md)  
**Purpose:** turn the selected Wild Blue Yonder/Skyforge dependency roster into a sequence of launchable, reproducible integration profiles while core Skyforge world-authorship work continues independently.

This strategy replaces the idea of waiting for core Skyforge to finish and then assembling the entire modpack in one step. It also replaces a monolithic "install everything, then debug" convergence pass.

## Principle

Each tranche must produce a **launchable profile**.

A profile is not accepted merely because Gradle resolves its jars. It must have:

1. immutable artifact pins;
2. explicit client/server/common classification;
3. dependency closure with no duplicate nested libraries;
4. one dedicated-server boot;
5. one actual-client boot/join/world-open path;
6. save/reload of the same world;
7. representative capability smoke checks for the newly admitted slice;
8. checks for unauthorized native worldgen/spawn/config authority;
9. bounded performance sanity evidence;
10. an explicit promotion result: **PROMOTE / KEEP OPTIONAL / REJECT / BLOCKED**.

Historical per-mod evidence should be reused. Do not rerun every isolated acceptance suite unless cumulative integration exposes a new failure class.

## Two-track development

```text
CORE SKYFORGE
descriptors -> morphology -> geology/hydrology -> structures/ecology -> dimension authorship
        |
        | progressively replaces temporary/native authorities
        v
INTEGRATION PROGRAM
pins -> launch profiles -> compatibility -> normalization -> playtest -> roster freeze
```

The integration program must not invent missing Skyforge semantics merely to make a profile launch. Where core authority is not ready, use vanilla/mod behavior as **temporary scaffolding** and record the ownership seam that will later be intercepted.

## Profile architecture

Use one stable cumulative spine plus isolated comparison overlays.

```text
BASE-0  physical/runtime foundation
   |
   +-- S0  canonical launch shell
   |
   +-- S0.5 pack integration/information shell
   |
   +-- S1  atmosphere + personal mobility
   |
   +-- S2  computing/control/logistics
   |
   +-- S3  industry/power/propulsion
   |
   +-- S4  ordinary life/building
   |
   +-- S5  Overworld ecology/biomes
   |
   +-- S6  structures/civilization
   |
   +-- S7  threats/combat
   |
   +-- S8  Nether
   |
   +-- S9  End
   |
   +-- S10 onboarding/operations/economy bridges
   |
   '-- S11 full convergence
```

An A/B candidate is **not** automatically added to the cumulative spine. Compare it as an overlay on the nearest accepted baseline. Promote only the winner.

# Existing accepted starting evidence

The repository has already done substantial useful work. Do not throw it away.

- **WBY-INT-0001 / #1341 — PASS:** Create + Sable + Create Aeronautics flight substrate.
- **WBY-INT-0002 / #1342 — PASS:** Sodium + Distant Horizons + SSRD long-range visibility baseline.
- **WBY-INT-0003 / #1351 — PASS:** conservative optimizer layering.
- **WBY-INT-0004 / #1411 and #1412 — accepted:** Reliable Gliders + A4MC compatibility, exact resolution/staging, distant-Sable visibility, shared-lift behavior, and client/server separation. Reuse this evidence in S1; the remaining D-03 gate is current-stack gameplay/role review and coexistence with Ornithopter.
- **WBY-INT-0005 / #1418 — PASS:** CC:Tweaked + Create: Avionics cumulative compatibility.
- **WBY-INT-0006 / #1422 — still open:** Diesel Generators must be re-proven in the current canonical cumulative profile; the obsolete failed composition should not be repaired blindly.

These results mean integration begins from a real tested substrate rather than from zero.

# S0 — canonical launch shell

**Goal:** establish one reproducible launch profile containing only already-selected foundational components and accepted conservative presentation/performance dependencies.

### Baseline
- Minecraft 1.21.1 / NeoForge 21.1.249
- Create
- Sable
- Create Aeronautics
- Sodium
- Distant Horizons
- SSRD or current selected distant-Sable solution
- accepted conservative optimizer set

### Exclusions
No unresolved A/B gameplay dependency is forced into S0.

### Exit
- exact manifest committed;
- dedicated server green;
- actual client creates/opens world;
- save/reload green;
- representative aircraft visible near and far;
- no client-only mod leaks server-side;
- bounded performance sample recorded.

**Status:** largely covered by WBY-INT-0001..0003; S0 work should consolidate, not re-prove from scratch.

# S0.5 — pack integration and information shell

**Goal:** establish the common modpack-integration and basic information layer before broad content arrives.

### Integration authority
- KubeJS
- KubeJS Create
- LootJS
- Paxi
- Almost Unified under explicit canonical-material allowlist only

These integrate Minecraft-facing recipes, tags, loot, and pack delivery. They do not own Skyforge simulation semantics.

### Information/QoL baseline
- JEI
- Jade + Jade Addons + Jade Sable Compat
- Controlling
- Mouse Tweaks
- Crafting Tweaks
- AppleSkin
- Polymorph
- Clumps
- Map Atlases under no-radar/no-auto-reveal policy
- Spyglass Improvements

### Exit
- pack-authored configs/datapacks/scripts load reproducibly;
- canonical recipe/loot suppression hooks are available before content expansion;
- information tools expose existing state without granting omniscient navigation;
- dedicated server does not load client-only helpers.

# S1 — atmosphere and personal mobility

**Goal:** close the shared environmental/mobility foundation without pulling in broad world content.

### Candidate layers
- Aerodynamics4MC — D-01
- Particle Rain
- Simple Clouds — selected D-02 renderer foundation; owner confirmed visibility with Distant Horizons
- Reliable Gliders — early-game personal soaring candidate, reusing accepted WBY-INT-0004 evidence
- Create: Ornithopter Glider — retained later-game Elytra-gated mobility
- Hang Glider — rejected by owner and excluded
- No More Elytra Boosting
- Create: FlyHigher
- ThinAir: ReLived as a separate breathability overlay
- Create: Deep Seas only as R&D overlay, never a blocker

### Required questions
- one wind/pressure authority;
- glider consumes atmosphere rather than inventing a second one;
- Sable/Aeronautics lift/propulsion behavior remains coherent;
- no renderer owns semantic weather;
- ThinAir does not turn the ordinary inhabited Overworld into oxygen micromanagement.

### Exit
Close D-01/D-03 where evidence permits. D-02's selected-renderer/Distant Horizons visual gate is accepted; Simple Clouds semantic weather mapping remains technical work. D-03 closes after the integrated Reliable Gliders + Ornithopter owner play review.

# S2 — computing, control, and routine logistics

**Goal:** admit control infrastructure independently of combat and heavy industry.

### Selected baseline
- CC:Tweaked
- Create: Avionics

### Comparison / bounded candidates
- Create:Aero Automated Logistics — D-06
- Create Aeronautics Discovery where route/prefab realization is needed

### Authority rule
Skyforge/civilization owns mission intent. External logistics mods may execute routes; they do not become NPC/faction strategic brains.

### Exit
- programmable I/O remains green;
- route execution does not teleport or erase geography;
- save/reload and cargo ownership are stable;
- D-06 resolved.

# S3 — industry, power, propulsion

**Goal:** prove the shared industrial economy before adding broad ecology/world content.

### Baseline/leading components
- Create: Big Cannons
- Create Crafts & Additions
- Create Diesel Generators
- SJE / Aero Propulsion
- Create Propulsion: Simulated
- CBC-family retained extensions where pinned

### A/B
- Create: Metallurgy — D-05

### Required normalization
- one Steel/metal identity where semantics match;
- no Silver geology;
- no required Wolframite/Tungsten/Obdurium progression;
- Diesel native petroleum geography suppressed under Skyforge authority;
- no parallel material ladders.

### Exit
- #1422 closes through the actual canonical cumulative profile;
- D-05 resolved or explicitly remains optional;
- representative power/refining/CBC/propulsion smoke checks green.

# S4 — ordinary life and building

**Goal:** add the common domestic vocabulary before world ecology so recipe collisions can be seen clearly.

### Baseline
- Farmer's Delight
- Create: Central Kitchen
- Supplementaries

### Conditional
- Slice & Dice only for demonstrated processing gaps
- Artifacts
- Lootr only when multiplayer loot contention is tested

### Exit
Recipe graph remains coherent and no retained utility block silently becomes a competing authority for weather, transport, navigation, or worldgen.

# S5 — Overworld ecology and biome vocabulary

**Goal:** establish the content library while keeping Skyforge in charge of ecology and morphology.

### Ecology
- Naturalist
- Fowl Play
- Critters & Companions
- selected Alex's Mobs
- Sky Whales candidate

### Biome/material vocabulary
- Biomes O' Plenty
- Regions Unexplored
- Nature's Spirit
- Terralith only as the D-07-like morphology/reference instrument described by the canonical ledger, never macro authority

### Test method
Use fixed-seed comparison worlds and small representative Skyforge worlds. Measure:
- loader/runtime stability;
- duplicate species/biome concepts;
- spawn density;
- native worldgen leakage;
- performance;
- whether selected content can be disabled/redirected cleanly.

### Exit
No new broad Overworld content search. The installed vocabulary is sufficient.

# S6 — structures and civilization

**Goal:** prove structure libraries can coexist before Skyforge takes increasingly precise site-placement authority.

### Leading stack
- Towns & Towers
- YUNG's Better Dungeons
- YUNG's Better Mineshafts
- selected YUNG replacements
- Create: Structures Arise
- Create Aeronautics Structures
- Create Aeronautics Discovery
- Aeronautical Explorations
- Radio Towers Lite

### A/B
- Structory vs Explorify vs both — D-07

### Exit
- no structure spam;
- progression-critical structures remain available;
- structure identities survive save/reload;
- selected libraries can be governed by Skyforge admission/density;
- D-07 resolved.

# S7 — threats, weapons, and combat

**Goal:** add hostile content after ordinary ecology so density and niche overlap remain observable.

### Threat/ecology stack
- Friends & Foes
- It Takes a Pillage Continuation
- Illager Structures
- Mowzie's Mobs
- Ice & Fire CE
- selected Alex's Mobs threat species
- Bosses of Mass Destruction where current compatibility passes
- In Control!

### Engineering/combat stack
- Create: Radars / Create Aero Radars
- Create: Fire Control
- Mianbao NewModernWarfare
- CBC Neo Warfare
- CBC Terminal Ballistics

### Personal firearms A/B
- TaCZ family + Create/Aeronautics compatibility
- Scorched Guns NeoForge

Do not promote either firearm family before moving-Sable projectile behavior, NPC use, ammo economy, catalogue suppression, and performance are exercised.

### Exit
- threats pressure players and vehicles without saturating the world;
- radar/IFF/APS/ERA/countermeasure interoperability is characterized;
- aircraft combat does not require bespoke penalties before native mass/recoil/ammunition costs are tested;
- D-17/D-18 resolved or deliberately left as bounded playtest overlays.

# S8 — Nether

**Goal:** build the Nether as its own controlled profile before merging it into full-stack play.

### Step A — physical substrate A/B
Same seed:
- vanilla/control;
- Jaden's/Mosaic candidate;
- Incendium candidate.

Evaluate cavern/vault/chasm scale, connectivity, lava basins, navigability, and governability only.

### Step B — ecology/resources
Layer:
- BetterNether: New Dawn;
- Nether Depths Upgrade;
- My Nether's Delight;
- Create: Nether Industry;
- selected retained cross-dimensional fauna.

### Step C — structures/progression
Layer:
- Eternal Nether;
- YUNG's Better Nether Fortresses;
- Better Bastions A/B;
- Wither: Reincarnated A/B;
- conditional Piglin Proliferation;
- ThinAir only where hazardous atmosphere is semantically justified.

### Exit
Close D-08, D-09, D-10, D-11. Then freeze Nether acquisition.

# S9 — End

**Goal:** validate the preferred modular End stack under explicit morphology authority.

### Step A — morphology
- Nullscape controllability audit first.
- Stellarity remains a reserve/control branch, not additive baseline.

### Step B — ecology/resources
- BetterEnd: New Dawn
- selected BOP End content
- End's Phantasm
- Unusual End supplementary content
- End's Delight
- Farmer's Cutting: BetterEnd
- ThinAir / FlyHigher pressure behavior

### Step C — structures/progression
- MES / Moog's End Structures
- YUNG's Better Strongholds
- YUNG's Better End Island
- Dragon A/B slate

### Exit
Close D-12/D-13 and freeze End acquisition.

# S10 — onboarding, operations, and optional economy bridges

Run these after the common game substrate exists so their value can be judged in context.

- FTB Quests + Patchouli authored guidance layer; D-15 now concerns alpha content depth rather than dependency discovery
- Create: Enchantment Industry — D-14
- Simple Voice Chat + Walkie-Talkie Plus
- Simple Backups
- ServerCore conservative profile
- True Adaptive Music / Presence Footsteps / accepted acoustic presentation
- optional shader/Iris profile only after D-02
- multiplayer claim tools only if public/server scope requires them
- small field backpack only if D-19 demonstrates a real need
- optional broad performance overlays only through D-20

# S11 — full-stack convergence

**Goal:** one cumulative alpha profile assembled from promoted winners only.

Required:
- exact pinned manifest;
- machine-readable dependency/side classification;
- recipe/material/capability closure;
- no unauthorized worldgen/spawn authorities;
- dedicated-server boot;
- actual-client create/join/open/reopen;
- same-world save/reload;
- representative Overworld/Nether/End travel;
- representative aircraft/rail/industry/combat/ecology/structure smoke tests;
- bounded performance sample;
- multiplayer sanity;
- clear optional profile mechanism for unresolved purely experiential comparisons.

At S11, the question becomes player tuning rather than loader archaeology.

# Promotion and regression policy

When a slice passes:

1. Record exact pins and configuration.
2. Promote only the proven winner/capability to the cumulative spine.
3. Re-run the **smallest** cross-slice regressions that exercise its dependency surface.
4. Keep rejected alternatives out of baseline; preserve their evidence only if useful.
5. Do not repeat unrelated historical proofs.

When a slice fails:

```text
classify failure
 -> configuration/authority conflict? fix narrowly
 -> dependency/version incompatibility? reject or repin explicitly
 -> duplicated capability? choose one
 -> core Skyforge capability missing? record seam and defer
 -> gameplay/taste question? human A/B
```

Never add another compatibility mod merely to hide an unexplained failure.

# Repository execution model

The integration program should use:

- a committed profile manifest per accepted slice;
- Gradle/runtime source-set or equivalent side-aware staging;
- dedicated GitHub Actions gates for machine acceptance;
- local/manual client play only for the human judgments that genuinely require it;
- the canonical mod ledger as the status surface;
- this document as the sequence/acceptance authority.

The DigitalOcean control plane is not a project build/test environment.

# Immediate action

The next executable integration action is **not** another discovery pass.

1. Treat the accepted WBY-INT-0001..0005 work as reusable evidence.
2. Reframe #1433 / the active alpha convergence work around this staged profile model.
3. Establish/verify S0 as the stable canonical launch shell.
4. Run the next unresolved slice against S0 rather than assembling the entire remaining roster simultaneously.
5. Keep #1422 open until Diesel Generators passes inside the actual promoted cumulative profile.
