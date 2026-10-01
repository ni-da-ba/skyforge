package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandContinuousHydrologicTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandEcologyField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
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

/** Generates deterministic AUTH-0021 current-domain downstream propagation evidence. */
public final class AuthorshipNaturalizedDomainPropagationCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 220;
    private static final int HEADER = 62;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;

    private AuthorshipNaturalizedDomainPropagationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-naturalized-domain-propagation-v1");
        Files.createDirectories(out);

        List<Long> keys = List.of(7L, 10L, 77L, 118L, 512L, 811L);
        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 3 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,watershedCells,sourceComponents,retainedComponents,"
                        + "retainedReaches,riparianCells,drops,waterbodyFootprints,"
                        + "changedTerrainCells\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies =
                    SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);

            BufferedImage panel =
                    renderPanel(descriptor, semantic, ecology, coherent, waterbodies);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(watershed.cells().size()).append(',')
                    .append(coherent.channels().sourceComponentCount()).append(',')
                    .append(coherent.channels().retainedComponentCount()).append(',')
                    .append(coherent.channels().retainedReachCount()).append(',')
                    .append(coherent.riparian().cellCount()).append(',')
                    .append(coherent.drops().drops().size()).append(',')
                    .append(waterbodies.footprints().size()).append(',')
                    .append(coherent.terrainSurface().changedCellCount()).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0021</title>"
                        + "<h1>Naturalized-domain propagation</h1>"
                        + "<p>CURRENT ELEVATION and CURRENT ECOLOGY use the authoritative AUTH-0020 "
                        + "domain through SkyIslandSemanticFieldSet. COHERENT HYDROLOGY renders the "
                        + "current continuous hydrologic terrain, retained-water footprints, and "
                        + "AUTH-0018/0019 coherent visible river skeleton under the same domain.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandEcologyField ecology,
            SkyIslandCoherentHydrologicRealizationPlan coherent,
            SkyIslandWaterbodyFootprintPlan waterbodies) {
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
                        "components %d -> %d   reaches=%d   riparian=%d   waterbodies=%d",
                        coherent.channels().sourceComponentCount(),
                        coherent.channels().retainedComponentCount(),
                        coherent.channels().retainedReachCount(),
                        coherent.riparian().cellCount(),
                        waterbodies.footprints().size()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "CURRENT ELEVATION", 0, MAP, 53);
        centered(g, "CURRENT ECOLOGY", MAP, MAP, 53);
        centered(g, "COHERENT HYDROLOGY", 2 * MAP, MAP, 53);

        SkyIslandContinuousHydrologicTerrainField terrain = coherent.continuousTerrain();
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.04;

        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    image.setRGB(px, HEADER + py, Color.WHITE.getRGB());
                    image.setRGB(MAP + px, HEADER + py, Color.WHITE.getRGB());
                    image.setRGB(2 * MAP + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }

                image.setRGB(
                        px,
                        HEADER + py,
                        elevationColor(semantic.elevationTendency().sample(position)).getRGB());
                image.setRGB(
                        MAP + px,
                        HEADER + py,
                        ecologyColor(ecology.sample(position).regime()).getRGB());

                double elevation = terrain.sample(position);
                int shade = (int) Math.round(52.0 + 188.0 * clamp01(elevation));
                image.setRGB(
                        2 * MAP + px,
                        HEADER + py,
                        new Color(shade, shade, shade).getRGB());
            }
        }

        g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(25, 95, 190));
        for (SkyIslandNaturalizedChannelPath path : coherent.naturalizedChannels().paths()) {
            for (int i = 1; i < path.points().size(); i++) {
                SkyIslandLocalPosition a = path.points().get(i - 1);
                SkyIslandLocalPosition b = path.points().get(i);
                g.drawLine(
                        2 * MAP + mapX(a, extent),
                        HEADER + mapY(a, extent),
                        2 * MAP + mapX(b, extent),
                        HEADER + mapY(b, extent));
            }
        }

        g.setColor(new Color(24, 90, 155));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = 2 * MAP + mapX(cell.position(), extent);
                int y = HEADER + mapY(cell.position(), extent);
                g.fillOval(x - 2, y - 2, 5, 5);
            }
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 3; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static int mapX(SkyIslandLocalPosition position, double extent) {
        return (int) Math.round((position.x() / extent + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double extent) {
        return (int) Math.round((1.0 - (position.z() / extent + 1.0) * 0.5) * (MAP - 1));
    }

    private static Color elevationColor(double value) {
        double t = clamp01(value);
        Color low = new Color(72, 96, 70);
        Color high = new Color(229, 220, 185);
        return ramp(t, low, high);
    }

    private static Color ecologyColor(SkyIslandEcologyRegime regime) {
        return switch (regime) {
            case COLD_BARREN -> new Color(211, 220, 224);
            case ALPINE -> new Color(139, 149, 137);
            case BOREAL_WOODLAND -> new Color(43, 86, 68);
            case TEMPERATE_WOODLAND -> new Color(72, 120, 62);
            case HUMID_WOODLAND -> new Color(31, 111, 76);
            case OPEN_GRASSLAND -> new Color(166, 176, 89);
            case DRY_SCRUB -> new Color(181, 145, 82);
            case WETLAND -> new Color(69, 130, 125);
        };
    }

    private static Color ramp(double value, Color low, Color high) {
        int red = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * value);
        int green = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * value);
        int blue = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * value);
        return new Color(red, green, blue);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
