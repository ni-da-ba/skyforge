package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandChannelSegment;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlan;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner;
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

/** Generates deterministic AUTH-0009 retained-waterbody footprint evidence. */
public final class AuthorshipWaterbodyFootprintCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private static final List<Long> KEYS = List.of(83L, 77L, 118L, 241L, 512L, 811L);

    private AuthorshipWaterbodyFootprintCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-waterbody-footprints-v1");
        Files.createDirectories(out);

        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 64), BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,footprints,inundatedCells,shorelineCells,maxInundatedDepressionFraction,maxDepthPotential,maxFillFraction\n");
        StringBuilder details = new StringBuilder(
                "islandKey,morphology,ordinal,kind,sinkCell,catchmentCells,depressionCells,inundatedCells,inundatedDepressionFraction,shorelineCells,fillFraction,waterSurfacePotential,spillSurfacePotential,maxDepthPotential\n");

        for (int n = 0; n < KEYS.size(); n++) {
            long key = KEYS.get(n);
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandWaterbodyFootprintPlan footprints = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, footprints, channels, watershed);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int inundatedCells = footprints.footprints().stream()
                    .mapToInt(SkyIslandWaterbodyFootprint::inundatedCellCount)
                    .sum();
            long shorelineCells = footprints.footprints().stream()
                    .mapToLong(SkyIslandWaterbodyFootprint::shorelineCellCount)
                    .sum();
            double maxInundatedFraction = footprints.footprints().stream()
                    .mapToDouble(SkyIslandWaterbodyFootprint::inundatedDepressionFraction)
                    .max().orElse(0.0);
            double maxDepth = footprints.footprints().stream()
                    .mapToDouble(SkyIslandWaterbodyFootprint::maxDepthPotential)
                    .max().orElse(0.0);
            double maxFill = footprints.footprints().stream()
                    .mapToDouble(SkyIslandWaterbodyFootprint::fillFraction)
                    .max().orElse(0.0);

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 64);
            atlasGraphics.setColor(Color.BLACK);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            atlasGraphics.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 18);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            atlasGraphics.drawString(
                    "footprints=" + footprints.footprints().size()
                            + " wetCells=" + inundatedCells
                            + " wet/depression=" + format(maxInundatedFraction)
                            + " depth=" + format(maxDepth),
                    x + 8,
                    y + 38);
            atlasGraphics.drawImage(image, x, y + 64, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(footprints.footprints().size()).append(',')
                    .append(inundatedCells).append(',')
                    .append(shorelineCells).append(',')
                    .append(format(maxInundatedFraction)).append(',')
                    .append(format(maxDepth)).append(',')
                    .append(format(maxFill)).append('\n');

            for (int ordinal = 0; ordinal < footprints.footprints().size(); ordinal++) {
                SkyIslandWaterbodyFootprint footprint = footprints.footprints().get(ordinal);
                details.append(key).append(',')
                        .append(descriptor.morphologyFamily().identifier()).append(',')
                        .append(ordinal).append(',')
                        .append(footprint.candidate().kind()).append(',')
                        .append(footprint.candidate().sinkCellIndex()).append(',')
                        .append(footprint.candidate().catchmentCellCount()).append(',')
                        .append(footprint.depressionCellCount()).append(',')
                        .append(footprint.inundatedCellCount()).append(',')
                        .append(format(footprint.inundatedDepressionFraction())).append(',')
                        .append(footprint.shorelineCellCount()).append(',')
                        .append(format(footprint.fillFraction())).append(',')
                        .append(format(footprint.waterSurfacePotential())).append(',')
                        .append(format(footprint.spillSurfacePotential())).append(',')
                        .append(format(footprint.maxDepthPotential())).append('\n');
            }
        }

        atlasGraphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("footprints.csv"), details.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0009</title>"
                        + "<h1>Retained waterbody footprint planning</h1>"
                        + "<p>Gray lines: accepted channel network. Colored cells: connected semantic inundation footprint inside the common priority-flood depression. "
                        + "Cyan: pond, dark blue: lake, teal: wetland. Dark cell borders mark the coarse shoreline. "
                        + "Black dots mark retained-sink anchors. Footprints use normalized semantic spill surfaces; "
                        + "they are not Minecraft block shorelines or literal world Y water levels.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> | <a href=\"footprints.csv\">footprints.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyFootprintPlan footprints,
            SkyIslandChannelNetworkPlan channels,
            SkyIslandWatershedPlan watershed) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);

        double radius = descriptor.nominalRadius();
        g.setColor(new Color(190, 190, 190));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandChannelSegment segment : channels.segments()) {
            g.drawLine(
                    mapX(segment.start(), radius),
                    mapY(segment.start(), radius),
                    mapX(segment.end(), radius),
                    mapY(segment.end(), radius));
        }

        int cellSize = Math.max(4, (int) Math.ceil(MAP / (double) watershed.gridSize()) + 1);
        for (SkyIslandWaterbodyFootprint footprint : footprints.footprints()) {
            Color fill = lightColor(footprint.candidate().kind());
            Color edge = darkColor(footprint.candidate().kind());
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.setColor(fill);
                g.fillRect(x - cellSize / 2, y - cellSize / 2, cellSize, cellSize);
                if (cell.shoreline()) {
                    g.setColor(edge);
                    g.drawRect(x - cellSize / 2, y - cellSize / 2, cellSize, cellSize);
                }
            }
            int anchorX = mapX(footprint.candidate().anchor(), radius);
            int anchorY = mapY(footprint.candidate().anchor(), radius);
            g.setColor(Color.BLACK);
            g.fillOval(anchorX - 3, anchorY - 3, 6, 6);
        }

        g.dispose();
        return image;
    }

    private static Color lightColor(SkyIslandWaterbodyKind kind) {
        return switch (kind) {
            case POND -> new Color(90, 195, 225);
            case LAKE -> new Color(80, 115, 205);
            case WETLAND -> new Color(90, 180, 165);
        };
    }

    private static Color darkColor(SkyIslandWaterbodyKind kind) {
        return switch (kind) {
            case POND -> new Color(25, 125, 165);
            case LAKE -> new Color(25, 55, 145);
            case WETLAND -> new Color(25, 115, 105);
        };
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
