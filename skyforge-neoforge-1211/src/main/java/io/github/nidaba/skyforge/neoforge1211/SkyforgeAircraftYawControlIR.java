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

/** Deterministic corrected v0.13.1 static yaw-control IR. Runtime yaw remains unqualified. */
public record SkyforgeAircraftYawControlIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceProbeManifestDigestSha256,
        String sourceAssemblyFixtureDigestSha256,
        String sourceGlueEncodingDigestSha256,
        String sourcePowertrainDigestSha256,
        String supersededYawDigestSha256,
        String correctionReason,
        String profileId,
        List<Placement> placements,
        List<AircraftBlockspaceIR.LatticePoint> fixedFinCoordinates,
        List<AircraftBlockspaceIR.LatticePoint> airGapCoordinates,
        List<AircraftBlockspaceIR.LatticePoint> rudderChildCoordinates,
        AircraftBlockspaceIR.LatticePoint swivelBearingCoordinate,
        AircraftBlockspaceIR.LatticePoint rudderSeedCoordinate,
        List<GlueDomain> runtimeGlueDomains,
        TopologyChecks topologyChecks,
        GlueChecks glueChecks,
        CountChecks countChecks,
        List<RuntimeObligation> runtimeObligations,
        AerodynamicAccounting aerodynamicAccounting,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-yaw-control-lowerer-1";

    public SkyforgeAircraftYawControlIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported yaw-control IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported yaw-control compiler: " + compilerVersion);
        sourceProbeManifestDigestSha256 = requireSha256(sourceProbeManifestDigestSha256);
        sourceAssemblyFixtureDigestSha256 = requireSha256(sourceAssemblyFixtureDigestSha256);
        sourceGlueEncodingDigestSha256 = requireSha256(sourceGlueEncodingDigestSha256);
        sourcePowertrainDigestSha256 = requireSha256(sourcePowertrainDigestSha256);
        supersededYawDigestSha256 = requireSha256(supersededYawDigestSha256);
        correctionReason = requireText("correctionReason", correctionReason);
        profileId = requireText("profileId", profileId);
        ArrayList<Placement> orderedPlacements = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        orderedPlacements.sort(Comparator.comparing(Placement::point).thenComparing(Placement::role));
        placements = List.copyOf(orderedPlacements);
        fixedFinCoordinates = sortedPoints(fixedFinCoordinates);
        airGapCoordinates = sortedPoints(airGapCoordinates);
        rudderChildCoordinates = sortedPoints(rudderChildCoordinates);
        swivelBearingCoordinate = Objects.requireNonNull(swivelBearingCoordinate, "swivelBearingCoordinate");
        rudderSeedCoordinate = Objects.requireNonNull(rudderSeedCoordinate, "rudderSeedCoordinate");
        ArrayList<GlueDomain> orderedGlue = new ArrayList<>(Objects.requireNonNull(runtimeGlueDomains, "runtimeGlueDomains"));
        orderedGlue.sort(Comparator.comparing(GlueDomain::name));
        runtimeGlueDomains = List.copyOf(orderedGlue);
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
        glueChecks = Objects.requireNonNull(glueChecks, "glueChecks");
        countChecks = Objects.requireNonNull(countChecks, "countChecks");
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        aerodynamicAccounting = Objects.requireNonNull(aerodynamicAccounting, "aerodynamicAccounting");
        metrics = Objects.requireNonNull(metrics, "metrics");
        readiness = Objects.requireNonNull(readiness, "readiness");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        token(canonical, Integer.toString(schemaVersion));
        token(canonical, assetId);
        token(canonical, compilerVersion);
        token(canonical, sourceProbeManifestDigestSha256);
        token(canonical, sourceAssemblyFixtureDigestSha256);
        token(canonical, sourceGlueEncodingDigestSha256);
        token(canonical, sourcePowertrainDigestSha256);
        token(canonical, supersededYawDigestSha256);
        token(canonical, correctionReason);
        token(canonical, profileId);
        placements.forEach(value -> token(canonical, value.toString()));
        fixedFinCoordinates.forEach(value -> token(canonical, value.toString()));
        airGapCoordinates.forEach(value -> token(canonical, value.toString()));
        rudderChildCoordinates.forEach(value -> token(canonical, value.toString()));
        token(canonical, swivelBearingCoordinate.toString());
        token(canonical, rudderSeedCoordinate.toString());
        runtimeGlueDomains.forEach(value -> token(canonical, value.toString()));
        token(canonical, topologyChecks.toString());
        token(canonical, glueChecks.toString());
        token(canonical, countChecks.toString());
        runtimeObligations.forEach(value -> token(canonical, value.toString()));
        token(canonical, aerodynamicAccounting.toString());
        token(canonical, metrics.toString());
        token(canonical, readiness.toString());
        token(canonical, validation.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Placement(
            String role,
            SkyforgeAircraftYawControlProfile.Mode mode,
            AircraftBlockspaceIR.LatticePoint point,
            String kind,
            String resourceId,
            Map<String, String> blockState,
            SkyforgeAircraftYawControlProfile.ReplaceExpectation replaces,
            String sourceContract) {
        public Placement {
            role = requireText("placement.role", role);
            mode = Objects.requireNonNull(mode, "mode");
            point = Objects.requireNonNull(point, "point");
            kind = requireText("placement.kind", kind);
            resourceId = requireText("placement.resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            sourceContract = requireText("placement.sourceContract", sourceContract);
        }
    }

    public record GlueDomain(
            String name,
            SkyforgeAircraftYawControlProfile.GlueOwner owner,
            AircraftBlockspaceIR.LatticePoint from,
            AircraftBlockspaceIR.LatticePoint to,
            AircraftBlockspaceIR.LatticePoint boundsMin,
            AircraftBlockspaceIR.LatticePoint boundsMax,
            AircraftBlockspaceIR.LatticePoint selectionSizeBlocks) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            owner = Objects.requireNonNull(owner, "owner");
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            boundsMin = Objects.requireNonNull(boundsMin, "boundsMin");
            boundsMax = Objects.requireNonNull(boundsMax, "boundsMax");
            selectionSizeBlocks = Objects.requireNonNull(selectionSizeBlocks, "selectionSizeBlocks");
        }
    }

    public record TopologyChecks(
            boolean exactlyOneBearingAddedToParent,
            boolean exactlyOneFormerTrailingFinRemoved,
            boolean exactlyFourRudderChildCells,
            boolean swivelBearingIsUpFacingAndIdle,
            boolean upFacingBearingSeedsRudderExactly,
            boolean fixedFinStateRetained,
            boolean airGapEmpty,
            boolean rudderChildConnected,
            boolean rudderChildUsesSymmetricZSails,
            boolean rudderDisjointFromParent,
            boolean rudderDisjointFromPropellerChild) {
        public boolean passed() {
            return exactlyOneBearingAddedToParent && exactlyOneFormerTrailingFinRemoved && exactlyFourRudderChildCells
                    && swivelBearingIsUpFacingAndIdle && upFacingBearingSeedsRudderExactly && fixedFinStateRetained
                    && airGapEmpty && rudderChildConnected && rudderChildUsesSymmetricZSails
                    && rudderDisjointFromParent && rudderDisjointFromPropellerChild;
        }
    }

    public record GlueChecks(
            boolean supersededVerticalTailRemovedExactlyOnce,
            boolean runtimeGlueDomainCountSeven,
            boolean allGlueWithinCreateSelectionLimit,
            boolean noParentGlueContainsRudder,
            boolean noGlueContainsAirGap,
            boolean fixedTailGlueContainsAllFixedFin,
            boolean bearingMountGlueContainsBearingAndParentAnchor,
            boolean rudderChildGlueContainsAllRudderAndExcludesBearing) {
        public boolean passed() {
            return supersededVerticalTailRemovedExactlyOnce && runtimeGlueDomainCountSeven
                    && allGlueWithinCreateSelectionLimit && noParentGlueContainsRudder && noGlueContainsAirGap
                    && fixedTailGlueContainsAllFixedFin && bearingMountGlueContainsBearingAndParentAnchor
                    && rudderChildGlueContainsAllRudderAndExcludesBearing;
        }
    }

    public record CountChecks(
            boolean v012MovingParentCountMatchesPowertrain,
            boolean parentAddRemoveBalanced,
            boolean correctedParentCount118,
            boolean propellerChildCount9,
            boolean rudderChildCount4,
            boolean resultingManifestCount130,
            boolean expectedPrimaryTransferCount131) {
        public boolean passed() {
            return v012MovingParentCountMatchesPowertrain && parentAddRemoveBalanced && correctedParentCount118
                    && propellerChildCount9 && rudderChildCount4 && resultingManifestCount130
                    && expectedPrimaryTransferCount131;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
            status = requireText("runtimeObligation.status", status);
        }
    }

    public record AerodynamicAccounting(
            int fixedVerticalTailRegularSailCells,
            int horizontalTailRegularSailCells,
            int rudderSymmetricSailCells,
            int airGapCells,
            String note) {
        public AerodynamicAccounting { note = requireText("aerodynamicAccounting.note", note); }
    }

    public record Metrics(
            int v012MovingParentMainBodyPlacementCount,
            int v0131MovingParentMainBodyPlacementCount,
            int nestedPropellerChildPlacementCount,
            int yawControlChildPlacementCount,
            int resultingManifestPlacementCount,
            int expectedPrimarySableTransferCount,
            int parentMainAdditionCount,
            int parentMainRemovalCount,
            int controlChildAdditionCount,
            int runtimeGlueDomainCount,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount) {}

    public record Readiness(
            boolean yawControlStaticTopologyPassed,
            boolean rudderChildCaptureProbeReady,
            boolean controlActuationProbeReady,
            boolean runtimeQualificationReady,
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

    private static List<AircraftBlockspaceIR.LatticePoint> sortedPoints(List<AircraftBlockspaceIR.LatticePoint> values) {
        ArrayList<AircraftBlockspaceIR.LatticePoint> ordered = new ArrayList<>(Objects.requireNonNull(values, "values"));
        ordered.sort(Comparator.naturalOrder());
        return List.copyOf(ordered);
    }

    private static void token(StringBuilder builder, String value) { builder.append(value.length()).append(':').append(value); }
    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
    private static String requireSha256(String value) {
        String sha = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return sha;
    }
}
