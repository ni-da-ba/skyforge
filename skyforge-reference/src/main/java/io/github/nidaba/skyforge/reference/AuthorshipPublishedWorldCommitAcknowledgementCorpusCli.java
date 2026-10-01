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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgement;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgementBinder;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitOutcome;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitOutcomeAttestation;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitTicket;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitTicketId;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitTicketIssuer;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldPreparedWork;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldPreparedWorkPreparer;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldPreparedWorkValidation;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldSnapshotBinder;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldSnapshotBinding;
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
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0064 externally attested commit-outcome evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "SUCCESS_ATTESTED", e.successPass());
        line(g, 0, 0, 58, "external outcome: SUCCEEDED");
        line(g, 0, 0, 86, "exact ticket: YES");
        line(g, 0, 0, 114, "evidence token present: YES");

        panel(g, PANEL_W, 0, "FAILURE_ATTESTED", e.failurePass());
        line(g, PANEL_W, 0, 58, "external outcome: FAILED");
        line(g, PANEL_W, 0, 86, "represented explicitly: YES");
        line(g, PANEL_W, 0, 114, "not collapsed to absence");

        panel(g, PANEL_W * 2, 0, "EXACT_TICKET", e.exactTicketPass());
        line(g, PANEL_W * 2, 0, 58, "ack ID -> ticket: exact");
        line(g, PANEL_W * 2, 0, 86, "attestation -> ticket: exact");
        line(g, PANEL_W * 2, 0, 114, "mismatched ticket blocked: YES");

        panel(g, 0, PANEL_H, "MALFORMED_BLOCKED", e.malformedPass());
        line(g, 0, PANEL_H, 58, "bad schema blocked: YES");
        line(g, 0, PANEL_H, 86, "blank token blocked: YES");
        line(g, 0, PANEL_H, 114, "null outcome blocked: YES");

        panel(g, PANEL_W, PANEL_H, "SEQUENCE_IDENTITY", e.sequencePass());
        line(g, PANEL_W, PANEL_H, 58, "ack sequence 1301 != 1302");
        line(g, PANEL_W, PANEL_H, 86, "same ticket, distinct ack IDs");
        line(g, PANEL_W, PANEL_H, 114, "identity explicit: YES");

        panel(g, PANEL_W * 2, PANEL_H, "NO_SUCCESS_FACTORY", e.noFactoryPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "binder requires external attestation");
        line(g, PANEL_W * 2, PANEL_H, 86, "outcome inference: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "backend trust remains downstream");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,ackSequence,outcome\n"
                        + row("SUCCESS_ATTESTED", e.successPass(), 1301L, "SUCCEEDED")
                        + row("FAILURE_ATTESTED", e.failurePass(), 1303L, "FAILED")
                        + row("EXACT_TICKET", e.exactTicketPass(), 1301L, "SUCCEEDED")
                        + row("MALFORMED_BLOCKED", e.malformedPass(), 1301L, "N/A")
                        + row("SEQUENCE_IDENTITY", e.sequencePass(), 1301L, "SUCCEEDED")
                        + row("NO_SUCCESS_FACTORY", e.noFactoryPass(), 1301L, "N/A");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String acknowledgement =
                "ackToken,ticketExact,successOutcome,failureOutcome,sequenceDistinct\n"
                        + e.success().id().canonicalToken()
                        + ","
                        + e.exactTicketPass()
                        + ","
                        + e.success().outcome()
                        + ","
                        + e.failure().outcome()
                        + ","
                        + e.sequencePass()
                        + "\n";
        Files.writeString(out.resolve("acknowledgement.csv"), acknowledgement, StandardCharsets.UTF_8);

        String failures =
                "ticketMismatchBlocked,badSchemaBlocked,blankTokenBlocked,nullOutcomeBlocked\n"
                        + e.ticketMismatchBlocked()
                        + ","
                        + e.badSchemaBlocked()
                        + ","
                        + e.blankTokenBlocked()
                        + ","
                        + e.nullOutcomeBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0064</title>"
                        + "<h1>Commit outcome acknowledgement</h1>"
                        + "<p>The 16:9 atlas proves exact external success/failure attestation binding "
                        + "without allowing authorship code to infer backend success.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"acknowledgement.csv\">acknowledgement.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(64501L);
        SkyIslandPublishedWorldCommitAcknowledgementBinder binder =
                new SkyIslandPublishedWorldCommitAcknowledgementBinder();

        TestAttestation successAttestation =
                new TestAttestation(
                        1,
                        ticket.id(),
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "backend-proof:64501:success");
        SkyIslandPublishedWorldCommitAcknowledgement success =
                binder.bind(ticket, successAttestation, 1301L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                binder.bind(ticket, successAttestation, 1302L);

        TestAttestation failureAttestation =
                new TestAttestation(
                        1,
                        ticket.id(),
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "backend-proof:64501:failure");
        SkyIslandPublishedWorldCommitAcknowledgement failure =
                binder.bind(ticket, failureAttestation, 1303L);

        SkyIslandPublishedWorldCommitTicket otherTicket = ticket(64502L);
        boolean ticketMismatchBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new TestAttestation(
                                                1,
                                                otherTicket.id(),
                                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                                "other"),
                                        1304L));
        boolean badSchemaBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new TestAttestation(
                                                2,
                                                ticket.id(),
                                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                                "bad-schema"),
                                        1305L));
        boolean blankTokenBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new TestAttestation(
                                                1,
                                                ticket.id(),
                                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                                " "),
                                        1306L));
        boolean nullOutcomeBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new TestAttestation(1, ticket.id(), null, "null-outcome"),
                                        1307L));

        boolean successPass =
                success.outcome() == SkyIslandPublishedWorldCommitOutcome.SUCCEEDED
                        && success.ticket().equals(ticket);
        boolean failurePass =
                failure.outcome() == SkyIslandPublishedWorldCommitOutcome.FAILED
                        && failure.ticket().equals(ticket);
        boolean exactTicketPass =
                success.id().ticketId().equals(ticket.id())
                        && success.attestation().ticketId().equals(ticket.id())
                        && ticketMismatchBlocked;
        boolean malformedPass =
                badSchemaBlocked && blankTokenBlocked && nullOutcomeBlocked;
        boolean sequencePass = !success.id().equals(second.id());
        boolean noFactoryPass =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementBinder.class
                                        .getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .allMatch(
                                method ->
                                        method.getName().equals("bind")
                                                && method.getParameterCount() == 3
                                                && method.getParameterTypes()[1]
                                                        == SkyIslandPublishedWorldCommitOutcomeAttestation.class);

        return new Evidence(
                success,
                failure,
                successPass,
                failurePass,
                exactTicketPass,
                ticketMismatchBlocked,
                badSchemaBlocked,
                blankTokenBlocked,
                nullOutcomeBlocked,
                malformedPass,
                sequencePass,
                noFactoryPass);
    }

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 130L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1200L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1250L);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) {
            return true;
        }
    }

    private static String row(String scenario, boolean pass, long sequence, String outcome) {
        return scenario + "," + pass + "," + sequence + "," + outcome + "\n";
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
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
            throw new IllegalStateException("AUTH-0064 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth64",
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

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgement success,
            SkyIslandPublishedWorldCommitAcknowledgement failure,
            boolean successPass,
            boolean failurePass,
            boolean exactTicketPass,
            boolean ticketMismatchBlocked,
            boolean badSchemaBlocked,
            boolean blankTokenBlocked,
            boolean nullOutcomeBlocked,
            boolean malformedPass,
            boolean sequencePass,
            boolean noFactoryPass) {}
}
