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
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMargin;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginKind;
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
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0010 retained-waterbody margin evidence. */
public final class AuthorshipWaterbodyMarginCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private static final List<Long> KEYS = List.of(83L, 77L, 118L, 241L, 512L, 811L);

    private AuthorshipWaterbodyMarginCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-waterbody-margins-v1");
        Files.createDirectories(out);

        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 64), BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,bodies,waterCells,marginCells,saturatedFringe,shoreTransitions,maxMarginPotential\n");
        StringBuilder details = new StringBuilder(
                "islandKey,morphology,ordinal,sourceSinks,waterCells,marginCells,saturatedFringe,shoreTransitions,meanMarginPotential,maxMarginPotential\n");

        for (int n = 0; n < KEYS.size(); n++) {
            long key = KEYS.get(n);
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandWaterbodyFootprintPlan footprints = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);
            SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, footprints, margins, channels, watershed);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int waterCells = footprints.footprints().stream()
                    .mapToInt(SkyIslandWaterbodyFootprint::inundatedCellCount)
                    .sum();
            int marginCells = margins.marginCellCount();
            long saturated = margins.count(SkyIslandWaterbodyMarginKind.SATURATED_FRINGE);
            long transition = margins.count(SkyIslandWaterbodyMarginKind.SHORE_TRANSITION);
            double maxPotential = margins.margins().stream()
                    .mapToDouble(SkyIslandWaterbodyMargin::maxMarginPotential)
                    .max().orElse(0.0);

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 64);
            atlasGraphics.setColor(Color.BLACK);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            atlasGraphics.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 18);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            atlasGraphics.drawString(
                    "bodies=" + footprints.footprints().size()
                            + " water=" + waterCells
                            + " margin=" + marginCells
                            + " sat=" + saturated
                            + " shore=" + transition,
                    x + 8,
                    y + 38);
            atlasGraphics.drawImage(image, x, y + 64, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(footprints.footprints().size()).append(',')
                    .append(waterCells).append(',')
                    .append(marginCells).append(',')
                    .append(saturated).append(',')
                    .append(transition).append(',')
                    .append(format(maxPotential)).append('\n');

            for (int ordinal = 0; ordinal < margins.margins().size(); ordinal++) {
                SkyIslandWaterbodyMargin margin = margins.margins().get(ordinal);
                String sourceSinks = margin.footprint().sourceCandidates().stream()
                        .map(candidate -> Integer.toString(candidate.sinkCellIndex()))
                        .collect(Collectors.joining("|"));
                double mean = margin.cells().stream()
                        .mapToDouble(SkyIslandWaterbodyMarginCell::marginPotential)
                        .average().orElse(0.0);
                details.append(key).append(',')
                        .append(descriptor.morphologyFamily().identifier()).append(',')
                        .append(ordinal).append(',')
                        .append(sourceSinks).append(',')
                        .append(margin.footprint().inundatedCellCount()).append(',')
                        .append(margin.cells().size()).append(',')
                        .append(margin.count(SkyIslandWaterbodyMarginKind.SATURATED_FRINGE)).append(',')
                        .append(margin.count(SkyIslandWaterbodyMarginKind.SHORE_TRANSITION)).append(',')
                        .append(format(mean)).append(',')
                        .append(format(margin.maxMarginPotential())).append('\n');
            }
        }

        atlasGraphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("margins.csv"), details.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0010</title>"
                        + "<h1>Retained waterbody margin planning</h1>"
                        + "<p>Gray lines: accepted channel network. Blue/teal cells: accepted AUTH-0009 water footprint. "
                        + "Gold cells: dry shore transition. Green cells: dry saturated fringe. Black dots: retained source anchors. "
                        + "Margins are coarse semantic transition zones, not Minecraft biome boundaries or final shore geometry.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> | <a href=\"margins.csv\">margins.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyFootprintPlan footprints,
            SkyIslandWaterbodyMarginPlan margins,
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
            Color fill = bodyColor(footprint);
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.setColor(fill);
                g.fillRect(x - cellSize / 2, y - cellSize / 2, cellSize, cellSize);
            }
            for (var source : footprint.sourceCandidates()) {
                int x = mapX(source.anchor(), radius);
                int y = mapY(source.anchor(), radius);
                g.setColor(Color.BLACK);
                g.fillOval(x - 4, y - 4, 8, 8);
            }
        }

        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.setColor(cell.kind() == SkyIslandWaterbodyMarginKind.SATURATED_FRINGE
                        ? new Color(55, 130, 80)
                        : new Color(200, 155, 65));
                g.fillRect(x - cellSize / 2, y - cellSize / 2, cellSize, cellSize);
            }
        }
        g.dispose();
        return image;
    }

    private static Color bodyColor(SkyIslandWaterbodyFootprint footprint) {
        if (footprint.hasMixedKinds()) {
            return new Color(150, 90, 170);
        }
        SkyIslandWaterbodyKind kind = footprint.sourceCandidates().getFirst().kind();
        return switch (kind) {
            case WETLAND -> new Color(75, 155, 150);
            case POND -> new Color(75, 170, 210);
            case LAKE -> new Color(55, 105, 190);
        };
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / (2.0 * radius) + 0.5) * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((0.5 - position.z() / (2.0 * radius)) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
