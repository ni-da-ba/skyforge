package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandChannelRole;
import io.github.nidaba.skyforge.world.SkyIslandChannelSegment;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeature;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeatureKind;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0007 channel-hierarchy evidence. */
public final class AuthorshipChannelHierarchyCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;

    private AuthorshipChannelHierarchyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-channel-hierarchy-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 58), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,segments,maxOrder,headwaters,tributaries,trunks,retainedWater,waterfalls\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandChannelNetworkPlan network = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandHydrologicFeaturePlan hydrology = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
            BufferedImage image = render(descriptor, network, hydrology);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 58);
            ag.setColor(Color.BLACK);
            ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            ag.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 19);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            ag.drawString(
                    "order=" + network.maxStreamOrder()
                            + " H=" + network.count(SkyIslandChannelRole.HEADWATER)
                            + " T=" + network.count(SkyIslandChannelRole.TRIBUTARY)
                            + " trunk=" + network.count(SkyIslandChannelRole.TRUNK),
                    x + 8,
                    y + 38);
            ag.drawImage(image, x, y + 58, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(network.segments().size()).append(',')
                    .append(network.maxStreamOrder()).append(',')
                    .append(network.count(SkyIslandChannelRole.HEADWATER)).append(',')
                    .append(network.count(SkyIslandChannelRole.TRIBUTARY)).append(',')
                    .append(network.count(SkyIslandChannelRole.TRUNK)).append(',')
                    .append(hydrology.count(SkyIslandHydrologicFeatureKind.RETAINED_WATER)).append(',')
                    .append(hydrology.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL)).append('\n');
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0007</title>"
                        + "<h1>Channel hierarchy</h1>"
                        + "<p>Light cyan: headwaters. Medium blue: tributaries. Dark blue: trunks. "
                        + "Line width follows normalized corridor scale. Blue circles: retained water. "
                        + "Orange circles: edge discharge/waterfall anchors.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelNetworkPlan network,
            SkyIslandHydrologicFeaturePlan hydrology) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);

        drawRole(g, descriptor, network, SkyIslandChannelRole.HEADWATER, new Color(95, 190, 220));
        drawRole(g, descriptor, network, SkyIslandChannelRole.TRIBUTARY, new Color(35, 125, 195));
        drawRole(g, descriptor, network, SkyIslandChannelRole.TRUNK, new Color(20, 55, 125));

        double radius = descriptor.nominalRadius();
        for (SkyIslandHydrologicFeature feature : hydrology.features()) {
            int x = mapX(feature.position(), radius);
            int y = mapY(feature.position(), radius);
            if (feature.kind() == SkyIslandHydrologicFeatureKind.RETAINED_WATER) {
                g.setColor(new Color(35, 70, 190));
                g.fillOval(x - 5, y - 5, 10, 10);
            } else if (feature.kind() == SkyIslandHydrologicFeatureKind.EDGE_WATERFALL) {
                g.setColor(new Color(220, 110, 35));
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }
        g.dispose();
        return image;
    }

    private static void drawRole(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandChannelNetworkPlan network,
            SkyIslandChannelRole role,
            Color color) {
        double radius = descriptor.nominalRadius();
        g.setColor(color);
        for (SkyIslandChannelSegment segment : network.segments()) {
            if (segment.role() != role) {
                continue;
            }
            float width = (float) (1.0 + 4.0 * segment.corridorScale());
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(
                    mapX(segment.start(), radius),
                    mapY(segment.start(), radius),
                    mapX(segment.end(), radius),
                    mapY(segment.end(), radius));
        }
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }
}
