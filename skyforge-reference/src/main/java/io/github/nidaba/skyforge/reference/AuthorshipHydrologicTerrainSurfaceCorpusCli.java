package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfaceCell;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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

/** Generates deterministic AUTH-0015 before/after hydrologic terrain-surface evidence. */
public final class AuthorshipHydrologicTerrainSurfaceCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 176;
    private static final int HEADER = 62;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;

    private AuthorshipHydrologicTerrainSurfaceCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-hydrologic-terrain-surface-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);
        BufferedImage atlas = new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,activeCells,changedCells,loweredCells,raisedCells,maxLowering,maxRaising,meanAbsAdjustment\n");
        StringBuilder cellsCsv = new StringBuilder(
                "islandKey,cellIndex,baseElevation,adjustedElevation,netAdjustment,incisionLowering,depositionRaising,floodplainAdjustment,dropLowering\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandHydrologicTerrainSurfacePlan surface =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            BufferedImage panel = renderPanel(descriptor, surface, profiles, waterbodies);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());

            int x = (n % 2) * PANEL_WIDTH;
            int y = (n / 2) * PANEL_HEIGHT;
            ag.drawImage(panel, x, y, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(surface.cells().size()).append(',')
                    .append(surface.changedCellCount()).append(',')
                    .append(surface.loweredCellCount()).append(',')
                    .append(surface.raisedCellCount()).append(',')
                    .append(format(surface.maxLowering())).append(',')
                    .append(format(surface.maxRaising())).append(',')
                    .append(format(surface.meanAbsoluteAdjustment())).append('\n');

            for (SkyIslandHydrologicTerrainSurfaceCell cell : surface.cells()) {
                cellsCsv.append(key).append(',')
                        .append(cell.watershedCellIndex()).append(',')
                        .append(format(cell.baseElevationPotential())).append(',')
                        .append(format(cell.adjustedElevationPotential())).append(',')
                        .append(format(cell.netAdjustment())).append(',')
                        .append(format(cell.incisionLowering())).append(',')
                        .append(format(cell.depositionRaising())).append(',')
                        .append(format(cell.floodplainAdjustment())).append(',')
                        .append(format(cell.dropLowering())).append('\n');
            }
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("cells.csv"), cellsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0015</title>"
                        + "<h1>Hydrologically adjusted terrain surface</h1>"
                        + "<p>Each island is shown three ways: BEFORE is the original authored elevation; AFTER is the same coarse surface after accepted hydrologic shaping; CHANGE shows only the difference. "
                        + "In CHANGE, blue means terrain was lowered and orange means it was raised. Blue lines are accepted channels and cyan cells are retained standing water. "
                        + "All heights remain normalized semantic elevation potentials, not Minecraft Y or blocks.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"cells.csv\">cells.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainSurfacePlan surface,
            SkyIslandChannelProfilePlan profiles,
            SkyIslandWaterbodyFootprintPlan waterbodies) {
        BufferedImage image = new BufferedImage(PANEL_WIDTH, PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("key=" + descriptor.identity().islandKey() + " / " + descriptor.morphologyFamily().identifier(), 7, 17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                "changed=" + surface.changedCellCount()
                        + "  lower=" + surface.loweredCellCount()
                        + "  raise=" + surface.raisedCellCount()
                        + String.format(Locale.ROOT, "  max -%.3f / +%.3f", surface.maxLowering(), surface.maxRaising()),
                7,
                34);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "BEFORE", 0, MAP, 52);
        centered(g, "AFTER", MAP, MAP, 52);
        centered(g, "CHANGE", 2 * MAP, MAP, 52);

        renderElevation(g, descriptor, surface, 0, true);
        renderElevation(g, descriptor, surface, MAP, false);
        renderChange(g, descriptor, surface, 2 * MAP);
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
            SkyIslandHydrologicTerrainSurfacePlan surface,
            int offsetX,
            boolean base) {
        g.setColor(new Color(247, 247, 247));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        for (SkyIslandHydrologicTerrainSurfaceCell cell : surface.cells()) {
            double elevation = base ? cell.baseElevationPotential() : cell.adjustedElevationPotential();
            int shade = (int) Math.round(35.0 + 205.0 * elevation);
            g.setColor(new Color(shade, shade, shade));
            int x = offsetX + mapX(cell.position(), descriptor.nominalRadius());
            int y = HEADER + mapY(cell.position(), descriptor.nominalRadius());
            g.fillRect(x - 2, y - 2, 5, 5);
        }
    }

    private static void renderChange(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainSurfacePlan surface,
            int offsetX) {
        g.setColor(new Color(247, 247, 247));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        for (SkyIslandHydrologicTerrainSurfaceCell cell : surface.cells()) {
            double delta = cell.netAdjustment();
            Color color;
            if (delta < -1.0e-12) {
                double strength = Math.min(1.0, -delta / SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING);
                color = blend(Color.WHITE, new Color(45, 105, 190), 0.20 + 0.80 * strength);
            } else if (delta > 1.0e-12) {
                double strength = Math.min(1.0, delta / SkyIslandHydrologicTerrainSurfacePlanner.MAX_RAISING);
                color = blend(Color.WHITE, new Color(225, 130, 35), 0.20 + 0.80 * strength);
            } else {
                color = new Color(235, 235, 235);
            }
            g.setColor(color);
            int x = offsetX + mapX(cell.position(), descriptor.nominalRadius());
            int y = HEADER + mapY(cell.position(), descriptor.nominalRadius());
            g.fillRect(x - 2, y - 2, 5, 5);
        }
    }

    private static void drawChannels(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandChannelProfilePlan profiles,
            int offsetX) {
        g.setColor(new Color(30, 95, 165));
        g.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
        g.setColor(new Color(80, 205, 220));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = offsetX + mapX(cell.position(), descriptor.nominalRadius());
                int y = HEADER + mapY(cell.position(), descriptor.nominalRadius());
                g.fillRect(x - 1, y - 1, 3, 3);
            }
        }
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

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
