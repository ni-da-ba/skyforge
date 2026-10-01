package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Retained Create 6.0.10 / Simulated v0.12 governed powertrain lowering contract. */
public record SkyforgeAircraftPowertrainProfile(
        String profileId,
        int portableEngineSourceRpm,
        int governorTargetRpm,
        List<Integer> laterGovernorRpmPoints,
        AircraftBlockspaceIR.LatticePoint existingPropellerBearingCoordinate,
        List<Placement> placements,
        GlueDomain powerplantGlueDomain,
        List<String> sourceEvidence,
        List<RuntimeObligation> runtimeObligations) {

    public SkyforgeAircraftPowertrainProfile {
        profileId = requireText("profileId", profileId);
        if (portableEngineSourceRpm != 32) throw new IllegalArgumentException("v0.12 requires 32-RPM Portable Engines");
        if (governorTargetRpm != 128) throw new IllegalArgumentException("bounded v0.12 accepts only the first 128-RPM governor point");
        laterGovernorRpmPoints = List.copyOf(Objects.requireNonNull(laterGovernorRpmPoints, "laterGovernorRpmPoints"));
        if (!laterGovernorRpmPoints.equals(List.of(160, 192, 224, 256))) {
            throw new IllegalArgumentException("v0.12 later governor ladder must be 160/192/224/256 RPM");
        }
        existingPropellerBearingCoordinate = Objects.requireNonNull(existingPropellerBearingCoordinate, "existingPropellerBearingCoordinate");
        placements = List.copyOf(Objects.requireNonNull(placements, "placements"));
        if (placements.isEmpty()) throw new IllegalArgumentException("v0.12 requires powertrain placements");
        Set<String> roles = new HashSet<>();
        Set<AircraftBlockspaceIR.LatticePoint> points = new HashSet<>();
        for (Placement placement : placements) {
            if (!roles.add(placement.role())) throw new IllegalArgumentException("duplicate powertrain role: " + placement.role());
            if (!points.add(placement.point())) throw new IllegalArgumentException("duplicate powertrain coordinate: " + placement.point());
        }
        Set<String> requiredRoles = Set.of("engine_port", "engine_starboard", "governor", "governor_cog", "prop_shaft");
        if (!roles.containsAll(requiredRoles)) throw new IllegalArgumentException("v0.12 missing required powertrain roles");
        Map<String, Placement> byRole = placements.stream().collect(java.util.stream.Collectors.toMap(Placement::role, value -> value));
        requirePlacement(byRole.get("engine_port"), Mode.ADD, "simulated:red_portable_engine", Map.of("facing", "south", "lit", "false"), null);
        requirePlacement(byRole.get("engine_starboard"), Mode.ADD, "simulated:red_portable_engine", Map.of("facing", "north", "lit", "false"), null);
        requirePlacement(byRole.get("governor"), Mode.REPLACE, "create:rotation_speed_controller", Map.of("axis", "z"), new ReplaceExpectation("airframe_structure", "minecraft:spruce_planks"));
        requirePlacement(byRole.get("governor_cog"), Mode.ADD, "create:large_cogwheel", Map.of("axis", "x"), null);
        requirePlacement(byRole.get("prop_shaft"), Mode.ADD, "create:shaft", Map.of("axis", "x"), null);
        powerplantGlueDomain = Objects.requireNonNull(powerplantGlueDomain, "powerplantGlueDomain");
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        Set<String> ids = new HashSet<>();
        for (RuntimeObligation obligation : runtimeObligations) {
            if (!ids.add(obligation.id())) throw new IllegalArgumentException("duplicate runtime obligation id: " + obligation.id());
        }
        Set<String> requiredObligations = Set.of(
                "powertrain_primary_sable_recapture_probe",
                "portable_engine_shared_network_probe",
                "governor_128_rpm_probe",
                "kinetic_stress_margin_probe",
                "propeller_thrust_sign_magnitude_probe");
        if (!ids.containsAll(requiredObligations)) throw new IllegalArgumentException("v0.12 profile missing required runtime obligations");
    }

    public static SkyforgeAircraftPowertrainProfile retainedC11() {
        return new SkyforgeAircraftPowertrainProfile(
                "skyforge.powertrain.create_6_0_10_simulated.guild_utility.v0_12",
                32,
                128,
                List.of(160, 192, 224, 256),
                point(0, 3, 0),
                List.of(
                        placement("engine_port", Mode.ADD, 2, 2, -1, "simulated:red_portable_engine", Map.of("facing", "south", "lit", "false"), null,
                                "PortableEngineBlock shaft exists only on HORIZONTAL_FACING; source speed range is exactly 32 RPM"),
                        placement("engine_starboard", Mode.ADD, 2, 2, 1, "simulated:red_portable_engine", Map.of("facing", "north", "lit", "false"), null,
                                "PortableEngineBlock shaft exists only on HORIZONTAL_FACING; source speed range is exactly 32 RPM"),
                        placement("governor", Mode.REPLACE, 2, 2, 0, "create:rotation_speed_controller", Map.of("axis", "z"),
                                new ReplaceExpectation("airframe_structure", "minecraft:spruce_planks"),
                                "SpeedControllerBlock is a horizontal-axis kinetic block; large cog directly above mediates governed output"),
                        placement("governor_cog", Mode.ADD, 2, 3, 0, "create:large_cogwheel", Map.of("axis", "x"), null,
                                "SpeedControllerBlockEntity requires dedicated large cog above with horizontal axis perpendicular to controller axis"),
                        placement("prop_shaft", Mode.ADD, 1, 3, 0, "create:shaft", Map.of("axis", "x"), null,
                                "X-axis shaft directly couples large cog output to WEST-facing X-axis Propeller Bearing")),
                new GlueDomain("powerplant", point(1, 2, -1), point(2, 3, 1)),
                List.of(
                        "Simulated PortableEngineBlock.getSpeedRange returns Couple.create(32,32)",
                        "Skyforge issue #237 accepted two real Portable Engines on one shared Create shaft inside a Sable ServerSubLevel",
                        "Create 6.0.10 SpeedControllerBlock requires a dedicated large cog directly above for governed transfer",
                        "Create 6.0.10 SpeedControllerBlockEntity targetSpeed is bounded by server maxRotationSpeed and rewires kinetics through updateTargetRotation",
                        "C12 issue #239 defines the first governor ladder as 128/160/192/224/256 RPM and requires live stress proof rather than arithmetic acceptance"),
                List.of(
                        new RuntimeObligation("powertrain_primary_sable_recapture_probe", "apply the v0.12 replacement/addition patch plus powerplant glue, reassemble, verify the intended primary plus nested payload transfers into one Sable sublevel, and remeasure mass/COM"),
                        new RuntimeObligation("portable_engine_shared_network_probe", "fuel/activate both relocated Portable Engines and verify both are non-virtual 32-RPM sources in the intended shared kinetic network"),
                        new RuntimeObligation("governor_128_rpm_probe", "set the relocated Rotation Speed Controller to 128 RPM and verify the relocated Propeller Bearing receives the expected nonzero signed speed"),
                        new RuntimeObligation("kinetic_stress_margin_probe", "read the real Create KineticNetwork capacity and stress at 128 RPM and require finite capacity >= stress with positive margin"),
                        new RuntimeObligation("propeller_thrust_sign_magnitude_probe", "with child sail power 8 and live 128-RPM drive, measure PropellerBearingBlockEntity.getThrust sign and magnitude without tuning analytical constants to fit it")));
    }

    private static void requirePlacement(Placement placement, Mode mode, String resourceId, Map<String, String> blockState, ReplaceExpectation replaces) {
        if (placement == null || placement.mode() != mode || !resourceId.equals(placement.resourceId())
                || !blockState.equals(placement.blockState()) || !Objects.equals(replaces, placement.replaces())) {
            throw new IllegalArgumentException("v0.12 source-backed placement contract mismatch for " + (placement == null ? "missing role" : placement.role()));
        }
    }
    private static Placement placement(String role, Mode mode, int x, int y, int z, String resourceId, Map<String, String> blockState, ReplaceExpectation replaces, String sourceContract) {
        return new Placement(role, mode, point(x, y, z), resourceId, blockState, replaces, sourceContract);
    }
    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) { return new AircraftBlockspaceIR.LatticePoint(x, y, z); }

    public enum Mode { ADD, REPLACE }
    public record Placement(String role, Mode mode, AircraftBlockspaceIR.LatticePoint point, String resourceId, Map<String, String> blockState, ReplaceExpectation replaces, String sourceContract) {
        public Placement {
            role = requireText("placement.role", role);
            mode = Objects.requireNonNull(mode, "mode");
            point = Objects.requireNonNull(point, "point");
            resourceId = requireNamespaced("placement.resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            if (mode == Mode.REPLACE && replaces == null) throw new IllegalArgumentException("replacement placement requires replaces contract");
            if (mode == Mode.ADD && replaces != null) throw new IllegalArgumentException("addition placement must not declare replaces contract");
            sourceContract = requireText("placement.sourceContract", sourceContract);
        }
    }
    public record ReplaceExpectation(String kind, String resourceId) {
        public ReplaceExpectation {
            kind = requireText("replace.kind", kind);
            resourceId = requireNamespaced("replace.resourceId", resourceId);
        }
    }
    public record GlueDomain(String name, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
    }
    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }
    private static String requireNamespaced(String property, String value) {
        value = requireText(property, value);
        if (!value.contains(":")) throw new IllegalArgumentException(property + " must be namespaced");
        return value;
    }
    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
