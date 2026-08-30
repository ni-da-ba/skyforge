package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderHybridMorphologySkyIslandVolumeRecipe;
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

/** Generates the SF-IMP-0024 standalone-custom and custom-to-built-in visual review atlas. */
public final class ProviderMorphologySuspendedVolumeCorpusCli {
    /** Stable evidence identifier for the first explicit-provider visual proof. */
    public static final String EVIDENCE_ID = "provider-morphology-suspended-volume-v1";

    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final double[] BUILT_IN_WEIGHTS = {0.25, 0.50, 0.75};
    private static final VolumeGridSpec REVIEW_GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 97, 65, 97);

    private ProviderMorphologySuspendedVolumeCorpusCli() {}

    /** Generates one standalone crescent and fifteen custom-to-built-in hybrid specimens. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: ProviderMorphologySuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(SKYFORGE_SEED);
        ReferenceCrescentMorphologyProvider crescent = new ReferenceCrescentMorphologyProvider();
        SkyIslandMorphologyProviderRegistry registry = registry(crescent);
        ProviderHybridMorphologySkyIslandVolumeRecipe hybridRecipe =
                new ProviderHybridMorphologySkyIslandVolumeRecipe();

        List<MemberResult> results = new ArrayList<>();
        results.add(writeMember(
                "crescent-standalone",
                "crescent",
                0,
                crescent.compilePrimary(descriptor).volume(),
                output,
                version));

        for (MorphologyFamily family : MorphologyFamily.values()) {
            for (double builtInWeight : BUILT_IN_WEIGHTS) {
                int percent = (int) Math.round(100.0 * builtInWeight);
                var builtInId = SkyIslandMorphologyProviders.builtInId(family);
                MorphologyProviderBlend blend = new MorphologyProviderBlend(
                        ReferenceCrescentMorphologyProvider.ID,
                        builtInId,
                        builtInWeight);
                CompiledSkyIslandVolume compiled = hybridRecipe.compile(descriptor, blend, registry);
                results.add(writeMember(
                        "crescent-to-" + family.identifier() + "-built-in-" + percent,
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
                "member,target,builtInPercent,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");
        for (MemberResult result : results) {
            VolumeMetrics metrics = result.metrics();
            summary.append(result.id()).append(',')
                    .append(result.target()).append(',')
                    .append(result.builtInPercent()).append(',')
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
                <html lang=\"en\"><head><meta charset=\"utf-8\">
                <title>Skyforge explicit morphology provider atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1800px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .progression{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem}
                article{padding:.75rem;border:1px solid #ddd;background:#fafafa}
                .images{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.6rem}
                figure{margin:0} img{width:100%;border:1px solid #ddd} figcaption{font-weight:600;margin:.25rem 0;font-size:.9rem}
                @media(max-width:1100px){.progression{grid-template-columns:1fr}}
                </style></head><body>
                <h1>Explicit morphology provider visual proof</h1>
                <p>SF-IMP-0024 review. The standalone specimen is implemented outside the built-in morphology enum. Each progression moves from 25% to 75% contribution from one accepted Skyforge built-in while retaining the custom crescent as the other parent.</p>
                <section><h2>Standalone custom provider: reference:crescent</h2><div class=\"progression\"><article><div class=\"images\">
                """);
        appendFigures(html, "crescent-standalone");
        html.append("</div></article></div></section>");

        for (MorphologyFamily family : MorphologyFamily.values()) {
            html.append("<section><h2>reference:crescent → skyforge:")
                    .append(family.identifier())
                    .append("</h2><div class=\"progression\">");
            for (double weight : BUILT_IN_WEIGHTS) {
                int percent = (int) Math.round(100.0 * weight);
                String id = "crescent-to-" + family.identifier() + "-built-in-" + percent;
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
