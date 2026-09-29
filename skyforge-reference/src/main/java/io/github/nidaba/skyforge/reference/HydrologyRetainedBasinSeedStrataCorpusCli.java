package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandContinuousWaterbodyDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandContinuousWaterbodyDiagnosticsPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyCandidate;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fixed seed-stratified discovery evidence for retained open-water calibration. */
public final class HydrologyRetainedBasinSeedStrataCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-retained-basin-seed-strata-v2";
    public static final int EXPECTED_SPECIMEN_COUNT = 6144;
    private static final int FIRST_KEY = 1;
    private static final int LAST_KEY = 1024;
    private static final List<SeedStratum> SEEDS = List.of(
            new SeedStratum("seed-min", Long.MIN_VALUE),
            new SeedStratum("seed-zero", 0L),
            new SeedStratum("seed-skyforge", 0x534B59464F524745L));
    private static final List<Namespace> NAMESPACES = List.of(
            new Namespace("reference-8-81", 8L, 81L),
            new Namespace("reference-6-61", 6L, 61L));

    private HydrologyRetainedBasinSeedStrataCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder specimens = new StringBuilder(
                "seed_id,world_seed_hex,namespace,province,cluster,island_key,morphology,"
                        + "nominal_radius,relief_budget,pond_count,lake_count,wetland_count\n");
        StringBuilder candidates = new StringBuilder(
                "seed_id,world_seed_hex,namespace,province,cluster,island_key,sink_cell,kind,"
                        + "catchment_fraction,relative_inflow,retention,saturation,persistence,basin_scale,"
                        + "area_world2,equivalent_diameter,max_depth_world,depth_to_diameter,"
                        + "max_shoreline_grade,spill_headroom_world,matched_terminal_reaches,"
                        + "max_channel_datum_mismatch_world,reaches_search_boundary,shoreline_crossings\n");
        StringBuilder semantics = new StringBuilder(
                "seed_id,world_seed_hex,namespace,province,cluster,island_key,sink_cell,kind,"
                        + "catchment_cell_count,catchment_fraction,relative_inflow,retention,saturation,"
                        + "persistence,basin_scale\n");
        StringBuilder failures = new StringBuilder(
                "seed_id,world_seed_hex,namespace,province,cluster,island_key,error\n");

        int specimenCount = 0;
        int pondCount = 0;
        int lakeCount = 0;
        int wetlandCount = 0;
        int measuredBasins = 0;
        int geometryFailures = 0;
        Map<String, Integer> stratumCounts = new LinkedHashMap<>();

        for (SeedStratum seed : SEEDS) {
            for (Namespace namespace : NAMESPACES) {
                String stratum = seed.id() + "/" + namespace.id();
                stratumCounts.put(stratum, 0);
                for (int key = FIRST_KEY; key <= LAST_KEY; key++) {
                    SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(
                                    seed.value(), namespace.province(), namespace.cluster(), key));
                    List<SkyIslandWaterbodyCandidate> waterbodies =
                            SkyIslandWaterbodyPlanner.plan(descriptor).candidates();
                    int ponds = count(waterbodies, SkyIslandWaterbodyKind.POND);
                    int lakes = count(waterbodies, SkyIslandWaterbodyKind.LAKE);
                    int wetlands = count(waterbodies, SkyIslandWaterbodyKind.WETLAND);
                    specimenCount++;
                    stratumCounts.merge(stratum, 1, Integer::sum);
                    pondCount += ponds;
                    lakeCount += lakes;
                    wetlandCount += wetlands;
                    specimens.append(seed.id()).append(',')
                            .append(hex(seed.value())).append(',')
                            .append(namespace.id()).append(',')
                            .append(namespace.province()).append(',')
                            .append(namespace.cluster()).append(',')
                            .append(key).append(',')
                            .append(descriptor.morphologyFamily().name().toLowerCase(Locale.ROOT)).append(',')
                            .append(format(descriptor.nominalRadius())).append(',')
                            .append(format(descriptor.reliefBudget())).append(',')
                            .append(ponds).append(',')
                            .append(lakes).append(',')
                            .append(wetlands).append('\n');

                    for (SkyIslandWaterbodyCandidate candidate : waterbodies) {
                        appendSemantic(semantics, seed, namespace, key, candidate);
                    }
                    if (ponds + lakes == 0) {
                        continue;
                    }
                    try {
                        List<SkyIslandContinuousWaterbodyDiagnostics> diagnostics =
                                SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);
                        Map<Integer, SkyIslandContinuousWaterbodyDiagnostics> bySink =
                                new LinkedHashMap<>();
                        for (SkyIslandContinuousWaterbodyDiagnostics diagnostic : diagnostics) {
                            bySink.put(diagnostic.basin().sourceCandidate().sinkCellIndex(), diagnostic);
                        }
                        for (SkyIslandWaterbodyCandidate candidate : waterbodies) {
                            if (candidate.kind() == SkyIslandWaterbodyKind.WETLAND) {
                                continue;
                            }
                            SkyIslandContinuousWaterbodyDiagnostics diagnostic =
                                    bySink.get(candidate.sinkCellIndex());
                            if (diagnostic == null) {
                                throw new IllegalStateException(
                                        "open-water candidate has no continuous diagnostic at sink "
                                                + candidate.sinkCellIndex());
                            }
                            measuredBasins++;
                            appendCandidate(
                                    candidates, seed, namespace, key, candidate, diagnostic);
                        }
                    } catch (RuntimeException exception) {
                        geometryFailures++;
                        failures.append(seed.id()).append(',')
                                .append(hex(seed.value())).append(',')
                                .append(namespace.id()).append(',')
                                .append(namespace.province()).append(',')
                                .append(namespace.cluster()).append(',')
                                .append(key).append(',')
                                .append(csv(exception.getClass().getSimpleName() + ": "
                                        + String.valueOf(exception.getMessage())))
                                .append('\n');
                    }
                }
            }
        }
        if (specimenCount != EXPECTED_SPECIMEN_COUNT
                || stratumCounts.values().stream().anyMatch(count -> count != 1024)) {
            throw new IllegalStateException("fixed seed-strata sample is incomplete");
        }

        Files.writeString(out.resolve("specimens.csv"), specimens, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("candidates.csv"), candidates, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("semantics.csv"), semantics, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("geometry-failures.csv"), failures, StandardCharsets.UTF_8);
        StringBuilder summary = new StringBuilder(
                "metric,value\n"
                        + "sample_design,3 seed strata x 2 fixed namespaces x 1024 keys\n"
                        + "specimens,").append(specimenCount).append('\n')
                .append("pond_candidates,").append(pondCount).append('\n')
                .append("lake_candidates,").append(lakeCount).append('\n')
                .append("wetland_candidates,").append(wetlandCount).append('\n')
                .append("continuous_basins_measured,").append(measuredBasins).append('\n')
                .append("geometry_failure_specimens,").append(geometryFailures).append('\n')
                .append("prevalence_estimate,NOT_CLAIMED\n")
                .append("production_qualification,NOT_GRANTED\n");
        for (Map.Entry<String, Integer> entry : stratumCounts.entrySet()) {
            summary.append("stratum_").append(entry.getKey()).append(',')
                    .append(entry.getValue()).append('\n');
        }
        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Retained open-water seed-strata discovery v2

                This is a deterministic candidate-discovery scan, not a prevalence estimate, basin
                calibration, threshold change, terrain authority, or Minecraft authorization.
                It holds the two existing reference identity namespaces fixed and varies only the
                three canonical signed seed strata (minimum, zero, Skyforge) over island keys 1..1024.
                Existing POND/LAKE classification is unchanged. Continuous E1 geometry diagnostics
                are measured only for identities that yield an existing open-water candidate.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
    }

    private static void appendSemantic(
            StringBuilder out,
            SeedStratum seed,
            Namespace namespace,
            int key,
            SkyIslandWaterbodyCandidate candidate) {
        out.append(seed.id()).append(',')
                .append(hex(seed.value())).append(',')
                .append(namespace.id()).append(',')
                .append(namespace.province()).append(',')
                .append(namespace.cluster()).append(',')
                .append(key).append(',')
                .append(candidate.sinkCellIndex()).append(',')
                .append(candidate.kind().name().toLowerCase(Locale.ROOT)).append(',')
                .append(candidate.catchmentCellCount()).append(',')
                .append(format(candidate.catchmentFraction())).append(',')
                .append(format(candidate.relativeInflow())).append(',')
                .append(format(candidate.retentionPotential())).append(',')
                .append(format(candidate.saturationPotential())).append(',')
                .append(format(candidate.persistence())).append(',')
                .append(format(candidate.basinScale())).append('\n');
    }

    private static void appendCandidate(
            StringBuilder out,
            SeedStratum seed,
            Namespace namespace,
            int key,
            SkyIslandWaterbodyCandidate candidate,
            SkyIslandContinuousWaterbodyDiagnostics d) {
        out.append(seed.id()).append(',')
                .append(hex(seed.value())).append(',')
                .append(namespace.id()).append(',')
                .append(namespace.province()).append(',')
                .append(namespace.cluster()).append(',')
                .append(key).append(',')
                .append(candidate.sinkCellIndex()).append(',')
                .append(candidate.kind().name().toLowerCase(Locale.ROOT)).append(',')
                .append(format(candidate.catchmentFraction())).append(',')
                .append(format(candidate.relativeInflow())).append(',')
                .append(format(candidate.retentionPotential())).append(',')
                .append(format(candidate.saturationPotential())).append(',')
                .append(format(candidate.persistence())).append(',')
                .append(format(candidate.basinScale())).append(',')
                .append(format(d.basin().approximateArea())).append(',')
                .append(format(d.equivalentDiameter())).append(',')
                .append(format(d.maximumDepthWorldUnits())).append(',')
                .append(format(d.depthToEquivalentDiameterRatio())).append(',')
                .append(format(d.maximumShorelineGrade())).append(',')
                .append(format(d.spillHeadroomWorldUnits())).append(',')
                .append(d.matchedTerminalReachCount()).append(',')
                .append(format(d.maximumChannelDatumMismatchWorldUnits())).append(',')
                .append(d.reachesSearchBoundary()).append(',')
                .append(d.shorelineCrossingCount()).append('\n');
    }

    private static int count(
            List<SkyIslandWaterbodyCandidate> candidates, SkyIslandWaterbodyKind kind) {
        return (int) candidates.stream().filter(candidate -> candidate.kind() == kind).count();
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "%016x", value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\"";
    }

    private record SeedStratum(String id, long value) {}
    private record Namespace(String id, long province, long cluster) {}
}
