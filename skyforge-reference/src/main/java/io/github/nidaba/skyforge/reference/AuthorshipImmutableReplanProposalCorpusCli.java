package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanGroupProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanValue;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationPreflight;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates AUTH-0055 immutable re-plan proposal evidence. */
public final class AuthorshipImmutableReplanProposalCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int PANEL_W = WIDTH / COLS;
    private static final int PANEL_H = HEIGHT / ROWS;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipImmutableReplanProposalCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-immutable-replan-proposal-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);

        renderProposal(
                graphics,
                "NO_CHANGE",
                evidence.noChange(),
                0,
                0,
                "candidate request equals original");
        renderProposal(
                graphics,
                "PROOF_RAISES_HORIZONTAL",
                evidence.proofRaise(),
                PANEL_W,
                0,
                "proof raises member + pairwise spacing; candidate not executed");
        renderProposal(
                graphics,
                "AUTHOR_MARGIN",
                evidence.margin(),
                PANEL_W * 2,
                0,
                "author margin remains distinct from proof minimum");
        renderProposal(
                graphics,
                "VERTICAL_ONLY",
                evidence.verticalOnly(),
                0,
                PANEL_H,
                "archipelago request unchanged; vertical reservation raised");
        renderProposal(
                graphics,
                "INCOMPLETE_UNCERTIFIED",
                evidence.incomplete(),
                PANEL_W,
                PANEL_H,
                "missing proof => no candidate request");
        renderBoundary(
                graphics,
                evidence.boundary(),
                evidence.boundaryFreshPlan(),
                evidence.boundaryFreshSynthesis(),
                evidence.boundaryFreshAdmitted(),
                PANEL_W * 2,
                PANEL_H);
        graphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,complete,candidatePresent,freshReplanRequired,"
                                + "originalHorizontal,proofHorizontal,authorHorizontalMargin,"
                                + "proposedHorizontal,originalLayoutSpacing,proofLayoutSpacing,"
                                + "layoutAuthorMargin,proposedLayoutSpacing,originalGroupRadius,"
                                + "proofGroupRadius,groupAuthorMargin,dependentGroupFloor,"
                                + "proposedGroupRadius,originalBelow,proofBelow,belowMargin,"
                                + "proposedBelow,originalAbove,proofAbove,aboveMargin,proposedAbove\n");
        append(manifest, "NO_CHANGE", evidence.noChange());
        append(manifest, "PROOF_RAISES_HORIZONTAL", evidence.proofRaise());
        append(manifest, "AUTHOR_MARGIN", evidence.margin());
        append(manifest, "VERTICAL_ONLY", evidence.verticalOnly());
        append(manifest, "INCOMPLETE_UNCERTIFIED", evidence.incomplete());
        append(manifest, "CANDIDATE_REPLAN_BOUNDARY", evidence.boundary());
        Files.writeString(
                out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);

        SkyIslandSupportReplanGroupProposal oldGroup =
                evidence.boundary().groupProposals().get(0);
        double oldProof =
                evidence.boundary()
                        .synthesis()
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();
        double freshProof =
                evidence.boundaryFreshSynthesis()
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();
        String boundary =
                "candidateBuiltWithoutExecution,explicitFreshPlanSucceeded,"
                        + "oldExactPlanGroupProof,proposalProvisionalGroupRadius,"
                        + "freshExactPlanGroupProof,freshPreflightAdmitted,"
                        + "originalObservedSpacing,freshObservedSpacing\n"
                        + "true,true,"
                        + f(oldProof)
                        + ","
                        + f(oldGroup.provisionalGroupRadius().proposedValue())
                        + ","
                        + f(freshProof)
                        + ","
                        + evidence.boundaryFreshAdmitted()
                        + ","
                        + f(evidence.boundary().originalPlan().groups().get(0).groupPlan()
                                .minimumObservedCenterSpacing())
                        + ","
                        + f(evidence.boundaryFreshPlan().groups().get(0).groupPlan()
                                .minimumObservedCenterSpacing())
                        + "\n";
        Files.writeString(
                out.resolve("boundary.csv"), boundary, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0055</title>"
                        + "<h1>Immutable proof-aware re-plan proposal</h1>"
                        + "<p>The 16:9 atlas separates original reservation, analytical proof minimum, "
                        + "explicit author margin, and proposed value. Candidate requests are reviewable "
                        + "objects; proposal construction does not execute them. The final panel shows an "
                        + "explicit fresh plan followed by fresh AUTH-0054 and AUTH-0053, proving that "
                        + "the old exact-plan outer-group requirement is not inherited.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"boundary.csv\">boundary.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();

        SkyIslandArchipelagoRequest noChangeRequest =
                singleRequest(
                        55501L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandSupportReplanProposal noChange =
                proposal(
                        noChangeRequest,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);

        SkyIslandArchipelagoRequest proofRequest =
                twoRequest(
                        55502L,
                        direct(MorphologyFamily.MASSIF),
                        120.0,
                        280.0,
                        260.0);
        SkyIslandSupportReplanProposal proofRaise =
                proposal(
                        proofRequest,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);

        SkyIslandArchipelagoRequest marginRequest =
                singleRequest(
                        55503L,
                        direct(MorphologyFamily.TABLELAND),
                        360.0,
                        440.0);
        SkyIslandSupportReplanProposal margin =
                proposal(
                        marginRequest,
                        ADEQUATE_VERTICAL,
                        new SkyIslandSupportReplanMargin(10.0, 20.0, 5.0, 7.0));

        SkyIslandArchipelagoRequest verticalRequest =
                singleRequest(
                        55504L,
                        direct(MorphologyFamily.SPINE),
                        360.0,
                        440.0);
        SkyIslandSupportReplanProposal verticalOnly =
                proposal(
                        verticalRequest,
                        new SkyIslandWorldVerticalReservation(180.0, 140.0),
                        SkyIslandSupportReplanMargin.ZERO);

        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandArchipelagoRequest incompleteRequest =
                singleRequest(
                        55505L,
                        ProviderBlendMorphologySpec.full(
                                new MorphologyProviderBlend(massif, basin, 0.35)),
                        360.0,
                        440.0);
        SkyIslandSupportReplanProposal incomplete =
                proposal(
                        incompleteRequest,
                        ADEQUATE_VERTICAL,
                        new SkyIslandSupportReplanMargin(5.0, 5.0, 5.0, 5.0));

        SkyIslandArchipelagoRequest boundaryRequest =
                twoRequest(
                        55506L,
                        direct(MorphologyFamily.MASSIF),
                        120.0,
                        280.0,
                        260.0);
        SkyIslandSupportReplanProposal boundary =
                proposal(
                        boundaryRequest,
                        ADEQUATE_VERTICAL,
                        new SkyIslandSupportReplanMargin(1.0, 400.0, 0.0, 0.0));

        SkyIslandArchipelagoPlan freshPlan =
                new SkyIslandArchipelagoPlanner()
                        .plan(boundary.candidateRequest().orElseThrow());
        SkyIslandSupportReservationRequirementSynthesis freshSynthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(freshPlan, registry);
        boolean freshAdmitted =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                freshPlan,
                                registry,
                                boundary.candidateVerticalReservation().orElseThrow())
                        .admitted();

        return new Evidence(
                noChange,
                proofRaise,
                margin,
                verticalOnly,
                incomplete,
                boundary,
                freshPlan,
                freshSynthesis,
                freshAdmitted);
    }

    private static SkyIslandSupportReplanProposal proposal(
            SkyIslandArchipelagoRequest request,
            SkyIslandWorldVerticalReservation vertical,
            SkyIslandSupportReplanMargin margin) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        return new SkyIslandSupportReplanProposalBuilder()
                .propose(request, plan, synthesis, vertical, margin);
    }

    private static void renderProposal(
            Graphics2D g,
            String title,
            SkyIslandSupportReplanProposal proposal,
            int x,
            int y,
            String note) {
        Color fill =
                !proposal.complete()
                        ? new Color(247, 235, 195)
                        : proposal.freshReplanRequired()
                                ? new Color(244, 224, 214)
                                : new Color(219, 239, 221);
        panel(g, x, y, title, fill);
        text(g, x + 18, y + 48, note, 9);
        text(
                g,
                x + 18,
                y + 64,
                "complete=" + proposal.complete()
                        + "  candidate=" + proposal.candidateRequest().isPresent()
                        + "  freshReplan=" + proposal.freshReplanRequired(),
                9);

        SkyIslandSupportReplanGroupProposal group = proposal.groupProposals().get(0);
        int bx = x + 28;
        int by = y + 104;
        int bw = PANEL_W - 58;
        drawValue(g, "member horizontal", group.memberHorizontal(), bx, by, bw, 500.0, "?");
        drawValue(
                g,
                "layout min spacing",
                group.layoutMinimumCenterSpacing(),
                bx,
                by + 54,
                bw,
                900.0,
                group.originalTemplate().memberMorphologies().size() == 1 ? "n/a" : "?");
        drawValue(
                g,
                "provisional group radius",
                group.provisionalGroupRadius(),
                bx,
                by + 108,
                bw,
                1000.0,
                "?");
        drawValue(g, "below suspension", proposal.belowSuspension(), bx, by + 162, bw, 300.0, "?");
        drawValue(g, "above suspension", proposal.aboveSuspension(), bx, by + 216, bw, 220.0, "?");
    }

    private static void renderBoundary(
            Graphics2D g,
            SkyIslandSupportReplanProposal proposal,
            SkyIslandArchipelagoPlan freshPlan,
            SkyIslandSupportReservationRequirementSynthesis freshSynthesis,
            boolean freshAdmitted,
            int x,
            int y) {
        panel(g, x, y, "CANDIDATE_REPLAN_BOUNDARY", new Color(225, 232, 244));
        text(g, x + 18, y + 48, "proposal construction: candidate built, not executed", 9);
        text(g, x + 18, y + 66, "explicit downstream plan: success", 9);
        text(g, x + 18, y + 84, "fresh AUTH-0054 + AUTH-0053: admitted=" + freshAdmitted, 9);

        double oldProof =
                proposal.synthesis().groupRequirements().get(0)
                        .exactPlanRequiredGroupRadius().orElseThrow();
        double provisional =
                proposal.groupProposals().get(0).provisionalGroupRadius().proposedValue();
        double freshProof =
                freshSynthesis.groupRequirements().get(0)
                        .exactPlanRequiredGroupRadius().orElseThrow();

        int bx = x + 34;
        int bw = PANEL_W - 70;
        drawScalar(g, "old exact-plan group proof", oldProof, bx, y + 130, bw, 1000.0, new Color(90, 90, 90));
        drawScalar(g, "proposal provisional radius", provisional, bx, y + 184, bw, 1000.0, new Color(75, 120, 165));
        drawScalar(g, "fresh exact-plan group proof", freshProof, bx, y + 238, bw, 1000.0, new Color(55, 125, 65));
        text(
                g,
                x + 18,
                y + 308,
                "spacing "
                        + shortF(proposal.originalPlan().groups().get(0).groupPlan().minimumObservedCenterSpacing())
                        + " -> "
                        + shortF(freshPlan.groups().get(0).groupPlan().minimumObservedCenterSpacing()),
                10);
    }

    private static void drawValue(
            Graphics2D g,
            String label,
            SkyIslandSupportReplanValue value,
            int x,
            int y,
            int width,
            double scaleMax,
            String absentProofLabel) {
        text(g, x, y - 5, label, 9);
        int originalX = x + scaled(value.originalValue(), width, scaleMax);
        int proofX =
                value.proofMinimum().isPresent()
                        ? x + scaled(value.proofMinimum().orElseThrow(), width, scaleMax)
                        : -1;
        int proposedX = x + scaled(value.proposedValue(), width, scaleMax);

        g.setColor(new Color(205, 205, 205));
        g.fillRect(x, y, Math.max(1, originalX - x), 14);
        g.setColor(new Color(125, 125, 125));
        g.drawRect(x, y, Math.max(1, originalX - x), 14);

        if (proofX >= 0) {
            g.setColor(new Color(170, 70, 70));
            g.fillRect(proofX - 2, y - 3, 4, 20);
        }

        g.setColor(new Color(55, 120, 70));
        g.fillRect(proposedX - 2, y - 5, 4, 24);
        text(
                g,
                x + Math.max(0, width - 190),
                y - 5,
                "O " + shortF(value.originalValue())
                        + " / P " + (value.proofMinimum().isPresent()
                                ? shortF(value.proofMinimum().orElseThrow())
                                : absentProofLabel)
                        + " / M " + shortF(value.authorMargin())
                        + " / N " + shortF(value.proposedValue()),
                8);
    }

    private static void drawScalar(
            Graphics2D g,
            String label,
            double value,
            int x,
            int y,
            int width,
            double scaleMax,
            Color color) {
        text(g, x, y - 5, label + " = " + shortF(value), 9);
        int px = x + scaled(value, width, scaleMax);
        g.setColor(new Color(215, 215, 215));
        g.fillRect(x, y, width, 12);
        g.setColor(color);
        g.fillRect(px - 2, y - 4, 4, 20);
    }

    private static int scaled(double value, int width, double scaleMax) {
        return (int) Math.round(Math.min(1.0, value / scaleMax) * width);
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
            SkyIslandSupportReplanProposal proposal) {
        SkyIslandSupportReplanGroupProposal group = proposal.groupProposals().get(0);
        appendValue(manifest, scenario, proposal, group);
    }

    private static void appendValue(
            StringBuilder manifest,
            String scenario,
            SkyIslandSupportReplanProposal proposal,
            SkyIslandSupportReplanGroupProposal group) {
        manifest.append(scenario).append(',')
                .append(proposal.complete()).append(',')
                .append(proposal.candidateRequest().isPresent()).append(',')
                .append(proposal.freshReplanRequired()).append(',');
        scalar(manifest, group.memberHorizontal());
        scalar(manifest, group.layoutMinimumCenterSpacing());
        manifest.append(f(group.provisionalGroupRadius().originalValue())).append(',')
                .append(optional(group.provisionalGroupRadius())).append(',')
                .append(f(group.provisionalGroupRadius().authorMargin())).append(',')
                .append(f(group.dependentCurrentLayoutGroupFloor())).append(',')
                .append(f(group.provisionalGroupRadius().proposedValue())).append(',');
        scalar(manifest, proposal.belowSuspension());
        scalarLast(manifest, proposal.aboveSuspension());
    }

    private static void scalar(StringBuilder manifest, SkyIslandSupportReplanValue value) {
        manifest.append(f(value.originalValue())).append(',')
                .append(optional(value)).append(',')
                .append(f(value.authorMargin())).append(',')
                .append(f(value.proposedValue())).append(',');
    }

    private static void scalarLast(StringBuilder manifest, SkyIslandSupportReplanValue value) {
        manifest.append(f(value.originalValue())).append(',')
                .append(optional(value)).append(',')
                .append(f(value.authorMargin())).append(',')
                .append(f(value.proposedValue())).append('\n');
    }

    private static String optional(SkyIslandSupportReplanValue value) {
        return value.proofMinimum().isPresent()
                ? f(value.proofMinimum().orElseThrow())
                : "";
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String shortF(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
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
                        "auth55",
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
            SkyIslandSupportReplanProposal noChange,
            SkyIslandSupportReplanProposal proofRaise,
            SkyIslandSupportReplanProposal margin,
            SkyIslandSupportReplanProposal verticalOnly,
            SkyIslandSupportReplanProposal incomplete,
            SkyIslandSupportReplanProposal boundary,
            SkyIslandArchipelagoPlan boundaryFreshPlan,
            SkyIslandSupportReservationRequirementSynthesis boundaryFreshSynthesis,
            boolean boundaryFreshAdmitted) {}
}
