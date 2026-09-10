# CIV-0041 — Bellanca Onboarding and Bootstrap Civilization Integration

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Core decision

> Skyforge begins at the crashed Bellanca. The crash, wreck, and unresolved insurance/registration question provide the narrative through-line for the complete introductory sequence.

FTB Quests may guide the player through foundational survival, engineering, traversal, and aviation systems while the player works toward reaching civilization, but FTB Quests are presentation/guidance rather than the authoritative state machine for Bellanca ownership, evidence, claims, Guild accounts, or economic state.

The tutorial terminates when the player reaches an eligible civilized/Guild location and resolves the Bellanca transaction.

## Canonical onboarding arc

Preferred sequence:

```text
CRASHED BELLANCA
        ↓
survive / orient
        ↓
FTB QUESTS introduce foundational Skyforge systems
        ↓
early Create / engineering
        ↓
gliding / vertical traversal / atmosphere
        ↓
regain practical regional mobility
        ↓
reach civilization
        ↓
encounter Guild Hall / eligible Guild interface
        ↓
initiate Bellanca claim
        ↓
present recorder / black-box evidence where applicable
        ↓
liability reversal
        ↓
choose restitution
        ↓
TUTORIAL COMPLETE
```

The first civilized destination is therefore not merely a location for an economy tutorial. Reaching civilization is itself the primary onboarding objective after the crash.

## Relationship to the existing Bootstrap Province sequence

The accepted Bootstrap Province progression remains authoritative where it proves foundational gameplay:

```text
ordinary survival foothold
→ basic Create workshop
→ cheap glider
→ vertical traversal / trusted thermals / soaring-fauna legibility
→ gather first-flight materials
→ build or improvise first practical powered aircraft
→ cross a distance where powered flight materially matters
→ reach specialized resource/site and/or civilization
→ begin regional freight/logistics
→ encounter mature skyborne infrastructure
```

The Bellanca opening does not invalidate this sequence. It frames it.

The preferred relationship is:

```text
CRUDE / IMPROVISED FIRST AIRCRAFT
= proof that the player learned to engineer practical powered flight

STANDARDIZED BELLANCA OR EQUIVALENT RESTITUTION
= end-of-tutorial institutional reward / capital that opens the wider aviation sandbox
```

The Bellanca transaction must not silently bypass the intended early engineering progression.

## Crash-site role

The Bellanca wreck should immediately communicate that aircraft, loss, registration, repair, salvage, and risk matter before the player understands the Guild in detail.

The wreck may expose or foreshadow:

- recognizable aircraft structure;
- damaged machinery;
- registration / transponder identity;
- recorder / black-box evidence;
- salvageable but bounded components;
- cargo or personal remnants where appropriate;
- a clear unresolved institutional question: what happened to this aircraft and what can the player do about it?

The wreck remains a meaningful world object until repaired, surrendered, salvaged, abandoned, or otherwise resolved by the authoritative Bellanca state.

## FTB Quests boundary

FTB Quests are the onboarding presentation layer.

They may:

- sequence or recommend survival goals;
- teach Create basics;
- teach gliding and vertical traversal;
- point toward first powered-flight capability;
- surface Bellanca/recorder objectives;
- direct the player toward civilization or Guild services;
- explain newly unlocked institutional concepts.

They must not become the source of truth for:

- aircraft title/registration;
- recorder evidence;
- Guild liability;
- claim state;
- restitution state;
- account balance;
- settlement stock;
- cargo custody;
- contract settlement.

Authoritative gameplay systems must own those facts so the onboarding path exercises the same systems used later in systemic play.

## Recorder / black-box timing

The recorder should be difficult to permanently miss, but the player need not understand its institutional significance when first encountered.

Supported flow:

```text
crash site
→ player may notice/recover recorder
→ player continues onboarding
→ later Guild claim initially resolves unfavorably
→ recorder becomes relevant evidence
```

If already recovered, the player may present it immediately when the claim reaches that stage.

If not, the claim/quest layer may point the player back toward recovery.

The evidence should establish that the Bellanca followed prescribed guidance and that Guild-operated navigation/approach infrastructure produced the unsafe condition, converting the dispute into Guild liability/restitution rather than arbitrary generosity.

## First civilization / Guild destination

The Bootstrap Province must guarantee at least one reachable eligible civilization/Guild destination that can terminate the tutorial.

However, the Bellanca claim should conceptually belong to the Guild network rather than to one uniquely scripted NPC at one mandatory coordinate.

Where systemic routing and content coverage permit:

```text
Bellanca claim may be initiated at:
    Bootstrap-guaranteed first Guild Hall
    OR
    another eligible Guild Hall reached first by an unusual player route
```

The player should not be forced to ignore a legitimate Guild settlement merely because it is not the authored tutorial waypoint.

