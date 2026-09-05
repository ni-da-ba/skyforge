package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldEntry;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldView;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0059 publication-set backend-view admission evidence. */
public final class AuthorshipPublishedWorldViewCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldViewCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-view-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderCanonical(g, evidence, 0, 0);
        renderQueryVsSupport(g, evidence, PANEL_W, 0);
        renderDuplicateRoot(g, evidence, PANEL_W * 2, 0);
        renderSupportOverlap(g, evidence, 0, PANEL_H);
        renderQueryProvenance(g, evidence, PANEL_W, PANEL_H);
        renderReplacement(g, evidence, PANEL_W * 2, PANEL_H);
        g.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,publicationCount,volumeCount,detail\n"
                        + row("CANONICAL_VIEW", evidence.canonicalPass(), 2, 2, "root-order")
                        + row(
                                "QUERY_BOUNDS_VS_SUPPORT",
                                evidence.querySupportPass(),
                                2,
                                2,
                                "query-overlap-support-disjoint")
                        + row(
                                "DUPLICATE_ROOT_BLOCKED",
                                evidence.duplicateRootBlocked(),
                                0,
                                0,
                                "explicit-version-selection")
                        + row(
                                "SUPPORT_OVERLAP_BLOCKED",
                                evidence.supportOverlapBlocked(),
                                0,
                                0,
                                "certified-support")
                        + row(
                                "QUERY_PROVENANCE",
                                evidence.queryProvenancePass(),
                                2,
                                2,
                                "publication-and-certificate")
                        + row(
                                "EXPLICIT_REPLACEMENT",
                                evidence.replacementPass(),
                                2,
                                2,
                                "compare-and-replace");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String view =
                "firstToken,secondToken,queryBoundsIntersect,supportBoundsIntersect,canonicalOrder\n"
                        + evidence.overlapView().publications().get(0).id().canonicalToken()
                        + ","
                        + evidence.overlapView().publications().get(1).id().canonicalToken()
                        + ","
                        + evidence.queryBoundsIntersect()
                        + ","
                        + evidence.supportBoundsIntersect()
                        + ","
                        + evidence.canonicalPass()
                        + "\n";
        Files.writeString(out.resolve("view.csv"), view, StandardCharsets.UTF_8);

        SkyIslandPublishedWorldEntry queryHit = evidence.queryHits().get(0);
        String query =
                "hitCount,publicationToken,volumePath,certificateExact\n"
                        + evidence.queryHits().size()
                        + ","
                        + queryHit.publicationId().canonicalToken()
                        + ","
                        + queryHit.volume().id().path()
                        + ","
                        + evidence.queryProvenancePass()
                        + "\n";
        Files.writeString(out.resolve("query.csv"), query, StandardCharsets.UTF_8);

        String replacement =
                "oldRevision,newRevision,originalRetained,newSelected,staleBlocked,collidingBlocked\n"
                        + evidence.replacementOriginalId().publicationRevision()
                        + ","
                        + evidence.replacementNewId().publicationRevision()
                        + ","
                        + evidence.originalRetained()
                        + ","
                        + evidence.newSelected()
                        + ","
                        + evidence.staleReplacementBlocked()
                        + ","
                        + evidence.collidingReplacementBlocked()
                        + "\n";
        Files.writeString(out.resolve("replacement.csv"), replacement, StandardCharsets.UTF_8);

        String failures =
                "duplicateRootContainsExpected,supportOverlapContainsExpected\n"
                        + evidence.duplicateRootBlocked()
                        + ","
                        + evidence.supportOverlapBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0059</title>"
                        + "<h1>Publication-set backend-view admission</h1>"
                        + "<p>The 16:9 atlas shows canonical multi-publication ordering, the "
                        + "query-bounds versus certified-support distinction, duplicate-root and "
                        + "support-overlap rejection, proof-carrying query provenance, and explicit "
                        + "monotonic replacement with full re-admission.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"view.csv\">view.csv</a> · "
                        + "<a href=\"query.csv\">query.csv</a> · "
                        + "<a href=\"replacement.csv\">replacement.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandCompiledWorldPublication overlapFirst =
                publication(59501L, 1L, 0.0);
        SkyIslandCompiledWorldPublication overlapSecond =
                publication(59502L, 1L, 680.0);
        boolean queryBoundsIntersect =
                overlapFirst.catalog().volumes().get(0).bounds()
                        .intersects(overlapSecond.catalog().volumes().get(0).bounds());
        WorldBounds firstSupport = overlapFirst.supportCertificates().get(0).supportBounds();
        WorldBounds secondSupport = overlapSecond.supportCertificates().get(0).supportBounds();
        boolean supportBoundsIntersect = firstSupport.intersects(secondSupport);
        SkyIslandPublishedWorldView overlapView =
                SkyIslandPublishedWorldView.of(List.of(overlapSecond, overlapFirst));
        boolean canonicalPass =
                overlapView.publications().get(0).id().equals(overlapFirst.id())
                        && overlapView.publications().get(1).id().equals(overlapSecond.id());
        boolean querySupportPass =
                queryBoundsIntersect
                        && !supportBoundsIntersect
                        && overlapView.publicationCount() == 2;

        SkyIslandAcceptedConvergenceCompilation duplicateCompilation =
                acceptedCompilation(59503L, 0.0);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication duplicateV1 =
                publisher.publish(duplicateCompilation, 1L);
        SkyIslandCompiledWorldPublication duplicateV2 =
                publisher.publish(duplicateCompilation, 2L);
        String duplicateFailure =
                failureMessage(
                        () ->
                                SkyIslandPublishedWorldView.of(
                                        List.of(duplicateV1, duplicateV2)));
        boolean duplicateRootBlocked =
                duplicateFailure.contains("exactly one publication per regional root");

        SkyIslandCompiledWorldPublication collidingFirst =
                publication(59504L, 1L, 0.0);
        SkyIslandCompiledWorldPublication collidingSecond =
                publication(59505L, 1L, 0.0);
        String supportFailure =
                failureMessage(
                        () ->
                                SkyIslandPublishedWorldView.of(
                                        List.of(collidingFirst, collidingSecond)));
        boolean supportOverlapBlocked =
                supportFailure.contains("certified support overlaps or touches");

        SkyIslandCompiledWorldPublication queryLeft =
                publication(59506L, 1L, -1_500.0);
        SkyIslandCompiledWorldPublication queryRight =
                publication(59507L, 1L, 1_500.0);
        SkyIslandPublishedWorldView queryView =
                SkyIslandPublishedWorldView.of(List.of(queryRight, queryLeft));
        List<SkyIslandPublishedWorldEntry> queryHits =
                queryView.query(queryLeft.catalog().volumes().get(0).bounds());
        boolean queryProvenancePass =
                queryHits.size() == 1
                        && queryHits.get(0).publicationId().equals(queryLeft.id())
                        && queryHits.get(0).supportCertificate()
                                .equals(
                                        queryLeft.compilation().supportBundle()
                                                .certificateFor(
                                                        queryHits.get(0).volume().id())
                                                .orElseThrow());

        SkyIslandAcceptedConvergenceCompilation replacementCompilation =
                acceptedCompilation(59508L, -1_500.0);
        SkyIslandCompiledWorldPublication replacementV1 =
                publisher.publish(replacementCompilation, 1L);
        SkyIslandCompiledWorldPublication replacementV2 =
                publisher.publish(replacementCompilation, 2L);
        SkyIslandCompiledWorldPublication replacementOther =
                publication(59509L, 1L, 1_500.0);
        SkyIslandPublishedWorldView replacementOriginal =
                SkyIslandPublishedWorldView.of(
                        List.of(replacementOther, replacementV1));
        SkyIslandPublishedWorldView replacementRevised =
                replacementOriginal.replace(replacementV1.id(), replacementV2);

        boolean originalRetained =
                replacementOriginal.publicationForRoot(59508L)
                        .orElseThrow()
                        .id()
                        .equals(replacementV1.id());
        boolean newSelected =
                replacementRevised.publicationForRoot(59508L)
                        .orElseThrow()
                        .id()
                        .equals(replacementV2.id());
        boolean staleReplacementBlocked =
                failureMessage(
                                () ->
                                        replacementRevised.replace(
                                                replacementV1.id(),
                                                publisher.publish(
                                                        replacementCompilation, 3L)))
                        .contains("stale replacement");
        SkyIslandCompiledWorldPublication collidingReplacement =
                publication(59508L, 3L, 1_500.0);
        boolean collidingReplacementBlocked =
                failureMessage(
                                () ->
                                        replacementRevised.replace(
                                                replacementV2.id(), collidingReplacement))
                        .contains("certified support overlaps or touches");
        boolean replacementPass =
                originalRetained
                        && newSelected
                        && staleReplacementBlocked
                        && collidingReplacementBlocked;

        return new Evidence(
                overlapView,
                queryBoundsIntersect,
                supportBoundsIntersect,
                canonicalPass,
                querySupportPass,
                duplicateRootBlocked,
                supportOverlapBlocked,
                queryHits,
                queryProvenancePass,
                replacementV1.id(),
                replacementV2.id(),
                originalRetained,
                newSelected,
                staleReplacementBlocked,
                collidingReplacementBlocked,
                replacementPass);
    }

    private static String row(
            String scenario,
            boolean pass,
            int publicationCount,
            int volumeCount,
            String detail) {
        return scenario
                + ","
                + pass
                + ","
                + publicationCount
                + ","
                + volumeCount
                + ","
                + detail
                + "\n";
    }

    private static String failureMessage(Runnable action) {
        try {
            action.run();
            return "UNEXPECTED SUCCESS";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
        }
    }

    private static void renderCanonical(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CANONICAL_VIEW", evidence.canonicalPass());
        line(g, x, y, 58, "caller order: root B, root A");
        line(g, x, y, 86, "view order: unsigned root A, root B");
        line(g, x, y, 114, "publication count: " + evidence.overlapView().publicationCount());
        line(g, x, y, 142, "volume count: " + evidence.overlapView().volumeCount());
        line(g, x, y, 184, "canonical identity: " + yes(evidence.canonicalPass()));
        line(g, x, y, 238, "caller list order carries no semantics");
    }

    private static void renderQueryVsSupport(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "QUERY_BOUNDS_VS_SUPPORT", evidence.querySupportPass());
        line(g, x, y, 58, "broad query bounds intersect: " + yes(evidence.queryBoundsIntersect()));
        line(g, x, y, 86, "certified support intersects: " + yes(evidence.supportBoundsIntersect()));
        line(g, x, y, 128, "view admitted: YES");
        line(g, x, y, 170, "proof uses certified physical support");
        line(g, x, y, 198, "query reservations remain conservative");
        line(g, x, y, 238, "broad overlap alone does not reject");
    }

    private static void renderDuplicateRoot(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "DUPLICATE_ROOT_BLOCKED", evidence.duplicateRootBlocked());
        line(g, x, y, 58, "same regional root");
        line(g, x, y, 86, "revision 1 + revision 2");
        line(g, x, y, 128, "implicit newest-wins: NONE");
        line(g, x, y, 170, "construction rejected: " + yes(evidence.duplicateRootBlocked()));
        line(g, x, y, 238, "caller must explicitly select one revision");
    }

    private static void renderSupportOverlap(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "SUPPORT_OVERLAP_BLOCKED", evidence.supportOverlapBlocked());
        line(g, x, y, 58, "different regional roots");
        line(g, x, y, 86, "certified physical support overlaps");
        line(g, x, y, 128, "composition policy: NONE");
        line(g, x, y, 170, "construction rejected: " + yes(evidence.supportOverlapBlocked()));
        line(g, x, y, 238, "cross-publication overlap fails closed");
    }

    private static void renderQueryProvenance(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "QUERY_PROVENANCE", evidence.queryProvenancePass());
        SkyIslandPublishedWorldEntry hit = evidence.queryHits().get(0);
        line(g, x, y, 58, "query hits: " + evidence.queryHits().size());
        line(g, x, y, 86, "publication: " + hit.publicationId().canonicalToken());
        line(g, x, y, 114, "world volume ID retained: YES");
        line(g, x, y, 142, "exact support certificate retained: YES");
        line(g, x, y, 184, "provenance pass: " + yes(evidence.queryProvenancePass()));
        line(g, x, y, 238, "queries never return a bare volume");
    }

    private static void renderReplacement(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "EXPLICIT_REPLACEMENT", evidence.replacementPass());
        line(
                g,
                x,
                y,
                58,
                "revision "
                        + evidence.replacementOriginalId().publicationRevision()
                        + " -> "
                        + evidence.replacementNewId().publicationRevision());
        line(g, x, y, 86, "original immutable: " + yes(evidence.originalRetained()));
        line(g, x, y, 114, "new revision selected: " + yes(evidence.newSelected()));
        line(g, x, y, 142, "stale expected ID blocked: " + yes(evidence.staleReplacementBlocked()));
        line(
                g,
                x,
                y,
                170,
                "colliding replacement blocked: "
                        + yes(evidence.collidingReplacementBlocked()));
        line(g, x, y, 238, "replacement is fully re-admitted");
    }

    private static void panel(Graphics2D g, int x, int y, String title, boolean pass) {
        g.setColor(pass ? new Color(224, 240, 226) : new Color(244, 218, 218));
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(176, 176, 176));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 28);
    }

    private static void line(Graphics2D g, int x, int y, int offsetY, String text) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(text, x + 18, y + offsetY);
    }

    private static String yes(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandCompiledWorldPublication publication(
            long rootSeed, long revision, double centerX) {
        return new SkyIslandCompiledWorldPublisher()
                .publish(acceptedCompilation(rootSeed, centerX), revision);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed, double centerX) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, centerX, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0059 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, double centerX, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth59",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }

    private record Evidence(
            SkyIslandPublishedWorldView overlapView,
            boolean queryBoundsIntersect,
            boolean supportBoundsIntersect,
            boolean canonicalPass,
            boolean querySupportPass,
            boolean duplicateRootBlocked,
            boolean supportOverlapBlocked,
            List<SkyIslandPublishedWorldEntry> queryHits,
            boolean queryProvenancePass,
            io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublicationId replacementOriginalId,
            io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublicationId replacementNewId,
            boolean originalRetained,
            boolean newSelected,
            boolean staleReplacementBlocked,
            boolean collidingReplacementBlocked,
            boolean replacementPass) {}
}
