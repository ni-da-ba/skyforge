# AUTH-0058 — Accepted compiled-world publication identity

## Purpose

AUTH-0058 defines the first backend-neutral capability that downstream adapters may treat as a
published regional Skyforge world.

AUTH-0057 established that an `ACCEPTED_ONE_PASS` convergence report can hand its exact accepted
fresh plan into proof-backed world compilation and emerge as a fully certified support bundle.
AUTH-0058 does **not** add another planning or compilation stage. It packages that already-proven
result behind an explicit publication identity and makes the publication gate visible in the type
system.

The design preserves the Volume II rule that Minecraft is a backend realization rather than the
foundation of the world model.

## Problem

Before AUTH-0058, a downstream subsystem could receive several related objects:

- an AUTH-0056 convergence report;
- an AUTH-0057 accepted compilation;
- a raw `SkyIslandWorldCatalog`;
- a `SkyIslandWorldCatalogSupportBundle`.

Those objects have different proof strength.

A raw catalog proves neither:

- that its planning attempt converged;
- that the accepted fresh preflight reproduced under the compilation registry;
- that every volume carries support proof;
- that the catalog is the exact catalog compiled from the accepted fresh plan.

AUTH-0058 therefore needs a publication capability whose construction path begins only from the
already-bound AUTH-0057 result.

## Publication identity

`SkyIslandCompiledWorldPublicationId` is schema-versioned and contains:

    schemaVersion
    archipelagoRootSeed
    publicationRevision

The canonical diagnostic/cache token is:

    sfpub:v<schema>:<16-hex regional root>:<16-hex revision>

### Regional root

`archipelagoRootSeed` identifies the accepted regional realization domain.

It must equal both:

- the accepted fresh plan root;
- the compiled world-catalog root.

### Publication revision

`publicationRevision` is an explicit positive author-controlled version axis within the regional
root.

It is **not**:

- a graph digest;
- a content hash;
- a provider implementation version;
- a substitute for catalog identity;
- a substitute for support-certificate identity.

The revision exists so a publication consumer can distinguish explicitly versioned releases of a
regional world. If compiled content or its proof binding changes while retaining the same regional
root, the publishing authority must issue a new revision rather than reuse an already published
identity.

AUTH-0058 deliberately does not create a persistent publication registry that could police
cross-process revision reuse. The identity contract is explicit; registry/persistence policy is a
later concern.

## Publication capability

`SkyIslandCompiledWorldPublication` contains exactly:

    SkyIslandCompiledWorldPublicationId
    SkyIslandAcceptedConvergenceCompilation

The accepted compilation remains the authoritative proof-bearing object. The publication does not
copy its plan, catalog, or certificate set into loosely related parallel state.

The publication exposes derived exact views:

    acceptedPlan()
    catalog()
    supportCertificates()
    catalogIdentity()
    volumeCount()

### Accepted convergence identity

`acceptedPlan()` is the exact AUTH-0056 fresh plan already bound by AUTH-0057.

No re-plan or semantic reconstruction occurs during publication.

### Compiled catalog identity

`catalog()` is the exact AUTH-0057 compiled catalog.

`catalogIdentity()` is its deterministic plan-order list of `SkyIslandWorldVolumeId` values.
Those IDs preserve:

- archipelago root seed;
- group identifier;
- group ordinal;
- member ordinal;
- exact geometry seed.

AUTH-0058 does not invent a second catalog identifier.

### Support-certificate identity

`supportCertificates()` is the exact deterministic plan-order certificate set already carried by
the AUTH-0057 support bundle.

Publication requires:

    certifiedCount == catalog.volumeCount

and therefore cannot represent a partially certified world.

AUTH-0058 does not recompute, widen, shrink, or reinterpret support envelopes.

## Publication gate

`SkyIslandCompiledWorldPublisher.publish(...)` accepts only:

    SkyIslandAcceptedConvergenceCompilation
    publicationRevision

There is intentionally no overload accepting:

