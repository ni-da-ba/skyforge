# AUTH-0092 — Regional island isolation evidence

## Purpose

AUTH-0092 adds raw regional isolation evidence above the accepted AUTH-0087 published
authored-realization binding.

Later fauna, ecology, civilization, route, and resource consumers need to distinguish questions such
as whether an island is embedded in a close cluster or separated from its nearest peer. The missing
input is geometric evidence, not another ecology class.

AUTH-0092 therefore answers only:

> For each exactly published authored island, which canonical peer has the smallest nominal radial
> gap, what is the exact horizontal center distance to that peer, and what regional summary follows
> from those selected relations?

It does not decide whether an island is "isolated", remote enough for a species, suitable for a
settlement, or expensive to travel to.

## Accepted source boundary

The public profiler accepts exactly one:

~~~text
SkyIslandPublishedAuthoredRealizationBinding
~~~

That binding already proves exact AUTH-0058 publication coverage through exact AUTH-0046
authored-realization associations.

Callers cannot supply:

- an arbitrary island subset;
- an inferred spatial association;
- a custom distance threshold;
- a custom nearest-neighbor list;
- a backend/Minecraft position source.

Canonical AUTH-0046 association order is retained in the profile.

## Raw horizontal center distance

For two exact associations A and B, AUTH-0092 derives horizontal center distance only from the
realized descriptors carried by those associations:

~~~text
centerDistance(A,B)
  = hypot(B.centerX - A.centerX,
          B.centerZ - A.centerZ)
~~~

This is world-horizontal planning evidence.

It is not:

- flight-path length;
- route cost;
- geodesic terrain distance;
- Minecraft chunk distance;
- line-of-sight;
- a gameplay range threshold.

## Nominal radial gap

AUTH-0046 already requires authored and realized nominal radii to match exactly.

AUTH-0092 therefore derives:

~~~text
nominalRadialGap(A,B)
  = max(0,
        centerDistance(A,B)
        - A.nominalRadius
        - B.nominalRadius)
~~~

The clamp means overlapping nominal radial envelopes have zero nominal gap.

This is deliberately not called physical terrain-edge distance. A nominal radius is an authored
planning scale, not the exact outer support of every realized morphology.

## Deterministic nearest relation

For each subject island, the selected peer is ordered by:

1. smallest nominal radial gap;
2. then smallest horizontal center distance;
3. then existing canonical AUTH-0046 association order for an exact remaining tie.

The third rule introduces no new key or random source. It preserves existing deterministic
provenance.

The profile retains the exact selected association together with the exact derived distance values.

## Singleton regions

A valid one-island publication has no peer.

AUTH-0092 represents that case with:

~~~text
nearestNeighbor = empty
all min/mean/max nearest-distance summaries = empty
~~~

No synthetic neighbor or zero-distance peer is fabricated.

## Regional summaries

For multi-island regions, AUTH-0092 exposes min/mean/max values over the selected gap-first neighbor
relation:

- selected-neighbor center distance;
- selected-neighbor nominal radial gap.

These summaries are descriptive regional evidence only.

They do not classify the region as sparse, dense, isolated, connected, safe, or traversable.

## Covariance

A uniform horizontal translation preserves the geometric relation up to ordinary floating-point
translation arithmetic.

A uniform two-times horizontal scale of centers and matching nominal radii preserves selected
neighbor identity and doubles both raw distance quantities.

No normalized isolation score is introduced.

## Concrete downstream motivation

AUTH-0092 is one missing cause for future habitat and world-composition decisions.

Examples that may eventually consume it together with other accepted causes include:

- island-fauna dispersal or remoteness opportunity;
- civilization/site isolation;
- route-network planning;
- regional resource specialization.

AUTH-0092 alone is insufficient for any of those decisions. Ecology, freshwater, geology,
disturbance, trophic/predator state, progression, infrastructure, and other domain-specific causes
remain independently owned.

## Ownership boundaries

AUTH-0092 remains backend-neutral.

It does not inspect or emit:

- Minecraft coordinates, chunks, tickets, or entity ranges;
- species, spawn counts, or carrying capacity;
- settlement/province labels;
- route costs or aircraft range;
- resource availability;
- persistence or lifecycle behavior;
- physical terrain-edge separation.

## Acceptance gate

Reject AUTH-0092 if:

- callers can bypass AUTH-0087 with arbitrary islands;
- association identity is inferred from geometry or list position;
- nominal radial gap is presented as exact physical terrain separation;
- a distance threshold or isolated/not-isolated class is introduced;
- exact selected-peer provenance is lost;
- tie handling depends on a new random/key source;
- singleton publications fabricate a neighbor;
- substituted distance values can enter a canonical entry without failing closed;
- Minecraft/backend policy enters the contract.

## Evidence target

The proof package demonstrates:

- exact AUTH-0087 publication/authorship provenance;
- canonical association-order retention;
- deterministic gap-first nearest selection;
- exact raw distance derivation from association geometry;
- singleton empty-neighbor behavior;
- uniform translation covariance;
- uniform two-times scale covariance;
- no ecological/gameplay/backend classification.

This is an architecture/provenance corpus, not a human aesthetic gate.

## Next boundary

Do not convert AUTH-0092 into a fauna/resource/settlement threshold immediately.

The next Authorship step should add a genuinely missing cause only when a concrete Bootstrap,
resource, fauna, or civilization consumer requires it. Cave/cliff opportunity remains a plausible
future cause, but semantic cave depth must not be relabeled as physical cubic habitat volume.
