package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.recipes.island.SeededIslandRecipe;
import io.github.nidaba.skyforge.reference.benchmark.ReferenceBenchmarkObservation;
import io.github.nidaba.skyforge.reference.evidence.EvidencePackageWriter;
import io.github.nidaba.skyforge.reference.evidence.GridStatistics;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidence;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.IslandMetrics;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Generates the complete canonical fixed-seed evidence, visual atlas, and benchmark report. */
public final class FixedSeedCorpusGenerator {
    /** Exact member artifacts whose bytes form the normative fixed-seed corpus. */
    public static final List<String> NORMATIVE_MEMBER_ARTIFACTS = List.of(
            "descriptor.json",
            "height-graph.json",
            "density-graph.json",
            "height.grid",
            "land-mask.grid",
            "slope.grid",
            "east-west.csv",
            "north-south.csv");

    /** Schema of the deterministic corpus-level manifest. */
    public static final int CORPUS_MANIFEST_SCHEMA_VERSION = 1;

    /** Schema of the explicitly noncanonical benchmark observation report. */
    public static final int BENCHMARK_SCHEMA_VERSION = 1;

    private final SeededIslandRecipe recipe = new SeededIslandRecipe();
    private final IslandEvidenceGenerator evidenceGenerator = new IslandEvidenceGenerator();
    private final EvidencePackageWriter packageWriter = new EvidencePackageWriter();

    /** Generates every standard member into one reviewable directory. */
    public Result generate(Path directory, String engineVersion) throws IOException {
        Objects.requireNonNull(directory, "directory");
        requireText("engineVersion", engineVersion);
        Files.createDirectories(directory);
        List<MemberResult> members = new ArrayList<>();

        for (FixedSeedReferenceCorpus.Member member : FixedSeedReferenceCorpus.members()) {
            IslandDescriptor descriptor = FixedSeedReferenceCorpus.descriptor(member);
            CompiledIsland compiled = recipe.compile(descriptor);
            GridSpec grid = evidenceGenerator.standardGrid(descriptor);
            long started = System.nanoTime();
            ScalarGrid height = evidenceGenerator.sampleHeight(compiled, grid, SamplingOrder.FORWARD);
            long wallTime = Math.max(1L, System.nanoTime() - started);
            double throughput = grid.sampleCount() * 1_000_000_000.0 / wallTime;
            ReferenceBenchmarkObservation benchmark = new ReferenceBenchmarkObservation(
                    member.id(), member.seed(), grid.sampleCount(), wallTime, throughput);
            IslandEvidence evidence = evidenceGenerator.generateFromHeight(compiled, height);
            Path memberDirectory = directory.resolve(member.id());
            packageWriter.write(evidence, memberDirectory, engineVersion, member.id());
            members.add(new MemberResult(member, memberDirectory, evidence, benchmark));
        }

        Path manifest = directory.resolve("corpus-manifest.json");
        Files.writeString(manifest, corpusManifest(members), StandardCharsets.UTF_8);
        Path benchmark = directory.resolve("benchmark.json");
        Files.writeString(benchmark, benchmarkJson(engineVersion, members), StandardCharsets.UTF_8);
        Path gallery = directory.resolve("index.html");
        Files.writeString(gallery, galleryHtml(members), StandardCharsets.UTF_8);
        Path actualChecksums = directory.resolve("corpus.sha256");
        FixedSeedCorpusVerifier.writeChecksums(directory, actualChecksums);
        return new Result(directory, manifest, benchmark, gallery, actualChecksums, members);
    }

