# CIV-0002: Guild architecture and donor strategy

**Status:** Precommitted authorship strategy
**Stage:** Pre-civilization; asset implementation deferred
**Date:** 2026-09-10

## Purpose

This document preserves the current production strategy for Guild and civilization architecture. The goal is to avoid a future civilization stage that assumes hundreds of bespoke structures while still producing settlements that feel coherent, authored, and institutionally legible.

## 1. Production invariant

> No new systemic feature may assume a unique physical asset for every instance.

Civilization systems must be designed around reusable architectural grammars, modular assets, parameterized variants, and authored exceptions.

Use an **ordinary / memorable / legendary** hierarchy:

- **ordinary:** generated or assembled from reusable kits;
- **memorable:** kit-based structure plus authored local features;
- **legendary:** bespoke authored landmark.

## 2. Guild architecture is a design system, not a building list

The Guild should not be represented by one repeated hall schematic. It should be recognizable through a shared architectural language that can appear across warehouses, banks, terminals, offices, repair shops, towers, exchanges, and hangars.

That language should operate at multiple levels:

1. material roles;
2. structural proportions;
3. repeated motifs;
4. massing families;
5. functional modules;
6. regional material substitution;
7. institutional signage and heraldry;
8. Create / Aeronautics functionality.

## 3. Material-role approach

Canonical architecture should prefer semantic material roles over one frozen block list.

Candidate roles:

- `FOUNDATION_STONE`
- `WALL_MASONRY`
- `STRUCTURAL_TIMBER`
- `WALL_INFILL`
- `ROOF_PRIMARY`
- `ROOF_TRIM`
- `METAL_STRUCTURAL`
- `METAL_WEATHERED`
- `WINDOW_FRAME`
- `GUILD_ACCENT`
- `FLOOR_PUBLIC`
- `FLOOR_INDUSTRIAL`
- `SIGNAGE_SUPPORT`

A temperate Guild chapter and a dry highland chapter can therefore share the same design grammar while resolving material roles differently.

## 4. Proportion and motif standards

The civilization stage should define a compact builder-facing standard covering at least:

- bay width families;
- floor-height families;
- window aspect ratios;
- freight-door classes;
- roof pitch ranges;
- eave / overhang ranges;
- structural post spacing;
- service-clearance envelopes;
- sign and crest mounting points;
- standard lamp / bracket families;
- industrial vent and chimney families.

Experienced Minecraft-builder practice supports the same general direction: coherence is usually achieved through repeated palette, proportion, roof, and motif logic rather than literal duplication of whole buildings.

## 5. Reusable module family

Initial candidate module vocabulary:

- `GUILD_ARCH_ENTRY_A`
- `GUILD_ARCH_WINDOW_A`
- `GUILD_ARCH_WINDOW_B`
- `GUILD_ARCH_WAREHOUSE_WINDOW`
- `GUILD_ARCH_ROOF_CORNER_A`
- `GUILD_ARCH_DORMER_A`
- `GUILD_COUNTER_SMALL`
- `GUILD_CONTRACT_BOARD`
- `GUILD_BANK_COUNTER`
- `GUILD_WAREHOUSE_BAY`
- `GUILD_LOADING_CANOPY`
- `GUILD_LOADING_DOOR_SMALL`
- `GUILD_LOADING_DOOR_MEDIUM`
- `GUILD_HANGAR_DOOR_MEDIUM`
- `GUILD_CRANE_SMALL`
- `GUILD_AIR_DOCK_LIGHT`
- `GUILD_AIR_DOCK_MEDIUM`
- `GUILD_RADIO_MAST`
- `GUILD_SIGNAL_TOWER`

Modules should carry metadata for:

- material roles;
- allowed rotations;
- allowed mirroring;
- attachment points;
- minimum clearances;
- optional sockets;
- functional interface requirements.

## 6. District composition hierarchy

Civilization architecture should not make every building Guild-owned.

Preferred hierarchy:

**Local vernacular**
→ houses, inns, farms, ordinary workshops

**Local industry**
→ mills, smithies, foundries, warehouses, agricultural processing

**Commercial infrastructure**
→ markets, exchanges, freight depots, stores, docks

**Guild infrastructure**
→ halls, registry, bank, certified stores, skyports, bonded freight

**Specialized Guild infrastructure**
→ recovery yards, navigation stations, underwriting offices, academies, major exchanges

