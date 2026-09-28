package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldWaterProjection;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import io.github.nidaba.skyforge.world.WorldSampleGrid;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Renders the backend-neutral F4E water projection on the exact terrain evidence lattice. */
public final class SkyIslandHydrologySemanticEvidenceWriter {
    public static final int WET_SAMPLE_RGB = new Color(28, 106, 214).getRGB();
    public static final int REACH_CENTERLINE_RGB = new Color(255, 211, 48).getRGB();
    private static final Color REACH_OUTLINE = new Color(28, 36, 48);

    public record Summary(int wetGridSamples, int acceptedReachCount, int centerlineSegments) {}

    /**
     * Copies the terrain top view, overlays F4E wet lattice samples and accepted reach centerlines,
     * and writes a PNG. This is semantic projection evidence, not voxel/Minecraft realization.
     */
    public Summary writeTopView(
            Path terrainTopSurface,
            Path output,
            WorldSampleGrid grid,
            SkyIslandComponentFluvialWorldWaterProjection water,
            List<SkyIslandHydraulicReachGeometry> reaches,
            double centerX,
            double centerZ) throws IOException {
        if (grid == null || water == null || reaches == null) {
            throw new NullPointerException("hydrology view inputs must be non-null");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("world center must be finite");
        }
        BufferedImage terrain = ImageIO.read(terrainTopSurface.toFile());
        if (terrain == null) {
            throw new IOException("terrain top-surface PNG could not be decoded: " + terrainTopSurface);
        }
        if (grid.xSamples() <= 0 || grid.zSamples() <= 0
                || terrain.getWidth() % grid.xSamples() != 0
                || terrain.getHeight() % grid.zSamples() != 0) {
            throw new IOException("terrain image dimensions do not match the semantic lattice");
        }
        int cellWidth = terrain.getWidth() / grid.xSamples();
        int cellHeight = terrain.getHeight() / grid.zSamples();
        if (cellWidth <= 0 || cellWidth != cellHeight) {
            throw new IOException("terrain view must use uniform square semantic cells");
        }

        BufferedImage rendered =
                new BufferedImage(terrain.getWidth(), terrain.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rendered.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.drawImage(terrain, 0, 0, null);

        int wetSamples = 0;
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                if (!water.sampleWorld(grid.xAt(x), grid.zAt(z)).wet()) {
                    continue;
                }
                int pixelY = (grid.zSamples() - 1 - z) * cellHeight;
                graphics.setColor(new Color(WET_SAMPLE_RGB));
                graphics.fillRect(x * cellWidth, pixelY, cellWidth, cellHeight);
                wetSamples++;
            }
        }

        int centerlineSegments = 0;
        for (SkyIslandHydraulicReachGeometry reach : reaches) {
            Path2D.Double path = new Path2D.Double();
            boolean first = true;
            for (var sample : reach.samples()) {
                double worldX = centerX + sample.position().x();
                double worldZ = centerZ + sample.position().z();
                double pixelX = ((worldX - grid.minimumX()) / grid.spacingX()) * cellWidth
                        + cellWidth / 2.0;
                double pixelY = (grid.zSamples() - 1
                                - (worldZ - grid.minimumZ()) / grid.spacingZ())
                        * cellHeight + cellHeight / 2.0;
                if (first) {
                    path.moveTo(pixelX, pixelY);
                    first = false;
                } else {
                    path.lineTo(pixelX, pixelY);
                    centerlineSegments++;
                }
            }
            if (!first) {
                float outerWidth = Math.max(2.4f, cellWidth * 0.8f);
                float innerWidth = Math.max(1.2f, cellWidth * 0.35f);
                graphics.setStroke(new BasicStroke(
                        outerWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(REACH_OUTLINE);
                graphics.draw(path);
                graphics.setStroke(new BasicStroke(
                        innerWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(new Color(REACH_CENTERLINE_RGB));
                graphics.draw(path);
            }
        }
        graphics.dispose();

        Files.createDirectories(output.getParent());
        if (!ImageIO.write(rendered, "png", output.toFile())) {
            throw new IOException("no PNG writer available for hydrology evidence");
        }
        return new Summary(wetSamples, reaches.size(), centerlineSegments);
    }
}
