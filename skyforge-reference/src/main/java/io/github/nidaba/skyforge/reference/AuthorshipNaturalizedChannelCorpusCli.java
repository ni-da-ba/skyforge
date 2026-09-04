package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfileKind;
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
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0017 coarse-versus-naturalized centerline evidence. */
public final class AuthorshipNaturalizedChannelCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 230;
    private static final int HEADER = 62;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;

    private AuthorshipNaturalizedChannelCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-naturalized-channels-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);

        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,paths,alluvial,incised,cascade,totalSamples,maxDeviationSpacing,meanLengthRatio,maxLengthRatio\n");
        StringBuilder pathsCsv = new StringBuilder(
                "islandKey,sourceCell,downstreamCell,kind,role,streamOrder,points,chordLength,pathLength,lengthRatio,maxDeviation,maxDeviationSpacing\n");
        StringBuilder pointsCsv = new StringBuilder(
                "islandKey,sourceCell,downstreamCell,ordinal,x,z\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandNaturalizedChannelPlan plan =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);
            BufferedImage panel = renderPanel(descriptor, plan);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            int totalSamples = plan.paths().stream().mapToInt(path -> path.points().size()).sum();
            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(plan.paths().size()).append(',')
                    .append(plan.count(SkyIslandChannelProfileKind.ALLUVIAL)).append(',')
                    .append(plan.count(SkyIslandChannelProfileKind.INCISED)).append(',')
                    .append(plan.count(SkyIslandChannelProfileKind.CASCADE)).append(',')
                    .append(totalSamples).append(',')
                    .append(format(plan.maxChordDeviation() / plan.planningSpacing())).append(',')
                    .append(format(plan.meanLengthRatio())).append(',')
                    .append(format(plan.maxLengthRatio())).append('\n');

            for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
                var segment = path.profile().segment();
                pathsCsv.append(key).append(',')
                        .append(segment.sourceCellIndex()).append(',')
                        .append(segment.downstreamCellIndex()).append(',')
                        .append(path.profile().kind()).append(',')
                        .append(segment.role()).append(',')
                        .append(segment.streamOrder()).append(',')
                        .append(path.points().size()).append(',')
                        .append(format(path.chordLength())).append(',')
                        .append(format(path.pathLength())).append(',')
                        .append(format(path.lengthRatio())).append(',')
                        .append(format(path.maxChordDeviation())).append(',')
                        .append(format(path.maxChordDeviation() / plan.planningSpacing())).append('\n');

                for (int i = 0; i < path.points().size(); i++) {
                    SkyIslandLocalPosition point = path.points().get(i);
                    pointsCsv.append(key).append(',')
                            .append(segment.sourceCellIndex()).append(',')
                            .append(segment.downstreamCellIndex()).append(',')
                            .append(i).append(',')
                            .append(format(point.x())).append(',')
                            .append(format(point.z())).append('\n');
                }
            }
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("paths.csv"), pathsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("points.csv"), pointsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0017</title>"
                        + "<h1>Naturalized channel centerlines</h1>"
                        + "<p>COARSE shows the accepted AUTH-0007/0012 straight lattice segments in red. NATURALIZED shows AUTH-0017 sub-grid splines in blue. OVERLAY shows coarse gray plus naturalized blue; black dots are the original graph nodes, which remain fixed. Background terrain is the accepted AUTH-0016 continuous hydrologic surface.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"paths.csv\">paths.csv</a> · <a href=\"points.csv\">points.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPlan plan) {
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
                        "paths=%d  max dev=%.2f cells  mean length x%.3f",
                        plan.paths().size(),
                        plan.maxChordDeviation() / plan.planningSpacing(),
                        plan.meanLengthRatio()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "COARSE", 0, MAP, 53);
        centered(g, "NATURALIZED", MAP, MAP, 53);
        centered(g, "OVERLAY", 2 * MAP, MAP, 53);

        renderTerrain(g, descriptor, 0);
        renderTerrain(g, descriptor, MAP);
        renderTerrain(g, descriptor, 2 * MAP);
        drawCoarse(g, descriptor, plan, 0, new Color(175, 65, 55), 1.4f);
        drawNaturalized(g, descriptor, plan, MAP, new Color(30, 100, 180), 1.7f);
        drawCoarse(g, descriptor, plan, 2 * MAP, new Color(125, 125, 125), 1.05f);
        drawNaturalized(g, descriptor, plan, 2 * MAP, new Color(25, 100, 190), 1.5f);
        drawNodes(g, descriptor, plan, 2 * MAP);
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
                int shade = (int) Math.round(50.0 + 190.0 * elevation);
                g.setColor(new Color(shade, shade, shade));
                g.fillRect(offsetX + px, HEADER + py, 1, 1);
            }
        }
    }

    private static void drawCoarse(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPlan plan,
            int offsetX,
            Color color,
            float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
            var segment = path.profile().segment();
            g.drawLine(
                    offsetX + mapX(segment.start(), descriptor.nominalRadius()),
                    HEADER + mapY(segment.start(), descriptor.nominalRadius()),
                    offsetX + mapX(segment.end(), descriptor.nominalRadius()),
                    HEADER + mapY(segment.end(), descriptor.nominalRadius()));
        }
    }

    private static void drawNaturalized(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPlan plan,
            int offsetX,
            Color color,
            float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
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

    private static void drawNodes(
            Graphics2D g,
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPlan plan,
            int offsetX) {
        g.setColor(new Color(20, 20, 20));
        for (SkyIslandNaturalizedChannelPath path : plan.paths()) {
            SkyIslandLocalPosition point = path.points().getFirst();
            int x = offsetX + mapX(point, descriptor.nominalRadius());
            int y = HEADER + mapY(point, descriptor.nominalRadius());
            g.fillOval(x - 1, y - 1, 3, 3);
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
