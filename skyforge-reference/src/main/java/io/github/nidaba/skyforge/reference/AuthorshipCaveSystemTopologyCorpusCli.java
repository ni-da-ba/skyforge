package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveConnectionKind;
import io.github.nidaba.skyforge.world.SkyIslandCaveLink;
import io.github.nidaba.skyforge.world.SkyIslandCaveNode;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystem;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystemPlan;
import io.github.nidaba.skyforge.world.SkyIslandCaveSystemPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegion;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionCell;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionKind;
import io.github.nidaba.skyforge.world.SkyIslandGeologicRegionPlan;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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

/** Generates deterministic AUTH-0024 semantic cave-system topology evidence. */
public final class AuthorshipCaveSystemTopologyCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final long PROVINCE = 8L;
    private static final long CLUSTER = 81L;
    private static final int MAP = 190;
    private static final int HEADER = 68;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(240, 240, 236);
    private static final Color VOID = new Color(126, 88, 151);
    private static final Color AQUIFER = new Color(56, 116, 177);
    private static final Color CONTINUITY = new Color(65, 65, 65);
    private static final Color FRACTURE_LINK = new Color(204, 102, 54);
    private static final Color AQUIFER_LINK = new Color(38, 105, 175);
    private static final Color MIXED_LINK = new Color(125, 72, 150);

    private AuthorshipCaveSystemTopologyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-cave-system-topology-v1");
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
                "role,islandKey,morphology,voidRegions,voidCells,caveSystems,nodes,links,"
                        + "crossRegionLinks,waterInfluencedSystems,maxNodesPerSystem,"
                        + "maxSourceVoidRegionsPerSystem,meanNodeGroundwater\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, PROVINCE, CLUSTER, selection.key()));
            SkyIslandCaveSystemPlan plan = SkyIslandCaveSystemPlanner.plan(descriptor);
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

            int maxNodes = plan.systems().stream()
                    .mapToInt(system -> system.nodes().size())
                    .max()
                    .orElse(0);
            int maxSourceRegions = plan.systems().stream()
                    .mapToInt(SkyIslandCaveSystem::sourceVoidRegionCount)
                    .max()
                    .orElse(0);
            double meanGroundwater = plan.systems().stream()
                    .flatMap(system -> system.nodes().stream())
                    .mapToDouble(SkyIslandCaveNode::groundwaterPotential)
                    .average()
                    .orElse(0.0);

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(plan.geology().regionCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)).append(',')
                    .append(plan.geology().cellCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)).append(',')
                    .append(plan.systems().size()).append(',')
                    .append(plan.nodeCount()).append(',')
                    .append(plan.linkCount()).append(',')
                    .append(plan.crossRegionLinkCount()).append(',')
                    .append(plan.waterInfluencedSystemCount()).append(',')
                    .append(maxNodes).append(',')
                    .append(maxSourceRegions).append(',')
                    .append(format(meanGroundwater)).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0024</title>"
                        + "<h1>Cave-system topology</h1>"
                        + "<p>VOID DOMAINS shows AUTH-0023 x/depth support. CAVE TOPOLOGY and "
                        + "HYDRO INFLUENCE are schematic graph evidence, not literal tunnel "
                        + "centerlines. TOP-DOWN GRAPH uses current naturalized ownership.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveSystemPlan plan) {
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
                        "void regions=%d  systems=%d  nodes=%d  links=%d  cross=%d  wet=%d",
                        plan.geology().regionCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN),
                        plan.systems().size(),
                        plan.nodeCount(),
                        plan.linkCount(),
                        plan.crossRegionLinkCount(),
                        plan.waterInfluencedSystemCount()),
                7,
                35);
        g.drawString("semantic topology only — graph links are not tunnel splines", 7, 50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "VOID DOMAINS", 0, MAP, 61);
        centered(g, "CAVE TOPOLOGY", MAP, MAP, 61);
        centered(g, "TOP-DOWN GRAPH", 2 * MAP, MAP, 61);
        centered(g, "HYDRO INFLUENCE", 3 * MAP, MAP, 61);

        double[][] voidDepth =
                projectDepth(plan.geology(), SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN);
        double[][] aquiferDepth =
                projectDepth(plan.geology(), SkyIslandGeologicRegionKind.AQUIFER_BODY);

        renderDepthBackground(image, 0, descriptor);
        renderDepthBackground(image, MAP, descriptor);
        renderPlanBackground(image, 2 * MAP, descriptor);
        renderDepthBackground(image, 3 * MAP, descriptor);

        drawDepthProjection(image, 0, voidDepth, VOID, 0.82);
        drawDepthProjection(image, MAP, voidDepth, VOID, 0.28);
        drawDepthProjection(image, 3 * MAP, aquiferDepth, AQUIFER, 0.65);

        drawDepthGraph(g, MAP, descriptor, plan);
        drawTopDownGraph(g, 2 * MAP, descriptor, plan);
        drawDepthGraph(g, 3 * MAP, descriptor, plan);

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

    private static void drawDepthProjection(
            BufferedImage image,
            int offsetX,
            double[][] projection,
            Color color,
            double opacity) {
        int depths = projection.length;
        int grid = projection[0].length;
        for (int py = 0; py < MAP; py++) {
            int id = nearestIndex(py, MAP, depths);
            for (int px = 0; px < MAP; px++) {
                int ix = nearestIndex(px, MAP, grid);
                double value = projection[id][ix];
                if (value > 0.0) {
                    image.setRGB(
                            offsetX + px,
                            HEADER + py,
                            blend(OWNED, color, opacity * value).getRGB());
                }
            }
        }
    }

    private static void drawDepthGraph(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveSystemPlan plan) {
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandCaveSystem system : plan.systems()) {
            for (SkyIslandCaveLink link : system.links()) {
                SkyIslandCaveNode first = node(system, link.firstNodeId());
                SkyIslandCaveNode second = node(system, link.secondNodeId());
                g.setColor(linkColor(link.kind()));
                g.drawLine(
                        offsetX + depthX(first.position(), descriptor),
                        HEADER + depthY(first.position()),
                        offsetX + depthX(second.position(), descriptor),
                        HEADER + depthY(second.position()));
            }
        }
        for (SkyIslandCaveSystem system : plan.systems()) {
            for (SkyIslandCaveNode node : system.nodes()) {
                int x = offsetX + depthX(node.position(), descriptor);
                int y = HEADER + depthY(node.position());
                g.setColor(nodeColor(node.groundwaterPotential()));
                g.fillOval(x - 4, y - 4, 9, 9);
                g.setColor(Color.BLACK);
                g.drawOval(x - 4, y - 4, 9, 9);
            }
        }
    }

    private static void drawTopDownGraph(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveSystemPlan plan) {
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandCaveSystem system : plan.systems()) {
            for (SkyIslandCaveLink link : system.links()) {
                SkyIslandCaveNode first = node(system, link.firstNodeId());
                SkyIslandCaveNode second = node(system, link.secondNodeId());
                g.setColor(linkColor(link.kind()));
                g.drawLine(
                        offsetX + planX(first.position(), descriptor),
                        HEADER + planY(first.position(), descriptor),
                        offsetX + planX(second.position(), descriptor),
                        HEADER + planY(second.position(), descriptor));
            }
        }
        for (SkyIslandCaveSystem system : plan.systems()) {
            for (SkyIslandCaveNode node : system.nodes()) {
                int x = offsetX + planX(node.position(), descriptor);
                int y = HEADER + planY(node.position(), descriptor);
                g.setColor(nodeColor(node.groundwaterPotential()));
                g.fillOval(x - 4, y - 4, 9, 9);
                g.setColor(Color.BLACK);
                g.drawOval(x - 4, y - 4, 9, 9);
            }
        }
    }

    private static SkyIslandCaveNode node(SkyIslandCaveSystem system, int nodeId) {
        return system.nodes().stream()
                .filter(node -> node.nodeId() == nodeId)
                .findFirst()
                .orElseThrow();
    }

    private static Color linkColor(SkyIslandCaveConnectionKind kind) {
        return switch (kind) {
            case VOID_CONTINUITY -> CONTINUITY;
            case FRACTURE_BRIDGE -> FRACTURE_LINK;
            case AQUIFER_BRIDGE -> AQUIFER_LINK;
            case MIXED_GEOLOGIC_BRIDGE -> MIXED_LINK;
        };
    }

    private static Color nodeColor(double groundwater) {
        return blend(new Color(238, 211, 128), new Color(51, 114, 181), groundwater);
    }

    private static int depthX(
            io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition position,
            SkyIslandDescriptor descriptor) {
        double normalized = position.x() / descriptor.nominalRadius();
        return clampPixel((int) Math.round((normalized + 1.0) * 0.5 * (MAP - 1)));
    }

    private static int depthY(
            io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition position) {
        return clampPixel((int) Math.round(position.depthFraction() * (MAP - 1)));
    }

    private static int planX(
            io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition position,
            SkyIslandDescriptor descriptor) {
        double extent = descriptor.nominalRadius() * 1.03;
        return clampPixel((int) Math.round((position.x() / extent + 1.0) * 0.5 * (MAP - 1)));
    }

    private static int planY(
            io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition position,
            SkyIslandDescriptor descriptor) {
        double extent = descriptor.nominalRadius() * 1.03;
        return clampPixel((int) Math.round((1.0 - (position.z() / extent + 1.0) * 0.5) * (MAP - 1)));
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
}
