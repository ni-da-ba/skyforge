package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLithologicRealizationChannel;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteCandidate;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteSelection;
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

/** Generates deterministic AUTH-0037 semantic palette-role evidence. */
public final class AuthorshipSemanticMaterialPaletteRolesCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 70;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipSemanticMaterialPaletteRolesCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-semantic-material-palette-roles-v1");
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
                "role,islandKey,morphology,materialSamples,primaryMassive,primaryFabric,"
                        + "secondaryEligible,alterationEligible,waterEligible,mineralEligible,"
                        + "meanCandidateCount,meanSecondaryCeiling,meanAlterationCeiling,"
                        + "meanWaterCeiling,meanMineralCeiling\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandSemanticMaterialPaletteField field =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            SkyIslandSemanticMaterialPaletteSelection[][] samples = sample(descriptor, field);
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
                    .append(stats.primaryMassive()).append(',')
                    .append(stats.primaryFabric()).append(',')
                    .append(stats.eligible(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)).append(',')
                    .append(stats.eligible(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT)).append(',')
                    .append(stats.eligible(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING)).append(',')
                    .append(stats.eligible(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE)).append(',')
                    .append(format(stats.meanCandidateCount())).append(',')
                    .append(format(stats.meanCeiling(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX))).append(',')
                    .append(format(stats.meanCeiling(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT))).append(',')
                    .append(format(stats.meanCeiling(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING))).append(',')
                    .append(format(stats.meanCeiling(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE))).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0037</title>"
                        + "<h1>Semantic material palette roles and selection constraints</h1>"
                        + "<p>All panels sample semantic depth 0.52. PRIMARY shows which AUTH-0036 "
                        + "host-matrix channel anchors the required primary role. SECONDARY, ALTERATION, "
                        + "WATER, and MINERAL show optional-role eligibility weighted by support. "
                        + "ROLE COUNT shows how many backend-neutral palette roles are eligible. "
                        + "No panel represents concrete blocks or registry materials.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static SkyIslandSemanticMaterialPaletteSelection[][] sample(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticMaterialPaletteField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandSemanticMaterialPaletteSelection[][] result =
                new SkyIslandSemanticMaterialPaletteSelection[SAMPLE][SAMPLE];
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
            SkyIslandSemanticMaterialPaletteSelection[][] samples,
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
                        "depth=%.2f material=%d mean roles=%.2f",
                        DEPTH,
                        stats.materialSamples(),
                        stats.meanCandidateCount()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "primary massive=%d fabric=%d optional S/A/W/N=%d/%d/%d/%d",
                        stats.primaryMassive(),
                        stats.primaryFabric(),
                        stats.eligible(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX),
                        stats.eligible(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT),
                        stats.eligible(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING),
                        stats.eligible(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE)),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "PRIMARY", 0, MAP, 64);
        centered(g, "SECONDARY", MAP, MAP, 64);
        centered(g, "ALTERATION", 2 * MAP, MAP, 64);
        centered(g, "WATER", 3 * MAP, MAP, 64);
        centered(g, "MINERAL", 4 * MAP, MAP, 64);
        centered(g, "ROLE COUNT", 5 * MAP, MAP, 64);

        render(image, 0, samples, Panel.PRIMARY);
        render(image, MAP, samples, Panel.SECONDARY);
        render(image, 2 * MAP, samples, Panel.ALTERATION);
        render(image, 3 * MAP, samples, Panel.WATER);
        render(image, 4 * MAP, samples, Panel.MINERAL);
        render(image, 5 * MAP, samples, Panel.ROLE_COUNT);

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
            SkyIslandSemanticMaterialPaletteSelection[][] samples,
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
                SkyIslandSemanticMaterialPaletteSelection sample = samples[sy][sx];
                if (!sample.materialPresent()) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                Pixel pixel = panel.pixel(sample);
                if (pixel.value() <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(Color.WHITE, pixel.color(), 0.20 + 0.80 * pixel.value()).getRGB());
            }
        }
    }

    private static Stats stats(SkyIslandSemanticMaterialPaletteSelection[][] samples) {
        int material = 0;
        int primaryMassive = 0;
        int primaryFabric = 0;
        int[] eligible = new int[SkyIslandSemanticMaterialPaletteRole.values().length];
        double[] ceilingSums = new double[eligible.length];
        int candidateCount = 0;

        for (SkyIslandSemanticMaterialPaletteSelection[] row : samples) {
            for (SkyIslandSemanticMaterialPaletteSelection selection : row) {
                if (!selection.materialPresent()) {
                    continue;
                }
                material++;
                candidateCount += selection.candidates().size();
                SkyIslandSemanticMaterialPaletteCandidate primary =
                        selection.candidate(
                                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)
                                .orElseThrow();
                if (primary.sourceChannel()
                        == SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX) {
                    primaryMassive++;
                } else {
                    primaryFabric++;
                }

                for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                        selection.candidates()) {
                    eligible[candidate.role().ordinal()]++;
                    ceilingSums[candidate.role().ordinal()] += candidate.expressionCeiling();
                }
            }
        }
        return new Stats(
                material,
                primaryMassive,
                primaryFabric,
                candidateCount,
                eligible,
                ceilingSums);
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
        PRIMARY(new Color(118, 101, 88)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                SkyIslandSemanticMaterialPaletteCandidate candidate =
                        sample.candidate(
                                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)
                                .orElseThrow();
                Color color = candidate.sourceChannel()
                                == SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX
                        ? new Color(71, 126, 89)
                        : color();
                return new Pixel(color, candidate.support());
            }
        },
        SECONDARY(new Color(101, 130, 105)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                return candidatePixel(
                        sample,
                        SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX,
                        color());
            }
        },
        ALTERATION(new Color(174, 96, 57)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                return candidatePixel(
                        sample,
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        color());
            }
        },
        WATER(new Color(52, 105, 164)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                return candidatePixel(
                        sample,
                        SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                        color());
            }
        },
        MINERAL(new Color(137, 87, 151)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                return candidatePixel(
                        sample,
                        SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                        color());
            }
        },
        ROLE_COUNT(new Color(68, 68, 68)) {
            @Override
            Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample) {
                return new Pixel(
                        color(),
                        Math.min(1.0, sample.candidates().size() / 5.0));
            }
        };

        private final Color color;

        Panel(Color color) {
            this.color = color;
        }

        Color color() {
            return color;
        }

        abstract Pixel pixel(SkyIslandSemanticMaterialPaletteSelection sample);

        static Pixel candidatePixel(
                SkyIslandSemanticMaterialPaletteSelection sample,
                SkyIslandSemanticMaterialPaletteRole role,
                Color color) {
            return sample.candidate(role)
                    .map(candidate -> new Pixel(color, candidate.support()))
                    .orElseGet(() -> new Pixel(color, 0.0));
        }
    }

    private record Pixel(Color color, double value) {}

    private record Stats(
            int materialSamples,
            int primaryMassive,
            int primaryFabric,
            int candidateCount,
            int[] eligibleCounts,
            double[] ceilingSums) {
        int eligible(SkyIslandSemanticMaterialPaletteRole role) {
            return eligibleCounts[role.ordinal()];
        }

        double meanCandidateCount() {
            return materialSamples == 0 ? 0.0 : (double) candidateCount / materialSamples;
        }

        double meanCeiling(SkyIslandSemanticMaterialPaletteRole role) {
            int count = eligible(role);
            return count == 0 ? 0.0 : ceilingSums[role.ordinal()] / count;
        }
    }

    private record Selection(String role, long key) {}
}
