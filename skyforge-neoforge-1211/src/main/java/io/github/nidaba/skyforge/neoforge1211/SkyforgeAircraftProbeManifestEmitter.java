package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Combines accepted static aircraft placements into one deterministic probe manifest. */
public final class SkyforgeAircraftProbeManifestEmitter {
    public SkyforgeAircraftProbeManifestIR emit(
            SkyforgeAircraftTargetPreflightIR targetPreflight,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftTailLoweringIR tailLowering,
            SkyforgeAircraftPilotStationIR pilotStation,
            String manifestAssetId,
            SkyforgeAircraftProbeManifestProfile profile) {
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        Objects.requireNonNull(propulsion, "propulsion");
        Objects.requireNonNull(tailLowering, "tailLowering");
        Objects.requireNonNull(pilotStation, "pilotStation");
        Objects.requireNonNull(profile, "profile");
        if (!targetPreflight.validation().passed()) throw new IllegalArgumentException("refusing manifest emission from invalid target preflight");
        if (!propulsion.validation().passed()) throw new IllegalArgumentException("refusing manifest emission from invalid propulsion artifact");
        if (!tailLowering.validation().passed()) throw new IllegalArgumentException("refusing manifest emission from invalid tail artifact");
        if (!pilotStation.validation().passed() || !pilotStation.readiness().probeSchematicEmissionReady()) {
            throw new IllegalArgumentException("pilot station has not cleared static probe emission");
        }
        if (!propulsion.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("propulsion artifact does not match target-preflight digest");
        }
        if (!tailLowering.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("tail artifact does not match target-preflight digest");
        }
        if (!pilotStation.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("pilot artifact does not match target-preflight digest");
        }
        if (!pilotStation.sourcePropulsionDigestSha256().equals(propulsion.sha256())) {
            throw new IllegalArgumentException("pilot artifact does not match propulsion digest");
        }
        if (!pilotStation.sourceTailLoweringDigestSha256().equals(tailLowering.sha256())) {
            throw new IllegalArgumentException("pilot artifact does not match tail-lowering digest");
        }
        if (!profile.targetProfileId().equals(targetPreflight.targetProfileId())) {
            throw new IllegalArgumentException("probe-manifest profile targetProfileId does not match target preflight");
        }
        if (tailLowering.resolvedAerodynamicPlacements().stream()
                .anyMatch(placement -> !profile.airframeAerodynamicProviderId().equals(placement.providerId()))) {
            throw new IllegalArgumentException("probe-manifest aerodynamic provider does not match tail placements");
        }

        ArrayList<SkyforgeAircraftProbeManifestIR.Placement> placements = new ArrayList<>();
        for (SkyforgeAircraftTargetPreflightIR.Placement placement : targetPreflight.placements()) {
            if (profile.airframeAerodynamicProviderId().equals(placement.providerId())) continue;
            if (!"none".equals(placement.stateRule())) {
                throw new IllegalArgumentException("structural placement lacks a closed block-state rule at " + placement.point());
            }
            placements.add(placement(
                    "airframe_structure", placement.point(), placement.resourceId(), Map.of(), "target_preflight_prod_003"));
        }
        for (SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement placement : tailLowering.resolvedAerodynamicPlacements()) {
            placements.add(placement(
                    "airframe_aerodynamic_surface", placement.point(), placement.resourceId(), placement.blockState(), "tail_lowering_prod_006"));
        }
        placements.add(placement(
                "propeller_bearing", propulsion.bearing().point(), propulsion.bearing().resourceId(), propulsion.bearing().blockState(), "propulsion_prod_004"));
        placements.add(placement(
                "propeller_hub", propulsion.hub().point(), propulsion.hub().resourceId(), propulsion.hub().blockState(), "propulsion_prod_004"));
        for (SkyforgeAircraftPropulsionIR.SailPlacement placement : propulsion.sails()) {
            placements.add(placement(
                    "propeller_sail", placement.point(), placement.resourceId(), placement.blockState(), "propulsion_prod_004"));
        }
        SkyforgeAircraftPilotStationIR.Placement pilot = pilotStation.placement();
        placements.add(placement(
                "pilot_occupancy_station", pilot.point(), pilot.resourceId(), pilot.blockState(), "pilot_station_prod_007"));
        placements.sort(java.util.Comparator
                .comparing(SkyforgeAircraftProbeManifestIR.Placement::point)
                .thenComparing(SkyforgeAircraftProbeManifestIR.Placement::kind)
                .thenComparing(SkyforgeAircraftProbeManifestIR.Placement::resourceId));

        Map<AircraftBlockspaceIR.LatticePoint, Integer> coordinateCounts = new HashMap<>();
        TreeSet<String> resourceIds = new TreeSet<>();
        TreeMap<String, Integer> kindCounts = new TreeMap<>();
        boolean resourcesNamespaced = true;
        boolean blockStatesExplicit = true;
        for (SkyforgeAircraftProbeManifestIR.Placement placement : placements) {
            coordinateCounts.merge(placement.point(), 1, Integer::sum);
            resourceIds.add(placement.resourceId());
            kindCounts.merge(placement.kind(), 1, Integer::sum);
            resourcesNamespaced &= placement.resourceId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
            blockStatesExplicit &= placement.blockState() != null;
        }
        List<AircraftBlockspaceIR.LatticePoint> duplicates = coordinateCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        int minX = placements.stream().mapToInt(value -> value.point().x()).min().orElseThrow();
        int minY = placements.stream().mapToInt(value -> value.point().y()).min().orElseThrow();
        int minZ = placements.stream().mapToInt(value -> value.point().z()).min().orElseThrow();
        int maxX = placements.stream().mapToInt(value -> value.point().x()).max().orElseThrow();
        int maxY = placements.stream().mapToInt(value -> value.point().y()).max().orElseThrow();
        int maxZ = placements.stream().mapToInt(value -> value.point().z()).max().orElseThrow();
        SkyforgeAircraftProbeManifestIR.Bounds bounds = new SkyforgeAircraftProbeManifestIR.Bounds(
                new AircraftBlockspaceIR.LatticePoint(minX, minY, minZ),
                new AircraftBlockspaceIR.LatticePoint(maxX, maxY, maxZ),
                new AircraftBlockspaceIR.LatticePoint(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));

        int propulsionPlacementCount = kindCounts.getOrDefault("propeller_bearing", 0)
                + kindCounts.getOrDefault("propeller_hub", 0)
                + kindCounts.getOrDefault("propeller_sail", 0);
        SkyforgeAircraftProbeManifestIR.ValidationChecks checks = new SkyforgeAircraftProbeManifestIR.ValidationChecks(
                duplicates.isEmpty(),
                resourcesNamespaced,
                blockStatesExplicit,
                kindCounts.getOrDefault("airframe_aerodynamic_surface", 0) == tailLowering.metrics().v07AerodynamicPlacementCount(),
                propulsionPlacementCount == propulsion.metrics().generatedPlacementCount(),
                kindCounts.getOrDefault("pilot_occupancy_station", 0) == 1);
        boolean staticReady = checks.passed();

        return new SkyforgeAircraftProbeManifestIR(
                SkyforgeAircraftProbeManifestIR.SCHEMA_VERSION,
                manifestAssetId,
                SkyforgeAircraftProbeManifestIR.COMPILER_VERSION,
                targetPreflight.sha256(),
                propulsion.sha256(),
                tailLowering.sha256(),
                pilotStation.sha256(),
                profile.profileId(),
                profile.originContract(),
                placements,
                bounds,
                List.copyOf(resourceIds),
                kindCounts,
                duplicates,
                checks,
                new SkyforgeAircraftProbeManifestIR.Readiness(
                        staticReady,
                        staticReady,
                        false,
                        false,
                        false,
                        staticReady ? List.of() : List.of("probe_manifest_static_validation_failed"),
                        List.of(
                                "physics_assembler_placement_unresolved",
                                "airframe_adhesion_graph_unresolved",
                                "control_surface_child_body_topology_unresolved"),
                        pilotStation.readiness().runtimeBlockers()),
                new SkyforgeAircraftProbeManifestIR.Validation(
                        staticReady,
                        "coordinate_unique_exact_resource_and_blockstate_probe_placement_manifest",
                        List.of(
                                "Physics Assembler capture of the intended airframe",
                                "Super Glue or other adhesion completeness",
                                "nested propeller capture",
                                "moving control-surface topology",
                                "runtime force sign or magnitude",
                                "pilot occupancy on a Sable body",
                                "control binding or authority",
                                "save/reload persistence",
                                "flight qualification")));
    }

    private static SkyforgeAircraftProbeManifestIR.Placement placement(
            String kind,
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            String sourceLayer) {
        return new SkyforgeAircraftProbeManifestIR.Placement(kind, point, resourceId, blockState, sourceLayer);
    }
}
