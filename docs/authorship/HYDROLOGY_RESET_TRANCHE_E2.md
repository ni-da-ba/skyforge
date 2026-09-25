# Hydrology reset tranche E2 — hard retained-basin qualification

**Status:** policy mechanism implemented; calibrated limits pending E1 basin corpus
**Depends on:** E1 retained-basin diagnostics
**Terrain mutation:** forbidden
**Minecraft changes:** none

## Purpose

E2 converts continuous retained-water diagnostics into a hard pre-authoring decision.

Open-water candidates are rejected for:

- escaping the bounded semantic search neighborhood;
- lacking a closed numerical shoreline;
- excessive depth relative to basin diameter;
- excessive shoreline grade;
- insufficient headroom below the semantic spill surface.

Boundary escape and missing shoreline are structural failures. Numeric limits remain explicit policy
values and are not yet frozen as production defaults.

A rejected basin is never repaired by excavating a retaining bowl or truncating the shoreline.

Wetlands remain outside this contract because they are saturated-margin/ecology semantics rather than
open-water basins.

The calibrated POND/LAKE limits will be frozen only after the deterministic E1 basin manifest is
reviewed.
