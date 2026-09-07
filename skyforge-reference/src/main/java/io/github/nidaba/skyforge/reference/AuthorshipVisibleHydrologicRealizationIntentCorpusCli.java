package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.*;
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
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** Generates AUTH-0086 visible-hydrology projection proof evidence. */
public final class AuthorshipVisibleHydrologicRealizationIntentCorpusCli {
    public static final String EVIDENCE_ID = "authorship-visible-hydrologic-realization-intent-v1";

    private static final long SEED = 0x534B59464F524745L;
    private static final List<Long> KEYS = List.of(77L, 118L, 241L, 512L, 811L, 83L);
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int PANEL_W = WIDTH / COLS;
    private static final int PANEL_H = HEIGHT / ROWS;
    private static final int MAP = 274;

    private AuthorshipVisibleHydrologicRealizationIntentCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipVisibleHydrologicRealizationIntentCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,sourceChannels,intentChannels,sourceWaterbodies,intentWaterbodies,"
                        + "sourceDrops,intentDrops,cascades,waterfalls,edgeDischarge,riparianCells,marginCells\n");
        StringBuilder provenance = new StringBuilder(
                "islandKey,intentKind,ordinal,sourceCell,downstreamCell,detail\n");

        for (int n = 0; n < KEYS.size(); n++) {
            long key = KEYS.get(n);
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandVisibleHydrologicRealizationPlan plan =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);

            int x = (n % COLS) * PANEL_W;
            int y = (n / COLS) * PANEL_H;
            renderPanel(g, x, y, descriptor, plan);

            int sourceChannels = plan.coherentHydrology().naturalizedChannels().paths().size();
            int sourceWaterbodies = plan.waterbodies().footprints().size();
            int sourceDrops = plan.coherentHydrology().drops().drops().size();
            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(sourceChannels).append(',')
                    .append(plan.channels().size()).append(',')
                    .append(sourceWaterbodies).append(',')
                    .append(plan.retainedWater().size()).append(',')
                    .append(sourceDrops).append(',')
                    .append(plan.drops().size()).append(',')
                    .append(plan.count(SkyIslandVisibleHydrologicRealizationKind.CASCADE)).append(',')
                    .append(plan.count(SkyIslandVisibleHydrologicRealizationKind.WATERFALL)).append(',')
                    .append(plan.count(SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)).append(',')
                    .append(plan.coherentHydrology().riparian().cellCount()).append(',')
                    .append(plan.waterbodyMargins().marginCellCount()).append('\n');

            writeProvenance(provenance, key, plan);
        }

        g.dispose();
        ImageIO.write(atlas, "png", output.resolve("atlas.png").toFile());
        Files.writeString(output.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("provenance.csv"), provenance, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"), indexHtml(), StandardCharsets.UTF_8);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static void renderPanel(
            Graphics2D g,
            int panelX,
            int panelY,
            SkyIslandDescriptor descriptor,
            SkyIslandVisibleHydrologicRealizationPlan plan) {
        g.setColor(new Color(247, 244, 236));
        g.fillRect(panelX + 6, panelY + 6, PANEL_W - 12, PANEL_H - 12);
        g.setColor(new Color(185, 185, 185));
        g.drawRect(panelX + 6, panelY + 6, PANEL_W - 12, PANEL_H - 12);

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(
                "key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                panelX + 16,
                panelY + 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                "channel " + plan.channels().size()
                        + "  waterbody " + plan.retainedWater().size()
                        + "  drops " + plan.drops().size(),
                panelX + 16,
                panelY + 42);
        g.drawString(
                "source=intent one-for-one; margins/riparian remain dry provenance",
                panelX + 16,
                panelY + 56);

        int mapX = panelX + (PANEL_W - MAP) / 2;
        int mapY = panelY + 70;
        g.setColor(Color.WHITE);
        g.fillRect(mapX, mapY, MAP, MAP);
        g.setColor(new Color(205, 205, 205));
        g.drawRect(mapX, mapY, MAP, MAP);
        double radius = descriptor.nominalRadius();

        g.setColor(new Color(224, 205, 165));
        for (SkyIslandVisibleRetainedWaterIntent intent : plan.retainedWater()) {
            for (SkyIslandWaterbodyMarginCell cell : intent.margin().cells()) {
                int x = px(cell.position(), radius, mapX);
                int y = py(cell.position(), radius, mapY);
                g.fillRect(x - 2, y - 2, 5, 5);
            }
        }

        g.setColor(new Color(120, 190, 224));
        for (SkyIslandVisibleRetainedWaterIntent intent : plan.retainedWater()) {
            for (SkyIslandWaterbodyFootprintCell cell : intent.footprint().cells()) {
                int x = px(cell.position(), radius, mapX);
                int y = py(cell.position(), radius, mapY);
                g.fillRect(x - 3, y - 3, 7, 7);
                if (cell.shoreline()) {
                    g.setColor(new Color(45, 105, 160));
                    g.drawRect(x - 3, y - 3, 7, 7);
                    g.setColor(new Color(120, 190, 224));
                }
            }
        }

        g.setColor(new Color(150, 205, 150));
        for (SkyIslandVisibleChannelWaterIntent intent : plan.channels()) {
            for (SkyIslandRiparianCell cell : intent.riparianCells()) {
                int x = px(cell.position(), radius, mapX);
                int y = py(cell.position(), radius, mapY);
                g.fillOval(x - 2, y - 2, 4, 4);
            }
        }

        g.setColor(new Color(35, 105, 190));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandVisibleChannelWaterIntent intent : plan.channels()) {
            List<SkyIslandLocalPosition> points = intent.path().points();
            for (int i = 1; i < points.size(); i++) {
                g.drawLine(
                        px(points.get(i - 1), radius, mapX),
                        py(points.get(i - 1), radius, mapY),
                        px(points.get(i), radius, mapX),
                        py(points.get(i), radius, mapY));
            }
        }

        for (SkyIslandVisibleDropWaterIntent intent : plan.drops()) {
            SkyIslandChannelDrop drop = intent.drop();
            int x = px(drop.position(), radius, mapX);
            int y = py(drop.position(), radius, mapY);
            switch (intent.kind()) {
                case CASCADE -> {
                    g.setColor(new Color(225, 135, 35));
                    g.fillOval(x - 4, y - 4, 8, 8);
                }
                case WATERFALL -> {
                    g.setColor(new Color(170, 55, 155));
                    g.fillRect(x - 4, y - 4, 8, 8);
                }
                case EDGE_DISCHARGE -> {
                    g.setColor(new Color(195, 45, 45));
                    Polygon diamond = new Polygon(
                            new int[] {x, x + 5, x, x - 5},
                            new int[] {y - 5, y, y + 5, y},
                            4);
                    g.fillPolygon(diamond);
                }
                case CHANNEL_WATER, RETAINED_WATER ->
                        throw new IllegalStateException("drop intent mapped to non-drop realization kind");
            }
        }
    }

    private static void writeProvenance(
            StringBuilder csv,
            long key,
            SkyIslandVisibleHydrologicRealizationPlan plan) {
        for (int i = 0; i < plan.channels().size(); i++) {
            SkyIslandVisibleChannelWaterIntent intent = plan.channels().get(i);
            SkyIslandChannelSegment segment = intent.path().profile().segment();
            csv.append(key).append(',')
                    .append(intent.kind()).append(',')
                    .append(i).append(',')
                    .append(segment.sourceCellIndex()).append(',')
                    .append(segment.downstreamCellIndex()).append(',')
                    .append(segment.role()).append('|')
                    .append("order=").append(segment.streamOrder()).append('|')
                    .append(intent.path().profile().kind()).append('|')
                    .append("riparian=").append(intent.riparianCells().size()).append('\n');
        }

        for (int i = 0; i < plan.retainedWater().size(); i++) {
            SkyIslandVisibleRetainedWaterIntent intent = plan.retainedWater().get(i);
            String sinks = intent.footprint().sourceCandidates().stream()
                    .map(candidate -> Integer.toString(candidate.sinkCellIndex()))
                    .collect(Collectors.joining("|"));
            String kinds = intent.footprint().sourceCandidates().stream()
                    .map(candidate -> candidate.kind().name())
                    .distinct()
                    .collect(Collectors.joining("|"));
            csv.append(key).append(',')
                    .append(intent.kind()).append(',')
                    .append(i).append(',')
                    .append(intent.footprint().sourceCandidates().getFirst().sinkCellIndex()).append(',')
                    .append(-1).append(',')
                    .append("sinks=").append(sinks).append('|')
                    .append("kinds=").append(kinds).append('|')
                    .append("cells=").append(intent.footprint().inundatedCellCount()).append('|')
                    .append("margin=").append(intent.margin().cells().size()).append('\n');
        }

        for (int i = 0; i < plan.drops().size(); i++) {
            SkyIslandVisibleDropWaterIntent intent = plan.drops().get(i);
            SkyIslandChannelDrop drop = intent.drop();
            csv.append(key).append(',')
                    .append(intent.kind()).append(',')
                    .append(i).append(',')
                    .append(drop.sourceCellIndex()).append(',')
                    .append(drop.downstreamCellIndex()).append(',')
                    .append("sourceKind=").append(drop.kind()).append('|')
                    .append("discharge=").append(format(drop.dischargePotential())).append('|')
                    .append("persistence=").append(format(drop.persistencePotential())).append('\n');
        }
    }

    private static int px(SkyIslandLocalPosition position, double radius, int mapX) {
        return mapX + (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int py(SkyIslandLocalPosition position, double radius, int mapY) {
        return mapY + (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String indexHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0086 visible hydrologic realization intent</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Visible hydrologic realization intent</h1>
                <p>AUTH-0086 projection proof: exact accepted channels, retained waterbodies and drop events become one-for-one backend-neutral visible-water intents. Riparian and margin semantics remain dry provenance. No Minecraft block selection, block placement or fluid scheduling occurs here.</p>
                <img src="atlas.png" alt="AUTH-0086 projection atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="provenance.csv">provenance.csv</a></p>
                </body></html>
                """;
    }
}
