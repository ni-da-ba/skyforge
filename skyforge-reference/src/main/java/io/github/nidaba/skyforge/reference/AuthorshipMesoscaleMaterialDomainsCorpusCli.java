package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialDomain;
import io.github.nidaba.skyforge.world.SkyIslandMaterialDomainCell;
import io.github.nidaba.skyforge.world.SkyIslandMaterialDomainKind;
import io.github.nidaba.skyforge.world.SkyIslandMaterialDomainPlan;
import io.github.nidaba.skyforge.world.SkyIslandMaterialDomainPlanner;
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

/** Generates deterministic AUTH-0032 mesoscale material-domain evidence. */
public final class AuthorshipMesoscaleMaterialDomainsCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 138;
    private static final int HEADER = 68;
    private static final int PANELS = 5;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipMesoscaleMaterialDomainsCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-mesoscale-material-domains-v1");
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
                "role,islandKey,morphology,mineralCarriers,fabricCarriers,activeHostCells,"
                        + "alteredDomains,alteredCells,largestAltered,alteredCoverage,"
                        + "saturatedDomains,saturatedCells,largestSaturated,saturatedCoverage,"
                        + "mineralizedDomains,mineralizedCells,largestMineralized,mineralizedCoverage,"
                        + "fabricDomains,fabricCells,largestFabric,fabricCoverage\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, plan);

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
                    .append(plan.mineralCarrierCount()).append(',')
                    .append(plan.fabricCarrierCount()).append(',')
                    .append(plan.activeHostCells()).append(',')
                    .append(plan.domainCount(SkyIslandMaterialDomainKind.ALTERED_ZONE)).append(',')
                    .append(plan.cellCount(SkyIslandMaterialDomainKind.ALTERED_ZONE)).append(',')
                    .append(plan.largestDomainCellCount(SkyIslandMaterialDomainKind.ALTERED_ZONE)).append(',')
                    .append(format(coverage(plan, SkyIslandMaterialDomainKind.ALTERED_ZONE))).append(',')
                    .append(plan.domainCount(SkyIslandMaterialDomainKind.SATURATED_BODY)).append(',')
                    .append(plan.cellCount(SkyIslandMaterialDomainKind.SATURATED_BODY)).append(',')
                    .append(plan.largestDomainCellCount(SkyIslandMaterialDomainKind.SATURATED_BODY)).append(',')
                    .append(format(coverage(plan, SkyIslandMaterialDomainKind.SATURATED_BODY))).append(',')
                    .append(plan.domainCount(SkyIslandMaterialDomainKind.MINERALIZED_BODY)).append(',')
                    .append(plan.cellCount(SkyIslandMaterialDomainKind.MINERALIZED_BODY)).append(',')
                    .append(plan.largestDomainCellCount(SkyIslandMaterialDomainKind.MINERALIZED_BODY)).append(',')
                    .append(format(coverage(plan, SkyIslandMaterialDomainKind.MINERALIZED_BODY))).append(',')
                    .append(plan.domainCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)).append(',')
                    .append(plan.cellCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)).append(',')
                    .append(plan.largestDomainCellCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)).append(',')
                    .append(format(coverage(plan, SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN))).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0032</title>"
                        + "<h1>Mesoscale subsurface material domains</h1>"
                        + "<p>All four domain panels are x/z maximum-membership projections through "
                        + "semantic depth, preserving island morphology and regional geography. "
                        + "COMPOSITE PLAN shows overlap among all four systems. Interpolation is "
                        + "evidence-only; stored domains remain connected three-dimensional semantic "
                        + "planning cells.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialDomainPlan plan) {
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
                        "carriers mineral=%d fabric=%d active=%d  cells A=%d S=%d M=%d F=%d",
                        plan.mineralCarrierCount(),
                        plan.fabricCarrierCount(),
                        plan.activeHostCells(),
                        plan.cellCount(SkyIslandMaterialDomainKind.ALTERED_ZONE),
                        plan.cellCount(SkyIslandMaterialDomainKind.SATURATED_BODY),
                        plan.cellCount(SkyIslandMaterialDomainKind.MINERALIZED_BODY),
                        plan.cellCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "domains A=%d S=%d M=%d F=%d",
                        plan.domainCount(SkyIslandMaterialDomainKind.ALTERED_ZONE),
                        plan.domainCount(SkyIslandMaterialDomainKind.SATURATED_BODY),
                        plan.domainCount(SkyIslandMaterialDomainKind.MINERALIZED_BODY),
                        plan.domainCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "ALTERED", 0, MAP, 62);
        centered(g, "SATURATED", MAP, MAP, 62);
        centered(g, "MINERALIZED", 2 * MAP, MAP, 62);
        centered(g, "FABRIC", 3 * MAP, MAP, 62);
        centered(g, "COMPOSITE PLAN", 4 * MAP, MAP, 62);

        double[][] altered = planProjection(plan, SkyIslandMaterialDomainKind.ALTERED_ZONE);
        double[][] saturated = planProjection(plan, SkyIslandMaterialDomainKind.SATURATED_BODY);
        double[][] mineralized = planProjection(plan, SkyIslandMaterialDomainKind.MINERALIZED_BODY);
        double[][] fabric = planProjection(plan, SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN);

        renderInterpolated(
                image,
                0,
                altered,
                new Color(247, 244, 236),
                new Color(164, 84, 54));
        renderInterpolated(
                image,
                MAP,
                saturated,
                new Color(247, 244, 236),
                new Color(48, 105, 166));
        renderInterpolated(
                image,
                2 * MAP,
                mineralized,
                new Color(247, 244, 236),
                new Color(137, 91, 150));
        renderInterpolated(
                image,
                3 * MAP,
                fabric,
                new Color(247, 244, 236),
                new Color(82, 132, 92));
        renderComposite(
                image,
                4 * MAP,
                altered,
                saturated,
                mineralized,
                fabric);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static double[][] sectionProjection(
            SkyIslandMaterialDomainPlan plan,
            SkyIslandMaterialDomainKind kind) {
        double[][] result = new double[plan.depthSamples()][plan.gridSize()];
        for (SkyIslandMaterialDomain domain : plan.domains()) {
            if (domain.kind() != kind) {
                continue;
            }
            for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                result[cell.depthIndex()][cell.xIndex()] =
                        Math.max(result[cell.depthIndex()][cell.xIndex()], cell.membership());
            }
        }
        return result;
    }

    private static double[][] planProjection(
            SkyIslandMaterialDomainPlan plan,
            SkyIslandMaterialDomainKind kind) {
        double[][] result = new double[plan.gridSize()][plan.gridSize()];
        for (SkyIslandMaterialDomain domain : plan.domains()) {
            if (domain.kind() != kind) {
                continue;
            }
            for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                result[cell.zIndex()][cell.xIndex()] =
                        Math.max(result[cell.zIndex()][cell.xIndex()], cell.membership());
            }
        }
        return result;
    }

    private static void renderInterpolated(
            BufferedImage image,
            int offsetX,
            double[][] values,
            Color low,
            Color high) {
        int height = values.length;
        int width = values[0].length;
        for (int py = 0; py < MAP; py++) {
            double gy = py * (height - 1.0) / (MAP - 1.0);
            int y0 = Math.min(height - 1, (int) Math.floor(gy));
            int y1 = Math.min(height - 1, y0 + 1);
            double ty = gy - y0;
            for (int px = 0; px < MAP; px++) {
                double gx = px * (width - 1.0) / (MAP - 1.0);
                int x0 = Math.min(width - 1, (int) Math.floor(gx));
                int x1 = Math.min(width - 1, x0 + 1);
                double tx = gx - x0;
                double value = bilinear(
                        values[y0][x0],
                        values[y0][x1],
                        values[y1][x0],
                        values[y1][x1],
                        tx,
                        ty);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        value <= 0.0
                                ? Color.WHITE.getRGB()
                                : ramp(value, low, high).getRGB());
            }
        }
    }

    private static void renderComposite(
            BufferedImage image,
            int offsetX,
            double[][] altered,
            double[][] saturated,
            double[][] mineralized,
            double[][] fabric) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double a = interpolated(altered, px, py);
                double s = interpolated(saturated, px, py);
                double m = interpolated(mineralized, px, py);
                double f = interpolated(fabric, px, py);
                double total = a + s + m + f;
                if (total <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }

                double red = 248.0
                        - 92.0 * a
                        - 35.0 * s
                        - 55.0 * m
                        - 52.0 * f;
                double green = 247.0
                        - 150.0 * a
                        - 78.0 * s
                        - 92.0 * m
                        - 70.0 * f;
                double blue = 242.0
                        - 165.0 * a
                        - 30.0 * s
                        - 58.0 * m
                        - 112.0 * f;
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        new Color(channel(red), channel(green), channel(blue)).getRGB());
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
        return bilinear(
                values[y0][x0],
                values[y0][x1],
                values[y1][x0],
                values[y1][x1],
                gx - x0,
                gy - y0);
    }

    private static double bilinear(
            double v00,
            double v10,
            double v01,
            double v11,
            double tx,
            double ty) {
        double top = v00 + (v10 - v00) * tx;
        double bottom = v01 + (v11 - v01) * tx;
        return top + (bottom - top) * ty;
    }

    private static Color ramp(double value, Color low, Color high) {
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

    private static double coverage(
            SkyIslandMaterialDomainPlan plan,
            SkyIslandMaterialDomainKind kind) {
        return plan.cellCount(kind) / (double) plan.activeHostCells();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}
}
