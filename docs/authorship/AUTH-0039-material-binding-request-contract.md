# AUTH-0039 — Backend-Neutral Material Binding Request Contract

AUTH-0039 defines the exact stable semantic request a backend material resolver may consume.

It remains backend-neutral.

No named rock, mineral species, ore, Minecraft block, registry object, resource location, or concrete palette entry enters the Skyforge world model.

## Functional position

The material-authorship chain is now:

~~~text
AUTH-0036 compositional lithologic realization
        ↓
AUTH-0037 eligible semantic palette roles + local expression limits
        ↓
AUTH-0038 stable binding coherence key
        ↓
AUTH-0039 stable backend-neutral binding request
        ↓
future backend resolver
        ↓
concrete backend material binding
~~~

AUTH-0039 is the contract boundary immediately before concrete backend material choice.

## Why a request is distinct from a local sample

AUTH-0037 support varies continuously through the island.

If a backend chose a concrete material directly from the first local sample it happened to encounter, generation order could affect the selected material for one AUTH-0038 coherence key.

AUTH-0039 prevents that.

The stable resolver request contains only information that is invariant for the AUTH-0038 binding identity.

The local sample keeps:

- actual AUTH-0037 support;
- actual AUTH-0037 expression ceiling.

Therefore:

- concrete binding identity can be cached once per stable request/key;
- local spatial expression may still vary continuously;
- traversal/generation order cannot legitimately change the request.

## Stable request contents

SkyIslandMaterialBindingRequest contains:

- AUTH-0038 binding key;
- required/optional state;
- AUTH-0037 minimum eligible support policy;
- AUTH-0037 minimum secondary-host ratio when applicable;
- maximum expression ceiling allowed by the semantic role;
- stable lithologic assemblage context;
- contact id/kind for CONTACT_TRANSITION requests.

Role and AUTH-0036 source channel remain directly available through the binding key.

## Stable role policy

The request projects the accepted AUTH-0037 policy:

| Role | Minimum support | Secondary host ratio | Maximum expression |
| --- | ---: | ---: | ---: |
| PRIMARY_MATRIX | 0.00 | 0.00 | 1.00 |
| SECONDARY_MATRIX | 0.18 | 0.28 | 0.48 |
| ALTERATION_OVERPRINT | 0.22 | 0.00 | 0.56 |
| HYDROLOGIC_CONDITIONING | 0.24 | 0.00 | 0.48 |
| MINERAL_BEARING_STRUCTURE | 0.20 | 0.00 | 0.34 |

These are resolver-side semantic limits.

They do not replace the local candidate's actual support or expression ceiling.

## Assemblage-region requests

For an AUTH-0038 ASSEMBLAGE_REGION key, the request carries exactly one AUTH-0034 assemblage id/kind.

That makes the stable host context explicit to a backend resolver while preserving the AUTH-0038 rule that matrix binding cannot cross assemblage boundaries.

## Conditioned-region requests

For an AUTH-0038 CONDITIONED_REGION key, the request carries the complete ordered set of AUTH-0034 assemblages participating in that connected conditioned region.

A backend can therefore know whether an alteration, hydrologic, or mineral-bearing binding is:

- local to one host assemblage; or
- intentionally coherent across several host assemblages.

The request does not tell the backend which concrete material to choose.

## Contact-transition requests

CONTACT_TRANSITION keys are not planning-cell domains.

AUTH-0039 synthesizes their request deterministically from the AUTH-0038 key and the anchored AUTH-0034 contact.

A contact request carries:

- contact id;
- contact kind;
- first parent assemblage id/kind;
- second parent assemblage id/kind;
- stable role/source policy.

The request therefore remains identical anywhere the same contact-scoped binding key reappears.

It is not derived from whichever local sample happened to be encountered first.

## Local request use

SkyIslandMaterialBindingRequestUse pairs:

- one AUTH-0038 binding candidate;
- its stable AUTH-0039 resolver request.

The local binding candidate still owns:

- local support;
- local expression ceiling.

The stable request owns:

- binding identity;
- resolver policy;
- stable lithologic context.

This separation is intentional.

## Backend contract

A future backend resolver may:

1. receive SkyIslandMaterialBindingRequest;
2. use the binding key as a deterministic cache/selection identity;
3. inspect semantic role, source channel, role-policy limits, assemblage context, and contact context;
4. choose one backend-specific material binding;
5. reuse that material binding for every occurrence of the same request key.

A downstream spatial realization stage may then use the local SkyIslandMaterialBindingRequestUse support/ceiling to decide how strongly that already-chosen binding appears.

A backend must not interpret local support as a reason to choose a different concrete material for the same stable request key.

## Evidence

The authorship-material-binding-request-v1 corpus uses the canonical six representatives at semantic depth 0.52.

Panels:

- PRIMARY REQUEST;
- SECONDARY REQUEST;
- ALTERATION REQUEST;
- WATER REQUEST;
- MINERAL REQUEST;
- CONTACT REQUEST.

Color identifies stable resolver request identity.

Repeated color means one future backend concrete material choice should be reused.

The manifest records:

- material samples;
- unique resolver requests;
- unique requests by role;
- contact-request sample count;
- maximum assemblage-context breadth;
- mean requests per material sample.

## Acceptance gate

Reject AUTH-0039 if:

- one AUTH-0038 binding key can produce two different stable requests;
- request identity depends on local support or sampling order;
- one local AUTH-0038 candidate receives zero or multiple resolver requests;
- local AUTH-0037 support/expression values are changed;
- request maximum expression is below an accepted local ceiling;
- stable request support policy diverges from AUTH-0037;
- ASSEMBLAGE_REGION request contains multiple assemblages;
- CONDITIONED_REGION request loses participating assemblage context;
- CONTACT_TRANSITION request loses its anchored contact or parent assemblages;
- contact request depends on the first sampled position;
- authored cave void receives request state;
- backend registry/material objects enter the world layer.

## Parallel implementation boundary

AUTH-0039 changes no cave/carver/persistence/mutation behavior.

The Minecraft implementation lane should not bind blocks directly from AUTH-0039 until a backend material resolver and concrete binding policy are deliberately introduced.

## Next milestone

If AUTH-0039 is accepted, the next authorship milestone should define **backend-neutral material binding capabilities and compatibility constraints**.

That milestone should describe what properties a concrete backend candidate must advertise in order to satisfy a request—for example structural matrix suitability, fabric expressiveness, conditioning/overlay suitability, or structural accent suitability—without naming any Minecraft blocks.

Only after that capability contract is stable should a Minecraft-specific resolver map semantic requests to actual block/material candidates.
