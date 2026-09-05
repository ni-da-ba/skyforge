package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
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
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;

/** Generates AUTH-0056 one-shot convergence evidence. */
public final class AuthorshipOneShotConvergenceCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int PANEL_W = WIDTH / COLS;
    private static final int PANEL_H = HEIGHT / ROWS;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipOneShotConvergenceCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-one-shot-convergence-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderOutcome(g, "ACCEPTED_ONE_PASS", evidence.accepted(), 0, 0);
        renderOutcome(g, "PLANNER_REJECTED", evidence.plannerRejected(), PANEL_W, 0);
        renderOutcome(
                g,
                "FRESH_SYNTHESIS_INCOMPLETE",
                evidence.freshIncomplete(),
                PANEL_W * 2,
                0);
        renderOutcome(
                g,
                "FRESH_RESERVATION_REJECTED",
                evidence.freshReservationRejected(),
                0,
                PANEL_H);
        renderMatrix(g, evidence, PANEL_W, PANEL_H);
        renderAttempts(
                g,
                evidence.repeatFirst(),
                evidence.repeatSecond(),
                PANEL_W * 2,
                PANEL_H);
        g.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,outcome,plannerAttempts,plannerFailure,freshPlan,"
                                + "freshSynthesis,freshPreflight,freshFullySynthesized,"
                                + "freshUncertifiedMembers,undersizedHorizontal,undersizedVertical,"
                                + "undersizedGroup,accepted\n");
        append(manifest, "ACCEPTED_ONE_PASS", evidence.accepted());
        append(manifest, "PLANNER_REJECTED", evidence.plannerRejected());
        append(manifest, "FRESH_SYNTHESIS_INCOMPLETE", evidence.freshIncomplete());
        append(
                manifest,
                "FRESH_RESERVATION_REJECTED",
                evidence.freshReservationRejected());
        Files.writeString(
                out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);

        String attempts =
                "explicitCallerExecutions,reportsEqual,firstPlannerAttempts,"
                        + "secondPlannerAttempts,firstOutcome,secondOutcome\n"
                        + "2,"
                        + evidence.repeatFirst().equals(evidence.repeatSecond())
                        + ","
                        + evidence.repeatFirst().plannerAttemptCount()
                        + ","
                        + evidence.repeatSecond().plannerAttemptCount()
                        + ","
                        + evidence.repeatFirst().outcome()
                        + ","
                        + evidence.repeatSecond().outcome()
                        + "\n";
        Files.writeString(
                out.resolve("attempts.csv"), attempts, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0056</title>"
                        + "<h1>One-shot candidate execution and proof convergence</h1>"
                        + "<p>The 16:9 atlas shows all four terminal outcomes, stage-artifact "
                        + "presence, and the one-shot invariant. One executeOnce call contains one "
                        + "candidate planner invocation. Two explicit caller invocations produce two "
                        + "separate one-attempt reports; there is no internal retry loop.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"attempts.csv\">attempts.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandMorphologyProviderRegistry builtIns =
                SkyIslandMorphologyProviders.builtInRegistry();

        SkyIslandSupportConvergenceReport accepted =
                execute(
                        singleRequest(
                                56501L,
                                ProviderMorphologySpec.full(
                                        SkyIslandMorphologyProviders.builtInId(
                                                MorphologyFamily.MASSIF)),
                                360.0,
                                440.0),
                        builtIns,
                        SkyIslandSupportReplanMargin.ZERO);

        SkyIslandSupportConvergenceReport plannerRejected =
                execute(
                        twoRequest(
                                56502L,
                                ProviderMorphologySpec.full(
                                        SkyIslandMorphologyProviders.builtInId(
                                                MorphologyFamily.MASSIF)),
                                120.0,
                                280.0,
                                260.0),
                        builtIns,
                        SkyIslandSupportReplanMargin.ZERO);

        MorphologyProviderId incompleteId =
                new MorphologyProviderId("reference", "auth56-fresh-uncertified");
        SkyIslandMorphologyProviderRegistry incompleteRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(positionSensitiveProvider(incompleteId, true))
                        .build();
        SkyIslandSupportConvergenceReport freshIncomplete =
                execute(
                        twoRequest(
                                56503L,
                                new ProviderMorphologySpec(incompleteId, 0.0, 0.0),
                                120.0,
                                350.0,
                                260.0),
                        incompleteRegistry,
                        new SkyIslandSupportReplanMargin(0.0, 100.0, 0.0, 0.0));

        MorphologyProviderId largerId =
                new MorphologyProviderId("reference", "auth56-fresh-larger");
        SkyIslandMorphologyProviderRegistry largerRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(positionSensitiveProvider(largerId, false))
                        .build();
        SkyIslandSupportConvergenceReport freshReservationRejected =
                execute(
                        twoRequest(
                                56504L,
                                new ProviderMorphologySpec(largerId, 0.0, 0.0),
                                120.0,
                                350.0,
                                260.0),
                        largerRegistry,
                        new SkyIslandSupportReplanMargin(0.0, 100.0, 0.0, 0.0));

        SkyIslandArchipelagoRequest repeatRequest =
                singleRequest(
                        56505L,
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(
                                        MorphologyFamily.BASIN)),
                        360.0,
                        440.0);
        SkyIslandSupportReplanProposal repeatProposal =
                proposal(
                        repeatRequest,
                        builtIns,
                        SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceExecutor executor =
                new SkyIslandSupportConvergenceExecutor();
        SkyIslandSupportConvergenceReport repeatFirst =
                executor.executeOnce(repeatProposal, builtIns);
        SkyIslandSupportConvergenceReport repeatSecond =
                executor.executeOnce(repeatProposal, builtIns);

        return new Evidence(
                accepted,
                plannerRejected,
                freshIncomplete,
                freshReservationRejected,
                repeatFirst,
                repeatSecond);
    }

    private static SkyIslandSupportConvergenceReport execute(
            SkyIslandArchipelagoRequest request,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandSupportReplanMargin margin) {
        return new SkyIslandSupportConvergenceExecutor()
                .executeOnce(proposal(request, registry, margin), registry);
    }

    private static SkyIslandSupportReplanProposal proposal(
            SkyIslandArchipelagoRequest request,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandSupportReplanMargin margin) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        return new SkyIslandSupportReplanProposalBuilder()
                .propose(request, plan, synthesis, ADEQUATE_VERTICAL, margin);
    }

    private static void renderOutcome(
            Graphics2D g,
            String title,
            SkyIslandSupportConvergenceReport report,
            int x,
            int y) {
        Color fill =
                switch (report.outcome()) {
                    case ACCEPTED_ONE_PASS -> new Color(219, 239, 221);
                    case PLANNER_REJECTED -> new Color(244, 215, 215);
                    case FRESH_SYNTHESIS_INCOMPLETE -> new Color(247, 235, 195);
                    case FRESH_RESERVATION_REJECTED -> new Color(244, 224, 214);
                };
        panel(g, x, y, title, fill);
        text(g, x + 18, y + 48, "planner attempts = " + report.plannerAttemptCount(), 10);
        drawStage(g, x + 28, y + 88, "PLAN", report.freshPlan().isPresent());
        drawStage(g, x + 28, y + 138, "AUTH-0054", report.freshSynthesis().isPresent());
        drawStage(g, x + 28, y + 188, "AUTH-0053", report.freshPreflight().isPresent());

        int tx = x + 170;
        if (report.plannerFailure().isPresent()) {
            text(g, tx, y + 100, "planner rejected", 10);
            text(
                    g,
                    tx,
                    y + 118,
                    simpleType(report.plannerFailure().orElseThrow().exceptionType()),
                    9);
        }
        if (report.freshSynthesis().isPresent()) {
            var synthesis = report.freshSynthesis().orElseThrow();
            text(
                    g,
                    tx,
                    y + 150,
                    "fresh complete=" + synthesis.fullySynthesized(),
                    10);
            text(
                    g,
                    tx,
                    y + 168,
                    "uncertified=" + synthesis.uncertifiedMemberCount(),
                    9);
        }
        if (report.freshPreflight().isPresent()) {
            var preflight = report.freshPreflight().orElseThrow();
            text(g, tx, y + 200, "fresh admitted=" + preflight.admitted(), 10);
            text(
                    g,
                    tx,
                    y + 218,
                    "H/V/G defects="
                            + preflight.undersizedMemberHorizontalCount()
                            + "/"
                            + preflight.undersizedVerticalCount()
                            + "/"
                            + preflight.undersizedGroupCount(),
                    9);
        }
        text(
                g,
                x + 18,
                y + 292,
                report.accepted() ? "terminal: eligible for proof-backed compile handoff"
                        : "terminal: no automatic retry",
                10);
    }

    private static void renderMatrix(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "STAGE_ARTIFACT_MATRIX", new Color(229, 232, 242));
        text(g, x + 18, y + 48, "terminal outcome -> retained fresh artifacts", 10);

        String[] names = {"planner rejected", "fresh incomplete", "reservation rejected", "accepted"};
        SkyIslandSupportConvergenceReport[] reports = {
            evidence.plannerRejected(),
            evidence.freshIncomplete(),
            evidence.freshReservationRejected(),
            evidence.accepted()
        };
        int rowY = y + 86;
        for (int i = 0; i < reports.length; i++) {
            SkyIslandSupportConvergenceReport report = reports[i];
            text(g, x + 18, rowY, names[i], 9);
            matrixCell(g, x + 178, rowY - 13, report.freshPlan().isPresent(), "PLAN");
            matrixCell(g, x + 244, rowY - 13, report.freshSynthesis().isPresent(), "SYN");
            matrixCell(g, x + 310, rowY - 13, report.freshPreflight().isPresent(), "PREF");
            rowY += 54;
        }
        text(g, x + 18, y + 316, "absence is outcome evidence, not a missing-file defect", 9);
    }

    private static void renderAttempts(
            Graphics2D g,
            SkyIslandSupportConvergenceReport first,
            SkyIslandSupportConvergenceReport second,
            int x,
            int y) {
        panel(g, x, y, "ONE_SHOT_NO_RETRY", new Color(229, 232, 242));
        text(g, x + 18, y + 50, "executeOnce call #1", 10);
        arrow(g, x + 38, y + 84, x + 350, y + 84);
        text(g, x + 115, y + 78, "1 planner invocation", 9);
        text(g, x + 18, y + 122, "report #1: " + first.outcome(), 9);

        text(g, x + 18, y + 176, "explicit caller action", 10);
        text(g, x + 18, y + 218, "executeOnce call #2", 10);
        arrow(g, x + 38, y + 252, x + 350, y + 252);
        text(g, x + 115, y + 246, "1 planner invocation", 9);
        text(g, x + 18, y + 290, "report #2: " + second.outcome(), 9);
        text(
                g,
                x + 18,
                y + 320,
                "reports equal=" + first.equals(second) + "  internal retries=0",
                10);
    }

    private static void drawStage(Graphics2D g, int x, int y, String label, boolean present) {
        g.setColor(present ? new Color(80, 145, 90) : new Color(185, 185, 185));
        g.fillRoundRect(x, y, 112, 28, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString(label + (present ? " ✓" : " —"), x + 12, y + 18);
    }

    private static void matrixCell(
            Graphics2D g, int x, int y, boolean present, String label) {
        g.setColor(present ? new Color(80, 145, 90) : new Color(205, 205, 205));
        g.fillRoundRect(x, y, 54, 24, 6, 6);
        g.setColor(present ? Color.WHITE : Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g.drawString(label, x + 11, y + 16);
    }

    private static void arrow(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setColor(new Color(85, 85, 85));
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x2 - 8, y2 - 5);
        g.drawLine(x2, y2, x2 - 8, y2 + 5);
    }

    private static void panel(Graphics2D g, int x, int y, String title, Color fill) {
        g.setColor(fill);
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 27);
    }

    private static void text(Graphics2D g, int x, int y, String value, int size) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }

    private static void append(
            StringBuilder manifest,
            String scenario,
            SkyIslandSupportConvergenceReport report) {
        String freshComplete =
                report.freshSynthesis().isPresent()
                        ? Boolean.toString(report.freshSynthesis().orElseThrow().fullySynthesized())
                        : "";
        String uncertified =
                report.freshSynthesis().isPresent()
                        ? Long.toString(report.freshSynthesis().orElseThrow().uncertifiedMemberCount())
                        : "";
        String horizontal =
                report.freshPreflight().isPresent()
                        ? Long.toString(
                                report.freshPreflight().orElseThrow()
                                        .undersizedMemberHorizontalCount())
                        : "";
        String vertical =
                report.freshPreflight().isPresent()
                        ? Long.toString(
                                report.freshPreflight().orElseThrow().undersizedVerticalCount())
                        : "";
        String group =
                report.freshPreflight().isPresent()
                        ? Long.toString(
                                report.freshPreflight().orElseThrow().undersizedGroupCount())
                        : "";

        manifest.append(scenario).append(',')
                .append(report.outcome()).append(',')
                .append(report.plannerAttemptCount()).append(',')
                .append(report.plannerFailure().isPresent()).append(',')
                .append(report.freshPlan().isPresent()).append(',')
                .append(report.freshSynthesis().isPresent()).append(',')
                .append(report.freshPreflight().isPresent()).append(',')
                .append(freshComplete).append(',')
                .append(uncertified).append(',')
                .append(horizontal).append(',')
                .append(vertical).append(',')
                .append(group).append(',')
                .append(report.accepted()).append('\n');
    }

    private static String simpleType(String type) {
        int index = type.lastIndexOf('.');
        return index >= 0 ? type.substring(index + 1) : type;
    }

    private static SkyIslandMorphologyProvider positionSensitiveProvider(
            MorphologyProviderId id, boolean becomeUncertified) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                throw new AssertionError(
                        "AUTH-0056 evidence must not compile primary morphology");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                double radial = Math.hypot(descriptor.centerX(), descriptor.centerZ());
                if (radial < 180.0) {
                    return Optional.of(
                            new PrimaryMorphologySupportEnvelope(200.0, 50.0, 50.0));
                }
                if (becomeUncertified) {
                    return Optional.empty();
                }
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(500.0, 50.0, 50.0));
            }
        };
    }

    private static SkyIslandArchipelagoRequest singleRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius) {
        return request(
                rootSeed,
                List.of(morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                0.0);
    }

    private static SkyIslandArchipelagoRequest twoRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius,
            double spacing) {
        return request(
                rootSeed,
                List.of(morphology, morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(spacing, 0.0, 0.0, 0.0),
                20.0);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            List<SkyIslandMorphologySpec> morphologies,
            double horizontal,
            double groupRadius,
            SkyIslandGroupLayout layout,
            double minimumGap) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth56",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        horizontal,
                        minimumGap,
                        0.0,
                        morphologies,
                        layout,
                        groupRadius);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
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
            SkyIslandSupportConvergenceReport accepted,
            SkyIslandSupportConvergenceReport plannerRejected,
            SkyIslandSupportConvergenceReport freshIncomplete,
            SkyIslandSupportConvergenceReport freshReservationRejected,
            SkyIslandSupportConvergenceReport repeatFirst,
            SkyIslandSupportConvergenceReport repeatSecond) {}
}
