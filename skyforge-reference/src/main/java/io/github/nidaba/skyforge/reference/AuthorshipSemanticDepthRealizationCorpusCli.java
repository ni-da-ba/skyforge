package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticDepthRealizationTransform;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandVerticalColumn;
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
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0027 semantic-depth realization evidence. */
public final class AuthorshipSemanticDepthRealizationCorpusCli {
    private static final int MAP = 190;
    private static final int HEADER = 70;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;
    private static final int METRIC_GRID = 61;

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SOLID = new Color(232, 232, 228);
    private static final Color UPPER = new Color(45, 45, 45);
    private static final Color LOWER = new Color(92, 92, 92);
    private static final Color QUARTER = new Color(194, 107, 64);
    private static final Color HALF = new Color(83, 114, 166);
    private static final Color THREE_QUARTER = new Color(112, 83, 140);

    private AuthorshipSemanticDepthRealizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-semantic-depth-realization-v1");
        Files.createDirectories(out);

        List<Fixture> fixtures = List.of(
                fixture("massif", 7L, SkyIslandMorphologyFamily.MASSIF, 0.18),
                fixture("tableland", 118L, SkyIslandMorphologyFamily.TABLELAND, -0.12),
                fixture("spine", 3670L, SkyIslandMorphologyFamily.SPINE, 0.24),
                fixture("basin", 77L, SkyIslandMorphologyFamily.BASIN, -0.20),
                fixture("lobed", 1051L, SkyIslandMorphologyFamily.LOBED, 0.08),
                fixture("lobed-asymmetric", 811L, SkyIslandMorphologyFamily.LOBED, 0.62));

        BufferedImage atlas =
                new BufferedImage(2 * SPECIMEN_WIDTH, 3 * SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(BACKGROUND);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,seed,morphology,positiveColumns,minThickness,maxThickness,"
                        + "maxRoundTripError,maxSurfaceMismatch\n");

