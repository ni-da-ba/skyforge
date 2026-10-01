# CIV-0039 — Faction, Sovereignty, Economic Access, and Allegiance

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Purpose

This precommit defines the minimum institutional/faction vocabulary needed for civilization, Guild commerce, vessels, structures, routes, and hostile locales to agree on ownership/allegiance and access without introducing a grand-strategy diplomacy simulation.

It extends the existing Skyforge civilization direction in which the Guild is a powerful inter-settlement institution but not a world government, while territorial/faction control remains a separate coarse world state.

## Core separations

The following concepts are distinct and must not be collapsed into one reputation or faction field:

```text
TERRITORIAL / FACTION CONTROL
        !=
LOCAL POLITY / SOVEREIGN AUTHORITY
        !=
GUILD RELATIONSHIP
        !=
ASSET ALLEGIANCE
        !=
ACTOR ACCESS / PERMISSION
```

These states may correlate but none universally implies another.

A settlement may be controlled by a civilian polity, be only an associate of the Guild, permit ordinary private trade, and still contain Guild-owned facilities or vessels. An illager-controlled region may contain internally coherent logistics and civilian or military assets while being hostile to Guild traffic.

## Minimal allegiance primitive

Skyforge needs a shared allegiance/affiliation primitive for persistent or gameplay-relevant assets such as:

- vessels;
- registered structures and civic capability anchors;
- Guild halls and service facilities;
- military/faction infrastructure;
- patrols and realized faction aircraft;
- commercial facilities;
- selected cargo or contract-bound assets where provenance matters.

The primitive should answer a small set of questions:

```text
Who owns or institutionally controls this asset?
What broad alignment does it present to world systems?
Whose jurisdiction/permissions should be consulted?
Which hostility/access rules apply when it is realized?
```

It should not attempt to encode a complete diplomacy graph.

### Candidate broad allegiance domains

The exact enum/API is deferred, but the semantic vocabulary should be able to represent at least:

```text
CIVILIAN / LOCAL_POLITY
GUILD
ILLAGER / HOSTILE_FACTION
INDEPENDENT
PLAYER / PLAYER_ORGANIZATION
UNCLAIMED / UNKNOWN
```

More specific factions or polities may refine these broad domains where authored content requires it.

The broad domain is not necessarily the unique legal owner. For example, a privately owned civilian freighter operating under Guild registration may have:

```text
owner = private operator
broad allegiance = CIVILIAN
Guild status = registered / insured
local jurisdiction = settlement polity
```

Ownership, institutional recognition, and allegiance remain separable.

## Allegiance should be composable, not magical

Asset allegiance is evidence used by other systems. It does not itself grant universal immunity, hostility, or legality.

Examples:

- a Guild-marked vessel in a full-member settlement is ordinarily recognized and serviced;
- the same vessel in an excluded/hostile region may be denied access or intercepted;
- a civilian vessel may trade through Guild infrastructure without becoming Guild-owned;
- an illager-controlled aircraft is eligible for faction-hostile encounter behavior where regional control permits it;
- a captured or transferred vessel may change ownership and possibly allegiance after the relevant registration/control transition rather than retaining an immutable spawn faction.

## Territorial control and local sovereignty

Territorial/faction control describes who can plausibly enforce power in a region or locale.

Local sovereignty/polity describes the authority responsible for ordinary law, land, taxation, policing, and settlement governance where such concepts matter.

The Guild generally does not replace either.

A region or cluster may therefore expose coarse state sufficient to answer:

```text
who controls the area;
which polity/local authority applies;
what factional threat or patrol behavior is plausible;
what commercial access conditions apply;
which Guild functions are recognized locally.
```

No international-relations matrix, government budget, election simulation, or dynamic treaty engine is required by this precommit.

## Guild relationship classes

Retain the existing conceptual relationship classes:

```text
FULL_MEMBER
ASSOCIATE
INDEPENDENT / NON_CHARTER
HOSTILE / EXCLUDED
```

These relationships influence recognition of:

- Guild accounts and services;
- bonded freight;
- registry and certification;
- insurance and claims;
- arbitration;
- salvage/recovery rights;
- route and navigation services;
- contract enforceability.

Lack of Guild membership does not remove a settlement from the economy.

## Economic participation outside the Guild

> Guild commerce is not synonymous with all commerce.

Independent and non-charter settlements may still:

- produce and consume goods;
- operate private aircraft;
- maintain internal or regional markets;
- accept private contracts;
- buy/sell to compatible external actors;
- participate in supply chains;
- maintain their own storage, transport, and industrial infrastructure.

