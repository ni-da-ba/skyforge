package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandChannelSegment;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCell;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlan;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlanner;
import io.github.nidaba.skyforge.world.SkyIslandRiparianKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMargin;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginPlanner;
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

/** Generates deterministic AUTH-0011 riparian-corridor evidence. */
public final class AuthorshipRiparianCorridorCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private static final List<Long> KEYS = List.of(77L, 118L, 241L, 512L, 811L, 83L);

    private AuthorshipRiparianCorridorCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-riparian-corridors-v1");
        Files.createDirectories(out);

        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 64), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,segments,riparianCells,transitions,saturatedRiparian,distance1,distance2,waterCells,waterbodyMarginCells,maxRiparianPotential\n");
        StringBuilder details = new StringBuilder(
                "islandKey,morphology,watershedCell,kind,channelSource,channelDownstream,channelRole,streamOrder,channelDistance,channelInfluence,saturationPotential,retentionPotential,riparianPotential\n");

        for (int n = 0; n < KEYS.size(); n++) {
            long key = KEYS.get(n);
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandRiparianCorridorPlan corridor = SkyIslandRiparianCorridorPlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

            BufferedImage image = render(descriptor, channels, corridor, waterbodies, margins, watershed);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int waterCells = waterbodies.footprints().stream()
                    .mapToInt(SkyIslandWaterbodyFootprint::inundatedCellCount)
                    .sum();
            long distance1 = corridor.cells().stream().filter(cell -> cell.channelDistance() == 1).count();
            long distance2 = corridor.cells().stream().filter(cell -> cell.channelDistance() == 2).count();

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 64);
            ag.setColor(Color.BLACK);
            ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            ag.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 18);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            ag.drawString(
                    "segments=" + channels.segments().size()
                            + " riparian=" + corridor.cellCount()
                            + " R=" + corridor.count(SkyIslandRiparianKind.RIPARIAN_TRANSITION)
                            + " S=" + corridor.count(SkyIslandRiparianKind.SATURATED_RIPARIAN),
                    x + 8,
                    y + 38);
            ag.drawImage(image, x, y + 64, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(channels.segments().size()).append(',')
                    .append(corridor.cellCount()).append(',')
                    .append(corridor.count(SkyIslandRiparianKind.RIPARIAN_TRANSITION)).append(',')
                    .append(corridor.count(SkyIslandRiparianKind.SATURATED_RIPARIAN)).append(',')
                    .append(distance1).append(',')
                    .append(distance2).append(',')
                    .append(waterCells).append(',')
                    .append(margins.marginCellCount()).append(',')
                    .append(format(corridor.maxRiparianPotential())).append('\n');

            for (SkyIslandRiparianCell cell : corridor.cells()) {
                details.append(key).append(',')
                        .append(descriptor.morphologyFamily().identifier()).append(',')
                        .append(cell.watershedCellIndex()).append(',')
                        .append(cell.kind()).append(',')
                        .append(cell.channelSourceCellIndex()).append(',')
                        .append(cell.channelDownstreamCellIndex()).append(',')
                        .append(cell.channelRole()).append(',')
                        .append(cell.streamOrder()).append(',')
                        .append(cell.channelDistance()).append(',')
                        .append(format(cell.channelInfluence())).append(',')
                        .append(format(cell.saturationPotential())).append(',')
                        .append(format(cell.retentionPotential())).append(',')
                        .append(format(cell.riparianPotential())).append('\n');
            }
        }

        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("cells.csv"), details.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0011</title>"
                        + "<h1>Semantic riparian corridor planning</h1>"
                        + "<p>Gray lines: accepted channel network. Gold cells: riparian transition. "
                        + "Green cells: saturated riparian. Pale blue: accepted standing water. "
                        + "Pale teal: AUTH-0010 waterbody margins, which are reserved from channel-corridor ownership. "
                        + "These are coarse semantic planning cells, not Minecraft river widths, banks, or biome boundaries.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> | <a href=\"cells.csv\">cells.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelNetworkPlan channels,
            SkyIslandRiparianCorridorPlan corridor,
            SkyIslandWaterbodyFootprintPlan waterbodies,
            SkyIslandWaterbodyMarginPlan margins,
            SkyIslandWatershedPlan watershed) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);

        double radius = descriptor.nominalRadius();
        int cellSize = Math.max(4, (int) Math.ceil(MAP / (double) watershed.gridSize()) + 1);

        g.setColor(new Color(195, 225, 245));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                fillCell(g, cell.position(), radius, cellSize);
            }
        }

        g.setColor(new Color(185, 230, 215));
        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                fillCell(g, cell.position(), radius, cellSize);
            }
        }

        for (SkyIslandRiparianCell cell : corridor.cells()) {
            g.setColor(cell.kind() == SkyIslandRiparianKind.SATURATED_RIPARIAN
                    ? new Color(55, 145, 85)
                    : new Color(205, 165, 75));
            fillCell(g, cell.position(), radius, cellSize);
        }

        g.setColor(new Color(70, 70, 70));
        for (SkyIslandChannelSegment segment : channels.segments()) {
            float width = (float) (1.0 + 2.2 * segment.corridorScale());
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(
                    mapX(segment.start(), radius),
                    mapY(segment.start(), radius),
                    mapX(segment.end(), radius),
                    mapY(segment.end(), radius));
        }
        g.dispose();
        return image;
    }

    private static void fillCell(Graphics2D g, SkyIslandLocalPosition position, double radius, int cellSize) {
        int x = mapX(position, radius);
        int y = mapY(position, radius);
        g.fillRect(x - cellSize / 2, y - cellSize / 2, cellSize, cellSize);
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
