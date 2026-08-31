package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import io.github.nidaba.skyforge.world.WorldRegionTerrain;
import io.github.nidaba.skyforge.world.WorldSampleGrid;
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

/** Writes human-reviewable evidence for backend-neutral terrain semantic bands. */
public final class SkyIslandTerrainSemanticEvidenceWriter {
    private static final Color AIR = new Color(246, 244, 238);
    private static final Color TEXT = new Color(38, 42, 50);

    /** Writes summary, top-surface, orthogonal section, isometric, and legend evidence. */
    public void write(WorldRegionTerrain terrain, Path output, String label, String version)
            throws IOException {
        Files.createDirectories(output);
        writeSummary(terrain, output, label, version);
        writeLegend(output.resolve("legend.png"));
        writeTopSurface(terrain, output.resolve("top-surface-semantics.png"));
        writeEastWestSection(terrain, output.resolve("east-west-section.png"));
        writeNorthSouthSection(terrain, output.resolve("north-south-section.png"));
        writeIsometric(terrain, output.resolve("isometric-top-semantics.png"));
    }

    private static void writeSummary(
            WorldRegionTerrain terrain, Path output, String label, String version) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"schemaVersion\": 1,\n")
                .append("  \"skyforgeVersion\": \"").append(escape(version)).append("\",\n")
                .append("  \"label\": \"").append(escape(label)).append("\",\n")
                .append("  \"semanticSha256\": \"").append(terrain.sha256()).append("\",\n")
                .append("  \"solidSampleCount\": ").append(terrain.solidSampleCount()).append(",\n")
                .append("  \"spatialQueries\": ").append(terrain.spatialQueries()).append(",\n")
                .append("  \"candidateVolumeReferences\": ")
                .append(terrain.candidateVolumeReferences()).append(",\n")
                .append("  \"counts\": {\n");
        SkyIslandTerrainSemantic[] semantics = SkyIslandTerrainSemantic.values();
        for (int index = 0; index < semantics.length; index++) {
            SkyIslandTerrainSemantic semantic = semantics[index];
            json.append("    \"").append(semantic).append("\": ").append(terrain.count(semantic));
            json.append(index + 1 == semantics.length ? "\n" : ",\n");
        }
        WorldSampleGrid grid = terrain.grid();
        json.append("  },\n")
                .append(String.format(Locale.ROOT,
                        "  \"grid\": {\"xSamples\": %d, \"ySamples\": %d, \"zSamples\": %d, "
                                + "\"spacingX\": %.3f, \"spacingY\": %.3f, \"spacingZ\": %.3f, "
                                + "\"minimumX\": %.3f, \"maximumX\": %.3f, "
                                + "\"minimumY\": %.3f, \"maximumY\": %.3f, "
                                + "\"minimumZ\": %.3f, \"maximumZ\": %.3f}\n",
                        grid.xSamples(), grid.ySamples(), grid.zSamples(),
                        grid.spacingX(), grid.spacingY(), grid.spacingZ(),
                        grid.minimumX(), grid.maximumX(),
                        grid.minimumY(), grid.maximumY(),
                        grid.minimumZ(), grid.maximumZ()))
                .append("}\n");
        Files.writeString(output.resolve("summary.json"), json, StandardCharsets.UTF_8);
    }

    private static void writeTopSurface(WorldRegionTerrain terrain, Path output) throws IOException {
        WorldSampleGrid grid = terrain.grid();
        int scale = Math.max(2, Math.min(8, 1400 / Math.max(grid.xSamples(), grid.zSamples())));
        BufferedImage image = new BufferedImage(
                grid.xSamples() * scale, grid.zSamples() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                SkyIslandTerrainSemantic semantic = topSemantic(terrain, x, z);
                if (!semantic.isSolid()) {
                    continue;
                }
                g.setColor(color(semantic));
                g.fillRect(x * scale, (grid.zSamples() - 1 - z) * scale, scale, scale);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeEastWestSection(WorldRegionTerrain terrain, Path output) throws IOException {
        int z = terrain.grid().zSamples() / 2;
        writeSection(terrain, output, true, z);
    }

    private static void writeNorthSouthSection(WorldRegionTerrain terrain, Path output) throws IOException {
        int x = terrain.grid().xSamples() / 2;
        writeSection(terrain, output, false, x);
    }

    private static void writeSection(
            WorldRegionTerrain terrain, Path output, boolean eastWest, int fixedIndex) throws IOException {
        WorldSampleGrid grid = terrain.grid();
        int horizontalSamples = eastWest ? grid.xSamples() : grid.zSamples();
        int scale = Math.max(2, Math.min(8, 1400 / Math.max(horizontalSamples, grid.ySamples())));
        BufferedImage image = new BufferedImage(
                horizontalSamples * scale, grid.ySamples() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(AIR);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int h = 0; h < horizontalSamples; h++) {
                SkyIslandTerrainSemantic semantic = eastWest
                        ? terrain.semanticAt(h, y, fixedIndex)
                        : terrain.semanticAt(fixedIndex, y, h);
                if (!semantic.isSolid()) {
                    continue;
                }
                g.setColor(color(semantic));
                g.fillRect(h * scale, (grid.ySamples() - 1 - y) * scale, scale, scale);
            }
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeIsometric(WorldRegionTerrain terrain, Path output) throws IOException {
        WorldSampleGrid grid = terrain.grid();
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                int y = topSolidY(terrain, x, z);
                if (y < 0) {
                    continue;
                }
                double wx = grid.xAt(x);
                double wy = grid.yAt(y);
                double wz = grid.zAt(z);
                double u = wx - wz;
                double v = 0.34 * (wx + wz) - 2.1 * wy;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }
        int width = 1500;
        int height = 900;
        int margin = 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        g.setColor(AIR);
        g.fillRect(0, 0, width, height);
        if (Double.isFinite(minU)) {
            double scale = Math.min(
                    (width - 2.0 * margin) / Math.max(1.0, maxU - minU),
                    (height - 2.0 * margin) / Math.max(1.0, maxV - minV));
            for (int z = 0; z < grid.zSamples(); z++) {
                for (int x = 0; x < grid.xSamples(); x++) {
                    int y = topSolidY(terrain, x, z);
                    if (y < 0) {
                        continue;
                    }
                    SkyIslandTerrainSemantic semantic = terrain.semanticAt(x, y, z);
                    double wx = grid.xAt(x);
                    double wy = grid.yAt(y);
                    double wz = grid.zAt(z);
                    double u = wx - wz;
                    double v = 0.34 * (wx + wz) - 2.1 * wy;
                    int sx = margin + (int) Math.round((u - minU) * scale);
                    int sy = height - margin - (int) Math.round((v - minV) * scale);
                    g.setColor(color(semantic));
                    g.fillOval(sx - 2, sy - 2, 5, 5);
                }
            }
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("Skyforge terrain semantics — topmost solid samples", 30, 32);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void writeLegend(Path output) throws IOException {
        int width = 520;
        int height = 280;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        g.setColor(AIR);
        g.fillRect(0, 0, width, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.setColor(TEXT);
        g.drawString("Backend-neutral terrain roles", 24, 30);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        int y = 62;
        for (SkyIslandTerrainSemantic semantic : SkyIslandTerrainSemantic.values()) {
            g.setColor(color(semantic));
            g.fillRect(24, y - 14, 28, 18);
            g.setColor(TEXT);
            g.drawString(semantic.name(), 66, y);
            y += 34;
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static SkyIslandTerrainSemantic topSemantic(WorldRegionTerrain terrain, int x, int z) {
        int y = topSolidY(terrain, x, z);
        return y < 0 ? SkyIslandTerrainSemantic.AIR : terrain.semanticAt(x, y, z);
    }

    private static int topSolidY(WorldRegionTerrain terrain, int x, int z) {
        for (int y = terrain.grid().ySamples() - 1; y >= 0; y--) {
            if (terrain.semanticAt(x, y, z).isSolid()) {
                return y;
            }
        }
        return -1;
    }

    private static Color color(SkyIslandTerrainSemantic semantic) {
        return switch (semantic) {
            case AIR -> AIR;
            case SURFACE_MANTLE -> new Color(104, 151, 92);
            case EDGE_SHELL -> new Color(177, 133, 84);
            case UNDERSIDE_SHELL -> new Color(85, 91, 105);
            case SHALLOW_INTERIOR -> new Color(167, 156, 132);
            case DEEP_MASS -> new Color(89, 76, 68);
        };
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
