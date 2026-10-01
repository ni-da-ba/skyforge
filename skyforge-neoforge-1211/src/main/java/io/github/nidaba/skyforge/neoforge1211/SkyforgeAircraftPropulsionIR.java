package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic static propulsion realization. Runtime mechanics remain unqualified. */
public record SkyforgeAircraftPropulsionIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceAssemblyDigestSha256,
        String sourceTargetPreflightDigestSha256,
        String propulsionProfileId,
        BearingPlacement bearing,
        HubPlacement hub,
        List<SailPlacement> sails,
        Geometry geometry,
        TopologyChecks topologyChecks,
        List<AircraftBlockspaceIR.LatticePoint> sourceAssemblyCollisions,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-propulsion-realizer-1";

    public SkyforgeAircraftPropulsionIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported propulsion IR schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported propulsion compiler: " + compilerVersion);
        }
        sourceAssemblyDigestSha256 = requireSha256(sourceAssemblyDigestSha256);
        sourceTargetPreflightDigestSha256 = requireSha256(sourceTargetPreflightDigestSha256);
        propulsionProfileId = requireText("propulsionProfileId", propulsionProfileId);
        bearing = Objects.requireNonNull(bearing, "bearing");
        hub = Objects.requireNonNull(hub, "hub");
        ArrayList<SailPlacement> orderedSails = new ArrayList<>(Objects.requireNonNull(sails, "sails"));
        orderedSails.sort(Comparator.comparing(SailPlacement::point));
        sails = List.copyOf(orderedSails);
        geometry = Objects.requireNonNull(geometry, "geometry");
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
        ArrayList<AircraftBlockspaceIR.LatticePoint> collisions =
                new ArrayList<>(Objects.requireNonNull(sourceAssemblyCollisions, "sourceAssemblyCollisions"));
        collisions.sort(Comparator.naturalOrder());
        sourceAssemblyCollisions = List.copyOf(collisions);
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        metrics = Objects.requireNonNull(metrics, "metrics");
        readiness = Objects.requireNonNull(readiness, "readiness");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        token(canonical, Integer.toString(schemaVersion));
        token(canonical, assetId);
        token(canonical, compilerVersion);
        token(canonical, sourceAssemblyDigestSha256);
        token(canonical, sourceTargetPreflightDigestSha256);
        token(canonical, propulsionProfileId);
        token(canonical, bearing.toString());
        token(canonical, hub.toString());
        sails.forEach(value -> token(canonical, value.toString()));
        token(canonical, geometry.toString());
        token(canonical, topologyChecks.toString());
        sourceAssemblyCollisions.forEach(value -> token(canonical, value.toString()));
        runtimeObligations.forEach(value -> token(canonical, value.toString()));
        token(canonical, metrics.toString());
        token(canonical, readiness.toString());
        token(canonical, validation.toString());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record BearingPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            String evidenceLevel) {
        public BearingPlacement {
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            evidenceLevel = requireText("evidenceLevel", evidenceLevel);
        }
    }

    public record HubPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            String assemblyContract) {
        public HubPlacement {
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            assemblyContract = requireText("assemblyContract", assemblyContract);
        }
    }

    public record SailPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            int sailPower) {
        public SailPlacement {
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            if (sailPower < 1) {
                throw new IllegalArgumentException("sailPower must be positive");
            }
        }
    }

    public record Geometry(
            SkyforgeAircraftPropulsionProfile.Axis bearingAxis,
            SkyforgeAircraftPropulsionProfile.Facing bearingFacing,
            AircraftBlockspaceIR.LatticePoint hubCoordinate,
            List<SkyforgeAircraftPropulsionProfile.BladeOffset> bladeOffsets,
            int transverseFirstMomentA,
            int transverseFirstMomentB,
            List<Integer> radiiSquared,
            int sailPower,
            int minimumSailPower,
            int targetSailPower) {
        public Geometry {
            bearingAxis = Objects.requireNonNull(bearingAxis, "bearingAxis");
            bearingFacing = Objects.requireNonNull(bearingFacing, "bearingFacing");
            hubCoordinate = Objects.requireNonNull(hubCoordinate, "hubCoordinate");
            bladeOffsets = List.copyOf(Objects.requireNonNull(bladeOffsets, "bladeOffsets"));
            radiiSquared = List.copyOf(Objects.requireNonNull(radiiSquared, "radiiSquared"));
        }
    }

    public record TopologyChecks(
            boolean bearingCoordinateFree,
            boolean hubCoordinateFree,
            boolean generatedCoordinatesUnique,
            boolean noSourceAssemblyCollision,
            boolean allSailsCoplanarNormalToBearingAxis,
            boolean bladeGraphConnectedToHub,
            boolean zeroTransverseFirstMoment,
            boolean centralSymmetry,
            boolean minimumSailPowerSatisfied,
            boolean targetSailPowerSatisfied) {
        public boolean passed() {
            return bearingCoordinateFree
                    && hubCoordinateFree
                    && generatedCoordinatesUnique
                    && noSourceAssemblyCollision
                    && allSailsCoplanarNormalToBearingAxis
                    && bladeGraphConnectedToHub
                    && zeroTransverseFirstMoment
                    && centralSymmetry
                    && minimumSailPowerSatisfied
                    && targetSailPowerSatisfied;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {}
    public record Metrics(
            int generatedPlacementCount,
            int propellerSailCount,
            int propellerSailPower,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount) {}
    public record Readiness(
            boolean propulsionStaticTopologyPassed,
            boolean propulsionRuntimeQualified,
            boolean schematicEmissionReady,
            boolean flightQualified,
            List<String> resolvedUpstreamBlockers,
            List<String> blockers) {
        public Readiness {
            resolvedUpstreamBlockers = List.copyOf(
                    Objects.requireNonNull(resolvedUpstreamBlockers, "resolvedUpstreamBlockers"));
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }
    }
    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static void token(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireSha256(String value) {
        String sha = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return sha;
    }
}
