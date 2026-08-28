package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.volume.SeededSuspendedVolumeReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
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

/** Generates the six-seed human-review corpus for structured secondary morphology. */
public final class SecondaryMorphologySuspendedVolumeCorpusCli {
    /** Stable evidence-package identifier for SF-IMP-0017 review. */
    public static final String EVIDENCE_ID = "secondary-morphology-suspended-volume-v1";

    private static final int PARALLELISM = 2;

    private SecondaryMorphologySuspendedVolumeCorpusCli() {}

    /** Samples all six canonical descriptors through the structured morphology recipe. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: SecondaryMorphologySuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        List<FixedSeedReferenceCorpus.Member> members = SeededSuspendedVolumeReferenceCorpus.members();
        Map<String, MemberResult> results = generateMembers(members, output, version);
        StringBuilder summary = new StringBuilder(
                "member,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");

        for (FixedSeedReferenceCorpus.Member member : members) {
            MemberResult result = results.get(member.id());
            if (result == null) {
                throw new IOException("missing completed corpus member: " + member.id());
            }
            VolumeMetrics metrics = result.metrics();
            summary.append(member.id()).append(',')
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
            List<FixedSeedReferenceCorpus.Member> members,
            Path output,
            String version) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        CompletionService<MemberResult> completion = new ExecutorCompletionService<>(executor);
        for (FixedSeedReferenceCorpus.Member member : members) {
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
            throw new IOException("structured morphology corpus generation was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("structured morphology corpus generation failed", cause);
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    private static MemberResult generateMember(
            FixedSeedReferenceCorpus.Member member,
            Path output,
            String version) throws IOException {
        SecondaryMorphologySkyIslandVolumeRecipe recipe =
                new SecondaryMorphologySkyIslandVolumeRecipe();
        SuspendedVolumeEvidenceGenerator generator = new SuspendedVolumeEvidenceGenerator();
        SuspendedVolumeEvidenceWriter writer = new SuspendedVolumeEvidenceWriter();
        CompiledSkyIslandVolume compiled = recipe.compile(
                SeededSuspendedVolumeReferenceCorpus.descriptor(member));
        SuspendedVolumeEvidence evidence = generator.generate(
                compiled, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        writer.write(evidence, output.resolve(member.id()), version);
        return new MemberResult(member, evidence.metrics());
    }

    private static String galleryHtml() {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang=\"en\"><head><meta charset=\"utf-8\">
                <title>Skyforge secondary morphology suspended-volume corpus</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1500px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .images{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:1rem}
                figure{margin:0} img{width:100%;image-rendering:auto;border:1px solid #ddd} figcaption{font-weight:600;margin:.35rem 0}
                </style></head><body>
                <h1>Structured secondary-morphology corpus</h1>
                <p>SF-IMP-0017 visual review: accepted seeded detail plus deterministic main ridge, spur, and valley organization.</p>
                """);
        for (FixedSeedReferenceCorpus.Member member : SeededSuspendedVolumeReferenceCorpus.members()) {
            html.append("<section><h2>").append(member.id()).append("</h2><div class=\"images\">");
            appendFigure(html, member.id(), "isometric.png", "Isometric occupancy");
            appendFigure(html, member.id(), "upper-surface.png", "Upper surface");
            appendFigure(html, member.id(), "underside.png", "Underside");
            appendFigure(html, member.id(), "east-west.png", "East-west section");
            appendFigure(html, member.id(), "north-south.png", "North-south section");
            appendFigure(html, member.id(), "suspension-occupancy.png", "Suspension plane");
            html.append("</div></section>");
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendFigure(StringBuilder html, String member, String file, String caption) {
        html.append("<figure><figcaption>").append(caption).append("</figcaption><img src=\"")
                .append(member).append('/').append(file).append("\" alt=\"").append(caption)
                .append("\"></figure>");
    }

    private record MemberResult(FixedSeedReferenceCorpus.Member member, VolumeMetrics metrics) {}
}
