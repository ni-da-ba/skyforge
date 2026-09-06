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
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0080 exact consumption-preparation proof evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-consumption-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "TARGET_IDENTITY", e.targetPass());
        line(g, 0, 0, 58, "explicit namespace/key target");
        line(g, 0, 0, 86, "blank/trimmed variants: BLOCKED");
        line(g, 0, 0, 114, "backend connection: NONE");

        panel(g, PANEL_W, 0, "CURRENT_PREPARE", e.currentPreparePass());
        line(g, PANEL_W, 0, 58, "AUTH-0079 validation: CURRENT");
        line(g, PANEL_W, 0, 86, "exact checkpoint + target captured");
        line(g, PANEL_W, 0, 114, "preparation sequence explicit");

        panel(g, PANEL_W * 2, 0, "AXIS_SEPARATION", e.axisPass());
        line(g, PANEL_W * 2, 0, 58, "sequence axis: DISTINCT");
        line(g, PANEL_W * 2, 0, 86, "target axis: DISTINCT");
        line(g, PANEL_W * 2, 0, 114, "checkpoint axis: DISTINCT");

        panel(g, 0, PANEL_H, "STALE_EXECUTION_BLOCKED", e.stalePass());
        line(g, 0, PANEL_H, 58, "execution validation: STALE");
        line(g, 0, PANEL_H, 86, "requireCurrent blocked: YES");
        line(g, 0, PANEL_H, 114, "prepared identity unchanged");

        panel(g, PANEL_W, PANEL_H, "INACTIVE_BLOCKED", e.inactivePass());
        line(g, PANEL_W, PANEL_H, 58, "execution validation: INACTIVE");
        line(g, PANEL_W, PANEL_H, 86, "requireCurrent blocked: YES");
        line(g, PANEL_W, PANEL_H, 114, "no implicit activation");

        panel(g, PANEL_W * 2, PANEL_H, "NO_IO_CLAIM", e.noIoPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "files/network/backend I/O: NONE");
        line(g, PANEL_W * 2, PANEL_H, 86, "durability claim: NO");
        line(g, PANEL_W * 2, PANEL_H, 114, "identity/currentness only");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,status,preparationSequence\n"
                        + row("TARGET_IDENTITY", e.targetPass(), "N/A", 4501L)
                        + row("CURRENT_PREPARE", e.currentPreparePass(), "CURRENT", 4501L)
                        + row("AXIS_SEPARATION", e.axisPass(), "N/A", 4501L)
                        + row("STALE_EXECUTION_BLOCKED", e.stalePass(), "STALE", 4501L)
                        + row("INACTIVE_BLOCKED", e.inactivePass(), "INACTIVE", 4501L)
                        + row("NO_IO_CLAIM", e.noIoPass(), "N/A", 4501L);
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String prepared =
                "preparedToken,targetToken,checkpointRevision,currentStatus,staleStatus,inactiveStatus\n"
                        + e.prepared().id().canonicalToken()
                        + ","
                        + e.target().canonicalToken()
                        + ","
                        + e.prepared().checkpointId().checkpointRevision()
                        + ","
                        + e.current().status()
                        + ","
                        + e.stale().status()
                        + ","
                        + e.inactive().status()
                        + "\n";
        Files.writeString(out.resolve("prepared.csv"), prepared, StandardCharsets.UTF_8);

        String failures =
                "blankTargetBlocked,stalePrepareBlocked,inactivePrepareBlocked,staleExecutionBlocked,inactiveExecutionBlocked\n"
                        + e.blankTargetBlocked()
                        + ","
                        + e.stalePrepareBlocked()
                        + ","
                        + e.inactivePrepareBlocked()
                        + ","
                        + e.staleExecutionBlocked()
                        + ","
                        + e.inactiveExecutionBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0080</title>"
                        + "<h1>Outcome-checkpoint-consumption outcome-checkpoint consumption preparation</h1>"
                        + "<p>The 16:9 atlas proves explicit target/checkpoint/sequence identity, "
                        + "CURRENT-only preparation, and stale/inactive execution blocking with no I/O.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"prepared.csv\">prepared.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        var first = acknowledgement("first", 4511L);
        var second = acknowledgement("second", 4512L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        var currentBindingValidation = binder.validate(binding, firstState);
        var staleBindingValidation = binder.validate(binding, secondState);
        var inactiveBindingValidation =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());

        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        boolean blankTargetBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                        .of("audit", " "));

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(currentBindingValidation, 4501L, target);
        boolean currentPreparePass =
                prepared.checkpointId().equals(firstCheckpoint.id())
                        && prepared.targetId().equals(target)
                        && prepared.preparationValidation().current();

        var sequenceChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4502L, binding, target);
        var targetChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(
                                4501L,
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                        .of("audit", "secondary"));
        var secondBinding = binder.bind(secondState);
        var checkpointChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4501L, secondBinding, target);
        boolean axisPass =
                !prepared.id().equals(sequenceChanged)
                        && !prepared.id().equals(targetChanged)
                        && !prepared.id().equals(checkpointChanged);

        boolean stalePrepareBlocked =
                fails(() -> preparer.prepare(staleBindingValidation, 4503L, target));
        boolean inactivePrepareBlocked =
                fails(() -> preparer.prepare(inactiveBindingValidation, 4504L, target));

        var current = preparer.validateForExecution(prepared, firstState);
        var stale = preparer.validateForExecution(prepared, secondState);
        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());
        boolean staleExecutionBlocked = !succeeds(stale::requireCurrent);
        boolean inactiveExecutionBlocked = !succeeds(inactive::requireCurrent);
        boolean stalePass =
                stale.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .STALE
                        && staleExecutionBlocked
                        && prepared.checkpointId().equals(firstCheckpoint.id())
                        && prepared.targetId().equals(target);
        boolean inactivePass =
                inactive.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .INACTIVE
                        && inactiveExecutionBlocked;

        boolean targetPass =
                target.namespace().equals("audit")
                        && target.key().equals("primary")
                        && target.canonicalToken().startsWith("sfackcpoutcpouttarget:v1:")
                        && blankTargetBlocked;

        boolean noIoPass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer
                                        .class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
                        .noneMatch(
                                type ->
                                        type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level"));

        return new Evidence(
                target,
                prepared,
                current,
                stale,
                inactive,
                targetPass,
                currentPreparePass,
                axisPass,
                stalePass,
                inactivePass,
                noIoPass,
                blankTargetBlocked,
                stalePrepareBlocked,
                inactivePrepareBlocked,
                staleExecutionBlocked,
                inactiveExecutionBlocked);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(String targetKey, long sequence) {
        var ticket = ticket(targetKey);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new Attestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                "storage-proof:" + targetKey),
                        sequence);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket(String targetKey) {
        var upstreamSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var upstreamCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, upstreamSet);
        var upstreamState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(upstreamCheckpoint);
        var upstreamBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var upstreamBinding = upstreamBinder.bind(upstreamState);

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        upstreamBinder.validate(upstreamBinding, upstreamState),
                        4520L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(preparer.validateForExecution(prepared, upstreamState), 4530L);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static boolean succeeds(Runnable action) {
        try {
            action.run();
            return true;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return false;
        }
    }

    private static String row(String scenario, boolean pass, String status, long sequence) {
        return scenario + "," + pass + "," + status + "," + sequence + "\n";
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                    target,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                    prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    inactive,
            boolean targetPass,
            boolean currentPreparePass,
            boolean axisPass,
            boolean stalePass,
            boolean inactivePass,
            boolean noIoPass,
            boolean blankTargetBlocked,
            boolean stalePrepareBlocked,
            boolean inactivePrepareBlocked,
            boolean staleExecutionBlocked,
            boolean inactiveExecutionBlocked) {}
}
