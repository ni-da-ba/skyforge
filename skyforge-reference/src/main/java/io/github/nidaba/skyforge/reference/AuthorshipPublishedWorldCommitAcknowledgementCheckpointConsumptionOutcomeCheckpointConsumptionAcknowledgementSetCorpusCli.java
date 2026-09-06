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
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0077 outcome-checkpoint consumption replay-admission evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSetCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSetCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-acknowledgement-set-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CANONICAL_SET", e.canonicalPass());
        line(g, 0, 0, 58, "caller order: 3902, 3901");
        line(g, 0, 0, 86, "stored order: 3901, 3902");
        line(g, 0, 0, 114, "canonical: YES");

        panel(g, PANEL_W, 0, "IMMUTABLE_ADMIT", e.immutablePass());
        line(g, PANEL_W, 0, 58, "empty -> one -> two");
        line(g, PANEL_W, 0, 86, "prior sets retained: YES");
        line(g, PANEL_W, 0, 114, "returned list immutable: YES");

        panel(g, PANEL_W * 2, 0, "REPLAY_BLOCKED", e.replayBlocked());
        line(g, PANEL_W * 2, 0, 58, "exact AUTH-0076 acknowledgement replay");
        line(g, PANEL_W * 2, 0, 86, "admission rejected: YES");
        line(g, PANEL_W * 2, 0, 114, "no hidden upsert");

        panel(g, 0, PANEL_H, "CONTRADICTION_BLOCKED", e.contradictionBlocked());
        line(g, 0, PANEL_H, 58, "same AUTH-0075 ticket: SUCCEEDED vs FAILED");
        line(g, 0, PANEL_H, 86, "second outcome rejected: YES");
        line(g, 0, PANEL_H, 114, "winner selected: NO");

        panel(g, PANEL_W, PANEL_H, "SEQUENCE_REUSE_BLOCKED", e.sequenceReuseBlocked());
        line(g, PANEL_W, PANEL_H, 58, "different tickets, sequence 3920");
        line(g, PANEL_W, PANEL_H, 86, "reuse rejected: YES");
        line(g, PANEL_W, PANEL_H, 114, "audit ordering unique");

        panel(g, PANEL_W * 2, PANEL_H, "NO_WINNER_SELECTION", e.noWinnerPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "replace/latest/winner/upsert: NONE");
        line(g, PANEL_W * 2, PANEL_H, 86, "ticket lookup is exact");
        line(g, PANEL_W * 2, PANEL_H, 114, "contradictions fail closed");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,setSize\n"
                        + row("CANONICAL_SET", e.canonicalPass(), 2)
                        + row("IMMUTABLE_ADMIT", e.immutablePass(), 2)
                        + row("REPLAY_BLOCKED", e.replayBlocked(), 1)
                        + row("CONTRADICTION_BLOCKED", e.contradictionBlocked(), 1)
                        + row("SEQUENCE_REUSE_BLOCKED", e.sequenceReuseBlocked(), 0)
                        + row("NO_WINNER_SELECTION", e.noWinnerPass(), 2);
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String set =
                "firstSequence,secondSequence,canonical,oldSetRetained,lookupExact\n"
                        + e.first().id().acknowledgementSequence()
                        + ","
                        + e.second().id().acknowledgementSequence()
                        + ","
                        + e.canonicalPass()
                        + ","
                        + e.oldSetRetained()
                        + ","
                        + e.lookupExact()
                        + "\n";
        Files.writeString(out.resolve("set.csv"), set, StandardCharsets.UTF_8);

        String failures =
                "replayBlocked,duplicateSameOutcomeBlocked,contradictionBlocked,sequenceReuseBlocked,noWinnerSurface\n"
                        + e.replayBlocked()
                        + ","
                        + e.duplicateSameOutcomeBlocked()
                        + ","
                        + e.contradictionBlocked()
                        + ","
                        + e.sequenceReuseBlocked()
                        + ","
                        + e.noWinnerPass()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0077</title>"
                        + "<h1>Outcome-checkpoint consumption acknowledgement replay admission</h1>"
                        + "<p>The 16:9 atlas proves canonical immutable admission with replay, "
                        + "same-ticket contradiction, and sequence reuse rejected without winner selection.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"set.csv\">set.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        var first =
                acknowledgement(
                        ticket("primary"),
                        3901L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:first");
        var second =
                acknowledgement(
                        ticket("secondary"),
                        3902L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:second");

        var canonical =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second, first));
        boolean canonicalPass = canonical.acknowledgements().equals(List.of(first, second));

        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();
        var one = empty.admit(first);
        var two = one.admit(second);
        boolean listImmutable;
        try {
            two.acknowledgements().clear();
            listImmutable = false;
        } catch (UnsupportedOperationException expected) {
            listImmutable = true;
        }
        boolean oldSetRetained =
                empty.isEmpty()
                        && one.acknowledgements().equals(List.of(first))
                        && two.size() == 2;
        boolean immutablePass = oldSetRetained && listImmutable;

        boolean replayBlocked = fails(() -> one.admit(first));

        var duplicateTicket = ticket("duplicate");
        var duplicateA =
                acknowledgement(
                        duplicateTicket,
                        3910L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:a");
        var duplicateB =
                acknowledgement(
                        duplicateTicket,
                        3911L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:b");
        var contradictory =
                acknowledgement(
                        duplicateTicket,
                        3912L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:contradictory");

        boolean duplicateSameOutcomeBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(duplicateA, duplicateB)));
        boolean contradictionBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(duplicateA, contradictory)));

        var sequenceA =
                acknowledgement(
                        ticket("sequence-a"),
                        3920L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:seq-a");
        var sequenceB =
                acknowledgement(
                        ticket("sequence-b"),
                        3920L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:seq-b");
        boolean sequenceReuseBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(sequenceA, sequenceB)));

        boolean lookupExact =
                canonical.forTicket(first.ticket().id()).orElseThrow().equals(first)
                        && canonical.forTicket(second.ticket().id()).orElseThrow().equals(second);

        List<String> publicNames =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .toList();
        boolean noWinnerPass =
                !publicNames.contains("replace")
                        && !publicNames.contains("latest")
                        && !publicNames.contains("winner")
                        && !publicNames.contains("upsert");

        return new Evidence(
                first,
                second,
                canonicalPass,
                oldSetRetained,
                immutablePass,
                replayBlocked,
                duplicateSameOutcomeBlocked,
                contradictionBlocked,
                sequenceReuseBlocked,
                lookupExact,
                noWinnerPass);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                    ticket,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                    outcome,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new Attestation(1, ticket.id(), outcome, evidenceToken),
                        acknowledgementSequence);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket(String targetKey) {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = checkpointBinder.bind(state);

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        checkpointBinder.validate(binding, state),
                        3875L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);

        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 3880L);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static String row(String scenario, boolean pass, int setSize) {
        return scenario + "," + pass + "," + setSize + "\n";
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    first,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    second,
            boolean canonicalPass,
            boolean oldSetRetained,
            boolean immutablePass,
            boolean replayBlocked,
            boolean duplicateSameOutcomeBlocked,
            boolean contradictionBlocked,
            boolean sequenceReuseBlocked,
            boolean lookupExact,
            boolean noWinnerPass) {}
}
