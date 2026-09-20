package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Searches one deterministic island family for strong AUTH-0105 human-review specimens. */
public final class AuthorshipFluvialSpecimenSearchCli {
    public static final String EVIDENCE_ID = "authorship-fluvial-specimen-search-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long GROUP = 8L;
    private static final long REGION = 81L;
    private static final int SEARCH_COUNT = 4096;
    private static final int TOP_COUNT = 8;
    private static final int MAP = 156;
    private static final int HEADER = 54;
    private static final int PANEL_W = 3 * MAP;
    private static final int PANEL_H = HEADER + MAP;
    private static final int COLS = 2;

    private AuthorshipFluvialSpecimenSearchCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1 ? Path.of(args[0]) : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Candidate> all = new ArrayList<>(SEARCH_COUNT);
        for (long key = 0; key < SEARCH_COUNT; key++) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
            if (!coherent.naturalizedChannels().paths().isEmpty()) {
                all.add(candidate(descriptor, coherent));
            }
        }
        all.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingLong(candidate -> candidate.descriptor().identity().islandKey()));
        List<Candidate> top = all.stream().limit(TOP_COUNT).toList();

        BufferedImage atlas = new BufferedImage(
                COLS * PANEL_W,
                ((top.size() + COLS - 1) / COLS) * PANEL_H,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder csv = new StringBuilder(
                "rank,islandKey,morphology,score,components,reaches,longestChainRatio,terminalDischarge,"
                        + "maxStreamOrder,retainedWater,interiorDrops,edgeFalls,meanValleyWidth,maxDryLowering\n");

        for (int i = 0; i < top.size(); i++) {
            Candidate candidate = top.get(i);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(candidate.descriptor());
            SkyIslandFluvialTerrainField field =
                    SkyIslandFluvialTerrainField.create(candidate.descriptor(), coherent);
            BufferedImage panel = render(i + 1, candidate, field);
            String name = String.format(Locale.ROOT, "rank-%02d-key-%d.png",
                    i + 1, candidate.descriptor().identity().islandKey());
            ImageIO.write(panel, "png", out.resolve(name).toFile());
            ag.drawImage(panel, (i % COLS) * PANEL_W, (i / COLS) * PANEL_H, null);

            double meanValley = field.reaches().stream()
                    .mapToDouble(reach -> 2.0 * reach.valleyHalfWidth()).average().orElse(0.0);
            csv.append(i + 1).append(',')
                    .append(candidate.descriptor().identity().islandKey()).append(',')
                    .append(candidate.descriptor().morphologyFamily().identifier()).append(',')
                    .append(format(candidate.score())).append(',')
                    .append(candidate.componentCount()).append(',')
                    .append(candidate.reachCount()).append(',')
                    .append(format(candidate.longestChainRatio())).append(',')
                    .append(format(candidate.terminalDischarge())).append(',')
                    .append(candidate.maxStreamOrder()).append(',')
                    .append(candidate.retainedWater()).append(',')
                    .append(candidate.interiorDrops()).append(',')
                    .append(candidate.edgeFalls()).append(',')
                    .append(format(meanValley)).append(',')
                    .append(format(denseMaxLowering(field))).append('\n');
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("candidates.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("index.html"), indexHtml(), StandardCharsets.UTF_8);
        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static Candidate candidate(
            SkyIslandDescriptor descriptor,
            SkyIslandCoherentHydrologicRealizationPlan coherent) {
        Map<Integer, SkyIslandNaturalizedChannelPath> bySource = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : coherent.naturalizedChannels().paths()) {
            bySource.put(path.profile().segment().sourceCellIndex(), path);
        }
        Map<Integer, Double> memo = new HashMap<>();
        double longestChain = bySource.values().stream()
                .mapToDouble(path -> chainLength(path, bySource, memo))
                .max().orElse(0.0);
        double chainRatio = longestChain / descriptor.nominalRadius();
        double terminalDischarge = coherent.channels().retainedComponents().stream()
                .mapToDouble(SkyIslandCoherentChannelComponent::terminalRelativeDischarge)
                .max().orElse(0.0);
        int maxOrder = coherent.channels().retainedComponents().stream()
                .mapToInt(SkyIslandCoherentChannelComponent::maxStreamOrder).max().orElse(1);
        int retainedWater = SkyIslandWaterbodyFootprintPlanner.plan(descriptor).footprints().size();
        int interiorDrops = (int) (coherent.drops().count(SkyIslandChannelDropKind.CASCADE_STEP)
                + coherent.drops().count(SkyIslandChannelDropKind.WATERFALL));
        int edgeFalls = (int) coherent.drops().count(SkyIslandChannelDropKind.EDGE_FALL);
        int components = coherent.channels().retainedComponentCount();
        int reaches = coherent.channels().retainedReachCount();

        double score =
                3.2 * Math.min(2.0, chainRatio)
                        + 1.4 * terminalDischarge
                        + 0.55 * Math.min(1.0, (maxOrder - 1) / 3.0)
                        + 0.65 * Math.min(1, retainedWater)
                        + 0.45 * Math.min(1.0, interiorDrops / 3.0)
                        + 0.25 * Math.min(1.0, reaches / 20.0)
                        - 0.18 * Math.max(0, components - 2)
                        - 0.04 * Math.max(0, edgeFalls - 2);

        return new Candidate(descriptor, score, components, reaches, chainRatio,
                terminalDischarge, maxOrder, retainedWater, interiorDrops, edgeFalls);
    }

    private static double chainLength(
            SkyIslandNaturalizedChannelPath path,
            Map<Integer, SkyIslandNaturalizedChannelPath> bySource,
            Map<Integer, Double> memo) {
        int source = path.profile().segment().sourceCellIndex();
        Double cached = memo.get(source);
        if (cached != null) {
            return cached;
        }
        SkyIslandNaturalizedChannelPath next =
                bySource.get(path.profile().segment().downstreamCellIndex());
        double value = path.pathLength() + (next == null ? 0.0 : chainLength(next, bySource, memo));
        memo.put(source, value);
        return value;
    }

    private static BufferedImage render(
            int rank,
            Candidate candidate,
            SkyIslandFluvialTerrainField field) {
        BufferedImage image = new BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString(
                "rank=" + rank
                        + " key=" + candidate.descriptor().identity().islandKey()
                        + " " + candidate.descriptor().morphologyFamily().identifier()
                        + String.format(Locale.ROOT, " score=%.2f chain/r=%.2f",
                                candidate.score(), candidate.longestChainRatio()),
                7, 16);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "BASE", 0, MAP, 37);
        centered(g, "DRY FLUVIAL", MAP, MAP, 37);
        centered(g, "WATER OVERLAY", 2 * MAP, MAP, 37);

        double radius = candidate.descriptor().nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(x, z);
                double base = field.baseTerrain().sample(p);
                double shaped = field.sample(p);
                paint(g, px, py, 0, elevationColor(base));
                paint(g, px, py, MAP, elevationColor(shaped));
                paint(g, px, py, 2 * MAP,
                        field.waterSurfacePotential(p).isPresent()
                                ? new Color(55, 145, 220)
                                : elevationColor(shaped));
            }
        }
        g.dispose();
        return image;
    }

    private static double denseMaxLowering(SkyIslandFluvialTerrainField field) {
        double radius = field.descriptor().nominalRadius();
        double max = 0.0;
        for (int z = 0; z <= 64; z++) {
            for (int x = 0; x <= 64; x++) {
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(
                        -radius + 2.0 * radius * x / 64.0,
                        -radius + 2.0 * radius * z / 64.0);
                max = Math.max(max, field.baseTerrain().sample(p) - field.sample(p));
            }
        }
        return max;
    }

    private static Color elevationColor(double value) {
        int shade = (int) Math.round(35.0 + 205.0 * Math.max(0.0, Math.min(1.0, value)));
        return new Color(shade, shade, shade);
    }

    private static void paint(Graphics2D g, int px, int py, int offsetX, Color color) {
        g.setColor(color);
        g.fillRect(offsetX + px, HEADER + py, 1, 1);
    }

    private static void centered(Graphics2D g, String text, int x, int width, int y) {
        g.drawString(text, x + (width - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, GROUP, REGION, islandKey));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String indexHtml() {
        return """
                <!doctype html><meta charset="utf-8">
                <title>AUTH-0105 specimen search</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1100px;margin:2rem auto;background:#f4f1e8;color:#2d333b}img{width:100%;border:1px solid #aaa;background:white}</style>
                <h1>AUTH-0105 hydrology specimen search</h1>
                <p>Top deterministic candidates from island keys 0..4095 in the DR-70 identity family. Ranking favors long connected channel chains, strong terminal discharge, stream hierarchy, retained water and interior drops, while penalizing fragmented edge-drain fans.</p>
                <p>Ranking finds useful review specimens; it does not constitute aesthetic acceptance.</p>
                <img src="atlas.png" alt="AUTH-0105 specimen search atlas">
                <p><a href="candidates.csv">candidates.csv</a></p>
                """;
    }

    private record Candidate(
            SkyIslandDescriptor descriptor,
            double score,
            int componentCount,
            int reachCount,
            double longestChainRatio,
            double terminalDischarge,
            int maxStreamOrder,
            int retainedWater,
            int interiorDrops,
            int edgeFalls) {}
}
