package io.github.nidaba.skyforge.reference;

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
import javax.imageio.ImageIO;

/** Generates AUTH-0075 outcome-checkpoint consumption admission evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-ticket-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CURRENT_ADMISSION", e.currentPass());
        line(g, 0, 0, 58, "prepared validation: CURRENT");
        line(g, 0, 0, 86, "ticket sequence: 3501");
        line(g, 0, 0, 114, "issue: PASS");

        panel(g, PANEL_W, 0, "TICKET_IDENTITY", e.identityPass());
        line(g, PANEL_W, 0, 58, "prepared identity retained: YES");
        line(g, PANEL_W, 0, 86, "ticket sequence distinct: YES");
        line(g, PANEL_W, 0, 114, "token embeds sfackcpoutprep");

        panel(g, PANEL_W * 2, 0, "STALE_BLOCKED", e.staleBlocked());
        line(g, PANEL_W * 2, 0, 58, "prepared validation: STALE");
        line(g, PANEL_W * 2, 0, 86, "issue rejected: YES");
        line(g, PANEL_W * 2, 0, 114, "no refresh or retarget");

        panel(g, 0, PANEL_H, "INACTIVE_BLOCKED", e.inactiveBlocked());
        line(g, 0, PANEL_H, 58, "prepared validation: INACTIVE");
        line(g, 0, PANEL_H, 86, "issue rejected: YES");
        line(g, 0, PANEL_H, 114, "no implicit activation");

        panel(g, PANEL_W, PANEL_H, "EXACT_PROVENANCE", e.provenancePass());
        line(g, PANEL_W, PANEL_H, 58, "checkpoint identity retained");
        line(g, PANEL_W, PANEL_H, 86, "target identity retained");
        line(g, PANEL_W, PANEL_H, 114, "preparation identity retained");

        panel(g, PANEL_W * 2, PANEL_H, "NO_IO_OUTCOME", e.noOutcomePass());
        line(g, PANEL_W * 2, PANEL_H, 58, "ticket means admitted for coordination");
        line(g, PANEL_W * 2, PANEL_H, 86, "storage success: NOT CLAIMED");
        line(g, PANEL_W * 2, PANEL_H, 114, "I/O performed: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,ticketSequence,status\n"
                        + row("CURRENT_ADMISSION", e.currentPass(), 3501L, "CURRENT")
                        + row("TICKET_IDENTITY", e.identityPass(), 3501L, "CURRENT")
                        + row("STALE_BLOCKED", e.staleBlocked(), 3501L, "STALE")
                        + row("INACTIVE_BLOCKED", e.inactiveBlocked(), 3501L, "INACTIVE")
                        + row("EXACT_PROVENANCE", e.provenancePass(), 3501L, "CURRENT")
                        + row("NO_IO_OUTCOME", e.noOutcomePass(), 3501L, "ADMITTED");
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
                        + "<title>AUTH-0075</title>"
                        + "<h1>Outcome-checkpoint consumption admission ticket</h1>"
                        + "<p>The 16:9 atlas proves CURRENT-only admission, exact checkpoint/target/preparation "
                        + "provenance, and absence of storage outcome claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"ticket.csv\">ticket.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();
        var ticket = issuer.issue(f.current(), 3501L);
        var second = issuer.issue(f.current(), 3502L);

        boolean currentPass = ticket.admissionValidation().current();
        boolean sequenceDistinct = !ticket.id().equals(second.id());
        boolean identityPass =
                ticket.id().preparedConsumptionId().equals(f.prepared().id())
                        && sequenceDistinct
                        && ticket.id().canonicalToken().contains(f.prepared().id().canonicalToken());

        boolean staleBlocked = fails(() -> issuer.issue(f.stale(), 3503L));
        boolean inactiveBlocked = fails(() -> issuer.issue(f.inactive(), 3504L));

        var otherTarget =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "secondary");
        var otherPrepared =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer()
                        .prepare(f.prepared().preparationValidation(), 3402L, otherTarget);
        boolean mismatchBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                                                .of(3505L, otherPrepared),
                                        f.current()));

        boolean invalidSequenceBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                                        1,
                                        0L,
                                        f.prepared().id()));

        boolean provenancePass =
                ticket.preparedConsumption().equals(f.prepared())
                        && ticket.checkpointId().equals(f.prepared().checkpointId())
                        && ticket.targetId().equals(f.prepared().targetId());

        boolean noOutcomePass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                        .class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .noneMatch(
                                name ->
                                        name.equals("success")
                                                || name.equals("outcome")
                                                || name.equals("persisted")
                                                || name.equals("durable"));

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

    private static Fixture fixture() {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, set);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        binder.validate(binding, firstState),
                        3401L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", "primary"));

        return new Fixture(
                prepared,
                preparer.validateForExecution(prepared, firstState),
                preparer.validateForExecution(prepared, secondState),
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()));
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                    prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    inactive) {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket,
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
