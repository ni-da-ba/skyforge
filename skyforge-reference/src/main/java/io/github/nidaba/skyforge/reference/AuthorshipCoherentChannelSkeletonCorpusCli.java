package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelComponent;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelPlanner;
import io.github.nidaba.skyforge.world.SkyIslandContinuousHydrologicTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlan;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlanner;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0018 visible-channel coherence evidence. */
public final class AuthorshipCoherentChannelSkeletonCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 230;
    private static final int HEADER = 62;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;

    private AuthorshipCoherentChannelSkeletonCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-coherent-channel-skeleton-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);

        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,sourceComponents,retainedComponents,prunedComponents,sourceReaches,retainedReaches,retainedReachFraction,minTerminalSeparationCells\n");
        StringBuilder componentsCsv = new StringBuilder(
                "islandKey,terminalCell,terminalX,terminalZ,terminalRelativeDischarge,maxStreamOrder,reaches\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandNaturalizedChannelPlan naturalized =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);
            SkyIslandCoherentChannelPlan coherent =
                    SkyIslandCoherentChannelPlanner.plan(descriptor);

            BufferedImage panel = renderPanel(descriptor, naturalized, coherent);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            int sourceReaches = naturalized.paths().size();
            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(coherent.sourceComponentCount()).append(',')
                    .append(coherent.retainedComponentCount()).append(',')
                    .append(coherent.prunedComponentCount()).append(',')
                    .append(sourceReaches).append(',')
                    .append(coherent.retainedReachCount()).append(',')
                    .append(format(sourceReaches == 0 ? 0.0
                            : (double) coherent.retainedReachCount() / sourceReaches)).append(',')
                    .append(format(SkyIslandCoherentChannelPlanner.MIN_TERMINAL_SEPARATION_CELLS))
                    .append('\n');

            for (SkyIslandCoherentChannelComponent component : coherent.retainedComponents()) {
                componentsCsv.append(key).append(',')
                        .append(component.terminalCellIndex()).append(',')
                        .append(format(component.terminalPosition().x())).append(',')
                        .append(format(component.terminalPosition().z())).append(',')
                        .append(format(component.terminalRelativeDischarge())).append(',')
                        .append(component.maxStreamOrder()).append(',')
                        .append(component.reachCount()).append('\n');
            }
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("components.csv"), componentsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0018</title>"
                        + "<h1>Coherent visible-channel skeleton</h1>"
                        + "<p>ALL shows the complete accepted AUTH-0017 naturalized channel set. COHERENT shows only components retained by AUTH-0018. OVERLAY renders retained components in blue and suppressed spatially redundant components in pale red; black circles mark retained terminal nodes. Watershed routing itself is unchanged.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"components.csv\">components.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPlan naturalized,
            SkyIslandCoherentChannelPlan coherent) {
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
                        "components %d -> %d   reaches %d -> %d",
                        coherent.sourceComponentCount(),
                        coherent.retainedComponentCount(),
                        naturalized.paths().size(),
                        coherent.retainedReachCount()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "ALL", 0, MAP, 53);
        centered(g, "COHERENT", MAP, MAP, 53);
        centered(g, "OVERLAY", 2 * MAP, MAP, 53);

        renderTerrain(g, descriptor, 0);
        renderTerrain(g, descriptor, MAP);
        renderTerrain(g, descriptor, 2 * MAP);

        Set<Integer> retainedSources = new HashSet<>();
        for (var profile : coherent.profiles()) {
            retainedSources.add(profile.segment().sourceCellIndex());
        }
        Map<Integer, SkyIslandNaturalizedChannelPath> bySource = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : naturalized.paths()) {
            bySource.put(path.profile().segment().sourceCellIndex(), path);
        }

        drawPaths(g, descriptor, naturalized.paths(), 0, new Color(75, 105, 135), 1.25f);
        drawPaths(
                g,
                descriptor,
                naturalized.paths().stream()
                        .filter(path -> retainedSources.contains(path.profile().segment().sourceCellIndex()))
                        .toList(),
                MAP,
                new Color(30, 100, 185),
                1.65f);

        drawPaths(
                g,
                descriptor,
                naturalized.paths().stream()
                        .filter(path -> !retainedSources.contains(path.profile().segment().sourceCellIndex()))
                        .toList(),
                2 * MAP,
                new Color(220, 145, 140),
                1.05f);
        drawPaths(
                g,
                descriptor,
                naturalized.paths().stream()
                        .filter(path -> retainedSources.contains(path.profile().segment().sourceCellIndex()))
                        .toList(),
                2 * MAP,
                new Color(25, 95, 190),
                1.7f);

        g.setColor(new Color(15, 15, 15));
        for (SkyIslandCoherentChannelComponent component : coherent.retainedComponents()) {
            int x = 2 * MAP + mapX(component.terminalPosition(), descriptor.nominalRadius());
            int y = HEADER + mapY(component.terminalPosition(), descriptor.nominalRadius());
            g.fillOval(x - 2, y - 2, 5, 5);
        }

        g.dispose();
        return image;
    }

    private static void renderTerrain(Graphics2D g, SkyIslandDescriptor descriptor, int offsetX) {
        g.setColor(new Color(248, 248, 248));
        g.fillRect(offsetX, HEADER, MAP, MAP);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandContinuousHydrologicTerrainField terrain =
                SkyIslandContinuousHydrologicTerrainField.create(descriptor);
        double radius = descriptor.nominalRadius();

        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    continue;
                }
                double elevation = terrain.sample(position);
                int shade = (int) Math.round(55.0 + 185.0 * elevation);
                g.setColor(new Color(shade, shade, shade));
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

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
