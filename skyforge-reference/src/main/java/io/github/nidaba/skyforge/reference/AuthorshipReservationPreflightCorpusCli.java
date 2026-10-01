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
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationMemberCheck;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationPreflight;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationPreflightReport;
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
import java.util.Optional;
import javax.imageio.ImageIO;

/** Generates AUTH-0053 seed-aware reservation-preflight evidence. */
public final class AuthorshipReservationPreflightCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int PANEL_W = WIDTH / COLS;
    private static final int PANEL_H = HEIGHT / ROWS;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipReservationPreflightCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-reservation-preflight-v1");
        Files.createDirectories(out);

        List<Row> rows = scenarios();
        BufferedImage atlas =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        for (int i = 0; i < rows.size(); i++) {
            renderPanel(
                    graphics,
                    rows.get(i),
                    (i % COLS) * PANEL_W,
                    (i / COLS) * PANEL_H);
        }
        graphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,admitted,certified,descriptorSeed,reservedHorizontal,"
                                + "requiredHorizontal,reservedBelow,requiredBelow,reservedAbove,"
                                + "requiredAbove,reservedGroup,requiredGroup,consumedReservationDefect\n");
        for (Row row : rows) {
            manifest.append(row.scenario()).append(',')
                    .append(row.report().admitted()).append(',')
                    .append(row.member().certified()).append(',')
                    .append(Long.toUnsignedString(row.member().descriptorSeed())).append(',')
                    .append(f(row.member().reservedHorizontalRadius())).append(',')
                    .append(f(row.member().requiredHorizontalRadius())).append(',')
                    .append(f(row.member().reservedBelowSuspension())).append(',')
                    .append(f(row.member().requiredBelowSuspension())).append(',')
                    .append(f(row.member().reservedAboveSuspension())).append(',')
                    .append(f(row.member().requiredAboveSuspension())).append(',')
                    .append(f(row.report().groupChecks().get(0).reservedGroupRadius())).append(',')
                    .append(row.report().groupChecks().get(0).requiredGroupRadius().isPresent()
                            ? f(row.report().groupChecks().get(0).requiredGroupRadius().orElseThrow())
                            : "")
                    .append(',')
                    .append(row.report().consumedReservationDefect())
                    .append('\n');
        }
        Files.writeString(
                out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0053</title>"
                        + "<h1>Seed-aware support reservation preflight</h1>"
                        + "<p>The 16:9 atlas compares already-consumed reservations against analytical "
                        + "requirements before procedural graph compilation. Green panels admit, red "
                        + "panels prove an undersized reservation, and amber marks support that remains "
                        + "explicitly uncertified. The seed-aware pair uses the same reusable morphology "
                        + "intent but different exact derived member seeds.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static List<Row> scenarios() {
        ArrayList<Row> result = new ArrayList<>();
        var builtIns = SkyIslandMorphologyProviders.builtInRegistry();
        var preflight = new SkyIslandSupportReservationPreflight();

        add(
                result,
                "ADMITTED_BUILTIN",
                preflight.evaluate(
                        plan(
                                53501L,
                                direct(MorphologyFamily.MASSIF),
                                360.0,
                                440.0),
                        builtIns,
                        ADEQUATE_VERTICAL));

        add(
                result,
                "HORIZONTAL_GROUP_UNDERSIZED",
                preflight.evaluate(
                        plan(
                                53502L,
                                direct(MorphologyFamily.MASSIF),
                                256.0,
                                280.0),
                        builtIns,
                        ADEQUATE_VERTICAL));

        add(
                result,
                "VERTICAL_UNDERSIZED",
                preflight.evaluate(
                        plan(
                                53503L,
                                direct(MorphologyFamily.SPINE),
                                360.0,
                                440.0),
                        builtIns,
                        new SkyIslandWorldVerticalReservation(180.0, 140.0)));

        MorphologyProviderId massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderId basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        add(
                result,
                "INTERIOR_BLEND_UNCERTIFIED",
                preflight.evaluate(
                        plan(
                                53504L,
                                ProviderBlendMorphologySpec.full(
                                        new MorphologyProviderBlend(
                                                massif, basin, 0.35)),
                                360.0,
                                440.0),
                        builtIns,
                        ADEQUATE_VERTICAL));

        MorphologyProviderId seedAwareId =
                new MorphologyProviderId("reference", "seed-aware");
        SkyIslandMorphologySpec seedAwareSpec =
                ProviderMorphologySpec.full(seedAwareId);
        SkyIslandArchipelagoPlan first =
                plan(53511L, seedAwareSpec, 200.0, 240.0);
        SkyIslandArchipelagoPlan second =
                plan(53512L, seedAwareSpec, 200.0, 240.0);
        long firstSeed =
                first.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        SkyIslandMorphologyProviderRegistry seedRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(seedAwareProvider(seedAwareId, firstSeed))
                        .build();
        SkyIslandWorldVerticalReservation seedVertical =
                new SkyIslandWorldVerticalReservation(100.0, 100.0);
        add(
                result,
                "SEED_A_LARGE",
                preflight.evaluate(first, seedRegistry, seedVertical));
        add(
                result,
                "SEED_B_SMALL",
                preflight.evaluate(second, seedRegistry, seedVertical));

        return List.copyOf(result);
    }

    private static void add(
            List<Row> rows,
            String scenario,
            SkyIslandSupportReservationPreflightReport report) {
        rows.add(new Row(scenario, report, report.memberChecks().get(0)));
    }

    private static void renderPanel(
            Graphics2D g, Row row, int x, int y) {
        boolean admitted = row.report().admitted();
        Color fill =
                !row.member().certified()
                        ? new Color(247, 235, 195)
                        : admitted
                                ? new Color(219, 239, 221)
                                : new Color(244, 215, 215);
        g.setColor(fill);
        g.fillRect(x + 8, y + 8, PANEL_W - 16, PANEL_H - 16);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 8, y + 8, PANEL_W - 16, PANEL_H - 16);

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(row.scenario(), x + 20, y + 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                "status="
                        + (row.member().certified()
                                ? (admitted ? "ADMITTED" : "REJECTED")
                                : "UNCERTIFIED"),
                x + 20,
                y + 47);
        g.drawString(
                "seed=" + Long.toUnsignedString(row.member().descriptorSeed()),
                x + 20,
                y + 62);

        int barX = x + 34;
        int barW = PANEL_W - 70;
        int lineY = y + 100;
        drawBar(
                g,
                "member horizontal",
                row.member().reservedHorizontalRadius(),
                row.member().requiredHorizontalRadius(),
                barX,
                lineY,
                barW,
                380.0);
        drawBar(
                g,
                "below suspension",
                row.member().reservedBelowSuspension(),
                row.member().requiredBelowSuspension(),
                barX,
                lineY + 52,
                barW,
                280.0);
        drawBar(
                g,
                "above suspension",
                row.member().reservedAboveSuspension(),
                row.member().requiredAboveSuspension(),
                barX,
                lineY + 104,
                barW,
                180.0);
        double groupRequired =
                row.report().groupChecks().get(0).requiredGroupRadius().isPresent()
                        ? row.report().groupChecks().get(0).requiredGroupRadius().orElseThrow()
                        : Double.NaN;
        drawBar(
                g,
                "group radius",
                row.report().groupChecks().get(0).reservedGroupRadius(),
                groupRequired,
                barX,
                lineY + 156,
                barW,
                480.0);
    }

    private static void drawBar(
            Graphics2D g,
            String label,
            double reserved,
            double required,
            int x,
            int y,
            int width,
            double scaleMax) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(label, x, y - 6);

        int reservedWidth =
                (int) Math.round(Math.min(1.0, reserved / scaleMax) * width);
        g.setColor(new Color(205, 205, 205));
        g.fillRect(x, y, reservedWidth, 16);
        g.setColor(new Color(120, 120, 120));
        g.drawRect(x, y, reservedWidth, 16);

        if (!Double.isNaN(required)) {
            int requiredX =
                    x + (int) Math.round(Math.min(1.0, required / scaleMax) * width);
            g.setColor(required <= reserved ? new Color(55, 125, 65) : new Color(175, 55, 55));
            g.fillRect(requiredX - 2, y - 3, 4, 22);
            g.drawString(
                    "R " + String.format(Locale.ROOT, "%.1f", reserved)
                            + " / Q " + String.format(Locale.ROOT, "%.1f", required),
                    x + width - 118,
                    y - 6);
        } else {
            g.setColor(new Color(135, 105, 30));
            g.drawString("R " + String.format(Locale.ROOT, "%.1f", reserved) + " / Q ?", x + width - 90, y - 6);
        }
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
                throw new AssertionError("AUTH-0053 evidence preflight must not compile graphs");
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

    private static SkyIslandArchipelagoPlan plan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontalRadius,
            double reservedGroupRadius) {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
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
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth53",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontalRadius,
                        96.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                640.0, 0.0, 0.0, 0.0),
                        reservedGroupRadius);
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

    private static String f(double value) {
        return Double.isNaN(value)
                ? ""
                : String.format(Locale.ROOT, "%.6f", value);
    }

    private record Row(
            String scenario,
            SkyIslandSupportReservationPreflightReport report,
            SkyIslandSupportReservationMemberCheck member) {}
}
