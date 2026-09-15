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

/** Deterministic discrete tail-junction lowering. Runtime attachment/force behavior is unqualified. */
public record SkyforgeAircraftTailLoweringIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceTargetPreflightDigestSha256,
        String sourceSurfaceStateDigestSha256,
        String profileId,
        SkyforgeAircraftTailLoweringProfile.Translation translationBlocks,
        List<ResolvedAerodynamicPlacement> resolvedAerodynamicPlacements,
        List<Translation> translatedVerticalTail,
        List<AircraftBlockspaceIR.LatticePoint> rootFaceContacts,
        List<AircraftBlockspaceIR.LatticePoint> collisions,
        TopologyChecks topologyChecks,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-tail-junction-lowerer-1";

    public SkyforgeAircraftTailLoweringIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported tail-lowering IR schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported tail-lowering compiler: " + compilerVersion);
        }
        sourceTargetPreflightDigestSha256 = requireSha256(sourceTargetPreflightDigestSha256);
        sourceSurfaceStateDigestSha256 = requireSha256(sourceSurfaceStateDigestSha256);
        profileId = requireText("profileId", profileId);
        translationBlocks = Objects.requireNonNull(translationBlocks, "translationBlocks");
        resolvedAerodynamicPlacements = sortedCopy(
                resolvedAerodynamicPlacements, Comparator.comparing(ResolvedAerodynamicPlacement::point));
        translatedVerticalTail = sortedCopy(translatedVerticalTail, Comparator.comparing(Translation::from));
        rootFaceContacts = sortedCopy(rootFaceContacts, Comparator.naturalOrder());
        collisions = sortedCopy(collisions, Comparator.naturalOrder());
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
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
        token(canonical, sourceSurfaceStateDigestSha256);
        token(canonical, profileId);
        token(canonical, translationBlocks.toString());
        resolvedAerodynamicPlacements.forEach(value -> token(canonical, value.toString()));
        translatedVerticalTail.forEach(value -> token(canonical, value.toString()));
        rootFaceContacts.forEach(value -> token(canonical, value.toString()));
        collisions.forEach(value -> token(canonical, value.toString()));
        token(canonical, topologyChecks.toString());
        token(canonical, metrics.toString());
        token(canonical, readiness.toString());
        token(canonical, validation.toString());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record ResolvedAerodynamicPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            String resourceId,
            String providerId,
            Map<String, String> blockState) {
        public ResolvedAerodynamicPlacement {
            point = Objects.requireNonNull(point, "point");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("resolved aerodynamic placement roles must not be empty");
            }
            resourceId = requireText("resourceId", resourceId);
            providerId = requireText("providerId", providerId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            if (blockState.size() != 1) {
                throw new IllegalArgumentException("resolved aerodynamic placement requires one explicit state property");
            }
        }
    }

    public record Translation(
            AircraftBlockspaceIR.LatticePoint from,
            AircraftBlockspaceIR.LatticePoint to) {
        public Translation {
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
    }

    public record TopologyChecks(
            boolean allSurfacePlacementsCoordinateUnique,
            boolean noImmutablePlacementCollision,
            boolean verticalCellCountPreserved,
            boolean verticalLongitudinalFirstMomentPreserved,
            boolean verticalRelativeShapePreserved,
            boolean verticalTailInternallyConnected,
            boolean verticalRootFaceAttachedToHorizontalTail,
            boolean allV06ConflictsResolved) {
        public boolean passed() {
            return allSurfacePlacementsCoordinateUnique
                    && noImmutablePlacementCollision
                    && verticalCellCountPreserved
                    && verticalLongitudinalFirstMomentPreserved
                    && verticalRelativeShapePreserved
                    && verticalTailInternallyConnected
                    && verticalRootFaceAttachedToHorizontalTail
                    && allV06ConflictsResolved;
        }
    }

    public record Metrics(
            int v06AerodynamicPlacementCount,
            int v07AerodynamicPlacementCount,
            int verticalTailCellCount,
            int horizontalTailCellCount,
            int resolvedFormerConflictCount,
            int verticalXFirstMomentBeforeBlocks,
            int verticalXFirstMomentAfterBlocks,
            int verticalYFirstMomentBeforeBlocks,
            int verticalYFirstMomentAfterBlocks,
            double verticalCentroidShiftXBlocks,
            double verticalCentroidShiftYBlocks,
            double verticalCentroidShiftZBlocks) {}

    public record Readiness(
            boolean tailJunctionLoweringPassed,
            boolean surfaceStateResolutionComplete,
            boolean probeSchematicEmissionReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> staticBlockers,
            List<String> runtimeBlockers) {
        public Readiness {
            staticBlockers = List.copyOf(Objects.requireNonNull(staticBlockers, "staticBlockers"));
            runtimeBlockers = List.copyOf(Objects.requireNonNull(runtimeBlockers, "runtimeBlockers"));
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
