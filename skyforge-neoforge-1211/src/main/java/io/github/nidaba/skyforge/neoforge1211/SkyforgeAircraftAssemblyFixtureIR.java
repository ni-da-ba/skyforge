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

/** Static Physics Assembler fixture topology. No live Sable capture is implied. */
public record SkyforgeAircraftAssemblyFixtureIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceProbeManifestDigestSha256,
        String profileId,
        PhysicsAssemblerPlacement physicsAssemblerPlacement,
        MainBody mainBody,
        NestedPropellerChild nestedPropellerChild,
        AdhesionIntent adhesionIntent,
        TopologyChecks topologyChecks,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-assembly-fixture-planner-1";

    public SkyforgeAircraftAssemblyFixtureIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported assembly-fixture IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported assembly-fixture compiler: " + compilerVersion);
        sourceProbeManifestDigestSha256 = requireSha256(sourceProbeManifestDigestSha256);
        profileId = requireText("profileId", profileId);
        physicsAssemblerPlacement = Objects.requireNonNull(physicsAssemblerPlacement, "physicsAssemblerPlacement");
        mainBody = Objects.requireNonNull(mainBody, "mainBody");
        nestedPropellerChild = Objects.requireNonNull(nestedPropellerChild, "nestedPropellerChild");
        adhesionIntent = Objects.requireNonNull(adhesionIntent, "adhesionIntent");
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
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
        token(canonical, sourceProbeManifestDigestSha256);
        token(canonical, profileId);
        token(canonical, physicsAssemblerPlacement.toString());
        token(canonical, mainBody.toString());
        token(canonical, nestedPropellerChild.toString());
        token(canonical, adhesionIntent.toString());
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

    public record PhysicsAssemblerPlacement(
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId,
            Map<String, String> blockState,
            AircraftBlockspaceIR.LatticePoint seedLattice,
            String movingBodyMembership,
            String sourceContract) {
        public PhysicsAssemblerPlacement {
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            seedLattice = Objects.requireNonNull(seedLattice, "seedLattice");
            movingBodyMembership = requireText("movingBodyMembership", movingBodyMembership);
            sourceContract = requireText("sourceContract", sourceContract);
        }
    }

    public record MainBody(
            int placementCount,
            int manifestPlacementCount,
            int fixturePlacementCount,
            List<AircraftBlockspaceIR.LatticePoint> coordinates,
            AircraftBlockspaceIR.LatticePoint seedCoordinate,
            int reachableCount,
            List<AircraftBlockspaceIR.LatticePoint> unreachableCoordinates) {
        public MainBody {
            coordinates = sortedPoints(coordinates);
            seedCoordinate = Objects.requireNonNull(seedCoordinate, "seedCoordinate");
            unreachableCoordinates = sortedPoints(unreachableCoordinates);
        }
    }

    public record NestedPropellerChild(
            int placementCount,
            List<AircraftBlockspaceIR.LatticePoint> coordinates,
            List<FaceAdjacency> bearingToHubFaceAdjacency) {
        public NestedPropellerChild {
            coordinates = sortedPoints(coordinates);
            ArrayList<FaceAdjacency> ordered = new ArrayList<>(Objects.requireNonNull(bearingToHubFaceAdjacency, "bearingToHubFaceAdjacency"));
            ordered.sort(Comparator.comparing(FaceAdjacency::bearing).thenComparing(FaceAdjacency::hub));
            bearingToHubFaceAdjacency = List.copyOf(ordered);
        }
    }

    public record FaceAdjacency(AircraftBlockspaceIR.LatticePoint bearing, AircraftBlockspaceIR.LatticePoint hub) {
        public FaceAdjacency {
            bearing = Objects.requireNonNull(bearing, "bearing");
            hub = Objects.requireNonNull(hub, "hub");
        }
    }

    public record AdhesionIntent(
            String policy,
            int edgeCount,
            List<Edge> edges,
            String encodingStatus) {
        public AdhesionIntent {
            policy = requireText("adhesion policy", policy);
            ArrayList<Edge> ordered = new ArrayList<>(Objects.requireNonNull(edges, "edges"));
            ordered.sort(Comparator.comparing(Edge::a).thenComparing(Edge::b));
            edges = List.copyOf(ordered);
            encodingStatus = requireText("encodingStatus", encodingStatus);
        }
    }

    public record Edge(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        public Edge {
            a = Objects.requireNonNull(a, "a");
            b = Objects.requireNonNull(b, "b");
            if (a.compareTo(b) > 0) {
                AircraftBlockspaceIR.LatticePoint swap = a;
                a = b;
                b = swap;
            }
            if (a.equals(b)) throw new IllegalArgumentException("adhesion edge endpoints must differ");
        }
    }

    public record TopologyChecks(
            boolean seedIsMainBodyPlacement,
            boolean assemblerCoordinateFree,
            boolean assemblerCeilingStickyFaceSeedsUpward,
            boolean physicsAssemblerIncludedInMovingMainBody,
            boolean physicsAssemblerHasAdhesionEdgeToSeed,
            boolean mainBodyAdjacencyGraphConnected,
            boolean spanningTreeEdgeCountIsNMinusOne,
            boolean nestedChildExcludedFromMainAdhesionGraph,
            boolean exactlyOnePropellerBearingOnMainBody,
            boolean propellerChildContainsHubAndSails,
            boolean bearingFaceAdjacentToChildHub) {
        public boolean passed() {
            return seedIsMainBodyPlacement
                    && assemblerCoordinateFree
                    && assemblerCeilingStickyFaceSeedsUpward
                    && physicsAssemblerIncludedInMovingMainBody
                    && physicsAssemblerHasAdhesionEdgeToSeed
                    && mainBodyAdjacencyGraphConnected
                    && spanningTreeEdgeCountIsNMinusOne
                    && nestedChildExcludedFromMainAdhesionGraph
                    && exactlyOnePropellerBearingOnMainBody
                    && propellerChildContainsHubAndSails
                    && bearingFaceAdjacentToChildHub;
        }
    }

    public record RuntimeObligation(String id, String method, String status) {}

    public record Metrics(
            int manifestPlacementCount,
            int fixturePlacementCount,
            int mainBodyManifestPlacementCount,
            int mainBodyPlacementCount,
            int nestedChildPlacementCount,
            int adhesionIntentEdgeCount,
            int mainBodyUnreachableCount,
            int runtimeObligationCount,
            int runtimeObligationVerifiedCount) {}

    public record Readiness(
            boolean assemblyFixtureTopologyPassed,
            boolean physicsAssemblerPlacementResolved,
            boolean mainBodyAdhesionGraphResolved,
            boolean adhesionApplicationEncodingReady,
            boolean physicsAssemblyProbeReady,
            boolean runtimeQualificationReady,
            boolean flightQualified,
            List<String> mechanicalBlockers) {
        public Readiness {
            ArrayList<String> blockers = new ArrayList<>(Objects.requireNonNull(mechanicalBlockers, "mechanicalBlockers"));
            blockers.sort(Comparator.naturalOrder());
            mechanicalBlockers = List.copyOf(blockers);
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    private static List<AircraftBlockspaceIR.LatticePoint> sortedPoints(List<AircraftBlockspaceIR.LatticePoint> values) {
        ArrayList<AircraftBlockspaceIR.LatticePoint> ordered = new ArrayList<>(Objects.requireNonNull(values, "values"));
        ordered.sort(Comparator.naturalOrder());
        return List.copyOf(ordered);
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
