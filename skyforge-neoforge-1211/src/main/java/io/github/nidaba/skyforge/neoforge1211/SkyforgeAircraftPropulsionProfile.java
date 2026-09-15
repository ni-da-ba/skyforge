package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Static source-backed propulsion topology requested by the aircraft target compiler. */
public record SkyforgeAircraftPropulsionProfile(
        String profileId,
        String targetProfileId,
        Bearing bearing,
        Propeller propeller,
        List<SkyforgeAircraftTargetProfile.RuntimeObligation> runtimeObligations) {
    public SkyforgeAircraftPropulsionProfile {
        profileId = requireText("profileId", profileId);
        targetProfileId = requireText("targetProfileId", targetProfileId);
        bearing = Objects.requireNonNull(bearing, "bearing");
        propeller = Objects.requireNonNull(propeller, "propeller");
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
    }

    public static SkyforgeAircraftPropulsionProfile retainedC11() {
        return new SkyforgeAircraftPropulsionProfile(
                "skyforge.propulsion.create_aeronautics_1_3_2.guild_utility.v1",
                SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                new Bearing(
                        "aeronautics:propeller_bearing",
                        Facing.WEST,
                        "released_source_verified_runtime_thrust_sign_unverified"),
                new Propeller(
                        "minecraft:spruce_planks",
                        Map.of(),
                        "simulated:white_symmetric_sail",
                        Axis.X,
                        1,
                        2,
                        8,
                        List.of(
                                new BladeOffset(1, 0),
                                new BladeOffset(2, 0),
                                new BladeOffset(-1, 0),
                                new BladeOffset(-2, 0),
                                new BladeOffset(0, 1),
                                new BladeOffset(0, 2),
                                new BladeOffset(0, -1),
                                new BladeOffset(0, -2))),
                List.of(
                        new SkyforgeAircraftTargetProfile.RuntimeObligation(
                                "bearing_neighbor_capture",
                                "prove bearing-facing neighbor and all sails enter one exact-stack bearing contraption"),
                        new SkyforgeAircraftTargetProfile.RuntimeObligation(
                                "reported_sail_power",
                                "verify runtime sail power equals eight without not-enough-sails rejection"),
                        new SkyforgeAircraftTargetProfile.RuntimeObligation(
                                "kinetic_network",
                                "verify shaft power, RPM sign, and stress margin only after Agent B gains platform authority"),
                        new SkyforgeAircraftTargetProfile.RuntimeObligation(
                                "thrust_direction",
                                "measure world-space force sign for target thrust-direction states"),
                        new SkyforgeAircraftTargetProfile.RuntimeObligation(
                                "nested_propeller_persistence",
                                "verify nested topology survives assembly lifecycle and save/reload")));
    }

    public record Bearing(String resourceId, Facing facing, String evidenceLevel) {
        public Bearing {
            resourceId = requireResourceId(resourceId);
            facing = Objects.requireNonNull(facing, "facing");
            evidenceLevel = requireText("evidenceLevel", evidenceLevel);
        }
    }

    public record Propeller(
            String hubResourceId,
            Map<String, String> hubBlockState,
            String sailResourceId,
            Axis sailAxis,
            int sailPowerPerBlock,
            int minimumSailPower,
            int targetSailPower,
            List<BladeOffset> bladeOffsets) {
        public Propeller {
            hubResourceId = requireResourceId(hubResourceId);
            hubBlockState = Map.copyOf(Objects.requireNonNull(hubBlockState, "hubBlockState"));
            sailResourceId = requireResourceId(sailResourceId);
            sailAxis = Objects.requireNonNull(sailAxis, "sailAxis");
            if (sailPowerPerBlock < 1 || minimumSailPower < 1 || targetSailPower < minimumSailPower) {
                throw new IllegalArgumentException("invalid propeller sail-power contract");
            }
            bladeOffsets = List.copyOf(Objects.requireNonNull(bladeOffsets, "bladeOffsets"));
            if (bladeOffsets.isEmpty() || bladeOffsets.stream().distinct().count() != bladeOffsets.size()) {
                throw new IllegalArgumentException("bladeOffsets must be non-empty and unique");
            }
            if (bladeOffsets.stream().anyMatch(offset -> offset.a() == 0 && offset.b() == 0)) {
                throw new IllegalArgumentException("bladeOffsets may not occupy the hub");
            }
        }
    }

    public record BladeOffset(int a, int b) {}

    public enum Axis { X, Y, Z }

    public enum Facing {
        WEST(-1, 0, 0, Axis.X),
        EAST(1, 0, 0, Axis.X),
        DOWN(0, -1, 0, Axis.Y),
        UP(0, 1, 0, Axis.Y),
        NORTH(0, 0, -1, Axis.Z),
        SOUTH(0, 0, 1, Axis.Z);

        private final int dx;
        private final int dy;
        private final int dz;
        private final Axis axis;

        Facing(int dx, int dy, int dz, Axis axis) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.axis = axis;
        }

        public int dx() { return dx; }
        public int dy() { return dy; }
        public int dz() { return dz; }
        public Axis axis() { return axis; }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireResourceId(String value) {
        String resource = requireText("resourceId", value);
        if (!resource.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid resourceId: " + value);
        }
        return resource;
    }
}
