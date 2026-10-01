# WBY Integration Wave 0 — Accepted Flight Substrate v0.1

**Snapshot:** 2026-09-30  
**Status:** Ready for implementation qualification  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21

## Purpose

Wave 0 establishes the smallest accepted Wild Blue Yonder runtime substrate before any broad content, renderer, or playtest candidate is admitted.

This wave does **not** search for newer versions. It reuses the immutable artifacts already pinned and exercised by the existing Skyforge Wave C1 work.

## Required runtime

- Skyforge current main
- Minecraft 1.21.1
- NeoForge 21.1.249
- Create 6.0.10+mc1.21.1
- Sable 2.0.5+mc1.21.1
- Create Aeronautics bundled 1.3.2+mc1.21.1
- JEI 19.50.0.414 for recipe/inspection acceptance

Use the exact coordinates already recorded in `skyforge-neoforge-1211/wave-c1-mods.properties`.

### Nested dependency rule

Do not add loose duplicates for:

- Ponder / Flywheel / Registrate bundled by Create;
- Sable Companion / Veil / Rapier bundled by Sable;
- Simulated / Aeronautics / Offroad modules bundled by Create Aeronautics.

The existing `docs/design-audit/wave-c1-loader-dependency-closure-v0.1.md` remains authoritative.

## Acceptance cases

Wave 0 passes only if all of the following are demonstrated on the exact stack:

1. Client boot reaches title screen without loader/mixin/linkage failure.
2. Dedicated server boot reaches ready state.
3. Client can join and leave the dedicated server.
4. A fresh Skyforge world can be created.
5. The same world can be saved and reopened.
6. A minimal Sable/Aeronautics craft can be assembled and controlled.
7. Create kinetics operate on ordinary terrain.
8. Create kinetics remain valid on a Sable sublevel.
9. A seated passenger survives assembly, movement, save/reload, and disassembly.
10. No duplicate mod ID or nested-library conflict is present.
11. Skyforge deterministic/world acceptance tests remain green.

## Evidence

Record:

- exact commit SHA;
- exact artifact coordinates;
- client latest.log;
- server latest.log;
- world seed;
- save/reload result;
- minimal aircraft specimen description;
- any warnings that remain after successful boot.

## Failure policy

A Wave 0 failure blocks Wave 1. Do not add compatibility mods to hide a Wave 0 failure without first classifying the failure against the existing accepted C1 substrate.

## Closure

> Wave 0 proves that the physical engineering/flight substrate is still healthy on current main. It is not a gameplay-content test.
