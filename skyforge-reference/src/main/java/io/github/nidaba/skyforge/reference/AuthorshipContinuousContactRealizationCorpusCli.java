package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContact;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactKind;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactPatch;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactRealization;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactRealizationField;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactRealizationSample;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactRealizationPlanner;
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

/** Generates deterministic AUTH-0035 continuous contact-realization evidence. */
public final class AuthorshipContinuousContactRealizationCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 126;
    private static final int HEADER = 70;
    private static final int PANELS = 6;
    private static final int SAMPLE = 30;
    private static final int DEPTH_PROJECTION_SAMPLES = 5;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipContinuousContactRealizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-continuous-contact-realization-v1");
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
                "role,islandKey,morphology,contacts,patches,minHalfWidth,meanHalfWidth,maxHalfWidth,"
                        + "gradationalContacts,fabricContacts,alterationFronts,hydrologicFronts,mineralizationFronts,"
                        + "meanPatchSharpness,meanCaveExposure\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandLithologicContactRealizationField field =
                    SkyIslandLithologicContactRealizationField.create(descriptor);
            SkyIslandLithologicContactRealizationPlan plan = field.plan();
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, field);

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
                    .append(plan.realizations().size()).append(',')
                    .append(plan.patchCount()).append(',')
                    .append(format(plan.minimumHalfWidth())).append(',')
                    .append(format(plan.meanHalfWidth())).append(',')
                    .append(format(plan.maximumHalfWidth())).append(',')
                    .append(contactCount(plan, SkyIslandLithologicContactKind.GRADATIONAL_CONTACT)).append(',')
                    .append(contactCount(plan, SkyIslandLithologicContactKind.HOST_FABRIC_CONTACT)).append(',')
                    .append(contactCount(plan, SkyIslandLithologicContactKind.ALTERATION_FRONT)).append(',')
                    .append(contactCount(plan, SkyIslandLithologicContactKind.HYDROLOGIC_FRONT)).append(',')
                    .append(contactCount(plan, SkyIslandLithologicContactKind.MINERALIZATION_FRONT)).append(',')
                    .append(format(meanSharpness(plan))).append(',')
                    .append(format(meanCaveExposure(plan))).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0035</title>"
                        + "<h1>Continuous lithologic contact realization</h1>"
                        + "<p>PLAN is maximum contact influence through five semantic-depth slices. "
                        + "SECTION samples a central x/depth plane. ALTERATION, WATER, MINERAL, and "
                        + "CAVE show continuous transition channels from the same realized contact "
                        + "field. White is host material outside compact contact support or authored "
                        + "void/unowned space.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicContactRealizationField field) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandLithologicContactRealizationPlan plan = field.plan();
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
                        "contacts=%d patches=%d width=%.3f..%.3f mean=%.3f",
                        plan.realizations().size(),
                        plan.patchCount(),
                        plan.minimumHalfWidth(),
                        plan.maximumHalfWidth(),
                        plan.meanHalfWidth()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "mean sharpness=%.3f cave-coupling=%.3f",
                        meanSharpness(plan),
                        meanCaveExposure(plan)),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "PLAN", 0, MAP, 64);
        centered(g, "SECTION", MAP, MAP, 64);
        centered(g, "ALTERATION", 2 * MAP, MAP, 64);
        centered(g, "WATER", 3 * MAP, MAP, 64);
        centered(g, "MINERAL", 4 * MAP, MAP, 64);
        centered(g, "CAVE", 5 * MAP, MAP, 64);

        SkyIslandLithologicContactRealizationSample[][] planSamples =
                projectPlan(descriptor, field);
        SkyIslandLithologicContactRealizationSample[][] sectionSamples =
                section(descriptor, field);

        renderSamples(image, 0, planSamples, Channel.CONTACT);
        renderSamples(image, MAP, sectionSamples, Channel.CONTACT);
        renderSamples(image, 2 * MAP, planSamples, Channel.ALTERATION);
        renderSamples(image, 3 * MAP, planSamples, Channel.WATER);
        renderSamples(image, 4 * MAP, planSamples, Channel.MINERAL);
        renderSamples(image, 5 * MAP, planSamples, Channel.CAVE);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static SkyIslandLithologicContactRealizationSample[][] projectPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicContactRealizationField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandLithologicContactRealizationSample[][] result =
                new SkyIslandLithologicContactRealizationSample[SAMPLE][SAMPLE];
        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                SkyIslandLithologicContactRealizationSample best = null;
                for (int id = 0; id < DEPTH_PROJECTION_SAMPLES; id++) {
                    double depth = id / (DEPTH_PROJECTION_SAMPLES - 1.0);
                    SkyIslandLithologicContactRealizationSample sample =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                    if (best == null || sample.contactInfluence() > best.contactInfluence()) {
                        best = sample;
                    }
                }
                result[iz][ix] = best;
            }
        }
        return result;
    }

    private static SkyIslandLithologicContactRealizationSample[][] section(
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicContactRealizationField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandLithologicContactRealizationSample[][] result =
                new SkyIslandLithologicContactRealizationSample[SAMPLE][SAMPLE];
        for (int id = 0; id < SAMPLE; id++) {
            double depth = id / (SAMPLE - 1.0);
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                result[id][ix] =
                        field.sample(new SkyIslandSubsurfacePosition(x, 0.0, depth));
            }
        }
        return result;
    }

    private static void renderSamples(
            BufferedImage image,
            int offsetX,
            SkyIslandLithologicContactRealizationSample[][] samples,
            Channel channel) {
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
                SkyIslandLithologicContactRealizationSample sample = samples[sy][sx];
                double value = channel.value(sample);
                if (value <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(Color.WHITE, channel.color(), 0.18 + 0.82 * value).getRGB());
            }
        }
    }

    private static long contactCount(
            SkyIslandLithologicContactRealizationPlan plan,
            SkyIslandLithologicContactKind kind) {
        return plan.realizations().stream()
                .map(SkyIslandLithologicContactRealization::contact)
                .filter(contact -> contact.kind() == kind)
                .count();
    }

    private static double meanSharpness(
            SkyIslandLithologicContactRealizationPlan plan) {
        return plan.realizations().stream()
                .flatMap(realization -> realization.patches().stream())
                .mapToDouble(SkyIslandLithologicContactPatch::transitionSharpness)
                .average()
                .orElse(0.0);
    }

    private static double meanCaveExposure(
            SkyIslandLithologicContactRealizationPlan plan) {
        return plan.realizations().stream()
                .flatMap(realization -> realization.patches().stream())
                .mapToDouble(SkyIslandLithologicContactPatch::caveExposureInfluence)
                .average()
                .orElse(0.0);
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

    private enum Channel {
        CONTACT(new Color(65, 65, 65)) {
            @Override
            double value(SkyIslandLithologicContactRealizationSample sample) {
                return sample.contactInfluence();
            }
        },
        ALTERATION(new Color(174, 96, 57)) {
            @Override
            double value(SkyIslandLithologicContactRealizationSample sample) {
                return sample.alterationTransition();
            }
        },
        WATER(new Color(52, 105, 164)) {
            @Override
            double value(SkyIslandLithologicContactRealizationSample sample) {
                return sample.hydrologicTransition();
            }
        },
        MINERAL(new Color(137, 87, 151)) {
            @Override
            double value(SkyIslandLithologicContactRealizationSample sample) {
                return sample.mineralizationTransition();
            }
        },
        CAVE(new Color(82, 132, 92)) {
            @Override
            double value(SkyIslandLithologicContactRealizationSample sample) {
                return sample.caveExposureCoupling();
            }
        };

        private final Color color;

        Channel(Color color) {
            this.color = color;
        }

        Color color() {
            return color;
        }

        abstract double value(SkyIslandLithologicContactRealizationSample sample);
    }

    private record Selection(String role, long key) {}
}
