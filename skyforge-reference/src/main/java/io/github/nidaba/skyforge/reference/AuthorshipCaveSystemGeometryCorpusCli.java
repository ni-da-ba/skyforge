package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveChamberGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveGeometryPlan;
import io.github.nidaba.skyforge.world.SkyIslandCaveGeometryPlanner;
import io.github.nidaba.skyforge.world.SkyIslandCaveLink;
import io.github.nidaba.skyforge.world.SkyIslandCaveNode;
import io.github.nidaba.skyforge.world.SkyIslandCavePassageGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCavePassagePoint;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystem;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystemGeometry;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegion;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionCell;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionKind;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionPlan;
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

/** Generates deterministic AUTH-0025 cave-system geometry evidence. */
public final class AuthorshipCaveSystemGeometryCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final long PROVINCE = 8L;
    private static final long CLUSTER = 81L;
    private static final int MAP = 200;
    private static final int HEADER = 72;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(241, 241, 237);
    private static final Color VOID = new Color(120, 82, 146);
    private static final Color FRACTURE = new Color(207, 107, 59);
    private static final Color AQUIFER = new Color(52, 111, 176);
    private static final Color GEOMETRY = new Color(55, 50, 62);
    private static final Color CHAMBER = new Color(174, 138, 194);

    private AuthorshipCaveSystemGeometryCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-cave-system-geometry-v1");
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
                "role,islandKey,morphology,systems,chambers,passages,"
                        + "meanChamberHorizontalRadiusFraction,meanChamberDepthRadius,"
                        + "meanPassageHorizontalRadiusFraction,meanPassageDepthRadius,"
                        + "meanSteeringSupport,maxNormalizedCenterlineDeviation\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, PROVINCE, CLUSTER, selection.key()));
            SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor);
            GeometryMetrics metrics = metrics(plan);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, plan, metrics);

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
                    .append(plan.systems().size()).append(',')
                    .append(plan.chamberCount()).append(',')
                    .append(plan.passageCount()).append(',')
                    .append(format(metrics.meanChamberHorizontalFraction())).append(',')
                    .append(format(metrics.meanChamberDepthRadius())).append(',')
                    .append(format(metrics.meanPassageHorizontalFraction())).append(',')
                    .append(format(metrics.meanPassageDepthRadius())).append(',')
                    .append(format(metrics.meanSteeringSupport())).append(',')
                    .append(format(metrics.maxNormalizedDeviation())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0025</title>"
                        + "<h1>Cave-system geometry</h1>"
                        + "<p>TOPOLOGY is AUTH-0024 graph structure. SECTION GEOMETRY and TOP-DOWN "
                        + "GEOMETRY show AUTH-0025 chamber/passages. GEOLOGIC SUPPORT overlays the "
                        + "section geometry on expressed AUTH-0023 fracture/aquifer support. "
                        + "Geometry is semantic and backend-neutral, not Minecraft voxels.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveGeometryPlan plan,
            GeometryMetrics metrics) {
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
                        "systems=%d chambers=%d passages=%d  mean steer=%.3f  max bend=%.3f",
                        plan.systems().size(),
                        plan.chamberCount(),
                        plan.passageCount(),
                        metrics.meanSteeringSupport(),
                        metrics.maxNormalizedDeviation()),
                7,
                35);
        g.drawString("horizontal scale and semantic-depth scale are intentionally independent", 7, 50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "TOPOLOGY", 0, MAP, 64);
        centered(g, "SECTION GEOMETRY", MAP, MAP, 64);
        centered(g, "TOP-DOWN GEOMETRY", 2 * MAP, MAP, 64);
        centered(g, "GEOLOGIC SUPPORT", 3 * MAP, MAP, 64);

        renderDepthBackground(image, 0, descriptor);
        renderDepthBackground(image, MAP, descriptor);
        renderPlanBackground(image, 2 * MAP, descriptor);
        renderSupportBackground(image, 3 * MAP, descriptor, plan.topology().geology());

        drawTopology(g, 0, descriptor, plan);
        drawSectionGeometry(g, MAP, descriptor, plan);
        drawTopDownGeometry(g, 2 * MAP, descriptor, plan);
        drawSectionGeometry(g, 3 * MAP, descriptor, plan);

        if (plan.systems().isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(new Color(90, 90, 90));
            for (int panel = 0; panel < 4; panel++) {
                centered(g, "NO AUTHORED CAVE SYSTEM", panel * MAP, MAP, HEADER + MAP / 2);
            }
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void drawTopology(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveGeometryPlan plan) {
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(95, 95, 95));
        for (SkyIslandCaveSystem system : plan.topology().systems()) {
            for (SkyIslandCaveLink link : system.links()) {
                SkyIslandCaveNode first = topologyNode(system, link.firstNodeId());
                SkyIslandCaveNode second = topologyNode(system, link.secondNodeId());
                g.drawLine(
                        offsetX + depthX(first.position(), descriptor),
                        HEADER + depthY(first.position()),
                        offsetX + depthX(second.position(), descriptor),
                        HEADER + depthY(second.position()));
            }
        }
        for (SkyIslandCaveSystem system : plan.topology().systems()) {
            for (SkyIslandCaveNode node : system.nodes()) {
                int x = offsetX + depthX(node.position(), descriptor);
                int y = HEADER + depthY(node.position());
                g.setColor(VOID);
                g.fillOval(x - 4, y - 4, 9, 9);
                g.setColor(Color.BLACK);
                g.drawOval(x - 4, y - 4, 9, 9);
            }
        }
    }

    private static void drawSectionGeometry(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveGeometryPlan plan) {
        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                List<SkyIslandCavePassagePoint> points = passage.points();
                for (int i = 1; i < points.size(); i++) {
                    SkyIslandCavePassagePoint a = points.get(i - 1);
                    SkyIslandCavePassagePoint b = points.get(i);
                    float width = (float) Math.max(
                            2.0,
                            MAP * (a.depthRadius() + b.depthRadius()));
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(GEOMETRY.getRed(), GEOMETRY.getGreen(), GEOMETRY.getBlue(), 185));
                    g.drawLine(
                            offsetX + depthX(a.position(), descriptor),
                            HEADER + depthY(a.position()),
                            offsetX + depthX(b.position(), descriptor),
                            HEADER + depthY(b.position()));
                }
            }
        }

        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                int cx = offsetX + depthX(chamber.center(), descriptor);
                int cy = HEADER + depthY(chamber.center());
                int width = Math.max(
                        5,
                        (int) Math.round(
                                chamber.horizontalRadius() / descriptor.nominalRadius() * MAP));
                int height = Math.max(
                        5,
                        (int) Math.round(2.0 * chamber.depthRadius() * MAP));
                g.setColor(new Color(CHAMBER.getRed(), CHAMBER.getGreen(), CHAMBER.getBlue(), 210));
                g.fillOval(cx - width / 2, cy - height / 2, width, height);
                g.setColor(new Color(45, 40, 50));
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(cx - width / 2, cy - height / 2, width, height);
            }
        }
    }

    private static void drawTopDownGeometry(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveGeometryPlan plan) {
        double extent = descriptor.nominalRadius() * 1.03;
        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                List<SkyIslandCavePassagePoint> points = passage.points();
                for (int i = 1; i < points.size(); i++) {
                    SkyIslandCavePassagePoint a = points.get(i - 1);
                    SkyIslandCavePassagePoint b = points.get(i);
                    float width = (float) Math.max(
                            2.0,
                            (a.horizontalRadius() + b.horizontalRadius()) / (2.0 * extent) * MAP);
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(GEOMETRY.getRed(), GEOMETRY.getGreen(), GEOMETRY.getBlue(), 185));
                    g.drawLine(
                            offsetX + planX(a.position(), extent),
                            HEADER + planY(a.position(), extent),
                            offsetX + planX(b.position(), extent),
                            HEADER + planY(b.position(), extent));
                }
            }
        }

        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                int cx = offsetX + planX(chamber.center(), extent);
                int cy = HEADER + planY(chamber.center(), extent);
                int diameter = Math.max(
                        5,
                        (int) Math.round(chamber.horizontalRadius() / extent * MAP));
                g.setColor(new Color(CHAMBER.getRed(), CHAMBER.getGreen(), CHAMBER.getBlue(), 210));
                g.fillOval(cx - diameter / 2, cy - diameter / 2, diameter, diameter);
                g.setColor(new Color(45, 40, 50));
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(cx - diameter / 2, cy - diameter / 2, diameter, diameter);
            }
        }
    }

    private static void renderDepthBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                boolean owned = semantic.interiority().sample(new SkyIslandLocalPosition(x, 0.0)) > 0.0;
                image.setRGB(offsetX + px, HEADER + py, (owned ? OWNED : OUTSIDE).getRGB());
            }
        }
    }

    private static void renderPlanBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double extent = descriptor.nominalRadius() * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        (semantic.interiority().sample(new SkyIslandLocalPosition(x, z)) > 0.0
                                ? OWNED
                                : OUTSIDE).getRGB());
            }
        }
    }

    private static void renderSupportBackground(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan geology) {
        double[][] fracture = projectDepth(geology, SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR);
        double[][] aquifer = projectDepth(geology, SkyIslandGeologicRegionKind.AQUIFER_BODY);
        renderDepthBackground(image, offsetX, descriptor);

        for (int py = 0; py < MAP; py++) {
            int id = nearestIndex(py, MAP, geology.depthSamples());
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, geology.gridSize());
                double f = fracture[id][ix];
                double a = aquifer[id][ix];
                if (f <= 0.0 && a <= 0.0) {
                    continue;
                }
                Color color;
                double strength;
                if (f > 0.0 && a > 0.0) {
                    double total = f + a;
                    color = new Color(
                            (int) Math.round((FRACTURE.getRed() * f + AQUIFER.getRed() * a) / total),
                            (int) Math.round((FRACTURE.getGreen() * f + AQUIFER.getGreen() * a) / total),
                            (int) Math.round((FRACTURE.getBlue() * f + AQUIFER.getBlue() * a) / total));
                    strength = Math.min(0.78, 0.35 + 0.30 * total);
                } else if (f > 0.0) {
                    color = FRACTURE;
                    strength = Math.min(0.72, 0.25 + 0.55 * f);
                } else {
                    color = AQUIFER;
                    strength = Math.min(0.72, 0.25 + 0.55 * a);
                }
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(OWNED, color, strength).getRGB());
            }
        }
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

    private static GeometryMetrics metrics(SkyIslandCaveGeometryPlan plan) {
        double chamberHorizontal = 0.0;
        double chamberDepth = 0.0;
        int chamberCount = 0;
        double passageHorizontal = 0.0;
        double passageDepth = 0.0;
        double steering = 0.0;
        int passageCount = 0;
        double maxDeviation = 0.0;

        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                chamberHorizontal += chamber.horizontalRadius() / plan.descriptor().nominalRadius();
                chamberDepth += chamber.depthRadius();
                chamberCount++;
            }
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                passageHorizontal += passage.meanHorizontalRadius() / plan.descriptor().nominalRadius();
                passageDepth += passage.meanDepthRadius();
                steering += passage.steeringSupport();
                passageCount++;
                maxDeviation = Math.max(maxDeviation, centerlineDeviation(passage, plan.descriptor()));
            }
        }

        return new GeometryMetrics(
                chamberCount == 0 ? 0.0 : chamberHorizontal / chamberCount,
                chamberCount == 0 ? 0.0 : chamberDepth / chamberCount,
                passageCount == 0 ? 0.0 : passageHorizontal / passageCount,
                passageCount == 0 ? 0.0 : passageDepth / passageCount,
                passageCount == 0 ? 0.0 : steering / passageCount,
                maxDeviation);
    }

    private static double centerlineDeviation(
            SkyIslandCavePassageGeometry passage,
            SkyIslandDescriptor descriptor) {
        SkyIslandSubsurfacePosition first = passage.points().getFirst().position();
        SkyIslandSubsurfacePosition last = passage.points().getLast().position();
        double max = 0.0;
        for (int i = 0; i < passage.points().size(); i++) {
            double t = i / (passage.points().size() - 1.0);
            SkyIslandSubsurfacePosition point = passage.points().get(i).position();
            double x = first.x() + (last.x() - first.x()) * t;
            double z = first.z() + (last.z() - first.z()) * t;
            double depth = first.depthFraction()
                    + (last.depthFraction() - first.depthFraction()) * t;
            double deviation = Math.sqrt(
                    Math.pow((point.x() - x) / descriptor.nominalRadius(), 2.0)
                            + Math.pow((point.z() - z) / descriptor.nominalRadius(), 2.0)
                            + Math.pow(point.depthFraction() - depth, 2.0));
            max = Math.max(max, deviation);
        }
        return max;
    }

    private static SkyIslandCaveNode topologyNode(SkyIslandCaveSystem system, int nodeId) {
        return system.nodes().stream()
                .filter(node -> node.nodeId() == nodeId)
                .findFirst()
                .orElseThrow();
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

    private static int nearestIndex(int pixel, int pixels, int samples) {
        return Math.max(
                0,
                Math.min(
                        samples - 1,
                        (int) Math.round(pixel * (samples - 1.0) / (pixels - 1.0))));
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

    private record GeometryMetrics(
            double meanChamberHorizontalFraction,
            double meanChamberDepthRadius,
            double meanPassageHorizontalFraction,
            double meanPassageDepthRadius,
            double meanSteeringSupport,
            double maxNormalizedDeviation) {}
}
