package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLithologicRealizationChannel;
import io.github.nidaba.skyforge.world.SkyIslandLithologicRealizationField;
import io.github.nidaba.skyforge.world.SkyIslandLithologicRealizationSample;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
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

/** Generates deterministic AUTH-0036 semantic lithologic-realization vocabulary evidence. */
public final class AuthorshipLithologicRealizationVocabularyCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 70;
    private static final int PANELS = 6;
    private static final int SAMPLE = 30;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipLithologicRealizationVocabularyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-lithologic-realization-vocabulary-v1");
        Files.createDirectories(out);

        List<Selection> selections = List.of(
                new Selection("competent", 2332L),
                new Selection("weak", 653L),
                new Selection("permeable", 1051L),
                new Selection("hydrologic", 2211L),
                new Selection("eroded", 1439L),
                new Selection("spine", 3670L));

        BufferedImage atlas =
                new BufferedImage(2 * SPECIMEN_WIDTH, 3 * SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,islandKey,morphology,materialSamples,contactSamples,"
                        + "massiveMean,massivePeak,fabricMean,fabricPeak,alterationMean,alterationPeak,"
                        + "waterMean,waterPeak,mineralMean,mineralPeak\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandLithologicRealizationField field =
                    SkyIslandLithologicRealizationField.create(descriptor);
            SkyIslandLithologicRealizationSample[][] samples = sample(descriptor, field);
            Stats stats = stats(samples);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, samples, stats);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.key() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(stats.materialSamples()).append(',')
                    .append(stats.contactSamples()).append(',')
                    .append(format(stats.mean(SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX))).append(',')
                    .append(format(stats.peak(SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX))).append(',')
                    .append(format(stats.mean(SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX))).append(',')
                    .append(format(stats.peak(SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX))).append(',')
                    .append(format(stats.mean(SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT))).append(',')
                    .append(format(stats.peak(SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT))).append(',')
                    .append(format(stats.mean(SkyIslandLithologicRealizationChannel.WATER_CONDITIONING))).append(',')
                    .append(format(stats.peak(SkyIslandLithologicRealizationChannel.WATER_CONDITIONING))).append(',')
                    .append(format(stats.mean(SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE))).append(',')
                    .append(format(stats.peak(SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE))).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0036</title>"
                        + "<h1>Backend-neutral lithologic realization vocabulary</h1>"
                        + "<p>All panels sample the stable AUTH-0036 realization contract at semantic "
                        + "depth 0.52. The first five panels are compositional semantic channels; "
                        + "CONTACT shows where AUTH-0035 blending is active. White is authored void "
                        + "or unowned space, or zero channel membership.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static SkyIslandLithologicRealizationSample[][] sample(
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicRealizationField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandLithologicRealizationSample[][] result =
                new SkyIslandLithologicRealizationSample[SAMPLE][SAMPLE];
        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                result[iz][ix] =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, DEPTH));
            }
        }
        return result;
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicRealizationSample[][] samples,
            Stats stats) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                role + " / key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "depth=%.2f material=%d contact=%d",
                        DEPTH,
                        stats.materialSamples(),
                        stats.contactSamples()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "means M=%.2f F=%.2f A=%.2f W=%.2f N=%.2f",
                        stats.mean(SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX),
                        stats.mean(SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX),
                        stats.mean(SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT),
                        stats.mean(SkyIslandLithologicRealizationChannel.WATER_CONDITIONING),
                        stats.mean(SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE)),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "MASSIVE", 0, MAP, 64);
        centered(g, "FABRIC", MAP, MAP, 64);
        centered(g, "ALTERATION", 2 * MAP, MAP, 64);
        centered(g, "WATER", 3 * MAP, MAP, 64);
        centered(g, "MINERAL", 4 * MAP, MAP, 64);
        centered(g, "CONTACT", 5 * MAP, MAP, 64);

        render(image, 0, samples, Panel.MASSIVE);
        render(image, MAP, samples, Panel.FABRIC);
        render(image, 2 * MAP, samples, Panel.ALTERATION);
        render(image, 3 * MAP, samples, Panel.WATER);
        render(image, 4 * MAP, samples, Panel.MINERAL);
        render(image, 5 * MAP, samples, Panel.CONTACT);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void render(
            BufferedImage image,
            int offsetX,
            SkyIslandLithologicRealizationSample[][] samples,
            Panel panel) {
        int height = samples.length;
        int width = samples[0].length;
        for (int py = 0; py < MAP; py++) {
            int sy = Math.min(
                    height - 1,
                    (int) Math.round(py * (height - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx = Math.min(
                        width - 1,
                        (int) Math.round(px * (width - 1.0) / (MAP - 1.0)));
                SkyIslandLithologicRealizationSample sample = samples[sy][sx];
                double value = panel.value(sample);
                if (!sample.materialPresent() || value <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(Color.WHITE, panel.color(), 0.18 + 0.82 * value).getRGB());
            }
        }
    }

    private static Stats stats(SkyIslandLithologicRealizationSample[][] samples) {
        double[] sums = new double[SkyIslandLithologicRealizationChannel.values().length];
        double[] peaks = new double[sums.length];
        int material = 0;
        int contacts = 0;
        for (SkyIslandLithologicRealizationSample[] row : samples) {
            for (SkyIslandLithologicRealizationSample sample : row) {
                if (!sample.materialPresent()) {
                    continue;
                }
                material++;
                if (sample.contactActive()) {
                    contacts++;
                }
                for (SkyIslandLithologicRealizationChannel channel :
                        SkyIslandLithologicRealizationChannel.values()) {
                    double value = sample.channel(channel);
                    sums[channel.ordinal()] += value;
                    peaks[channel.ordinal()] =
                            Math.max(peaks[channel.ordinal()], value);
                }
            }
        }
        return new Stats(material, contacts, sums, peaks);
    }

    private static Color blend(Color low, Color high, double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return new Color(
                channel(low.getRed() + (high.getRed() - low.getRed()) * t),
                channel(low.getGreen() + (high.getGreen() - low.getGreen()) * t),
                channel(low.getBlue() + (high.getBlue() - low.getBlue()) * t));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private enum Panel {
        MASSIVE(new Color(119, 104, 91)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.massiveMatrix();
            }
        },
        FABRIC(new Color(75, 126, 91)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.fabricRichMatrix();
            }
        },
        ALTERATION(new Color(174, 96, 57)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.alterationOverprint();
            }
        },
        WATER(new Color(52, 105, 164)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.waterConditioning();
            }
        },
        MINERAL(new Color(137, 87, 151)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.mineralBearingStructure();
            }
        },
        CONTACT(new Color(50, 50, 50)) {
            @Override
            double value(SkyIslandLithologicRealizationSample sample) {
                return sample.contactActive()
                        ? Math.min(
                                1.0,
                                2.0
                                        * Math.min(
                                                sample.firstAssemblageWeight(),
                                                sample.secondAssemblageWeight()))
                        : 0.0;
            }
        };

        private final Color color;

        Panel(Color color) {
            this.color = color;
        }

        Color color() {
            return color;
        }

        abstract double value(SkyIslandLithologicRealizationSample sample);
    }

    private record Stats(
            int materialSamples,
            int contactSamples,
            double[] sums,
            double[] peaks) {
        double mean(SkyIslandLithologicRealizationChannel channel) {
            return materialSamples == 0
                    ? 0.0
                    : sums[channel.ordinal()] / materialSamples;
        }

        double peak(SkyIslandLithologicRealizationChannel channel) {
            return peaks[channel.ordinal()];
        }
    }

    private record Selection(String role, long key) {}
}
