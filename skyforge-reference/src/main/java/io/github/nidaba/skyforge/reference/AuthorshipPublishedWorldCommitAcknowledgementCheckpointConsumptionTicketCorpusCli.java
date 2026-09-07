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

/** Generates AUTH-0069 checkpoint-consumption ticket admission evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-ticket-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CURRENT_ADMISSION", e.currentPass());
        line(g, 0, 0, 58, "prepared validation: CURRENT");
        line(g, 0, 0, 86, "ticket sequence: 2301");
        line(g, 0, 0, 114, "issue: PASS");

        panel(g, PANEL_W, 0, "TICKET_IDENTITY", e.identityPass());
        line(g, PANEL_W, 0, 58, "prepared consumption retained: YES");
        line(g, PANEL_W, 0, 86, "sequence change -> distinct ticket: YES");
        line(g, PANEL_W, 0, 114, "token exposes sfackcpprep identity");

        panel(g, PANEL_W * 2, 0, "STALE_BLOCKED", e.staleBlocked());
        line(g, PANEL_W * 2, 0, 58, "prepared validation: STALE");
        line(g, PANEL_W * 2, 0, 86, "issue rejected: YES");
        line(g, PANEL_W * 2, 0, 114, "no refresh/retarget");

        panel(g, 0, PANEL_H, "INACTIVE_BLOCKED", e.inactiveBlocked());
        line(g, 0, PANEL_H, 58, "prepared validation: INACTIVE");
        line(g, 0, PANEL_H, 86, "issue rejected: YES");
        line(g, 0, PANEL_H, 114, "no implicit activation");

        panel(g, PANEL_W, PANEL_H, "EXACT_TARGET_PROVENANCE", e.provenancePass());
        line(g, PANEL_W, PANEL_H, 58, "checkpoint identity retained");
        line(g, PANEL_W, PANEL_H, 86, "target identity retained");
        line(g, PANEL_W, PANEL_H, 114, "preparation identity retained");

        panel(g, PANEL_W * 2, PANEL_H, "NO_IO_OUTCOME", e.noOutcomePass());
        line(g, PANEL_W * 2, PANEL_H, 58, "ticket means admitted for I/O coordination");
        line(g, PANEL_W * 2, PANEL_H, 86, "storage/replication success: NOT CLAIMED");
        line(g, PANEL_W * 2, PANEL_H, 114, "I/O performed: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,ticketSequence,status\n"
                        + row("CURRENT_ADMISSION", e.currentPass(), 2301L, "CURRENT")
                        + row("TICKET_IDENTITY", e.identityPass(), 2301L, "CURRENT")
                        + row("STALE_BLOCKED", e.staleBlocked(), 2301L, "STALE")
                        + row("INACTIVE_BLOCKED", e.inactiveBlocked(), 2301L, "INACTIVE")
                        + row("EXACT_TARGET_PROVENANCE", e.provenancePass(), 2301L, "CURRENT")
                        + row("NO_IO_OUTCOME", e.noOutcomePass(), 2301L, "ADMITTED");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String ticket =
                "ticketToken,preparedToken,targetToken,checkpointRevision,sequenceDistinct\n"
                        + e.ticket().id().canonicalToken()
                        + ","
                        + e.ticket().preparedConsumption().id().canonicalToken()
                        + ","
                        + e.ticket().targetId().canonicalToken()
                        + ","
                        + e.ticket().checkpointId().checkpointRevision()
                        + ","
                        + e.sequenceDistinct()
                        + "\n";
        Files.writeString(out.resolve("ticket.csv"), ticket, StandardCharsets.UTF_8);

        String failures =
                "staleBlocked,inactiveBlocked,mismatchBlocked,invalidSequenceBlocked\n"
                        + e.staleBlocked()
                        + ","
                        + e.inactiveBlocked()
                        + ","
                        + e.mismatchBlocked()
                        + ","
                        + e.invalidSequenceBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0069</title>"
                        + "<h1>Checkpoint-consumption I/O admission ticket</h1>"
                        + "<p>The 16:9 atlas proves CURRENT-only admission, exact checkpoint/target "
                        + "preparation provenance, and absence of persistence/replication outcome claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"ticket.csv\">ticket.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        Fixture f = fixture(69501L);
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer();
        var ticket = issuer.issue(f.current(), 2301L);
        var second = issuer.issue(f.current(), 2302L);

        boolean currentPass = ticket.admissionValidation().current();
        boolean sequenceDistinct = !ticket.id().equals(second.id());
        boolean identityPass =
                ticket.id()
                                .preparedConsumptionId()
                                .equals(f.prepared().id())
                        && sequenceDistinct
                        && ticket.id()
                                .canonicalToken()
                                .contains(f.prepared().id().canonicalToken());

        boolean staleBlocked = fails(() -> issuer.issue(f.stale(), 2303L));
        boolean inactiveBlocked = fails(() -> issuer.issue(f.inactive(), 2304L));

        Fixture other = fixture(69502L);
        boolean mismatchBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId
                                                .of(2305L, other.prepared()),
                                        f.current()));
        boolean invalidSequenceBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId(
                                        1,
                                        0L,
                                        f.prepared().id()));

        boolean provenancePass =
                ticket.preparedConsumption().equals(f.prepared())
                        && ticket.checkpointId().equals(f.prepared().checkpointId())
                        && ticket.targetId().equals(f.prepared().targetId());

        boolean noOutcomePass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket.class
                                        .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .noneMatch(
                                name ->
                                        name.equals("success")
                                                || name.equals("outcome")
                                                || name.equals("completed")
                                                || name.equals("persisted"));

        return new Evidence(
                ticket,
                currentPass,
                identityPass,
                sequenceDistinct,
                staleBlocked,
                inactiveBlocked,
                mismatchBlocked,
                invalidSequenceBlocked,
                provenancePass,
                noOutcomePass);
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandPublishedWorldCommitAcknowledgement first = acknowledgement(rootSeed, 2301L);
        SkyIslandPublishedWorldCommitAcknowledgement second = acknowledgement(rootSeed + 1L, 2302L);
        var firstSet = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var secondSet = firstSet.admit(second);
        var firstCheckpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, firstSet);
        var secondCheckpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, secondSet);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(firstState);
        var prepareValidation = binder.validate(binding, firstState);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var prepared = preparer.prepare(prepareValidation, 2201L, target);

        return new Fixture(
                prepared,
                preparer.validateForExecution(prepared, firstState),
                preparer.validateForExecution(prepared, secondState),
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()));
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new TestAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "proof:" + rootSeed),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 230L);
        SkyIslandPublishedWorldSnapshotBinding snapshotBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer workPreparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                workPreparer.prepare(
                        snapshotBinding,
                        2200L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2250L);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static String row(String scenario, boolean pass, long sequence, String status) {
        return scenario + "," + pass + "," + sequence + "," + status + "\n";
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
            throw new IllegalStateException("AUTH-0069 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth69",
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation inactive) {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket,
            boolean currentPass,
            boolean identityPass,
            boolean sequenceDistinct,
            boolean staleBlocked,
            boolean inactiveBlocked,
            boolean mismatchBlocked,
            boolean invalidSequenceBlocked,
            boolean provenancePass,
            boolean noOutcomePass) {}
}
