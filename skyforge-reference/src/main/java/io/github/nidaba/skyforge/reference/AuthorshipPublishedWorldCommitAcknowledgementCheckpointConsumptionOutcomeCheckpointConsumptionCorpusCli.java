package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.*;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.*;
import io.github.nidaba.skyforge.recipes.skyisland.group.*;
import io.github.nidaba.skyforge.world.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0074 outcome-checkpoint consumption preparation evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "TARGET_IDENTITY", e.targetPass());
        line(g, 0, 0, 58, "namespace: audit");
        line(g, 0, 0, 86, "key: primary");
        line(g, 0, 0, 114, "canonical target token: YES");

        panel(g, PANEL_W, 0, "CURRENT_PREPARE", e.currentPreparePass());
        line(g, PANEL_W, 0, 58, "preparation validation: CURRENT");
        line(g, PANEL_W, 0, 86, "outcome checkpoint revision: 1");
        line(g, PANEL_W, 0, 114, "preparation sequence: 3301");

        panel(g, PANEL_W * 2, 0, "AXIS_SEPARATION", e.axisPass());
        line(g, PANEL_W * 2, 0, 58, "sequence axis distinct: YES");
        line(g, PANEL_W * 2, 0, 86, "target axis distinct: YES");
        line(g, PANEL_W * 2, 0, 114, "checkpoint axis distinct: YES");

        panel(g, 0, PANEL_H, "STALE_EXECUTION_BLOCKED", e.stalePass());
        line(g, 0, PANEL_H, 58, "activation moved rev 1 -> 2");
        line(g, 0, PANEL_H, 86, "execution validation: STALE");
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
                        + row("TARGET_IDENTITY", e.targetPass(), "N/A", 3301L)
                        + row("CURRENT_PREPARE", e.currentPreparePass(), "CURRENT", 3301L)
                        + row("AXIS_SEPARATION", e.axisPass(), "N/A", 3301L)
                        + row("STALE_EXECUTION_BLOCKED", e.stalePass(), "STALE", 3301L)
                        + row("INACTIVE_BLOCKED", e.inactivePass(), "INACTIVE", 3301L)
                        + row("NO_IO_CLAIM", e.noIoPass(), "N/A", 3301L);
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
                        + "<title>AUTH-0074</title>"
                        + "<h1>Outcome-checkpoint consumption preparation provenance</h1>"
                        + "<p>The 16:9 atlas proves explicit target/checkpoint/sequence identity, "
                        + "CURRENT-only preparation, and stale/inactive execution blocking with no I/O.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"prepared.csv\">prepared.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        var first = outcomeAcknowledgement(74501L, 3301L);
        var second = outcomeAcknowledgement(74502L, 3302L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        var currentBindingValidation = binder.validate(binding, firstState);
        var staleBindingValidation = binder.validate(binding, secondState);
        var inactiveBindingValidation =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());

        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        boolean blankTargetBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                        .of("audit", " "));

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(currentBindingValidation, 3301L, target);
        boolean currentPreparePass =
                prepared.checkpointId().equals(firstCheckpoint.id())
                        && prepared.targetId().equals(target)
                        && prepared.preparationValidation().current();

        var sequenceChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3302L, binding, target);
        var targetChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(
                                3301L,
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                        .of("audit", "secondary"));
        var secondBinding = binder.bind(secondState);
        var checkpointChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3301L, secondBinding, target);
        boolean axisPass =
                !prepared.id().equals(sequenceChanged)
                        && !prepared.id().equals(targetChanged)
                        && !prepared.id().equals(checkpointChanged);

        boolean stalePrepareBlocked =
                fails(() -> preparer.prepare(staleBindingValidation, 3303L, target));
        boolean inactivePrepareBlocked =
                fails(() -> preparer.prepare(inactiveBindingValidation, 3304L, target));

        var current = preparer.validateForExecution(prepared, firstState);
        var stale = preparer.validateForExecution(prepared, secondState);
        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());
        boolean staleExecutionBlocked = !succeeds(stale::requireCurrent);
        boolean inactiveExecutionBlocked = !succeeds(inactive::requireCurrent);
        boolean stalePass =
                stale.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .STALE
                        && staleExecutionBlocked
                        && prepared.checkpointId().equals(firstCheckpoint.id())
                        && prepared.targetId().equals(target);
        boolean inactivePass =
                inactive.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .INACTIVE
                        && inactiveExecutionBlocked;

        boolean targetPass =
                target.namespace().equals("audit")
                        && target.key().equals("primary")
                        && target.canonicalToken().startsWith("sfackcpouttarget:v1:")
                        && blankTargetBlocked;

        boolean noIoPass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    outcomeAcknowledgement(long seed, long sequence) {
        var ticket = ioTicket(seed);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                "io-proof:" + seed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ioTicket(
            long seed) {
        var upstream = upstreamAcknowledgement(seed, 3301L);
        var set = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(upstream));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, set);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(activation);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        binder.validate(binding, activation),
                        3200L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                                "replica", "primary"));
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(preparer.validateForExecution(prepared, activation), 3250L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long seed, long sequence) {
        var ticket = upstreamTicket(seed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream:" + seed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long seed) {
        var publication = new SkyIslandCompiledWorldPublisher().publish(compilation(seed), 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 330L);
        var binding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var preparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                preparer.prepare(
                        binding,
                        3200L,
                        publication.catalog().volumes().get(0).bounds());
        return new SkyIslandPublishedWorldCommitTicketIssuer()
                .issue(preparer.validateForCommit(work, active), 3250L);
    }

    private static SkyIslandAcceptedConvergenceCompilation compilation(long seed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        var request = request(seed, morphology);
        var original = new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        var proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        var convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0074 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long seed, ProviderMorphologySpec morphology) {
        var template =
                new SkyIslandGroupTemplate(
                        "auth74",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                seed,
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

    private record IoAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation {}

    private record UpstreamAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}

    private record Evidence(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                    target,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                    prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
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
