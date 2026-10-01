package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Deterministic static target-preflight artifact. No runtime success is implied. */
public record SkyforgeAircraftTargetPreflightIR(
        int schemaVersion,
        String assetId,
        String sourceAssemblyAssetId,
        String sourceAssemblyDigestSha256,
        String compilerVersion,
        SkyforgeAircraftTargetProfile.TargetStack target,
        String targetProfileId,
        List<Placement> placements,
        List<Station> stations,
        List<CompanionCheck> companionChecks,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation,
        List<UnresolvedSite> unresolvedSites,
        List<Placement> unresolvedStateOrResource,
        List<Station> unresolvedRequiredStations) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-target-preflight-1";

    public SkyforgeAircraftTargetPreflightIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported target preflight schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        sourceAssemblyAssetId = requireText("sourceAssemblyAssetId", sourceAssemblyAssetId);
        sourceAssemblyDigestSha256 = requireSha256(sourceAssemblyDigestSha256);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported target preflight compiler: " + compilerVersion);
        }
        target = Objects.requireNonNull(target, "target");
        targetProfileId = requireText("targetProfileId", targetProfileId);
        placements = sortedCopy(placements, Comparator.comparing(Placement::point));
        stations = sortedCopy(stations, Comparator.comparing(station -> station.type().id()));
        companionChecks = List.copyOf(Objects.requireNonNull(companionChecks, "companionChecks"));
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        metrics = Objects.requireNonNull(metrics, "metrics");
        readiness = Objects.requireNonNull(readiness, "readiness");
        validation = Objects.requireNonNull(validation, "validation");
        unresolvedSites = sortedCopy(unresolvedSites, Comparator.comparing(UnresolvedSite::point));
        unresolvedStateOrResource = sortedCopy(unresolvedStateOrResource, Comparator.comparing(Placement::point));
        unresolvedRequiredStations = sortedCopy(
                unresolvedRequiredStations, Comparator.comparing(station -> station.type().id()));
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        token(canonical, Integer.toString(schemaVersion));
        token(canonical, assetId);
        token(canonical, sourceAssemblyAssetId);
        token(canonical, sourceAssemblyDigestSha256);
        token(canonical, compilerVersion);
        token(canonical, target.toString());
        token(canonical, targetProfileId);
        placements.forEach(value -> token(canonical, value.toString()));
        stations.forEach(value -> token(canonical, value.toString()));
        companionChecks.forEach(value -> token(canonical, value.toString()));
        runtimeObligations.forEach(value -> token(canonical, value.toString()));
        token(canonical, metrics.toString());
        token(canonical, readiness.toString());
        token(canonical, validation.toString());
        unresolvedSites.forEach(value -> token(canonical, value.toString()));
        unresolvedStateOrResource.forEach(value -> token(canonical, value.toString()));
        unresolvedRequiredStations.forEach(value -> token(canonical, value.toString()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Placement(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            List<String> requiredCapabilities,
            String providerId,
            String resourceId,
            String evidenceLevel,
            String stateRule) {
        public Placement {
            point = Objects.requireNonNull(point, "point");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            requiredCapabilities = List.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
            providerId = requireText("providerId", providerId);
            resourceId = requireText("resourceId", resourceId);
            evidenceLevel = requireText("evidenceLevel", evidenceLevel);
            stateRule = requireText("stateRule", stateRule);
        }
    }

    public record Station(
            AircraftBlockspaceIR.AnchorType type,
            AircraftBlockspaceIR.LatticePoint lattice,
            List<String> requiredCapabilities,
            SkyforgeAircraftTargetProfile.StationStatus status,
            String resourceId,
            String stateRule,
            String evidence) {
        public Station {
            type = Objects.requireNonNull(type, "type");
            lattice = Objects.requireNonNull(lattice, "lattice");
            requiredCapabilities = List.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
            status = Objects.requireNonNull(status, "status");
            evidence = requireText("station evidence", evidence);
        }
    }

    public record CompanionCheck(String id, String role, int minimumCount, int actualCount, boolean passed, String evidence) {}
    public record RuntimeObligation(String id, String method, String status) {}
    public record Metrics(
            int assemblySiteCount,
            int mappedSiteCount,
            int unresolvedSiteCount,
            int unresolvedStateOrResourceCount,
            int companionFailureCount,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount,
            List<String> staticResourceIds) {
        public Metrics {
            staticResourceIds = List.copyOf(Objects.requireNonNull(staticResourceIds, "staticResourceIds"));
        }
    }
    public record Readiness(
            boolean staticCapabilityCoveragePassed,
            boolean schematicEmissionReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> blockers) {
        public Readiness {
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        }
    }
    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }
    public record UnresolvedSite(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            List<String> requiredCapabilities) {
        public UnresolvedSite {
            point = Objects.requireNonNull(point, "point");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            requiredCapabilities = List.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
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
