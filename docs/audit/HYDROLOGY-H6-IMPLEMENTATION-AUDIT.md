# Hydrology H6 implementation audit

Status: code audit complete; automated exact-head validation and human visual acceptance remain gates.

## Scope

This audit covers the focused key-287 production hydrology path from accepted authored intent through
Minecraft realization:

- watershed and coherent visible-channel authority;
- terrain-aware channel routing and AUTH-0105 fluvial shaping;
- retained-water projection;
- exact-volume Minecraft hydrology projection;
- physical admission and deferred catch-up;
- composed-cave / surface / interior lifecycle ordering;
- authored-water propagation fencing; and
- the H6 focused visual review harness.

The audit does not change authored watershed topology, accepted graph endpoints, retained-basin
selection, drop selection, or H2/H3/H4 tuning thresholds.

## Authoritative invariants checked

- Priority-Flood drainage authority remains acyclic and downstream flow is flood-rank decreasing.
- Visible channels preserve accepted coarse graph endpoints and remain inside the bounded fine
  routing corridor.
- Minecraft rasterization consumes accepted naturalized paths; it does not invent alternate routes.
- Retained water remains one connected, level physical basin or fails closed.
- Water, dry carving, and surface dressing are globally disjoint after normalization.
- Exact-volume ownership remains authoritative, including stacked-volume foreign-owner exclusion.
- Deferred catch-up never creates chunk tickets; the H6 harness alone owns its bounded review tickets.
- Cave topology precedes final surface representation and whole-volume native population ordering.
- Authored visible water remains fenced to the immutable authored water footprint after the review
  reaches READY.

## Defects found and corrected

### 1. Lazy cross-thread hydrology planning

World-generation and server-thread catch-up could both enter a lazily initialized authored-hydrology
cache. The mapping computation was expensive enough to block the server thread. Hydrology is now
built once as immutable adapter state before publication.

### 2. Server-thread bootstrap monopolization

Moving planning to eager construction initially moved the expensive work into player login. The H6
harness now performs pure immutable bootstrap work on a dedicated worker and installs runtime
bindings only from the server thread. Bootstrap and server-thread watchdogs remain active diagnostic
guards.

### 3. Whole-reach rectangular rasterization

The Minecraft adapter scanned each reach's complete expanded bounding rectangle and compared every
column to every path segment. It now rasterizes exact segment-local corridors and merges minimum
point-to-segment distance in canonical order, preserving the same corridor membership without the
large area-times-segment multiplier.

### 4. Repeated deterministic column evaluation

Overlapping reaches repeatedly recomputed exact solid ranges and fluvial samples. Plan-local caches
now reuse deterministic column support and field results.

### 5. Quadratic deployment validation

Deployment overlap validation used large Lists as membership collections, producing quadratic
behavior for production-size retained basins. Validation is now hash-based and linear while retaining
the same cross-role rejection semantics.

### 6. Giant whole-island immutable membership sets

Global water/carve conflict normalization constructed very large immutable sets, and the key-287
bootstrap spent tens of seconds rehashing them. Conflict resolution is now partitioned by chunk and
final immutable Deployments are constructed only once.

### 7. Whole-island scan on every chunk realization

The runtime previously iterated every deployment and every authored block position for each chunk,
discarding positions outside the current chunk. The normalized plan is now indexed once into
immutable chunk-local projections. Terrain realization, authored-water membership, and population
state lookup all use that same chunk index.

### 8. Duplicate global runtime caches

Separate whole-island water-position and population-state caches duplicated the deployment
representation and required additional full-plan hashing. They were removed in favor of the single
chunk-local state projection.

### 9. Redundant exact-volume voxel reclassification

After obtaining the authoritative continuous integer solid range for a column, the hydrology adapter
reclassified every Y sample in that range. Single-volume projection now trusts the accepted exact
range; stacked volumes retain per-cell foreign-owner exclusion.

### 10. Whole-ledger snapshots in H6 phase barriers

The review cursor rebuilt complete pending sets to answer whether its current chunk still owed work.
Terrain, biome-presentation, and native-interior barriers now use direct per-volume/per-chunk ledger
lookups.

### 11. READY removed the authored-water propagation fence

The review harness closed the terrain binding when generation completed. That binding also supplies
the immutable AUTHORED_HYDROLOGY membership authority used by fluid propagation. READY now closes
mutable generation obligations but keeps the immutable terrain/hydrology binding installed for the
human review.

### 12. Bootstrap information command could re-enter fixture construction

The fixture builder is synchronized. Calling the info command during worker bootstrap could therefore
block the server thread behind the same construction. The command now reports that metadata is still
bootstrapping rather than synchronously re-entering fixture creation.

### 13. Ecology rebuilt the fluvial field after bootstrap

The H6 bind path constructed production ecology on the server thread after the terrain adapter had
already planned the same authored fluvial geometry. The terrain adapter now retains the exact
immutable fluvial field from its hydrology PlanningResult, and the H6 ecology resolver consumes that
same field. This removes duplicate planning and guarantees that native river/bank biome context is
derived from the same geometry that produced the Minecraft water projection.

## Additional audit notes

The DR-70 review world preset is intentionally void-backed. The H6 runtime does not construct a
stone platform; absence of a platform is therefore not a hydrology-generation failure.

The missing Create/CreateAddition recipe messages emitted by the isolated ModDev run are unrelated
optional-mod/resource noise. They do not prevent the world from reaching the integrated-server
hydrology bootstrap.

The world/authorship layer did not reveal a topology-authority defect in this pass. Existing tests
cover deterministic watershed accumulation, acyclic downstream routing, bounded fine corridors,
fixed graph endpoints, coherent pruning, normalized fluvial terrain, contained centerline water, and
geomorphic profile ordering.

## Validation gates

Automated validation for the exact audited head must include:

- ordinary Gradle check;
- NeoForge hydrology adapter tests, including chunk-index partition equivalence and idempotent replay;
- physical-admission point-lookup regression coverage;
- source guards for chunk-local runtime application and READY-time authored-water fence retention;
- existing world hydrology routing/fluvial tests; and
- PR CI on the complete branch diff.

A successful automated run does not replace H6 human visual acceptance. The branch remains draft and
the 100-island DR-70 atlas remains frozen until key 287 passes the human-eye review.

The final audit head also preserves defensive copies at the immutable Deployment boundary; caller
collection mutation after construction is covered explicitly by regression test.
