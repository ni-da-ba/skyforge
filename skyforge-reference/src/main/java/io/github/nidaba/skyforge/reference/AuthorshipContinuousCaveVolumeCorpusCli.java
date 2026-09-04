package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveGeometryPlan;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0026 continuous cave-volume evidence. */
public final class AuthorshipContinuousCaveVolumeCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final long PROVINCE = 8L;
    private static final long CLUSTER = 81L;
    private static final int MAP = 150;
    private static final int HEADER = 70;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;
    private static final int PROJECTION_SAMPLES = 17;
    private static final int GRID_XZ = 37;
    private static final int GRID_DEPTH = 19;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(241, 241, 237);
    private static final Color CAVE = new Color(62, 55, 70);
    private static final Color BOUNDARY = new Color(177, 132, 197);

    private AuthorshipContinuousCaveVolumeCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-continuous-cave-volume-v1");
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
                "role,islandKey,morphology,systems,chambers,passages,ownedSamples,"
                        + "positiveSamples,caveVolumeFraction,occupiedComponents,"
                        + "maximumPositiveClearance,nearestExteriorClearance\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, PROVINCE, CLUSTER, selection.key()));
            SkyIslandCaveVolumeField field = SkyIslandCaveVolumeField.create(descriptor);
            VolumeMetrics metrics = measure(descriptor, field);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, field, metrics);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.key() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            SkyIslandCaveGeometryPlan geometry = field.geometry();
            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(geometry.systems().size()).append(',')
                    .append(geometry.chamberCount()).append(',')
                    .append(geometry.passageCount()).append(',')
                    .append(metrics.ownedSamples()).append(',')
                    .append(metrics.positiveSamples()).append(',')
                    .append(format(metrics.caveFraction())).append(',')
                    .append(metrics.components()).append(',')
                    .append(format(metrics.maximumPositive())).append(',')
                    .append(format(metrics.nearestExterior())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0026</title>"
                        + "<h1>Continuous cave-volume field</h1>"
                        + "<p>SECTION OCCUPANCY is maximum signed cave clearance through z. "
                        + "TOP-DOWN OCCUPANCY is maximum clearance through semantic depth. "
                        + "SECTION CLEARANCE displays the continuous field around the zero boundary. "
                        + "SYSTEM PROVENANCE colors positive top-down samples by winning cave-system id. "
                        + "All projections are evidence views; the field remains fully 3D.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField field,
            VolumeMetrics metrics) {
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
                        "systems=%d chambers=%d passages=%d  sampled cave fraction=%.4f  components=%d",
                        field.geometry().systems().size(),
                        field.geometry().chamberCount(),
                        field.geometry().passageCount(),
                        metrics.caveFraction(),
                        metrics.components()),
                7,
                35);
        g.drawString("positive = authored cave void; zero = cave boundary; negative = exterior", 7, 50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "SECTION OCCUPANCY", 0, MAP, 63);
        centered(g, "TOP-DOWN OCCUPANCY", MAP, MAP, 63);
        centered(g, "SECTION CLEARANCE", 2 * MAP, MAP, 63);
        centered(g, "SYSTEM PROVENANCE", 3 * MAP, MAP, 63);

        Projection section = sectionProjection(descriptor, field);
        Projection topDown = topDownProjection(descriptor, field);

        drawSectionBackground(image, 0, descriptor);
        drawPlanBackground(image, MAP, descriptor);
        drawSectionBackground(image, 2 * MAP, descriptor);
        drawPlanBackground(image, 3 * MAP, descriptor);

        drawOccupancy(image, 0, section, true);
        drawOccupancy(image, MAP, topDown, true);
        drawClearance(image, 2 * MAP, section);
        drawProvenance(image, 3 * MAP, topDown);

        if (field.geometry().systems().isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(95, 95, 95));
            for (int panel = 0; panel < 4; panel++) {
                centered(g, "NO AUTHORED CAVE VOLUME", panel * MAP, MAP, HEADER + MAP / 2);
            }
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static Projection sectionProjection(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField field) {
        double[][] values = new double[MAP][MAP];
        int[][] systems = new int[MAP][MAP];
        for (int py = 0; py < MAP; py++) {
            double depth = py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * px / (MAP - 1.0);
                SkyIslandCaveVolumeSample best = SkyIslandCaveVolumeSample.outside(-1.0e9);
                for (int iz = 0; iz < PROJECTION_SAMPLES; iz++) {
                    double z = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * iz / (PROJECTION_SAMPLES - 1.0);
                    SkyIslandCaveVolumeSample sample =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                    if (sample.signedClearance() > best.signedClearance()) {
                        best = sample;
                    }
                }
                values[py][px] = best.signedClearance();
                systems[py][px] = best.inside() ? best.systemId() : -1;
            }
        }
        return new Projection(values, systems);
    }

    private static Projection topDownProjection(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField field) {
        double[][] values = new double[MAP][MAP];
        int[][] systems = new int[MAP][MAP];
        double extent = descriptor.nominalRadius() * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandCaveVolumeSample best = SkyIslandCaveVolumeSample.outside(-1.0e9);
                for (int id = 0; id < PROJECTION_SAMPLES; id++) {
                    double depth = id / (PROJECTION_SAMPLES - 1.0);
                    SkyIslandCaveVolumeSample sample =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                    if (sample.signedClearance() > best.signedClearance()) {
                        best = sample;
                    }
                }
                values[py][px] = best.signedClearance();
                systems[py][px] = best.inside() ? best.systemId() : -1;
            }
        }
        return new Projection(values, systems);
    }

    private static void drawOccupancy(
            BufferedImage image,
            int offsetX,
            Projection projection,
            boolean strong) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double value = projection.values()[py][px];
                if (value > 0.0) {
                    double strength = Math.min(1.0, 0.62 + 0.38 * value);
                    image.setRGB(
                            offsetX + px,
                            HEADER + py,
                            blend(OWNED, CAVE, strong ? strength : strength * 0.7).getRGB());
                }
            }
        }
    }

    private static void drawClearance(
            BufferedImage image,
            int offsetX,
            Projection projection) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double value = projection.values()[py][px];
                if (value > 0.0) {
                    image.setRGB(
                            offsetX + px,
                            HEADER + py,
                            blend(BOUNDARY, CAVE, Math.min(1.0, value)).getRGB());
                } else if (value > -0.55) {
                    image.setRGB(
                            offsetX + px,
                            HEADER + py,
                            blend(OWNED, BOUNDARY, 0.22 * (1.0 + value / 0.55)).getRGB());
                }
            }
        }
    }

    private static void drawProvenance(
            BufferedImage image,
            int offsetX,
            Projection projection) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                int system = projection.systems()[py][px];
                if (system >= 0) {
                    Color color = systemColor(system);
                    image.setRGB(
                            offsetX + px,
                            HEADER + py,
                            blend(OWNED, color, 0.88).getRGB());
                }
            }
        }
    }

    private static void drawSectionBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double x = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * px / (MAP - 1.0);
                boolean owned = semantic.interiority().sample(new SkyIslandLocalPosition(x, 0.0)) > 0.0;
                image.setRGB(offsetX + px, HEADER + py, (owned ? OWNED : OUTSIDE).getRGB());
            }
        }
    }

    private static void drawPlanBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double extent = descriptor.nominalRadius() * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                boolean owned = semantic.interiority().sample(new SkyIslandLocalPosition(x, z)) > 0.0;
                image.setRGB(offsetX + px, HEADER + py, (owned ? OWNED : OUTSIDE).getRGB());
            }
        }
    }

    private static VolumeMetrics measure(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField field) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        int total = GRID_XZ * GRID_DEPTH * GRID_XZ;
        boolean[] positive = new boolean[total];
        int ownedSamples = 0;
        int positiveSamples = 0;
        double maximumPositive = 0.0;
        double nearestExterior = Double.NEGATIVE_INFINITY;

        for (int iz = 0; iz < GRID_XZ; iz++) {
            double z = -descriptor.nominalRadius()
                    + 2.0 * descriptor.nominalRadius() * iz / (GRID_XZ - 1.0);
            for (int id = 0; id < GRID_DEPTH; id++) {
                double depth = id / (GRID_DEPTH - 1.0);
                for (int ix = 0; ix < GRID_XZ; ix++) {
                    double x = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * ix / (GRID_XZ - 1.0);
                    int index = index(ix, id, iz);
                    SkyIslandLocalPosition surface = new SkyIslandLocalPosition(x, z);
                    if (semantic.interiority().sample(surface) <= 0.0) {
                        continue;
                    }
                    ownedSamples++;
                    double value = field.signedClearance(
                            new SkyIslandSubsurfacePosition(surface, depth));
                    if (value > 0.0) {
                        positive[index] = true;
                        positiveSamples++;
                        maximumPositive = Math.max(maximumPositive, value);
                    } else {
                        nearestExterior = Math.max(nearestExterior, value);
                    }
                }
            }
        }

        return new VolumeMetrics(
                ownedSamples,
                positiveSamples,
                ownedSamples == 0 ? 0.0 : positiveSamples / (double) ownedSamples,
                components(positive),
                maximumPositive,
                nearestExterior == Double.NEGATIVE_INFINITY ? 0.0 : nearestExterior);
    }

    private static int components(boolean[] positive) {
        boolean[] visited = new boolean[positive.length];
        int components = 0;
        int[][] offsets = {
            {-1, 0, 0}, {1, 0, 0},
            {0, -1, 0}, {0, 1, 0},
            {0, 0, -1}, {0, 0, 1}
        };

        for (int seed = 0; seed < positive.length; seed++) {
            if (!positive[seed] || visited[seed]) {
                continue;
            }
            components++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(seed);
            visited[seed] = true;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int ix = xIndex(current);
                int id = depthIndex(current);
                int iz = zIndex(current);
                for (int[] offset : offsets) {
                    int nx = ix + offset[0];
                    int nd = id + offset[1];
                    int nz = iz + offset[2];
                    if (nx < 0 || nd < 0 || nz < 0
                            || nx >= GRID_XZ || nd >= GRID_DEPTH || nz >= GRID_XZ) {
                        continue;
                    }
                    int neighbor = index(nx, nd, nz);
                    if (positive[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true;
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    private static int index(int ix, int id, int iz) {
        return (iz * GRID_DEPTH + id) * GRID_XZ + ix;
    }

    private static int xIndex(int index) {
        return index % GRID_XZ;
    }

    private static int depthIndex(int index) {
        return (index / GRID_XZ) % GRID_DEPTH;
    }

    private static int zIndex(int index) {
        return index / (GRID_XZ * GRID_DEPTH);
    }

    private static Color systemColor(int system) {
        return switch (Math.floorMod(system, 5)) {
            case 0 -> new Color(95, 72, 145);
            case 1 -> new Color(45, 123, 155);
            case 2 -> new Color(180, 101, 63);
            case 3 -> new Color(70, 135, 85);
            default -> new Color(160, 90, 125);
        };
    }

    private static Color blend(Color background, Color foreground, double strength) {
        double t = Math.max(0.0, Math.min(1.0, strength));
        int red = (int) Math.round(background.getRed() + (foreground.getRed() - background.getRed()) * t);
        int green = (int) Math.round(background.getGreen() + (foreground.getGreen() - background.getGreen()) * t);
        int blue = (int) Math.round(background.getBlue() + (foreground.getBlue() - background.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}

    private record Projection(double[][] values, int[][] systems) {}

    private record VolumeMetrics(
            int ownedSamples,
            int positiveSamples,
            double caveFraction,
            int components,
            double maximumPositive,
            double nearestExterior) {}
}
