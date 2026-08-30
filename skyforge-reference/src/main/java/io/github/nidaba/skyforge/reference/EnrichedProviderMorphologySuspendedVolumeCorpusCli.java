package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderHybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderHybridMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.HybridMorphologyReferenceCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates the SF-IMP-0025 enriched custom-provider and custom-to-built-in review atlas. */
public final class EnrichedProviderMorphologySuspendedVolumeCorpusCli {
    /** Stable evidence identifier for provider-aware enrichment review. */
    public static final String EVIDENCE_ID = "enriched-provider-morphology-suspended-volume-v1";

    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final double[] BUILT_IN_WEIGHTS = {0.25, 0.50, 0.75};
    private static final VolumeGridSpec REVIEW_GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 97, 65, 97);

    private EnrichedProviderMorphologySuspendedVolumeCorpusCli() {}

    /** Generates one enriched custom endpoint and fifteen enriched custom-to-built-in hybrids. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: EnrichedProviderMorphologySuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(SKYFORGE_SEED);
        ReferenceCrescentMorphologyProvider crescent = new ReferenceCrescentMorphologyProvider();
        SkyIslandMorphologyProviderRegistry registry = registry(crescent);
        EnrichedProviderHybridMorphologySkyIslandVolumeRecipe recipe =
                new EnrichedProviderHybridMorphologySkyIslandVolumeRecipe();

        List<MemberResult> results = new ArrayList<>();
        MorphologyProviderBlend crescentEndpoint = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID,
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0);
        results.add(writeMember(
                "crescent-enriched-standalone",
                "crescent",
                0,
                recipe.compile(
                        descriptor,
                        ProviderHybridMorphologyEnrichment.full(crescentEndpoint),
                        registry),
                output,
                version));

        for (MorphologyFamily family : MorphologyFamily.values()) {
            for (double builtInWeight : BUILT_IN_WEIGHTS) {
                int percent = (int) Math.round(100.0 * builtInWeight);
                MorphologyProviderBlend blend = new MorphologyProviderBlend(
                        ReferenceCrescentMorphologyProvider.ID,
                        SkyIslandMorphologyProviders.builtInId(family),
                        builtInWeight);
                CompiledSkyIslandVolume compiled = recipe.compile(
                        descriptor,
                        ProviderHybridMorphologyEnrichment.full(blend),
                        registry);
                results.add(writeMember(
                        "crescent-to-" + family.identifier() + "-enriched-built-in-" + percent,
                        family.identifier(),
                        percent,
                        compiled,
                        output,
                        version));
            }
        }

        writeSummary(results, output);
        Files.writeString(output.resolve("index.html"), galleryHtml(), StandardCharsets.UTF_8);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandMorphologyProviderRegistry registry(
            ReferenceCrescentMorphologyProvider crescent) {
        SkyIslandMorphologyProviderRegistry.Builder builder =
                SkyIslandMorphologyProviderRegistry.builder();
        for (SkyIslandMorphologyProvider provider : SkyIslandMorphologyProviders.builtInRegistry().providers()) {
            builder.register(provider);
        }
        return builder.register(crescent).build();
    }

    private static MemberResult writeMember(
            String id,
            String target,
            int builtInPercent,
            CompiledSkyIslandVolume compiled,
            Path output,
            String version) throws IOException {
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, REVIEW_GRID, SamplingOrder.FORWARD);
        new SuspendedVolumeEvidenceWriter().write(evidence, output.resolve(id), version);
        VolumeMetrics metrics = evidence.metrics();
        System.out.printf(
                "%s: solid=%d; components=%d; faceContacts=%d; minimumClearance=%.3f%n",
                id,
                metrics.solidSampleCount(),
                metrics.connectedSolidComponents(),
                metrics.faceContacts().total(),
                metrics.airClearance().minimum());
        return new MemberResult(id, target, builtInPercent, metrics);
    }

    private static void writeSummary(List<MemberResult> results, Path output) throws IOException {
        StringBuilder summary = new StringBuilder(
                "member,target,builtInPercent,detailAmplitude,secondaryAmplitude,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");
        for (MemberResult result : results) {
            VolumeMetrics metrics = result.metrics();
            summary.append(result.id()).append(',')
                    .append(result.target()).append(',')
                    .append(result.builtInPercent()).append(',')
                    .append("1.0,1.0,")
                    .append(SKYFORGE_SEED).append(',')
                    .append(metrics.solidSampleCount()).append(',')
                    .append(metrics.connectedSolidComponents()).append(',')
                    .append(metrics.faceContacts().total()).append(',')
                    .append(metrics.airClearance().minimum()).append(',')
                    .append(metrics.bounds().minimumX()).append(',')
                    .append(metrics.bounds().maximumX()).append(',')
                    .append(metrics.bounds().minimumY()).append(',')
                    .append(metrics.bounds().maximumY()).append(',')
                    .append(metrics.bounds().minimumZ()).append(',')
                    .append(metrics.bounds().maximumZ()).append('\n');
        }
        Files.writeString(output.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
    }

    private static String galleryHtml() {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>Skyforge enriched provider morphology atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1800px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .progression{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem}
                article{padding:.75rem;border:1px solid #ddd;background:#fafafa}
                .images{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.6rem}
                figure{margin:0} img{width:100%;border:1px solid #ddd} figcaption{font-weight:600;margin:.25rem 0;font-size:.9rem}
                @media(max-width:1100px){.progression{grid-template-columns:1fr}}
                </style></head><body>
                <h1>Provider-aware enrichment visual proof</h1>
                <p>SF-IMP-0025 review. Every specimen uses full bounded detail and full provider-aware secondary morphology. The custom endpoint uses reference:crescent; each progression moves from 25% to 75% contribution from one accepted built-in provider.</p>
                <section><h2>Enriched custom provider endpoint: reference:crescent</h2><div class="progression"><article><div class="images">
                """);
        appendFigures(html, "crescent-enriched-standalone");
        html.append("</div></article></div></section>");

        for (MorphologyFamily family : MorphologyFamily.values()) {
            html.append("<section><h2>reference:crescent → skyforge:")
                    .append(family.identifier())
                    .append("</h2><div class=\"progression\">");
            for (double weight : BUILT_IN_WEIGHTS) {
                int percent = (int) Math.round(100.0 * weight);
                String id = "crescent-to-" + family.identifier() + "-enriched-built-in-" + percent;
                html.append("<article><h3>")
                        .append(percent)
                        .append("% built-in contribution</h3><div class=\"images\">");
                appendFigures(html, id);
                html.append("</div></article>");
            }
            html.append("</div></section>");
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendFigures(StringBuilder html, String member) {
        appendFigure(html, member, "suspension-occupancy.png", "Suspension-plane silhouette");
        appendFigure(html, member, "upper-surface.png", "Upper elevation");
        appendFigure(html, member, "underside.png", "Underside");
        appendFigure(html, member, "isometric.png", "Isometric occupancy");
        appendFigure(html, member, "east-west.png", "East-west section");
        appendFigure(html, member, "north-south.png", "North-south section");
    }

    private static void appendFigure(StringBuilder html, String member, String file, String caption) {
        html.append("<figure><figcaption>").append(caption).append("</figcaption><img src=\"")
                .append(member).append('/').append(file).append("\" alt=\"").append(caption)
                .append("\"></figure>");
    }

    private record MemberResult(String id, String target, int builtInPercent, VolumeMetrics metrics) {}
}
