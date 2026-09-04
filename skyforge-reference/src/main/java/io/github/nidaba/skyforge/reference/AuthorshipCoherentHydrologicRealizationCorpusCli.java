package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandContinuousHydrologicTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfaceCell;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlan;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlanner;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlanner;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0019 raw-versus-coherent hydrologic realization evidence. */
public final class AuthorshipCoherentHydrologicRealizationCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 230;
    private static final int HEADER = 62;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;
    private static final double DELTA_THRESHOLD = 1.0e-5;

    private AuthorshipCoherentHydrologicRealizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-coherent-hydrologic-realization-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);

        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,rawReaches,coherentReaches,rawRiparian,coherentRiparian,"
                        + "rawChangedCells,coherentChangedCells,migrationChangedCells,"
                        + "meanAbsCoarseMigration,maxCoherentRaiseVsRaw,maxCoherentLowerVsRaw,"
                        + "denseMeanAbsMigration\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandHydrologicTerrainSurfacePlan rawSurface =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
            SkyIslandContinuousHydrologicTerrainField rawField =
                    SkyIslandContinuousHydrologicTerrainField.create(descriptor, rawSurface);
            SkyIslandNaturalizedChannelPlan rawChannels =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
            SkyIslandContinuousHydrologicTerrainField coherentField = coherent.continuousTerrain();

            MigrationMetrics metrics =
                    measure(rawSurface, coherent.terrainSurface(), descriptor, rawField, coherentField);
            BufferedImage panel = renderPanel(
                    descriptor,
                    rawField,
                    rawChannels,
                    coherentField,
                    coherent.naturalizedChannels(),
                    metrics);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(rawChannels.paths().size()).append(',')
                    .append(coherent.naturalizedChannels().paths().size()).append(',')
                    .append(SkyIslandRiparianCorridorPlanner.plan(descriptor).cellCount()).append(',')
                    .append(coherent.riparian().cellCount()).append(',')
                    .append(rawSurface.changedCellCount()).append(',')
                    .append(coherent.terrainSurface().changedCellCount()).append(',')
                    .append(metrics.changedCells()).append(',')
                    .append(format(metrics.meanAbsCoarseMigration())).append(',')
                    .append(format(metrics.maxRaiseVsRaw())).append(',')
                    .append(format(metrics.maxLowerVsRaw())).append(',')
                    .append(format(metrics.denseMeanAbsMigration())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0019</title>"
                        + "<h1>Coherent hydrologic realization migration</h1>"
                        + "<p>RAW TERRAIN uses the pre-AUTH-0018 complete visible channel set. COHERENT TERRAIN propagates only the retained AUTH-0018 skeleton through riparian, interior drops, terrain influence, coarse shaping, continuous interpolation, and naturalized centerlines. MIGRATION DELTA shows coherent minus raw: orange restores terrain that the redundant network had lowered, blue lowers relative to raw. Ordinary unpruned islands should have a blank delta.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static MigrationMetrics measure(
            SkyIslandHydrologicTerrainSurfacePlan raw,
            SkyIslandHydrologicTerrainSurfacePlan coherent,
            SkyIslandDescriptor descriptor,
            SkyIslandContinuousHydrologicTerrainField rawField,
            SkyIslandContinuousHydrologicTerrainField coherentField) {
        Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> rawByIndex = new HashMap<>();
        for (SkyIslandHydrologicTerrainSurfaceCell cell : raw.cells()) {
            rawByIndex.put(cell.watershedCellIndex(), cell);
        }

        long changed = 0L;
        double sum = 0.0;
        double maxRaise = 0.0;
        double maxLower = 0.0;
        for (SkyIslandHydrologicTerrainSurfaceCell cell : coherent.cells()) {
            SkyIslandHydrologicTerrainSurfaceCell old = rawByIndex.get(cell.watershedCellIndex());
            if (old == null) {
                continue;
            }
            double delta = cell.adjustedElevationPotential() - old.adjustedElevationPotential();
            if (Math.abs(delta) >= DELTA_THRESHOLD) {
                changed++;
            }
            sum += Math.abs(delta);
            maxRaise = Math.max(maxRaise, delta);
            maxLower = Math.max(maxLower, -delta);
        }

        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        long inside = 0L;
        double denseSum = 0.0;
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                inside++;
                denseSum += Math.abs(coherentField.sample(position) - rawField.sample(position));
            }
        }

        return new MigrationMetrics(
                changed,
                coherent.cells().isEmpty() ? 0.0 : sum / coherent.cells().size(),
                maxRaise,
                maxLower,
                inside == 0L ? 0.0 : denseSum / inside);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandContinuousHydrologicTerrainField rawField,
            SkyIslandNaturalizedChannelPlan rawChannels,
            SkyIslandContinuousHydrologicTerrainField coherentField,
            SkyIslandNaturalizedChannelPlan coherentChannels,
            MigrationMetrics metrics) {
        BufferedImage image = new BufferedImage(PANEL_WIDTH, PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                "key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "reaches %d -> %d   migration cells=%d   dense mean |d|=%.5f",
                        rawChannels.paths().size(),
                        coherentChannels.paths().size(),
                        metrics.changedCells(),
                        metrics.denseMeanAbsMigration()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "RAW TERRAIN", 0, MAP, 53);
        centered(g, "COHERENT TERRAIN", MAP, MAP, 53);
        centered(g, "MIGRATION DELTA", 2 * MAP, MAP, 53);

        renderTerrain(g, descriptor, rawField, 0);
        renderTerrain(g, descriptor, coherentField, MAP);
        renderDelta(g, descriptor, rawField, coherentField, 2 * MAP);
        drawPaths(g, descriptor, rawChannels.paths(), 0, new Color(95, 105, 115), 1.15f);
        drawPaths(g, descriptor, coherentChannels.paths(), MAP, new Color(25, 100, 185), 1.55f);
        drawPaths(g, descriptor, coherentChannels.paths(), 2 * MAP, new Color(35, 95, 170), 1.1f);
        g.dispose();
        return image;
    }

    private static void renderTerrain(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandContinuousHydrologicTerrainField field,
            int offsetX) {
        g.setColor(new Color(248, 248, 248));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                int shade = (int) Math.round(50.0 + 190.0 * field.sample(position));
                g.setColor(new Color(shade, shade, shade));
                g.fillRect(offsetX + px, HEADER + py, 1, 1);
            }
        }
    }

    private static void renderDelta(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandContinuousHydrologicTerrainField raw,
            SkyIslandContinuousHydrologicTerrainField coherent,
            int offsetX) {
        g.setColor(new Color(240, 240, 240));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                double delta = coherent.sample(position) - raw.sample(position);
                if (Math.abs(delta) < DELTA_THRESHOLD) {
                    g.setColor(new Color(232, 232, 232));
                } else if (delta > 0.0) {
                    double strength = Math.min(1.0, delta / 0.12);
                    g.setColor(blend(Color.WHITE, new Color(225, 130, 35), 0.12 + 0.88 * strength));
                } else {
                    double strength = Math.min(1.0, -delta / 0.12);
                    g.setColor(blend(Color.WHITE, new Color(50, 105, 190), 0.12 + 0.88 * strength));
                }
                g.fillRect(offsetX + px, HEADER + py, 1, 1);
            }
        }
    }

    private static void drawPaths(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            List<SkyIslandNaturalizedChannelPath> paths,
            int offsetX,
            Color color,
            float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandNaturalizedChannelPath path : paths) {
            for (int i = 1; i < path.points().size(); i++) {
                SkyIslandLocalPosition a = path.points().get(i - 1);
                SkyIslandLocalPosition b = path.points().get(i);
                g.drawLine(
                        offsetX + mapX(a, descriptor.nominalRadius()),
                        HEADER + mapY(a, descriptor.nominalRadius()),
                        offsetX + mapX(b, descriptor.nominalRadius()),
                        HEADER + mapY(b, descriptor.nominalRadius()));
            }
        }
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static Color blend(Color from, Color to, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return new Color(
                (int) Math.round(from.getRed() + f * (to.getRed() - from.getRed())),
                (int) Math.round(from.getGreen() + f * (to.getGreen() - from.getGreen())),
                (int) Math.round(from.getBlue() + f * (to.getBlue() - from.getBlue())));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record MigrationMetrics(
            long changedCells,
            double meanAbsCoarseMigration,
            double maxRaiseVsRaw,
            double maxLowerVsRaw,
            double denseMeanAbsMigration) {}
}
