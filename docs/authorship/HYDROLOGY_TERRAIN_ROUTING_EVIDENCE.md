# Terrain-conditioned drainage evidence

This tranche adds a small, deterministic Priority-Flood/D8 diagnostic primitive. It conditions raster depressions to their lowest spill elevation, gives each interior cell one acyclic receiver leading to a raster-boundary outlet, and computes contributing cell counts. Equal-elevation routes are resolved by stable cell-index and neighbor ordering; no artificial elevation increment is added.

The method follows Barnes, Lehman & Mulla, “Priority-Flood: An Optimal Depression-Filling and Watershed-Labeling Algorithm for Digital Elevation Models” (2014), including the priority-queue flood from open boundaries. D8 is used only as a compact single-receiver diagnostic: it is not a claim that grid-direction bias is absent. Tarboton’s D-infinity method is a recognized alternative that distributes upslope area between two downslope cells, but this tranche does not need that additional routing model to establish basic depression, flat, determinism, and mass-conservation invariants.

References:
- [Barnes, Lehman & Mulla (2014), Priority-Flood](https://doi.org/10.1016/j.cageo.2013.04.024)
- [Tarboton (1997), D-infinity flow directions and upslope areas](https://doi.org/10.1029/96WR03137)
- [RichDEM Priority-Flood reference implementation](https://github.com/r-barnes/richdem/blob/master/include/richdem/depressions/Barnes2014.hpp)

## Authority and limitations

This result is evidence only. It does not edit or replace the authored semantic drainage graph, choose channel identities/fates, or authorize Minecraft placement. Treating every raster boundary as an outlet is a diagnostic boundary policy, not a universal lake/closed-basin model. A later integration must define the physical terrain window and boundary semantics explicitly, compare candidate flow paths/accumulation to the authored graph, and retain the established hydraulic benchmark set (Environment Agency/HEC-RAS Test C, accepted key 77, rejected key 287). The hydraulic benchmark suite remains the acceptance authority; these synthetic cases test only the routing primitive's algorithmic invariants.