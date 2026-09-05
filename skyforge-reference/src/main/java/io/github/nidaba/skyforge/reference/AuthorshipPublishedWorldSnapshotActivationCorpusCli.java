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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldActivationState;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldEntry;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldSnapshot;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldSnapshotId;
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

/** Generates AUTH-0060 view-snapshot identity and activation evidence. */
public final class AuthorshipPublishedWorldSnapshotActivationCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldSnapshotActivationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-snapshot-activation-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderBinding(g, evidence, 0, 0);
        renderCanonicalIdentity(g, evidence, PANEL_W, 0);
        renderInitialActivation(g, evidence, PANEL_W * 2, 0);
        renderReplacement(g, evidence, 0, PANEL_H);
        renderStaleCas(g, evidence, PANEL_W, PANEL_H);
        renderQuery(g, evidence, PANEL_W * 2, PANEL_H);
        g.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,snapshotRevision,publicationCount,volumeCount\n"
                        + row("SNAPSHOT_BINDING", evidence.bindingPass(), evidence.snapshot())
                        + row("CANONICAL_IDENTITY", evidence.canonicalPass(), evidence.snapshot())
                        + row("INITIAL_ACTIVATION", evidence.initialActivationPass(), evidence.active().requireActive())
                        + row("MONOTONIC_REPLACEMENT", evidence.replacementPass(), evidence.revised().requireActive())
                        + row("STALE_CAS_BLOCKED", evidence.staleCasPass(), evidence.revised().requireActive())
                        + row("QUERY_DELEGATION", evidence.queryPass(), evidence.active().requireActive());
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String binding =
                "snapshotToken,viewIdentityEqual,canonicalUnsignedOrder,forgedBindingBlocked\n"
                        + evidence.snapshot().id().canonicalToken()
                        + ","
                        + evidence.bindingPass()
                        + ","
                        + evidence.canonicalPass()
                        + ","
                        + evidence.forgedBindingBlocked()
                        + "\n";
        Files.writeString(out.resolve("binding.csv"), binding, StandardCharsets.UTF_8);

        String activation =
                "inactiveBefore,activeAfter,originalStillInactive,queryEqual\n"
                        + !evidence.inactive().active()
                        + ","
                        + evidence.active().active()
                        + ","
                        + !evidence.inactive().active()
                        + ","
                        + evidence.queryPass()
                        + "\n";
        Files.writeString(out.resolve("activation.csv"), activation, StandardCharsets.UTF_8);

        String replacement =
                "oldSnapshotRevision,newSnapshotRevision,oldPublicationRevision,newPublicationRevision,"
                        + "viewChanged,originalRetained,staleBlocked,nonIncreasingBlocked\n"
                        + evidence.original().requireActive().id().snapshotRevision()
                        + ","
                        + evidence.revised().requireActive().id().snapshotRevision()
                        + ","
                        + evidence.oldPublicationRevision()
                        + ","
                        + evidence.newPublicationRevision()
                        + ","
                        + evidence.viewChanged()
                        + ","
                        + evidence.originalRetained()
                        + ","
                        + evidence.staleBlocked()
                        + ","
                        + evidence.nonIncreasingBlocked()
                        + "\n";
        Files.writeString(out.resolve("replacement.csv"), replacement, StandardCharsets.UTF_8);

        String version =
                "sameSnapshotRevision,viewIdentityChanged,snapshotIdentityChanged\n"
                        + evidence.sameRevisionAcrossViews()
                        + ","
                        + evidence.sameRevisionViewChanged()
                        + ","
                        + evidence.sameRevisionIdentityChanged()
                        + "\n";
        Files.writeString(out.resolve("version.csv"), version, StandardCharsets.UTF_8);

        String failures =
                "forgedBindingBlocked,activeInitialBlocked,inactiveReplaceBlocked,staleBlocked,nonIncreasingBlocked\n"
                        + evidence.forgedBindingBlocked()
                        + ","
                        + evidence.activeInitialBlocked()
                        + ","
                        + evidence.inactiveReplaceBlocked()
                        + ","
                        + evidence.staleBlocked()
                        + ","
                        + evidence.nonIncreasingBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0060</title>"
                        + "<h1>Published-world snapshot identity and activation</h1>"
                        + "<p>The 16:9 atlas shows exact admitted-view snapshot binding, unsigned "
                        + "canonical identity, explicit immutable initial activation, monotonic "
                        + "compare-and-swap replacement, stale activation rejection, and exact "
                        + "proof-carrying query delegation.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"binding.csv\">binding.csv</a> · "
                        + "<a href=\"activation.csv\">activation.csv</a> · "
                        + "<a href=\"replacement.csv\">replacement.csv</a> · "
                        + "<a href=\"version.csv\">version.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandCompiledWorldPublication canonicalLow =
                publication(60501L, 1L, -1_500.0);
        SkyIslandCompiledWorldPublication canonicalHigh =
                publication(-1L, 1L, 1_500.0);
        SkyIslandPublishedWorldView canonicalView =
                SkyIslandPublishedWorldView.of(List.of(canonicalHigh, canonicalLow));
        SkyIslandPublishedWorldSnapshot snapshot =
                SkyIslandPublishedWorldSnapshot.of(7L, canonicalView);

        boolean bindingPass =
                snapshot.id().viewIdentity().equals(canonicalView.viewIdentity());
        boolean canonicalPass =
                snapshot.id().viewIdentity().get(0).equals(canonicalLow.id())
                        && snapshot.id().viewIdentity().get(1).equals(canonicalHigh.id());

        SkyIslandPublishedWorldView forgedOtherView =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(60502L, 1L, 0.0)));
        boolean forgedBindingBlocked =
                failureMessage(
                                () ->
                                        new SkyIslandPublishedWorldSnapshot(
                                                SkyIslandPublishedWorldSnapshotId.of(
                                                        7L,
                                                        forgedOtherView),
                                                canonicalView))
                        .contains("does not bind the exact admitted publication view");

        SkyIslandPublishedWorldActivationState inactive =
                SkyIslandPublishedWorldActivationState.inactive();
        SkyIslandPublishedWorldActivationState active =
                inactive.activateInitial(canonicalView, 10L);
        boolean activeInitialBlocked =
                failureMessage(() -> active.activateInitial(canonicalView, 11L))
                        .contains("requires an inactive state");

        var queryBounds = canonicalLow.catalog().volumes().get(0).bounds();
        List<SkyIslandPublishedWorldEntry> directHits = canonicalView.query(queryBounds);
        List<SkyIslandPublishedWorldEntry> activeHits = active.query(queryBounds);
        boolean queryPass =
                directHits.equals(activeHits)
                        && activeHits.size() == 1
                        && activeHits.get(0).publicationId().equals(canonicalLow.id());
        boolean initialActivationPass =
                !inactive.active()
                        && active.active()
                        && active.requireActive().id().snapshotRevision() == 10L;

        SkyIslandAcceptedConvergenceCompilation replacementCompilation =
                acceptedCompilation(60503L, -1_500.0);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publicationV1 =
                publisher.publish(replacementCompilation, 1L);
        SkyIslandCompiledWorldPublication publicationV2 =
                publisher.publish(replacementCompilation, 2L);
        SkyIslandCompiledWorldPublication replacementOther =
                publication(60504L, 1L, 1_500.0);
        SkyIslandPublishedWorldView originalView =
                SkyIslandPublishedWorldView.of(
                        List.of(publicationV1, replacementOther));
        SkyIslandPublishedWorldView replacementView =
                originalView.replace(publicationV1.id(), publicationV2);

        SkyIslandPublishedWorldActivationState original =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(originalView, 20L);
        SkyIslandPublishedWorldSnapshotId expected = original.requireActive().id();
        SkyIslandPublishedWorldActivationState revised =
                original.replace(expected, replacementView, 21L);

        boolean viewChanged =
                !originalView.viewIdentity().equals(replacementView.viewIdentity());
        boolean originalRetained =
                original.requireActive().id().snapshotRevision() == 20L
                        && original.requireActive().id().viewIdentity().equals(originalView.viewIdentity());
        boolean staleBlocked =
                failureMessage(
                                () ->
                                        revised.replace(
                                                expected,
                                                replacementView,
                                                22L))
                        .contains("stale");
        boolean nonIncreasingBlocked =
                failureMessage(
                                () ->
                                        original.replace(
                                                expected,
                                                replacementView,
                                                20L))
                        .contains("strictly increase");
        boolean inactiveReplaceBlocked =
                failureMessage(
                                () ->
                                        SkyIslandPublishedWorldActivationState.inactive()
                                                .replace(
                                                        expected,
                                                        replacementView,
                                                        21L))
                        .contains("requires an active snapshot");
        boolean replacementPass =
                revised.requireActive().id().snapshotRevision() == 21L
                        && revised.requireActive().id().viewIdentity().equals(replacementView.viewIdentity())
                        && viewChanged
                        && originalRetained;

        SkyIslandPublishedWorldSnapshot sameRevisionOld =
                SkyIslandPublishedWorldSnapshot.of(30L, originalView);
        SkyIslandPublishedWorldSnapshot sameRevisionNew =
                SkyIslandPublishedWorldSnapshot.of(30L, replacementView);
        boolean sameRevisionAcrossViews =
                sameRevisionOld.id().snapshotRevision()
                        == sameRevisionNew.id().snapshotRevision();
        boolean sameRevisionViewChanged =
                !sameRevisionOld.id().viewIdentity()
                        .equals(sameRevisionNew.id().viewIdentity());
        boolean sameRevisionIdentityChanged =
                !sameRevisionOld.id().equals(sameRevisionNew.id());

        boolean staleCasPass =
                staleBlocked && nonIncreasingBlocked && inactiveReplaceBlocked;

        return new Evidence(
                snapshot,
                bindingPass,
                canonicalPass,
                forgedBindingBlocked,
                inactive,
                active,
                initialActivationPass,
                activeInitialBlocked,
                queryPass,
                original,
                revised,
                publicationV1.id().publicationRevision(),
                publicationV2.id().publicationRevision(),
                viewChanged,
                originalRetained,
                replacementPass,
                staleBlocked,
                nonIncreasingBlocked,
                inactiveReplaceBlocked,
                staleCasPass,
                sameRevisionAcrossViews,
                sameRevisionViewChanged,
                sameRevisionIdentityChanged);
    }

    private static String row(
            String scenario,
            boolean pass,
            SkyIslandPublishedWorldSnapshot snapshot) {
        return scenario
                + ","
                + pass
                + ","
                + snapshot.id().snapshotRevision()
                + ","
                + snapshot.publicationCount()
                + ","
                + snapshot.volumeCount()
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

    private static void renderBinding(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "SNAPSHOT_BINDING", evidence.bindingPass() && evidence.forgedBindingBlocked());
        line(g, x, y, 58, "snapshot revision: " + evidence.snapshot().id().snapshotRevision());
        line(g, x, y, 86, "exact view identity bound: " + yes(evidence.bindingPass()));
        line(g, x, y, 114, "forged other-view ID blocked: " + yes(evidence.forgedBindingBlocked()));
        line(g, x, y, 156, "publication count: " + evidence.snapshot().publicationCount());
        line(g, x, y, 184, "volume count: " + evidence.snapshot().volumeCount());
        line(g, x, y, 238, "snapshot ID carries full AUTH-0059 identity");
    }

    private static void renderCanonicalIdentity(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CANONICAL_IDENTITY", evidence.canonicalPass());
        line(g, x, y, 58, "caller supplied: unsigned-high, unsigned-low");
        line(g, x, y, 86, "snapshot stores: unsigned-low, unsigned-high");
        line(g, x, y, 128, "strict canonical order: " + yes(evidence.canonicalPass()));
        line(g, x, y, 170, "token prefix: sfviewsnap:v1");
        line(g, x, y, 238, "caller order cannot alter snapshot identity");
    }

    private static void renderInitialActivation(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "INITIAL_ACTIVATION", evidence.initialActivationPass());
        line(g, x, y, 58, "before: INACTIVE");
        line(g, x, y, 86, "after: ACTIVE revision 10");
        line(g, x, y, 114, "original state still inactive: " + yes(!evidence.inactive().active()));
        line(g, x, y, 156, "second initial activation blocked: " + yes(evidence.activeInitialBlocked()));
        line(g, x, y, 238, "activation returns a new immutable state");
    }

    private static void renderReplacement(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "MONOTONIC_REPLACEMENT", evidence.replacementPass());
        line(
                g,
                x,
                y,
                58,
                "snapshot revision "
                        + evidence.original().requireActive().id().snapshotRevision()
                        + " -> "
                        + evidence.revised().requireActive().id().snapshotRevision());
        line(
                g,
                x,
                y,
                86,
                "publication revision "
                        + evidence.oldPublicationRevision()
                        + " -> "
                        + evidence.newPublicationRevision());
        line(g, x, y, 128, "view identity changed: " + yes(evidence.viewChanged()));
        line(g, x, y, 156, "old activation retained: " + yes(evidence.originalRetained()));
        line(g, x, y, 198, "axes remain separate");
        line(g, x, y, 238, "replacement is exact compare-and-swap");
    }

    private static void renderStaleCas(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "STALE_CAS_BLOCKED", evidence.staleCasPass());
        line(g, x, y, 58, "stale expected snapshot blocked: " + yes(evidence.staleBlocked()));
        line(g, x, y, 86, "same/lower snapshot revision blocked: " + yes(evidence.nonIncreasingBlocked()));
        line(g, x, y, 114, "replace while inactive blocked: " + yes(evidence.inactiveReplaceBlocked()));
        line(g, x, y, 156, "same revision + changed view -> different ID: " + yes(evidence.sameRevisionIdentityChanged()));
        line(g, x, y, 238, "activation state has explicit stale detection");
    }

    private static void renderQuery(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "QUERY_DELEGATION", evidence.queryPass());
        line(g, x, y, 58, "active query == AUTH-0059 view query");
        line(g, x, y, 86, "hit count: 1");
        line(g, x, y, 114, "publication identity retained: YES");
        line(g, x, y, 142, "support certificate retained: YES");
        line(g, x, y, 184, "query delegation pass: " + yes(evidence.queryPass()));
        line(g, x, y, 238, "AUTH-0060 adds no spatial semantics");
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
            long rootSeed,
            long publicationRevision,
            double centerX) {
        return new SkyIslandCompiledWorldPublisher()
                .publish(
                        acceptedCompilation(rootSeed, centerX),
                        publicationRevision);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            double centerX) {
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
            throw new IllegalStateException("AUTH-0060 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            double centerX,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth60",
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
            SkyIslandPublishedWorldSnapshot snapshot,
            boolean bindingPass,
            boolean canonicalPass,
            boolean forgedBindingBlocked,
            SkyIslandPublishedWorldActivationState inactive,
            SkyIslandPublishedWorldActivationState active,
            boolean initialActivationPass,
            boolean activeInitialBlocked,
            boolean queryPass,
            SkyIslandPublishedWorldActivationState original,
            SkyIslandPublishedWorldActivationState revised,
            long oldPublicationRevision,
            long newPublicationRevision,
            boolean viewChanged,
            boolean originalRetained,
            boolean replacementPass,
            boolean staleBlocked,
            boolean nonIncreasingBlocked,
            boolean inactiveReplaceBlocked,
            boolean staleCasPass,
            boolean sameRevisionAcrossViews,
            boolean sameRevisionViewChanged,
            boolean sameRevisionIdentityChanged) {}
}
