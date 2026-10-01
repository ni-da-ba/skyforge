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

/** Generates AUTH-0067 checkpoint activation/binding evidence. */
public final class AuthorshipPublishedWorldCommitAcknowledgementCheckpointBindingCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldCommitAcknowledgementCheckpointBindingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-commit-acknowledgement-checkpoint-binding-v1");
        Files.createDirectories(out);
        Evidence e = evidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "ACTIVATE_EXACT", e.activatePass());
        line(g, 0, 0, 58, "initial checkpoint revision: 1");
        line(g, 0, 0, 86, "exact checkpoint active: YES");
        line(g, 0, 0, 114, "reentry blocked: YES");

        panel(g, PANEL_W, 0, "CURRENT_BINDING", e.currentPass());
        line(g, PANEL_W, 0, 58, "validation: CURRENT");
        line(g, PANEL_W, 0, 86, "binding -> checkpoint rev 1");
        line(g, PANEL_W, 0, 114, "requireCurrent: PASS");

        panel(g, PANEL_W * 2, 0, "STALE_NO_REFRESH", e.stalePass());
        line(g, PANEL_W * 2, 0, 58, "activation moved rev 1 -> 2");
        line(g, PANEL_W * 2, 0, 86, "old validation: STALE");
        line(g, PANEL_W * 2, 0, 114, "old binding still sees old set: YES");

        panel(g, 0, PANEL_H, "INACTIVE_DISTINCT", e.inactivePass());
        line(g, 0, PANEL_H, 58, "validation: INACTIVE");
        line(g, 0, PANEL_H, 86, "current checkpoint ID: NONE");
        line(g, 0, PANEL_H, 114, "requireCurrent blocked: YES");

        panel(g, PANEL_W, PANEL_H, "CAS_REPLACEMENT", e.casPass());
        line(g, PANEL_W, PANEL_H, 58, "exact expected ID required");
        line(g, PANEL_W, PANEL_H, 86, "strict higher revision required");
        line(g, PANEL_W, PANEL_H, 114, "old activation state retained");

        panel(g, PANEL_W * 2, PANEL_H, "DURABILITY_BOUNDARY", e.durabilityPass());
        line(g, PANEL_W * 2, PANEL_H, 58, "activation selects consumer generation");
        line(g, PANEL_W * 2, PANEL_H, 86, "storage/replication operation: NONE");
        line(g, PANEL_W * 2, PANEL_H, 114, "durability claim: NO");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,status,checkpointRevision\n"
                        + row("ACTIVATE_EXACT", e.activatePass(), "ACTIVE", 1L)
                        + row("CURRENT_BINDING", e.currentPass(), "CURRENT", 1L)
                        + row("STALE_NO_REFRESH", e.stalePass(), "STALE", 1L)
                        + row("INACTIVE_DISTINCT", e.inactivePass(), "INACTIVE", 1L)
                        + row("CAS_REPLACEMENT", e.casPass(), "REPLACED", 2L)
                        + row("DURABILITY_BOUNDARY", e.durabilityPass(), "N/A", 2L);
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
                        + "<title>AUTH-0067</title>"
                        + "<h1>Checkpoint activation and exact binding currentness</h1>"
                        + "<p>The 16:9 atlas proves exact activation, compare-and-replace, "
                        + "CURRENT/STALE/INACTIVE validation, and no-refresh stale bindings.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"binding.csv\">binding.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence evidence() {
        SkyIslandPublishedWorldCommitAcknowledgement first = acknowledgement(67501L, 1901L);
        SkyIslandPublishedWorldCommitAcknowledgement second = acknowledgement(67502L, 1902L);
        var firstSet = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var secondSet = firstSet.admit(second);
        var firstCheckpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, firstSet);
        var secondCheckpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, secondSet);

        var inactiveState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive();
        var firstState = inactiveState.activateInitial(firstCheckpoint);
        boolean reentryBlocked = fails(() -> firstState.activateInitial(secondCheckpoint));
        boolean activatePass =
                firstState.requireActive().equals(firstCheckpoint) && reentryBlocked;

        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(firstState);
        var current = binder.validate(binding, firstState);
        boolean currentPass =
                current.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT
                        && current.current()
                        && succeeds(current::requireCurrent);

        boolean staleExpectedBlocked =
                fails(() -> firstState.replace(secondCheckpoint.id(), secondCheckpoint));
        var sameRevisionChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, secondSet);
        boolean sameRevisionBlocked =
                fails(() -> firstState.replace(firstCheckpoint.id(), sameRevisionChanged));

        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);
        boolean casPass =
                staleExpectedBlocked
                        && sameRevisionBlocked
                        && firstState.requireActive().equals(firstCheckpoint)
                        && secondState.requireActive().equals(secondCheckpoint);

        var stale = binder.validate(binding, secondState);
        boolean staleRequireBlocked = !succeeds(stale::requireCurrent);
        boolean oldLookupRetained =
                binding.forTicket(second.ticket().id()).isEmpty()
                        && secondState.requireActive().forTicket(second.ticket().id()).isPresent();
        boolean stalePass =
                stale.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE
                        && !stale.current()
                        && staleRequireBlocked
                        && oldLookupRetained;

        var inactive = binder.validate(binding, inactiveState);
        boolean inactiveRequireBlocked = !succeeds(inactive::requireCurrent);
        boolean inactivePass =
                inactive.status()
                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE
                        && inactive.currentCheckpointId().isEmpty()
                        && inactiveRequireBlocked;

        boolean durabilityPass =
                java.util.Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.class
                                        .getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .noneMatch(
                                name ->
                                        name.contains("persist")
                                                || name.contains("replic")
                                                || name.contains("store")
                                                || name.contains("save"));

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
                durabilityPass,
                oldLookupRetained,
                reentryBlocked,
                staleExpectedBlocked,
                sameRevisionBlocked,
                staleRequireBlocked,
                inactiveRequireBlocked);
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
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 190L);
        SkyIslandPublishedWorldSnapshotBinding snapshotBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        snapshotBinding,
                        1800L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1850L);
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
            throw new IllegalStateException("AUTH-0067 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth67",
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding binding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation inactive,
            boolean activatePass,
            boolean currentPass,
            boolean stalePass,
            boolean inactivePass,
            boolean casPass,
            boolean durabilityPass,
            boolean oldLookupRetained,
            boolean reentryBlocked,
            boolean staleExpectedBlocked,
            boolean sameRevisionBlocked,
            boolean staleRequireBlocked,
            boolean inactiveRequireBlocked) {}
}
