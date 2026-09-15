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

/** Deterministic static v0.12 governed-powertrain placement patch. Live kinetics remain unverified. */
public record SkyforgeAircraftPowertrainIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        String sourceProbeManifestDigestSha256,
        String sourceAssemblyFixtureDigestSha256,
        String sourceGlueEncodingDigestSha256,
        String profileId,
        Governor governor,
        List<Placement> placements,
        int replacementCount,
        int additionCount,
        GlueDomain powerplantGlueDomain,
        TopologyChecks topologyChecks,
        GlueChecks glueChecks,
        List<RuntimeObligation> runtimeObligations,
        Metrics metrics,
        Readiness readiness,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "skyforge-aircraft-powertrain-lowerer-1";

    public SkyforgeAircraftPowertrainIR {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported powertrain IR schema: " + schemaVersion);
        assetId = requireText("assetId", assetId);
        if (!COMPILER_VERSION.equals(compilerVersion)) throw new IllegalArgumentException("unsupported powertrain compiler: " + compilerVersion);
        sourceProbeManifestDigestSha256 = requireSha256(sourceProbeManifestDigestSha256);
        sourceAssemblyFixtureDigestSha256 = requireSha256(sourceAssemblyFixtureDigestSha256);
        sourceGlueEncodingDigestSha256 = requireSha256(sourceGlueEncodingDigestSha256);
        profileId = requireText("profileId", profileId);
        governor = Objects.requireNonNull(governor, "governor");
        ArrayList<Placement> ordered = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        ordered.sort(Comparator.comparing(Placement::point).thenComparing(Placement::role));
        placements = List.copyOf(ordered);
        powerplantGlueDomain = Objects.requireNonNull(powerplantGlueDomain, "powerplantGlueDomain");
        topologyChecks = Objects.requireNonNull(topologyChecks, "topologyChecks");
        glueChecks = Objects.requireNonNull(glueChecks, "glueChecks");
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
        token(canonical, sourceAssemblyFixtureDigestSha256);
        token(canonical, sourceGlueEncodingDigestSha256);
        token(canonical, profileId);
        token(canonical, governor.toString());
        for (Placement value : placements) {
            token(canonical, value.role());
            token(canonical, value.mode().name());
            token(canonical, value.point().toString());
            token(canonical, value.resourceId());
            new TreeMap<>(value.blockState()).forEach((key, state) -> {
                token(canonical, key);
                token(canonical, state);
            });
            token(canonical, Objects.toString(value.replaces(), ""));
            token(canonical, value.sourceContract());
        }
        token(canonical, Integer.toString(replacementCount));
        token(canonical, Integer.toString(additionCount));
        token(canonical, powerplantGlueDomain.toString());
        token(canonical, topologyChecks.toString());
        token(canonical, glueChecks.toString());
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

    public record Governor(int sourceRpmPerPortableEngine, int firstAcceptedTargetRpm, String higherRpmPointsStatus, List<Integer> higherRpmPoints) {
        public Governor { higherRpmPointsStatus = requireText("higherRpmPointsStatus", higherRpmPointsStatus); higherRpmPoints = List.copyOf(Objects.requireNonNull(higherRpmPoints, "higherRpmPoints")); }
    }
    public record Placement(String role, SkyforgeAircraftPowertrainProfile.Mode mode, AircraftBlockspaceIR.LatticePoint point, String resourceId, Map<String, String> blockState, SkyforgeAircraftPowertrainProfile.ReplaceExpectation replaces, String sourceContract) {
        public Placement {
            role = requireText("placement.role", role);
            mode = Objects.requireNonNull(mode, "mode");
            point = Objects.requireNonNull(point, "point");
            resourceId = requireText("placement.resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            sourceContract = requireText("placement.sourceContract", sourceContract);
        }
    }
    public record GlueDomain(String name, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to, AircraftBlockspaceIR.LatticePoint boundsMin, AircraftBlockspaceIR.LatticePoint boundsMax, AircraftBlockspaceIR.LatticePoint selectionSizeBlocks) {}
    public record TopologyChecks(boolean enginesOpposeAcrossGovernor, boolean governorCogDirectlyAbove, boolean propShaftCollinearWithCogAndBearing, boolean governorReplacesExactlyOneStructuralCell, boolean allOtherPowertrainCellsAreAdditions, boolean noPowertrainCellCrossesPropellerChild) {
        public boolean passed() { return enginesOpposeAcrossGovernor && governorCogDirectlyAbove && propShaftCollinearWithCogAndBearing && governorReplacesExactlyOneStructuralCell && allOtherPowertrainCellsAreAdditions && noPowertrainCellCrossesPropellerChild; }
    }
    public record GlueChecks(boolean powerplantGlueWithinCreateSelectionLimit, boolean powerplantGlueContainsAllPowertrainCells, boolean powerplantGlueExcludesPropellerChild, boolean powerplantGlueOverlapsAcceptedMainGlue) {
        public boolean passed() { return powerplantGlueWithinCreateSelectionLimit && powerplantGlueContainsAllPowertrainCells && powerplantGlueExcludesPropellerChild && powerplantGlueOverlapsAcceptedMainGlue; }
    }
    public record RuntimeObligation(String id, String method, String status) {}
    public record Metrics(int inputManifestPlacementCount, int resultingManifestPlacementCount, int replacementCount, int additionCount, int priorMovingMainBodyPlacementCount, int resultingMovingMainBodyPlacementCount, int nestedPropellerChildPlacementCount, int expectedPrimarySableTransferCount, int runtimeObligationCount, int runtimeObligationVerifiedCount) {}
    public record Readiness(boolean powertrainStaticTopologyPassed, boolean powertrainPlacementPatchReady, boolean governor128RuntimeProbeReady, boolean runtimeQualificationReady, boolean flightQualified, List<String> runtimeBlockers) {
        public Readiness { ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(runtimeBlockers, "runtimeBlockers")); copy.sort(Comparator.naturalOrder()); runtimeBlockers = List.copyOf(copy); }
    }
    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation { scope = requireText("validation scope", scope); doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve")); }
    }
    private static void token(StringBuilder builder, String value) { builder.append(value.length()).append(':').append(value); }
    private static String requireText(String property, String value) { Objects.requireNonNull(value, property); if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank"); return value; }
    private static String requireSha256(String value) { String sha = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT); if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters"); return sha; }
}
