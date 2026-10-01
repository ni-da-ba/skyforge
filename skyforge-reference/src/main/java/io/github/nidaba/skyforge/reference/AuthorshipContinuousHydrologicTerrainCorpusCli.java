package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandContinuousHydrologicTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfaceCell;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
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
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0016 continuous hydrologic terrain-field evidence. */
public final class AuthorshipContinuousHydrologicTerrainCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 220;
    private static final int HEADER = 64;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;
    private static final double CHANGE_THRESHOLD = 1.0e-4;

    private AuthorshipContinuousHydrologicTerrainCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-continuous-hydrologic-terrain-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);

        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,insideSamples,changedSamples,changedFraction,maxLowering,maxRaising,meanAbsAdjustment,anchorMaxError\n");
        StringBuilder anchors = new StringBuilder(
                "islandKey,cellIndex,x,z,coarseAdjusted,continuousAdjusted,error,coarseAdjustment,continuousAdjustment\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandHydrologicTerrainSurfacePlan coarse =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
            SkyIslandContinuousHydrologicTerrainField continuous =
                    SkyIslandContinuousHydrologicTerrainField.create(descriptor);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);

            RasterMetrics metrics = measure(descriptor, semantic, continuous);
            double anchorMaxError = 0.0;
            for (SkyIslandHydrologicTerrainSurfaceCell cell : coarse.cells()) {
                double sampled = continuous.sample(cell.position());
                double error = Math.abs(sampled - cell.adjustedElevationPotential());
                anchorMaxError = Math.max(anchorMaxError, error);
                anchors.append(key).append(',')
                        .append(cell.watershedCellIndex()).append(',')
                        .append(format(cell.position().x())).append(',')
                        .append(format(cell.position().z())).append(',')
                        .append(format(cell.adjustedElevationPotential())).append(',')
                        .append(format(sampled)).append(',')
                        .append(format(error)).append(',')
                        .append(format(cell.netAdjustment())).append(',')
                        .append(format(continuous.adjustment(cell.position()))).append('\n');
            }

            BufferedImage panel =
                    renderPanel(descriptor, semantic, continuous, profiles, waterbodies, metrics);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(metrics.insideSamples()).append(',')
                    .append(metrics.changedSamples()).append(',')
                    .append(format(metrics.changedFraction())).append(',')
                    .append(format(metrics.maxLowering())).append(',')
                    .append(format(metrics.maxRaising())).append(',')
                    .append(format(metrics.meanAbsoluteAdjustment())).append(',')
                    .append(format(anchorMaxError)).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("anchors.csv"), anchors.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0016</title>"
                        + "<h1>Continuous hydrologic terrain field</h1>"
                        + "<p>BEFORE is the original continuous authored elevation. AFTER adds the continuous AUTH-0015 hydrologic adjustment. CHANGE shows the actual continuous difference: blue lowers terrain, orange raises terrain. Thin blue lines show accepted channel topology and cyan marks retained standing water. The images are pixel-sampled fields rather than coarse planning cells.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"anchors.csv\">anchors.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static RasterMetrics measure(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandContinuousHydrologicTerrainField continuous) {
        long inside = 0L;
        long changed = 0L;
        double maxLowering = 0.0;
        double maxRaising = 0.0;
        double sumAbsolute = 0.0;
        double radius = descriptor.nominalRadius();

        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                inside++;
                double base = semantic.elevationTendency().sample(position);
                double adjusted = continuous.sample(position);
                double delta = adjusted - base;
                if (Math.abs(delta) >= CHANGE_THRESHOLD) {
                    changed++;
                }
                maxLowering = Math.max(maxLowering, -delta);
                maxRaising = Math.max(maxRaising, delta);
                sumAbsolute += Math.abs(delta);
            }
        }
        return new RasterMetrics(
                inside,
                changed,
                inside == 0L ? 0.0 : (double) changed / inside,
                Math.max(0.0, maxLowering),
                Math.max(0.0, maxRaising),
                inside == 0L ? 0.0 : sumAbsolute / inside);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandContinuousHydrologicTerrainField continuous,
            SkyIslandChannelProfilePlan profiles,
            SkyIslandWaterbodyFootprintPlan waterbodies,
            RasterMetrics metrics) {
        BufferedImage image =
                new BufferedImage(PANEL_WIDTH, PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
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
                        "changed %.1f%%  max -%.3f / +%.3f  mean |d| %.4f",
                        100.0 * metrics.changedFraction(),
                        metrics.maxLowering(),
                        metrics.maxRaising(),
                        metrics.meanAbsoluteAdjustment()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "BEFORE", 0, MAP, 53);
        centered(g, "AFTER", MAP, MAP, 53);
        centered(g, "CHANGE", 2 * MAP, MAP, 53);

        renderElevation(g, descriptor, semantic, continuous, 0, true);
        renderElevation(g, descriptor, semantic, continuous, MAP, false);
        renderChange(g, descriptor, semantic, continuous, 2 * MAP);
        drawChannels(g, descriptor, profiles, MAP);
        drawChannels(g, descriptor, profiles, 2 * MAP);
        drawWater(g, descriptor, waterbodies, MAP);
        drawWater(g, descriptor, waterbodies, 2 * MAP);
        g.dispose();
        return image;
    }

    private static void renderElevation(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandContinuousHydrologicTerrainField continuous,
            int offsetX,
            boolean base) {
        g.setColor(new Color(248, 248, 248));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                double elevation = base
                        ? semantic.elevationTendency().sample(position)
                        : continuous.sample(position);
                int shade = (int) Math.round(35.0 + 205.0 * elevation);
                g.setColor(new Color(shade, shade, shade));
                g.fillRect(offsetX + px, HEADER + py, 1, 1);
            }
        }
    }

    private static void renderChange(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandContinuousHydrologicTerrainField continuous,
            int offsetX) {
        g.setColor(new Color(248, 248, 248));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                double delta = continuous.sample(position)
                        - semantic.elevationTendency().sample(position);
                Color color;
                if (delta <= -CHANGE_THRESHOLD) {
                    double strength = Math.min(
                            1.0,
                            -delta / SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING);
                    color = blend(Color.WHITE, new Color(45, 105, 190), 0.12 + 0.88 * strength);
                } else if (delta >= CHANGE_THRESHOLD) {
                    double strength = Math.min(
                            1.0,
                            delta / SkyIslandHydrologicTerrainSurfacePlanner.MAX_RAISING);
                    color = blend(Color.WHITE, new Color(225, 130, 35), 0.12 + 0.88 * strength);
                } else {
                    color = new Color(236, 236, 236);
                }
                g.setColor(color);
                g.fillRect(offsetX + px, HEADER + py, 1, 1);
            }
        }
    }

    private static void drawChannels(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandChannelProfilePlan profiles,
            int offsetX) {
        g.setColor(new Color(25, 85, 155));
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            g.drawLine(
                    offsetX + mapX(profile.segment().start(), descriptor.nominalRadius()),
                    HEADER + mapY(profile.segment().start(), descriptor.nominalRadius()),
                    offsetX + mapX(profile.segment().end(), descriptor.nominalRadius()),
                    HEADER + mapY(profile.segment().end(), descriptor.nominalRadius()));
        }
    }

    private static void drawWater(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyFootprintPlan waterbodies,
            int offsetX) {
        g.setColor(new Color(70, 200, 220));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = offsetX + mapX(cell.position(), descriptor.nominalRadius());
                int y = HEADER + mapY(cell.position(), descriptor.nominalRadius());
                g.fillOval(x - 1, y - 1, 3, 3);
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

    private record RasterMetrics(
            long insideSamples,
            long changedSamples,
            double changedFraction,
            double maxLowering,
            double maxRaising,
            double meanAbsoluteAdjustment) {}
}
