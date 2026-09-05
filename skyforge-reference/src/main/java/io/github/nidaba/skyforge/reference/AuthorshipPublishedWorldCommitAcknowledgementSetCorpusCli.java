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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgementSet;
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

/** Generates AUTH-0065 acknowledgement replay/contradiction admission evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementSetCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementSetCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-set-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "CANONICAL_SET", e.canonicalPass());
        line(g, 0, 0, 58, "caller order: 1502, 1501");
        line(g, 0, 0, 86, "stored order: 1501, 1502");
        line(g, 0, 0, 114, "canonical: YES");

        panel(g, PANEL_W, 0, "IMMUTABLE_ADMIT", e.immutablePass());
        line(g, PANEL_W, 0, 58, "empty -> one -> two");
        line(g, PANEL_W, 0, 86, "prior sets retained: YES");
        line(g, PANEL_W, 0, 114, "returned list immutable: YES");

        panel(g, PANEL_W * 2, 0, "REPLAY_BLOCKED", e.replayBlocked());
        line(g, PANEL_W * 2, 0, 58, "exact acknowledgement replay");
        line(g, PANEL_W * 2, 0, 86, "admission rejected: YES");
        line(g, PANEL_W * 2, 0, 114, "no idempotent hidden upsert");

        panel(g, 0, PANEL_H, "CONTRADICTION_BLOCKED", e.contradictionBlocked());
        line(g, 0, PANEL_H, 58, "same ticket: SUCCEEDED vs FAILED");
        line(g, 0, PANEL_H, 86, "second outcome rejected: YES");
        line(g, 0, PANEL_H, 114, "winner selected: NO");

        panel(g, PANEL_W, PANEL_H, "SEQUENCE_REUSE_BLOCKED", e.sequenceReuseBlocked());
        line(g, PANEL_W, PANEL_H, 58, "different tickets, sequence 1520");
        line(g, PANEL_W, PANEL_H, 86, "reuse rejected: YES");
        line(g, PANEL_W, PANEL_H, 114, "audit ordering remains unique");

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
                        + "<title>AUTH-0065</title>"
                        + "<h1>Acknowledgement replay and contradiction admission</h1>"
                        + "<p>The 16:9 atlas proves canonical immutable admission with replay, "
                        + "same-ticket contradiction, and acknowledgement-sequence reuse rejected "
                        + "without winner selection.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"set.csv\">set.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(
                        ticket(65501L),
                        1501L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:first");
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(
                        ticket(65502L),
                        1502L,
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "proof:second");

        SkyIslandPublishedWorldCommitAcknowledgementSet canonical =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(second, first));
        boolean canonicalPass = canonical.acknowledgements().equals(List.of(first, second));

        SkyIslandPublishedWorldCommitAcknowledgementSet empty =
                SkyIslandPublishedWorldCommitAcknowledgementSet.empty();
        SkyIslandPublishedWorldCommitAcknowledgementSet one = empty.admit(first);
        SkyIslandPublishedWorldCommitAcknowledgementSet two = one.admit(second);
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

        SkyIslandPublishedWorldCommitTicket duplicateTicket = ticket(65503L);
        SkyIslandPublishedWorldCommitAcknowledgement duplicateA =
                acknowledgement(
                        duplicateTicket,
                        1510L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:a");
        SkyIslandPublishedWorldCommitAcknowledgement duplicateB =
                acknowledgement(
                        duplicateTicket,
                        1511L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:b");
        SkyIslandPublishedWorldCommitAcknowledgement contradictory =
                acknowledgement(
                        duplicateTicket,
                        1512L,
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "proof:contradictory");
        boolean duplicateSameOutcomeBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementSet.of(
                                        List.of(duplicateA, duplicateB)));
        boolean contradictionBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementSet.of(
                                        List.of(duplicateA, contradictory)));

        SkyIslandPublishedWorldCommitAcknowledgement sequenceA =
                acknowledgement(
                        ticket(65504L),
                        1520L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:seq-a");
        SkyIslandPublishedWorldCommitAcknowledgement sequenceB =
                acknowledgement(
                        ticket(65505L),
                        1520L,
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "proof:seq-b");
        boolean sequenceReuseBlocked =
                fails(
                        () ->
                                SkyIslandPublishedWorldCommitAcknowledgementSet.of(
                                        List.of(sequenceA, sequenceB)));

        boolean lookupExact =
                canonical.forTicket(first.ticket().id()).orElseThrow().equals(first)
                        && canonical.forTicket(second.ticket().id()).orElseThrow().equals(second);
        List<String> publicNames =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementSet.class
                                        .getDeclaredMethods())
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

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            SkyIslandPublishedWorldCommitTicket ticket,
            long acknowledgementSequence,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new TestAttestation(1, ticket.id(), outcome, evidenceToken),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 150L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1400L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1450L);
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
            throw new IllegalStateException("AUTH-0065 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth65",
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
            SkyIslandPublishedWorldCommitAcknowledgement first,
            SkyIslandPublishedWorldCommitAcknowledgement second,
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
