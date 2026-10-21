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

/** Static bounded Create Super Glue command/domain lowering. Runtime realization remains unverified. */
public record SkyforgeAircraftGlueEncodingIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceAssemblyFixtureDigestSha256,
        String profileId,
        EncodingPolicy encodingPolicy,
        String physicsAssemblerCommand,
        List<String> glueCommands,
        List<GlueDomain> glueDomains,
        AdhesionProof adhesionProof,
        List<SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge> forbiddenGlueEdges,
        Checks checks,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-glue-domain-encoder-1";

    public SkyforgeAircraftGlueEncodingIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported glue-encoding IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported glue-encoding compiler: " + compilerVersion);
        sourceAssemblyFixtureDigestSha256 = requireSha256(sourceAssemblyFixtureDigestSha256);
        profileId = requireText("profileId", profileId);
        encodingPolicy = Objects.requireNonNull(encodingPolicy, "encodingPolicy");
        physicsAssemblerCommand = requireText("physicsAssemblerCommand", physicsAssemblerCommand);
        glueCommands = List.copyOf(Objects.requireNonNull(glueCommands, "glueCommands"));
        glueDomains = List.copyOf(Objects.requireNonNull(glueDomains, "glueDomains"));
        adhesionProof = Objects.requireNonNull(adhesionProof, "adhesionProof");
        ArrayList<SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge> forbidden = new ArrayList<>(Objects.requireNonNull(forbiddenGlueEdges, "forbiddenGlueEdges"));
        forbidden.sort(Comparator.comparing(SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge::a)
                .thenComparing(SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge::b));
        forbiddenGlueEdges = List.copyOf(forbidden);
        checks = Objects.requireNonNull(checks, "checks");
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
        token(canonical, sourceAssemblyFixtureDigestSha256);
        token(canonical, profileId);
        token(canonical, encodingPolicy.toString());
        token(canonical, physicsAssemblerCommand);
        glueCommands.forEach(value -> token(canonical, value));
        glueDomains.forEach(value -> token(canonical, value.toString()));
        token(canonical, adhesionProof.toString());
        forbiddenGlueEdges.forEach(value -> token(canonical, value.toString()));
        token(canonical, checks.toString());
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

    public record EncodingPolicy(
            String policy,
            String commandRoot,
            int requiredPermissionLevel,
            int maxSelectionDimensionBlocks,
            boolean idempotent,
            String rerunRule,
            String rationale) {}

    public record GlueDomain(
            String name,
            AircraftBlockspaceIR.LatticePoint from,
            AircraftBlockspaceIR.LatticePoint to,
            String command,
            AircraftBlockspaceIR.LatticePoint boundsMin,
            AircraftBlockspaceIR.LatticePoint boundsMax,
            AircraftBlockspaceIR.LatticePoint selectionSizeBlocks,
            List<SkyforgeAircraftAssemblyFixtureIR.Edge> coveredAdhesionEdges) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            command = requireText("glueDomain.command", command);
            boundsMin = Objects.requireNonNull(boundsMin, "boundsMin");
            boundsMax = Objects.requireNonNull(boundsMax, "boundsMax");
            selectionSizeBlocks = Objects.requireNonNull(selectionSizeBlocks, "selectionSizeBlocks");
            ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = new ArrayList<>(Objects.requireNonNull(coveredAdhesionEdges, "coveredAdhesionEdges"));
            edges.sort(Comparator.comparing(SkyforgeAircraftAssemblyFixtureIR.Edge::a).thenComparing(SkyforgeAircraftAssemblyFixtureIR.Edge::b));
            coveredAdhesionEdges = List.copyOf(edges);
        }

        public int coveredAdhesionEdgeCount() {
            return coveredAdhesionEdges.size();
        }
    }

    public record AdhesionProof(
            int edgeCount,
            int coveredEdgeCount,
            List<SkyforgeAircraftAssemblyFixtureIR.Edge> uncoveredEdges) {
        public AdhesionProof {
            ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = new ArrayList<>(Objects.requireNonNull(uncoveredEdges, "uncoveredEdges"));
            edges.sort(Comparator.comparing(SkyforgeAircraftAssemblyFixtureIR.Edge::a).thenComparing(SkyforgeAircraftAssemblyFixtureIR.Edge::b));
            uncoveredEdges = List.copyOf(edges);
        }
    }

    public record Checks(
            boolean domainCoverEncoding,
            boolean mainBodyConnectivityProofRetained,
            boolean allAdhesionIntentEdgesCovered,
            boolean allGlueDomainsWithinSelectionLimit,
            boolean noGlueDomainContainsNestedChild,
            boolean forbiddenDynamicBoundariesClear,
            boolean physicsAssemblerCommandEncoded,
            boolean createGlueCommandSourceBacked) {
        public boolean passed() {
            return domainCoverEncoding
                    && mainBodyConnectivityProofRetained
                    && allAdhesionIntentEdgesCovered
                    && allGlueDomainsWithinSelectionLimit
                    && noGlueDomainContainsNestedChild
                    && forbiddenDynamicBoundariesClear
                    && physicsAssemblerCommandEncoded
                    && createGlueCommandSourceBacked;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {}
    public record Metrics(
            int mainBodyPlacementCount,
            int nestedChildPlacementCount,
            int adhesionIntentEdgeCount,
            int coveredAdhesionIntentEdgeCount,
            int glueDomainCount,
            int glueCommandCount,
            int assemblerPlacementCommandCount,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount) {}

    public record Readiness(
            boolean adhesionApplicationEncodingReady,
            boolean mainBodyPhysicsAssemblyProbeReady,
            boolean physicsAssemblyProbeReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> remainingMechanicalBlockers,
            List<String> runtimeBlockers) {
        public Readiness {
            remainingMechanicalBlockers = sortedStrings(remainingMechanicalBlockers);
            runtimeBlockers = sortedStrings(runtimeBlockers);
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static List<String> sortedStrings(List<String> values) {
        ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
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
