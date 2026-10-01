package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegion;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionCell;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionKind;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionPlan;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionPlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0023 mesoscale geological-region evidence. */
public final class AuthorshipMesoscaleGeologyCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final long PROVINCE = 8L;
    private static final long CLUSTER = 81L;
    private static final int MAP = 180;
    private static final int HEADER = 64;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(239, 239, 235);
    private static final Color FRACTURE = new Color(205, 105, 58);
    private static final Color AQUIFER = new Color(49, 111, 175);
    private static final Color VOID = new Color(105, 72, 136);

    private AuthorshipMesoscaleGeologyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-mesoscale-geology-v1");
        Files.createDirectories(out);

        List<Candidate> candidates = new ArrayList<>();
        for (long key = 0; key < 4096; key++) {
            candidates.add(new Candidate(
                    key,
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, PROVINCE, CLUSTER, key))));
        }

        Set<Long> used = new HashSet<>();
        List<Selection> selections = List.of(
                select("competent", candidates, used, SkyIslandDescriptor::rockCompetence),
                select("weak", candidates, used, d -> 1.0 - d.rockCompetence()),
                select("permeable", candidates, used, SkyIslandDescriptor::permeability),
                select("hydrologic", candidates, used, SkyIslandDescriptor::hydrologicalPotential),
                select("eroded", candidates, used, SkyIslandDescriptor::erosionMaturity),
                select(
                        "spine",
                        candidates,
                        used,
                        d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.SPINE ? 2.0 : 0.0)
                                + d.erosionMaturity()));

        BufferedImage atlas =
                new BufferedImage(2 * SPECIMEN_WIDTH, 3 * SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,islandKey,morphology,rockCompetence,permeability,hydrology,erosion,"
                        + "structuralCorridors,fractureRegions,fractureCells,largestFracture,"
                        + "aquiferRegions,aquiferCells,largestAquifer,"
                        + "voidRegions,voidCells,largestVoid\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = selection.candidate().descriptor();
            SkyIslandGeologicRegionPlan plan = SkyIslandGeologicRegionPlanner.plan(descriptor);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, plan);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.candidate().key() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.candidate().key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(format(descriptor.rockCompetence())).append(',')
                    .append(format(descriptor.permeability())).append(',')
                    .append(format(descriptor.hydrologicalPotential())).append(',')
                    .append(format(descriptor.erosionMaturity())).append(',')
                    .append(plan.structuralCorridorCount()).append(',')
                    .append(plan.regionCount(SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR)).append(',')
                    .append(plan.cellCount(SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR)).append(',')
                    .append(plan.largestRegionCellCount(SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR)).append(',')
                    .append(plan.regionCount(SkyIslandGeologicRegionKind.AQUIFER_BODY)).append(',')
                    .append(plan.cellCount(SkyIslandGeologicRegionKind.AQUIFER_BODY)).append(',')
                    .append(plan.largestRegionCellCount(SkyIslandGeologicRegionKind.AQUIFER_BODY)).append(',')
                    .append(plan.regionCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)).append(',')
                    .append(plan.cellCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)).append(',')
                    .append(plan.largestRegionCellCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0023</title>"
                        + "<h1>Mesoscale geological regions</h1>"
                        + "<p>FRACTURE and AQUIFER are x/z maximum-membership projections through "
                        + "semantic depth. VOID and COMPOSITE are x/depth projections through z. "
                        + "Projections are review evidence only; stored regions remain connected 3D "
                        + "planning cells.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Selection select(
            String role,
            List<Candidate> candidates,
            Set<Long> used,
            ToDoubleFunction<SkyIslandDescriptor> score) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : candidates) {
            if (used.contains(candidate.key())) {
                continue;
            }
            double value = score.applyAsDouble(candidate.descriptor());
            if (value > bestScore) {
                best = candidate;
                bestScore = value;
            }
        }
        if (best == null) {
            throw new IllegalStateException("unable to select mesoscale geology representative");
        }
        used.add(best.key());
        return new Selection(role, best);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan plan) {
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
                        "corridors=%d  F=%d/%d  A=%d/%d  V=%d/%d",
                        plan.structuralCorridorCount(),
                        plan.regionCount(SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR),
                        plan.cellCount(SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR),
                        plan.regionCount(SkyIslandGeologicRegionKind.AQUIFER_BODY),
                        plan.cellCount(SkyIslandGeologicRegionKind.AQUIFER_BODY),
                        plan.regionCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN),
                        plan.cellCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)),
                7,
                35);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "FRACTURE SYSTEM", 0, MAP, 55);
        centered(g, "AQUIFER SYSTEM", MAP, MAP, 55);
        centered(g, "VOID DOMAINS", 2 * MAP, MAP, 55);
        centered(g, "COMPOSITE", 3 * MAP, MAP, 55);

        renderPlanBackground(image, 0, descriptor);
        renderPlanBackground(image, MAP, descriptor);
        renderDepthBackground(image, 2 * MAP, plan);
        renderDepthBackground(image, 3 * MAP, plan);

        double[][] fracturePlan = projectPlan(plan, SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR);
        double[][] aquiferPlan = projectPlan(plan, SkyIslandGeologicRegionKind.AQUIFER_BODY);
        double[][] fractureDepth = projectDepth(plan, SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR);
        double[][] aquiferDepth = projectDepth(plan, SkyIslandGeologicRegionKind.AQUIFER_BODY);
        double[][] voidDepth = projectDepth(plan, SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN);

        drawPlanProjection(image, 0, fracturePlan, FRACTURE);
        drawPlanProjection(image, MAP, aquiferPlan, AQUIFER);
        drawDepthProjection(image, 2 * MAP, voidDepth, VOID);
        drawCompositeDepth(image, 3 * MAP, fractureDepth, aquiferDepth, voidDepth);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void renderPlanBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        (semantic.interiority().sample(position) > 0.0 ? OWNED : OUTSIDE).getRGB());
            }
        }
    }

    private static void renderDepthBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandGeologicRegionPlan plan) {
        boolean[][] owned = new boolean[plan.depthSamples()][plan.gridSize()];
        for (SkyIslandGeologicRegion region : plan.regions()) {
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                owned[cell.depthIndex()][cell.xIndex()] = true;
            }
        }
        for (int py = 0; py < MAP; py++) {
            int id = nearestIndex(py, MAP, plan.depthSamples());
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, plan.gridSize());
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        (owned[id][ix] ? OWNED : OUTSIDE).getRGB());
            }
        }
    }

    private static double[][] projectPlan(
            SkyIslandGeologicRegionPlan plan,
            SkyIslandGeologicRegionKind kind) {
        double[][] projection = new double[plan.gridSize()][plan.gridSize()];
        for (SkyIslandGeologicRegion region : plan.regions()) {
            if (region.kind() != kind) {
                continue;
            }
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                projection[cell.zIndex()][cell.xIndex()] = Math.max(
                        projection[cell.zIndex()][cell.xIndex()],
                        cell.membership());
            }
        }
        return projection;
    }

    private static double[][] projectDepth(
            SkyIslandGeologicRegionPlan plan,
            SkyIslandGeologicRegionKind kind) {
        double[][] projection = new double[plan.depthSamples()][plan.gridSize()];
        for (SkyIslandGeologicRegion region : plan.regions()) {
            if (region.kind() != kind) {
                continue;
            }
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                projection[cell.depthIndex()][cell.xIndex()] = Math.max(
                        projection[cell.depthIndex()][cell.xIndex()],
                        cell.membership());
            }
        }
        return projection;
    }

    private static void drawPlanProjection(
            BufferedImage image,
            int offsetX,
            double[][] projection,
            Color color) {
        int grid = projection.length;
        for (int py = 0; py < MAP; py++) {
            int iz = grid - 1 - nearestIndex(py, MAP, grid);
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, grid);
                double value = projection[iz][ix];
                if (value > 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, blend(OWNED, color, value).getRGB());
                }
            }
        }
    }

    private static void drawDepthProjection(
            BufferedImage image,
            int offsetX,
            double[][] projection,
            Color color) {
        int depths = projection.length;
        int grid = projection[0].length;
        for (int py = 0; py < MAP; py++) {
            int id = nearestIndex(py, MAP, depths);
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, grid);
                double value = projection[id][ix];
                if (value > 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, blend(OWNED, color, value).getRGB());
                }
            }
        }
    }

    private static void drawCompositeDepth(
            BufferedImage image,
            int offsetX,
            double[][] fracture,
            double[][] aquifer,
            double[][] voidProne) {
        int depths = fracture.length;
        int grid = fracture[0].length;
        for (int py = 0; py < MAP; py++) {
            int id = nearestIndex(py, MAP, depths);
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, grid);
                double f = fracture[id][ix];
                double a = aquifer[id][ix];
                double v = voidProne[id][ix];
                if (f <= 0.0 && a <= 0.0 && v <= 0.0) {
                    continue;
                }
                double total = f + a + v;
                int red = (int) Math.round(
                        (FRACTURE.getRed() * f + AQUIFER.getRed() * a + VOID.getRed() * v) / total);
                int green = (int) Math.round(
                        (FRACTURE.getGreen() * f + AQUIFER.getGreen() * a + VOID.getGreen() * v) / total);
                int blue = (int) Math.round(
                        (FRACTURE.getBlue() * f + AQUIFER.getBlue() * a + VOID.getBlue() * v) / total);
                Color mixed = new Color(red, green, blue);
                double strength = Math.min(1.0, 0.45 + 0.30 * total);
                image.setRGB(offsetX + px, HEADER + py, blend(OWNED, mixed, strength).getRGB());
            }
        }
    }

    private static int nearestIndex(int pixel, int pixels, int samples) {
        return Math.max(
                0,
                Math.min(
                        samples - 1,
                        (int) Math.round(pixel * (samples - 1.0) / (pixels - 1.0))));
    }

    private static Color blend(Color background, Color foreground, double strength) {
        double t = Math.max(0.0, Math.min(1.0, 0.32 + 0.68 * strength));
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

    private record Candidate(long key, SkyIslandDescriptor descriptor) {}

    private record Selection(String role, Candidate candidate) {}
}
