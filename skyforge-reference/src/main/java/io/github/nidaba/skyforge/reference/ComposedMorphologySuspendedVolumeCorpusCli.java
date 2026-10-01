package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.ComposedMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.ComposedMorphologyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.MorphologyFamilyReferenceCorpus;
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

/** Generates the fifteen-member human-review atlas for full cross-family morphology composition. */
public final class ComposedMorphologySuspendedVolumeCorpusCli {
    /** Stable evidence identifier for the first family composition visual proof. */
    public static final String EVIDENCE_ID = ComposedMorphologyReferenceCorpus.CORPUS_ID;

    private static final int PARALLELISM = 2;
    private static final VolumeGridSpec REVIEW_GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 97, 65, 97);

    private ComposedMorphologySuspendedVolumeCorpusCli() {}

    /** Generates three full-amplitude composed specimens for each accepted primary family. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: ComposedMorphologySuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        List<MorphologyFamilyReferenceCorpus.Member> members =
                ComposedMorphologyReferenceCorpus.members();
        Map<String, MemberResult> results = generateMembers(members, output, version);
        StringBuilder summary = new StringBuilder(
                "member,family,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");
        for (MorphologyFamilyReferenceCorpus.Member member : members) {
            MemberResult result = results.get(member.id());
            if (result == null) {
                throw new IOException("missing completed corpus member: " + member.id());
            }
            VolumeMetrics metrics = result.metrics();
            summary.append(member.id()).append(',')
                    .append(member.family().identifier()).append(',')
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
            List<MorphologyFamilyReferenceCorpus.Member> members,
            Path output,
            String version) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        CompletionService<MemberResult> completion = new ExecutorCompletionService<>(executor);
        for (MorphologyFamilyReferenceCorpus.Member member : members) {
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
            throw new IOException("composed morphology corpus generation was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("composed morphology corpus generation failed", cause);
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    private static MemberResult generateMember(
            MorphologyFamilyReferenceCorpus.Member member,
            Path output,
            String version) throws IOException {
        CompiledSkyIslandVolume compiled = new ComposedMorphologySkyIslandVolumeRecipe().compile(
                ComposedMorphologyReferenceCorpus.descriptor(member), member.family());
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, REVIEW_GRID, SamplingOrder.FORWARD);
        new SuspendedVolumeEvidenceWriter().write(evidence, output.resolve(member.id()), version);
        return new MemberResult(member, evidence.metrics());
    }

    private static String galleryHtml() {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang=\"en\"><head><meta charset=\"utf-8\">
                <title>Skyforge composed morphology-family atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1600px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                article{margin:1.5rem 0;padding-top:.5rem;border-top:1px solid #ddd}
                .images{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:1rem}
                figure{margin:0} img{width:100%;image-rendering:auto;border:1px solid #ddd} figcaption{font-weight:600;margin:.35rem 0}
                </style></head><body>
                <h1>Composed morphology-family atlas</h1>
                <p>SF-IMP-0019 design review: accepted bounded detail plus structured ridge/spur/valley relief applied at full amplitude to all five SF-IMP-0018 primary families. Numerical acceptance uses the full canonical grid; this atlas uses an 8-unit review grid.</p>
                """);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            html.append("<section><h2>").append(family.identifier()).append("</h2>");
            for (MorphologyFamilyReferenceCorpus.Member member : ComposedMorphologyReferenceCorpus.members()) {
                if (member.family() != family) {
                    continue;
                }
                html.append("<article><h3>").append(member.id()).append("</h3><div class=\"images\">");
                appendFigure(html, member.id(), "suspension-occupancy.png", "Preserved family silhouette");
                appendFigure(html, member.id(), "upper-surface.png", "Composed upper elevation");
                appendFigure(html, member.id(), "underside.png", "Detailed underside depth");
                appendFigure(html, member.id(), "isometric.png", "Isometric occupancy");
                appendFigure(html, member.id(), "east-west.png", "East-west section");
                appendFigure(html, member.id(), "north-south.png", "North-south section");
                html.append("</div></article>");
            }
            html.append("</section>");
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendFigure(StringBuilder html, String member, String file, String caption) {
        html.append("<figure><figcaption>").append(caption).append("</figcaption><img src=\"")
                .append(member).append('/').append(file).append("\" alt=\"").append(caption)
                .append("\"></figure>");
    }

    private record MemberResult(
            MorphologyFamilyReferenceCorpus.Member member, VolumeMetrics metrics) {}
}