The Guild primarily changes institutional support, trust, enforceability, information access, and network reach.

## Jurisdictional access

Routes, markets, ports, structures, and service points may apply coarse access states such as:

```text
OPEN
RESTRICTED
CONTROLLED
HOSTILE
```

Exact names are deferred.

Access can depend on:

- local control;
- asset allegiance;
- actor identity/allegiance;
- Guild status;
- authorization/certification;
- contract provenance;
- local hostility or exclusion state.

Consequences should normally enter existing systems through:

- service availability;
- inspection/authorization requirements;
- generalized transaction cost;
- route risk/reliability;
- insurance confidence;
- contract enforceability;
- physical patrol/interception eligibility when realized.

## Guild authorization is not universal legality

Guild authorization and local legality may disagree.

A Guild-recognized recovery claim may conflict with a local sovereign salvage restriction. A private commission may be locally legal but carry no Guild protection. A discretionary Guild action may rely on a disputed interpretation of delegated authority.

Therefore:

```text
GUILD_AUTHORIZED != UNIVERSALLY_LEGAL
LOCALLY_LEGAL != GUILD_PROTECTED
```

Institutional consequences are applied by the authority whose rules or obligations are relevant.

## No universal reputation meter

Do not collapse institutional relations into one global player reputation value.

A player may simultaneously have:

```text
Guild Standing: high
Settlement/Polity A: trusted
Faction B: hostile
Independent Settlement C: welcome
```

Guild Standing remains specifically Guild trust. Local/faction relationships remain separate, coarse state where gameplay requires them.

## Hostile and contested economies

Hostile territory can still contain coherent production, storage, military logistics, freight, markets, and civilian activity.

Hostility primarily changes who may access those systems and what physical response is plausible.

`CONTESTED` or equivalent regional state may increase uncertainty in:

- route security;
- authority;
- inspection;
- insurance;
- service recognition;
- contract enforceability.

These effects should feed the existing route and delivered-cost systems rather than spawning a separate political economy solver.

## Faction physical realization

Existing threat/faction governance remains authoritative for realized hostile activity.

Faction allegiance may be consumed by the realization layer to determine eligibility for:

- patrols;
- scouts;
- interceptors;
- gunships;
- raiders;
- controlled military/civic structures.

Large persistent physics fleets remain prohibited as a requirement for far-field faction plausibility. Coarse regional state remains authoritative offscreen; concrete assets are realized near relevant players or interactions.

## Sanctions and exclusion

Guild consequences should remain institutional rather than magical.

Possible consequences include:

- denial of new bonded contracts;
- suspension of Guild authorizations/certifications;
- altered collateral or insurance terms;
- restricted account/service access;
- route/network restrictions.

They do not automatically force every independent merchant, polity, or faction to behave identically.

Likewise, local/faction hostility should not automatically modify unrelated Guild state unless evidence, jurisdiction, or institutional rules connect the events.

## Ownership transfer and capture

Because vessels and some infrastructure may be captured, sold, salvaged, or transferred, allegiance must not be treated as immutable spawn provenance.

Where relevant, distinguish:

```text
provenance / origin
current owner/controller
current broad allegiance
Guild registration/recognition
local legal status
```

A captured illager aircraft can remain visibly of illager origin while becoming player-controlled. Whether it becomes Guild-registrable or legally recognized is a separate registry/certification question.

This distinction preserves both world history and player agency.

## Computational constraints

Do not require:

- all-pairs faction diplomacy;
- continuous political simulation;
- global law evaluation;
- per-NPC allegiance accounting;
- universal hostility propagation;
- dynamically simulated wars merely to support access checks.

Prefer small stable identifiers/tags plus coarse regional relationship/access state.

## Design invariants

> Territorial control, local sovereignty, Guild relationship, asset allegiance, ownership, and actor access are distinct concepts.

> Guild commerce is not synonymous with all commerce.

> Allegiance is a compact cross-system primitive for determining institutional identity and applicable access/hostility rules; it is not a complete diplomacy system.

> Guild authorization is not universal legality, and local legality does not automatically imply Guild protection.

> Hostile economies remain economically coherent; hostility primarily changes access, risk, enforcement, and realization.

> Allegiance may change through capture, transfer, registration, or control changes while provenance may remain historically persistent.

> Political/faction effects should enter existing route, market, contract, insurance, and realization systems through bounded access/risk/permission semantics rather than a grand-strategy layer.
