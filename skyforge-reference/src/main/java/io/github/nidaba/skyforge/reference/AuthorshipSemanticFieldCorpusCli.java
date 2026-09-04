package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptorJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates the deterministic AUTH-0002 semantic-field visual review corpus. */
public final class AuthorshipSemanticFieldCorpusCli {
    private static final int MAP_SIZE = 384;
    private static final int PANEL_LABEL_HEIGHT = 34;
    private static final Color OUTSIDE_COLOR = new Color(12, 14, 18);
    private static final SkyIslandIdentity IDENTITY = SkyIslandIdentity.of(
            0x534B59464F524745L,
            0x0000000000000002L,
            0x0000000000000011L,
            0x0000000000000042L);

    private AuthorshipSemanticFieldCorpusCli() {}

    /** Writes descriptor data, five scalar maps, statistics, and a combined overview. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("usage: AuthorshipSemanticFieldCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", "authorship-semantic-fields-v1");
        Files.createDirectories(output);

        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(IDENTITY);
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.createLegacyCircular(descriptor);
        Files.write(output.resolve("descriptor.json"), new SkyIslandDescriptorJson().write(descriptor));

        List<FieldPanel> panels = List.of(
                new FieldPanel("elevation", fields.elevationTendency(), Palette.ELEVATION),
                new FieldPanel("temperature", fields.temperature(), Palette.TEMPERATURE),
                new FieldPanel("moisture", fields.moisture(), Palette.MOISTURE),
                new FieldPanel("exposure", fields.exposure(), Palette.EXPOSURE),
                new FieldPanel("interiority", fields.interiority(), Palette.INTERIORITY));

        List<RenderedPanel> rendered = new ArrayList<>();
        StringBuilder stats = new StringBuilder("field,min,max,mean\n");
        for (FieldPanel panel : panels) {
            RenderedPanel image = render(panel, fields.interiority(), descriptor);
            rendered.add(image);
            ImageIO.write(image.image(), "png", output.resolve(panel.name() + ".png").toFile());
            stats.append(panel.name()).append(',')
                    .append(image.minimum()).append(',')
                    .append(image.maximum()).append(',')
                    .append(image.mean()).append('\n');
        }
        Files.writeString(output.resolve("stats.csv"), stats.toString(), StandardCharsets.UTF_8);
        ImageIO.write(renderOverview(rendered), "png", output.resolve("overview.png").toFile());
        Files.writeString(output.resolve("index.html"), indexHtml(descriptor, panels), StandardCharsets.UTF_8);
        System.out.println(output.toAbsolutePath());
    }

    private static RenderedPanel render(
            FieldPanel panel,
            SkyIslandSemanticField mask,
            SkyIslandDescriptor descriptor) {
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        double extent = descriptor.nominalRadius() * 1.08;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        long count = 0L;
        for (int py = 0; py < MAP_SIZE; py++) {
            double z = extent - 2.0 * extent * py / (MAP_SIZE - 1.0);
            for (int px = 0; px < MAP_SIZE; px++) {
                double x = -extent + 2.0 * extent * px / (MAP_SIZE - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (mask.sample(position) <= 0.0) {
                    image.setRGB(px, py, OUTSIDE_COLOR.getRGB());
                    continue;
                }
                double value = panel.field().sample(position);
                image.setRGB(px, py, panel.palette().color(value).getRGB());
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                sum += value;
                count++;
            }
        }
        if (count == 0L) {
            throw new IllegalStateException("semantic field corpus contained no owned island samples");
        }
        return new RenderedPanel(panel.name(), image, minimum, maximum, sum / count);
    }

    private static BufferedImage renderOverview(List<RenderedPanel> panels) {
        int columns = 3;
        int rows = 2;
        int width = columns * MAP_SIZE;
        int height = rows * (MAP_SIZE + PANEL_LABEL_HEIGHT);
        BufferedImage overview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = overview.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        graphics.setColor(Color.BLACK);
        for (int index = 0; index < panels.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = column * MAP_SIZE;
            int y = row * (MAP_SIZE + PANEL_LABEL_HEIGHT);
            RenderedPanel panel = panels.get(index);
            graphics.drawString(panel.name(), x + 10, y + 25);
            graphics.drawImage(panel.image(), x, y + PANEL_LABEL_HEIGHT, null);
        }
        graphics.dispose();
        return overview;
    }

    private static String indexHtml(SkyIslandDescriptor descriptor, List<FieldPanel> panels) {
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><meta charset=\"utf-8\"><title>AUTH-0002 semantic fields</title>");
        body.append("<h1>AUTH-0002 semantic field corpus</h1>");
        body.append("<p>Fixed deterministic island identity; morphology: ")
                .append(descriptor.morphologyFamily().identifier())
                .append("; nominal radius: ")
                .append(descriptor.nominalRadius())
                .append(".</p>");
        body.append("<p>Rendered statistics and color maps include owned island samples only; ")
                .append("the underlying field API remains mathematically evaluable outside the boundary.</p>");
        body.append("<p><img src=\"overview.png\" style=\"max-width:100%\"></p>");
        for (FieldPanel panel : panels) {
            body.append("<h2>").append(panel.name()).append("</h2><img src=\"")
                    .append(panel.name()).append(".png\" style=\"max-width:384px\">");
        }
        body.append("<p><a href=\"descriptor.json\">descriptor.json</a> | <a href=\"stats.csv\">stats.csv</a></p>");
        return body.toString();
    }

    private record FieldPanel(String name, SkyIslandSemanticField field, Palette palette) {}

    private record RenderedPanel(String name, BufferedImage image, double minimum, double maximum, double mean) {}

    private enum Palette {
        ELEVATION {
            @Override
            Color color(double value) {
                return ramp(value, new Color(16, 36, 48), new Color(232, 226, 190));
            }
        },
        TEMPERATURE {
            @Override
            Color color(double value) {
                return value < 0.5
                        ? ramp(value * 2.0, new Color(28, 68, 138), new Color(236, 236, 218))
                        : ramp((value - 0.5) * 2.0, new Color(236, 236, 218), new Color(170, 48, 34));
            }
        },
        MOISTURE {
            @Override
            Color color(double value) {
                return ramp(value, new Color(112, 86, 54), new Color(37, 115, 177));
            }
        },
        EXPOSURE {
            @Override
            Color color(double value) {
                return ramp(value, new Color(42, 48, 58), new Color(242, 203, 88));
            }
        },
        INTERIORITY {
            @Override
            Color color(double value) {
                int shade = (int) Math.round(value * 255.0);
                return new Color(shade, shade, shade);
            }
        };

        abstract Color color(double value);

        static Color ramp(double value, Color low, Color high) {
            double clamped = Math.max(0.0, Math.min(1.0, value));
            int red = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * clamped);
            int green = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * clamped);
            int blue = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * clamped);
            return new Color(red, green, blue);
        }
    }
}
