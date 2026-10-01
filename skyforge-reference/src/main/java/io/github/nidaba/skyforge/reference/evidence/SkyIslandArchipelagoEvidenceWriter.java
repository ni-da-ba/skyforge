package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
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
import java.util.Locale;
import javax.imageio.ImageIO;

/** Writes regional planner intent and realized hierarchical archipelago evidence. */
public final class SkyIslandArchipelagoEvidenceWriter {
    private static final Color AIR = new Color(246, 244, 238);
    private static final Color GRID = new Color(220, 218, 210);
    private static final Color TEXT = new Color(40, 44, 52);

    public void write(SkyIslandArchipelagoEvidence evidence, Path output, String version)
            throws IOException {
        Files.createDirectories(output);
        writeSummary(evidence, output, version);
        writeGroups(evidence, output);
        writePlan(evidence, output.resolve("plan.png"));
        writeTopDown(evidence, output.resolve("top-down-groups.png"));
        writeEnvelope(evidence, output.resolve("upper-envelope.png"), true);
        writeEnvelope(evidence, output.resolve("underside-envelope.png"), false);
        writeIsometric(evidence, output.resolve("isometric.png"));
    }

    private static void writeSummary(
            SkyIslandArchipelagoEvidence evidence, Path output, String version) throws IOException {
        var metrics = evidence.metrics();
        var grid = evidence.occupancy().specification();
        String json = String.format(Locale.ROOT, """
                {
                  "schemaVersion": 1,
                  "skyforgeVersion": "%s",
                  "rootSeed": %d,
                  "layout": "%s",
                  "groupCount": %d,
                  "islandCount": %d,
                  "solidSampleCount": %d,
                  "connectedComponents": %d,
                  "overlappingSolidSamples": %d,
                  "crossGroupOverlappingSolidSamples": %d,
                  "faceContacts": %d,
                  "minimumObservedGroupGap": %.9f,
                  "occupancySha256": "%s",
                  "grid": {"xSamples": %d, "ySamples": %d, "zSamples": %d,
                    "minimumX": %.3f, "maximumX": %.3f,
                    "minimumY": %.3f, "maximumY": %.3f,
                    "minimumZ": %.3f, "maximumZ": %.3f}
                }
                """,
                escape(version),
                evidence.plan().rootSeed(),
                evidence.plan().layout().kind(),
                metrics.groupCount(),
                metrics.islandCount(),
                metrics.solidSampleCount(),
                metrics.connectedComponents(),
                metrics.overlappingSolidSamples(),
                metrics.crossGroupOverlappingSolidSamples(),
                metrics.faceContacts(),
                metrics.minimumObservedGroupGap(),
                evidence.occupancy().sha256(),
                grid.xSamples(), grid.ySamples(), grid.zSamples(),
                grid.minimumX(), grid.maximumX(),
                grid.minimumY(), grid.maximumY(),
                grid.minimumZ(), grid.maximumZ());
        Files.writeString(output.resolve("summary.json"), json, StandardCharsets.UTF_8);
    }

