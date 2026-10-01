# AUTH-0053 — Seed-Aware Provider-Support Reservation Preflight

AUTH-0053 moves proof-backed reservation admission earlier than primary/full-volume/world-volume compilation.

It consumes an already deterministic SkyIslandArchipelagoPlan, where exact group/member seeds and descriptors are known, and determines whether the reservations already consumed by planning remain sufficient for every analytically certifiable morphology spec.

It does not move islands, enlarge reservations, or rewrite an archipelago.

## Position in the pipeline

    reusable templates
        -> deterministic archipelago/group planning
        -> exact member seeds/descriptors now exist
        -> AUTH-0053 reservation preflight
        -> only admitted plans may use compileProofBacked
        -> primary/full-volume compilation
        -> AUTH-0052 world support bundle

This is the earliest generic stage where arbitrary provider certificates can safely be evaluated, because provider support may depend on the exact derived member descriptor and seed.

## Why template-only preflight is insufficient

The current built-in support certificate is intentionally seed-independent, but SkyIslandMorphologyProvider does not require that property.

A third-party provider may analytically certify different support for different deterministic descriptor seeds.

SkyIslandGroupPlanner derives each member geometry seed from:

    group root seed + member ordinal

The reusable SkyIslandGroupTemplate does not contain that final seed.

AUTH-0053 therefore never assumes that a template-level envelope is sufficient for arbitrary providers.

## Member check

SkyIslandSupportReservationMemberCheck records, for one exact member:

- group ordinal/identifier;
- member ordinal;
- exact descriptor seed;
- morphology stable identifier;
- reserved horizontal radius;
- proposed below/above suspension reservation;
- optional analytical support envelope.

For certified members, it derives:

- requiredHorizontalRadius;
- requiredBelowSuspension;
- requiredAboveSuspension.

A member is admitted only when all three existing reservation dimensions contain certified support.

Uncertified members remain explicitly uncertified and are not assigned invented requirements.

## Exact world-space arithmetic

AUTH-0053 uses the same outward binary64 support-bound convention as AUTH-0052.

A relative requirement that is numerically equal to a reservation is not automatically sufficient, because AUTH-0052 expands world-space support minima/maxima outward with Math.nextDown/Math.nextUp.

Member adequacy is therefore evaluated in the exact descriptor world frame:

- horizontal X/Z support minima/maxima versus the existing square query reservation;
- suspension-relative underside/upper support after the same outward world-space rounding.

This prevents preflight from admitting a plan that AUTH-0052 would later reject by one ulp.

## Group check

AUTH-0053 also verifies that higher-level group reservation assumptions remain valid.

For every certified member:

    required member reach from group center
        = outward(
            distance(memberCenter, groupCenter)
            + certifiedHorizontalRadius)

The required group radius is the maximum of those reaches, rounded outward.

A group is admitted only when:

- all members are certified;
- all member horizontal/vertical reservations are adequate;
- requiredGroupRadius <= reservedGroupRadius.

This matters because archipelago placement already consumed reservedGroupRadius when separating groups.

If the real proof requirement is larger, AUTH-0053 rejects the plan even if the final sampled placement happens to have spare room.

Accidental slack is not allowed to retroactively validate an undersized planning contract.

## Vertical reservation

SkyIslandWorldVerticalReservation is checked before world-volume compilation.

For every certified member:

    maximumUndersideDepth <= belowSuspension
    maximumUpperOffset    <= aboveSuspension

This moves AUTH-0052's query-support failure earlier while preserving the same proof semantics.

## Report

SkyIslandSupportReservationPreflightReport carries:

- exact archipelago root seed;
- proposed vertical reservation;
- member checks;
- group checks.

It exposes:

- admitted;
- uncertifiedMemberCount;
- undersizedMemberHorizontalCount;
- undersizedVerticalCount;
- undersizedGroupCount;
- consumedReservationDefect.

consumedReservationDefect is true only when a known analytical requirement proves an existing reservation too small.

Uncertified support is reported separately because no numerical requirement is known.

## Fully proof-backed compilation

SkyIslandWorldCatalogCompiler gains:

    preflightSupportReservations(...)
    compileProofBacked(...)

compileProofBacked performs AUTH-0053 first.

If preflight rejects, primary morphology and full world-volume graphs are not compiled.

If preflight admits, AUTH-0052 compileWithSupport runs and must produce a fully certified bundle.

The older methods remain unchanged:

- compile — ordinary legacy compilation;
- compileWithSupport — partial proof bundle allowed.

This preserves diagnostics and compatibility while providing a strict production-quality proof path.

## Seed-awareness acceptance proof

AUTH-0053 includes a provider whose analytical support explicitly depends on descriptor.seed.

Two otherwise equivalent plans with different archipelago roots derive different exact member seeds.

The provider returns:

- 300 horizontal support for the first exact seed;
- 120 horizontal support for the second.

With a 200 horizontal reservation:

- first plan rejects;
- second plan admits.

The provider's compilePrimary method intentionally fails if called during the test.

Therefore the result proves that AUTH-0053:

- uses exact derived descriptors;
- does not compile primary/full-volume morphology;
- does not infer support from reusable templates.

Provider support certification may still call compileSecondaryMorphology to obtain the
provider-declared analytical factor envelope. SecondaryMorphologyContribution contains its factor
graph, so AUTH-0053 does not claim that literally no graph object can be constructed during
preflight.

## Fail-before-primary/full-volume proof

A second test provider supplies no support certificate and throws an AssertionError from compilePrimary.

compileProofBacked rejects at AUTH-0053 with an uncertified-member report before compilePrimary can run.

Thus preflight is genuinely upstream of primary/full-volume compilation rather than merely a
second check after world-volume compilation.

## Visual evidence atlas

The AUTH-0053 atlas is reservation/admission oriented rather than morphology oriented.

Six panels show:

- ADMITTED_BUILTIN;
- HORIZONTAL_GROUP_UNDERSIZED;
- VERTICAL_UNDERSIZED;
- INTERIOR_BLEND_UNCERTIFIED;
- SEED_A_LARGE;
- SEED_B_SMALL.

Each panel visualizes existing reservation versus analytical requirement for:

- member horizontal radius;
- below suspension;
- above suspension;
- group radius.

The seed-aware pair displays the exact derived member seeds to make the distinction auditable.

The atlas is diagnostic proof evidence, not an aesthetic morphology gate.

## Acceptance gate

Reject AUTH-0053 if:

- support is evaluated from reusable templates when exact provider inputs are not yet known;
- descriptor seed is omitted from evidence;
- an uncertified member receives invented numerical requirements;
- undersized reservations are silently enlarged;
- a group reservation is accepted from accidental final spacing rather than its consumed reservation contract;
- graph compilation occurs before strict preflight admission;
- compile or compileWithSupport changes behavior;
- partial certification is treated as proof-backed admission;
- Minecraft or NeoForge types enter the contract.

## Next milestone

AUTH-0053 makes exact reservation defects visible before graph compilation, but it does not yet help authors choose adequate reservations automatically.

A likely AUTH-0054 direction is **reservation requirement synthesis for certifiable planned members**.

It should produce recommended minimum:

- member horizontal reservation;
- group outer reservation;
- below/above suspension reservation;

from the exact deterministic plan while preserving the rule that applying those recommendations requires a fresh re-plan rather than mutating an existing accepted placement.
