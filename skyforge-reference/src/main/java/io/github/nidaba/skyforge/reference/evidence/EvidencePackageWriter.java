package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Writes a deterministic, backend-neutral island evidence directory. */
public final class EvidencePackageWriter {
    /** Schema of the evidence manifest and its fixed artifact set. */
    public static final int MANIFEST_SCHEMA_VERSION = 1;

    private static final int CROSS_SECTION_IMAGE_HEIGHT = 256;

    private final CanonicalGraphJson graphCodec = new CanonicalGraphJson();

    /** Writes every numerical, visual, graph, and manifest artifact, replacing named outputs. */
    public Path write(IslandEvidence evidence, Path directory, String engineVersion) throws IOException {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(directory, "directory");
        requireText("engineVersion", engineVersion);
        Files.createDirectories(directory);

        LinkedHashMap<String, Path> artifacts = new LinkedHashMap<>();
        writeText(directory, artifacts, "descriptor.json", descriptorJson(evidence.compiledIsland().descriptor()));
        writeBytes(
                directory,
                artifacts,
                "height-graph.json",
                graphCodec.write(evidence.compiledIsland().heightGraph()));
        writeBytes(
                directory,
                artifacts,
                "density-graph.json",
                graphCodec.write(evidence.compiledIsland().densityGraph()));
        writeGrid(directory, artifacts, "height.grid", evidence.height());
        writeGrid(directory, artifacts, "land-mask.grid", evidence.landMask());
        writeGrid(directory, artifacts, "slope.grid", evidence.slope());
        writeText(directory, artifacts, "east-west.csv", evidence.eastWest().canonicalCsv());
        writeText(directory, artifacts, "north-south.csv", evidence.northSouth().canonicalCsv());
        writePng(
                directory,
                artifacts,
                "height.png",
                scalarImage(evidence.height(), 0.0, evidence.compiledIsland().descriptor().maximumElevation()));
        writePng(directory, artifacts, "land-mask.png", scalarImage(evidence.landMask(), 0.0, 1.0));
        writePng(
                directory,
                artifacts,
                "slope.png",
                scalarImage(evidence.slope(), 0.0, evidence.slopeStatistics().maximum()));
        writePng(
                directory,
                artifacts,
                "east-west.png",
                crossSectionImage(evidence.eastWest()));
        writePng(
                directory,
                artifacts,
                "north-south.png",
                crossSectionImage(evidence.northSouth()));

        Map<String, String> artifactHashes = new LinkedHashMap<>();
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            artifactHashes.put(artifact.getKey(), sha256(Files.readAllBytes(artifact.getValue())));
        }
        Path manifest = directory.resolve("manifest.json");
        Files.writeString(
                manifest,
                manifestJson(evidence, engineVersion, artifactHashes),
                StandardCharsets.UTF_8);
        return manifest;
    }

    private static void writeText(
            Path directory, Map<String, Path> artifacts, String name, String content) throws IOException {
        Path target = directory.resolve(name);
        Files.writeString(target, content, StandardCharsets.UTF_8);
        artifacts.put(name, target);
    }

    private static void writeBytes(
            Path directory, Map<String, Path> artifacts, String name, byte[] content) throws IOException {
        Path target = directory.resolve(name);
        Files.write(target, content);
        artifacts.put(name, target);
    }

    private static void writeGrid(
            Path directory, Map<String, Path> artifacts, String name, ScalarGrid grid) throws IOException {
        Path target = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(target)) {
            grid.writeCanonical(output);
        }
        artifacts.put(name, target);
    }

    private static void writePng(
            Path directory, Map<String, Path> artifacts, String name, BufferedImage image) throws IOException {
        Path target = directory.resolve(name);
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("no PNG writer is available");
        }
        artifacts.put(name, target);
    }

    private static BufferedImage scalarImage(ScalarGrid grid, double minimum, double maximum) {
        BufferedImage image = new BufferedImage(
                grid.specification().width(), grid.specification().height(), BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        double range = maximum - minimum;
        for (int z = 0; z < grid.specification().height(); z++) {
            for (int x = 0; x < grid.specification().width(); x++) {
                double normalized = range > 0.0 ? (grid.valueAt(x, z) - minimum) / range : 0.0;
                int gray = (int) Math.round(255.0 * clamp01(normalized));
                raster.setSample(x, z, 0, gray);
            }
        }
        return image;
    }

    private static BufferedImage crossSectionImage(CrossSection section) {
        BufferedImage image = new BufferedImage(
                section.size(), CROSS_SECTION_IMAGE_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.setSample(x, y, 0, 255);
            }
        }

        double[] heights = section.heights();
        double minimum = Math.min(0.0, minimum(heights));
        double maximum = Math.max(0.0, maximum(heights));
        if (maximum == minimum) {
            maximum = minimum + 1.0;
        }
        int seaY = imageY(0.0, minimum, maximum, image.getHeight());
        for (int x = 0; x < image.getWidth(); x++) {
            raster.setSample(x, seaY, 0, 160);
        }
        for (int x = 1; x < image.getWidth(); x++) {
            int priorY = imageY(heights[x - 1], minimum, maximum, image.getHeight());
            int currentY = imageY(heights[x], minimum, maximum, image.getHeight());
            drawLine(raster, x - 1, priorY, x, currentY);
        }
        return image;
    }

    private static int imageY(double value, double minimum, double maximum, int height) {
        double normalized = (value - minimum) / (maximum - minimum);
        return height - 1 - (int) Math.round(clamp01(normalized) * (height - 1));
    }

    private static void drawLine(WritableRaster raster, int x0, int y0, int x1, int y1) {
        int x = x0;
        int y = y0;
        int deltaX = Math.abs(x1 - x0);
        int stepX = x0 < x1 ? 1 : -1;
        int deltaY = -Math.abs(y1 - y0);
        int stepY = y0 < y1 ? 1 : -1;
        int error = deltaX + deltaY;
        while (true) {
            raster.setSample(x, y, 0, 0);
            if (x == x1 && y == y1) {
                return;
            }
            int doubled = 2 * error;
            if (doubled >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubled <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    private static double minimum(double[] values) {
        double minimum = values[0];
        for (double value : values) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static double maximum(double[] values) {
        double maximum = values[0];
        for (double value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    private static String descriptorJson(IslandDescriptor descriptor) {
        StringBuilder json = new StringBuilder();
        appendDescriptor(json, descriptor);
        return json.append('\n').toString();
    }

    private static String manifestJson(
            IslandEvidence evidence, String engineVersion, Map<String, String> artifactHashes) {
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":").append(MANIFEST_SCHEMA_VERSION);
        json.append(",\"engineVersion\":");
        appendJsonString(json, engineVersion);
        json.append(",\"provenance\":{\"world\":\"skyforge-v0.1-proof\",\"province\":\"reference\",\"cluster\":\"reference\",\"island\":\"signal-free-island-v1\"}");
        json.append(",\"descriptor\":");
        appendDescriptor(json, evidence.compiledIsland().descriptor());
        json.append(",\"recipeVersion\":").append(evidence.compiledIsland().recipeVersion());
        json.append(",\"graphSchemaVersion\":").append(evidence.compiledIsland().graphSchemaVersion());
        appendSampling(json, evidence.height().specification());
        json.append(",\"statistics\":{\"height\":");
        appendStatistics(json, evidence.heightStatistics());
        json.append(",\"slope\":");
        appendStatistics(json, evidence.slopeStatistics());
        json.append('}');
        appendMetrics(json, evidence.metrics());
        json.append(",\"canonicalChecksums\":{\"heightGrid\":\"")
                .append(evidence.height().sha256())
                .append("\",\"landMaskGrid\":\"")
                .append(evidence.landMask().sha256())
                .append("\",\"slopeGrid\":\"")
                .append(evidence.slope().sha256())
                .append("\"}");
        json.append(",\"artifacts\":[");
        int index = 0;
        for (Map.Entry<String, String> artifact : artifactHashes.entrySet()) {
            if (index++ != 0) {
                json.append(',');
            }
            json.append("{\"path\":");
            appendJsonString(json, artifact.getKey());
            json.append(",\"sha256\":\"").append(artifact.getValue()).append("\"}");
        }
        return json.append("]}\n").toString();
    }

    private static void appendDescriptor(StringBuilder json, IslandDescriptor descriptor) {
        json.append("{\"schemaVersion\":").append(descriptor.schemaVersion());
        json.append(",\"seed\":\"0x")
                .append(HexFormat.of().toHexDigits(descriptor.seed()))
                .append('"');
        appendHexMember(json, "centerX", descriptor.centerX());
        appendHexMember(json, "centerZ", descriptor.centerZ());
        appendHexMember(json, "nominalRadius", descriptor.nominalRadius());
        appendHexMember(json, "maximumElevation", descriptor.maximumElevation());
        appendHexMember(json, "coastalFalloff", descriptor.coastalFalloff());
        appendHexMember(json, "ridgeAzimuth", descriptor.ridgeAzimuth());
        appendHexMember(json, "ridgeStrength", descriptor.ridgeStrength());
        appendHexMember(json, "signalAmplitude", descriptor.signalAmplitude());
        appendHexMember(json, "signalScale", descriptor.signalScale());
        json.append('}');
    }

    private static void appendSampling(StringBuilder json, GridSpec grid) {
        json.append(",\"sampling\":{\"gridBinarySchemaVersion\":")
                .append(ScalarGrid.BINARY_SCHEMA_VERSION)
                .append(",\"layout\":\"z-row-x-column\",\"bounds\":{");
        json.append("\"minimumX\":\"").append(Double.toHexString(grid.minimumX())).append('"');
        appendHexMember(json, "maximumX", grid.maximumX());
        appendHexMember(json, "minimumZ", grid.minimumZ());
        appendHexMember(json, "maximumZ", grid.maximumZ());
        json.append("},\"width\":").append(grid.width());
        json.append(",\"height\":").append(grid.height());
        json.append(",\"slopeRule\":\"central-interior-one-sided-boundary\"}");
    }

    private static void appendStatistics(StringBuilder json, GridStatistics statistics) {
        json.append("{\"minimum\":\"").append(Double.toHexString(statistics.minimum())).append('"');
        appendHexMember(json, "maximum", statistics.maximum());
        appendHexMember(json, "mean", statistics.mean());
        json.append(",\"sampleCount\":").append(statistics.sampleCount()).append('}');
    }

    private static void appendMetrics(StringBuilder json, IslandMetrics metrics) {
        json.append(",\"morphology\":{\"landSampleCount\":").append(metrics.landSampleCount());
        json.append(",\"connectedLandComponents\":").append(metrics.connectedLandComponents());
        json.append(",\"boundaryLandSampleCount\":").append(metrics.boundaryLandSampleCount());
        appendHexMember(json, "estimatedLandArea", metrics.estimatedLandArea());
        appendHexMember(json, "landCentroidX", metrics.landCentroidX());
        appendHexMember(json, "landCentroidZ", metrics.landCentroidZ());
        json.append('}');
    }

    private static void appendHexMember(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":\"").append(Double.toHexString(value)).append('"');
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u00");
                        json.append(Character.forDigit((character >>> 4) & 0xf, 16));
                        json.append(Character.forDigit(character & 0xf, 16));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
    }
}
