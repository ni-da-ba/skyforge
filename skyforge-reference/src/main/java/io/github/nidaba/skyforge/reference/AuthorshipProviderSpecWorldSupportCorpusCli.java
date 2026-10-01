package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalogCompiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalogSupportBundle;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeSupportCertificate;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.BasicStroke;
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

/** Generates AUTH-0052 provider-spec/world-catalog support proof evidence. */
public final class AuthorshipProviderSpecWorldSupportCorpusCli {
    private static final long ROOT_SEED = 0x534b594641303532L;
    private static final double RADIUS = 192.0;
    private static final double ADEQUATE_HORIZONTAL = 360.0;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);
    private static final double LEGACY_HORIZONTAL = 256.0;
    private static final double LEGACY_BELOW = 180.0;
    private static final double LEGACY_ABOVE = 140.0;

    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 250;
    private static final int PLOT_X = 48;
    private static final int PLOT_Y = 58;
    private static final int PLOT_WIDTH = 462;
    private static final int PLOT_HEIGHT = 156;
    private static final double DISPLAY_X = 380.0;
    private static final double DISPLAY_MIN_Y = -280.0;
    private static final double DISPLAY_MAX_Y = 180.0;

    private AuthorshipProviderSpecWorldSupportCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-provider-spec-world-support-v1");
        Files.createDirectories(out);

        List<ScenarioSpec> specs = scenarioSpecs();
        SkyIslandArchipelagoRequest request =
                request(specs, ADEQUATE_HORIZONTAL);
        var plan = new SkyIslandArchipelagoPlanner().plan(request);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var compiler = new SkyIslandWorldCatalogCompiler();
        SkyIslandWorldCatalog ordinary =
                compiler.compile(plan, registry, ADEQUATE_VERTICAL);
        SkyIslandWorldCatalogSupportBundle bundle =
                compiler.compileWithSupport(plan, registry, ADEQUATE_VERTICAL);

        if (!ordinary.volumes().equals(bundle.catalog().volumes())) {
            throw new IllegalStateException(
                    "AUTH-0052 support compilation changed ordinary world catalog");
        }

        ArrayList<Row> rows = new ArrayList<>();
        ArrayList<BufferedImage> panels = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ScenarioSpec spec = specs.get(i);
            SkyIslandWorldVolume volume = bundle.catalog().volumes().get(i);
            SkyIslandWorldVolumeSupportCertificate certificate =
                    bundle.certificateFor(volume).orElse(null);
            Row row = measure(spec, volume, certificate);
            rows.add(row);
            panels.add(renderScenario(row, volume, certificate));
        }

        boolean legacyRejected = false;
        try {
            SkyIslandArchipelagoRequest legacyRequest =
                    request(specs, LEGACY_HORIZONTAL);
            compiler.compileWithSupport(
                    new SkyIslandArchipelagoPlanner().plan(legacyRequest),
                    registry,
                    new SkyIslandWorldVerticalReservation(
                            LEGACY_BELOW, LEGACY_ABOVE));
        } catch (IllegalArgumentException expected) {
            legacyRejected = true;
        }
        if (!legacyRejected) {
            throw new IllegalStateException(
                    "legacy reservation unexpectedly contained AUTH-0052 support");
        }

        SkyIslandWorldVolume gateVolume = bundle.catalog().volumes().get(1);
        SkyIslandWorldVolumeSupportCertificate gateCertificate =
                bundle.certificateFor(gateVolume).orElseThrow();
        panels.add(renderReservationGate(gateVolume, gateCertificate));

        BufferedImage atlas =
                new BufferedImage(
                        PANEL_WIDTH * 2,
                        PANEL_HEIGHT * 3,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        for (int i = 0; i < panels.size(); i++) {
            atlasGraphics.drawImage(
                    panels.get(i),
                    (i % 2) * PANEL_WIDTH,
                    (i / 2) * PANEL_HEIGHT,
                    null);
        }
        atlasGraphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,specKind,certified,certificateKind,queryContainsSupport,"
                                + "sampledColumns,queryContainmentViolations,"
                                + "supportContainmentViolations,queryHorizontalRadius,"
                                + "supportHorizontalRadius,queryBelow,queryAbove,"
                                + "supportBelow,supportAbove\n");
        for (Row row : rows) {
            manifest.append(row.scenario()).append(',')
                    .append(row.specKind()).append(',')
                    .append(row.certified()).append(',')
                    .append(row.certificateKind()).append(',')
                    .append(row.queryContainsSupport()).append(',')
                    .append(row.sampledColumns()).append(',')
                    .append(row.queryContainmentViolations()).append(',')
                    .append(row.supportContainmentViolations()).append(',')
                    .append(format(row.queryHorizontalRadius())).append(',')
                    .append(format(row.supportHorizontalRadius())).append(',')
                    .append(format(row.queryBelow())).append(',')
                    .append(format(row.queryAbove())).append(',')
                    .append(format(row.supportBelow())).append(',')
                    .append(format(row.supportAbove())).append('\n');
        }
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);

        String bundleManifest =
                "volumeCount,certifiedCount,uncertifiedCount,fullyCertified,"
                        + "ordinaryCatalogEqual,legacyReservationRejected\n"
                        + bundle.catalog().volumeCount()
                        + ","
                        + bundle.certifiedCount()
                        + ","
                        + bundle.uncertifiedCount()
                        + ","
                        + bundle.fullyCertified()
                        + ",true,"
                        + legacyRejected
                        + "\n";
        Files.writeString(
                out.resolve("bundle.csv"),
                bundleManifest,
                StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0052</title>"
                        + "<h1>Provider-spec world support proof</h1>"
                        + "<p>Five provider-spec panels show unchanged world query reservations, "
                        + "actual compiled X/Y slices, and proof-grade support where analytically "
                        + "available. Direct providers and exact blend endpoints certify. The true "
                        + "interior blend remains deliberately uncertified. The final panel shows "
                        + "the reservation gate: the older query envelope is visibly smaller than "
                        + "the certified support and is rejected by support-aware world compilation.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">scenario manifest</a> · "
                        + "<a href=\"bundle.csv\">world bundle manifest</a></p>",
                StandardCharsets.UTF_8);
    }

    private static List<ScenarioSpec> scenarioSpecs() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        return List.of(
                new ScenarioSpec(
                        "DIRECT_MASSIF",
                        "DIRECT",
                        ProviderMorphologySpec.full(massif)),
                new ScenarioSpec(
                        "DIRECT_SPINE",
                        "DIRECT",
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(
                                        MorphologyFamily.SPINE))),
                new ScenarioSpec(
                        "ENDPOINT_FIRST",
                        "BLEND_ENDPOINT",
                        ProviderBlendMorphologySpec.full(
                                new MorphologyProviderBlend(
                                        massif, basin, 0.0))),
                new ScenarioSpec(
                        "ENDPOINT_SECOND",
                        "BLEND_ENDPOINT",
                        ProviderBlendMorphologySpec.full(
                                new MorphologyProviderBlend(
                                        massif, basin, 1.0))),
                new ScenarioSpec(
                        "INTERIOR_BLEND",
                        "BLEND_INTERIOR",
                        ProviderBlendMorphologySpec.full(
                                new MorphologyProviderBlend(
                                        massif, basin, 0.35))));
    }

    private static SkyIslandArchipelagoRequest request(
            List<ScenarioSpec> specs, double reservedHorizontalRadius) {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        0L,
                        0.0,
                        0.0,
                        320.0,
                        RADIUS,
                        76.0,
                        100.0,
                        48.0,
                        Math.PI / 6.0,
                        0.65,
                        0.60,
                        0.25,
                        0.0,
                        28.0);

        ArrayList<SkyIslandGroupTemplate> templates = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ScenarioSpec spec = specs.get(i);
            templates.add(
                    new SkyIslandGroupTemplate(
                            "auth52-" + i,
                            i == 0
                                    ? SkyIslandGroupRole.ANCHOR
                                    : SkyIslandGroupRole.OUTLIER,
                            descriptor,
                            reservedHorizontalRadius,
                            96.0,
                            0.0,
                            List.of(spec.morphology()),
                            new SkyIslandGroupLayout.Cluster(
                                    640.0, 0.0, 0.0, 0.0),
                            Math.max(440.0, reservedHorizontalRadius)));
        }
        return new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                320.0,
                520.0,
                templates,
                new SkyIslandArchipelagoLayout.Hub(
                        2_200.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static Row measure(
            ScenarioSpec spec,
            SkyIslandWorldVolume volume,
            SkyIslandWorldVolumeSupportCertificate certificate) {
        var descriptor = volume.compiledVolume().descriptor();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume());
        WorldBounds query = volume.bounds();
        WorldBounds support =
                certificate == null ? null : certificate.supportBounds();

        int sampledColumns = 0;
        int queryViolations = 0;
        int supportViolations = 0;
        double scan = ADEQUATE_HORIZONTAL;
        for (int iz = 0; iz < 45; iz++) {
            double z = -scan + iz * (2.0 * scan / 44.0);
            for (int ix = 0; ix < 45; ix++) {
                double x = -scan + ix * (2.0 * scan / 44.0);
                var column = columns.columnAt(new SkyIslandLocalPosition(x, z));
                if (column.isEmpty()) {
                    continue;
                }
                sampledColumns++;
                double worldX = descriptor.centerX() + x;
                double worldZ = descriptor.centerZ() + z;
                if (!query.contains(
                        worldX, column.orElseThrow().upperY(), worldZ)
                        || !query.contains(
                                worldX,
                                column.orElseThrow().undersideY(),
                                worldZ)) {
                    queryViolations++;
                }
                if (support != null
                        && (!support.contains(
                                        worldX,
                                        column.orElseThrow().upperY(),
                                        worldZ)
                                || !support.contains(
                                        worldX,
                                        column.orElseThrow().undersideY(),
                                        worldZ))) {
                    supportViolations++;
                }
            }
        }

        double queryHorizontal =
                query.maximumX() - descriptor.centerX();
        double queryBelow =
                descriptor.suspensionElevation() - query.minimumY();
        double queryAbove =
                query.maximumY() - descriptor.suspensionElevation();
        double supportHorizontal =
                certificate == null
                        ? Double.NaN
                        : certificate.envelope().maximumHorizontalRadius();
        double supportBelow =
                certificate == null
                        ? Double.NaN
                        : certificate.envelope().maximumUndersideDepth();
        double supportAbove =
                certificate == null
                        ? Double.NaN
                        : certificate.envelope().maximumUpperOffset();

        return new Row(
                spec.scenario(),
                spec.specKind(),
                certificate != null,
                certificate == null
                        ? ""
                        : certificate.envelope().certificateKind(),
                certificate != null && query.contains(support),
                sampledColumns,
                queryViolations,
                certificate == null ? -1 : supportViolations,
                queryHorizontal,
                supportHorizontal,
                queryBelow,
                queryAbove,
                supportBelow,
                supportAbove);
    }

    private static BufferedImage renderScenario(
            Row row,
            SkyIslandWorldVolume volume,
            SkyIslandWorldVolumeSupportCertificate certificate) {
        BufferedImage image =
                new BufferedImage(
                        PANEL_WIDTH,
                        PANEL_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(row.scenario(), 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                row.certified()
                        ? "CERTIFIED  " + row.certificateKind()
                        : "UNCERTIFIED — interior hybrid structural support not inferred",
                10,
                38);

        drawRelativeBounds(
                graphics,
                volume,
                volume.bounds(),
                new Color(236, 236, 236),
                new Color(160, 160, 160),
                true);
        if (certificate != null) {
            drawRelativeBounds(
                    graphics,
                    volume,
                    certificate.supportBounds(),
                    new Color(224, 240, 225),
                    new Color(65, 130, 75),
                    true);
        }
        drawSlice(graphics, volume, new Color(77, 83, 92));
        graphics.dispose();
        return image;
    }

    private static BufferedImage renderReservationGate(
            SkyIslandWorldVolume volume,
            SkyIslandWorldVolumeSupportCertificate certificate) {
        BufferedImage image =
                new BufferedImage(
                        PANEL_WIDTH,
                        PANEL_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString("RESERVATION GATE", 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "old 256 / 180 / 140 query is smaller than certified support -> REJECT",
                10,
                38);

        var descriptor = volume.compiledVolume().descriptor();
        WorldBounds adequate = volume.bounds();
        WorldBounds legacy =
                new WorldBounds(
                        descriptor.centerX() - LEGACY_HORIZONTAL,
                        descriptor.centerX() + LEGACY_HORIZONTAL,
                        descriptor.suspensionElevation() - LEGACY_BELOW,
                        descriptor.suspensionElevation() + LEGACY_ABOVE,
                        descriptor.centerZ() - LEGACY_HORIZONTAL,
                        descriptor.centerZ() + LEGACY_HORIZONTAL);

        drawRelativeBounds(
                graphics,
                volume,
                adequate,
                new Color(240, 240, 240),
                new Color(170, 170, 170),
                true);
        drawRelativeBounds(
                graphics,
                volume,
                certificate.supportBounds(),
                new Color(224, 240, 225),
                new Color(65, 130, 75),
                true);
        drawRelativeBounds(
                graphics,
                volume,
                legacy,
                new Color(0, 0, 0, 0),
                new Color(170, 65, 65),
                false);
        drawSlice(graphics, volume, new Color(77, 83, 92));

        graphics.setColor(new Color(160, 55, 55));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        graphics.drawString("OLD QUERY", PLOT_X + 8, PLOT_Y + 16);
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(218, 218, 218));
        graphics.drawRect(PLOT_X, PLOT_Y, PLOT_WIDTH, PLOT_HEIGHT);
        return graphics;
    }

    private static void drawRelativeBounds(
            Graphics2D graphics,
            SkyIslandWorldVolume volume,
            WorldBounds bounds,
            Color fill,
            Color border,
            boolean fillEnabled) {
        var descriptor = volume.compiledVolume().descriptor();
        double minimumX = bounds.minimumX() - descriptor.centerX();
        double maximumX = bounds.maximumX() - descriptor.centerX();
        double minimumY =
                bounds.minimumY() - descriptor.suspensionElevation();
        double maximumY =
                bounds.maximumY() - descriptor.suspensionElevation();

        int left = clamp(xPixel(minimumX), PLOT_X, PLOT_X + PLOT_WIDTH);
        int right = clamp(xPixel(maximumX), PLOT_X, PLOT_X + PLOT_WIDTH);
        int top = clamp(yPixel(maximumY), PLOT_Y, PLOT_Y + PLOT_HEIGHT);
        int bottom = clamp(yPixel(minimumY), PLOT_Y, PLOT_Y + PLOT_HEIGHT);

        if (fillEnabled) {
            graphics.setColor(fill);
            graphics.fillRect(
                    left,
                    top,
                    Math.max(1, right - left),
                    Math.max(1, bottom - top));
        }
        graphics.setColor(border);
        graphics.setStroke(new BasicStroke(fillEnabled ? 1.2f : 2.0f));
        graphics.drawRect(
                left,
                top,
                Math.max(1, right - left),
                Math.max(1, bottom - top));
    }

    private static void drawSlice(
            Graphics2D graphics,
            SkyIslandWorldVolume volume,
            Color color) {
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        volume.compiledVolume());
        var descriptor = volume.compiledVolume().descriptor();
        graphics.setColor(color);
        for (int px = 0; px < PLOT_WIDTH; px++) {
            double localX = localX(px);
            var column =
                    columns.columnAt(new SkyIslandLocalPosition(localX, 0.0));
            if (column.isEmpty()) {
                continue;
            }
            double upper =
                    column.orElseThrow().upperY()
                            - descriptor.suspensionElevation();
            double underside =
                    column.orElseThrow().undersideY()
                            - descriptor.suspensionElevation();
            graphics.drawLine(
                    PLOT_X + px,
                    yPixel(upper),
                    PLOT_X + px,
                    yPixel(underside));
        }
    }

    private static int xPixel(double x) {
        return PLOT_X
                + (int)
                        Math.round(
                                (x + DISPLAY_X)
                                        / (2.0 * DISPLAY_X)
                                        * PLOT_WIDTH);
    }

    private static double localX(int pixel) {
        return -DISPLAY_X
                + pixel
                        * (2.0 * DISPLAY_X)
                        / (PLOT_WIDTH - 1.0);
    }

    private static int yPixel(double y) {
        return PLOT_Y
                + PLOT_HEIGHT
                - (int)
                        Math.round(
                                (y - DISPLAY_MIN_Y)
                                        / (DISPLAY_MAX_Y - DISPLAY_MIN_Y)
                                        * PLOT_HEIGHT);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String format(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private record ScenarioSpec(
            String scenario,
            String specKind,
            SkyIslandMorphologySpec morphology) {}

    private record Row(
            String scenario,
            String specKind,
            boolean certified,
            String certificateKind,
            boolean queryContainsSupport,
            int sampledColumns,
            int queryContainmentViolations,
            int supportContainmentViolations,
            double queryHorizontalRadius,
            double supportHorizontalRadius,
            double queryBelow,
            double queryAbove,
            double supportBelow,
            double supportAbove) {}
}