- `SkyIslandWorldCatalog`;
- `SkyIslandWorldCatalogSupportBundle`;
- `SkyIslandArchipelagoPlan`;
- `SkyIslandSupportConvergenceReport`.

This is a capability boundary.

A subsystem that holds only a raw catalog has not proved enough to publish it.

A subsystem that holds an AUTH-0057 compilation already holds the exact accepted convergence,
reproduced preflight, fully certified catalog, deterministic plan-order world-volume binding, and
support certificates required by AUTH-0058.

## Constructor revalidation

Although AUTH-0057 already proves its invariants, the publication constructor revalidates the
boundary assumptions that matter to downstream adapters:

- convergence remains accepted;
- reproduced preflight remains admitted;
- support bundle remains fully certified;
- publication regional root equals compiled catalog root;
- publication regional root equals accepted fresh-plan root;
- certificate count equals catalog volume count.

This is intentionally redundant at the trust boundary.

## Determinism and version separation

For the same accepted compilation and same publication revision:

- publication identity is identical;
- canonical publication token is identical;
- catalog identity is identical;
- support-certificate set is identical.

Changing only the publication revision:

- changes publication identity;
- changes the canonical publication token;
- does not mutate or recompile the accepted world;
- does not change catalog identity;
- does not change the support-certificate set.

Changing the regional root while holding revision constant changes:

- publication identity;
- canonical publication token;
- accepted plan identity;
- compiled catalog identity.

## Explicit non-goals

AUTH-0058 does not:

- run the planner;
- synthesize reservation requirements;
- execute AUTH-0056 convergence;
- re-run AUTH-0057 compilation;
- retry a failed operation;
- alter a world reservation;
- serialize a publication to disk;
- maintain a persistent publication registry;
- define provider/plugin versioning;
- map semantic materials to Minecraft BlockState;
- discover chunks;
- mutate terrain;
- install a NeoForge runtime object.

The publication is backend-neutral data and capability only.

## Backend consumption rule

A downstream backend adapter that requires a proof-backed regional world should consume
`SkyIslandCompiledWorldPublication` rather than a raw `SkyIslandWorldCatalog`.

That rule provides an explicit answer to:

> When is a compiled regional world safe to expose downstream?

For AUTH-0058, the answer is:

> Only after AUTH-0056 accepted one fresh attempt, AUTH-0057 reproduced that accepted preflight and
> compiled the exact fresh plan into a fully certified bundle, and AUTH-0058 assigned an explicit
> publication identity to that exact bound result.

This does not imply that every future backend action is automatically safe. Backend-specific
placement, chunk ownership, persistence, and mutation contracts remain separate downstream gates.

## Acceptance gate

Reject AUTH-0058 if:

- a raw catalog can be published through the AUTH-0058 publisher;
- a raw support bundle can be published through the AUTH-0058 publisher;
- publication can trigger planning or compilation;
- publication can silently change the accepted plan;
- publication can silently change world-volume order or IDs;
- publication can omit any support certificate;
- publication identity can use a non-positive revision;
- publication identity can use an unsupported schema;
- the publication root can differ from accepted-plan/catalog root;
- changing revision mutates compiled world content;
- Minecraft or NeoForge types enter the publication contract.

## Visual evidence

AUTH-0058 uses a 1280×720 (16:9) contract atlas with six panels:

- `PUBLICATION_GATE`;
- `CONVERGENCE_BINDING`;
- `CATALOG_BINDING`;
- `SUPPORT_BINDING`;
- `VERSION_AXIS`;
- `REGIONAL_IDENTITY`.

The atlas is proof/architecture evidence, not morphology approval.

## Next boundary

A likely AUTH-0059 direction is **publication-set / backend-view admission**.

It should define how one or more immutable AUTH-0058 publications become an explicitly selected
backend query view while preserving:

- publication identity;
- deterministic volume order;
- support proof;
- regional isolation;
- replacement/version semantics.

It should still avoid Minecraft BlockState mapping or terrain mutation until the backend-view
contract is stable.
