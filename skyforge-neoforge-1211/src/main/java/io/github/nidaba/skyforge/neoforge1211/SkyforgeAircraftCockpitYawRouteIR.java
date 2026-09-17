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

/** Deterministic AIRCRAFT-PROD-014 static cockpit-to-rudder route IR. */
public record SkyforgeAircraftCockpitYawRouteIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourcePilotStationDigestSha256,
        String sourceManifestDigestSha256,
        String sourceAssemblyFixtureDigestSha256,
        String sourceGlueEncodingDigestSha256,
        String sourcePowertrainDigestSha256,
        String sourceYawControlDigestSha256,
        String sourceSteeringYawSourceDigestSha256,
        String profileId,
        AircraftBlockspaceIR.LatticePoint pilotSeatCoordinate,
        String selectedCandidate,
        int selectedCandidateScore,
        AircraftBlockspaceIR.LatticePoint steeringWheelCoordinate,
        Map<String, String> steeringWheelBlockState,
        AircraftBlockspaceIR.LatticePoint driveCogCoordinate,
        AircraftBlockspaceIR.LatticePoint swivelBearingCoordinate,
        int routePlaneY,
        List<AircraftBlockspaceIR.LatticePoint> routePath,
        List<Placement> placements,
        GlueDomain routeGlueDomain,
        List<RejectedCandidate> rejectedCandidates,
        SignContract signContract,
        Metrics metrics,
        List<RuntimeObligation> runtimeObligations,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-cockpit-yaw-route-lowerer-1";

    public SkyforgeAircraftCockpitYawRouteIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported cockpit yaw-route IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported cockpit yaw-route compiler: " + compilerVersion);
        sourcePilotStationDigestSha256 = requireSha256(sourcePilotStationDigestSha256);
        sourceManifestDigestSha256 = requireSha256(sourceManifestDigestSha256);
        sourceAssemblyFixtureDigestSha256 = requireSha256(sourceAssemblyFixtureDigestSha256);
        sourceGlueEncodingDigestSha256 = requireSha256(sourceGlueEncodingDigestSha256);
        sourcePowertrainDigestSha256 = requireSha256(sourcePowertrainDigestSha256);
        sourceYawControlDigestSha256 = requireSha256(sourceYawControlDigestSha256);
        sourceSteeringYawSourceDigestSha256 = requireSha256(sourceSteeringYawSourceDigestSha256);
        profileId = requireText("profileId", profileId);
        pilotSeatCoordinate = Objects.requireNonNull(pilotSeatCoordinate, "pilotSeatCoordinate");
        selectedCandidate = requireText("selectedCandidate", selectedCandidate);
        steeringWheelCoordinate = Objects.requireNonNull(steeringWheelCoordinate, "steeringWheelCoordinate");
        steeringWheelBlockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(steeringWheelBlockState, "steeringWheelBlockState")));
        driveCogCoordinate = Objects.requireNonNull(driveCogCoordinate, "driveCogCoordinate");
        swivelBearingCoordinate = Objects.requireNonNull(swivelBearingCoordinate, "swivelBearingCoordinate");
        routePath = List.copyOf(Objects.requireNonNull(routePath, "routePath"));
        if (routePath.isEmpty()) throw new IllegalArgumentException("routePath must not be empty");
        ArrayList<Placement> orderedPlacements = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        orderedPlacements.sort(Comparator.comparing(Placement::point).thenComparing(Placement::role));
        placements = List.copyOf(orderedPlacements);
        routeGlueDomain = Objects.requireNonNull(routeGlueDomain, "routeGlueDomain");
        ArrayList<RejectedCandidate> rejected = new ArrayList<>(Objects.requireNonNull(rejectedCandidates, "rejectedCandidates"));
        rejected.sort(Comparator.comparing(RejectedCandidate::name).thenComparing(RejectedCandidate::reason));
        rejectedCandidates = List.copyOf(rejected);
        signContract = Objects.requireNonNull(signContract, "signContract");
        metrics = Objects.requireNonNull(metrics, "metrics");
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
        token(canonical, sourcePilotStationDigestSha256);
        token(canonical, sourceManifestDigestSha256);
        token(canonical, sourceAssemblyFixtureDigestSha256);
        token(canonical, sourceGlueEncodingDigestSha256);
        token(canonical, sourcePowertrainDigestSha256);
        token(canonical, sourceYawControlDigestSha256);
        token(canonical, sourceSteeringYawSourceDigestSha256);
        token(canonical, profileId);
        token(canonical, pilotSeatCoordinate.toString());
        token(canonical, selectedCandidate);
        token(canonical, Integer.toString(selectedCandidateScore));
        token(canonical, steeringWheelCoordinate.toString());
        new TreeMap<>(steeringWheelBlockState).forEach((key, value) -> { token(canonical, key); token(canonical, value); });
        token(canonical, driveCogCoordinate.toString());
        token(canonical, swivelBearingCoordinate.toString());
        token(canonical, Integer.toString(routePlaneY));
        routePath.forEach(point -> token(canonical, point.toString()));
        placements.forEach(value -> token(canonical, value.toString()));
        token(canonical, routeGlueDomain.toString());
        rejectedCandidates.forEach(value -> token(canonical, value.toString()));
        token(canonical, signContract.toString());
        token(canonical, metrics.toString());
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

    public record Placement(String kind, AircraftBlockspaceIR.LatticePoint point, String resourceId,
            Map<String, String> blockState, String role) {
        public Placement {
            kind = requireText("placement.kind", kind);
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("placement.resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            role = requireText("placement.role", role);
        }
    }

    public record GlueDomain(String name, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to,
            AircraftBlockspaceIR.LatticePoint boundsMin, AircraftBlockspaceIR.LatticePoint boundsMax,
            AircraftBlockspaceIR.LatticePoint selectionSizeBlocks) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            boundsMin = Objects.requireNonNull(boundsMin, "boundsMin");
            boundsMax = Objects.requireNonNull(boundsMax, "boundsMax");
            selectionSizeBlocks = Objects.requireNonNull(selectionSizeBlocks, "selectionSizeBlocks");
        }
    }

    public record RejectedCandidate(String name, String reason) {
        public RejectedCandidate {
            name = requireText("rejectedCandidate.name", name);
            reason = requireText("rejectedCandidate.reason", reason);
        }
    }

    public record SignStep(AircraftBlockspaceIR.LatticePoint at, String primitive, String sourceDirection,
            String outputDirection, int modifier, int signAfter) {
        public SignStep {
            at = Objects.requireNonNull(at, "at");
            primitive = requireText("signStep.primitive", primitive);
            sourceDirection = requireText("signStep.sourceDirection", sourceDirection);
            outputDirection = requireText("signStep.outputDirection", outputDirection);
            if (modifier != -1 && modifier != 1) throw new IllegalArgumentException("sign modifier must be +/-1");
            if (signAfter != -1 && signAfter != 1) throw new IllegalArgumentException("signAfter must be +/-1");
        }
    }

    public record SignContract(int positiveWheelCommandGeneratedSign, int expectedTailDriveCogSign,
            int expectedSwivelExtraCogSign, int expectedRpmMagnitude, List<SignStep> trace,
            String sourceContract, String createSourceCommit) {
        public SignContract {
            if (positiveWheelCommandGeneratedSign != -1 && positiveWheelCommandGeneratedSign != 1) throw new IllegalArgumentException("wheel sign must be +/-1");
            if (expectedTailDriveCogSign != -1 && expectedTailDriveCogSign != 1) throw new IllegalArgumentException("tail cog sign must be +/-1");
            if (expectedSwivelExtraCogSign != -1 && expectedSwivelExtraCogSign != 1) throw new IllegalArgumentException("Swivel sign must be +/-1");
            if (expectedRpmMagnitude != 16) throw new IllegalArgumentException("Steering Wheel route RPM authority is exactly 16");
            trace = List.copyOf(Objects.requireNonNull(trace, "trace"));
            sourceContract = requireText("signContract.sourceContract", sourceContract);
            createSourceCommit = requireText("signContract.createSourceCommit", createSourceCommit);
        }
    }

    public record Metrics(int routePlacementCount, int routePlaneCellCount, int routeTurnCount, int gearboxCount,
            int shaftCount, int priorMovingParentMainBodyPlacementCount, int resultingMovingParentMainBodyPlacementCount,
            int nestedPropellerChildPlacementCount, int yawControlChildPlacementCount, int expectedPrimarySableTransferCount) {}

    public record RuntimeObligation(String id, String method, String status) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
            status = requireText("runtimeObligation.status", status);
        }
    }

    public record Readiness(boolean cockpitRouteStaticSearchPassed, boolean cockpitRoutePlacementPatchReady,
            boolean cockpitRouteRuntimeProbeReady, boolean cockpitRouteRuntimeQualified,
            boolean pilotInteractionBindingReady, boolean runtimeQualificationReady, boolean flightQualified,
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

    private static void token(StringBuilder builder, String value) { builder.append(value.length()).append(':').append(value); }
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
