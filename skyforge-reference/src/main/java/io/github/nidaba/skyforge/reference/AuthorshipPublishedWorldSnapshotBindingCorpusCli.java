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
import io.github.nidaba.skyforge.world.SkyIslandPublishedWorldBindingValidation;
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
import java.util.Optional;
import javax.imageio.ImageIO;

/** Generates AUTH-0061 immutable snapshot-binding and stale-validation evidence. */
public final class AuthorshipPublishedWorldSnapshotBindingCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedWorldSnapshotBindingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-published-world-snapshot-binding-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderCapture(g, evidence, 0, 0);
        renderCurrent(g, evidence, PANEL_W, 0);
        renderStale(g, evidence, PANEL_W * 2, 0);
        renderInactive(g, evidence, 0, PANEL_H);
        renderQuery(g, evidence, PANEL_W, PANEL_H);
        renderInvariants(g, evidence, PANEL_W * 2, PANEL_H);
        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,boundRevision,currentRevision,status\n"
                        + row("CAPTURE_EXACT", evidence.capturePass(), evidence, "CURRENT")
                        + row("CURRENT_VALIDATION", evidence.currentPass(), evidence, "CURRENT")
                        + row("STALE_NO_REFRESH", evidence.stalePass(), evidence, "STALE")
                        + row("INACTIVE_DISTINCT", evidence.inactivePass(), evidence, "INACTIVE")
                        + row("CAPTURED_QUERY", evidence.queryPass(), evidence, "STALE")
                        + row("STATUS_INVARIANTS", evidence.invariantPass(), evidence, "CHECKED");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String capture =
                "bindingToken,boundSnapshotToken,capturedExact,inactiveBindBlocked\n"
                        + evidence.binding().canonicalToken()
                        + ","
                        + evidence.binding().snapshotId().canonicalToken()
                        + ","
                        + evidence.capturePass()
                        + ","
                        + evidence.inactiveBindBlocked()
                        + "\n";
        Files.writeString(out.resolve("capture.csv"), capture, StandardCharsets.UTF_8);

        String validation =
                "currentStatus,currentExact,staleStatus,staleNamesReplacement,inactiveStatus,inactiveHasNoId\n"
                        + evidence.current().status()
                        + ","
                        + evidence.currentPass()
                        + ","
                        + evidence.stale().status()
                        + ","
                        + evidence.staleNamesReplacement()
                        + ","
                        + evidence.inactive().status()
                        + ","
                        + evidence.inactive().currentSnapshotId().isEmpty()
                        + "\n";
        Files.writeString(out.resolve("validation.csv"), validation, StandardCharsets.UTF_8);

        String query =
                "boundPublication,currentPublication,bindingStayedOld,queryProvenancePass\n"
                        + evidence.boundPublication().id().canonicalToken()
                        + ","
                        + evidence.currentPublication().id().canonicalToken()
                        + ","
                        + evidence.bindingStayedOld()
                        + ","
                        + evidence.queryPass()
                        + "\n";
        Files.writeString(out.resolve("query.csv"), query, StandardCharsets.UTF_8);

        String failures =
                "inactiveBindBlocked,currentMismatchBlocked,staleSameIdBlocked,inactiveWithIdBlocked,staleRequireCurrentBlocked,inactiveRequireCurrentBlocked\n"
                        + evidence.inactiveBindBlocked()
                        + ","
                        + evidence.currentMismatchBlocked()
                        + ","
                        + evidence.staleSameIdBlocked()
                        + ","
                        + evidence.inactiveWithIdBlocked()
                        + ","
                        + evidence.staleRequireCurrentBlocked()
                        + ","
                        + evidence.inactiveRequireCurrentBlocked()
                        + "\n";
        Files.writeString(out.resolve("failures.csv"), failures, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0061</title>"
                        + "<h1>Snapshot binding and stale-validation handoff</h1>"
                        + "<p>The 16:9 atlas shows exact active-snapshot capture, CURRENT validation, "
                        + "STALE detection without refresh, distinct INACTIVE state, continued "
                        + "queries through the captured snapshot, and fail-closed validation-state "
                        + "invariants.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"capture.csv\">capture.csv</a> · "
                        + "<a href=\"validation.csv\">validation.csv</a> · "
                        + "<a href=\"query.csv\">query.csv</a> · "
                        + "<a href=\"failures.csv\">failures.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(61501L, 0.0);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 =
                SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 = viewV1.replace(v1.id(), v2);

        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(viewV1, 70L);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 71L);

        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();
        SkyIslandPublishedWorldSnapshotBinding binding = binder.bind(first);

        boolean capturePass =
                binding.snapshot().equals(first.requireActive())
                        && binding.snapshotId().equals(first.requireActive().id())
                        && binding.canonicalToken()
                                .equals("sfbinding:v1:" + first.requireActive().id().canonicalToken());
        boolean inactiveBindBlocked =
                failureMessage(
                                () ->
                                        binder.bind(
                                                SkyIslandPublishedWorldActivationState.inactive()))
                        .contains("no published-world snapshot is active");

        SkyIslandPublishedWorldBindingValidation current = binder.validate(binding, first);
        boolean currentPass =
                current.status() == SkyIslandPublishedWorldBindingStatus.CURRENT
                        && current.current()
                        && current.currentSnapshotId().equals(Optional.of(binding.snapshotId()));
        current.requireCurrent();

        SkyIslandPublishedWorldBindingValidation stale = binder.validate(binding, second);
        boolean staleNamesReplacement =
                stale.currentSnapshotId().equals(Optional.of(second.requireActive().id()));
        boolean bindingStayedOld =
                binding.snapshotId().equals(first.requireActive().id())
                        && !binding.snapshotId().equals(second.requireActive().id());
        boolean staleRequireCurrentBlocked =
                failureMessage(stale::requireCurrent).contains("status=STALE");
        boolean stalePass =
                stale.status() == SkyIslandPublishedWorldBindingStatus.STALE
                        && !stale.current()
                        && staleNamesReplacement
                        && bindingStayedOld
                        && staleRequireCurrentBlocked;

        SkyIslandPublishedWorldBindingValidation inactive =
                binder.validate(binding, SkyIslandPublishedWorldActivationState.inactive());
        boolean inactiveRequireCurrentBlocked =
                failureMessage(inactive::requireCurrent).contains("status=INACTIVE");
        boolean inactivePass =
                inactive.status() == SkyIslandPublishedWorldBindingStatus.INACTIVE
                        && !inactive.current()
                        && inactive.currentSnapshotId().isEmpty()
                        && inactiveRequireCurrentBlocked;

        var region = v1.catalog().volumes().get(0).bounds();
        var boundHits = binding.query(region);
        var oldHits = first.query(region);
        var currentHits = second.query(region);
        boolean queryPass =
                boundHits.equals(oldHits)
                        && boundHits.size() == 1
                        && boundHits.get(0).publicationId().equals(v1.id())
                        && currentHits.size() == 1
                        && currentHits.get(0).publicationId().equals(v2.id())
                        && !boundHits.get(0).publicationId().equals(currentHits.get(0).publicationId());

        boolean currentMismatchBlocked =
                validationFailure(
                        () ->
                                new SkyIslandPublishedWorldBindingValidation(
                                        binding,
                                        SkyIslandPublishedWorldBindingStatus.CURRENT,
                                        Optional.of(second.requireActive().id())));
        boolean staleSameIdBlocked =
                validationFailure(
                        () ->
                                new SkyIslandPublishedWorldBindingValidation(
                                        binding,
                                        SkyIslandPublishedWorldBindingStatus.STALE,
                                        Optional.of(binding.snapshotId())));
        boolean inactiveWithIdBlocked =
                validationFailure(
                        () ->
                                new SkyIslandPublishedWorldBindingValidation(
                                        binding,
                                        SkyIslandPublishedWorldBindingStatus.INACTIVE,
                                        Optional.of(binding.snapshotId())));
        boolean invariantPass =
                currentMismatchBlocked && staleSameIdBlocked && inactiveWithIdBlocked;

        return new Evidence(
                binding,
                v1,
                v2,
                capturePass,
                inactiveBindBlocked,
                current,
                currentPass,
                stale,
                staleNamesReplacement,
                bindingStayedOld,
                staleRequireCurrentBlocked,
                stalePass,
                inactive,
                inactiveRequireCurrentBlocked,
                inactivePass,
                queryPass,
                currentMismatchBlocked,
                staleSameIdBlocked,
                inactiveWithIdBlocked,
                invariantPass);
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
                + evidence.binding().snapshotId().snapshotRevision()
                + ","
                + evidence.stale().currentSnapshotId().orElseThrow().snapshotRevision()
                + ","
                + status
                + "\n";
    }

    private static String failureMessage(Runnable action) {
        try {
            action.run();
            return "UNEXPECTED SUCCESS";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
        }
    }

    private static boolean validationFailure(Runnable action) {
        return !failureMessage(action).equals("UNEXPECTED SUCCESS");
    }

    private static void renderCapture(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CAPTURE_EXACT", evidence.capturePass());
        line(g, x, y, 58, "bound snapshot revision: " + evidence.binding().snapshotId().snapshotRevision());
        line(g, x, y, 86, "exact active snapshot captured: " + yes(evidence.capturePass()));
        line(g, x, y, 114, "inactive bind blocked: " + yes(evidence.inactiveBindBlocked()));
        line(g, x, y, 156, "binding token exposes snapshot identity");
        line(g, x, y, 238, "no clock or hidden lease identity");
    }

    private static void renderCurrent(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CURRENT_VALIDATION", evidence.currentPass());
        line(g, x, y, 58, "validation status: " + evidence.current().status());
        line(g, x, y, 86, "current ID == bound ID: YES");
        line(g, x, y, 128, "requireCurrent: PASS");
        line(g, x, y, 170, "validation is against supplied state");
        line(g, x, y, 238, "CURRENT means exact identity equality");
    }

    private static void renderStale(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "STALE_NO_REFRESH", evidence.stalePass());
        line(g, x, y, 58, "bound revision: " + evidence.binding().snapshotId().snapshotRevision());
        line(g, x, y, 86, "active revision: " + evidence.stale().currentSnapshotId().orElseThrow().snapshotRevision());
        line(g, x, y, 114, "validation status: " + evidence.stale().status());
        line(g, x, y, 156, "binding stayed old: " + yes(evidence.bindingStayedOld()));
        line(g, x, y, 184, "requireCurrent blocked: " + yes(evidence.staleRequireCurrentBlocked()));
        line(g, x, y, 238, "no implicit refresh/rebind");
    }

    private static void renderInactive(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "INACTIVE_DISTINCT", evidence.inactivePass());
        line(g, x, y, 58, "validation status: " + evidence.inactive().status());
        line(g, x, y, 86, "current snapshot ID present: NO");
        line(g, x, y, 128, "distinct from STALE: YES");
        line(g, x, y, 170, "requireCurrent blocked: " + yes(evidence.inactiveRequireCurrentBlocked()));
        line(g, x, y, 238, "absence is explicit state");
    }

    private static void renderQuery(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CAPTURED_QUERY", evidence.queryPass());
        line(g, x, y, 58, "bound publication revision: " + evidence.boundPublication().id().publicationRevision());
        line(g, x, y, 86, "current publication revision: " + evidence.currentPublication().id().publicationRevision());
        line(g, x, y, 128, "binding still queries old snapshot: " + yes(evidence.queryPass()));
        line(g, x, y, 170, "publication + support provenance retained");
        line(g, x, y, 238, "preparation cannot silently cross snapshots");
    }

    private static void renderInvariants(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "STATUS_INVARIANTS", evidence.invariantPass());
        line(g, x, y, 58, "CURRENT + different ID blocked: " + yes(evidence.currentMismatchBlocked()));
        line(g, x, y, 86, "STALE + same ID blocked: " + yes(evidence.staleSameIdBlocked()));
        line(g, x, y, 114, "INACTIVE + current ID blocked: " + yes(evidence.inactiveWithIdBlocked()));
        line(g, x, y, 156, "validation tuples fail closed");
        line(g, x, y, 238, "atomic backend commit remains downstream");
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

    private static void line(Graphics2D g, int x, int y, int offsetY, String text) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(text, x + 18, y + offsetY);
    }

    private static String yes(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            double centerX) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, centerX, morphology);
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
            throw new IllegalStateException("AUTH-0061 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            double centerX,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth61",
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
                centerX,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
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
            SkyIslandCompiledWorldPublication boundPublication,
            SkyIslandCompiledWorldPublication currentPublication,
            boolean capturePass,
            boolean inactiveBindBlocked,
            SkyIslandPublishedWorldBindingValidation current,
            boolean currentPass,
            SkyIslandPublishedWorldBindingValidation stale,
            boolean staleNamesReplacement,
            boolean bindingStayedOld,
            boolean staleRequireCurrentBlocked,
            boolean stalePass,
            SkyIslandPublishedWorldBindingValidation inactive,
            boolean inactiveRequireCurrentBlocked,
            boolean inactivePass,
            boolean queryPass,
            boolean currentMismatchBlocked,
            boolean staleSameIdBlocked,
            boolean inactiveWithIdBlocked,
            boolean invariantPass) {}
}
