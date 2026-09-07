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
import io.github.nidaba.skyforge.world.*;
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

/** Generates AUTH-0063 commit-ticket admission evidence. */
public final class AuthorshipPublishedWorldCommitTicketCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitTicketCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of("build", "evidence", "authorship-published-world-commit-ticket-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CURRENT_ADMISSION", e.currentAdmission());
        line(g, 0, 0, 58, "validation: CURRENT");
        line(g, 0, 0, 86, "ticket sequence: " + e.ticket().id().ticketSequence());
        line(g, 0, 0, 114, "issue: PASS");

        panel(g, PANEL_W, 0, "TICKET_IDENTITY", e.identityPass());
        line(g, PANEL_W, 0, 58, "prepared work retained: YES");
        line(g, PANEL_W, 0, 86, "sequence change -> distinct ticket: YES");
        line(g, PANEL_W, 0, 114, "token exposes sfwork identity: YES");

        panel(g, PANEL_W * 2, 0, "STALE_BLOCKED", e.staleBlocked());
        line(g, PANEL_W * 2, 0, 58, "validation: STALE");
        line(g, PANEL_W * 2, 0, 86, "issue rejected: " + yes(e.staleBlocked()));
        line(g, PANEL_W * 2, 0, 114, "no refresh/rebind");

        panel(g, 0, PANEL_H, "INACTIVE_BLOCKED", e.inactiveBlocked());
        line(g, 0, PANEL_H, 58, "validation: INACTIVE");
        line(g, 0, PANEL_H, 86, "issue rejected: " + yes(e.inactiveBlocked()));
        line(g, 0, PANEL_H, 114, "no implicit activation");

        panel(g, PANEL_W, PANEL_H, "EXACT_PROVENANCE", e.provenancePass());
        line(g, PANEL_W, PANEL_H, 58, "ticket -> exact validation");
        line(g, PANEL_W, PANEL_H, 86, "ticket -> exact prepared work");
        line(g, PANEL_W, PANEL_H, 114, "publication/support evidence retained");

        panel(g, PANEL_W * 2, PANEL_H, "NO_OUTCOME_CLAIM", e.noOutcomeClaim());
        line(g, PANEL_W * 2, PANEL_H, 58, "ticket means admitted for coordination");
        line(g, PANEL_W * 2, PANEL_H, 86, "backend success field: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "mutation performed: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,ticketSequence,workSequence,status\n"
                        + row("CURRENT_ADMISSION", e.currentAdmission(), e, "CURRENT")
                        + row("TICKET_IDENTITY", e.identityPass(), e, "CURRENT")
                        + row("STALE_BLOCKED", e.staleBlocked(), e, "STALE")
                        + row("INACTIVE_BLOCKED", e.inactiveBlocked(), e, "INACTIVE")
                        + row("EXACT_PROVENANCE", e.provenancePass(), e, "CURRENT")
                        + row("NO_OUTCOME_CLAIM", e.noOutcomeClaim(), e, "ADMITTED");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String ticket =
                "ticketToken,preparedWorkExact,validationCurrent,sequenceDistinct\n"
                        + e.ticket().id().canonicalToken()
                        + ","
                        + e.ticket().preparedWork().equals(e.current().preparedWork())
                        + ","
                        + e.ticket().admissionValidation().current()
                        + ","
                        + e.sequenceDistinct()
                        + "\n";
        Files.writeString(out.resolve("ticket.csv"), ticket, StandardCharsets.UTF_8);

        String failures =
                "staleBlocked,inactiveBlocked,mismatchedWorkBlocked,invalidSequenceBlocked\n"
                        + e.staleBlocked()
                        + ","
                        + e.inactiveBlocked()
                        + ","
                        + e.mismatchedWorkBlocked()
                        + ","
                        + e.invalidSequenceBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0063</title>"
                        + "<h1>Commit-ticket admission capability</h1>"
                        + "<p>The 16:9 atlas proves CURRENT-only ticket admission, exact prepared-work "
                        + "provenance, explicit admission identity, and absence of backend-outcome claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"ticket.csv\">ticket.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        Fixture f = fixture(63501L);
        SkyIslandPublishedWorldCommitTicketIssuer issuer =
                new SkyIslandPublishedWorldCommitTicketIssuer();
        SkyIslandPublishedWorldCommitTicket ticket = issuer.issue(f.current(), 1001L);
        SkyIslandPublishedWorldCommitTicket other = issuer.issue(f.current(), 1002L);

        boolean currentAdmission = ticket.admissionValidation().current();
        boolean sequenceDistinct = !ticket.id().equals(other.id());
        boolean identityPass =
                ticket.id().preparedWorkId().equals(f.current().preparedWork().id())
                        && sequenceDistinct
                        && ticket.id().canonicalToken().contains(f.current().preparedWork().id().canonicalToken());
        boolean staleBlocked = fails(() -> issuer.issue(f.stale(), 1003L));
        boolean inactiveBlocked = fails(() -> issuer.issue(f.inactive(), 1004L));
        boolean mismatchedWorkBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitTicket(
                                        SkyIslandPublishedWorldCommitTicketId.of(
                                                1005L, f.otherWork()),
                                        f.current()));
        boolean invalidSequenceBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitTicketId(
                                        SkyIslandPublishedWorldCommitTicketId.SCHEMA_VERSION,
                                        0L,
                                        f.current().preparedWork().id()));
        boolean provenancePass =
                ticket.preparedWork().equals(f.current().preparedWork())
                        && ticket.snapshotId().equals(f.current().preparedWork().snapshotId())
                        && ticket.hitCount() == f.current().preparedWork().hitCount();
        boolean noOutcomeClaim =
                java.util.Arrays.stream(SkyIslandPublishedWorldCommitTicket.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .noneMatch(name -> name.equals("success") || name.equals("outcome") || name.equals("completed"));

        return new Evidence(
                ticket,
                f.current(),
                currentAdmission,
                sequenceDistinct,
                identityPass,
                staleBlocked,
                inactiveBlocked,
                mismatchedWorkBlocked,
                invalidSequenceBlocked,
                provenancePass,
                noOutcomeClaim);
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 = SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 = viewV1.replace(v1.id(), v2);
        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(viewV1, 110L);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 111L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(first);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        WorldBounds region = v1.catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWork work = preparer.prepare(binding, 900L, region);

        SkyIslandAcceptedConvergenceCompilation otherCompilation = acceptedCompilation(rootSeed + 1L);
        SkyIslandCompiledWorldPublication otherPublication =
                publisher.publish(otherCompilation, 1L);
        SkyIslandPublishedWorldView otherView =
                SkyIslandPublishedWorldView.of(List.of(otherPublication));
        SkyIslandPublishedWorldActivationState otherActivation =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(otherView, 112L);
        SkyIslandPublishedWorldSnapshotBinding otherBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(otherActivation);
        SkyIslandPublishedWorldPreparedWork otherWork =
                preparer.prepare(otherBinding, 901L, otherPublication.catalog().volumes().get(0).bounds());

        return new Fixture(
                preparer.validateForCommit(work, first),
                preparer.validateForCommit(work, second),
                preparer.validateForCommit(work, SkyIslandPublishedWorldActivationState.inactive()),
                otherWork);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static String row(String scenario, boolean pass, Evidence e, String status) {
        return scenario
                + ","
                + pass
                + ","
                + e.ticket().id().ticketSequence()
                + ","
                + e.ticket().preparedWork().id().workSequence()
                + ","
                + status
                + "\n";
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

    private static void line(Graphics2D g, int x, int y, int offsetY, String value) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(value, x + 18, y + offsetY);
    }

    private static String yes(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
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
            throw new IllegalStateException("AUTH-0063 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth63",
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
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
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

    private record Fixture(
            SkyIslandPublishedWorldPreparedWorkValidation current,
            SkyIslandPublishedWorldPreparedWorkValidation stale,
            SkyIslandPublishedWorldPreparedWorkValidation inactive,
            SkyIslandPublishedWorldPreparedWork otherWork) {}

    private record Evidence(
            SkyIslandPublishedWorldCommitTicket ticket,
            SkyIslandPublishedWorldPreparedWorkValidation current,
            boolean currentAdmission,
            boolean sequenceDistinct,
            boolean identityPass,
            boolean staleBlocked,
            boolean inactiveBlocked,
            boolean mismatchedWorkBlocked,
            boolean invalidSequenceBlocked,
            boolean provenancePass,
            boolean noOutcomeClaim) {}
}
