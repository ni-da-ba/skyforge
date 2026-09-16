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

/** Coordinate-unique exact-resource/blockstate manifest for isolated probe placement only. */
public record SkyforgeAircraftProbeManifestIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceTargetPreflightDigestSha256,
        String sourcePropulsionDigestSha256,
        String sourceTailLoweringDigestSha256,
        String sourcePilotStationDigestSha256,
        String profileId,
        SkyforgeAircraftProbeManifestProfile.OriginContract originContract,
        List<Placement> placements,
        Bounds bounds,
        List<String> resourceIds,
        Map<String, Integer> kindCounts,
        List<AircraftBlockspaceIR.LatticePoint> duplicateCoordinates,
        ValidationChecks validationChecks,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-probe-manifest-emitter-1";

    private static final Comparator<Placement> PLACEMENT_ORDER = Comparator
            .comparing(Placement::point)
            .thenComparing(Placement::kind)
            .thenComparing(Placement::resourceId);

    public SkyforgeAircraftProbeManifestIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported probe-manifest IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported probe-manifest compiler: " + compilerVersion);
        sourceTargetPreflightDigestSha256 = requireSha256(sourceTargetPreflightDigestSha256);
        sourcePropulsionDigestSha256 = requireSha256(sourcePropulsionDigestSha256);
        sourceTailLoweringDigestSha256 = requireSha256(sourceTailLoweringDigestSha256);
        sourcePilotStationDigestSha256 = requireSha256(sourcePilotStationDigestSha256);
        profileId = requireText("profileId", profileId);
        originContract = Objects.requireNonNull(originContract, "originContract");
        ArrayList<Placement> ordered = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        ordered.sort(PLACEMENT_ORDER);
        placements = List.copyOf(ordered);
        if (placements.isEmpty()) throw new IllegalArgumentException("probe manifest must contain placements");
        bounds = Objects.requireNonNull(bounds, "bounds");
        ArrayList<String> resources = new ArrayList<>(Objects.requireNonNull(resourceIds, "resourceIds"));
        resources.sort(Comparator.naturalOrder());
        resourceIds = List.copyOf(resources);
        kindCounts = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(kindCounts, "kindCounts")));
        ArrayList<AircraftBlockspaceIR.LatticePoint> duplicates = new ArrayList<>(Objects.requireNonNull(duplicateCoordinates, "duplicateCoordinates"));
        duplicates.sort(Comparator.naturalOrder());
        duplicateCoordinates = List.copyOf(duplicates);
        validationChecks = Objects.requireNonNull(validationChecks, "validationChecks");
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
        token(canonical, sourcePilotStationDigestSha256);
        token(canonical, profileId);
        token(canonical, originContract.toString());
        for (Placement placement : placements) {
            token(canonical, placement.kind());
            token(canonical, placement.point().toString());
            token(canonical, placement.resourceId());
            new TreeMap<>(placement.blockState()).forEach((key, value) -> {
                token(canonical, key);
                token(canonical, value);
            });
            token(canonical, placement.sourceLayer());
        }
        token(canonical, bounds.toString());
        resourceIds.forEach(value -> token(canonical, value));
        kindCounts.forEach((key, value) -> {
            token(canonical, key);
            token(canonical, Integer.toString(value));
        });
        duplicateCoordinates.forEach(value -> token(canonical, value.toString()));
        token(canonical, validationChecks.toString());
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
            String kind,
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            String sourceLayer) {
        public Placement {
            kind = requireText("placement kind", kind);
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            sourceLayer = requireText("sourceLayer", sourceLayer);
        }
    }

    public record Bounds(
            AircraftBlockspaceIR.LatticePoint min,
            AircraftBlockspaceIR.LatticePoint max,
            AircraftBlockspaceIR.LatticePoint sizeBlocks) {
        public Bounds {
            min = Objects.requireNonNull(min, "min");
            max = Objects.requireNonNull(max, "max");
            sizeBlocks = Objects.requireNonNull(sizeBlocks, "sizeBlocks");
        }
    }

    public record ValidationChecks(
            boolean allPlacementsCoordinateUnique,
            boolean allResourceIdsNamespaced,
            boolean allBlockStatesExplicitObjects,
            boolean tailSurfaceCountPreserved,
            boolean propulsionPlacementCountPreserved,
            boolean pilotPlacementCountIsOne) {
        public boolean passed() {
            return allPlacementsCoordinateUnique
                    && allResourceIdsNamespaced
                    && allBlockStatesExplicitObjects
                    && tailSurfaceCountPreserved
                    && propulsionPlacementCountPreserved
                    && pilotPlacementCountIsOne;
        }
    }

    public record Readiness(
            boolean probePlacementManifestReady,
            boolean probePlacementCommandsReady,
            boolean physicsAssemblyProbeReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> staticBlockers,
            List<String> mechanicalBlockers,
            List<String> runtimeBlockers) {
        public Readiness {
            staticBlockers = sortedCopy(staticBlockers);
            mechanicalBlockers = sortedCopy(mechanicalBlockers);
            runtimeBlockers = sortedCopy(runtimeBlockers);
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static List<String> sortedCopy(List<String> values) {
        ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
        copy.sort(Comparator.naturalOrder());
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
