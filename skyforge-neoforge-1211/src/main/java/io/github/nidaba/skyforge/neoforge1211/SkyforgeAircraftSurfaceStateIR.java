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

/** Deterministic static regular-sail state resolution. Runtime force sign remains unqualified. */
public record SkyforgeAircraftSurfaceStateIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceTargetPreflightDigestSha256,
        String sourcePropulsionDigestSha256,
        String surfaceStateProfileId,
        String providerId,
        String stateProperty,
        List<ResolvedPlacement> resolvedPlacements,
        List<Conflict> conflicts,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-surface-state-resolver-1";

    public SkyforgeAircraftSurfaceStateIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported surface-state IR schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported surface-state compiler: " + compilerVersion);
        }
        sourceTargetPreflightDigestSha256 = requireSha256(sourceTargetPreflightDigestSha256);
        sourcePropulsionDigestSha256 = requireSha256(sourcePropulsionDigestSha256);
        surfaceStateProfileId = requireText("surfaceStateProfileId", surfaceStateProfileId);
        providerId = requireText("providerId", providerId);
        stateProperty = requireText("stateProperty", stateProperty);
        resolvedPlacements = sortedCopy(
                resolvedPlacements, Comparator.comparing(ResolvedPlacement::point));
        conflicts = sortedCopy(conflicts, Comparator.comparing(Conflict::point));
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
        token(canonical, surfaceStateProfileId);
        token(canonical, providerId);
        token(canonical, stateProperty);
        resolvedPlacements.forEach(value -> token(canonical, value.toString()));
        conflicts.forEach(value -> token(canonical, value.toString()));
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

    public record ResolvedPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            String resourceId,
            String providerId,
            List<String> demandedStates,
            Map<String, String> blockState,
            String status) {
        public ResolvedPlacement {
            point = Objects.requireNonNull(point, "point");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            resourceId = requireText("resourceId", resourceId);
            providerId = requireText("providerId", providerId);
            demandedStates = List.copyOf(Objects.requireNonNull(demandedStates, "demandedStates"));
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            status = requireText("status", status);
            if (!"resolved".equals(status) || demandedStates.size() != 1 || blockState.size() != 1) {
                throw new IllegalArgumentException("resolved placement must contain one demanded state and one block state");
            }
        }
    }

    public record Conflict(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            String resourceId,
            String providerId,
            List<String> demandedStates,
            String status,
            String reason) {
        public Conflict {
            point = Objects.requireNonNull(point, "point");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            resourceId = requireText("resourceId", resourceId);
            providerId = requireText("providerId", providerId);
            demandedStates = List.copyOf(Objects.requireNonNull(demandedStates, "demandedStates"));
            status = requireText("status", status);
            reason = requireText("reason", reason);
            if (!"conflict".equals(status)) {
                throw new IllegalArgumentException("surface-state conflict status must be 'conflict'");
            }
        }
    }

    public record Metrics(
            int providerPlacementCount,
            int stateResolvedCount,
            int stateConflictCount,
            int inputUnresolvedStateOrResourceCount,
            int remainingUnresolvedStateOrResourceCount) {
        public Metrics {
            if (providerPlacementCount < 0
                    || stateResolvedCount < 0
                    || stateConflictCount < 0
                    || inputUnresolvedStateOrResourceCount < 0
                    || remainingUnresolvedStateOrResourceCount < 0) {
                throw new IllegalArgumentException("surface-state metrics must be non-negative");
            }
        }
    }

    public record Readiness(
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