    private static String corpusManifest(List<MemberResult> members) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":").append(CORPUS_MANIFEST_SCHEMA_VERSION);
        json.append(",\"corpusId\":\"").append(FixedSeedReferenceCorpus.CORPUS_ID).append('\"');
        json.append(",\"sampling\":{\"resolution\":")
                .append(IslandEvidenceGenerator.STANDARD_RESOLUTION)
                .append(",\"halfWidthFactor\":\"")
                .append(Double.toHexString(IslandEvidenceGenerator.STANDARD_HALF_WIDTH_FACTOR))
                .append("\"},\"members\":[");
        for (int index = 0; index < members.size(); index++) {
            if (index != 0) {
                json.append(',');
            }
            MemberResult member = members.get(index);
            IslandEvidence evidence = member.evidence();
            json.append("{\"id\":\"").append(member.member().id()).append('\"');
            json.append(",\"seed\":\"0x")
                    .append(HexFormat.of().toHexDigits(member.member().seed())).append('\"');
            json.append(",\"artifacts\":{");
            for (int artifactIndex = 0; artifactIndex < NORMATIVE_MEMBER_ARTIFACTS.size(); artifactIndex++) {
                if (artifactIndex != 0) {
                    json.append(',');
                }
                String artifact = NORMATIVE_MEMBER_ARTIFACTS.get(artifactIndex);
                json.append('\"').append(artifact).append("\":\"")
                        .append(sha256(member.memberDirectory().resolve(artifact))).append('\"');
            }
            json.append("},\"heightStatistics\":");
            appendStatistics(json, evidence.heightStatistics());
            json.append(",\"slopeStatistics\":");
            appendStatistics(json, evidence.slopeStatistics());
            json.append(",\"morphology\":");
            appendMetrics(json, evidence.metrics());
            json.append('}');
        }
        return json.append("]}\n").toString();
    }

    private static String benchmarkJson(String engineVersion, List<MemberResult> members) {
        Runtime runtime = Runtime.getRuntime();
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":").append(BENCHMARK_SCHEMA_VERSION);
        json.append(",\"status\":\"observational-no-pass-fail-threshold\"");
        json.append(",\"engineVersion\":");
        appendJsonString(json, engineVersion);
        json.append(",\"environment\":{");
        appendJsonMember(json, "javaVersion", System.getProperty("java.version"), false);
        appendJsonMember(json, "javaVendor", System.getProperty("java.vendor"), true);
        appendJsonMember(json, "vmName", System.getProperty("java.vm.name"), true);
        appendJsonMember(json, "osName", System.getProperty("os.name"), true);
        appendJsonMember(json, "osVersion", System.getProperty("os.version"), true);
        appendJsonMember(json, "osArchitecture", System.getProperty("os.arch"), true);
        json.append(",\"availableProcessors\":").append(runtime.availableProcessors());
        json.append(",\"maximumHeapBytes\":").append(runtime.maxMemory()).append('}');
        json.append(",\"method\":\"single-forward-1024x1024-height-grid-no-warmup\"");
        json.append(",\"observations\":[");
        long totalSamples = 0L;
        long totalNanoseconds = 0L;
        for (int index = 0; index < members.size(); index++) {
            if (index != 0) {
                json.append(',');
            }
            ReferenceBenchmarkObservation observation = members.get(index).benchmark();
            totalSamples += observation.sampleCount();
            totalNanoseconds += observation.wallTimeNanoseconds();
            json.append("{\"memberId\":\"").append(observation.memberId()).append('\"');
            json.append(",\"rootSeed\":\"0x")
                    .append(HexFormat.of().toHexDigits(observation.rootSeed())).append('\"');
            json.append(",\"sampleCount\":").append(observation.sampleCount());
            json.append(",\"wallTimeNanoseconds\":").append(observation.wallTimeNanoseconds());
            json.append(",\"samplesPerSecond\":\"")
                    .append(Double.toHexString(observation.samplesPerSecond())).append("\"}");
        }
        double aggregateThroughput = totalSamples * 1_000_000_000.0 / totalNanoseconds;
        json.append("],\"aggregate\":{\"sampleCount\":").append(totalSamples);
        json.append(",\"wallTimeNanoseconds\":").append(totalNanoseconds);
        json.append(",\"samplesPerSecond\":\"")
                .append(Double.toHexString(aggregateThroughput)).append("\"}}\n");
        return json.toString();
    }

    private static String galleryHtml(List<MemberResult> members) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        html.append("<title>Skyforge fixed-seed island corpus v1</title><style>");
        html.append("body{margin:0;background:#0b1118;color:#e9f1f7;font:15px system-ui,sans-serif}");
        html.append("header{padding:28px 32px;background:#111d28;border-bottom:1px solid #294052}");
        html.append("h1{margin:0 0 8px;font-size:28px}p{color:#aec2d1;margin:4px 0}");
        html.append("main{padding:24px 32px;display:grid;gap:28px}.member{background:#111d28;border:1px solid #294052;border-radius:10px;padding:18px}");
        html.append("h2{margin:0 0 6px;font-size:19px}.meta{font-family:ui-monospace,monospace;color:#8eb8d3}");
        html.append(".maps{display:grid;grid-template-columns:repeat(3,minmax(180px,1fr));gap:14px;margin-top:16px}");
        html.append(".sections{display:grid;grid-template-columns:repeat(2,minmax(240px,1fr));gap:14px;margin-top:14px}");
        html.append("figure{margin:0;background:#081018;padding:10px;border-radius:6px}img{display:block;width:100%;height:auto;image-rendering:auto}");
        html.append("figcaption{margin-top:7px;color:#aec2d1}@media(max-width:800px){.maps,.sections{grid-template-columns:1fr}main,header{padding-left:16px;padding-right:16px}}");
        html.append("</style></head><body><header><h1>Skyforge fixed-seed island corpus v1</h1>");
        html.append("<p>Six deterministic seeded realizations of one preserved primary morphology.</p>");
        html.append("<p>Images are review projections; canonical grids and checksums remain authoritative.</p></header><main>");
        for (MemberResult member : members) {
            IslandEvidence evidence = member.evidence();
            html.append("<section class=\"member\"><h2>").append(member.member().id()).append("</h2>");
            html.append("<p class=\"meta\">seed 0x")
                    .append(HexFormat.of().toHexDigits(member.member().seed()))
                    .append(" | peak ")
                    .append(String.format(Locale.ROOT, "%.5f", evidence.heightStatistics().maximum()))
                    .append(" | land area ")
                    .append(String.format(Locale.ROOT, "%.2f", evidence.metrics().estimatedLandArea()))
                    .append("</p><div class=\"maps\">");
            appendFigure(html, member.member().id(), "height.png", "Height");
            appendFigure(html, member.member().id(), "land-mask.png", "Land mask");
            appendFigure(html, member.member().id(), "slope.png", "Slope magnitude");
            html.append("</div><div class=\"sections\">");
            appendFigure(html, member.member().id(), "east-west.png", "East-west section");
            appendFigure(html, member.member().id(), "north-south.png", "North-south section");
            html.append("</div></section>");
        }
        return html.append("</main></body></html>\n").toString();
    }

    private static void appendFigure(StringBuilder html, String directory, String file, String caption) {
        html.append("<figure><img src=\"").append(directory).append('/').append(file)
                .append("\" alt=\"").append(caption).append(" for ").append(directory)
                .append("\"><figcaption>").append(caption).append("</figcaption></figure>");
    }

    private static void appendStatistics(StringBuilder json, GridStatistics statistics) {
        json.append("{\"minimum\":\"").append(Double.toHexString(statistics.minimum())).append('\"');
        json.append(",\"maximum\":\"").append(Double.toHexString(statistics.maximum())).append('\"');
        json.append(",\"mean\":\"").append(Double.toHexString(statistics.mean())).append('\"');
        json.append(",\"sampleCount\":").append(statistics.sampleCount()).append('}');
    }

    private static void appendMetrics(StringBuilder json, IslandMetrics metrics) {
        json.append("{\"landSampleCount\":").append(metrics.landSampleCount());
        json.append(",\"connectedLandComponents\":").append(metrics.connectedLandComponents());
        json.append(",\"boundaryLandSampleCount\":").append(metrics.boundaryLandSampleCount());
        json.append(",\"estimatedLandArea\":\"")
                .append(Double.toHexString(metrics.estimatedLandArea())).append('\"');
        json.append(",\"landCentroidX\":\"")
                .append(Double.toHexString(metrics.landCentroidX())).append('\"');
        json.append(",\"landCentroidZ\":\"")
                .append(Double.toHexString(metrics.landCentroidZ())).append("\"}");
    }

    private static void appendJsonMember(
            StringBuilder json, String name, String value, boolean comma) {
        if (comma) {
            json.append(',');
        }
        json.append('\"').append(name).append("\":");
        appendJsonString(json, value);
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('\"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\"' || character == '\\') {
                json.append('\\');
            }
            if (character >= 0x20) {
                json.append(character);
            }
        }
        json.append('\"');
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
    }

    /** Complete paths and observations produced by one corpus run. */
    public record Result(
            Path directory,
            Path manifest,
            Path benchmark,
            Path gallery,
            Path checksums,
            List<MemberResult> members) {
        /** Defensively freezes the member order. */
        public Result {
            members = List.copyOf(members);
        }
    }

    /** Evidence and benchmark output for one stable corpus member. */
    public record MemberResult(
            FixedSeedReferenceCorpus.Member member,
            Path memberDirectory,
            IslandEvidence evidence,
            ReferenceBenchmarkObservation benchmark) {}
}
