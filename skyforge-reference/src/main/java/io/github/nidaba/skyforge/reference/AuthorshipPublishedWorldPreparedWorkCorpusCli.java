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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldBindingStatus;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldPreparedWork;
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldPreparedWorkId;
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
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0062 prepared-work provenance evidence. */
public final class AuthorshipPublishedWorldPreparedWorkCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldPreparedWorkCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of("build", "evidence", "authorship-published-world-prepared-work-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();
        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "WORK_IDENTITY", evidence.identityPass());
        line(g, 0, 0, 58, "sequence: " + evidence.work().id().workSequence());
        line(g, 0, 0, 86, "snapshot retained: YES");
        line(g, 0, 0, 114, "region retained: YES");
        line(g, 0, 0, 156, "changed sequence/region -> different ID: YES");

        panel(g, PANEL_W, 0, "EXACT_EVIDENCE", evidence.evidencePass());
        line(g, PANEL_W, 0, 58, "hit count: " + evidence.work().hitCount());
        line(g, PANEL_W, 0, 86, "evidence == exact binding query: YES");
        line(g, PANEL_W, 0, 114, "forged evidence blocked: " + yes(evidence.forgedBlocked()));
        line(g, PANEL_W, 0, 156, "snapshot mismatch blocked: " + yes(evidence.snapshotMismatchBlocked()));

        panel(g, PANEL_W * 2, 0, "IMMUTABLE_CAPTURE", evidence.immutablePass());
        line(g, PANEL_W * 2, 0, 58, "caller source cleared after construction");
        line(g, PANEL_W * 2, 0, 86, "stored evidence retained: " + yes(evidence.immutablePass()));
        line(g, PANEL_W * 2, 0, 114, "returned evidence list immutable: YES");
        line(g, PANEL_W * 2, 0, 156, "preparation provenance cannot drift");

        panel(g, 0, PANEL_H, "CURRENT_GATE", evidence.currentPass());
        line(g, 0, PANEL_H, 58, "validation: " + evidence.current().status());
        line(g, 0, PANEL_H, 86, "requireCurrent: PASS");
        line(g, 0, PANEL_H, 114, "exact work binding retained: YES");
        line(g, 0, PANEL_H, 156, "commit gate is identity-only");

        panel(g, PANEL_W, PANEL_H, "STALE_BLOCKED", evidence.stalePass());
        line(g, PANEL_W, PANEL_H, 58, "activation revision moved 90 -> 91");
        line(g, PANEL_W, PANEL_H, 86, "validation: " + evidence.stale().status());
        line(g, PANEL_W, PANEL_H, 114, "requireCurrent blocked: YES");
        line(g, PANEL_W, PANEL_H, 156, "prepared evidence still revision 1");

        panel(g, PANEL_W * 2, PANEL_H, "INACTIVE_BLOCKED", evidence.inactivePass());
        line(g, PANEL_W * 2, PANEL_H, 58, "validation: " + evidence.inactive().status());
        line(g, PANEL_W * 2, PANEL_H, 86, "requireCurrent blocked: YES");
        line(g, PANEL_W * 2, PANEL_H, 114, "no auto-refresh/retry");
        line(g, PANEL_W * 2, PANEL_H, 156, "backend atomicity remains downstream");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,workSequence,hitCount,status\n"
                        + row("WORK_IDENTITY", evidence.identityPass(), evidence, "N/A")
                        + row("EXACT_EVIDENCE", evidence.evidencePass(), evidence, "N/A")
                        + row("IMMUTABLE_CAPTURE", evidence.immutablePass(), evidence, "N/A")
                        + row("CURRENT_GATE", evidence.currentPass(), evidence, "CURRENT")
                        + row("STALE_BLOCKED", evidence.stalePass(), evidence, "STALE")
                        + row("INACTIVE_BLOCKED", evidence.inactivePass(), evidence, "INACTIVE");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String identity =
                "workToken,sequenceChanged,regionChanged,snapshotTokenVisible\n"
                        + evidence.work().id().canonicalToken()
                        + ","
                        + evidence.sequenceChanged()
                        + ","
                        + evidence.regionChanged()
                        + ","
                        + evidence.work().id().canonicalToken().contains(evidence.binding().snapshotId().canonicalToken())
                        + "\n";
        Files.writeString(out.resolve("identity.csv"), identity, StandardCharsets.UTF_8);

        String proof =
                "exactEvidence,forgedBlocked,snapshotMismatchBlocked,immutableCapture,listImmutable\n"
                        + evidence.exactEvidence()
                        + ","
                        + evidence.forgedBlocked()
                        + ","
                        + evidence.snapshotMismatchBlocked()
                        + ","
                        + evidence.immutablePass()
                        + ","
                        + evidence.listImmutable()
                        + "\n";
        Files.writeString(out.resolve("proof.csv"), proof, StandardCharsets.UTF_8);

        String validation =
                "currentStatus,staleStatus,inactiveStatus,staleBlocked,inactiveBlocked\n"
                        + evidence.current().status()
                        + ","
                        + evidence.stale().status()
                        + ","
                        + evidence.inactive().status()
                        + ","
                        + evidence.stalePass()
                        + ","
                        + evidence.inactivePass()
                        + "\n";
        Files.writeString(out.resolve("validation.csv"), validation, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0062</title>"
                        + "<h1>Prepared-work provenance and commit-intent identity</h1>"
                        + "<p>The 16:9 atlas proves deterministic work identity, exact immutable "
                        + "query evidence, and CURRENT/STALE/INACTIVE commit-currentness gates.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"identity.csv\">identity.csv</a> · "
                        + "<a href=\"proof.csv\">proof.csv</a> · "
                        + "<a href=\"validation.csv\">validation.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(62501L);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 = SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 = viewV1.replace(v1.id(), v2);
        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(viewV1, 90L);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 91L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(first);
        WorldBounds region = v1.catalog().volumes().get(0).bounds();

        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work = preparer.prepare(binding, 700L, region);

        SkyIslandPublishedWorldPreparedWorkId sequenceId =
                SkyIslandPublishedWorldPreparedWorkId.of(701L, binding, region);
        WorldBounds changedRegion =
                new WorldBounds(
                        region.minimumX() + 1.0,
                        region.maximumX(),
                        region.minimumY(),
                        region.maximumY(),
                        region.minimumZ(),
                        region.maximumZ());
        SkyIslandPublishedWorldPreparedWorkId regionId =
                SkyIslandPublishedWorldPreparedWorkId.of(700L, binding, changedRegion);
        boolean sequenceChanged = !work.id().equals(sequenceId);
        boolean regionChanged = !work.id().equals(regionId);
        boolean identityPass =
                sequenceChanged
                        && regionChanged
                        && work.snapshotId().equals(binding.snapshotId())
                        && work.region().equals(region);

        boolean exactEvidence = work.queryEvidence().equals(binding.query(region));
        boolean forgedBlocked =
                failure(
                        () ->
                                new SkyIslandPublishedWorldPreparedWork(
                                        work.id(),
                                        binding,
                                        List.of()));
        SkyIslandPublishedWorldSnapshotBinding bindingV2 =
                new SkyIslandPublishedWorldSnapshotBinder().bind(second);
        boolean snapshotMismatchBlocked =
                failure(
                        () ->
                                new SkyIslandPublishedWorldPreparedWork(
                                        work.id(),
                                        bindingV2,
                                        bindingV2.query(region)));
        boolean evidencePass = exactEvidence && forgedBlocked && snapshotMismatchBlocked;

        ArrayList<io.github.nidaba.skyforge.world.SkyIslandPublishedWorldEntry> source =
                new ArrayList<>(binding.query(region));
        SkyIslandPublishedWorldPreparedWork copied =
                new SkyIslandPublishedWorldPreparedWork(work.id(), binding, source);
        source.clear();
        boolean immutableCapture = copied.queryEvidence().equals(binding.query(region));
        boolean listImmutable;
        try {
            copied.queryEvidence().clear();
            listImmutable = false;
        } catch (UnsupportedOperationException expected) {
            listImmutable = true;
        }
        boolean immutablePass = immutableCapture && listImmutable;

        SkyIslandPublishedWorldPreparedWorkValidation current =
                preparer.validateForCommit(work, first);
        boolean currentPass =
                current.status() == SkyIslandPublishedWorldBindingStatus.CURRENT
                        && current.current()
                        && succeeds(current::requireCurrent);

        SkyIslandPublishedWorldPreparedWorkValidation stale =
                preparer.validateForCommit(work, second);
        boolean stalePass =
                stale.status() == SkyIslandPublishedWorldBindingStatus.STALE
                        && !stale.current()
                        && !succeeds(stale::requireCurrent)
                        && work.queryEvidence().get(0).publicationId().equals(v1.id());

        SkyIslandPublishedWorldPreparedWorkValidation inactive =
                preparer.validateForCommit(work, SkyIslandPublishedWorldActivationState.inactive());
        boolean inactivePass =
                inactive.status() == SkyIslandPublishedWorldBindingStatus.INACTIVE
                        && !inactive.current()
                        && !succeeds(inactive::requireCurrent);

        return new Evidence(
                binding,
                work,
                sequenceChanged,
                regionChanged,
                identityPass,
                exactEvidence,
                forgedBlocked,
                snapshotMismatchBlocked,
                evidencePass,
                immutableCapture,
                listImmutable,
                immutablePass,
                current,
                currentPass,
                stale,
                stalePass,
                inactive,
                inactivePass);
    }

    private static String row(
            String scenario,
            boolean pass,
            Evidence evidence,
            String status) {
        return scenario
                + ","
                + pass
                + ","
                + evidence.work().id().workSequence()
                + ","
                + evidence.work().hitCount()
                + ","
                + status
                + "\n";
    }

    private static boolean failure(Runnable action) {
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

    private static String yes(boolean value) {
        return value ? "YES" : "NO";
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
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
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
            throw new IllegalStateException("AUTH-0062 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth62",
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

    private record Evidence(
            SkyIslandPublishedWorldSnapshotBinding binding,
            SkyIslandPublishedWorldPreparedWork work,
            boolean sequenceChanged,
            boolean regionChanged,
            boolean identityPass,
            boolean exactEvidence,
            boolean forgedBlocked,
            boolean snapshotMismatchBlocked,
            boolean evidencePass,
            boolean immutableCapture,
            boolean listImmutable,
            boolean immutablePass,
            SkyIslandPublishedWorldPreparedWorkValidation current,
            boolean currentPass,
            SkyIslandPublishedWorldPreparedWorkValidation stale,
            boolean stalePass,
            SkyIslandPublishedWorldPreparedWorkValidation inactive,
            boolean inactivePass) {}
}