This preserves local identity while allowing the Guild to impose recognizable standards at interfaces it controls.

## 7. Morphological donor strategy

Donor discovery should search by spatial morphology, not only by literal Guild keywords.

High-value donor families discovered so far include:

- wharf warehouses;
- industrial warehouse yards;
- Victorian factories;
- railway terminals;
- market / exchange halls;
- postal / dispatch buildings;
- civic halls;
- manor houses;
- universities and institutional campuses;
- stables and carriage houses;
- chandleries and supply stores;
- silos / granaries;
- foundries;
- clock / signal towers;
- harbor cranes;
- airfield campuses;
- compact and medium Aeronautics craft.

Functional remapping is explicitly encouraged. Examples:

- train station → skyport;
- stable → aircraft service bay;
- chandlery → aircraft-supply store;
- postal sorting building → dispatch / manifest office;
- harbor crane → functional Create freight crane;
- manor → regional chapter headquarters;
- observatory → navigation / meteorology station.

## 8. Donor integration pipeline

Preferred process:

**DONOR BUILD**
→ structural and composition audit
→ extract worthwhile geometry
→ classify geometry by semantic material role
→ apply Guild architectural grammar
→ apply canonical / regional palette
→ add Create, Supplementaries, Copycats+, or Aeronautics functionality
→ add service-specific interior
→ add standardized cargo / aircraft interfaces
→ add Guild signage / heraldry
→ weathering and regional adaptation
→ gameplay validation
→ rights / attribution verification
→ **GUILD-CERTIFIED ASSET**

The donor should save labor without becoming stylistic authority.

## 9. Donor acceptance classes

Use three broad authorship classes:

### A — Modification candidate

A sufficiently sophisticated build whose shell, composition, or machinery can materially survive into a canonical derivative.

### B — Module donor

The whole structure is not strong enough, but specific bays, windows, roofs, doors, machinery, yards, or layouts are useful.

### C — Reference only

Useful for composition, silhouette, systems thinking, or sophistication targets, but expected to be rebuilt rather than derived.

Rights status is separate from quality:

- **GREEN:** explicit license / permission supports intended derivative use;
- **YELLOW:** downloadable or modification-friendly, but redistribution / commercial derivative status requires verification;
- **RED:** restrictive terms or provenance issues; reference only until explicit permission exists.

A high-quality RED reference remains valuable; a low-quality GREEN donor does not become canonical merely because it is easy to license.

## 10. Highest-value known donor candidates

The following should be preserved for future civilization authorship review:

### Freight and industrial

- **Warehouse Row** — highest-value current warehouse kit donor; seven warehouse forms, crane, dock office, loading grammar, interior storage vocabulary. Strong modification candidate; non-commercial restriction requires careful rights handling.
- **Harbour Storehouse** — strong standalone freight donor; especially valuable because prior audit identified explicit CC BY 4.0 reuse.
- **2025 Warehouses / PanieStasiu** — higher-detail port-warehouse comparison donor.
- **Koin Victorian Factory** — strong Create-oriented industrial shell and machinery-volume donor.
- **Koin Factory Yard** — especially valuable for site composition: building + service yard + machinery/power space.
- **Warehouse Area / MineServContent** — bulk shipment and operational-yard reference.
- **Industrial District** — high-sophistication district composition benchmark; restrictive rights mean reference unless permission is obtained.
- **Steampunk Factory / CeterumCenseo** — selective industrial ornament and massing reference; avoid generic steampunk excess.
- **Grain Storage Silos** — useful settlement-industry donor that makes agricultural demand physically legible.

### Commercial and institutional

- **The Market: Server Shop** — major exchange / contract-hall donor; sophisticated trading-floor, storefront, upper-office, and monumental-commercial program.
- **Koin Create Train Station** — one of the strongest skyport conversion shells; transport/public circulation maps directly to Guild terminal use.
- **Medieval Manor (CC BY 4.0)** — strong legally clean regional chapter HQ / underwriting-office donor.
- **Town Hall 1.20.4** — formal civic shell for registry / arbitration / administration; creator permits free use with credit.
- **CH Postal Company** — exceptional dispatch / clerical / sorting-office program reference; franchise/IP provenance makes it reference-first.
- **Victorian Town Building / Frit** — strong repeatable urban façade donor for brokers, insurers, surveyors, merchants, and other Guild-adjacent businesses.
- **Victorian Town Hall / axianerve** — institutional shell reference with empty interior useful for authored Guild programming.
- **Abbington College** — institutional-campus reference for academy, navigation college, or central records.
- **Small Medieval Guild Hall** — strong local/frontier hall program, but requires major visual sophistication pass.
- **Merchant / Guild House** — useful compact urban Guild branch / broker / bank donor.

