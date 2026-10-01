package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfaceMaterialFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfaceMaterialSample;
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

/** Generates deterministic AUTH-0031 continuous subsurface material-character evidence. */
public final class AuthorshipSubsurfaceMaterialCharacterCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 138;
    private static final int HEADER = 68;
    private static final int PANELS = 5;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipSubsurfaceMaterialCharacterCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-subsurface-material-character-v1");
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
                "role,islandKey,morphology,materialSamples,voidSamples,"
                        + "meanIntegrity,meanAlteration,meanSaturation,meanMineralization,"
                        + "meanWallAlteration,maxWallAlteration\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
            Stats stats = measure(descriptor, material);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, material, stats);

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
                    .append(stats.voidSamples()).append(',')
                    .append(format(stats.meanIntegrity())).append(',')
                    .append(format(stats.meanAlteration())).append(',')
                    .append(format(stats.meanSaturation())).append(',')
                    .append(format(stats.meanMineralization())).append(',')
                    .append(format(stats.meanWallAlteration())).append(',')
                    .append(format(stats.maxWallAlteration())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0031</title>"
                        + "<h1>Subsurface material character</h1>"
                        + "<p>All panels are x/depth sections through island-local z=0. "
                        + "Depth increases downward. White is outside current ownership; black is "
                        + "authored cave void. Values are semantic realization tendencies, not named "
                        + "rocks, ores, or backend block palettes.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfaceMaterialFieldSet material,
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
                        "rock=%.3f perm=%.3f hydro=%.3f erosion=%.3f  material=%d void=%d",
                        descriptor.rockCompetence(),
                        descriptor.permeability(),
                        descriptor.hydrologicalPotential(),
                        descriptor.erosionMaturity(),
                        stats.materialSamples(),
                        stats.voidSamples()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "means integrity=%.3f alteration=%.3f saturation=%.3f mineral=%.3f wall=%.3f",
                        stats.meanIntegrity(),
                        stats.meanAlteration(),
                        stats.meanSaturation(),
                        stats.meanMineralization(),
                        stats.meanWallAlteration()),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "MATRIX INTEGRITY", 0, MAP, 62);
        centered(g, "ALTERATION", MAP, MAP, 62);
        centered(g, "SATURATION", 2 * MAP, MAP, 62);
        centered(g, "MINERALIZATION", 3 * MAP, MAP, 62);
        centered(g, "CAVE-WALL ALTER.", 4 * MAP, MAP, 62);

        renderSection(image, 0, descriptor, material, Field.INTEGRITY);
        renderSection(image, MAP, descriptor, material, Field.ALTERATION);
        renderSection(image, 2 * MAP, descriptor, material, Field.SATURATION);
        renderSection(image, 3 * MAP, descriptor, material, Field.MINERALIZATION);
        renderSection(image, 4 * MAP, descriptor, material, Field.WALL);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void renderSection(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfaceMaterialFieldSet material,
            Field field) {
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.03;
        for (int py = 0; py < MAP; py++) {
            double depth = py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandSubsurfaceMaterialSample sample =
                        material.sample(new SkyIslandSubsurfacePosition(x, 0.0, depth));
                if (!sample.owned()) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                if (!sample.materialPresent()) {
                    image.setRGB(offsetX + px, HEADER + py, Color.BLACK.getRGB());
                    continue;
                }
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        field.color(field.value(sample)).getRGB());
            }
        }
    }

    private static Stats measure(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfaceMaterialFieldSet material) {
        double radius = descriptor.nominalRadius();
        long materialCount = 0L;
        long voidCount = 0L;
        double integrity = 0.0;
        double alteration = 0.0;
        double saturation = 0.0;
        double mineralization = 0.0;
        double wall = 0.0;
        double maxWall = 0.0;

        for (int iz = 0; iz <= 30; iz++) {
            double z = -radius + 2.0 * radius * iz / 30.0;
            for (int ix = 0; ix <= 30; ix++) {
                double x = -radius + 2.0 * radius * ix / 30.0;
                for (int id = 0; id <= 16; id++) {
                    SkyIslandSubsurfaceMaterialSample sample = material.sample(
                            new SkyIslandSubsurfacePosition(x, z, id / 16.0));
                    if (!sample.owned()) {
                        continue;
                    }
                    if (!sample.materialPresent()) {
                        voidCount++;
                        continue;
                    }
                    materialCount++;
                    integrity += sample.matrixIntegrity();
                    alteration += sample.alteration();
                    saturation += sample.saturation();
                    mineralization += sample.mineralizationTendency();
                    wall += sample.caveWallAlteration();
                    maxWall = Math.max(maxWall, sample.caveWallAlteration());
                }
            }
        }
        if (materialCount == 0L) {
            throw new IllegalStateException("material specimen had no material-present samples");
        }
        return new Stats(
                materialCount,
                voidCount,
                integrity / materialCount,
                alteration / materialCount,
                saturation / materialCount,
                mineralization / materialCount,
                wall / materialCount,
                maxWall);
    }

    private static Color ramp(double value, Color low, Color high) {
        double t = Math.max(0.0, Math.min(1.0, value));
        int red = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * t);
        int green = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * t);
        int blue = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private enum Field {
        INTEGRITY {
            @Override
            double value(SkyIslandSubsurfaceMaterialSample sample) {
                return sample.matrixIntegrity();
            }

            @Override
            Color color(double value) {
                return ramp(value, new Color(95, 75, 62), new Color(224, 217, 195));
            }
        },
        ALTERATION {
            @Override
            double value(SkyIslandSubsurfaceMaterialSample sample) {
                return sample.alteration();
            }

            @Override
            Color color(double value) {
                return ramp(value, new Color(220, 216, 202), new Color(157, 90, 58));
            }
        },
        SATURATION {
            @Override
            double value(SkyIslandSubsurfaceMaterialSample sample) {
                return sample.saturation();
            }

            @Override
            Color color(double value) {
                return ramp(value, new Color(205, 194, 167), new Color(48, 107, 164));
            }
        },
        MINERALIZATION {
            @Override
            double value(SkyIslandSubsurfaceMaterialSample sample) {
                return sample.mineralizationTendency();
            }

            @Override
            Color color(double value) {
                return ramp(value, new Color(232, 229, 214), new Color(129, 91, 146));
            }
        },
        WALL {
            @Override
            double value(SkyIslandSubsurfaceMaterialSample sample) {
                return sample.caveWallAlteration();
            }

            @Override
            Color color(double value) {
                return ramp(value, new Color(244, 243, 238), new Color(44, 135, 111));
            }
        };

        abstract double value(SkyIslandSubsurfaceMaterialSample sample);

        abstract Color color(double value);
    }

    private record Selection(String role, long key) {}

    private record Stats(
            long materialSamples,
            long voidSamples,
            double meanIntegrity,
            double meanAlteration,
            double meanSaturation,
            double meanMineralization,
            double meanWallAlteration,
            double maxWallAlteration) {}
}
