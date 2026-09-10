# CIV-0007: Dialogue and service conversation

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Purpose

Skyforge needs dialogue for Guild representatives and occasional future narrative or systemic NPC interactions, but dialogue is not intended to become a major gameplay pillar. This record preserves a deliberately lightweight, state-driven approach.

## 1. Core rule

> Dialogue is a thin interaction layer over authoritative world, Guild, settlement, contract, and player state. It should not become a separate RPG simulation.

Routine conversations should be fast, functional, and context-sensitive. Important authored moments may temporarily become deeper conversations, but this is the exception rather than the default.

## 2. Interaction depth

Use three broad dialogue depths:

1. **Routine service interaction** — short contextual greeting plus direct access to relevant services.
2. **Contextual/world reaction** — one to three short exchanges that reflect local state, recent events, route hazards, shortages, or player history.
3. **Important authored event** — a compact branching conversation used for major decisions, disputes, discoveries, or state changes.

The Bellanca black-box claim is a canonical example of the third category.

## 3. Service conversation pattern

A normal Guild representative interaction should usually expose a small number of relevant actions, for example:

- Contracts
- Registry
- Account
- Trade
- Local information / unusual events

Selecting a service should normally open the corresponding gameplay interface directly rather than forcing the player through multiple additional dialogue nodes.

Dialogue may be mechanically distinct from the service UI without being spatially or interactionally tedious.

## 4. Authoritative state ownership

Dialogue must not own canonical state. It reads and acts on state owned elsewhere, such as:

- claim state;
- registered vessels;
- Guild Standing;
- Guild Scrip/account state;
- contracts;
- settlement state;
- known routes;
- event flags;
- Hall capabilities;
- inventory/evidence conditions where required.

A dialogue node may test conditions and invoke state transitions, but the underlying systems remain authoritative.

## 5. Data-driven authoring

Dialogue should be representable through compact data definitions that support:

- speaker or NPC role;
- conditions;
- text;
- available choices;
- target node;
- service-opening action;
- state-change action;
- quest-book linkage;
- optional contextual variants.

The implementation should avoid requiring custom code for every individual conversation.

## 6. NPC roles rather than bespoke subsystems

Guild NPCs may expose role identities such as:

- `GUILD_GENERALIST`
- `GUILD_FACTOR`
- `CLAIMS_OFFICER`
- `REGISTRAR`
- `QUARTERMASTER`
- `NAVIGATOR`
- `MECHANIC`

These roles should map onto Hall capability/service data rather than separate gameplay systems.

A frontier Hall may have one generalist NPC providing nearly every service. A major Hall may present several specialists, all backed by the same service registry.

## 7. FTB Quests relationship

FTB Quests should carry instructional and reference-heavy material where practical.

Dialogue should sound like a person speaking in-world; the quest book should explain precise tasks, requirements, controls, and recovery steps.

Canonical separation:

> NPC dialogue provides context, character, and decisions. FTB Quests provides explicit player guidance.

A dialogue action may direct or open the relevant quest entry when the player needs instructions.

## 8. Tone and density

Routine Guild dialogue should be concise and professional. It may include local observations, recent hazards, shortages, aircraft commentary, or other brief state-aware lines, but should not require exhaustive questioning.

Avoid by default:

- giant dialogue trees;
- dialogue skill checks as a core progression system;
- relationship meters for every NPC;
- mandatory pleasantry loops;
- long exposition dumps;
- one NPC per individual service;
- AI-generated freeform dialogue as an authoritative gameplay mechanic.

Strong authored lines are preferable to large volumes of generic text.

## 9. Presentation abstraction

The Skyforge dialogue/state schema should remain independent of any particular NPC presentation mod.

Preferred dependency direction:

**Skyforge authoritative state**
→ **dialogue/service definitions**
→ **presentation adapter**
→ **NPC/UI implementation**

This permits prototyping with an external NPC/dialogue mod while retaining the ability to replace that presentation layer later without rewriting claims, contracts, Guild state, or narrative logic.

Current prototype candidate: Easy NPC on NeoForge 1.21.1, subject to in-game compatibility and authoring tests. Exact mod adoption is deferred.

## 10. Bellanca opening application

The first Guild claim should demonstrate the full pattern:

- routine initial interaction;
- initial unfavorable claim determination;
- player submits flight recorder/black box evidence;
- conversation deepens briefly;
- Guild liability is established;
- restitution options are presented;
- authoritative claim/account/vessel state changes;
- FTB Quests carries any explicit recovery instructions.

The Guild's tone should remain procedural and professional. The significance comes from evidence changing the institutional determination, not from a dramatic personality reversal.

## 11. Acceptance criteria

The dialogue layer succeeds if:

1. routine service access takes only a few clicks;
2. dialogue reflects authoritative world state rather than duplicating it;
3. major authored moments can branch without requiring a full RPG dialogue engine;
4. frontier and major Guild Halls can share the same underlying service/dialogue architecture;
5. FTB Quests carries instructional burden rather than forcing exposition into conversation;
6. NPC presentation technology can be replaced without invalidating Skyforge narrative/system state;
7. the system remains useful for future non-Guild NPC interactions not yet conceived.

## Precommitted rule

> **Use sparse, contextual, state-driven service dialogue. Routine conversations open systems quickly; deeper branching is reserved for moments where evidence, decisions, or world state materially change.**

Exact UI skin, dialogue mod, portrait treatment, speaker animation, node syntax, and localization pipeline remain implementation-stage decisions.