    private static void writeGroups(SkyIslandArchipelagoEvidence evidence, Path output)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "ordinal,identifier,role,centerX,baseSuspensionElevation,centerZ,reservedGroupRadius,orientation,memberCount,solidSamples\n");
        for (SkyIslandArchipelagoGroupPlan group : evidence.plan().groups()) {
            csv.append(group.ordinal()).append(',')
                    .append(group.identifier()).append(',')
                    .append(group.role()).append(',')
                    .append(group.centerX()).append(',')
                    .append(group.baseSuspensionElevation()).append(',')
                    .append(group.centerZ()).append(',')
                    .append(group.reservedGroupRadius()).append(',')
                    .append(group.orientationRadians()).append(',')
                    .append(group.groupPlan().memberCount()).append(',')
                    .append(evidence.metrics().groupSolidSampleCounts().get(group.ordinal()))
                    .append('\n');
        }
        Files.writeString(output.resolve("groups.csv"), csv, StandardCharsets.UTF_8);
    }

    private static void writePlan(SkyIslandArchipelagoEvidence evidence, Path output)
            throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int width = 1600;
        int height = 1000;
        int margin = 80;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        g.setColor(AIR);
        g.fillRect(0, 0, width, height);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i <= 10; i++) {
            int x = margin + (width - 2 * margin) * i / 10;
            int y = margin + (height - 2 * margin) * i / 10;
            g.drawLine(x, margin, x, height - margin);
            g.drawLine(margin, y, width - margin, y);
        }

        double xScale = (width - 2.0 * margin) / (grid.maximumX() - grid.minimumX());
        double zScale = (height - 2.0 * margin) / (grid.maximumZ() - grid.minimumZ());
        for (SkyIslandArchipelagoGroupPlan group : evidence.plan().groups()) {
            int cx = mapX(group.centerX(), grid, width, margin);
            int cy = mapZ(group.centerZ(), grid, height, margin);
            int rx = (int) Math.round(group.reservedGroupRadius() * xScale);
            int rz = (int) Math.round(group.reservedGroupRadius() * zScale);
            Color color = groupColor(group.ordinal());
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
            g.fillOval(cx - rx, cy - rz, 2 * rx, 2 * rz);
            g.setColor(color.darker());
            g.setStroke(new BasicStroke(group.ordinal() == 0 ? 4.0f : 2.5f));
            g.drawOval(cx - rx, cy - rz, 2 * rx, 2 * rz);
            g.fillOval(cx - 6, cy - 6, 12, 12);

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(
                    group.ordinal() + " " + group.role() + " " + group.identifier(),
                    cx + 10,
                    cy - 10);
            for (var member : group.groupPlan().members()) {
                int mx = mapX(member.descriptor().centerX(), grid, width, margin);
                int my = mapZ(member.descriptor().centerZ(), grid, height, margin);
                g.setColor(color.darker());
                g.fillOval(mx - 3, my - 3, 6, 6);
            }
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString("Skyforge archipelago plan — " + evidence.plan().layout().kind(), margin, 38);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.drawString("large envelopes = child-group reservations; small dots = island centers", margin, 60);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeTopDown(SkyIslandArchipelagoEvidence evidence, Path output)
            throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int scale = Math.max(2, Math.min(5, 1500 / Math.max(grid.xSamples(), grid.zSamples())));
        BufferedImage image = new BufferedImage(
                grid.xSamples() * scale, grid.zSamples() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int[] owners = evidence.horizontalGroupOwner();
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int group = owners[z * grid.xSamples() + x];
                if (group < 0) {
                    continue;
                }
                g.setColor(groupColor(group));
                g.fillRect(x * scale, (grid.zSamples() - 1 - z) * scale, scale, scale);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeEnvelope(
            SkyIslandArchipelagoEvidence evidence, Path output, boolean upper) throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        double[] values = upper ? evidence.upperEnvelope() : evidence.undersideEnvelope();
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (Double.isFinite(value)) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        int scale = Math.max(2, Math.min(5, 1500 / Math.max(grid.xSamples(), grid.zSamples())));
        BufferedImage image = new BufferedImage(
                grid.xSamples() * scale, grid.zSamples() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        double span = Math.max(1.0e-9, maximum - minimum);
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                double value = values[z * grid.xSamples() + x];
                if (!Double.isFinite(value)) {
                    continue;
                }
                float normalized = (float) ((value - minimum) / span);
                g.setColor(elevationColor(normalized));
                g.fillRect(x * scale, (grid.zSamples() - 1 - z) * scale, scale, scale);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    /** Fits the actual regional surface points to the canvas, avoiding the undersized prior isometric. */
    private static void writeIsometric(SkyIslandArchipelagoEvidence evidence, Path output)
            throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        double[] upper = evidence.upperEnvelope();
        int[] owners = evidence.horizontalGroupOwner();
        double minimumU = Double.POSITIVE_INFINITY;
        double maximumU = Double.NEGATIVE_INFINITY;
        double minimumV = Double.POSITIVE_INFINITY;
        double maximumV = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int index = z * grid.xSamples() + x;
                if (owners[index] < 0 || !Double.isFinite(upper[index])) {
                    continue;
                }
                double wx = grid.xAt(x) - evidence.plan().centerX();
                double wz = grid.zAt(z) - evidence.plan().centerZ();
                double wy = upper[index] - evidence.plan().baseSuspensionElevation();
                double u = wx - wz;
                double v = 0.34 * (wx + wz) - 2.2 * wy;
                minimumU = Math.min(minimumU, u);
                maximumU = Math.max(maximumU, u);
                minimumV = Math.min(minimumV, v);
                maximumV = Math.max(maximumV, v);
            }
        }
        int width = 1600;
        int height = 1000;
        int margin = 70;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        g.setColor(AIR);
        g.fillRect(0, 0, width, height);
        double scale = Math.min(
                (width - 2.0 * margin) / Math.max(1.0, maximumU - minimumU),
                (height - 2.0 * margin) / Math.max(1.0, maximumV - minimumV));
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int index = z * grid.xSamples() + x;
                int group = owners[index];
                if (group < 0 || !Double.isFinite(upper[index])) {
                    continue;
                }
                double wx = grid.xAt(x) - evidence.plan().centerX();
                double wz = grid.zAt(z) - evidence.plan().centerZ();
                double wy = upper[index] - evidence.plan().baseSuspensionElevation();
                double u = wx - wz;
                double v = 0.34 * (wx + wz) - 2.2 * wy;
                int sx = margin + (int) Math.round((u - minimumU) * scale);
                int sy = height - margin - (int) Math.round((v - minimumV) * scale);
                g.setColor(groupColor(group));
                g.fillOval(sx - 2, sy - 2, 5, 5);
            }
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("archipelago upper-surface isometric — colored by child group", 35, 38);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static Color groupColor(int ordinal) {
        float hue = (ordinal * 0.61803398875f) % 1.0f;
        return Color.getHSBColor(hue, 0.58f, 0.82f);
    }

    private static Color elevationColor(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return Color.getHSBColor(0.62f - 0.50f * clamped, 0.58f, 0.90f);
    }

    private static int mapX(double x, VolumeGridSpec grid, int width, int margin) {
        return margin + (int) Math.round((x - grid.minimumX())
                / (grid.maximumX() - grid.minimumX()) * (width - 2.0 * margin));
    }

    private static int mapZ(double z, VolumeGridSpec grid, int height, int margin) {
        return height - margin - (int) Math.round((z - grid.minimumZ())
                / (grid.maximumZ() - grid.minimumZ()) * (height - 2.0 * margin));
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
