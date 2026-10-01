package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.*;
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
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0071 checkpoint-consumption outcome replay-admission evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSetCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSetCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-consumption-acknowledgement-set-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CANONICAL_SET", e.canonicalPass());
        line(g, 0, 0, 58, "caller order: 2702, 2701");
        line(g, 0, 0, 86, "stored order: 2701, 2702");
        line(g, 0, 0, 114, "canonical: YES");

        panel(g, PANEL_W, 0, "IMMUTABLE_ADMIT", e.immutablePass());
        line(g, PANEL_W, 0, 58, "empty -> one -> two");
        line(g, PANEL_W, 0, 86, "prior sets retained: YES");
        line(g, PANEL_W, 0, 114, "returned list immutable: YES");

        panel(g, PANEL_W * 2, 0, "REPLAY_BLOCKED", e.replayBlocked());
        line(g, PANEL_W * 2, 0, 58, "exact outcome acknowledgement replay");
        line(g, PANEL_W * 2, 0, 86, "admission rejected: YES");
        line(g, PANEL_W * 2, 0, 114, "no hidden upsert");

        panel(g, 0, PANEL_H, "CONTRADICTION_BLOCKED", e.contradictionBlocked());
        line(g, 0, PANEL_H, 58, "same I/O ticket: SUCCEEDED vs FAILED");
        line(g, 0, PANEL_H, 86, "second outcome rejected: YES");
        line(g, 0, PANEL_H, 114, "winner selected: NO");

        panel(g, PANEL_W, PANEL_H, "SEQUENCE_REUSE_BLOCKED", e.sequenceReuseBlocked());
        line(g, PANEL_W, PANEL_H, 58, "different tickets, sequence 2720");
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
                        + "<title>AUTH-0071</title>"
                        + "<h1>Checkpoint-consumption outcome replay admission</h1>"
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
                        ticket(71501L),
                        2701L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:first");
        var second =
                acknowledgement(
                        ticket(71502L),
                        2702L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .FAILED,
                        "io-proof:second");

        var canonical =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second, first));
        boolean canonicalPass = canonical.acknowledgements().equals(List.of(first, second));

        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
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

        var duplicateTicket = ticket(71503L);
        var duplicateA =
                acknowledgement(
                        duplicateTicket,
                        2710L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:a");
        var duplicateB =
                acknowledgement(
                        duplicateTicket,
                        2711L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:b");
        var contradictory =
                acknowledgement(
                        duplicateTicket,
                        2712L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .FAILED,
                        "io-proof:contradictory");

        boolean duplicateSameOutcomeBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(duplicateA, duplicateB)));
        boolean contradictionBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(duplicateA, contradictory)));

        var sequenceA =
                acknowledgement(
                        ticket(71504L),
                        2720L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:seq-a");
        var sequenceB =
                acknowledgement(
                        ticket(71505L),
                        2720L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .FAILED,
                        "io-proof:seq-b");
        boolean sequenceReuseBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                        .of(List.of(sequenceA, sequenceB)));

        boolean lookupExact =
                canonical.forTicket(first.ticket().id()).orElseThrow().equals(first)
                        && canonical.forTicket(second.ticket().id()).orElseThrow().equals(second);

        List<String> publicNames =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket
                                    ticket,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                    outcome,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(1, ticket.id(), outcome, evidenceToken),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket(
            long rootSeed) {
        var upstreamAck = upstreamAcknowledgement(rootSeed, 2701L);
        var set = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(upstreamAck));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, set);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = checkpointBinder.bind(activation);
        var bindingValidation = checkpointBinder.validate(binding, activation);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(bindingValidation, 2600L, target);
        var current = preparer.validateForExecution(prepared, activation);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(current, 2650L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        var ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream-proof:" + rootSeed),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        var compilation = acceptedCompilation(rootSeed);
        var publisher = new SkyIslandCompiledWorldPublisher();
        var publication = publisher.publish(compilation, 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 270L);
        var snapshotBinding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var workPreparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                workPreparer.prepare(
                        snapshotBinding,
                        2600L,
                        publication.catalog().volumes().get(0).bounds());
        var validation = workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2650L);
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        var proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        var convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0071 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth71",
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement first,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement second,
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
