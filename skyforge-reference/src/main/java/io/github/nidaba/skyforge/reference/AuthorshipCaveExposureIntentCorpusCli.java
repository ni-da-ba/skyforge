package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveChamberGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureIntent;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposurePlan;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposurePlanner;
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

/** Generates deterministic AUTH-0028 cave-exposure intent evidence. */
public final class AuthorshipCaveExposureIntentCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 190;
    private static final int HEADER = 72;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color OUTSIDE = Color.WHITE;
    private static final Color OWNED = new Color(241, 241, 237);
    private static final Color CAVE = new Color(76, 64, 86);
    private static final Color CHAMBER = new Color(177, 139, 196);
    private static final Color UPPER = new Color(190, 94, 55);
    private static final Color UNDERSIDE = new Color(68, 101, 167);

    private AuthorshipCaveExposureIntentCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-cave-exposure-intent-v1");
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
                "role,islandKey,morphology,caveSystems,exposedSystems,sealedSystems,"
                        + "upperIntents,undersideIntents,acceptedSide,score,semanticGap,"
                        + "proximitySupport,fractureSupport,weatheringSupport,hydrologicSupport\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandCaveExposurePlan plan = SkyIslandCaveExposurePlanner.plan(descriptor);
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

            SkyIslandCaveExposureIntent intent =
                    plan.intents().isEmpty() ? null : plan.intents().getFirst();
            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(plan.geometry().systems().size()).append(',')
                    .append(plan.exposedSystemCount()).append(',')
                    .append(plan.sealedSystemCount()).append(',')
                    .append(plan.upperSurfaceCount()).append(',')
                    .append(plan.undersideCount()).append(',')
                    .append(intent == null ? "sealed" : intent.side().name().toLowerCase(Locale.ROOT)).append(',')
                    .append(format(intent == null ? 0.0 : intent.score())).append(',')
                    .append(format(intent == null ? 0.0 : intent.semanticGap())).append(',')
                    .append(format(intent == null ? 0.0 : intent.proximitySupport())).append(',')
                    .append(format(intent == null ? 0.0 : intent.fractureSupport())).append(',')
                    .append(format(intent == null ? 0.0 : intent.weatheringSupport())).append(',')
                    .append(format(intent == null ? 0.0 : intent.hydrologicSupport())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0028</title>"
                        + "<h1>Cave exposure intent</h1>"
                        + "<p>CAVE SECTION is accepted AUTH-0025 geometry. EXPOSURE SECTION adds "
                        + "only the accepted AUTH-0028 exterior intent. The intent line is schematic "
                        + "and is not entrance geometry. TOP-DOWN ANCHOR shows the selected x/z. "
                        + "SUPPORT records the explainable acceptance terms.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposurePlan plan) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandCaveExposureIntent intent =
                plan.intents().isEmpty() ? null : plan.intents().getFirst();

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                role + " / key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                "systems=" + plan.geometry().systems().size()
                        + " exposed=" + plan.exposedSystemCount()
                        + " sealed=" + plan.sealedSystemCount()
                        + " decision=" + (intent == null ? "SEALED" : intent.side()),
                7,
                35);
        g.drawString(
                intent == null
                        ? "no exterior intent accepted"
                        : String.format(
                                Locale.ROOT,
                                "score=%.3f gap=%.3f proximity=%.3f fracture=%.3f weather=%.3f hydro=%.3f",
                                intent.score(),
                                intent.semanticGap(),
                                intent.proximitySupport(),
                                intent.fractureSupport(),
                                intent.weatheringSupport(),
                                intent.hydrologicSupport()),
                7,
                50);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "CAVE SECTION", 0, MAP, 65);
        centered(g, "EXPOSURE SECTION", MAP, MAP, 65);
        centered(g, "TOP-DOWN ANCHOR", 2 * MAP, MAP, 65);
        centered(g, "SUPPORT", 3 * MAP, MAP, 65);

        drawSectionBackground(image, 0, descriptor);
        drawSectionBackground(image, MAP, descriptor);
        drawPlanBackground(image, 2 * MAP, descriptor);
        drawSupportBackground(image, 3 * MAP);

        drawGeometrySection(g, 0, descriptor, plan);
        drawGeometrySection(g, MAP, descriptor, plan);
        if (intent != null) {
            drawExposureSection(g, MAP, descriptor, intent);
            drawTopDownIntent(g, 2 * MAP, descriptor, intent);
            drawSupport(g, 3 * MAP, intent);
        } else {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(100, 100, 100));
            centered(
                    g,
                    plan.geometry().systems().isEmpty() ? "NO CAVE SYSTEM" : "SEALED",
                    MAP,
                    MAP,
                    HEADER + MAP / 2);
            centered(g, "NO EXTERIOR ANCHOR", 2 * MAP, MAP, HEADER + MAP / 2);
            centered(g, "NO ACCEPTED INTENT", 3 * MAP, MAP, HEADER + MAP / 2);
        }

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void drawGeometrySection(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposurePlan plan) {
        g.setColor(CAVE);
        g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandCaveSystemGeometry system : plan.geometry().systems()) {
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
        for (SkyIslandCaveSystemGeometry system : plan.geometry().systems()) {
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

    private static void drawExposureSection(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureIntent intent) {
        Color color = intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE;
        int x = offsetX + depthX(intent.caveAnchor(), descriptor);
        int caveY = HEADER + depthY(intent.caveAnchor());
        int boundaryY = HEADER + depthY(intent.boundaryAnchor());

        g.setColor(color);
        g.setStroke(new BasicStroke(2.4f));
        g.drawLine(x, caveY, x, boundaryY);
        g.fillOval(x - 4, caveY - 4, 9, 9);
        g.drawOval(x - 6, boundaryY - 3, 12, 6);
    }

    private static void drawTopDownIntent(
            Graphics2D g,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandCaveExposureIntent intent) {
        double extent = descriptor.nominalRadius() * 1.03;
        int x = offsetX + planX(intent.boundaryAnchor(), extent);
        int y = HEADER + planY(intent.boundaryAnchor(), extent);
        g.setColor(intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE);
        g.fillOval(x - 7, y - 7, 15, 15);
        g.setColor(Color.BLACK);
        g.drawOval(x - 7, y - 7, 15, 15);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString(
                intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? "UPPER" : "UNDERSIDE",
                offsetX + 6,
                HEADER + 15);
    }

    private static void drawSupport(
            Graphics2D g,
            int offsetX,
            SkyIslandCaveExposureIntent intent) {
        String[] labels = {"proximity", "fracture", "weather", "hydrology", "TOTAL"};
        double[] values = {
            intent.proximitySupport(),
            intent.fractureSupport(),
            intent.weatheringSupport(),
            intent.hydrologicSupport(),
            intent.score()
        };
        int barX = offsetX + 68;
        int barWidth = MAP - 82;
        int top = HEADER + 24;
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int index = 0; index < labels.length; index++) {
            int y = top + index * 29;
            g.setColor(Color.DARK_GRAY);
            g.drawString(labels[index], offsetX + 7, y + 10);
            g.setColor(new Color(225, 225, 222));
            g.fillRect(barX, y, barWidth, 12);
            Color color = index == labels.length - 1
                    ? (intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? UPPER : UNDERSIDE)
                    : new Color(105, 105, 112);
            g.setColor(color);
            g.fillRect(barX, y, (int) Math.round(barWidth * values[index]), 12);
            g.setColor(Color.BLACK);
            g.drawRect(barX, y, barWidth, 12);
            g.drawString(String.format(Locale.ROOT, "%.2f", values[index]), barX, y + 25);
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

    private static void drawSupportBackground(BufferedImage image, int offsetX) {
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                image.setRGB(offsetX + px, HEADER + py, new Color(248, 248, 246).getRGB());
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

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private record Selection(String role, long key) {}
}
