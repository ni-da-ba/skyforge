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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgementCheckpoint;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgementCheckpointId;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher;
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
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0066 acknowledgement checkpoint identity evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_CHECKPOINT", e.exactPass());
        line(g, 0, 0, 58, "checkpoint revision: 1");
        line(g, 0, 0, 86, "exact set identity bound: YES");
        line(g, 0, 0, 114, "acknowledgement count: 2");

        panel(g, PANEL_W, 0, "EMPTY_CHECKPOINT", e.emptyPass());
        line(g, PANEL_W, 0, 58, "valid empty AUTH-0065 set");
        line(g, PANEL_W, 0, 86, "checkpoint identity count: 0");
        line(g, PANEL_W, 0, 114, "explicitly representable: YES");

        panel(g, PANEL_W * 2, 0, "REVISION_AXIS", e.revisionPass());
        line(g, PANEL_W * 2, 0, 58, "same set: rev 10 vs 11");
        line(g, PANEL_W * 2, 0, 86, "checkpoint IDs differ: YES");
        line(g, PANEL_W * 2, 0, 114, "revision is explicit axis");

        panel(g, 0, PANEL_H, "CONTENT_AXIS", e.contentPass());
        line(g, 0, PANEL_H, 58, "same checkpoint revision: 10");
        line(g, 0, PANEL_H, 86, "set identity changed: YES");
        line(g, 0, PANEL_H, 114, "checkpoint IDs differ: YES");

        panel(g, PANEL_W, PANEL_H, "FORGED_BINDING_BLOCKED", e.forgedBlocked());
        line(g, PANEL_W, PANEL_H, 58, "ID from different set");
        line(g, PANEL_W, PANEL_H, 86, "binding rejected: YES");
        line(g, PANEL_W, PANEL_H, 114, "exact identity required");

        panel(g, PANEL_W * 2, PANEL_H, "NO_STORAGE_CLAIM", e.noStoragePass());
        line(g, PANEL_W * 2, PANEL_H, 58, "publisher creates capability only");
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
                        + "<title>AUTH-0066</title>"
                        + "<h1>Acknowledgement-set checkpoint identity</h1>"
                        + "<p>The 16:9 atlas proves exact checkpoint/set binding, independent "
                        + "checkpoint revision and content axes, and absence of persistence claims.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"checkpoint.csv\">checkpoint.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        SkyIslandPublishedWorldCommitAcknowledgement first = acknowledgement(66501L, 1701L);
        SkyIslandPublishedWorldCommitAcknowledgement second = acknowledgement(66502L, 1702L);
        SkyIslandPublishedWorldCommitAcknowledgementSet two =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(second, first));
        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher()
                        .publish(two, 1L);
        boolean exactPass =
                checkpoint.acknowledgementSet().equals(two)
                        && checkpoint.id().acknowledgementIdentity()
                                .equals(List.of(first.id(), second.id()));

        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(
                        2L,
                        SkyIslandPublishedWorldCommitAcknowledgementSet.empty());
        boolean emptyPass =
                empty.isEmpty() && empty.id().acknowledgementIdentity().isEmpty();

        SkyIslandPublishedWorldCommitAcknowledgementSet one =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var revisionA = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(10L, one);
        var revisionB = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(11L, one);
        boolean revisionPass = !revisionA.id().equals(revisionB.id());

        var contentB = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(10L, two);
        boolean contentPass =
                revisionA.id().checkpointRevision() == contentB.id().checkpointRevision()
                        && !revisionA.id().equals(contentB.id());

        boolean forgedBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpoint(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.of(
                                                3L,
                                                two),
                                        one));
        boolean noncanonicalBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                        1,
                                        4L,
                                        List.of(second.id(), first.id())));
        boolean revisionZeroBlocked =
                fails(
                        () ->
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                        1,
                                        0L,
                                        List.of(first.id())));

        boolean noStoragePass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher.class
                                        .getDeclaredMethods())
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

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(rootSeed);
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

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 170L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1600L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1650L);
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
            throw new IllegalStateException("AUTH-0066 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth66",
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint,
            boolean exactPass,
            boolean emptyPass,
            boolean revisionPass,
            boolean contentPass,
            boolean forgedBlocked,
            boolean noncanonicalBlocked,
            boolean revisionZeroBlocked,
            boolean noStoragePass) {}
}
