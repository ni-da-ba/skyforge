# Wave C24 — petroleum infrastructure candidate matching

**Status:** ACCEPTED merge candidate  
**Issue:** #372  
**Predecessor:** C23 / PR #367  
**Authorship inputs:** AUTH-0096, AUTH-0097, AUTH-0100  
**Parent vertical slice:** #224

## Purpose

C23 proves whether a published province can satisfy the geological prerequisite for an intentional
petroleum strategic node. C24 asks the next concrete Content question:

> Can already-accepted local site/access evidence nominate broad petroleum infrastructure candidates
> without another Authorship wrapper?

C24 intentionally performs **candidate nomination only**.

## Roles

### PETROLEUM_EXTRACTION_INTERFACE

Candidate requirements:

- the AUTH-0097 profile belongs to a C23/AUTH-0100 petroleum-eligible association;
- the local AUTH-0096 source anchor has exact physical surface support.

This means only:

> this petroleum-eligible island has a real local surface anchor where extraction infrastructure
> might later be tested.

It does not align a literal subsurface deposit to that anchor. Deposit geometry and exact
surface/subsurface alignment remain Implementation-owned.

### REFINERY_PROCESSING

Candidate requirement:

- exact AUTH-0096 physical surface support.

A refinery may occupy another island in the same petroleum province. C24 therefore does not require
refinery candidates to be petroleum-source islands.

No flatness, area, relief, grade, hydrologic, or morphology threshold is introduced at this stage.
Those requirements should come from concrete structure/process geometry rather than intuition.

### FREIGHT_TRANSFER_EDGE

Candidate requirements:

- exact AUTH-0096 physical surface support;
- at least one AUTH-0097 ray with an accepted observed-open sample.

This is deliberately broad directional edge evidence.

It is not:

- a runway;
- a dock;
- an aircraft approach corridor;
- neighboring-island clearance;
- a vehicle envelope;
- exact structure admission.

## Provenance and order

The AUTH-0097 profile must belong to an association in the exact C23/AUTH-0100 published region.

Candidate order is unchanged AUTH-0097 anchor order.

C24 does not use AUTH-0100 mean geological ranking for local site nomination and does not return a
selected anchor.

## Why no numeric thresholds

AUTH-0096/0097 were deliberately authored as threshold-free evidence.

C24 follows the staged site-capability rule: coarse filters should avoid premature false negatives,
while exact geometry/admission later catches false positives.

Therefore this first matcher uses only accepted presence/identity semantics:

- petroleum association eligibility from C23/AUTH-0100;
- physical surface present from AUTH-0096;
- observed open directional sample from AUTH-0097.

Any later minimum footprint, grade, approach length, water setback, tank-farm area, or similar value
must be justified by a concrete retained/bespoke structure or vehicle requirement.

## Authorship consequence

If C24 passes, **no new Authorship petroleum/site wrapper is justified** for coarse infrastructure
candidate nomination.

The next gap moves downstream:

- Content defines concrete infrastructure requirement envelopes from actual retained/bespoke assets
  and gameplay roles;
- Implementation proves exact geometry/orientation/clearance, deposit-interface alignment,
  neighboring obstruction, mutation, placement, persistence, and lifecycle.

Only a concrete requirement that cannot be evaluated from AUTH-0096/0097 should reopen Authorship.

## Acceptance

1. extraction-interface nomination requires a C23 petroleum-eligible association;
2. extraction/refinery candidates preserve exact supported AUTH-0096/0097 anchor order;
3. refinery candidates may exist on another island in the same published region;
4. freight-edge candidates are exactly supported anchors with at least one observed-open AUTH-0097 ray;
5. evidence from outside the exact C23/AUTH-0100 region is rejected;
6. the public matcher returns candidate lists only and exposes no selected site;
7. no new numeric threshold, geological ranking, literal deposit, or backend ontology enters C24;
8. targeted tests and repository CI pass.

No Minecraft manual or visual gate applies to C24.


## Accepted candidate evidence

Exact C24 code head `73eaee5a91a52f8c6826f99b3449235258658773` passed:

- Wave C24 Petroleum Infrastructure Candidates run `34186747769`;
- Wave C20 Base Metal Content Policy regression run `34186747718`;
- repository CI run `34186747691`.

These tests exercise real AUTH-0096/AUTH-0097 profiles over exact published C23-region associations.
The result is sufficient to close the coarse-candidate evidence question: no new Authorship wrapper is
required at this stage.
