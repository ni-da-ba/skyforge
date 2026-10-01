package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelDrop;
import io.github.nidaba.skyforge.world.SkyIslandChannelDropKind;
import io.github.nidaba.skyforge.world.SkyIslandChannelDropPlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelDropPlanner;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCell;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlan;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0013 channel-drop review evidence. */
public final class AuthorshipChannelDropCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;

    private AuthorshipChannelDropCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-channel-drops-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 68), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,totalDrops,cascadeSteps,waterfalls,edgeFalls,maxDrop,maxPersistence,maxPlungePool\n");
        StringBuilder dropsCsv = new StringBuilder(
                "islandKey,kind,sourceCell,downstreamCell,dropPotential,dischargePotential,persistencePotential,plungePoolPotential\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandChannelDropPlan drops = SkyIslandChannelDropPlanner.plan(descriptor);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandRiparianCorridorPlan riparian = SkyIslandRiparianCorridorPlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, drops, profiles, riparian, waterbodies);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 68);
            ag.setColor(Color.BLACK);
            ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            ag.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 7, y + 18);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            ag.drawString(
                    "steps=" + drops.count(SkyIslandChannelDropKind.CASCADE_STEP)
                            + " falls=" + drops.count(SkyIslandChannelDropKind.WATERFALL)
                            + " edge=" + drops.count(SkyIslandChannelDropKind.EDGE_FALL),
                    x + 7,
                    y + 35);
            ag.drawString(
                    String.format(Locale.ROOT, "max drop=%.2f persist=%.2f pool=%.2f",
                            drops.maxDropPotential(),
                            drops.maxPersistencePotential(),
                            drops.maxPlungePoolPotential()),
                    x + 7,
                    y + 51);
            ag.drawImage(image, x, y + 68, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(drops.drops().size()).append(',')
                    .append(drops.count(SkyIslandChannelDropKind.CASCADE_STEP)).append(',')
                    .append(drops.count(SkyIslandChannelDropKind.WATERFALL)).append(',')
                    .append(drops.count(SkyIslandChannelDropKind.EDGE_FALL)).append(',')
                    .append(format(drops.maxDropPotential())).append(',')
                    .append(format(drops.maxPersistencePotential())).append(',')
                    .append(format(drops.maxPlungePoolPotential())).append('\n');

            for (SkyIslandChannelDrop drop : drops.drops()) {
                dropsCsv.append(key).append(',')
                        .append(drop.kind()).append(',')
                        .append(drop.sourceCellIndex()).append(',')
                        .append(drop.downstreamCellIndex()).append(',')
                        .append(format(drop.dropPotential())).append(',')
                        .append(format(drop.dischargePotential())).append(',')
                        .append(format(drop.persistencePotential())).append(',')
                        .append(format(drop.plungePoolPotential())).append('\n');
            }
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("drops.csv"), dropsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0013</title>"
                        + "<h1>Semantic channel drops</h1>"
                        + "<p>Orange circles: cascade steps. Magenta squares: interior waterfalls. Red diamonds: edge falls. "
                        + "Symbol size follows normalized drop potential. Pale gray lines are accepted AUTH-0012 channel profiles; "
                        + "pale green cells are AUTH-0011 riparian semantics and pale blue cells are standing water. "
                        + "Drop, persistence and plunge-pool values are realization potentials rather than block heights or fluid geometry.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"drops.csv\">drops.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelDropPlan drops,
            SkyIslandChannelProfilePlan profiles,
            SkyIslandRiparianCorridorPlan riparian,
            SkyIslandWaterbodyFootprintPlan waterbodies) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);
        double radius = descriptor.nominalRadius();

        g.setColor(new Color(224, 240, 220));
        for (SkyIslandRiparianCell cell : riparian.cells()) {
            int x = mapX(cell.position(), radius);
            int y = mapY(cell.position(), radius);
            g.fillRect(x - 2, y - 2, 5, 5);
        }
        g.setColor(new Color(199, 228, 246));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.fillRect(x - 2, y - 2, 5, 5);
            }
        }

        g.setColor(new Color(150, 158, 166));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            g.drawLine(
                    mapX(profile.segment().start(), radius),
                    mapY(profile.segment().start(), radius),
                    mapX(profile.segment().end(), radius),
                    mapY(profile.segment().end(), radius));
        }

        for (SkyIslandChannelDrop drop : drops.drops()) {
            int x = mapX(drop.position(), radius);
            int y = mapY(drop.position(), radius);
            int size = 6 + (int) Math.round(7.0 * drop.dropPotential());
            int half = size / 2;
            switch (drop.kind()) {
                case CASCADE_STEP -> {
                    g.setColor(new Color(230, 130, 35));
                    g.fillOval(x - half, y - half, size, size);
                }
                case WATERFALL -> {
                    g.setColor(new Color(180, 45, 155));
                    g.fillRect(x - half, y - half, size, size);
                }
                case EDGE_FALL -> {
                    g.setColor(new Color(205, 45, 45));
                    Polygon diamond = new Polygon(
                            new int[] {x, x + half, x, x - half},
                            new int[] {y - half, y, y + half, y},
                            4);
                    g.fillPolygon(diamond);
                }
            }
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.0f));
            g.drawOval(x - 1, y - 1, 2, 2);
        }
        g.dispose();
        return image;
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
