package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyCell;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyKind;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyPlan;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyPlanner;
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

/** Generates deterministic AUTH-0033 semantic material-family evidence. */
public final class AuthorshipSemanticMaterialFamiliesCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 126;
    private static final int HEADER = 68;
    private static final int PANELS = 6;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color MASSIVE = new Color(119, 104, 91);
    private static final Color FABRIC = new Color(75, 126, 91);
    private static final Color ALTERED = new Color(174, 96, 57);
    private static final Color WATER = new Color(52, 105, 164);
    private static final Color MINERAL = new Color(137, 87, 151);

    private AuthorshipSemanticMaterialFamiliesCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-semantic-material-families-v1");
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
                "role,islandKey,morphology,activeHostCells,"
                        + "massiveExpressed,massiveMean,massivePeak,"
                        + "fabricExpressed,fabricMean,fabricPeak,"
                        + "alteredExpressed,alteredMean,alteredPeak,"
                        + "waterExpressed,waterMean,waterPeak,"
                        + "mineralExpressed,mineralMean,mineralPeak\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialFamilyPlan plan = SkyIslandMaterialFamilyPlanner.plan(descriptor);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, plan);

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
                    .append(plan.activeHostCells());
            appendFamily(manifest, plan, SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST);
            appendFamily(manifest, plan, SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST);
            appendFamily(manifest, plan, SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST);
            appendFamily(manifest, plan, SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST);
            appendFamily(
                    manifest,
                    plan,
                    SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST);
            manifest.append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0033</title>"
                        + "<h1>Semantic material families</h1>"
                        + "<p>Five backend-neutral family affinities are rendered as x/z maximum "
                        + "projections through semantic depth. COMPOSITE blends overlapping family "
                        + "support; it is evidence-only and does not choose a categorical material. "
                        + "Conditioned-host families remain gated by AUTH-0032 mesoscale domains.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialFamilyPlan plan) {
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
                        "active=%d  expressed@%.2f M=%d F=%d A=%d W=%d N=%d",
                        plan.activeHostCells(),
                        SkyIslandMaterialFamilyPlanner.EVIDENCE_EXPRESSION_THRESHOLD,
                        expressed(plan, SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST),
                        expressed(plan, SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST),
                        expressed(plan, SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST),
                        expressed(plan, SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST),
                        expressed(
                                plan,
                                SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST)),
                7,
                35);
        g.drawString(
                "families remain overlapping semantic affinities; composite is evidence-only",
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "MASSIVE", 0, MAP, 62);
        centered(g, "FABRIC", MAP, MAP, 62);
        centered(g, "ALTERED", 2 * MAP, MAP, 62);
        centered(g, "WATER", 3 * MAP, MAP, 62);
        centered(g, "MINERAL", 4 * MAP, MAP, 62);
        centered(g, "COMPOSITE", 5 * MAP, MAP, 62);

        double[][] massive =
                planProjection(plan, SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST);
        double[][] fabric =
                planProjection(plan, SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST);
        double[][] altered =
                planProjection(plan, SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST);
        double[][] water =
                planProjection(plan, SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST);
        double[][] mineral = planProjection(
                plan, SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST);

        renderInterpolated(image, 0, massive, MASSIVE);
        renderInterpolated(image, MAP, fabric, FABRIC);
        renderInterpolated(image, 2 * MAP, altered, ALTERED);
        renderInterpolated(image, 3 * MAP, water, WATER);
        renderInterpolated(image, 4 * MAP, mineral, MINERAL);
        renderComposite(image, 5 * MAP, massive, fabric, altered, water, mineral);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static double[][] planProjection(
            SkyIslandMaterialFamilyPlan plan,
            SkyIslandMaterialFamilyKind kind) {
        double[][] result = new double[plan.gridSize()][plan.gridSize()];
        for (SkyIslandMaterialFamilyCell cell : plan.cells()) {
            result[cell.zIndex()][cell.xIndex()] =
                    Math.max(result[cell.zIndex()][cell.xIndex()], cell.membership(kind));
        }
        return result;
    }

    private static void renderInterpolated(
            BufferedImage image,
            int offsetX,
            double[][] values,
            Color familyColor) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double value = interpolated(values, px, py);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        value <= 0.0
                                ? Color.WHITE.getRGB()
                                : blend(Color.WHITE, familyColor, 0.24 + 0.76 * value).getRGB());
            }
        }
    }

    private static void renderComposite(
            BufferedImage image,
            int offsetX,
            double[][] massive,
            double[][] fabric,
            double[][] altered,
            double[][] water,
            double[][] mineral) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double m = interpolated(massive, px, py);
                double f = interpolated(fabric, px, py);
                double a = interpolated(altered, px, py);
                double w = interpolated(water, px, py);
                double n = interpolated(mineral, px, py);
                double total = m + f + a + w + n;
                if (total <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }

                double red = (m * MASSIVE.getRed()
                                + f * FABRIC.getRed()
                                + a * ALTERED.getRed()
                                + w * WATER.getRed()
                                + n * MINERAL.getRed())
                        / total;
                double green = (m * MASSIVE.getGreen()
                                + f * FABRIC.getGreen()
                                + a * ALTERED.getGreen()
                                + w * WATER.getGreen()
                                + n * MINERAL.getGreen())
                        / total;
                double blue = (m * MASSIVE.getBlue()
                                + f * FABRIC.getBlue()
                                + a * ALTERED.getBlue()
                                + w * WATER.getBlue()
                                + n * MINERAL.getBlue())
                        / total;
                Color mixture =
                        new Color(channel(red), channel(green), channel(blue));
                double strength = Math.max(m, Math.max(f, Math.max(a, Math.max(w, n))));
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(Color.WHITE, mixture, 0.30 + 0.70 * strength).getRGB());
            }
        }
    }

    private static double interpolated(double[][] values, int px, int py) {
        int height = values.length;
        int width = values[0].length;
        double gy = py * (height - 1.0) / (MAP - 1.0);
        double gx = px * (width - 1.0) / (MAP - 1.0);
        int y0 = Math.min(height - 1, (int) Math.floor(gy));
        int y1 = Math.min(height - 1, y0 + 1);
        int x0 = Math.min(width - 1, (int) Math.floor(gx));
        int x1 = Math.min(width - 1, x0 + 1);
        double tx = gx - x0;
        double ty = gy - y0;
        double top = values[y0][x0] + (values[y0][x1] - values[y0][x0]) * tx;
        double bottom = values[y1][x0] + (values[y1][x1] - values[y1][x0]) * tx;
        return top + (bottom - top) * ty;
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

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static int expressed(
            SkyIslandMaterialFamilyPlan plan,
            SkyIslandMaterialFamilyKind kind) {
        return plan.cellCountAbove(
                kind, SkyIslandMaterialFamilyPlanner.EVIDENCE_EXPRESSION_THRESHOLD);
    }

    private static void appendFamily(
            StringBuilder manifest,
            SkyIslandMaterialFamilyPlan plan,
            SkyIslandMaterialFamilyKind kind) {
        manifest.append(',')
                .append(expressed(plan, kind)).append(',')
                .append(format(plan.meanMembership(kind))).append(',')
                .append(format(plan.peakMembership(kind)));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}
}
