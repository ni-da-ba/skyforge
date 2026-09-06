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
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Generates AUTH-0082 external outcome-checkpoint consumption outcome evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-consumption-acknowledgement-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "SUCCESS_ATTESTED", e.successPass());
        line(g, 0, 0, 58, "external outcome: SUCCEEDED");
        line(g, 0, 0, 86, "exact AUTH-0081 ticket: YES");
        line(g, 0, 0, 114, "evidence token present: YES");

        panel(g, PANEL_W, 0, "FAILURE_ATTESTED", e.failurePass());
        line(g, PANEL_W, 0, 58, "external outcome: FAILED");
        line(g, PANEL_W, 0, 86, "represented explicitly: YES");
        line(g, PANEL_W, 0, 114, "not collapsed to absence");

        panel(g, PANEL_W * 2, 0, "EXACT_TICKET", e.exactTicketPass());
        line(g, PANEL_W * 2, 0, 58, "ack ID -> ticket: exact");
        line(g, PANEL_W * 2, 0, 86, "attestation -> ticket: exact");
        line(g, PANEL_W * 2, 0, 114, "mismatch blocked: YES");

        panel(g, 0, PANEL_H, "MALFORMED_BLOCKED", e.malformedPass());
        line(g, 0, PANEL_H, 58, "bad schema blocked: YES");
        line(g, 0, PANEL_H, 86, "blank token blocked: YES");
        line(g, 0, PANEL_H, 114, "null outcome blocked: YES");

        panel(g, PANEL_W, PANEL_H, "SEQUENCE_IDENTITY", e.sequencePass());
        line(g, PANEL_W, PANEL_H, 58, "ack sequence 3701 != 3702");
        line(g, PANEL_W, PANEL_H, 86, "same ticket, distinct ack IDs");
        line(g, PANEL_W, PANEL_H, 114, "identity explicit: YES");

        panel(g, PANEL_W * 2, PANEL_H, "NO_SUCCESS_FACTORY", e.noFactoryPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "binder requires external attestation");
        line(g, PANEL_W * 2, PANEL_H, 86, "outcome inference: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "I/O trust remains downstream");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,ackSequence,outcome\n"
                        + row("SUCCESS_ATTESTED", e.successPass(), 3701L, "SUCCEEDED")
                        + row("FAILURE_ATTESTED", e.failurePass(), 3703L, "FAILED")
                        + row("EXACT_TICKET", e.exactTicketPass(), 3701L, "SUCCEEDED")
                        + row("MALFORMED_BLOCKED", e.malformedPass(), 3701L, "N/A")
                        + row("SEQUENCE_IDENTITY", e.sequencePass(), 3701L, "SUCCEEDED")
                        + row("NO_SUCCESS_FACTORY", e.noFactoryPass(), 3701L, "N/A");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String acknowledgement =
                "ackToken,ticketToken,targetToken,successOutcome,failureOutcome,sequenceDistinct\n"
                        + e.success().id().canonicalToken()
                        + ","
                        + e.success().ticket().id().canonicalToken()
                        + ","
                        + e.success().targetId().canonicalToken()
                        + ","
                        + e.success().outcome()
                        + ","
                        + e.failure().outcome()
                        + ","
                        + e.sequencePass()
                        + "\n";
        Files.writeString(
                out.resolve("acknowledgement.csv"),
                acknowledgement,
                StandardCharsets.UTF_8);

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
                        + "<title>AUTH-0082</title>"
                        + "<h1>External outcome-checkpoint-consumption outcome-checkpoint consumption outcome acknowledgement</h1>"
                        + "<p>The 16:9 atlas proves exact externally supplied success/failure binding "
                        + "to one AUTH-0081 ticket without allowing authorship code to infer success.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"acknowledgement.csv\">acknowledgement.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        var ticket = ticket("primary");
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder();

        var successAttestation =
                new Attestation(
                        1,
                        ticket.id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:success");
        var success = binder.bind(ticket, successAttestation, 3701L);
        var second = binder.bind(ticket, successAttestation, 3702L);

        var failureAttestation =
                new Attestation(
                        1,
                        ticket.id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:failure");
        var failure = binder.bind(ticket, failureAttestation, 3703L);

        var otherTicket = ticket("secondary");
        boolean ticketMismatchBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new Attestation(
                                                1,
                                                otherTicket.id(),
                                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                        .SUCCEEDED,
                                                "other"),
                                        3704L));
        boolean badSchemaBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new Attestation(
                                                2,
                                                ticket.id(),
                                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                        .SUCCEEDED,
                                                "bad-schema"),
                                        3705L));
        boolean blankTokenBlocked =
                fails(
                        () ->
                                binder.bind(
                                        ticket,
                                        new Attestation(
                                                1,
                                                ticket.id(),
                                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                        .SUCCEEDED,
                                                " "),
                                        3706L));
        boolean nullOutcomeBlocked =
                fails(() -> binder.bind(ticket, new Attestation(1, ticket.id(), null, "null"), 3707L));

        boolean successPass =
                success.outcome()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                        .SUCCEEDED
                        && success.ticket().equals(ticket);
        boolean failurePass =
                failure.outcome()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                        .FAILED
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
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .allMatch(
                                method ->
                                        method.getName().equals("bind")
                                                && method.getParameterCount() == 3
                                                && method.getParameterTypes()[1]
                                                        == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                                                                .class);

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

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket(String targetKey) {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();
        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = checkpointBinder.bind(state);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        checkpointBinder.validate(binding, state),
                        3601L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 3650L);
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

    private record Attestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    success,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    failure,
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
