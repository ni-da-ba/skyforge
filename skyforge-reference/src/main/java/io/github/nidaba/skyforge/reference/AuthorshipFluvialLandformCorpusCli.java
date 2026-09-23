package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfileKind;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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

/** Generates AUTH-0105 dry-landform and water-overlay evidence for hydrology review. */
public final class AuthorshipFluvialLandformCorpusCli {
    public static final String EVIDENCE_ID = "authorship-fluvial-landforms-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 144;
    private static final int HEADER = 58;
    private static final int PANEL_W = 3 * MAP;
    private static final int PANEL_H = HEADER + MAP;
    private static final int COLS = 2;

    private AuthorshipFluvialLandformCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("dr70-1471", descriptor(8L, 81L, 1471L)),
                new Specimen("key-77", descriptor(6L, 61L, 77L)),
                new Specimen("key-118", descriptor(6L, 61L, 118L)),
                new Specimen("key-241", descriptor(6L, 61L, 241L)),
                new Specimen("key-83-retained-basin", descriptor(6L, 61L, 83L)),
                new Specimen("key-512-stress", descriptor(6L, 61L, 512L)),
                new Specimen("key-811-stress", descriptor(6L, 61L, 811L)));

        int rows = (specimens.size() + COLS - 1) / COLS;
        BufferedImage atlas =
                new BufferedImage(COLS * PANEL_W, rows * PANEL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "specimen,islandKey,morphology,reaches,alluvial,incised,cascade,retainedWater,drops,"
                        + "meanBankfullWidth,meanValleyWidth,maxDryLowering,wetSampleFraction,"
                        + "meanConfinement,loweringCapFraction\n");

        for (int i = 0; i < specimens.size(); i++) {
            Specimen specimen = specimens.get(i);
            SkyIslandDescriptor descriptor = specimen.descriptor();
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
            Metrics metrics = metrics(field);

            BufferedImage panel = render(specimen, field);
            ImageIO.write(panel, "png", out.resolve(specimen.name() + ".png").toFile());
            ag.drawImage(panel, (i % COLS) * PANEL_W, (i / COLS) * PANEL_H, null);

            manifest.append(specimen.name()).append(',')
                    .append(descriptor.identity().islandKey()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(field.reaches().size()).append(',')
                    .append(count(field, SkyIslandChannelProfileKind.ALLUVIAL)).append(',')
                    .append(count(field, SkyIslandChannelProfileKind.INCISED)).append(',')
                    .append(count(field, SkyIslandChannelProfileKind.CASCADE)).append(',')
                    .append(io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner
                            .plan(descriptor).footprints().size()).append(',')
                    .append(coherent.drops().drops().size()).append(',')
                    .append(format(metrics.meanBankfullWidth())).append(',')
                    .append(format(metrics.meanValleyWidth())).append(',')
                    .append(format(metrics.maxDryLowering())).append(',')
                    .append(format(metrics.wetSampleFraction())).append(',')
                    .append(format(metrics.meanConfinement())).append(',')
                    .append(format(metrics.loweringCapFraction())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("index.html"), indexHtml(), StandardCharsets.UTF_8);
        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static BufferedImage render(Specimen specimen, SkyIslandFluvialTerrainField field) {
        BufferedImage image = new BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandDescriptor descriptor = specimen.descriptor();
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(
                specimen.name() + " / " + descriptor.morphologyFamily().identifier()
                        + " / reaches=" + field.reaches().size(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "BASE", 0, MAP, 40);
        centered(g, "DRY FLUVIAL", MAP, MAP, 40);
        centered(g, "WATER OVERLAY", 2 * MAP, MAP, 40);

        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(x, z);
                double base = field.baseTerrain().sample(p);
                double shaped = field.sample(p);
                paint(g, px, py, 0, elevationColor(base));
                paint(g, px, py, MAP, elevationColor(shaped));
                Color overlay = field.waterSurfacePotential(p).isPresent()
                        ? new Color(55, 145, 220)
                        : elevationColor(shaped);
                paint(g, px, py, 2 * MAP, overlay);
            }
        }
        g.dispose();
        return image;
    }

    private static Metrics metrics(SkyIslandFluvialTerrainField field) {
        double meanBankfull = field.reaches().stream()
                .mapToDouble(reach -> 2.0 * reach.bankfullHalfWidth()).average().orElse(0.0);
        double meanValley = field.reaches().stream()
                .mapToDouble(reach -> 2.0 * reach.valleyHalfWidth()).average().orElse(0.0);
        double meanConfinement = field.reaches().stream()
                .mapToDouble(io.github.nidaba.skyforge.world.SkyIslandFluvialReachGeometry::confinementPotential)
                .average().orElse(0.0);

        double radius = field.descriptor().nominalRadius();
        double maxLowering = 0.0;
        int wet = 0;
        int active = 0;
        int loweringCap = 0;
        for (int z = 0; z <= 80; z++) {
            for (int x = 0; x <= 80; x++) {
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(
                        -radius + 2.0 * radius * x / 80.0,
                        -radius + 2.0 * radius * z / 80.0);
                double base = field.baseTerrain().sample(p);
                double shaped = field.sample(p);
                if (base > 0.0 || shaped > 0.0) {
                    active++;
                }
                double lowering = base - shaped;
                maxLowering = Math.max(maxLowering, lowering);
                if (lowering >= SkyIslandFluvialTerrainField.MAX_FLUVIAL_LOWERING - 1.0e-6) {
                    loweringCap++;
                }
                if (field.waterSurfacePotential(p).isPresent()) {
                    wet++;
                }
            }
        }
        return new Metrics(
                meanBankfull,
                meanValley,
                maxLowering,
                active == 0 ? 0.0 : (double) wet / active,
                meanConfinement,
                active == 0 ? 0.0 : (double) loweringCap / active);
    }

    private static long count(
            SkyIslandFluvialTerrainField field,
            SkyIslandChannelProfileKind kind) {
        return field.reaches().stream().filter(reach -> reach.profile().kind() == kind).count();
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
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (width - textWidth) / 2, y);
    }

    private static SkyIslandDescriptor descriptor(long groupKey, long regionKey, long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, groupKey, regionKey, islandKey));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String indexHtml() {
        return """
                <!doctype html><meta charset="utf-8">
                <title>AUTH-0105 fluvial landforms</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1100px;margin:2rem auto;background:#f4f1e8;color:#2d333b}img{width:100%;border:1px solid #aaa;background:white}</style>
                <h1>AUTH-0105 fluvial landform review</h1>
                <p>BASE is accepted AUTH-0019 continuous hydrologic terrain. DRY FLUVIAL adds the new profile-sensitive channel/valley terrain primitive with all water hidden. WATER OVERLAY shows only authored wet-channel support over the same dry terrain.</p>
                <p>The primary human question is deliberately simple: <strong>does the drainage landform remain legible in DRY FLUVIAL without relying on blue pixels?</strong> The first panel is the previous DR-70 specimen; the remaining panels are deterministic hydrology controls/candidates.</p>
                <img src="atlas.png" alt="AUTH-0105 fluvial landform atlas">
                <p><a href="manifest.csv">manifest.csv</a></p>
                """;
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
    private record Metrics(
            double meanBankfullWidth,
            double meanValleyWidth,
            double maxDryLowering,
            double wetSampleFraction,
            double meanConfinement,
            double loweringCapFraction) {}
}