        SemanticSkyIslandVolumeRecipe recipe = new SemanticSkyIslandVolumeRecipe();
        for (int index = 0; index < fixtures.size(); index++) {
            Fixture fixture = fixtures.get(index);
            CompiledSkyIslandVolume compiled = recipe.compile(fixture.descriptor());
            SkyIslandCompiledVolumeColumnField columns =
                    new SkyIslandCompiledVolumeColumnField(compiled);
            SkyIslandSemanticDepthRealizationTransform transform =
                    new SkyIslandSemanticDepthRealizationTransform(columns);
            Metrics metrics = measure(compiled, columns, transform);
            BufferedImage specimen = renderSpecimen(fixture.role(), compiled, columns, metrics);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(fixture.role() + "-" + fixture.seed() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (index % 2) * SPECIMEN_WIDTH,
                    (index / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(fixture.role()).append(',')
                    .append(fixture.seed()).append(',')
                    .append(fixture.descriptor().morphologyFamily().identifier()).append(',')
                    .append(metrics.positiveColumns()).append(',')
                    .append(format(metrics.minimumThickness())).append(',')
                    .append(format(metrics.maximumThickness())).append(',')
                    .append(format(metrics.maximumRoundTripError())).append(',')
                    .append(format(metrics.maximumSurfaceMismatch())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0027</title>"
                        + "<h1>Semantic-depth physical realization transform</h1>"
                        + "<p>Depth bands are evaluated against authoritative compiled upper/underside "
                        + "surfaces. The fixtures validate the transform and do not define a new "
                        + "authorship-to-volume descriptor mapping.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Fixture fixture(
            String role,
            long seed,
            SkyIslandMorphologyFamily family,
            double undersideAsymmetry) {
        SkyIslandVolumeDescriptor descriptor = SkyIslandVolumeDescriptor.schema2(
                seed,
                0.0,
                0.0,
                256.0,
                112.0,
                66.0,
                96.0,
                28.0,
                0.48,
                0.61,
                0.59,
                undersideAsymmetry,
                family,
                0.17,
                34.0,
                0.36);
        return new Fixture(role, seed, descriptor);
    }

    private static BufferedImage renderSpecimen(
            String role,
            CompiledSkyIslandVolume compiled,
            SkyIslandCompiledVolumeColumnField columns,
            Metrics metrics) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandVolumeDescriptor descriptor = compiled.descriptor();
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                role + " / " + descriptor.morphologyFamily().identifier()
                        + " / seed=" + descriptor.seed(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "positive columns=%d  thickness=[%.2f, %.2f]  roundtrip<=%.3g",
                        metrics.positiveColumns(),
                        metrics.minimumThickness(),
                        metrics.maximumThickness(),
                        metrics.maximumRoundTripError()),
                7,
                35);
        g.drawString("depth 0 = upper surface; depth 1 = underside", 7, 50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "CENTER DEPTH BANDS", 0, MAP, 64);
        centered(g, "OFFSET DEPTH BANDS", MAP, MAP, 64);
        centered(g, "MID-DEPTH PLAN", 2 * MAP, MAP, 64);
        centered(g, "COLUMN THICKNESS", 3 * MAP, MAP, 64);

        double[] centerRange = sectionRange(columns, 0.0);
        double[] offsetRange = sectionRange(columns, descriptor.nominalRadius() * 0.34);
        drawDepthBands(image, 0, columns, 0.0, centerRange[0], centerRange[1]);
        drawDepthBands(
                image,
                MAP,
                columns,
                descriptor.nominalRadius() * 0.34,
                offsetRange[0],
                offsetRange[1]);
        drawPlan(image, 2 * MAP, columns, true, metrics);
        drawPlan(image, 3 * MAP, columns, false, metrics);

        g.setColor(new Color(30, 30, 30));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void drawDepthBands(
            BufferedImage image,
            int offsetX,
            SkyIslandCompiledVolumeColumnField columns,
            double localZ,
            double minimumY,
            double maximumY) {
        double radius = columns.nominalRadius();
        for (int px = 0; px < MAP; px++) {
            double localX = -radius + 2.0 * radius * px / (MAP - 1.0);
            var column = columns.columnAt(new SkyIslandLocalPosition(localX, localZ));
            if (column.isEmpty()) {
                continue;
            }
            SkyIslandVerticalColumn c = column.orElseThrow();
            int upperY = yPixel(c.upperY(), minimumY, maximumY);
            int lowerY = yPixel(c.undersideY(), minimumY, maximumY);
            for (int py = upperY; py <= lowerY; py++) {
                image.setRGB(offsetX + px, HEADER + py, SOLID.getRGB());
            }
            setPixel(image, offsetX + px, HEADER + upperY, UPPER);
            setPixel(image, offsetX + px, HEADER + lowerY, LOWER);
            setPixel(
                    image,
                    offsetX + px,
                    HEADER + yPixel(c.physicalYAt(0.25), minimumY, maximumY),
                    QUARTER);
            setPixel(
                    image,
                    offsetX + px,
                    HEADER + yPixel(c.physicalYAt(0.50), minimumY, maximumY),
                    HALF);
            setPixel(
                    image,
                    offsetX + px,
                    HEADER + yPixel(c.physicalYAt(0.75), minimumY, maximumY),
                    THREE_QUARTER);
        }
    }

    private static void drawPlan(
            BufferedImage image,
            int offsetX,
            SkyIslandCompiledVolumeColumnField columns,
            boolean midpoint,
            Metrics metrics) {
        double radius = columns.nominalRadius();
        double minimum = midpoint
                ? metrics.minimumMidpointY()
                : metrics.minimumThickness();
        double maximum = midpoint
                ? metrics.maximumMidpointY()
                : metrics.maximumThickness();

        for (int py = 0; py < MAP; py++) {
            double localZ = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double localX = -radius + 2.0 * radius * px / (MAP - 1.0);
                var column = columns.columnAt(new SkyIslandLocalPosition(localX, localZ));
                if (column.isEmpty()) {
                    image.setRGB(offsetX + px, HEADER + py, BACKGROUND.getRGB());
                    continue;
                }
                SkyIslandVerticalColumn c = column.orElseThrow();
                double value = midpoint ? c.physicalYAt(0.5) : c.thickness();
                double normalized = maximum <= minimum
                        ? 0.5
                        : clamp01((value - minimum) / (maximum - minimum));
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        heat(normalized).getRGB());
            }
        }
    }

