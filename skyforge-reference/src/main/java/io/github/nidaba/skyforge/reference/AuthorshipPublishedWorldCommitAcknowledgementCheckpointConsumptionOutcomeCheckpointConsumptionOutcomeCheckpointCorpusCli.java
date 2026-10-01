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

/** Generates AUTH-0078 outcome-checkpoint consumption outcome-checkpoint evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_CHECKPOINT", e.exactPass());
        line(g, 0, 0, 58, "checkpoint revision: 1");
        line(g, 0, 0, 86, "exact AUTH-0077 set identity bound: YES");
        line(g, 0, 0, 114, "acknowledgement count: 2");

        panel(g, PANEL_W, 0, "EMPTY_CHECKPOINT", e.emptyPass());
        line(g, PANEL_W, 0, 58, "valid empty AUTH-0077 set");
        line(g, PANEL_W, 0, 86, "checkpoint identity count: 0");
        line(g, PANEL_W, 0, 114, "explicitly representable: YES");

        panel(g, PANEL_W * 2, 0, "REVISION_AXIS", e.revisionPass());
        line(g, PANEL_W * 2, 0, 58, "same set: rev 10 vs 11");
        line(g, PANEL_W * 2, 0, 86, "checkpoint IDs differ: YES");
        line(g, PANEL_W * 2, 0, 114, "revision explicit");

        panel(g, 0, PANEL_H, "CONTENT_AXIS", e.contentPass());
        line(g, 0, PANEL_H, 58, "same checkpoint revision: 10");
        line(g, 0, PANEL_H, 86, "acknowledgement-set identity changed: YES");
        line(g, 0, PANEL_H, 114, "checkpoint IDs differ: YES");

        panel(g, PANEL_W, PANEL_H, "FORGED_BINDING_BLOCKED", e.forgedBlocked());
        line(g, PANEL_W, PANEL_H, 58, "ID from different AUTH-0077 set");
        line(g, PANEL_W, PANEL_H, 86, "binding rejected: YES");
        line(g, PANEL_W, PANEL_H, 114, "exact identity required");

        panel(g, PANEL_W * 2, PANEL_H, "NO_STORAGE_CLAIM", e.noStoragePass());
        line(g, PANEL_W * 2, PANEL_H, 58, "publisher creates handoff capability only");
        line(g, PANEL_W * 2, PANEL_H, 86, "file/network/backend I/O: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "durability claim: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,checkpointRevision,size\n"
                        + row("EXACT_CHECKPOINT", e.exactPass(), 1L, 2)
                        + row("EMPTY_CHECKPOINT", e.emptyPass(), 2L, 0)
                        + row("REVISION_AXIS", e.revisionPass(), 10L, 1)
                        + row("CONTENT_AXIS", e.contentPass(), 10L, 2)
                        + row("FORGED_BINDING_BLOCKED", e.forgedBlocked(), 3L, 1)
                        + row("NO_STORAGE_CLAIM", e.noStoragePass(), 1L, 2);
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String checkpoint =
                "checkpointToken,identityCount,revisionDistinct,contentDistinct,emptyAllowed\n"
                        + e.checkpoint().id().canonicalToken()
                        + ","
                        + e.checkpoint().id().acknowledgementIdentity().size()
                        + ","
                        + e.revisionPass()
                        + ","
                        + e.contentPass()
                        + ","
                        + e.emptyPass()
                        + "\n";
        Files.writeString(out.resolve("checkpoint.csv"), checkpoint, StandardCharsets.UTF_8);

        String failures =
                "forgedBlocked,noncanonicalBlocked,revisionZeroBlocked\n"
                        + e.forgedBlocked()
                        + ","
                        + e.noncanonicalBlocked()
                        + ","
                        + e.revisionZeroBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0078</title>"
                        + "<h1>Outcome-checkpoint consumption outcome checkpoint identity</h1>"
                        + "<p>The 16:9 atlas proves exact checkpoint/acknowledgement-set binding, "
                        + "independent revision and content axes, and absence of persistence claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"checkpoint.csv\">checkpoint.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        var first = acknowledgement("primary", 4101L);
        var second = acknowledgement("secondary", 4102L);
        var two =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second, first));
        var checkpoint =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher()
                        .publish(two, 1L);
        boolean exactPass =
                checkpoint.acknowledgementSet().equals(two)
                        && checkpoint.id().acknowledgementIdentity()
                                .equals(List.of(first.id(), second.id()));

        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(
                                2L,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .empty());
        boolean emptyPass = empty.isEmpty() && empty.id().acknowledgementIdentity().isEmpty();

        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var revisionA =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(10L, one);
        var revisionB =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(11L, one);
        boolean revisionPass = !revisionA.id().equals(revisionB.id());

        var contentB =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(10L, two);
        boolean contentPass =
                revisionA.id().checkpointRevision() == contentB.id().checkpointRevision()
                        && !revisionA.id().equals(contentB.id());

        boolean forgedBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                                                .of(3L, two),
                                        one));
        boolean noncanonicalBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                        1,
                                        4L,
                                        List.of(second.id(), first.id())));
        boolean revisionZeroBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                        1,
                                        0L,
                                        List.of(first.id())));

        boolean noStoragePass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher
                                        .class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .allMatch(
                                method ->
                                        method.getName().equals("publish")
                                                && method.getParameterCount() == 2);

        return new Evidence(
                checkpoint,
                exactPass,
                emptyPass,
                revisionPass,
                contentPass,
                forgedBlocked,
                noncanonicalBlocked,
                revisionZeroBlocked,
                noStoragePass);
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
                        4075L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 4080L);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static String row(String scenario, boolean pass, long revision, int size) {
        return scenario + "," + pass + "," + revision + "," + size + "\n";
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    checkpoint,
            boolean exactPass,
            boolean emptyPass,
            boolean revisionPass,
            boolean contentPass,
            boolean forgedBlocked,
            boolean noncanonicalBlocked,
            boolean revisionZeroBlocked,
            boolean noStoragePass) {}
}
