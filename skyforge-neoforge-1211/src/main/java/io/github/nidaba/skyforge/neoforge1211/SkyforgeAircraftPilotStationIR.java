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

/** Deterministic static pilot-station lowering. Runtime occupancy/control behavior is unqualified. */
public record SkyforgeAircraftPilotStationIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceTargetPreflightDigestSha256,
        String sourcePropulsionDigestSha256,
        String sourceTailLoweringDigestSha256,
        String profileId,
        SemanticStation semanticStation,
        Placement placement,
        SkyforgeAircraftPilotStationProfile.ControlBindingContract controlBindingContract,
        TopologyChecks topologyChecks,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-pilot-station-realizer-1";

    public SkyforgeAircraftPilotStationIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported pilot-station IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported pilot-station compiler: " + compilerVersion);
        sourceTargetPreflightDigestSha256 = requireSha256(sourceTargetPreflightDigestSha256);
        sourcePropulsionDigestSha256 = requireSha256(sourcePropulsionDigestSha256);
        sourceTailLoweringDigestSha256 = requireSha256(sourceTailLoweringDigestSha256);
        profileId = requireText("profileId", profileId);
        semanticStation = Objects.requireNonNull(semanticStation, "semanticStation");
        placement = Objects.requireNonNull(placement, "placement");
        controlBindingContract = Objects.requireNonNull(controlBindingContract, "controlBindingContract");
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
        runtimeObligations = sortedCopy(runtimeObligations, Comparator.comparing(RuntimeObligation::id));
        metrics = Objects.requireNonNull(metrics, "metrics");
        readiness = Objects.requireNonNull(readiness, "readiness");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        token(canonical, Integer.toString(schemaVersion));
        token(canonical, assetId);
        token(canonical, compilerVersion);
        token(canonical, sourceTargetPreflightDigestSha256);
        token(canonical, sourcePropulsionDigestSha256);
        token(canonical, sourceTailLoweringDigestSha256);
        token(canonical, profileId);
        token(canonical, semanticStation.toString());
        token(canonical, placement.toString());
        token(canonical, controlBindingContract.toString());
        token(canonical, topologyChecks.toString());
        runtimeObligations.forEach(value -> token(canonical, value.toString()));
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

    public record SemanticStation(
            AircraftBlockspaceIR.AnchorType type,
            AircraftBlockspaceIR.LatticePoint anchorLattice,
            SkyforgeAircraftPilotStationProfile.Offset installationOffsetBlocks) {
        public SemanticStation {
            type = Objects.requireNonNull(type, "type");
            anchorLattice = Objects.requireNonNull(anchorLattice, "anchorLattice");
            installationOffsetBlocks = Objects.requireNonNull(installationOffsetBlocks, "installationOffsetBlocks");
        }
    }

    public record Placement(
            String kind,
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            String staticCapability,
            String runtimeBehaviorStatus) {
        public Placement {
            kind = requireText("placement kind", kind);
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            staticCapability = requireText("staticCapability", staticCapability);
            runtimeBehaviorStatus = requireText("runtimeBehaviorStatus", runtimeBehaviorStatus);
        }
    }

    public record TopologyChecks(
            boolean installationOffsetIsOneBlockUp,
            boolean pilotAnchorIsStructuralAirframeSite,
            boolean seatCoordinateFree,
            boolean seatSupportedByPilotAnchor,
            boolean tailLoweringPassed,
            boolean surfaceStateResolutionComplete) {
        public boolean passed() {
            return installationOffsetIsOneBlockUp
                    && pilotAnchorIsStructuralAirframeSite
                    && seatCoordinateFree
                    && seatSupportedByPilotAnchor
                    && tailLoweringPassed
                    && surfaceStateResolutionComplete;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {
        public RuntimeObligation {
            id = requireText("runtime obligation id", id);
            method = requireText("runtime obligation method", method);
            status = requireText("runtime obligation status", status);
        }
    }

    public record Metrics(
            boolean pilotAnchorOccupiedByStructure,
            int pilotSeatPlacementCount,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount) {}

    public record Readiness(
            boolean pilotStationStaticPlacementPassed,
            boolean probeSchematicEmissionReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> resolvedUpstreamBlockers,
            List<String> staticBlockers,
            List<String> runtimeBlockers) {
        public Readiness {
            resolvedUpstreamBlockers = sortedCopy(resolvedUpstreamBlockers, Comparator.naturalOrder());
            staticBlockers = sortedCopy(staticBlockers, Comparator.naturalOrder());
            runtimeBlockers = sortedCopy(runtimeBlockers, Comparator.naturalOrder());
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<T> comparator) {
        ArrayList<T> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
        copy.sort(comparator);
        return List.copyOf(copy);
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
        String sha = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return sha;
    }
}
