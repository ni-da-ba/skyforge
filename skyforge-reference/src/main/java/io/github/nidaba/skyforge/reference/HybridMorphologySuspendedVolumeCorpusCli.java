package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.HybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.HybridMorphologyReferenceCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generates the thirty-member 25/50/75-percent visual progression atlas for primary hybrids. */
public final class HybridMorphologySuspendedVolumeCorpusCli {
    /** Stable evidence identifier for the first pairwise hybrid review corpus. */
    public static final String EVIDENCE_ID = HybridMorphologyReferenceCorpus.CORPUS_ID;

    private static final int PARALLELISM = 2;
    private static final VolumeGridSpec REVIEW_GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 97, 65, 97);

    private HybridMorphologySuspendedVolumeCorpusCli() {}

    /** Generates 25/50/75-percent progression specimens for every unordered built-in family pair. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: HybridMorphologySuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        List<HybridMorphologyReferenceCorpus.ReviewMember> members =
                HybridMorphologyReferenceCorpus.reviewMembers();
        Map<String, MemberResult> results = generateMembers(members, output, version);

        StringBuilder summary = new StringBuilder(
                "member,firstFamily,secondFamily,secondPercent,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");
        for (HybridMorphologyReferenceCorpus.ReviewMember member : members) {
            MemberResult result = results.get(member.id());
            if (result == null) {
                throw new IOException("missing completed hybrid corpus member: " + member.id());
            }
            VolumeMetrics metrics = result.metrics();
            summary.append(member.id()).append(',')
                    .append(member.pair().first().identifier()).append(',')
                    .append(member.pair().second().identifier()).append(',')
                    .append(member.secondPercent()).append(',')
                    .append(member.seed()).append(',')
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
        Files.writeString(output.resolve("index.html"), galleryHtml(), StandardCharsets.UTF_8);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static Map<String, MemberResult> generateMembers(
            List<HybridMorphologyReferenceCorpus.ReviewMember> members,
            Path output,
            String version) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        CompletionService<MemberResult> completion = new ExecutorCompletionService<>(executor);
        for (HybridMorphologyReferenceCorpus.ReviewMember member : members) {
            completion.submit(() -> generateMember(member, output, version));
        }

        Map<String, MemberResult> results = new HashMap<>();
        try {
            for (int completed = 0; completed < members.size(); completed++) {
                MemberResult result = completion.take().get();
                results.put(result.member().id(), result);
                VolumeMetrics metrics = result.metrics();
                System.out.printf(
                        "%s: solid=%d; components=%d; faceContacts=%d; minimumClearance=%.3f%n",
                        result.member().id(),
                        metrics.solidSampleCount(),
                        metrics.connectedSolidComponents(),
                        metrics.faceContacts().total(),
                        metrics.airClearance().minimum());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("hybrid morphology corpus generation was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("hybrid morphology corpus generation failed", cause);
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    private static MemberResult generateMember(
            HybridMorphologyReferenceCorpus.ReviewMember member,
            Path output,
            String version) throws IOException {
        CompiledSkyIslandVolume compiled = new HybridMorphologySkyIslandVolumeRecipe().compile(
                HybridMorphologyReferenceCorpus.descriptor(member.seed()), member.blend());
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, REVIEW_GRID, SamplingOrder.FORWARD);
        new SuspendedVolumeEvidenceWriter().write(evidence, output.resolve(member.id()), version);
        return new MemberResult(member, evidence.metrics());
    }

    private static String galleryHtml() {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang=\"en\"><head><meta charset=\"utf-8\">
                <title>Skyforge primary morphology hybrid atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1800px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .progression{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem}
                article{padding:.75rem;border:1px solid #ddd;background:#fafafa}
                .images{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.6rem}
                figure{margin:0} img{width:100%;image-rendering:auto;border:1px solid #ddd} figcaption{font-weight:600;margin:.25rem 0;font-size:.9rem}
                @media(max-width:1100px){.progression{grid-template-columns:1fr}}
                </style></head><body>
                <h1>Primary morphology hybrid progression atlas</h1>
                <p>SF-IMP-0022 human design review. Each section holds one unordered built-in family pair at the Skyforge root seed. Columns move from 25% to 50% to 75% contribution from the second named family. Numerical acceptance separately evaluates every pair midpoint across all three canonical seeds on the full 4-unit grid.</p>
                """);
        for (HybridMorphologyReferenceCorpus.Pair pair : HybridMorphologyReferenceCorpus.pairs()) {
            html.append("<section><h2>")
                    .append(pair.first().identifier()).append(" → ")
                    .append(pair.second().identifier()).append("</h2><div class=\"progression\">");
            for (HybridMorphologyReferenceCorpus.ReviewMember member
                    : HybridMorphologyReferenceCorpus.reviewMembers()) {
                if (!member.pair().equals(pair)) {
                    continue;
                }
                html.append("<article><h3>")
                        .append(member.secondPercent()).append("% ")
                        .append(pair.second().identifier())
                        .append("</h3><div class=\"images\">");
                appendFigure(html, member.id(), "suspension-occupancy.png", "Hybrid silhouette");
                appendFigure(html, member.id(), "upper-surface.png", "Upper elevation");
                appendFigure(html, member.id(), "underside.png", "Hybrid underside");
                appendFigure(html, member.id(), "isometric.png", "Isometric occupancy");
                appendFigure(html, member.id(), "east-west.png", "East-west section");
                appendFigure(html, member.id(), "north-south.png", "North-south section");
                html.append("</div></article>");
            }
            html.append("</div></section>");
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendFigure(StringBuilder html, String member, String file, String caption) {
        html.append("<figure><figcaption>").append(caption).append("</figcaption><img src=\"")
                .append(member).append('/').append(file).append("\" alt=\"").append(caption)
                .append("\"></figure>");
    }

    private record MemberResult(
            HybridMorphologyReferenceCorpus.ReviewMember member, VolumeMetrics metrics) {}
}
