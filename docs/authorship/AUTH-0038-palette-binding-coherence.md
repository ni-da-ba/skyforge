# AUTH-0038 — Palette-Binding Coherence Domains and Stable Binding Keys

AUTH-0038 defines where a downstream backend should reuse one concrete material-binding decision.

It remains backend-neutral.

No Minecraft registry object, block id, named rock, mineral species, or concrete palette entry enters the Skyforge world model.

## Problem

AUTH-0037 determines which semantic palette roles are eligible at one position.

If a backend binds those roles independently at every sample, the result can be semantically valid but visually incoherent:

- one assemblage could randomly change its primary material every few blocks;
- one altered body could use unrelated alteration materials from sample to sample;
- a continuous water-conditioned region could fragment into palette noise;
- a contact transition could create unstable per-voxel binding choices.

AUTH-0038 therefore defines stable **binding coherence domains** and **binding keys**.

A backend remains free to decide what concrete material a key maps to.

It should make that decision once per key and reuse it while the key remains the same.

## Stable key

SkyIslandSemanticPaletteBindingKey schema 1 contains:

- complete SkyIslandIdentity;
- AUTH-0037 semantic role;
- AUTH-0036 source channel;
- coherence-domain kind;
- deterministic semantic anchor id.

It exposes a canonical portable token:

~~~text
sfbind:v1:<world>:<province>:<cluster>:<island>:<kind>:<role>:<channel>:<anchor>
~~~

The token is deterministic for the same authored world and accepted semantic plan.

It contains no backend placement coordinate and no backend material identifier.

## Coherence domain kinds

AUTH-0038 defines:

- ASSEMBLAGE_REGION;
- CONDITIONED_REGION;
- CONTACT_TRANSITION.

### ASSEMBLAGE_REGION

PRIMARY_MATRIX and SECONDARY_MATRIX use ASSEMBLAGE_REGION coherence.

A matrix binding domain is a face-connected set of planning cells that:

- belongs to one AUTH-0034 assemblage;
- has the same AUTH-0037 role;
- has the same AUTH-0036 source channel.

Matrix binding does not cross an assemblage boundary.

If the dominant host channel changes inside one assemblage, the source-channel change is allowed to split binding coherence.

This preserves semantic distinction without creating per-cell binding randomness.

### CONDITIONED_REGION

ALTERATION_OVERPRINT, HYDROLOGIC_CONDITIONING, and MINERAL_BEARING_STRUCTURE use CONDITIONED_REGION coherence.

A conditioned binding domain is a face-connected set of AUTH-0037-eligible planning cells with the same role and source channel.

Unlike matrix roles, a conditioned domain may cross AUTH-0034 assemblage boundaries.

That is deliberate.

An alteration system, saturated body, or mineral-bearing structural system may remain semantically coherent while passing through different host assemblages.

The role still remains support-gated by AUTH-0037 at every member cell.

### CONTACT_TRANSITION

AUTH-0035 makes the final semantic field continuously sampleable between planning cells.

At some continuous contact positions, blending may create an eligible role/source combination that the nearest planning cell does not itself carry.

AUTH-0038 does not assign a fresh per-sample key.

Instead it creates a deterministic CONTACT_TRANSITION fallback key anchored by:

- island identity;
- active AUTH-0035 contact id;
- AUTH-0037 role;
- AUTH-0036 source channel.

Every equivalent role/source state on that semantic contact therefore resolves to the same stable key.

CONTACT_TRANSITION is a key scope, not a special contact material role.

## Planning-domain anchors

Every connected planning domain is anchored by the minimum planning-cell index in that domain.

The anchor is deterministic and independent of traversal order.

The stable key also includes island identity, role, source channel, and domain kind, so identical local anchor numbers on other islands or roles do not collide.

## Continuous sampling

AUTH-0038 sampling first asks AUTH-0037 for eligible roles.

For each role:

1. locate the nearest AUTH-0034 planning cell using the accepted island-normalized semantic metric;
2. reuse that planning cell's role binding key when role/source provenance matches;
3. if contact blending creates a role/source state absent from that cell, use the contact-scoped fallback key;
4. if no contact is active, missing planning-domain provenance is an invariant failure.

Therefore off-contact material cannot silently become sample-local binding noise.

## Binding semantics

A binding key means:

> a downstream backend should make one coherent concrete binding decision for this semantic role and reuse it wherever this key occurs.

A key does not define:

- the concrete material;
- the number of concrete variants within a backend binding;
- block placement;
- texture choice;
- stochastic pattern;
- expression ceiling.

AUTH-0037 support and expression ceilings remain attached to each sample.

AUTH-0038 only stabilizes the identity of the downstream binding decision.

## Material boundary

AUTH-0038 inherits AUTH-0037 ownership and material presence exactly.

Unowned space and authored cave void have no binding keys.

Every AUTH-0037 candidate on material receives exactly one AUTH-0038 key.

## Evidence

The authorship-palette-binding-coherence-v1 corpus uses the canonical six subsurface representatives at semantic depth 0.52.

Each role panel is categorical by stable binding key:

- PRIMARY KEY;
- SECONDARY KEY;
- ALTERATION KEY;
- WATER KEY;
- MINERAL KEY.

Repeated color inside one panel represents one stable binding key.

The CONTACT KEY panel shows only continuous contact-transition fallback keys.

White means:

- role ineligible; or
- material absent.

manifest.csv records:

- material sample count;
- unique visible key count for every role;
- contact-fallback sample count;
- total planned coherence-domain count;
- count of conditioned domains that coherently cross assemblage boundaries.

## Acceptance gate

Reject AUTH-0038 if:

- binding plans are non-deterministic;
- canonical tokens are non-deterministic;
- keys can collide across island identity, role, source channel, domain kind, or anchor;
- one matrix coherence domain crosses an AUTH-0034 assemblage boundary;
- one conditioned domain includes a cell where its AUTH-0037 role is ineligible;
- planning domains are not face-connected;
- one planning-cell role receives multiple keys;
- AUTH-0037 material presence or provenance is changed;
- an AUTH-0037 candidate receives no binding key;
- off-contact samples require ad hoc fallback keys;
- contact fallback is sample-local instead of contact-scoped;
- cave void receives binding state;
- visual evidence degenerates into per-sample/key noise;
- backend registry objects or concrete materials enter the world layer.

## Parallel implementation boundary

AUTH-0038 changes no Minecraft cave/carver/persistence/mutation contract.

A future backend-material lane may consume the canonical binding keys as deterministic cache/selection identities.

The implementation lane should not infer that AUTH-0038 itself authorizes any particular Minecraft block palette.

## Next milestone

If AUTH-0038 is accepted, the next native-authorship milestone should define a **backend-neutral material binding request contract**.

That contract should package:

- stable AUTH-0038 binding key;
- AUTH-0037 role;
- AUTH-0036 source semantics;
- support/expression constraints;
- relevant assemblage/contact context;

into the exact semantic request a backend binding resolver can consume.

Concrete backend binding remains downstream of that request.