### Service and specialty

- **Free Medieval House + Stable** — excellent frontier office + attached aircraft-service-bay morphology.
- **Realistic Medieval Stables / ReaperWillRise** — strong maintenance-bay conversion donor.
- **Medieval Blacksmith's Forge & House** — small repair shop / component dealer / certified mechanic donor.
- **Dockside Chandlery** — exact source geometry may be secondary, but the aircraft-chandlery typology is highly valuable.
- **Observatory** — navigation, survey, charting, and meteorology station donor family.
- **Victorian Clock Tower** — signal / clock / weather / transponder landmark reference.
- **Harbor Crane / Cargo Dock Crane** — proportion donors to rebuild as functional Create machinery.

### Aviation and docking

- **Survival-Friendly Airship / Dustbriks** — strongest first complete small-airship modification candidate; compact and functional.
- **Compact Airship / Spiritsuwu** — compact mechanical-chassis donor.
- **Small Survival Airship / Zurriqcos** — frontier / rescue craft systems donor.
- **Cargo Haul Blimp** — important freight-interface donor; directly informed provisional GFU-3 thinking.
- **Boreas Mk2.2** — engineering benchmark for control sophistication, cargo systems, labeling, auto-level behavior, and maintenance access; not recommended as first derivative donor.
- **Small Airship / Portative Engine** — current 1.21.x systems reference; hull provenance must be resolved before derivative use.
- **Aeronautics Vehicle Hangar** — mechanical/workshop donor.
- **Airship Auto Dock** — compact berth-mechanism donor.
- **Airship Drydock / Airship Station / airfield complexes** — composition references for larger aviation quarters.

This list is intentionally inclusive. Future authorship passes may reject individual assets after direct in-game visual inspection.

## 11. First Guild branch composition

A useful first branch prototype should test whether unrelated donors can be transformed into one coherent institutional language.

Candidate composition:

**signal / transponder mast**
→ **Guild Hall** — contracts, registry, account services
→ **Guild Store / trading counter**
→ **freight yard**
→ **warehouse + crane + loading apron**
→ **converted stable / workshop maintenance bays**
→ **light air dock**
→ **standardized light utility aircraft**

The key empirical question is:

> Can existing donor assets be standardized strongly enough that the player perceives one civilization rather than a collection of downloaded schematics?

## 12. Builder-tool workflow

Likely authorship tooling during the civilization stage:

- **Axiom** — high-speed visual / structural editing and palette standardization;
- **WorldEdit / FAWE** — selection, masks, patterns, systematic replacement, schematic operations;
- **Litematica** — placement, comparison, verification, layer inspection, controlled rebuilds;
- **Create schematic tools** — functional import / construction validation where appropriate;
- **PureRef / Eagle / equivalent** — visual reference review, if useful to authors;
- **repo-native metadata** — canonical provenance, license, review state, and derivative lineage.

The preferred long-term donor record is version-controlled, machine-readable metadata adjacent to accepted assets; external visual tools are convenience layers, not authority.

## 13. Future automation opportunity

A Skyforge-specific schematic standardizer may eventually map donor blocks to semantic material roles and then resolve those roles into a Guild or regional palette.

Example conceptual transformation:

`oak_planks` → `WALL_INFILL`

`spruce_logs` → `STRUCTURAL_TIMBER`

`stone_bricks` → `FOUNDATION_STONE`

followed by:

`STRUCTURAL_TIMBER` → region-specific timber family

`FOUNDATION_STONE` → region-specific masonry family

This should never be treated as a substitute for an artistic cleanup pass. It is a labor-saving normalization stage.

## 14. Deferred implementation decisions

The civilization stage should decide, after prototype imports:

- final canonical Guild palette roles;
- exact module dimensions;
- exact GFU dimensions;
- exact skyport clearance classes;
- which donors are actually imported;
- which imported donors are legally shippable;
- whether an automated schematic standardizer is worth implementation cost;
- whether Guild architecture needs procedural kit assembly or curated variants are sufficient;
- how strongly regional vernacular overrides Guild standardization.

These decisions should be made from actual in-game comparisons rather than screenshots alone.