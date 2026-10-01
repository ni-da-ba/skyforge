package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
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

/** Generates deterministic AUTH-0030 exterior-connected cave-volume evidence. */
public final class AuthorshipExteriorConnectedCaveVolumeCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 150;
    private static final int HEADER = 70;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;
    private static final int PROJECTION_SAMPLES = 61;
    private static final int GRID_XZ = 73;
    private static final int GRID_DEPTH = 37;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(241, 241, 237);
    private static final Color BASE = new Color(71, 63, 82);
    private static final Color EXPOSURE = new Color(190, 94, 55);
    private static final Color ADDED = new Color(65, 128, 156);

    private AuthorshipExteriorConnectedCaveVolumeCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-exterior-connected-cave-volume-v1");
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
                "role,islandKey,morphology,caveSystems,connections,ownedSamples,"
                        + "basePositiveSamples,connectedPositiveSamples,addedPositiveSamples,"
                        + "addedVolumeFraction,connectedComponents,upperExposureBoundarySamples,"
                        + "undersideExposureBoundarySamples\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandExteriorConnectedCaveVolumeField field =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            Metrics metrics = measure(descriptor, field);
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

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(field.baseField().geometry().systems().size()).append(',')
                    .append(field.exposureGeometry().connectionCount()).append(',')
                    .append(metrics.ownedSamples()).append(',')
                    .append(metrics.basePositiveSamples()).append(',')
                    .append(metrics.connectedPositiveSamples()).append(',')
                    .append(metrics.addedPositiveSamples()).append(',')
                    .append(format(metrics.addedVolumeFraction())).append(',')
                    .append(metrics.connectedComponents()).append(',')
                    .append(metrics.upperExposureBoundarySamples()).append(',')
                    .append(metrics.undersideExposureBoundarySamples()).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0030</title>"
                        + "<h1>Exterior-connected continuous cave volume</h1>"
                        + "<p>BASE SECTION shows AUTH-0026 occupancy. CONNECTED SECTION shows "
                        + "AUTH-0030. ADDED TOP-DOWN shows x/z positions where AUTH-0030 adds positive "
                        + "void beyond AUTH-0026. PROVENANCE separates base-cave and exposure-connection "
                        + "winning samples.</p><img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field,
            Metrics metrics) {
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
                        "systems=%d connections=%d base=%d connected=%d added=%d components=%d",
                        field.baseField().geometry().systems().size(),
                        field.exposureGeometry().connectionCount(),
                        metrics.basePositiveSamples(),
                        metrics.connectedPositiveSamples(),
                        metrics.addedPositiveSamples(),
                        metrics.connectedComponents()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "added owned-volume fraction=%.6f boundary exposure upper=%d underside=%d",
                        metrics.addedVolumeFraction(),
                        metrics.upperExposureBoundarySamples(),
                        metrics.undersideExposureBoundarySamples()),
                7,
                50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "BASE SECTION", 0, MAP, 63);
        centered(g, "CONNECTED SECTION", MAP, MAP, 63);
        centered(g, "ADDED TOP-DOWN", 2 * MAP, MAP, 63);
        centered(g, "PROVENANCE", 3 * MAP, MAP, 63);

        drawSectionBackground(image, 0, descriptor);
        drawSectionBackground(image, MAP, descriptor);
        drawPlanBackground(image, 2 * MAP, descriptor);
        drawPlanBackground(image, 3 * MAP, descriptor);

        drawBaseSection(image, 0, descriptor, field.baseField());
        drawConnectedSection(image, MAP, descriptor, field);
        drawAddedTopDown(image, 2 * MAP, descriptor, field);
        drawProvenanceTopDown(image, 3 * MAP, descriptor, field);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void drawBaseSection(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField base) {
        for (int py = 0; py < MAP; py++) {
            double depth = py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * px / (MAP - 1.0);
                boolean inside = false;
                for (int iz = 0; iz < PROJECTION_SAMPLES; iz++) {
                    double z = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * iz / (PROJECTION_SAMPLES - 1.0);
                    if (base.contains(new SkyIslandSubsurfacePosition(x, z, depth))) {
                        inside = true;
                        break;
                    }
                }
                if (inside) {
                    image.setRGB(offsetX + px, HEADER + py, BASE.getRGB());
                }
            }
        }
    }

    private static void drawConnectedSection(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field) {
        for (int py = 0; py < MAP; py++) {
            double depth = py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -descriptor.nominalRadius()
                        + 2.0 * descriptor.nominalRadius() * px / (MAP - 1.0);
                boolean inside = false;
                for (int iz = 0; iz < PROJECTION_SAMPLES; iz++) {
                    double z = -descriptor.nominalRadius()
                            + 2.0 * descriptor.nominalRadius() * iz / (PROJECTION_SAMPLES - 1.0);
                    if (field.contains(new SkyIslandSubsurfacePosition(x, z, depth))) {
                        inside = true;
                        break;
                    }
                }
                if (inside) {
                    image.setRGB(offsetX + px, HEADER + py, BASE.getRGB());
                }
            }
        }
    }

    private static void drawAddedTopDown(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field) {
        double extent = descriptor.nominalRadius() * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                boolean added = false;
                for (int id = 0; id < PROJECTION_SAMPLES; id++) {
                    double depth = id / (PROJECTION_SAMPLES - 1.0);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    if (field.contains(position) && !field.baseField().contains(position)) {
                        added = true;
                        break;
                    }
                }
                if (added) {
                    image.setRGB(offsetX + px, HEADER + py, ADDED.getRGB());
                }
            }
        }
    }

    private static void drawProvenanceTopDown(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field) {
        double extent = descriptor.nominalRadius() * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandExteriorConnectedCaveVolumeSample best = null;
                for (int id = 0; id < PROJECTION_SAMPLES; id++) {
                    double depth = id / (PROJECTION_SAMPLES - 1.0);
                    SkyIslandExteriorConnectedCaveVolumeSample sample =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                    if (sample.inside()
                            && (best == null
                                    || sample.signedClearance() > best.signedClearance())) {
                        best = sample;
                    }
                }
                if (best != null) {
                    Color color = best.sourceKind()
                                    == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION
                            ? EXPOSURE
                            : BASE;
                    image.setRGB(offsetX + px, HEADER + py, color.getRGB());
                }
            }
        }
    }

    private static Metrics measure(
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        int total = GRID_XZ * GRID_DEPTH * GRID_XZ;
        boolean[] positive = new boolean[total];
        int owned = 0;
        int basePositive = 0;
        int connectedPositive = 0;
        int addedPositive = 0;

        for (int iz = 0; iz < GRID_XZ; iz++) {
            double z = -radius + 2.0 * radius * iz / (GRID_XZ - 1.0);
            for (int id = 0; id < GRID_DEPTH; id++) {
                double depth = id / (GRID_DEPTH - 1.0);
                for (int ix = 0; ix < GRID_XZ; ix++) {
                    double x = -radius + 2.0 * radius * ix / (GRID_XZ - 1.0);
                    SkyIslandLocalPosition surface = new SkyIslandLocalPosition(x, z);
                    if (semantic.interiority().sample(surface) <= 0.0) {
                        continue;
                    }
                    owned++;
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(surface, depth);
                    boolean baseInside = field.baseField().contains(position);
                    boolean connectedInside = field.contains(position);
                    if (baseInside) {
                        basePositive++;
                    }
                    if (connectedInside) {
                        connectedPositive++;
                        positive[index(ix, id, iz)] = true;
                    }
                    if (connectedInside && !baseInside) {
                        addedPositive++;
                    }
                }
            }
        }

        int upperBoundary = exposureBoundarySamples(field, descriptor, 0.0);
        int undersideBoundary = exposureBoundarySamples(field, descriptor, 1.0);
        return new Metrics(
                owned,
                basePositive,
                connectedPositive,
                addedPositive,
                owned == 0 ? 0.0 : addedPositive / (double) owned,
                components(positive),
                upperBoundary,
                undersideBoundary);
    }

    private static int exposureBoundarySamples(
            SkyIslandExteriorConnectedCaveVolumeField field,
            SkyIslandDescriptor descriptor,
            double depth) {
        double radius = descriptor.nominalRadius();
        int count = 0;
        for (int iz = 0; iz < GRID_XZ; iz++) {
            double z = -radius + 2.0 * radius * iz / (GRID_XZ - 1.0);
            for (int ix = 0; ix < GRID_XZ; ix++) {
                double x = -radius + 2.0 * radius * ix / (GRID_XZ - 1.0);
                SkyIslandExteriorConnectedCaveVolumeSample sample =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                if (sample.inside()
                        && sample.sourceKind()
                                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int components(boolean[] positive) {
        boolean[] visited = new boolean[positive.length];
        int count = 0;
        int[][] offsets = {
            {-1, 0, 0}, {1, 0, 0},
            {0, -1, 0}, {0, 1, 0},
            {0, 0, -1}, {0, 0, 1}
        };
        for (int seed = 0; seed < positive.length; seed++) {
            if (!positive[seed] || visited[seed]) {
                continue;
            }
            count++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(seed);
            visited[seed] = true;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int ix = current % GRID_XZ;
                int id = (current / GRID_XZ) % GRID_DEPTH;
                int iz = current / (GRID_XZ * GRID_DEPTH);
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
        return count;
    }

    private static int index(int ix, int id, int iz) {
        return (iz * GRID_DEPTH + id) * GRID_XZ + ix;
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

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}

    private record Metrics(
            int ownedSamples,
            int basePositiveSamples,
            int connectedPositiveSamples,
            int addedPositiveSamples,
            double addedVolumeFraction,
            int connectedComponents,
            int upperExposureBoundarySamples,
            int undersideExposureBoundarySamples) {}
}
