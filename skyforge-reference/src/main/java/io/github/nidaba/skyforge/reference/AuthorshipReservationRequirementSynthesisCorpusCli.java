package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
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
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationGroupRequirement;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationMemberRequirement;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
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
import java.util.Optional;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;

/** Generates AUTH-0054 exact reservation-requirement synthesis evidence. */
public final class AuthorshipReservationRequirementSynthesisCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int PANEL_W = WIDTH / COLS;
    private static final int PANEL_H = HEIGHT / ROWS;

    private AuthorshipReservationRequirementSynthesisCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-reservation-requirement-synthesis-v1");
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

        renderSingle(
                graphics,
                "ADEQUATE_CURRENT",
                evidence.adequate(),
                260.0,
                160.0,
                0,
                0,
                "current reservations already satisfy synthesis");
        renderSingle(
                graphics,
                "SYNTHESIZE_HORIZONTAL_GROUP",
                evidence.undersized(),
                260.0,
                160.0,
                PANEL_W,
                0,
                "synthesis is advisory -> fresh re-plan required");
        renderSingle(
                graphics,
                "SYNTHESIZE_VERTICAL",
                evidence.vertical(),
                180.0,
                140.0,
                PANEL_W * 2,
                0,
                "global below/above minima are independent outputs");
        renderSingle(
                graphics,
                "UNCERTIFIED_INCOMPLETE",
                evidence.uncertified(),
                260.0,
                160.0,
                0,
                PANEL_H,
                "no complete reservation recommendation is invented");
        renderSeedPair(
                graphics,
                evidence.seedA(),
                evidence.seedB(),
                PANEL_W,
                PANEL_H);
        renderReplan(
                graphics,
                evidence.replanOriginal(),
                evidence.replanFresh(),
                PANEL_W * 2,
                PANEL_H);
        graphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,fullySynthesized,requiresFreshReplan,descriptorSeed,"
                                + "currentHorizontal,requiredHorizontal,currentGroup,requiredGroup,"
                                + "currentBelow,requiredBelow,currentAbove,requiredAbove\n");
        append(manifest, "ADEQUATE_CURRENT", evidence.adequate(), 260.0, 160.0);
        append(
                manifest,
                "SYNTHESIZE_HORIZONTAL_GROUP",
                evidence.undersized(),
                260.0,
                160.0);
        append(manifest, "SYNTHESIZE_VERTICAL", evidence.vertical(), 180.0, 140.0);
        append(
                manifest,
                "UNCERTIFIED_INCOMPLETE",
                evidence.uncertified(),
                260.0,
                160.0);
        append(manifest, "SEED_A_LARGE", evidence.seedA(), 100.0, 100.0);
        append(manifest, "SEED_B_SMALL", evidence.seedB(), 100.0, 100.0);
        append(manifest, "REPLAN_ORIGINAL", evidence.replanOriginal(), 260.0, 160.0);
        append(manifest, "REPLAN_FRESH", evidence.replanFresh(), 260.0, 160.0);
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);

        double originalGroup =
                evidence.replanOriginal()
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();
        double freshGroup =
                evidence.replanFresh()
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();
        String replan =
                "sameRootSeed,originalRequiredGroupRadius,freshRequiredGroupRadius,"
                        + "originalMember1X,freshMember1X,requirementsDiffer\n"
                        + "true,"
                        + f(originalGroup)
                        + ","
                        + f(freshGroup)
                        + ","
                        + f(evidence.replanOriginalPlan().groups().get(0).groupPlan().members().get(1)
                                .descriptor().centerX())
                        + ","
                        + f(evidence.replanFreshPlan().groups().get(0).groupPlan().members().get(1)
                                .descriptor().centerX())
                        + ","
                        + (Double.doubleToLongBits(originalGroup)
                                != Double.doubleToLongBits(freshGroup))
                        + "\n";
        Files.writeString(out.resolve("replan.csv"), replan, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0054</title>"
                        + "<h1>Exact reservation requirement synthesis</h1>"
                        + "<p>The 16:9 atlas compares current planning/query reservations against "
                        + "AUTH-0054 admission-safe synthesized minima. The seed-aware panel proves "
                        + "requirements are descriptor-specific. The final panel shows that an outer "
                        + "group radius synthesized for one exact placement changes after a fresh "
                        + "re-plan and therefore cannot be copied forward as a placement invariant.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"replan.csv\">replan.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        var synthesizer = new SkyIslandSupportReservationRequirementSynthesizer();
        var builtIns = SkyIslandMorphologyProviders.builtInRegistry();

        var adequate =
                synthesizer.synthesize(
                        singlePlan(
                                54501L,
                                direct(MorphologyFamily.MASSIF),
                                360.0,
                                440.0),
                        builtIns);
        var undersized =
                synthesizer.synthesize(
                        singlePlan(
                                54502L,
                                direct(MorphologyFamily.MASSIF),
                                256.0,
                                280.0),
                        builtIns);
        var vertical =
                synthesizer.synthesize(
                        singlePlan(
                                54503L,
                                direct(MorphologyFamily.SPINE),
                                360.0,
                                440.0),
                        builtIns);

        MorphologyProviderId massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderId basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        var uncertified =
                synthesizer.synthesize(
                        singlePlan(
                                54504L,
                                ProviderBlendMorphologySpec.full(
                                        new MorphologyProviderBlend(
                                                massif, basin, 0.35)),
                                360.0,
                                440.0),
                        builtIns);

        MorphologyProviderId seedId =
                new MorphologyProviderId("reference", "auth54-seed-aware");
        SkyIslandMorphologySpec seedSpec = ProviderMorphologySpec.full(seedId);
        SkyIslandArchipelagoPlan seedAPlan =
                singlePlan(54511L, seedSpec, 200.0, 340.0);
        SkyIslandArchipelagoPlan seedBPlan =
                singlePlan(54512L, seedSpec, 200.0, 340.0);
        long seedA =
                seedAPlan.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        var seedRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(seedAwareProvider(seedId, seedA))
                        .build();
        var seedASynthesis = synthesizer.synthesize(seedAPlan, seedRegistry);
        var seedBSynthesis = synthesizer.synthesize(seedBPlan, seedRegistry);

        SkyIslandArchipelagoPlan originalPlan =
                twoMemberPlan(54520L, direct(MorphologyFamily.MASSIF), 120.0, 280.0, 260.0);
        SkyIslandArchipelagoPlan freshPlan =
                twoMemberPlan(54520L, direct(MorphologyFamily.MASSIF), 360.0, 900.0, 820.0);
        var originalSynthesis = synthesizer.synthesize(originalPlan, builtIns);
        var freshSynthesis = synthesizer.synthesize(freshPlan, builtIns);

        return new Evidence(
                adequate,
                undersized,
                vertical,
                uncertified,
                seedASynthesis,
                seedBSynthesis,
                originalSynthesis,
                freshSynthesis,
                originalPlan,
                freshPlan);
    }

    private static void renderSingle(
            Graphics2D g,
            String title,
            SkyIslandSupportReservationRequirementSynthesis synthesis,
            double currentBelow,
            double currentAbove,
            int x,
            int y,
            String note) {
        SkyIslandSupportReservationMemberRequirement member =
                synthesis.memberRequirements().get(0);
        SkyIslandSupportReservationGroupRequirement group =
                synthesis.groupRequirements().get(0);
        Color fill =
                !synthesis.fullySynthesized()
                        ? new Color(247, 235, 195)
                        : synthesis.requiresFreshReplan()
                                ? new Color(244, 215, 215)
                                : new Color(219, 239, 221);
        panel(g, x, y, title, fill);
        text(g, x + 18, y + 48, note, 10);
        text(
                g,
                x + 18,
                y + 64,
                "seed=" + Long.toUnsignedString(member.descriptorSeed()),
                9);

        int bx = x + 32;
        int by = y + 102;
        int bw = PANEL_W - 64;
        drawRequirement(
                g,
                "member horizontal",
                member.currentReservedHorizontalRadius(),
                member.requiredHorizontalRadius(),
                bx,
                by,
                bw,
                400.0);
        drawRequirement(
                g,
                "group radius (exact current layout)",
                group.currentReservedGroupRadius(),
                group.exactPlanRequiredGroupRadius(),
                bx,
                by + 55,
                bw,
                500.0);
        drawRequirement(
                g,
                "below suspension",
                currentBelow,
                synthesis.requiredBelowSuspension(),
                bx,
                by + 110,
                bw,
                280.0);
        drawRequirement(
                g,
                "above suspension",
                currentAbove,
                synthesis.requiredAboveSuspension(),
                bx,
                by + 165,
                bw,
                180.0);
    }

    private static void renderSeedPair(
            Graphics2D g,
            SkyIslandSupportReservationRequirementSynthesis first,
            SkyIslandSupportReservationRequirementSynthesis second,
            int x,
            int y) {
        panel(g, x, y, "SEED_A_VS_SEED_B", new Color(229, 232, 242));
        var a = first.memberRequirements().get(0);
        var b = second.memberRequirements().get(0);
        text(g, x + 18, y + 48, "same semantic provider intent; exact derived seed changes support", 10);
        text(g, x + 18, y + 70, "A seed=" + Long.toUnsignedString(a.descriptorSeed()), 9);
        text(g, x + 18, y + 88, "B seed=" + Long.toUnsignedString(b.descriptorSeed()), 9);

        int bx = x + 34;
        int bw = PANEL_W - 70;
        drawRequirement(
                g,
                "A horizontal",
                a.currentReservedHorizontalRadius(),
                a.requiredHorizontalRadius(),
                bx,
                y + 126,
                bw,
                340.0);
        drawRequirement(
                g,
                "B horizontal",
                b.currentReservedHorizontalRadius(),
                b.requiredHorizontalRadius(),
                bx,
                y + 188,
                bw,
                340.0);
        text(
                g,
                x + 18,
                y + 270,
                "A requires fresh re-plan=" + first.requiresFreshReplan()
                        + "   B requires fresh re-plan=" + second.requiresFreshReplan(),
                10);
    }

    private static void renderReplan(
            Graphics2D g,
            SkyIslandSupportReservationRequirementSynthesis original,
            SkyIslandSupportReservationRequirementSynthesis fresh,
            int x,
            int y) {
        panel(g, x, y, "REPLAN_CHANGES_GROUP_RADIUS", new Color(229, 232, 242));
        double originalGroup =
                original.groupRequirements().get(0).exactPlanRequiredGroupRadius().orElseThrow();
        double freshGroup =
                fresh.groupRequirements().get(0).exactPlanRequiredGroupRadius().orElseThrow();
        text(
                g,
                x + 18,
                y + 48,
                "same root seed + morphology; changed spacing inputs -> different member centers",
                10);
        text(
                g,
                x + 18,
                y + 67,
                "old exact-plan outer minimum is not a proof for the fresh placement",
                10);

        int bx = x + 34;
        int bw = PANEL_W - 70;
        drawRequirement(
                g,
                "original exact-plan group radius",
                original.groupRequirements().get(0).currentReservedGroupRadius(),
                OptionalDouble.of(originalGroup),
                bx,
                y + 118,
                bw,
                800.0);
        drawRequirement(
                g,
                "fresh exact-plan group radius",
                fresh.groupRequirements().get(0).currentReservedGroupRadius(),
                OptionalDouble.of(freshGroup),
                bx,
                y + 190,
                bw,
                1000.0);
        text(
                g,
                x + 18,
                y + 280,
                "original Q=" + f(originalGroup) + "   fresh Q=" + f(freshGroup),
                10);
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

    private static void drawRequirement(
            Graphics2D g,
            String label,
            double current,
            OptionalDouble required,
            int x,
            int y,
            int width,
            double scaleMaximum) {
        text(g, x, y - 7, label, 9);
        int currentWidth =
                (int) Math.round(Math.min(1.0, current / scaleMaximum) * width);
        g.setColor(new Color(205, 205, 205));
        g.fillRect(x, y, currentWidth, 16);
        g.setColor(new Color(120, 120, 120));
        g.drawRect(x, y, currentWidth, 16);

        if (required.isPresent()) {
            double value = required.orElseThrow();
            int requirementX =
                    x + (int) Math.round(Math.min(1.0, value / scaleMaximum) * width);
            g.setColor(value <= current ? new Color(55, 125, 65) : new Color(175, 55, 55));
            g.fillRect(requirementX - 2, y - 3, 4, 22);
            text(
                    g,
                    x + Math.max(0, width - 146),
                    y - 7,
                    "current " + shortF(current) + " / min " + shortF(value),
                    9);
        } else {
            g.setColor(new Color(135, 105, 30));
            text(
                    g,
                    x + Math.max(0, width - 104),
                    y - 7,
                    "current " + shortF(current) + " / min ?",
                    9);
        }
    }

    private static void text(Graphics2D g, int x, int y, String value, int size) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }

    private static void append(
            StringBuilder manifest,
            String scenario,
            SkyIslandSupportReservationRequirementSynthesis synthesis,
            double currentBelow,
            double currentAbove) {
        var member = synthesis.memberRequirements().get(0);
        var group = synthesis.groupRequirements().get(0);
        manifest.append(scenario).append(',')
                .append(synthesis.fullySynthesized()).append(',')
                .append(synthesis.requiresFreshReplan()).append(',')
                .append(Long.toUnsignedString(member.descriptorSeed())).append(',')
                .append(f(member.currentReservedHorizontalRadius())).append(',')
                .append(value(member.requiredHorizontalRadius())).append(',')
                .append(f(group.currentReservedGroupRadius())).append(',')
                .append(value(group.exactPlanRequiredGroupRadius())).append(',')
                .append(f(currentBelow)).append(',')
                .append(value(synthesis.requiredBelowSuspension())).append(',')
                .append(f(currentAbove)).append(',')
                .append(value(synthesis.requiredAboveSuspension())).append('\n');
    }

    private static String value(OptionalDouble value) {
        return value.isPresent() ? f(value.orElseThrow()) : "";
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String shortF(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static SkyIslandMorphologyProvider seedAwareProvider(
            MorphologyProviderId id, long largeSeed) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                throw new AssertionError("AUTH-0054 evidence synthesis must not compile graphs");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(
                                descriptor.seed() == largeSeed ? 300.0 : 120.0,
                                50.0,
                                50.0));
            }
        };
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
    }

    private static SkyIslandArchipelagoPlan singlePlan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup) {
        SkyIslandVolumeDescriptor descriptor = descriptor();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth54",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontal,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                Math.max(800.0, 2.0 * reservedHorizontal),
                                0.0,
                                0.0,
                                0.0),
                        reservedGroup);
        return plan(rootSeed, template);
    }

    private static SkyIslandArchipelagoPlan twoMemberPlan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup,
            double minimumSpacing) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth54-two",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        reservedHorizontal,
                        20.0,
                        0.0,
                        List.of(morphology, morphology),
                        new SkyIslandGroupLayout.Cluster(
                                minimumSpacing, 0.0, 0.0, 0.0),
                        reservedGroup);
        return plan(rootSeed, template);
    }

    private static SkyIslandArchipelagoPlan plan(
            long rootSeed, SkyIslandGroupTemplate template) {
        return new SkyIslandArchipelagoPlanner()
                .plan(
                        new SkyIslandArchipelagoRequest(
                                rootSeed,
                                0.0,
                                0.0,
                                320.0,
                                500.0,
                                List.of(template),
                                new SkyIslandArchipelagoLayout.Hub(
                                        1_600.0, 0.0, 0.0, 0.0, 0.0)));
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
            SkyIslandSupportReservationRequirementSynthesis adequate,
            SkyIslandSupportReservationRequirementSynthesis undersized,
            SkyIslandSupportReservationRequirementSynthesis vertical,
            SkyIslandSupportReservationRequirementSynthesis uncertified,
            SkyIslandSupportReservationRequirementSynthesis seedA,
            SkyIslandSupportReservationRequirementSynthesis seedB,
            SkyIslandSupportReservationRequirementSynthesis replanOriginal,
            SkyIslandSupportReservationRequirementSynthesis replanFresh,
            SkyIslandArchipelagoPlan replanOriginalPlan,
            SkyIslandArchipelagoPlan replanFreshPlan) {}
}
