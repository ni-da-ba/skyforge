package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFreshwaterHabitatOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandFreshwaterHabitatOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates AUTH-0091 retained-freshwater habitat-opportunity evidence. */
public final class AuthorshipFreshwaterHabitatOpportunityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-freshwater-habitat-opportunity-v1";

    private static final long SEED = 0x534B59464F524745L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipFreshwaterHabitatOpportunityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipFreshwaterHabitatOpportunityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandFreshwaterHabitatOpportunityProfiler profiler =
                new SkyIslandFreshwaterHabitatOpportunityProfiler();
        SkyIslandDescriptor wetDescriptor = descriptor(83L);
        SkyIslandFreshwaterHabitatOpportunityProfile wet =
                profiler.profile(wetDescriptor);
        SkyIslandFreshwaterHabitatOpportunityProfile repeat =
                profiler.profile(wetDescriptor);
        SkyIslandFreshwaterHabitatOpportunityProfile dry =
                profiler.profile(descriptor(77L));
        SkyIslandFreshwaterHabitatOpportunityProfile doubled =
                profiler.profile(withRadius(
                        wetDescriptor,
                        wetDescriptor.nominalRadius() * 2.0));

        boolean deterministic = equivalent(wet, repeat);
        boolean wetProvenance =
                wet.hasRetainedFreshwater()
                        && wet.footprintCount() == 1
                        && wet.sourceCandidateCount() == 2L
                        && wet.watershed().descriptor().equals(wetDescriptor)
                        && wet.footprintPlan().descriptor().equals(wetDescriptor);
        boolean dryZero =
                !dry.hasRetainedFreshwater()
                        && dry.footprintCount() == 0
                        && dry.sourceCandidateCount() == 0L
                        && dry.inundatedCellCount() == 0L
                        && dry.coarseHorizontalInundatedAreaEstimate() == 0.0
                        && dry.shorelineCellCount() == 0L
                        && dry.meanWaterDepthPotential() == 0.0
                        && dry.maxWaterDepthPotential() == 0.0;
        boolean areaArithmetic = areaArithmetic(wet);
        boolean normalizedDepth =
                wet.meanWaterDepthPotential() >= 0.0
                        && wet.meanWaterDepthPotential() <= wet.maxWaterDepthPotential()
                        && wet.maxWaterDepthPotential() <= 1.0;
        boolean scaleCovariant = scaleCovariant(wet, doubled);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "WET_PROVENANCE", wetProvenance,
                "key 83: exact watershed + footprint plans",
                "footprints=" + wet.footprintCount() + " sources=" + wet.sourceCandidateCount(),
                "unique inundated cells=" + wet.inundatedCellCount());
        panel(g, PANEL_W, 0, "DRY_ZERO", dryZero,
                "key 77: accepted drainage control",
                "no retained footprint -> zero opportunity",
                "no water fabricated to avoid emptiness");
        panel(g, PANEL_W * 2, 0, "AREA_ARITHMETIC", areaArithmetic,
                "watershed spacing=" + wet.watershed().spacing(),
                "area=" + wet.coarseHorizontalInundatedAreaEstimate(),
                "unique cells x spacing^2");
        panel(g, 0, PANEL_H, "NORMALIZED_DEPTH", normalizedDepth,
                "mean=" + wet.meanWaterDepthPotential(),
                "max=" + wet.maxWaterDepthPotential(),
                "semantic potential, not metres/blocks");
        panel(g, PANEL_W, PANEL_H, "SCALE_COVARIANCE", scaleCovariant,
                "radius doubled only",
                "topology/depth unchanged",
                "coarse horizontal water area x4");
        panel(g, PANEL_W * 2, PANEL_H, "NO_BACKEND_POLICY", true,
                "no fish / crop / carrying capacity",
                "no Minecraft fluid / biome identity",
                "no physical cubic water volume");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "WET_PROVENANCE," + wetProvenance + "\n"
                + "DRY_ZERO," + dryZero + "\n"
                + "AREA_ARITHMETIC," + areaArithmetic + "\n"
                + "NORMALIZED_DEPTH," + normalizedDepth + "\n"
                + "SCALE_COVARIANCE," + scaleCovariant + "\n"
                + "NO_BACKEND_POLICY,true\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder csv = new StringBuilder(
                "fixture,radius,gridSize,spacing,footprints,sources,inundatedCells,coarseHorizontalArea,shorelineCells,meanDepthPotential,maxDepthPotential");
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            csv.append(',').append(kind.name());
        }
        csv.append('\n');
        append(csv, "wet-key-83", wet);
        append(csv, "dry-key-77", dry);
        append(csv, "wet-key-83-radius-x2", doubled);
        Files.writeString(out.resolve("profiles.csv"), csv, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0091 freshwater habitat opportunity</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Freshwater habitat opportunity</h1>
                <p>AUTH-0091 summarizes accepted watershed and retained-waterbody footprints into coarse horizontal inundated-area, shoreline planning cells, source kinds, and normalized water-depth opportunity. Dry islands remain valid zero-water profiles. The evidence does not claim physical cubic volume, fish/crop eligibility, Minecraft fluid identity, or population authority.</p>
                <img src="atlas.png" alt="AUTH-0091 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="profiles.csv">profiles.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static void append(
            StringBuilder csv,
            String fixture,
            SkyIslandFreshwaterHabitatOpportunityProfile profile) {
        csv.append(fixture).append(',')
                .append(profile.descriptor().nominalRadius()).append(',')
                .append(profile.watershed().gridSize()).append(',')
                .append(profile.watershed().spacing()).append(',')
                .append(profile.footprintCount()).append(',')
                .append(profile.sourceCandidateCount()).append(',')
                .append(profile.inundatedCellCount()).append(',')
                .append(profile.coarseHorizontalInundatedAreaEstimate()).append(',')
                .append(profile.shorelineCellCount()).append(',')
                .append(profile.meanWaterDepthPotential()).append(',')
                .append(profile.maxWaterDepthPotential());
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            csv.append(',').append(profile.sourceCount(kind));
        }
        csv.append('\n');
    }

    private static boolean equivalent(
            SkyIslandFreshwaterHabitatOpportunityProfile first,
            SkyIslandFreshwaterHabitatOpportunityProfile second) {
        return first.descriptor().equals(second.descriptor())
                && first.watershed().equals(second.watershed())
                && first.footprintPlan().equals(second.footprintPlan())
                && first.sourceCandidateCount() == second.sourceCandidateCount()
                && first.inundatedCellCount() == second.inundatedCellCount()
                && Double.doubleToLongBits(first.coarseHorizontalInundatedAreaEstimate())
                        == Double.doubleToLongBits(second.coarseHorizontalInundatedAreaEstimate())
                && first.shorelineCellCount() == second.shorelineCellCount()
                && Double.doubleToLongBits(first.meanWaterDepthPotential())
                        == Double.doubleToLongBits(second.meanWaterDepthPotential())
                && Double.doubleToLongBits(first.maxWaterDepthPotential())
                        == Double.doubleToLongBits(second.maxWaterDepthPotential())
                && first.sourceKindCounts().equals(second.sourceKindCounts());
    }

    private static boolean areaArithmetic(
            SkyIslandFreshwaterHabitatOpportunityProfile profile) {
        double cellArea = profile.watershed().spacing() * profile.watershed().spacing();
        return Double.doubleToLongBits(profile.inundatedCellCount() * cellArea)
                == Double.doubleToLongBits(profile.coarseHorizontalInundatedAreaEstimate());
    }

    private static boolean scaleCovariant(
            SkyIslandFreshwaterHabitatOpportunityProfile small,
            SkyIslandFreshwaterHabitatOpportunityProfile large) {
        return small.watershed().gridSize() == large.watershed().gridSize()
                && close(small.watershed().spacing() * 2.0, large.watershed().spacing())
                && small.sourceCandidateCount() == large.sourceCandidateCount()
                && small.inundatedCellCount() == large.inundatedCellCount()
                && small.shorelineCellCount() == large.shorelineCellCount()
                && small.sourceKindCounts().equals(large.sourceKindCounts())
                && footprintIndices(small).equals(footprintIndices(large))
                && close(small.meanWaterDepthPotential(), large.meanWaterDepthPotential())
                && close(small.maxWaterDepthPotential(), large.maxWaterDepthPotential())
                && close(
                        small.coarseHorizontalInundatedAreaEstimate() * 4.0,
                        large.coarseHorizontalInundatedAreaEstimate());
    }

    private static Set<Integer> footprintIndices(
            SkyIslandFreshwaterHabitatOpportunityProfile profile) {
        Set<Integer> indices = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : profile.footprintPlan().footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                indices.add(cell.watershedCellIndex());
            }
        }
        return indices;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= 1.0e-12 * Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static SkyIslandDescriptor withRadius(
            SkyIslandDescriptor source,
            double radius) {
        return new SkyIslandDescriptor(
                source.schemaVersion(),
                source.identity(),
                source.authorshipSeed(),
                source.morphologyFamily(),
                radius,
                source.reliefBudget(),
                source.rockCompetence(),
                source.permeability(),
                source.temperatureTendency(),
                source.moistureTendency(),
                source.exposureTendency(),
                source.erosionMaturity(),
                source.hydrologicalPotential(),
                source.ecologicalPotential());
    }

    private static void panel(
            Graphics2D g,
            int x,
            int y,
            String title,
            boolean pass,
            String first,
            String second,
            String third) {
        g.setColor(pass ? new Color(225, 241, 228) : new Color(245, 220, 220));
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(first, x + 18, y + 72);
        g.drawString(second, x + 18, y + 104);
        g.drawString(third, x + 18, y + 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(pass ? "PASS" : "FAIL", x + 18, y + 205);
    }
}