    private static double[] sectionRange(
            SkyIslandCompiledVolumeColumnField columns,
            double localZ) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double radius = columns.nominalRadius();
        for (int sample = 0; sample < 361; sample++) {
            double localX = -radius + 2.0 * radius * sample / 360.0;
            var column = columns.columnAt(new SkyIslandLocalPosition(localX, localZ));
            if (column.isEmpty()) {
                continue;
            }
            SkyIslandVerticalColumn c = column.orElseThrow();
            minimum = Math.min(minimum, c.undersideY());
            maximum = Math.max(maximum, c.upperY());
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalStateException("transform fixture has no positive section columns");
        }
        double margin = Math.max(2.0, 0.05 * (maximum - minimum));
        return new double[] {minimum - margin, maximum + margin};
    }

    private static Metrics measure(
            CompiledSkyIslandVolume compiled,
            SkyIslandCompiledVolumeColumnField columns,
            SkyIslandSemanticDepthRealizationTransform transform) {
        SkyIslandTerrainInterpreter interpreter =
                new SkyIslandTerrainInterpreter(compiled, SkyIslandTerrainProfile.reference());
        double radius = columns.nominalRadius();
        int positive = 0;
        double minThickness = Double.POSITIVE_INFINITY;
        double maxThickness = Double.NEGATIVE_INFINITY;
        double minMidpoint = Double.POSITIVE_INFINITY;
        double maxMidpoint = Double.NEGATIVE_INFINITY;
        double maxRoundTrip = 0.0;
        double maxMismatch = 0.0;

        for (int iz = 0; iz < METRIC_GRID; iz++) {
            double localZ = -radius + 2.0 * radius * iz / (METRIC_GRID - 1.0);
            for (int ix = 0; ix < METRIC_GRID; ix++) {
                double localX = -radius + 2.0 * radius * ix / (METRIC_GRID - 1.0);
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(localX, localZ);
                var optional = columns.columnAt(local);
                if (optional.isEmpty()) {
                    continue;
                }
                positive++;
                SkyIslandVerticalColumn column = optional.orElseThrow();
                minThickness = Math.min(minThickness, column.thickness());
                maxThickness = Math.max(maxThickness, column.thickness());
                double midpoint = column.physicalYAt(0.5);
                minMidpoint = Math.min(minMidpoint, midpoint);
                maxMidpoint = Math.max(maxMidpoint, midpoint);

                double worldX = compiled.descriptor().centerX() + localX;
                double worldZ = compiled.descriptor().centerZ() + localZ;
                maxMismatch = Math.max(
                        maxMismatch,
                        Math.abs(interpreter.upperSurfaceHeight(worldX, worldZ) - column.upperY()));
                maxMismatch = Math.max(
                        maxMismatch,
                        Math.abs(interpreter.undersideSurfaceHeight(worldX, worldZ) - column.undersideY()));

                for (double depth : new double[] {0.0, 0.125, 0.25, 0.5, 0.75, 0.875, 1.0}) {
                    SkyIslandSubsurfacePosition semantic =
                            new SkyIslandSubsurfacePosition(local, depth);
                    SkyIslandRealizedSubsurfacePosition physical =
                            transform.toPhysical(semantic).orElseThrow();
                    double recovered = transform.toSemantic(physical)
                            .orElseThrow()
                            .depthFraction();
                    maxRoundTrip = Math.max(maxRoundTrip, Math.abs(depth - recovered));
                }
            }
        }

        if (positive == 0) {
            throw new IllegalStateException("transform fixture has no positive columns");
        }
        return new Metrics(
                positive,
                minThickness,
                maxThickness,
                minMidpoint,
                maxMidpoint,
                maxRoundTrip,
                maxMismatch);
    }

    private static int yPixel(double y, double minimumY, double maximumY) {
        double normalized = (maximumY - y) / (maximumY - minimumY);
        return Math.max(0, Math.min(MAP - 1, (int) Math.round(normalized * (MAP - 1))));
    }

    private static void setPixel(BufferedImage image, int x, int y, Color color) {
        if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
            image.setRGB(x, y, color.getRGB());
        }
    }

    private static Color heat(double normalized) {
        double t = clamp01(normalized);
        float hue = (float) (0.66 - 0.56 * t);
        return Color.getHSBColor(hue, 0.48f, 0.90f);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private record Fixture(
            String role,
            long seed,
            SkyIslandVolumeDescriptor descriptor) {}

    private record Metrics(
            int positiveColumns,
            double minimumThickness,
            double maximumThickness,
            double minimumMidpointY,
            double maximumMidpointY,
            double maximumRoundTripError,
            double maximumSurfaceMismatch) {}
}
