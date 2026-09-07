# Wave C15 — live 1:1 Nether portal linking and placement

**Status:** IN PROGRESS until exact-head runtime acceptance is green and merged  
**Issue:** #258  
**Predecessor:** C10 / PR #232

## Purpose

C10 proves that the standalone datapack changes the final live Nether dimension type to
`coordinate_scale=1.0`. C15 tests the next user-visible mechanical consequence: whether vanilla
portal destination search and portal creation actually behave as a 1:1 route at non-origin
coordinates while the retained Create/Sable/Aeronautics stack is loaded.

## Existing-portal linking

The fixture builds three valid vanilla portal rectangles:

```text
Overworld source          ( 640, 80, -384)
Nether 1:1 target         ( 640, 70, -384)
Nether 8:1 distractor     (  80, 70,  -48)
```

A real NeoForge FakePlayer is positioned inside the Overworld portal and the fixture calls the public
vanilla `NetherPortalBlock.getPortalDestination` path. Acceptance requires the returned
`DimensionTransition` to target the same-coordinate Nether portal rather than the deliberate
vanilla-compression distractor.

The reverse Nether-to-Overworld transition must return to the same-coordinate source portal.

## Missing-target placement

A second Overworld source portal is placed at `(-704, 80, 448)`. The fixture first proves no active
Nether portal exists in the normal destination search radius around `(-704, 70, 448)`, then calls the
same vanilla destination method.

Acceptance requires vanilla to create a searchable Nether portal near that 1:1 target and return a
transition near the created portal.

## Retained-stack boundary

The C15 run loads the exact retained Create/Sable/Aeronautics artifacts already pinned by Skyforge.
It does not install a portal-overhaul mod and does not patch vanilla portal code.

Passing C15 accepts ordinary player portal linking/placement mechanics for the interim 1:1 route-scale
policy. It does not accept assembled contraption transfer, passenger/cargo transfer, final authored
portal-site safety, or subjective terminal usability.
