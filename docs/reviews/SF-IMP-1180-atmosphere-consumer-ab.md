# SF-IMP-1180 — Atmosphere consumer machine A/B

Issue: #1180  
Status: diagnostic protocol

## Purpose

Resolve two observations from the 2026-09-27 project-owner atmosphere review without tuning the accepted atmosphere authority:

1. the Reliable Gliders prototype produced no perceptible lift difference by feel;
2. repeated Fowl Play hawk batches tended to leave the high DR-50 island and circle downward toward ordinary terrain.

## Stop boundary

This tranche is measurement-only.

It must not:
- change A4MC strength or physics;
- change the 1.5 m/s hawk SOAR-entry threshold;
- change C7 smoothing or sink constants;
- add a second weather/thermal authority;
- treat ordinary Fowl Play behavior as a Skyforge bug without an A/B discriminator.

## Glider evidence

The workflow acquires a fresh realized-terrain A4MC treatment probe on the canonical DR-50 specimen through the same server-authoritative `SkyforgeAtmosphereView` path accepted by #1140/#1142.

`tools/analyze_atmosphere_consumer_ab.py glider` then replays each measured temporal sample through the exact constants read from `SkyforgeGliderLiftCoupling.java`.

The replay reports:
- per-point measured updraft;
- exact per-tick C7 vertical-velocity delta;
- 600-tick accumulated altitude advantage versus the Reliable Gliders baseline sink;
- whether the effect is machine-measurable.

This is an effect-size diagnostic, not a gameplay-tuning recommendation.

## Hawk evidence

The workflow generates DR-50 once, clones that exact persisted world into two run directories, and observes equal hawk batches for 1,200 ticks:

- **control:** Fowl Play loaded, C6 disabled;
- **treatment:** same world and spawns, C6 enabled.

Each arm records island and ordinary-terrain comparison batches, including:
- spawn position;
- min/mean/max/final Y;
- maximum horizontal displacement;
- island-edge departure;
- descent below the island vertical envelope;
- approach to ordinary terrain after departure;
- removal status;
- C6 adaptation, SOAR-transition and steering-command counters.

The treatment must adapt every hawk. The evidence separately records whether C6 ever actually changed schedules or issued navigation.

## Interpretation

- Equivalent coarse outcomes with zero C6 transitions/steering strongly support stock Fowl Play/world-geometry behavior.
- Divergence with zero C6 transitions/steering requires inspection of adaptation-only side effects before attributing the behavior to weather.
- Divergence after legitimate C6 SOAR/steering means the thermal navigation path itself must be inspected.
- Glider effect magnitude may be machine-valid while remaining sub-perceptual; perceptual tuning is explicitly later work.
