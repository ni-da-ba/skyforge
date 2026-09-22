package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposurePlanner;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Lightweight semantic population audit for the post-platform DR-70 integration gate.
 *
 * <p>This deliberately stops before Minecraft block realization. It measures accepted island-level
 * authorship outputs and freezes a deterministic human-review sample without seed hunting.
 */public final class Dr70IslandPopulationAuditCli {
    public static final String EVIDENCE_ID = "dr70-island-population-audit-v1";
    static final int DEFAULT_POPULATION_COUNT = 4096;
    static final int DEFAULT_REVIEW_COUNT = 100;

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final int BASELINE_PERCENT = 52;

    private static final List<Stratum> STRATA = List.of(
            new Stratum("no-channel", row -> row.retainedReaches() == 0),
            new Stratum("substantial-channel", row -> row.retainedReaches() >= 20),
            new Stratum("retained-water", row -> row.retainedWaterbodies() > 0),
            new Stratum("cave-sealed", row -> row.caveSystems() > 0 && row.exposedCaveSystems() == 0),
            new Stratum("cave-upper", row -> row.upperSurfaceOpenings() > 0),
            new Stratum("cave-underside", row -> row.undersideOpenings() > 0),
            new Stratum("channel-and-cave", row -> row.retainedReaches() > 0 && row.caveSystems() > 0),
            new Stratum("channel-and-upper", row -> row.retainedReaches() > 0 && row.upperSurfaceOpenings() > 0),
            new Stratum("channel-and-underside", row -> row.retainedReaches() > 0 && row.undersideOpenings() > 0),
            new Stratum("channel-water-cave", row -> row.retainedReaches() > 0
                    && row.retainedWaterbodies() > 0 && row.caveSystems() > 0),
            new Stratum("wet-large-relief-channel", row -> wetLargeRelief(row) && row.retainedReaches() > 0),
            new Stratum("wet-large-relief-no-channel", row -> wetLargeRelief(row) && row.retainedReaches() == 0));

    private Dr70IslandPopulationAuditCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        AuditResult result = generate(out, DEFAULT_POPULATION_COUNT, DEFAULT_REVIEW_COUNT);
        System.out.print(result.summaryText());
        System.out.println("DR70_REVIEW_CORPUS_BEGIN");
        System.out.print(Files.readString(out.resolve("review-corpus.csv"), StandardCharsets.UTF_8));
        System.out.println("DR70_REVIEW_CORPUS_END");
    }    static AuditResult generate(Path out, int populationCount, int reviewCount) throws IOException {
        if (Integer.bitCount(populationCount) != 1 || populationCount < 16) {
            throw new IllegalArgumentException("populationCount must be a power of two >= 16");
        }
        if (reviewCount <= 0 || reviewCount > populationCount) {
            throw new IllegalArgumentException("reviewCount must be in (0, populationCount]");
        }
        Files.createDirectories(out);

        List<Row> population = IntStream.range(0, populationCount)
                .parallel()
                .mapToObj(Dr70IslandPopulationAuditCli::characterize)
                .sorted(Comparator.comparingLong(Row::islandKey))
                .toList();
        List<ReviewSelection> review = selectReview(population, reviewCount);
        String summary = summary(population, review);

        Files.writeString(out.resolve("population.csv"), populationCsv(population), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("review-corpus.csv"), reviewCsv(review), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        return new AuditResult(population.size(), review.size(), summary, review);
    }

    private static Row characterize(int key) {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, key));
        var channels = SkyIslandCoherentChannelPlanner.plan(descriptor);
        var waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        var exposure = SkyIslandCaveExposurePlanner.plan(descriptor);
        var topology = exposure.geometry().topology();

        return new Row(
                key,
                descriptor.morphologyFamily(),
                descriptor.nominalRadius(),
                descriptor.reliefBudget(),
                descriptor.moistureTendency(),
                descriptor.permeability(),
                descriptor.hydrologicalPotential(),
                descriptor.ecologicalPotential(),
                channels.retainedComponentCount(),
                channels.retainedReachCount(),
                waterbodies.footprints().size(),
                topology.systems().size(),
                topology.waterInfluencedSystemCount(),
                exposure.exposedSystemCount(),
                exposure.upperSurfaceCount(),
                exposure.undersideCount());
    }    private static List<ReviewSelection> selectReview(List<Row> population, int reviewCount) {
        Map<Long, ReviewSelection> selected = new LinkedHashMap<>();
        int baselineTarget = Math.min(reviewCount, (reviewCount * BASELINE_PERCENT + 99) / 100);

        for (int i = 0; selected.size() < baselineTarget && i < population.size(); i++) {
            int key = (811 + i * 1093) & (population.size() - 1);
            Row row = population.get(key);
            selected.put(row.islandKey(), new ReviewSelection("unfiltered", row));
        }

        int remaining = reviewCount - selected.size();
        int basePerStratum = remaining / STRATA.size();
        int extra = remaining % STRATA.size();
        Comparator<Row> selectionOrder = (left, right) -> {
            int compare = Long.compareUnsigned(selectionRank(left.islandKey()), selectionRank(right.islandKey()));
            return compare != 0 ? compare : Long.compare(left.islandKey(), right.islandKey());
        };

        for (int index = 0; index < STRATA.size(); index++) {
            Stratum stratum = STRATA.get(index);
            int target = basePerStratum + (index < extra ? 1 : 0);
            int added = 0;
            for (Row row : population.stream().filter(stratum.predicate()).sorted(selectionOrder).toList()) {
                if (selected.containsKey(row.islandKey())) {
                    continue;
                }
                selected.put(row.islandKey(), new ReviewSelection(stratum.name(), row));
                if (++added == target || selected.size() == reviewCount) {
                    break;
                }
            }
        }

        for (int i = 0; selected.size() < reviewCount && i < population.size(); i++) {
            int key = (811 + i * 1093) & (population.size() - 1);
            Row row = population.get(key);
            selected.putIfAbsent(row.islandKey(), new ReviewSelection("deterministic-fill", row));
        }
        if (selected.size() != reviewCount) {
            throw new IllegalStateException("unable to freeze requested review corpus: " + selected.size());
        }
        return List.copyOf(selected.values());
    }    private static String populationCsv(List<Row> population) {
        StringBuilder csv = new StringBuilder(
                "islandKey,morphology,radius,reliefBudget,moisture,permeability,hydrologicalPotential,"
                        + "ecologicalPotential,retainedComponents,retainedReaches,retainedWaterbodies,"
                        + "caveSystems,waterInfluencedCaveSystems,exposedCaveSystems,"
                        + "upperSurfaceOpenings,undersideOpenings\n");
        for (Row row : population) {
            appendRow(csv, row);
        }
        return csv.toString();
    }

    private static String reviewCsv(List<ReviewSelection> review) {
        StringBuilder csv = new StringBuilder(
                "selectionBucket,islandKey,morphology,radius,reliefBudget,moisture,permeability,"
                        + "hydrologicalPotential,ecologicalPotential,retainedComponents,retainedReaches,"
                        + "retainedWaterbodies,caveSystems,waterInfluencedCaveSystems,exposedCaveSystems,"
                        + "upperSurfaceOpenings,undersideOpenings\n");
        for (ReviewSelection selection : review) {
            csv.append(selection.bucket()).append(',');
            appendRow(csv, selection.row());
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, Row row) {
        csv.append(row.islandKey()).append(',')
                .append(row.morphology().identifier()).append(',')
                .append(format(row.radius())).append(',')
                .append(format(row.reliefBudget())).append(',')
                .append(format(row.moisture())).append(',')
                .append(format(row.permeability())).append(',')
                .append(format(row.hydrologicalPotential())).append(',')
                .append(format(row.ecologicalPotential())).append(',')
                .append(row.retainedComponents()).append(',')
                .append(row.retainedReaches()).append(',')
                .append(row.retainedWaterbodies()).append(',')
                .append(row.caveSystems()).append(',')
                .append(row.waterInfluencedCaveSystems()).append(',')
                .append(row.exposedCaveSystems()).append(',')
                .append(row.upperSurfaceOpenings()).append(',')
                .append(row.undersideOpenings()).append('\n');
    }    private static String summary(List<Row> population, List<ReviewSelection> review) {
        StringBuilder out = new StringBuilder();
        out.append("DR-70 ISLAND POPULATION AUDIT\n");
        out.append("population=").append(population.size()).append('\n');
        out.append("reviewCorpus=").append(review.size()).append('\n');
        appendMetric(out, "anyCoherentChannel", population, row -> row.retainedReaches() > 0);
        appendMetric(out, "substantialChannel20Plus", population, row -> row.retainedReaches() >= 20);
        appendMetric(out, "retainedWaterbody", population, row -> row.retainedWaterbodies() > 0);
        appendMetric(out, "caveBearing", population, row -> row.caveSystems() > 0);
        appendMetric(out, "caveExposed", population, row -> row.exposedCaveSystems() > 0);
        appendMetric(out, "upperSurfaceOpening", population, row -> row.upperSurfaceOpenings() > 0);
        appendMetric(out, "undersideOpening", population, row -> row.undersideOpenings() > 0);
        appendMetric(out, "channelAndCave", population,
                row -> row.retainedReaches() > 0 && row.caveSystems() > 0);
        appendMetric(out, "channelAndExposure", population,
                row -> row.retainedReaches() > 0 && row.exposedCaveSystems() > 0);
        appendMetric(out, "channelAndWaterbody", population,
                row -> row.retainedReaches() > 0 && row.retainedWaterbodies() > 0);
        appendMetric(out, "channelWaterbodyCave", population,
                row -> row.retainedReaches() > 0 && row.retainedWaterbodies() > 0 && row.caveSystems() > 0);

        List<Row> wetLargeRelief = population.stream().filter(Dr70IslandPopulationAuditCli::wetLargeRelief).toList();
        appendMetric(out, "wetLargeRelief.channel", wetLargeRelief, row -> row.retainedReaches() > 0);
        appendMetric(out, "wetLargeRelief.waterbody", wetLargeRelief, row -> row.retainedWaterbodies() > 0);
        appendMetric(out, "wetLargeRelief.cave", wetLargeRelief, row -> row.caveSystems() > 0);

        for (SkyIslandMorphologyFamily family : SkyIslandMorphologyFamily.values()) {
            List<Row> rows = population.stream().filter(row -> row.morphology() == family).toList();
            appendMetric(out, "morphology." + family.identifier() + ".channel", rows, row -> row.retainedReaches() > 0);
            appendMetric(out, "morphology." + family.identifier() + ".cave", rows, row -> row.caveSystems() > 0);
        }
        return out.toString();
    }    private static void appendMetric(
            StringBuilder out,
            String name,
            List<Row> population,
            Predicate<Row> predicate) {
        long count = population.stream().filter(predicate).count();
        out.append(name).append('=')
                .append(count).append('/')
                .append(population.size()).append(" (")
                .append(population.isEmpty() ? "n/a" : formatPercent(count, population.size()))
                .append(")\n");
    }

    private static boolean wetLargeRelief(Row row) {
        return row.moisture() >= 0.65
                && row.radius() >= 160.0
                && row.reliefBudget() >= 100.0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatPercent(long count, long total) {
        return String.format(Locale.ROOT, "%.2f%%", 100.0 * count / total);
    }

    private static long selectionRank(long key) {
        return mix64(key ^ 0x4452373041554449L);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    record AuditResult(
            int populationCount,
            int reviewCount,
            String summaryText,
            List<ReviewSelection> reviewSelections) {}

    record ReviewSelection(String bucket, Row row) {}

    private record Stratum(String name, Predicate<Row> predicate) {}

    record Row(
            long islandKey,
            SkyIslandMorphologyFamily morphology,
            double radius,
            double reliefBudget,
            double moisture,
            double permeability,
            double hydrologicalPotential,
            double ecologicalPotential,
            int retainedComponents,
            int retainedReaches,
            int retainedWaterbodies,
            int caveSystems,
            long waterInfluencedCaveSystems,
            int exposedCaveSystems,
            long upperSurfaceOpenings,
            long undersideOpenings) {}
}
