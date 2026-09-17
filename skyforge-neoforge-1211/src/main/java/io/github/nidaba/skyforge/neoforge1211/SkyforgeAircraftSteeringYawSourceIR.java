package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic AIRCRAFT-PROD-013 static Steering Wheel yaw-source IR. */
public record SkyforgeAircraftSteeringYawSourceIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceYawControlDigestSha256,
        String sourceRudderActuationAuthorityDigestSha256,
        String sourceRudderNeutralReturnAuthorityDigestSha256,
        String profileId,
        AircraftBlockspaceIR.LatticePoint swivelBearingCoordinate,
        AircraftBlockspaceIR.LatticePoint driveCogCoordinate,
        AircraftBlockspaceIR.LatticePoint steeringWheelCoordinate,
        String steeringWheelResource,
        Map<String, String> steeringWheelBlockState,
        int expectedSteeringWheelRpmMagnitude,
        double wheelCommandDegrees,
        String platformCapabilityId,
        String simulatedSourceRepository,
        String simulatedSourceCommit,
        List<String> sourceEvidence,
        TopologyChecks topologyChecks,
        List<RuntimeObligation> runtimeObligations,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-steering-yaw-source-lowerer-1";

    public SkyforgeAircraftSteeringYawSourceIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported Steering Wheel yaw-source IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported Steering Wheel yaw-source compiler: " + compilerVersion);
        sourceYawControlDigestSha256 = requireSha256(sourceYawControlDigestSha256);
        sourceRudderActuationAuthorityDigestSha256 = requireSha256(sourceRudderActuationAuthorityDigestSha256);
        sourceRudderNeutralReturnAuthorityDigestSha256 = requireSha256(sourceRudderNeutralReturnAuthorityDigestSha256);
        profileId = requireText("profileId", profileId);
        swivelBearingCoordinate = Objects.requireNonNull(swivelBearingCoordinate, "swivelBearingCoordinate");
        driveCogCoordinate = Objects.requireNonNull(driveCogCoordinate, "driveCogCoordinate");
        steeringWheelCoordinate = Objects.requireNonNull(steeringWheelCoordinate, "steeringWheelCoordinate");
        steeringWheelResource = requireText("steeringWheelResource", steeringWheelResource);
        steeringWheelBlockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(steeringWheelBlockState, "steeringWheelBlockState")));
        platformCapabilityId = requireText("platformCapabilityId", platformCapabilityId);
        simulatedSourceRepository = requireText("simulatedSourceRepository", simulatedSourceRepository);
        simulatedSourceCommit = requireText("simulatedSourceCommit", simulatedSourceCommit);
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
        ArrayList<RuntimeObligation> obligations = new ArrayList<>(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        obligations.sort(Comparator.comparing(RuntimeObligation::id));
        runtimeObligations = List.copyOf(obligations);
        readiness = Objects.requireNonNull(readiness, "readiness");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        token(canonical, Integer.toString(schemaVersion));
        token(canonical, assetId);
        token(canonical, compilerVersion);
        token(canonical, sourceYawControlDigestSha256);
        token(canonical, sourceRudderActuationAuthorityDigestSha256);
        token(canonical, sourceRudderNeutralReturnAuthorityDigestSha256);
        token(canonical, profileId);
        token(canonical, swivelBearingCoordinate.toString());
        token(canonical, driveCogCoordinate.toString());
        token(canonical, steeringWheelCoordinate.toString());
        token(canonical, steeringWheelResource);
        new TreeMap<>(steeringWheelBlockState).forEach((key, value) -> {
            token(canonical, key);
            token(canonical, value);
        });
        token(canonical, Integer.toString(expectedSteeringWheelRpmMagnitude));
        token(canonical, Double.toString(wheelCommandDegrees));
        token(canonical, platformCapabilityId);
        token(canonical, simulatedSourceRepository);
        token(canonical, simulatedSourceCommit);
        sourceEvidence.forEach(value -> token(canonical, value));
        token(canonical, topologyChecks.toString());
        runtimeObligations.forEach(value -> token(canonical, value.toString()));
        token(canonical, readiness.toString());
        token(canonical, validation.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record TopologyChecks(
            boolean sourceAuthorityMatchesYawDigest,
            boolean actuationAuthorityDigestCurrent,
            boolean neutralReturnAuthorityDigestCurrent,
            boolean acceptedTailDriveCogRetained,
            boolean steeringWheelDirectlyBelowDriveCog,
            boolean steeringWheelResourceExact,
            boolean steeringWheelStateExact,
            boolean yAxisUpwardShaftContract,
            boolean steeringWheelRpmMatchesPlatform,
            boolean boundedCommandReferenceRetained,
            boolean platformAuthorityReferenced,
            boolean exactSimulatedSourcePinned) {
        public boolean passed() {
            return sourceAuthorityMatchesYawDigest
                    && actuationAuthorityDigestCurrent
                    && neutralReturnAuthorityDigestCurrent
                    && acceptedTailDriveCogRetained
                    && steeringWheelDirectlyBelowDriveCog
                    && steeringWheelResourceExact
                    && steeringWheelStateExact
                    && yAxisUpwardShaftContract
                    && steeringWheelRpmMatchesPlatform
                    && boundedCommandReferenceRetained
                    && platformAuthorityReferenced
                    && exactSimulatedSourcePinned;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
            status = requireText("runtimeObligation.status", status);
        }
    }

    public record Readiness(
            boolean steeringWheelSourceStaticTopologyPassed,
            boolean platformSteeringWheelRuntimeAuthorityReferenced,
            boolean aircraftTailSourceRuntimeReady,
            boolean cockpitRoutingReady,
            boolean pilotInteractionBindingReady,
            boolean flightQualified,
            List<String> runtimeBlockers) {
        public Readiness {
            ArrayList<String> blockers = new ArrayList<>(Objects.requireNonNull(runtimeBlockers, "runtimeBlockers"));
            blockers.sort(Comparator.naturalOrder());
            runtimeBlockers = List.copyOf(blockers);
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation.scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static void token(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }

    private static String requireSha256(String value) {
        String sha = requireText("sha256", value);
        if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return sha;
    }
}
