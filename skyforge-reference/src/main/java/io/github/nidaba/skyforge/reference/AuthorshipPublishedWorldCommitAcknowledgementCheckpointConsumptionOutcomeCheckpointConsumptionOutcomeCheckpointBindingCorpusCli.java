package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.world.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

/** Generates AUTH-0079 exact activation/currentness proof evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-binding-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "ACTIVATE_EXACT", e.activatePass());
        line(g, 0, 0, 58, "AUTH-0078 checkpoint revision: 1");
        line(g, 0, 0, 86, "initial activation exact: YES");
        line(g, 0, 0, 114, "extra activation revision: NONE");

        panel(g, PANEL_W, 0, "CURRENT_BINDING", e.currentPass());
        line(g, PANEL_W, 0, 58, "binding captures exact active checkpoint");
        line(g, PANEL_W, 0, 86, "validation: CURRENT");
        line(g, PANEL_W, 0, 114, "requireCurrent: PASS");

        panel(g, PANEL_W * 2, 0, "STALE_NO_REFRESH", e.stalePass());
        line(g, PANEL_W * 2, 0, 58, "replacement checkpoint revision: 2");
        line(g, PANEL_W * 2, 0, 86, "old binding validation: STALE");
        line(g, PANEL_W * 2, 0, 114, "old exact ticket view retained: YES");

        panel(g, 0, PANEL_H, "INACTIVE_DISTINCT", e.inactivePass());
        line(g, 0, PANEL_H, 58, "no active checkpoint");
        line(g, 0, PANEL_H, 86, "validation: INACTIVE");
        line(g, 0, PANEL_H, 114, "requireCurrent: REJECTED");

        panel(g, PANEL_W, PANEL_H, "CAS_REPLACEMENT", e.casPass());
        line(g, PANEL_W, PANEL_H, 58, "exact expected-current ID required");
        line(g, PANEL_W, PANEL_H, 86, "strictly higher revision required");
        line(g, PANEL_W, PANEL_H, 114, "stale/same-revision replacement: BLOCKED");

        panel(g, PANEL_W * 2, PANEL_H, "NO_DURABILITY_CLAIM", e.noDurabilityPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "binder surface: bind + validate only");
        line(g, PANEL_W * 2, PANEL_H, 86, "refresh/latest/retry: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "persistence/durability claim: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,status,checkpointRevision\n"
                        + row("ACTIVATE_EXACT", e.activatePass(), "CURRENT", 1L)
                        + row("CURRENT_BINDING", e.currentPass(), "CURRENT", 1L)
                        + row("STALE_NO_REFRESH", e.stalePass(), "STALE", 2L)
                        + row("INACTIVE_DISTINCT", e.inactivePass(), "INACTIVE", 0L)
                        + row("CAS_REPLACEMENT", e.casPass(), "CURRENT", 2L)
                        + row("NO_DURABILITY_CLAIM", e.noDurabilityPass(), "CURRENT", 1L);
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String binding =
                "bindingToken,currentStatus,staleStatus,inactiveStatus,oldLookupRetained\n"
                        + e.binding().canonicalToken()
                        + ","
                        + e.current().status()
                        + ","
                        + e.stale().status()
                        + ","
                        + e.inactive().status()
                        + ","
                        + e.oldLookupRetained()
                        + "\n";
        Files.writeString(out.resolve("binding.csv"), binding, StandardCharsets.UTF_8);

        String failures =
                "reentryBlocked,staleExpectedBlocked,sameRevisionBlocked,staleRequireBlocked,inactiveRequireBlocked\n"
                        + e.reentryBlocked()
                        + ","
                        + e.staleExpectedBlocked()
                        + ","
                        + e.sameRevisionBlocked()
                        + ","
                        + e.staleRequireBlocked()
                        + ","
                        + e.inactiveRequireBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0079</title>"
                        + "<h1>Outcome-checkpoint-consumption outcome-checkpoint activation binding</h1>"
                        + "<p>The 16:9 atlas proves exact activation, immutable binding currentness, "
                        + "CAS replacement, stale no-refresh behavior, and absence of durability claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"binding.csv\">binding.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        Fixture f = fixture();
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        var binding = binder.bind(firstState);
        var current = binder.validate(binding, firstState);
        var secondState = firstState.replace(f.firstCheckpoint().id(), f.secondCheckpoint());
        var stale = binder.validate(binding, secondState);
        var inactive =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());

        var sameRevisionChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(
                                f.firstCheckpoint().id().checkpointRevision(),
                                f.secondCheckpoint().acknowledgementSet());

        boolean reentryBlocked =
                fails(() -> firstState.activateInitial(f.secondCheckpoint()));
        boolean staleExpectedBlocked =
                fails(() -> firstState.replace(f.secondCheckpoint().id(), f.secondCheckpoint()));
        boolean sameRevisionBlocked =
                fails(() -> firstState.replace(f.firstCheckpoint().id(), sameRevisionChanged));
        boolean staleRequireBlocked = fails(stale::requireCurrent);
        boolean inactiveRequireBlocked = fails(inactive::requireCurrent);

        List<String> binderMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .sorted()
                        .toList();

        boolean oldLookupRetained =
                binding.forTicket(f.firstAcknowledgement().ticket().id()).isPresent()
                        && binding.forTicket(f.secondAcknowledgement().ticket().id()).isEmpty();

        boolean activatePass =
                firstState.active()
                        && firstState.requireActive().id().equals(f.firstCheckpoint().id());
        boolean currentPass =
                current.current()
                        && current.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .CURRENT
                        && succeeds(current::requireCurrent);
        boolean stalePass =
                stale.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .STALE
                        && stale.currentCheckpointId().equals(Optional.of(f.secondCheckpoint().id()))
                        && oldLookupRetained
                        && staleRequireBlocked;
        boolean inactivePass =
                inactive.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .INACTIVE
                        && inactive.currentCheckpointId().isEmpty()
                        && inactiveRequireBlocked;
        boolean casPass =
                secondState.requireActive().id().equals(f.secondCheckpoint().id())
                        && firstState.requireActive().id().equals(f.firstCheckpoint().id())
                        && staleExpectedBlocked
                        && sameRevisionBlocked;
        boolean noDurabilityPass =
                binderMethods.equals(List.of("bind", "validate"))
                        && !binderMethods.contains("refresh")
                        && !binderMethods.contains("rebind")
                        && !binderMethods.contains("latest")
                        && !binderMethods.contains("retry");

        return new Evidence(
                binding,
                current,
                stale,
                inactive,
                activatePass,
                currentPass,
                stalePass,
                inactivePass,
                casPass,
                noDurabilityPass,
                oldLookupRetained,
                reentryBlocked,
                staleExpectedBlocked,
                sameRevisionBlocked,
                staleRequireBlocked,
                inactiveRequireBlocked);
    }

    private static Fixture fixture() {
        var first = acknowledgement("first", 4301L);
        var second = acknowledgement("second", 4302L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        return new Fixture(
                first,
                second,
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one),
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two));
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
                        4275L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, upstreamState);

        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 4280L);
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

    private static String row(String scenario, boolean pass, String status, long revision) {
        return scenario + "," + pass + "," + status + "," + revision + "\n";
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    firstAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    secondAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    secondCheckpoint) {}

    private record Attestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                    binding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    inactive,
            boolean activatePass,
            boolean currentPass,
            boolean stalePass,
            boolean inactivePass,
            boolean casPass,
            boolean noDurabilityPass,
            boolean oldLookupRetained,
            boolean reentryBlocked,
            boolean staleExpectedBlocked,
            boolean sameRevisionBlocked,
            boolean staleRequireBlocked,
            boolean inactiveRequireBlocked) {}
}
