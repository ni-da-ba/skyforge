package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedDomainField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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

/** Generates deterministic AUTH-0020 legacy-circle versus naturalized-domain evidence. */
public final class AuthorshipNaturalizedDomainCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 220;
    private static final int HEADER = 56;
    private static final int PANEL_WIDTH = 3 * MAP;
    private static final int PANEL_HEIGHT = HEADER + MAP;
    private static final int ANGLE_SAMPLES = 1440;

    private AuthorshipNaturalizedDomainCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-naturalized-island-domain-v1");
        Files.createDirectories(out);

        List<Long> keys = List.of(1L, 6L, 4L, 14L, 7L, 18L, 10L, 33L, 3L, 11L);
        BufferedImage atlas =
                new BufferedImage(2 * PANEL_WIDTH, 5 * PANEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,minBoundaryFraction,maxBoundaryFraction,"
                        + "meanBoundaryFraction,areaFractionVsLegacyCircle\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);

            BoundaryMetrics metrics = measure(descriptor, domain);
            BufferedImage panel = renderPanel(descriptor, domain, metrics);
            ImageIO.write(panel, "png", out.resolve("island-" + key + ".png").toFile());
            ag.drawImage(panel, (n % 2) * PANEL_WIDTH, (n / 2) * PANEL_HEIGHT, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(format(metrics.minimumFraction())).append(',')
                    .append(format(metrics.maximumFraction())).append(',')
                    .append(format(metrics.meanFraction())).append(',')
                    .append(format(metrics.areaFraction())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0020</title>"
                        + "<h1>Naturalized island domain</h1>"
                        + "<p>LEGACY CIRCLE shows the AUTH-0002 circular interiority assumption. "
                        + "NATURALIZED DOMAIN shows the morphology-aware AUTH-0020 candidate. "
                        + "MORPHOLOGY IN DOMAIN clips the existing elevation tendency to the new "
                        + "candidate ownership geometry. AUTH-0020 is evidence-only with respect "
                        + "to downstream semantic consumers.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BoundaryMetrics measure(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedDomainField domain) {
        double radius = descriptor.nominalRadius();
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        double areaSum = 0.0;
        for (int i = 0; i < ANGLE_SAMPLES; i++) {
            double angle = 2.0 * Math.PI * i / ANGLE_SAMPLES;
            double fraction = domain.boundaryRadius(angle) / radius;
            minimum = Math.min(minimum, fraction);
            maximum = Math.max(maximum, fraction);
            sum += fraction;
            areaSum += fraction * fraction;
        }
        return new BoundaryMetrics(
                minimum,
                maximum,
                sum / ANGLE_SAMPLES,
                areaSum / ANGLE_SAMPLES);
    }

    private static BufferedImage renderPanel(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedDomainField domain,
            BoundaryMetrics metrics) {
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
                        "boundary %.3f..%.3f R   area %.3f x legacy circle",
                        metrics.minimumFraction(),
                        metrics.maximumFraction(),
                        metrics.areaFraction()),
                7,
                34);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "LEGACY CIRCLE", 0, MAP, 50);
        centered(g, "NATURALIZED DOMAIN", MAP, MAP, 50);
        centered(g, "MORPHOLOGY IN DOMAIN", 2 * MAP, MAP, 50);

        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.createLegacyCircular(descriptor);
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.04;

        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);

                double legacy = semantic.interiority().sample(position);
                image.setRGB(px, HEADER + py, grayscale(legacy).getRGB());

                double naturalized = domain.sample(position);
                image.setRGB(MAP + px, HEADER + py, grayscale(naturalized).getRGB());

                if (naturalized <= 0.0) {
                    image.setRGB(2 * MAP + px, HEADER + py, new Color(238, 238, 238).getRGB());
                } else {
                    double elevation = semantic.elevationTendency().sample(position);
                    image.setRGB(2 * MAP + px, HEADER + py, elevationColor(elevation).getRGB());
                }
            }
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 3; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static Color grayscale(double value) {
        int shade = (int) Math.round(245.0 - 205.0 * clamp01(value));
        return new Color(shade, shade, shade);
    }

    private static Color elevationColor(double value) {
        double t = clamp01(value);
        Color low = new Color(70, 93, 67);
        Color high = new Color(226, 218, 183);
        int red = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * t);
        int green = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * t);
        int blue = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record BoundaryMetrics(
            double minimumFraction,
            double maximumFraction,
            double meanFraction,
            double areaFraction) {}
}
