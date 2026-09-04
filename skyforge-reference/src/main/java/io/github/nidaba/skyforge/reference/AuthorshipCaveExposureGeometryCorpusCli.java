package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveChamberGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureConnectionGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureGeometryPlan;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureGeometryPlanner;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureIntent;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandCavePassageGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCavePassagePoint;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystemGeometry;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import java.awt.BasicStroke;
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

/** Generates deterministic AUTH-0029 cave exposure connection evidence. */
public final class AuthorshipCaveExposureGeometryCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 190;
    private static final int HEADER = 72;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(241, 241, 237);
    private static final Color CAVE = new Color(76, 64, 86);
    private static final Color CHAMBER = new Color(177, 139, 196);
    private static final Color INTENT = new Color(150, 150, 150);
    private static final Color UPPER = new Color(190, 94, 55);
    private static final Color UNDERSIDE = new Color(68, 101, 167);

    private AuthorshipCaveExposureGeometryCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-cave-exposure-geometry-v1");
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
                "role,islandKey,morphology,caveSystems,exposureIntents,connections,side,"
                        + "steeringSupport,straightSupport,mouthOffset,maxDeviation,"
                        + "caveSideHorizontalRadiusFraction,mouthHorizontalRadiusFraction,"
                        + "caveSideDepthRadius,mouthDepthRadius\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor);
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

            SkyIslandCaveExposureConnectionGeometry connection =
                    plan.connections().isEmpty() ? null : plan.connections().getFirst();
            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(plan.exposurePlan().geometry().systems().size()).append(',')
                    .append(plan.exposurePlan().intents().size()).append(',')
                    .append(plan.connectionCount()).append(',')
                    .append(connection == null
                            ? "sealed"
                            : connection.side().name().toLowerCase(Locale.ROOT)).append(',')
                    .append(format(connection == null ? 0.0 : connection.steeringSupport())).append(',')
                    .append(format(connection == null ? 0.0 : connection.straightSupport())).append(',')
                    .append(format(connection == null ? 0.0 : connection.normalizedMouthOffset())).append(',')
                    .append(format(connection == null ? 0.0 : connection.normalizedMaxDeviation())).append(',')
                    .append(format(connection == null
                            ? 0.0
                            : connection.caveSidePoint().horizontalRadius() / descriptor.nominalRadius())).append(',')
                    .append(format(connection == null
                            ? 0.0
                            : connection.mouthPoint().horizontalRadius() / descriptor.nominalRadius())).append(',')
                    .append(format(connection == null ? 0.0 : connection.caveSidePoint().depthRadius())).append(',')
                    .append(format(connection == null ? 0.0 : connection.mouthPoint().depthRadius())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0029</title>"
                        + "<h1>Cave exposure connection geometry</h1>"
                        + "<p>INTENT SECTION shows the AUTH-0028 projected line. CONNECTION SECTION "
                        + "shows the authored AUTH-0029 curved/tapered corridor. TOP-DOWN MOUTH "
                        + "compares projected and realized boundary anchors. SUPPORT compares "
                        + "selected geology support against the protected straight baseline.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureGeometryPlan plan) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandCaveExposureConnectionGeometry connection =
                plan.connections().isEmpty() ? null : plan.connections().getFirst();

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                role + " / key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                "systems=" + plan.exposurePlan().geometry().systems().size()
                        + " intents=" + plan.exposurePlan().intents().size()
                        + " connections=" + plan.connectionCount()
                        + " side=" + (connection == null ? "SEALED" : connection.side()),
                7,
                35);
        g.drawString(
                connection == null
                        ? "no AUTH-0029 boundary connection"
                        : String.format(
                                Locale.ROOT,
                                "support=%.3f baseline=%.3f mouth offset=%.3f max bend=%.3f",
                                connection.steeringSupport(),
                                connection.straightSupport(),
                                connection.normalizedMouthOffset(),
                                connection.normalizedMaxDeviation()),
                7,
                50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "INTENT SECTION", 0, MAP, 65);
        centered(g, "CONNECTION SECTION", MAP, MAP, 65);
        centered(g, "TOP-DOWN MOUTH", 2 * MAP, MAP, 65);
        centered(g, "SUPPORT", 3 * MAP, MAP, 65);

        drawSectionBackground(image, 0, descriptor);
        drawSectionBackground(image, MAP, descriptor);
        drawPlanBackground(image, 2 * MAP, descriptor);
        fillPanel(image, 3 * MAP, new Color(248, 248, 246));

        drawExistingCave(g, 0, descriptor, plan);
        drawExistingCave(g, MAP, descriptor, plan);

        if (connection != null) {
            drawIntent(g, 0, descriptor, connection.intent());
            drawConnection(g, MAP, descriptor, connection);
            drawTopDown(g, 2 * MAP, descriptor, connection);
            drawSupport(g, 3 * MAP, connection);
        } else {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(100, 100, 100));
            String state = plan.exposurePlan().geometry().systems().isEmpty() ? "NO CAVE SYSTEM" : "SEALED";
            centered(g, state, 0, MAP, HEADER + MAP / 2);
            centered(g, "NO CONNECTION", MAP, MAP, HEADER + MAP / 2);
            centered(g, "NO MOUTH", 2 * MAP, MAP, HEADER + MAP / 2);
            centered(g, "NO ACCEPTED GEOMETRY", 3 * MAP, MAP, HEADER + MAP / 2);
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void drawExistingCave(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureGeometryPlan plan) {
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(CAVE);
        for (SkyIslandCaveSystemGeometry system : plan.exposurePlan().geometry().systems()) {
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                List<SkyIslandCavePassagePoint> points = passage.points();
                for (int index = 1; index < points.size(); index++) {
                    g.drawLine(
                            offsetX + depthX(points.get(index - 1).position(), descriptor),
                            HEADER + depthY(points.get(index - 1).position()),
                            offsetX + depthX(points.get(index).position(), descriptor),
                            HEADER + depthY(points.get(index).position()));
                }
            }
        }
        for (SkyIslandCaveSystemGeometry system : plan.exposurePlan().geometry().systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                int cx = offsetX + depthX(chamber.center(), descriptor);
                int cy = HEADER + depthY(chamber.center());
                int width = Math.max(
                        5,
                        (int) Math.round(chamber.horizontalRadius() / descriptor.nominalRadius() * MAP));
                int height = Math.max(5, (int) Math.round(2.0 * chamber.depthRadius() * MAP));
                g.setColor(CHAMBER);
                g.fillOval(cx - width / 2, cy - height / 2, width, height);
                g.setColor(CAVE);
                g.drawOval(cx - width / 2, cy - height / 2, width, height);
            }
        }
    }

    private static void drawIntent(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureIntent intent) {
        int x = offsetX + depthX(intent.caveAnchor(), descriptor);
        g.setColor(INTENT);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(
                x,
                HEADER + depthY(intent.caveAnchor()),
                x,
                HEADER + depthY(intent.boundaryAnchor()));
    }

    private static void drawConnection(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureConnectionGeometry connection) {
        Color color = connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE;
        List<SkyIslandCavePassagePoint> points = connection.points();
        for (int index = 1; index < points.size(); index++) {
            SkyIslandCavePassagePoint a = points.get(index - 1);
            SkyIslandCavePassagePoint b = points.get(index);
            float width = (float) Math.max(2.0, MAP * (a.depthRadius() + b.depthRadius()));
            g.setColor(color);
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(
                    offsetX + depthX(a.position(), descriptor),
                    HEADER + depthY(a.position()),
                    offsetX + depthX(b.position(), descriptor),
                    HEADER + depthY(b.position()));
        }
        SkyIslandCavePassagePoint mouth = connection.mouthPoint();
        int mx = offsetX + depthX(mouth.position(), descriptor);
        int my = HEADER + depthY(mouth.position());
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(mx - 5, my - 3, 10, 6);
    }

    private static void drawTopDown(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureConnectionGeometry connection) {
        double extent = descriptor.nominalRadius() * 1.03;
        SkyIslandSubsurfacePosition projected = connection.intent().boundaryAnchor();
        SkyIslandSubsurfacePosition mouth = connection.mouthPoint().position();

        int px = offsetX + planX(projected, extent);
        int py = HEADER + planY(projected, extent);
        int mx = offsetX + planX(mouth, extent);
        int my = HEADER + planY(mouth, extent);

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(INTENT);
        g.drawLine(px, py, mx, my);
        g.fillOval(px - 4, py - 4, 9, 9);

        Color color = connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE;
        g.setColor(color);
        g.fillOval(mx - 7, my - 7, 15, 15);
        g.setColor(Color.BLACK);
        g.drawOval(mx - 7, my - 7, 15, 15);
    }

    private static void drawSupport(
            Graphics2D g,
            int offsetX,
            SkyIslandCaveExposureConnectionGeometry connection) {
        String[] labels = {"selected", "straight", "mouth offset", "max bend"};
        double[] values = {
            connection.steeringSupport(),
            connection.straightSupport(),
            connection.normalizedMouthOffset() / 0.065,
            connection.normalizedMaxDeviation() / 0.040
        };
        int barX = offsetX + 71;
        int barWidth = MAP - 85;
        int top = HEADER + 28;
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int index = 0; index < labels.length; index++) {
            int y = top + index * 34;
            g.setColor(Color.DARK_GRAY);
            g.drawString(labels[index], offsetX + 6, y + 10);
            g.setColor(new Color(225, 225, 222));
            g.fillRect(barX, y, barWidth, 12);
            Color color = index == 0
                    ? (connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE)
                    : new Color(105, 105, 112);
            g.setColor(color);
            g.fillRect(barX, y, (int) Math.round(barWidth * clamp01(values[index])), 12);
            g.setColor(Color.BLACK);
            g.drawRect(barX, y, barWidth, 12);
        }

        g.drawString(
                String.format(
                        Locale.ROOT,
                        "r: %.3fR -> %.3fR",
                        connection.caveSidePoint().horizontalRadius()
                                / connection.intent().caveAnchor().surfacePosition().distanceFromOrigin()
                                * 0.0
                                + connection.caveSidePoint().horizontalRadius(),
                        connection.mouthPoint().horizontalRadius()),
                offsetX + 8,
                HEADER + MAP - 18);
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

    private static void fillPanel(BufferedImage image, int offsetX, Color color) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                image.setRGB(offsetX + px, HEADER + py, color.getRGB());
            }
        }
    }

    private static int depthX(SkyIslandSubsurfacePosition position, SkyIslandDescriptor descriptor) {
        return clampPixel((int) Math.round(
                (position.x() / descriptor.nominalRadius() + 1.0) * 0.5 * (MAP - 1)));
    }

    private static int depthY(SkyIslandSubsurfacePosition position) {
        return clampPixel((int) Math.round(position.depthFraction() * (MAP - 1)));
    }

    private static int planX(SkyIslandSubsurfacePosition position, double extent) {
        return clampPixel((int) Math.round((position.x() / extent + 1.0) * 0.5 * (MAP - 1)));
    }

    private static int planY(SkyIslandSubsurfacePosition position, double extent) {
        return clampPixel((int) Math.round(
                (1.0 - (position.z() / extent + 1.0) * 0.5) * (MAP - 1)));
    }

    private static int clampPixel(int value) {
        return Math.max(0, Math.min(MAP - 1, value));
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}
}
