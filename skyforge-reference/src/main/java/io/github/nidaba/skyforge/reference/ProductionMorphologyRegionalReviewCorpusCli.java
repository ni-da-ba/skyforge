package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.reference.evidence.ProductionRegionalMorphologyDiagnostics;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidenceWriter;
import io.github.nidaba.skyforge.reference.volume.ProductionMorphologyRegionalReviewCorpus;
import io.github.nidaba.skyforge.reference.volume.SkyIslandGroupReferenceCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AUTH-0084 regional morphology visual-review atlas for issue #214.
 *
 * <p>The package compares sparse, chain, cluster, Hub, and Arc contexts using only accepted group
 * and archipelago planners. Diagnostic values are evidence only; no aesthetic thresholds are
 * encoded.
 */
public final class ProductionMorphologyRegionalReviewCorpusCli {
    /** Stable evidence identity shared with the regional corpus definition. */
    public static final String EVIDENCE_ID =
            ProductionMorphologyRegionalReviewCorpus.CORPUS_ID;

    private ProductionMorphologyRegionalReviewCorpusCli() {}

    /** Generates all five regional review contexts. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: ProductionMorphologyRegionalReviewCorpusCli [output-directory]");
        }

        Path output =
                arguments.length == 1
                        ? Path.of(arguments[0])
                        : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);
        String version = System.getProperty("skyforge.version", "development");

        LinkedHashMap<String, ContextResult> results = new LinkedHashMap<>();

        SkyIslandGroupEvidenceWriter groupWriter = new SkyIslandGroupEvidenceWriter();
        for (var context : ProductionMorphologyRegionalReviewCorpus.groupContexts()) {
            SkyIslandGroupEvidence evidence = generateGroup(context.request());
            groupWriter.write(evidence, output.resolve(context.id()), version);
            results.put(
                    context.id(),
                    new ContextResult(
                            context.id(),
                            context.kind().name(),
                            1,
                            evidence.plan().memberCount(),
                            evidence.metrics().solidSampleCount(),
                            evidence.metrics().connectedComponents(),
                            evidence.metrics().overlappingSolidSamples(),
                            evidence.metrics().faceContacts(),
                            ProductionRegionalMorphologyDiagnostics.measure(evidence)));
        }

        SkyIslandArchipelagoEvidenceWriter archipelagoWriter =
                new SkyIslandArchipelagoEvidenceWriter();
        for (var context : ProductionMorphologyRegionalReviewCorpus.archipelagoContexts()) {
            SkyIslandArchipelagoEvidence evidence = generateArchipelago(context.request());
            archipelagoWriter.write(evidence, output.resolve(context.id()), version);
            results.put(
                    context.id(),
                    new ContextResult(
                            context.id(),
                            context.kind().name(),
                            evidence.plan().groupCount(),
                            evidence.plan().totalMemberCount(),
                            evidence.metrics().solidSampleCount(),
                            evidence.metrics().connectedComponents(),
                            evidence.metrics().crossGroupOverlappingSolidSamples(),
                            evidence.metrics().faceContacts(),
                            ProductionRegionalMorphologyDiagnostics.measure(evidence)));
        }

        writeSummary(results, output);
        writeMinecraftHandoff(output);
        Files.writeString(
                output.resolve("index.html"),
                galleryHtml(),
                StandardCharsets.UTF_8);

        for (ContextResult result : results.values()) {
            ProductionRegionalMorphologyDiagnostics d = result.diagnostics();
            System.out.printf(
                    "%s: groups=%d islands=%d coverage=%.5f minSep/rSum=%.5f verticalSpan=%.5f distinctMorph=%.5f nearestRepeat=%.5f aspect=%.5f dominantGroup=%.5f%n",
                    result.id(),
                    result.groupCount(),
                    result.islandCount(),
                    d.horizontalCoverageFraction(),
                    d.minimumIslandSeparationRadiusSum(),
                    d.elevationSpanVerticalScale(),
                    d.distinctMorphologyFraction(),
                    d.nearestNeighborMorphologyRepeatFraction(),
                    d.horizontalAspectRatio(),
                    d.dominantGroupSolidShare());
        }
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandGroupEvidence generateGroup(
            io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest request) {
        SkyIslandGroupPlan plan = new SkyIslandGroupPlanner().plan(request);
        var registry = SkyIslandGroupReferenceCorpus.registry();
        var compiled = new SkyIslandMorphologySpecCompiler().compile(plan, registry);
        return new SkyIslandGroupEvidenceGenerator().generate(plan, compiled);
    }

    private static SkyIslandArchipelagoEvidence generateArchipelago(
            io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest
                    request) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        return new SkyIslandArchipelagoEvidenceGenerator()
                .generate(plan, SkyIslandGroupReferenceCorpus.registry());
    }

    private static void writeSummary(
            Map<String, ContextResult> results,
            Path output)
            throws IOException {
        StringBuilder csv =
                new StringBuilder(
                        "context,kind,groupCount,islandCount,solidSamples,components,overlaps,faceContacts,"
                                + "horizontalCoverage,minSeparationRadiusSum,nearestSpacingCv,elevationSpanVerticalScale,"
                                + "distinctMorphologyFraction,dominantMorphologyShare,nearestMorphologyRepeat,"
                                + "horizontalAspectRatio,dominantGroupSolidShare\n");

        for (ContextResult result : results.values()) {
            ProductionRegionalMorphologyDiagnostics d = result.diagnostics();
            csv.append(result.id()).append(',')
                    .append(result.kind()).append(',')
                    .append(result.groupCount()).append(',')
                    .append(result.islandCount()).append(',')
                    .append(result.solidSamples()).append(',')
                    .append(result.connectedComponents()).append(',')
                    .append(result.overlaps()).append(',')
                    .append(result.faceContacts()).append(',')
                    .append(d.horizontalCoverageFraction()).append(',')
                    .append(d.minimumIslandSeparationRadiusSum()).append(',')
                    .append(d.nearestNeighborSpacingCoefficientOfVariation()).append(',')
                    .append(d.elevationSpanVerticalScale()).append(',')
                    .append(d.distinctMorphologyFraction()).append(',')
                    .append(d.dominantMorphologyShare()).append(',')
                    .append(d.nearestNeighborMorphologyRepeatFraction()).append(',')
                    .append(d.horizontalAspectRatio()).append(',')
                    .append(d.dominantGroupSolidShare()).append('\n');
        }

        Files.writeString(
                output.resolve("summary.csv"),
                csv,
                StandardCharsets.UTF_8);
    }

    private static void writeMinecraftHandoff(Path output) throws IOException {
        String csv =
                """
                context,requiredMinecraftViews,requiredRoute,reviewFocus
                sparse,high-altitude-plan|horizon-approach|below,sparse-crossing,negative-space|navigation|island-individuality
                chain,high-altitude-plan|along-chain-approach|below,end-to-end-chain,route-readability|repetition|orientation
                cluster,high-altitude-plan|cluster-approach|below,cluster-perimeter-and-through,occlusion|local-hierarchy|open-sky
                hub,high-altitude-plan|anchor-approach|below,outer-group-to-anchor,anchor-dominance|group-hierarchy|roofing-risk
                arc,high-altitude-plan|corridor-approach|below,end-to-end-arc,corridor-readability|negative-space|repetition
                """;
        Files.writeString(
                output.resolve("minecraft-handoff.csv"),
                csv,
                StandardCharsets.UTF_8);
    }

    private static String galleryHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Skyforge production regional morphology review</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1900px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:1rem}
                figure{margin:0} img{width:100%;border:1px solid #ccc;background:#fafafa} figcaption{font-weight:650;margin:.35rem 0}
                </style></head><body>
                <h1>Production regional morphology review</h1>
                <p>AUTH-0084 / issue #214. Five accepted-planner contexts expose negative space, regional hierarchy, repetition, layering, and approach-route readability. Metrics are descriptive only; human reference + Minecraft review determines aesthetic thresholds.</p>
                """
                + groupSection("sparse", "Sparse mixed-provider formation")
                + groupSection("chain", "Curved seven-island chain")
                + groupSection("cluster", "Organic nine-island cluster")
                + archipelagoSection("hub", "Hub archipelago")
                + archipelagoSection("arc", "Arc archipelago")
                + "</body></html>";
    }

    private static String groupSection(String id, String title) {
        return "<section><h2>" + title + "</h2><div class=\"grid\">"
                + figure(id, "plan.png", "Planner reservation view")
                + figure(id, "top-down-union.png", "Realized top-down union")
                + figure(id, "upper-envelope.png", "Upper elevation envelope")
                + figure(id, "underside-envelope.png", "Underside elevation envelope")
                + figure(id, "east-west.png", "East-west center section")
                + figure(id, "north-south.png", "North-south center section")
                + figure(id, "isometric.png", "Isometric upper-surface view")
                + "</div></section>";
    }

    private static String archipelagoSection(String id, String title) {
        return "<section><h2>" + title + "</h2><div class="grid">"
                + figure(id, "plan.png", "Hierarchical planner view")
                + figure(id, "top-down-groups.png", "Realized top-down geometry by group")
                + figure(id, "upper-envelope.png", "Regional upper envelope")
                + figure(id, "underside-envelope.png", "Regional underside envelope")
                + figure(id, "isometric.png", "Regional isometric view")
                + "</div></section>";
    }

    private static String figure(String id, String file, String caption) {
        return "<figure><figcaption>" + caption + "</figcaption><img src="" + id + "/"
                + file + "" alt="" + caption + ""></figure>";
    }

    private record ContextResult(
            String id,
            String kind,
            int groupCount,
            int islandCount,
            int solidSamples,
            int connectedComponents,
            int overlaps,
            int faceContacts,
            ProductionRegionalMorphologyDiagnostics diagnostics) {}
}
