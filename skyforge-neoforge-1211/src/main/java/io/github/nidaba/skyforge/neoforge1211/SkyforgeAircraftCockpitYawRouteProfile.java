package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Retained source-backed profile for deterministic cockpit-to-rudder Create routing. */
public record SkyforgeAircraftCockpitYawRouteProfile(
        String profileId,
        Map<String, String> resources,
        List<WheelCandidate> pilotWheelCandidates,
        RouteBounds routeBounds,
        int turnPenalty,
        int pilotDistancePenalty,
        int expectedSwivelExtraCogSignForPositiveWheelCommand,
        int maxGlueSelectionDimensionBlocks,
        List<AircraftBlockspaceIR.LatticePoint> forbiddenCoordinates,
        String createSourceCommit,
        List<String> sourceEvidence,
        List<RuntimeObligation> runtimeObligations) {
    public static final String PROFILE_ID = "skyforge.cockpit_yaw_route.create_1_21_1.v0_18";
    public static final String CREATE_SOURCE_COMMIT = "ac0c444d9828da3453ae8cc65338e8de063286fb";
    public static final Map<String, String> EXACT_RESOURCES = Map.of(
            "steeringWheel", "simulated:steering_wheel",
            "gearbox", "create:gearbox",
            "shaft", "create:shaft",
            "cogwheel", "create:cogwheel");

    public SkyforgeAircraftCockpitYawRouteProfile {
        profileId = requireText("profileId", profileId);
        if (!PROFILE_ID.equals(profileId)) throw new IllegalArgumentException("unsupported cockpit yaw-route profile: " + profileId);
        resources = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(resources, "resources")));
        if (!EXACT_RESOURCES.equals(resources)) throw new IllegalArgumentException("exact cockpit route resources changed");
        ArrayList<WheelCandidate> candidates = new ArrayList<>(Objects.requireNonNull(pilotWheelCandidates, "pilotWheelCandidates"));
        candidates.sort(Comparator.comparing(WheelCandidate::name));
        pilotWheelCandidates = List.copyOf(candidates);
        if (pilotWheelCandidates.size() != 2 || !pilotWheelCandidates.stream().map(WheelCandidate::name).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("port_adjacent_floor", "starboard_adjacent_floor"))) {
            throw new IllegalArgumentException("retained cockpit Steering Wheel candidate set changed");
        }
        routeBounds = Objects.requireNonNull(routeBounds, "routeBounds");
        if (!routeBounds.equals(new RouteBounds(3, 18, -3, 3))) throw new IllegalArgumentException("retained cockpit route bounds changed");
        if (turnPenalty != 4 || pilotDistancePenalty != 3) throw new IllegalArgumentException("retained cockpit route scoring changed");
        if (expectedSwivelExtraCogSignForPositiveWheelCommand != -1) {
            throw new IllegalArgumentException("retained positive-command Swivel sign changed");
        }
        if (maxGlueSelectionDimensionBlocks != 24) throw new IllegalArgumentException("retained Create glue bound changed");
        ArrayList<AircraftBlockspaceIR.LatticePoint> forbidden = new ArrayList<>(Objects.requireNonNull(forbiddenCoordinates, "forbiddenCoordinates"));
        forbidden.sort(Comparator.naturalOrder());
        if (new HashSet<>(forbidden).size() != forbidden.size()) throw new IllegalArgumentException("forbiddenCoordinates contains duplicates");
        forbiddenCoordinates = List.copyOf(forbidden);
        createSourceCommit = requireText("createSourceCommit", createSourceCommit);
        if (!CREATE_SOURCE_COMMIT.equals(createSourceCommit)) throw new IllegalArgumentException("exact Create source provenance changed");
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        if (sourceEvidence.stream().noneMatch(value -> value.contains(CREATE_SOURCE_COMMIT) && value.contains("RotationPropagator"))) {
            throw new IllegalArgumentException("cockpit route profile must retain exact Create RotationPropagator source evidence");
        }
        if (sourceEvidence.stream().noneMatch(value -> value.contains("SteeringWheelBlock") && value.contains("floor"))) {
            throw new IllegalArgumentException("cockpit route profile must retain Steering Wheel shaft/sign source evidence");
        }
        ArrayList<RuntimeObligation> obligations = new ArrayList<>(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        obligations.sort(Comparator.comparing(RuntimeObligation::id));
        runtimeObligations = List.copyOf(obligations);
        Set<String> obligationIds = runtimeObligations.stream().map(RuntimeObligation::id).collect(java.util.stream.Collectors.toSet());
        if (!obligationIds.equals(Set.of(
                "cockpit_route_primary_recapture_probe",
                "cockpit_steering_network_probe",
                "cockpit_steering_command_return_probe",
                "post_route_mass_com_probe"))) {
            throw new IllegalArgumentException("cockpit route runtime-obligation set changed");
        }
    }

    public static SkyforgeAircraftCockpitYawRouteProfile retainedC11() {
        return retainedC11WithForbidden(List.of());
    }

    static SkyforgeAircraftCockpitYawRouteProfile retainedC11WithForbidden(List<AircraftBlockspaceIR.LatticePoint> forbidden) {
        return new SkyforgeAircraftCockpitYawRouteProfile(
                PROFILE_ID,
                EXACT_RESOURCES,
                List.of(
                        new WheelCandidate("starboard_adjacent_floor", new Offset(0, 0, 1),
                                Map.of("facing", "north", "on_floor", "true", "waterlogged", "false"), 0),
                        new WheelCandidate("port_adjacent_floor", new Offset(0, 0, -1),
                                Map.of("facing", "south", "on_floor", "true", "waterlogged", "false"), 0)),
                new RouteBounds(3, 18, -3, 3),
                4,
                3,
                -1,
                24,
                forbidden,
                CREATE_SOURCE_COMMIT,
                List.of(
                        "Simulated SteeringWheelBlock exact source 50443d00afa06e0982b45f40cd686f7ecf978132 uses Y-axis kinetics; a floor-mounted wheel exposes its shaft downward and north/west floor orientation negates positive logical source rotation.",
                        "Create 6.0.10 exact source " + CREATE_SOURCE_COMMIT + " RotationPropagator.getAxisModifier applies gearbox sign from source face and output direction; route sign is searched from that source-pinned equation rather than a hand-maintained route table.",
                        "Create 6.0.10 exact route resources are create:shaft, create:gearbox and create:cogwheel; AIRCRAFT-PROD-013 supplies simulated:steering_wheel authority."),
                List.of(
                        new RuntimeObligation("cockpit_route_primary_recapture_probe", "assemble the route-modified exact-stack aircraft and require routed parent-main cells plus accepted propeller/rudder payloads to transfer into the primary Sable body"),
                        new RuntimeObligation("cockpit_steering_network_probe", "command the pilot-adjacent Steering Wheel and measure 16-RPM magnitude plus compiler-predicted sign at the aft drive cog and Swivel hidden extra cog"),
                        new RuntimeObligation("cockpit_steering_command_return_probe", "command bounded rudder deflection through the complete cockpit route, hold, then explicitly command neutral and verify physical return"),
                        new RuntimeObligation("post_route_mass_com_probe", "measure Sable mass and center of mass after all production-route parent blocks are captured")));
    }

    public record WheelCandidate(String name, Offset offsetFromPilotSeat, Map<String, String> blockState, int preferencePenalty) {
        public WheelCandidate {
            name = requireText("wheelCandidate.name", name);
            offsetFromPilotSeat = Objects.requireNonNull(offsetFromPilotSeat, "offsetFromPilotSeat");
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            if (preferencePenalty < 0) throw new IllegalArgumentException("wheel candidate preferencePenalty must be nonnegative");
            if (!"true".equals(blockState.get("on_floor")) || !"false".equals(blockState.get("waterlogged"))) {
                throw new IllegalArgumentException("production cockpit Steering Wheel candidates must be dry and floor-mounted");
            }
        }
    }

    public record Offset(int dx, int dy, int dz) {}

    public record RouteBounds(int minX, int maxX, int minZ, int maxZ) {
        public RouteBounds {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("route bounds are inverted");
        }
        boolean contains(AircraftBlockspaceIR.LatticePoint p) {
            return minX <= p.x() && p.x() <= maxX && minZ <= p.z() && p.z() <= maxZ;
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
