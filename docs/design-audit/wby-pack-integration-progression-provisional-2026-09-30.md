# Skyforge — Pack Integration / Progression Control — Provisional Freeze

**Status:** Provisionally closed for current mod-stack planning  
**Target:** Minecraft 1.21.1 / NeoForge  
**Date:** 2026-09-30

## Purpose

This stack exists to make the selected mods behave as one coherent game.

It is not the Skyforge simulation engine. Skyforge-owned systems such as world semantics, geology, ecology, civilization orchestration, aircraft AI, and other core simulation logic remain real Skyforge code.

The pack-integration layer owns Minecraft-facing recipe, loot, tag, compatibility, and content-curation concerns.

## Core stack

### KubeJS — strong keep
Primary pack scripting authority.

Use for:
- recipe removal and replacement;
- tag normalization;
- progression integration;
- compatibility scripting;
- small Minecraft-facing rules;
- disabling unintended bypasses;
- controlled event logic that does not belong in the Skyforge engine.

Architectural rule:
> KubeJS integrates the modpack; it does not become the Skyforge simulation engine.

### KubeJS Create — strong keep
Use for Create-native recipe integration, including:
- mixing;
- compacting;
- pressing;
- cutting;
- sequenced assembly;
- other Create processing chains.

Where practical, important industrial components should be integrated into Create’s production language rather than defaulting to arbitrary crafting-table recipes.

### LootJS — strong keep
Primary loot-control layer.

Use for:
- curated Artifact distribution;
- ordinary vs strategic/shared loot;
- civilization loot;
- firearm/ammunition loot;
- boss and creature rewards;
- aircraft salvage;
- rare discoveries;
- removal of unwanted default drops;
- Lootr compatibility/exclusion policy.

### Paxi — strong keep
Use for globally and consistently distributing Skyforge datapacks/resource packs and controlling pack load order.

Likely responsibilities:
- Skyforge core datapacks;
- recipe overrides;
- loot definitions;
- tags;
- compatibility datapacks;
- resource packs;
- music/assets where appropriate.

## Configured / conditional tools

### Almost Unified — configured keep candidate
Use only to normalize externally duplicated implementations of resources that Skyforge considers semantically identical.

Appropriate examples:
- duplicate ingots/dusts from different mods;
- redundant brass-type outputs where no semantic distinction is intended;
- other ordinary cross-mod material duplication.

Hard rule:
> Almost Unified may normalize implementation duplicates; it may not define Skyforge’s material ontology.

Skyforge-authored geological/material distinctions always take precedence.

### Item Obliterator — conditional / reserve
Use when a hard content-removal mechanism is useful, especially if the winning firearm stack carries significant unwanted content.

Potential uses:
- unwanted weapons;
- ExoSuit or flight items;
- redundant equipment;
- unwanted trades;
- unwanted recipes;
- unwanted creative/JEI exposure;
- other content that should not exist in Skyforge.

Do not require it unless KubeJS/datapack-level curation proves insufficient or less reliable.

## Explicit omissions

### CraftTweaker — omit
Do not run KubeJS and CraftTweaker as parallel pack-script authorities.

KubeJS is the single primary scripting authority.

### GameStages / broad hard recipe staging — omit baseline
Avoid arbitrary quest- or stage-based craft permissions.

Progression should normally arise from:
- resource availability;
- geography;
- engineering complexity;
- discovery/knowledge;
- production infrastructure;
- economy;
- prerequisite machinery;
- danger;
- actual player capability.

Hard gating should be introduced only to solve a demonstrated sequence-breaking exploit or progression failure.

## Integration work order

### 1. Canonical resource model
Define:
- canonical materials;
- canonical tags;
- preferred outputs;
- which mods may generate which resources;
- which resources are owned by Skyforge.

### 2. Recipe normalization
Audit all major progression systems:
- Create;
- aircraft;
- firearms;
- gliders;
- artifacts;
- radios/radar;
- recovery tools;
- other high-impact equipment.

Remove:
- duplicate recipes;
- trivial bypasses;
- recipes inconsistent with intended progression;
- recipes inconsistent with Skyforge’s industrial language.

Add Create-native processing where useful.

### 3. Loot normalization
Classify and author:
- ordinary loot;
- exploration loot;
- strategic/shared loot;
- boss loot;
- civilization loot;
- aircraft salvage;
- rare discoveries.

### 4. Content suppression
Disable unwanted:
- items;
- recipes;
- trades;
- structures;
- mobs;
- worldgen;
- progression systems.

This is especially important for the eventual winning firearm stack.

### 5. Pack delivery
Use Paxi to distribute authored datapacks/resource packs and control load order.

### 6. Validation
Build automated or scripted checks where practical for:
- unintended recipe bypasses;
- unobtainable intended items;
- disabled items leaking into loot/trades;
- canonical tag resolution;
- strategic loot accidentally becoming per-player Lootr loot;
- duplicate progression-critical materials;
- unwanted worldgen leakage;
- other integration regressions.

## Existing policy interactions

This stack is expected to enforce already-frozen decisions, including:
- rocket-propelled Elytra remains disabled;
- firearm content is curated;
- Artifacts remain curated;
- Lootr excludes strategic/shared assets;
- large personal storage remains constrained;
- navigation should not gain omniscient locators;
- unintended mod-default progression should not override Skyforge progression.

## Reopen conditions

Reopen only if:
1. KubeJS or related addons prove technically unsuitable on the final target stack;
2. another integration framework provides materially better reliability or maintainability;
3. playtesting reveals that explicit progression staging is necessary for a concrete exploit;
4. Skyforge’s own backend eventually absorbs functions currently handled by the pack-integration layer.