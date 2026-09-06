package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.reference.evidence.ProductionMorphologyDiagnostics;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.volume.ProductionMorphologyVisualReviewCorpus;
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

/**
 * AUTH-0083 production morphology reference atlas for issue #214.
 *
 * <p>The atlas intentionally records measurements without aesthetic thresholds. Minecraft-side
 * review should render the exact same member IDs/specifications before thresholds or underside
 * grammar changes are accepted.
 */
public final class ProductionMorphologyVisualReviewCorpusCli {
    /** Stable evidence identity shared with the corpus definition. */
    public static final String EVIDENCE_ID = ProductionMorphologyVisualReviewCorpus.CORPUS_ID;

    private static final int PARALLELISM = 2;

    private ProductionMorphologyVisualReviewCorpusCli() {}

    /** Generates all 41 deterministic isolated semantic specimens and their diagnostics. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: ProductionMorphologyVisualReviewCorpusCli [output-directory]");
        }
        Path output =
                arguments.length == 1
                        ? Path.of(arguments[0])
                        : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        String version = System.getProperty("skyforge.version", "development");
        List<ProductionMorphologyVisualReviewCorpus.Member> members =
                ProductionMorphologyVisualReviewCorpus.members();
        Map<String, MemberResult> results =
                generateMembers(members, output, version);

        writeSummary(members, results, output);
        writeMinecraftHandoff(members, output);
        Files.writeString(output.resolve("index.html"), galleryHtml(members), StandardCharsets.UTF_8);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static Map<String, MemberResult> generateMembers(
            List<ProductionMorphologyVisualReviewCorpus.Member> members,
            Path output,
            String version)
            throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        CompletionService<MemberResult> completion =
                new ExecutorCompletionService<>(executor);

        for (ProductionMorphologyVisualReviewCorpus.Member member : members) {
            completion.submit(() -> generateMember(member, output, version));
        }

        Map<String, MemberResult> results = new HashMap<>();
        try {
            for (int completed = 0; completed < members.size(); completed++) {
                MemberResult result = completion.take().get();
                results.put(result.member().id(), result);
                VolumeMetrics topology = result.evidence().metrics();
                ProductionMorphologyDiagnostics diagnostics = result.diagnostics();
                System.out.printf(
                        "%s: components=%d faceContacts=%d thicknessMean/r=%.5f neighborJump/r=%.5f halfTurnMismatch=%.5f upperUnderCorr=%.5f%n",
                        result.member().id(),
                        topology.connectedSolidComponents(),
                        topology.faceContacts().total(),
                        diagnostics.meanThicknessNormalized(),
                        diagnostics.maximumNeighborThicknessJumpNormalized(),
                        diagnostics.halfTurnOccupancyMismatchFraction(),
                        diagnostics.upperUndersidePearsonCorrelation());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "production morphology visual review generation was interrupted",
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("production morphology visual review generation failed", cause);
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    private static MemberResult generateMember(
            ProductionMorphologyVisualReviewCorpus.Member member,
            Path output,
            String version)
            throws IOException {
        SuspendedVolumeEvidence evidence =
                new SuspendedVolumeEvidenceGenerator()
                        .generate(
                                ProductionMorphologyVisualReviewCorpus.compile(member),
                                ProductionMorphologyVisualReviewCorpus.reviewGrid(member),
                                SamplingOrder.FORWARD);
        new SuspendedVolumeEvidenceWriter()
                .write(evidence, output.resolve(member.id()), version);
        return new MemberResult(
                member,
                evidence,
                ProductionMorphologyDiagnostics.measure(evidence));
    }

    private static void writeSummary(
            List<ProductionMorphologyVisualReviewCorpus.Member> members,
            Map<String, MemberResult> results,
            Path output)
            throws IOException {
        StringBuilder csv =
                new StringBuilder(
                        "member,kind,scale,seed,morphologySpec,solidSamples,components,faceContacts,minimumClearance,"
                                + "occupiedColumns,minThicknessR,p05ThicknessR,meanThicknessR,maxThicknessR,"
                                + "maxNeighborThicknessJumpR,meanUpperNeighborDiffR,meanUndersideNeighborDiffR,"
                                + "meanUpperSecondDiffR,meanUndersideSecondDiffR,halfTurnOccupancyMismatch,"
                                + "meanHalfTurnThicknessDiffR,upperUndersideCorrelation\n");

        for (ProductionMorphologyVisualReviewCorpus.Member member : members) {
            MemberResult result = results.get(member.id());
            if (result == null) {
                throw new IOException("missing completed review member: " + member.id());
            }
            VolumeMetrics topology = result.evidence().metrics();
            ProductionMorphologyDiagnostics d = result.diagnostics();
            csv.append(member.id()).append(',')
                    .append(member.kind()).append(',')
                    .append(member.scale().id()).append(',')
                    .append(member.seed()).append(',')
                    .append(csvEscape(member.morphology().stableIdentifier())).append(',')
                    .append(topology.solidSampleCount()).append(',')
                    .append(topology.connectedSolidComponents()).append(',')
                    .append(topology.faceContacts().total()).append(',')
                    .append(topology.airClearance().minimum()).append(',')
                    .append(d.occupiedColumns()).append(',')
                    .append(d.minimumThicknessNormalized()).append(',')
                    .append(d.fifthPercentileThicknessNormalized()).append(',')
                    .append(d.meanThicknessNormalized()).append(',')
                    .append(d.maximumThicknessNormalized()).append(',')
                    .append(d.maximumNeighborThicknessJumpNormalized()).append(',')
                    .append(d.meanUpperNeighborDifferenceNormalized()).append(',')
                    .append(d.meanUndersideNeighborDifferenceNormalized()).append(',')
                    .append(d.meanUpperSecondDifferenceNormalized()).append(',')
                    .append(d.meanUndersideSecondDifferenceNormalized()).append(',')
                    .append(d.halfTurnOccupancyMismatchFraction()).append(',')
                    .append(d.meanHalfTurnThicknessDifferenceNormalized()).append(',')
                    .append(d.upperUndersidePearsonCorrelation()).append('\n');
        }
        Files.writeString(output.resolve("summary.csv"), csv, StandardCharsets.UTF_8);
    }

    /**
     * Writes the explicit downstream contract for Implementation without adding a production-core
     * dependency on the reference module.
     */
    private static void writeMinecraftHandoff(
            List<ProductionMorphologyVisualReviewCorpus.Member> members,
            Path output)
            throws IOException {
        StringBuilder csv =
                new StringBuilder(
                        "member,kind,scale,seed,morphologySpec,requiredMinecraftViews,flightReview\n");
        for (ProductionMorphologyVisualReviewCorpus.Member member : members) {
            csv.append(member.id()).append(',')
                    .append(member.kind()).append(',')
                    .append(member.scale().id()).append(',')
                    .append(member.seed()).append(',')
                    .append(csvEscape(member.morphology().stableIdentifier())).append(',')
                    .append("above|horizon-approach|below")
                    .append(',')
                    .append("orbit-and-underside")
                    .append('\n');
        }
        Files.writeString(
                output.resolve("minecraft-handoff.csv"),
                csv,
                StandardCharsets.UTF_8);
    }

