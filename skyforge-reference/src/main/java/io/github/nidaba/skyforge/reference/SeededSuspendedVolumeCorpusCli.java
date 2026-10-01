package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.volume.SeededSuspendedVolumeReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates human-review evidence for the canonical six-member seeded suspended-volume corpus. */
public final class SeededSuspendedVolumeCorpusCli {
    /** Stable evidence-package identifier. */
    public static final String EVIDENCE_ID = SeededSuspendedVolumeReferenceCorpus.CORPUS_ID;

    private SeededSuspendedVolumeCorpusCli() {}

    /** Samples all six canonical seeded volumes and writes one evidence directory per seed. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: SeededSuspendedVolumeCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        SeededSkyIslandVolumeRecipe recipe = new SeededSkyIslandVolumeRecipe();
        SuspendedVolumeEvidenceGenerator generator = new SuspendedVolumeEvidenceGenerator();
        SuspendedVolumeEvidenceWriter writer = new SuspendedVolumeEvidenceWriter();
        StringBuilder summary = new StringBuilder(
                "member,seed,solidSamples,components,faceContacts,minimumClearance,minimumX,maximumX,minimumY,maximumY,minimumZ,maximumZ\n");

        for (FixedSeedReferenceCorpus.Member member : SeededSuspendedVolumeReferenceCorpus.members()) {
            CompiledSkyIslandVolume compiled = recipe.compile(
                    SeededSuspendedVolumeReferenceCorpus.descriptor(member));
            SuspendedVolumeEvidence evidence = generator.generate(
                    compiled, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
            Path memberDirectory = output.resolve(member.id());
            writer.write(evidence, memberDirectory, version);

            var metrics = evidence.metrics();
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

            System.out.printf(
                    "%s: solid=%d; components=%d; faceContacts=%d; minimumClearance=%.3f%n",
                    member.id(),
                    metrics.solidSampleCount(),
                    metrics.connectedSolidComponents(),
                    metrics.faceContacts().total(),
                    metrics.airClearance().minimum());
        }

        Files.writeString(output.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"), galleryHtml(), StandardCharsets.UTF_8);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static String galleryHtml() {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang=\"en\"><head><meta charset=\"utf-8\">
                <title>Skyforge seeded suspended-volume corpus</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1500px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .images{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:1rem}
                figure{margin:0} img{width:100%;image-rendering:auto;border:1px solid #ddd} figcaption{font-weight:600;margin:.35rem 0}
                </style></head><body>
                <h1>Seeded suspended-volume corpus</h1>
                <p>SF-IMP-0016 visual review: identical semantic descriptor, six canonical root seeds, full bounded enrichment amplitude.</p>
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
}