The exact minimum capability required for an alternate Hall to resolve the onboarding claim remains an implementation/content question.

## Bellanca transaction as tutorial terminus

The Bellanca resolution should introduce several mature concepts together:

- registered vessel identity;
- Guild account relationship;
- insurance / claim handling;
- institutional evidence;
- Guild liability;
- Scrip;
- restitution choice;
- standardized aircraft / recovery of productive capital.

Preferred claim arc:

```text
initial claim
→ evidence currently favors pilot responsibility
→ recorder evidence supplied
→ Guild infrastructure fault established
→ Guild accepts liability / restitution obligation
→ player chooses resolution
```

Current preferred restitution choices remain:

```text
A. standardized Bellanca replacement
B. Scrip payout
C. retain wreck + partial payout
```

The exact values and aircraft asset maturity are implementation/content tuning questions.

## Tutorial-complete state

The tutorial ends when Bellanca restitution is resolved, not when the player merely sees civilization.

At tutorial completion the player should normally possess or understand:

- practical survival competence;
- basic Create/engineering competence;
- glider and vertical-mobility fundamentals;
- first practical powered-flight experience;
- a Guild account relationship;
- Scrip as serious commercial money;
- basic vessel registration/insurance concepts;
- access to viable aviation capability or equivalent capital through the chosen restitution;
- access to civilization/Guild services;
- freedom to choose the next systemic profession or objective.

Canonical transition:

```text
RESTITUTION RESOLVED
        ↓
TUTORIAL COMPLETE
        ↓
PLAYER IS AN INDEPENDENT SKYFARER
```

## Freight as first post-tutorial professional opportunity

The Bellanca claim should not require a freight contract merely to conclude the tutorial.

Instead, after restitution the nearby region should make one or more professional opportunities immediately legible, such as:

- routine freight;
- commodity trading;
- specialized-resource exploration;
- repair or recovery work;
- aircraft-related work;
- survey/navigation work.

For the Bootstrap/public-alpha vertical slice, at least one meaningful freight/logistics loop remains a required executable proof.

A preferred first freight loop is deliberately forgiving:

```text
ROUTINE
ordinary cargo
short recognized route
low hazard
minimal/no punitive deadline
basic manifest/cargo custody
physical loading and transport
verified delivery
Scrip settlement exactly once
```

This should use real settlement demand, commercial custody, delivery reconciliation, and economic state rather than disposable quest-only bookkeeping.

## Minimum civilization proof in Bootstrap

The first executable civilization slice should remain intentionally narrow even though the broader CIV precommit is extensive.

One coherent regional chain is sufficient to prove the architecture, for example:

```text
SPECIALIZED PRODUCER / RESOURCE SITE
    ↓
WAREHOUSE / CARGO INTERFACE
    ↓
AIRFIELD / DOCK / ROUTE
    ↓
CIVILIZED SETTLEMENT
    ↓
GUILD HALL / MARKET / CONSUMPTION
```

Minimum proof should demonstrate some subset sufficient to establish:

- specialization driven by geography/resources;
- cargo staging and transfer;
- aircraft handling;
- route/navigation infrastructure;
- a basic Guild interface;
- a real market/economic need;
- one physical freight transaction;
- authoritative stock/payment settlement;
- sparse but convincing mature skyborne infrastructure.

The first slice does not need the complete economy/Guild feature set.

## Guild Hall MVP for Bootstrap

The first Hall need only expose the minimum institutional surface necessary for the onboarding and first systemic play:

```text
ACCOUNT
BELLANCA CLAIM / RESTITUTION
BASIC MARKET
BASIC CONTRACT ACCESS
BASIC ROUTE / SETTLEMENT INFORMATION
```

Vessel recognition/registration may be included where technically natural.

Advanced credit, broad insurance products, autonomous logistics, deep arbitration, player agencies, complex jurisdiction disputes, and other precommitted systems may layer onto the same architecture later.

## Sparse-world requirement

Civilization must not destroy the project's negative-space and first-flight goals.

The first distant civilized encounter should feel like the player has actually reached inhabited society after traversing a sparse sky-island world.

Bootstrap should therefore favor one legible low-density functional network over dense town spam or constant routine traffic.

## Design invariants

> The Bellanca crash frames the tutorial; the Bellanca Guild transaction ends it.

> FTB Quests guide onboarding but do not own authoritative institutional/economic state.

> Reaching civilization is a meaningful early-game objective, not merely a menu unlock.

> Bellanca restitution must not erase the need for the player to learn practical early engineering and powered mobility.

> The player should enter the systemic sandbox after restitution as an independent Skyfarer, with Guild access and meaningful professional choices rather than another mandatory tutorial chore.

> Bootstrap proves the architecture with a small real economic network; it does not need to ship the entire civilization simulation at once.