    private static String galleryHtml(
            List<ProductionMorphologyVisualReviewCorpus.Member> members) {
        StringBuilder html =
                new StringBuilder(
                        """
                        <!doctype html>
                        <html lang="en"><head><meta charset="utf-8">
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <title>Skyforge production morphology visual review</title>
                        <style>
                        body{font-family:system-ui,sans-serif;max-width:1900px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                        section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                        article{margin:1.25rem 0;padding:1rem;border-top:1px solid #ddd}
                        .images{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:.8rem}
                        figure{margin:0} img{width:100%;border:1px solid #ddd} figcaption{font-weight:600;margin:.25rem 0}
                        code{background:#eee;padding:.1rem .25rem}
                        </style></head><body>
                        <h1>Production morphology visual-quality corpus</h1>
                        <p>AUTH-0083 / issue #214. These are backend-neutral reference views of exact production-path morphology specs. No aesthetic diagnostic threshold is encoded yet. Compare this corpus to Minecraft renders carrying the same member IDs before accepting thresholds or an underside-secondary grammar.</p>
                        """);

        for (ProductionMorphologyVisualReviewCorpus.Kind kind :
                ProductionMorphologyVisualReviewCorpus.Kind.values()) {
            html.append("<section><h2>").append(kind).append("</h2>");
            for (ProductionMorphologyVisualReviewCorpus.Member member : members) {
                if (member.kind() != kind) {
                    continue;
                }
                html.append("<article><h3>")
                        .append(member.id())
                        .append("</h3><p><code>")
                        .append(member.morphology().stableIdentifier())
                        .append("</code> · scale ")
                        .append(member.scale().id())
                        .append(" · seed ")
                        .append(member.seed())
                        .append("</p><div class=\"images\">");
                appendFigure(html, member.id(), "suspension-occupancy.png", "Planform / silhouette");
                appendFigure(html, member.id(), "upper-surface.png", "Upper elevation");
                appendFigure(html, member.id(), "underside.png", "Underside elevation");
                appendFigure(html, member.id(), "isometric.png", "Isometric occupancy");
                appendFigure(html, member.id(), "east-west.png", "East-west section");
                appendFigure(html, member.id(), "north-south.png", "North-south section");
                html.append("</div></article>");
            }
            html.append("</section>");
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendFigure(
            StringBuilder html,
            String member,
            String file,
            String caption) {
        html.append("<figure><figcaption>")
                .append(caption)
                .append("</figcaption><img src=\\"")
                .append(member)
                .append('/')
                .append(file)
                .append("\\" alt=\\"")
                .append(caption)
                .append("\\"></figure>");
    }

    private static String csvEscape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return "\\"" + value.replace("\\"", "\\"\\\"") + "\\"";
    }

    private record MemberResult(
            ProductionMorphologyVisualReviewCorpus.Member member,
            SuspendedVolumeEvidence evidence,
            ProductionMorphologyDiagnostics diagnostics) {}
}
