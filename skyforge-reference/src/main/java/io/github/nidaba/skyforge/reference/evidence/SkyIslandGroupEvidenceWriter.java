package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
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

/** Writes group-scale plan and realized-union review artifacts. */
public final class SkyIslandGroupEvidenceWriter {
    private static final Color AIR = new Color(246, 244, 238);
    private static final Color GRID = new Color(220, 218, 210);
    private static final Color TEXT = new Color(40, 44, 52);

    /** Writes metrics, member data, plan view, union projections, sections, and isometric surface. */
    public void write(SkyIslandGroupEvidence evidence, Path output, String version) throws IOException {
        Files.createDirectories(output);
        writeSummary(evidence, output, version);
        writeMembers(evidence, output);
        writePlan(evidence, output.resolve("plan.png"));
        writeTopDown(evidence, output.resolve("top-down-union.png"));
        writeEnvelope(evidence, output.resolve("upper-envelope.png"), true);
        writeEnvelope(evidence, output.resolve("underside-envelope.png"), false);
        writeSection(evidence, output.resolve("east-west.png"), true);
        writeSection(evidence, output.resolve("north-south.png"), false);
        writeIsometric(evidence, output.resolve("isometric.png"));
    }

    private static void writeSummary(SkyIslandGroupEvidence evidence, Path output, String version)
            throws IOException {
        SkyIslandGroupMetrics metrics = evidence.metrics();
        VolumeGridSpec grid = evidence.occupancy().specification();
        String json = String.format(Locale.ROOT, """
                {
                  "schemaVersion": 1,
                  "skyforgeVersion": "%s",
                  "rootSeed": %d,
                  "layout": "%s",
                  "memberCount": %d,
                  "solidSampleCount": %d,
                  "connectedComponents": %d,
                  "overlappingSolidSamples": %d,
                  "faceContacts": %d,
                  "minimumObservedCenterSpacing": %.9f,
                  "minimumReservedGap": %.9f,
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
                metrics.memberCount(),
                metrics.solidSampleCount(),
                metrics.connectedComponents(),
                metrics.overlappingSolidSamples(),
                metrics.faceContacts(),
                metrics.minimumObservedCenterSpacing(),
                metrics.minimumReservedGap(),
                evidence.occupancy().sha256(),
                grid.xSamples(), grid.ySamples(), grid.zSamples(),
                grid.minimumX(), grid.maximumX(),
                grid.minimumY(), grid.maximumY(),
                grid.minimumZ(), grid.maximumZ());
        Files.writeString(output.resolve("summary.json"), json, StandardCharsets.UTF_8);
    }

    private static void writeMembers(SkyIslandGroupEvidence evidence, Path output) throws IOException {
        StringBuilder csv = new StringBuilder(
                "ordinal,seed,centerX,suspensionElevation,centerZ,ridgeAzimuth,reservedRadius,morphology,solidSamples\n");
        for (SkyIslandGroupMemberPlan member : evidence.plan().members()) {
            var descriptor = member.descriptor();
            csv.append(member.ordinal()).append(',')
                    .append(descriptor.seed()).append(',')
                    .append(descriptor.centerX()).append(',')
                    .append(descriptor.suspensionElevation()).append(',')
                    .append(descriptor.centerZ()).append(',')
                    .append(descriptor.ridgeAzimuth()).append(',')
                    .append(member.reservedHorizontalRadius()).append(',')
                    .append(member.morphology().stableIdentifier()).append(',')
                    .append(evidence.metrics().memberSolidSampleCounts().get(member.ordinal()))
                    .append('\n');
        }
        Files.writeString(output.resolve("members.csv"), csv, StandardCharsets.UTF_8);
    }

    private static void writePlan(SkyIslandGroupEvidence evidence, Path output) throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int width = 1400;
        int height = 900;
        int margin = 70;
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

        for (SkyIslandGroupMemberPlan member : evidence.plan().members()) {
            var d = member.descriptor();
            int cx = mapX(d.centerX(), grid, width, margin);
            int cy = mapZ(d.centerZ(), grid, height, margin);
            double xScale = (width - 2.0 * margin) / (grid.maximumX() - grid.minimumX());
            double zScale = (height - 2.0 * margin) / (grid.maximumZ() - grid.minimumZ());
            int rx = (int) Math.round(member.reservedHorizontalRadius() * xScale);
            int rz = (int) Math.round(member.reservedHorizontalRadius() * zScale);
            Color color = memberColor(member.ordinal(), evidence.plan().memberCount());
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
            g.fillOval(cx - rx, cy - rz, 2 * rx, 2 * rz);
            g.setColor(color.darker());
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(cx - rx, cy - rz, 2 * rx, 2 * rz);
            g.fillOval(cx - 5, cy - 5, 10, 10);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString(Integer.toString(member.ordinal()), cx + 8, cy - 8);
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Skyforge group plan — " + evidence.plan().layout().kind(), margin, 35);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.drawString("circles = reserved horizontal placement envelopes; dots = member centers", margin, 56);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeTopDown(SkyIslandGroupEvidence evidence, Path output) throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int scale = Math.max(2, Math.min(5, 1200 / Math.max(grid.xSamples(), grid.zSamples())));
        BufferedImage image = new BufferedImage(
                grid.xSamples() * scale, grid.zSamples() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int[] owners = evidence.ownerByHorizontalSample();
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int owner = owners[z * grid.xSamples() + x];
                if (owner < 0) {
                    continue;
                }
                g.setColor(memberColor(owner, evidence.plan().memberCount()));
                g.fillRect(x * scale, (grid.zSamples() - 1 - z) * scale, scale, scale);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeEnvelope(SkyIslandGroupEvidence evidence, Path output, boolean upper)
            throws IOException {
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
        int scale = Math.max(2, Math.min(5, 1200 / Math.max(grid.xSamples(), grid.zSamples())));
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

    private static void writeSection(SkyIslandGroupEvidence evidence, Path output, boolean eastWest)
            throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int horizontalSamples = eastWest ? grid.xSamples() : grid.zSamples();
        int fixedIndex = eastWest
                ? nearest(evidence.plan().groupCenterZ(), grid.minimumZ(), grid.spacingZ(), grid.zSamples())
                : nearest(evidence.plan().groupCenterX(), grid.minimumX(), grid.spacingX(), grid.xSamples());
        int scaleX = Math.max(2, Math.min(5, 1400 / horizontalSamples));
        int scaleY = 4;
        BufferedImage image = new BufferedImage(
                horizontalSamples * scaleX, grid.ySamples() * scaleY, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int[] owners = evidence.ownerBySample();
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int horizontal = 0; horizontal < horizontalSamples; horizontal++) {
                int x = eastWest ? horizontal : fixedIndex;
                int z = eastWest ? fixedIndex : horizontal;
                int owner = owners[grid.linearIndex(x, y, z)];
                if (owner < 0) {
                    continue;
                }
                g.setColor(memberColor(owner, evidence.plan().memberCount()));
                g.fillRect(
                        horizontal * scaleX,
                        (grid.ySamples() - 1 - y) * scaleY,
                        scaleX,
                        scaleY);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeIsometric(SkyIslandGroupEvidence evidence, Path output) throws IOException {
        VolumeGridSpec grid = evidence.occupancy().specification();
        int width = 1400;
        int height = 900;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        g.setColor(AIR);
        g.fillRect(0, 0, width, height);
        double[] upper = evidence.upperEnvelope();
        int[] owners = evidence.ownerByHorizontalSample();
        double rangeX = grid.maximumX() - grid.minimumX();
        double rangeZ = grid.maximumZ() - grid.minimumZ();
        double horizontalScale = 0.45 * Math.min(width / Math.max(1.0, rangeX), height / Math.max(1.0, rangeZ));
        double verticalScale = 0.60;
        double originX = width * 0.50;
        double originY = height * 0.72;
        for (int sum = 0; sum <= grid.xSamples() + grid.zSamples() - 2; sum++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                int x = sum - z;
                if (x < 0 || x >= grid.xSamples()) {
                    continue;
                }
                int horizontal = z * grid.xSamples() + x;
                int owner = owners[horizontal];
                if (owner < 0 || !Double.isFinite(upper[horizontal])) {
                    continue;
                }
                double worldX = grid.xAt(x) - evidence.plan().groupCenterX();
                double worldZ = grid.zAt(z) - evidence.plan().groupCenterZ();
                double worldY = upper[horizontal] - evidence.plan().baseSuspensionElevation();
                int sx = (int) Math.round(originX + (worldX - worldZ) * horizontalScale);
                int sy = (int) Math.round(originY + (worldX + worldZ) * horizontalScale * 0.36 - worldY * verticalScale);
                if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                    g.setColor(memberColor(owner, evidence.plan().memberCount()));
                    g.fillRect(sx - 1, sy - 1, 3, 3);
                }
            }
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("group upper-surface isometric", 30, 34);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static Color memberColor(int ordinal, int count) {
        float hue = (ordinal * 0.61803398875f) % 1.0f;
        return Color.getHSBColor(hue, 0.56f, 0.82f);
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

    private static int nearest(double coordinate, double minimum, double spacing, int samples) {
        long rounded = Math.round((coordinate - minimum) / spacing);
        return (int) Math.max(0L, Math.min(samples - 1L, rounded));
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
