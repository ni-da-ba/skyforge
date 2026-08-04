package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import io.github.nidaba.skyforge.reference.sampling.ScalarVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Writes deterministic numerical and human-review evidence for one suspended volume. */
public final class SuspendedVolumeEvidenceWriter {
    /** Schema of the suspended-volume evidence manifest. */
    public static final int MANIFEST_SCHEMA_VERSION = 1;

    private static final int AIR_COLOR = 0xfff4f0e6;
    private static final int SOLID_COLOR = 0xff30343b;
    private static final int ANCHOR_COLOR = 0xffc66a2b;
    private static final int SLICE_SCALE = 3;

    private final CanonicalGraphJson graphCodec = new CanonicalGraphJson();

    /** Writes the complete replaceable evidence directory and returns its manifest. */
    public Path write(
            SuspendedVolumeEvidence evidence, Path directory, String engineVersion)
            throws IOException {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(directory, "directory");
        requireText("engineVersion", engineVersion);
        Files.createDirectories(directory);

        LinkedHashMap<String, Path> artifacts = new LinkedHashMap<>();
        writeText(directory, artifacts, "descriptor.json", descriptorJson(
                evidence.compiledVolume().descriptor()));
        writeBytes(directory, artifacts, "upper-surface-graph.json", graphCodec.write(
                evidence.compiledVolume().upperSurfaceGraph()));
        writeBytes(directory, artifacts, "underside-surface-graph.json", graphCodec.write(
                evidence.compiledVolume().undersideSurfaceGraph()));
        writeBytes(directory, artifacts, "density-graph.json", graphCodec.write(
                evidence.compiledVolume().densityGraph()));
        writeText(directory, artifacts, "provenance.json", provenanceJson(
                evidence.compiledVolume().provenance()));
        writeVolume(directory, artifacts, "density.volume", evidence.density());
        writeOccupancy(directory, artifacts, "occupancy.volume", evidence.occupancy());
        writeGrid(directory, artifacts, "upper-surface.grid", evidence.upperSurface());
        writeGrid(directory, artifacts, "underside-surface.grid", evidence.undersideSurface());
        writeGrid(directory, artifacts, "suspension-density.grid", evidence.suspensionDensity());
        writeText(directory, artifacts, "east-west.csv", evidence.eastWest().canonicalCsv());
        writeText(directory, artifacts, "north-south.csv", evidence.northSouth().canonicalCsv());
        writePng(directory, artifacts, "upper-surface.png", upperSurfaceImage(evidence));
        writePng(directory, artifacts, "underside.png", undersideImage(evidence));
        writePng(directory, artifacts, "suspension-occupancy.png", suspensionImage(evidence));
        writePng(directory, artifacts, "east-west.png", verticalSliceImage(
                evidence.eastWest(), evidence.compiledVolume().descriptor().suspensionElevation()));
        writePng(directory, artifacts, "north-south.png", verticalSliceImage(
                evidence.northSouth(), evidence.compiledVolume().descriptor().suspensionElevation()));
        writePng(directory, artifacts, "isometric.png", isometricImage(evidence.occupancy()));
        writeText(directory, artifacts, "index.html", galleryHtml());

        LinkedHashMap<String, String> hashes = hashArtifacts(artifacts);
        Path manifest = directory.resolve("manifest.json");
        Files.writeString(
                manifest, manifestJson(evidence, engineVersion, hashes), StandardCharsets.UTF_8);
        hashes.put("manifest.json", sha256(manifest));
        Files.writeString(
                directory.resolve("evidence.sha256"), checksumListing(hashes), StandardCharsets.UTF_8);
        return manifest;
    }

    private static void writeText(
            Path directory, Map<String, Path> artifacts, String name, String content)
            throws IOException {
        Path target = directory.resolve(name);
        Files.writeString(target, content, StandardCharsets.UTF_8);
        artifacts.put(name, target);
    }

    private static void writeBytes(
            Path directory, Map<String, Path> artifacts, String name, byte[] content)
            throws IOException {
        Path target = directory.resolve(name);
        Files.write(target, content);
        artifacts.put(name, target);
    }

    private static void writeGrid(
            Path directory, Map<String, Path> artifacts, String name, ScalarGrid grid)
            throws IOException {
        Path target = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(target)) {
            grid.writeCanonical(output);
        }
        artifacts.put(name, target);
    }

    private static void writeVolume(
            Path directory,
            Map<String, Path> artifacts,
            String name,
            ScalarVolumeGrid volume) throws IOException {
        Path target = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(target)) {
            volume.writeCanonical(output);
        }
        artifacts.put(name, target);
    }

    private static void writeOccupancy(
            Path directory,
            Map<String, Path> artifacts,
            String name,
            OccupancyVolumeGrid occupancy) throws IOException {
        Path target = directory.resolve(name);
        try (OutputStream output = Files.newOutputStream(target)) {
            occupancy.writeCanonical(output);
        }
        artifacts.put(name, target);
    }

    private static void writePng(
            Path directory, Map<String, Path> artifacts, String name, BufferedImage image)
            throws IOException {
        Path target = directory.resolve(name);
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("no PNG writer is available");
        }
        artifacts.put(name, target);
    }

    private static BufferedImage upperSurfaceImage(SuspendedVolumeEvidence evidence) {
        ScalarGrid surface = evidence.upperSurface();
        OccupancyVolumeGrid occupancy = evidence.occupancy();
        SkyIslandVolumeDescriptor descriptor = evidence.compiledVolume().descriptor();
        BufferedImage image = new BufferedImage(
                surface.specification().width(),
                surface.specification().height(),
                BufferedImage.TYPE_INT_RGB);
        double minimum = descriptor.suspensionElevation();
        double maximum = minimum + descriptor.upperElevation();
        for (int z = 0; z < surface.specification().height(); z++) {
            for (int x = 0; x < surface.specification().width(); x++) {
                if (!solidColumn(occupancy, x, z)) {
                    image.setRGB(x, z, AIR_COLOR);
                    continue;
                }
                double normalized = normalize(surface.valueAt(x, z), minimum, maximum);
                int gray = (int) Math.round(70.0 + 180.0 * normalized);
                image.setRGB(x, z, rgb(gray, gray, gray));
            }
        }
        return image;
    }

    private static BufferedImage undersideImage(SuspendedVolumeEvidence evidence) {
        ScalarGrid underside = evidence.undersideSurface();
        OccupancyVolumeGrid occupancy = evidence.occupancy();
        double anchor = evidence.compiledVolume().descriptor().suspensionElevation();
        double maximumDepth = 0.0;
        for (int z = 0; z < underside.specification().height(); z++) {
            for (int x = 0; x < underside.specification().width(); x++) {
                if (solidColumn(occupancy, x, z)) {
                    maximumDepth = Math.max(maximumDepth, anchor - underside.valueAt(x, z));
                }
            }
        }
        BufferedImage image = new BufferedImage(
                underside.specification().width(),
                underside.specification().height(),
                BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < underside.specification().height(); z++) {
            for (int x = 0; x < underside.specification().width(); x++) {
                if (!solidColumn(occupancy, x, z)) {
                    image.setRGB(x, z, AIR_COLOR);
                    continue;
                }
                double normalized = maximumDepth > 0.0
                        ? clamp01((anchor - underside.valueAt(x, z)) / maximumDepth)
                        : 0.0;
                int gray = (int) Math.round(225.0 - 185.0 * normalized);
                image.setRGB(x, z, rgb(gray, gray, gray + 5));
            }
        }
        return image;
    }

    private static BufferedImage suspensionImage(SuspendedVolumeEvidence evidence) {
        ScalarGrid slice = evidence.suspensionDensity();
        BufferedImage image = new BufferedImage(
                slice.specification().width(),
                slice.specification().height(),
                BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < slice.specification().height(); z++) {
            for (int x = 0; x < slice.specification().width(); x++) {
                image.setRGB(x, z, slice.valueAt(x, z) > 0.0 ? SOLID_COLOR : AIR_COLOR);
            }
        }
        return image;
    }

    private static BufferedImage verticalSliceImage(VolumeSlice slice, double anchor) {
        BufferedImage image = new BufferedImage(
                slice.width() * SLICE_SCALE,
                slice.height() * SLICE_SCALE,
                BufferedImage.TYPE_INT_RGB);
        fill(image, AIR_COLOR);
        int anchorIndex = nearestVerticalIndex(slice, anchor);
        int anchorImageY = (slice.height() - 1 - anchorIndex) * SLICE_SCALE;
        for (int y = anchorImageY; y < anchorImageY + SLICE_SCALE; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, ANCHOR_COLOR);
            }
        }
        for (int vertical = 0; vertical < slice.height(); vertical++) {
            for (int horizontal = 0; horizontal < slice.width(); horizontal++) {
                if (slice.densityAt(horizontal, vertical) > 0.0) {
                    int imageX = horizontal * SLICE_SCALE;
                    int imageY = (slice.height() - 1 - vertical) * SLICE_SCALE;
                    fillBlock(image, imageX, imageY, SLICE_SCALE, SOLID_COLOR);
                }
            }
        }
        return image;
    }

    private static BufferedImage isometricImage(OccupancyVolumeGrid occupancy) {
        VolumeGridSpec grid = occupancy.specification();
        int width = 2 * (grid.xSamples() + grid.zSamples() - 2) + 33;
        int height = grid.xSamples() + grid.zSamples() + 3 * grid.ySamples() + 33;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        fill(image, AIR_COLOR);
        int[] depth = new int[width * height];
        java.util.Arrays.fill(depth, Integer.MIN_VALUE);
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                for (int x = 0; x < grid.xSamples(); x++) {
                    if (!occupancy.isSolidAt(x, y, z) || !isBoundary(occupancy, x, y, z)) {
                        continue;
                    }
                    int screenX = 2 * (x - z + grid.zSamples() - 1) + 16;
                    int screenY = x + z + 3 * (grid.ySamples() - 1 - y) + 16;
                    int sampleDepth = x + z + 2 * y;
                    int gray = 55 + (165 * y) / Math.max(1, grid.ySamples() - 1);
                    int color = rgb(gray, Math.min(255, gray + 7), Math.min(255, gray + 12));
                    paintProjected(image, depth, screenX, screenY, sampleDepth, color);
                }
            }
        }
        return image;
    }

    private static boolean solidColumn(OccupancyVolumeGrid occupancy, int x, int z) {
        for (int y = 0; y < occupancy.specification().ySamples(); y++) {
            if (occupancy.isSolidAt(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoundary(OccupancyVolumeGrid occupancy, int x, int y, int z) {
        VolumeGridSpec grid = occupancy.specification();
        return x == 0
                || !occupancy.isSolidAt(x - 1, y, z)
                || x + 1 == grid.xSamples()
                || !occupancy.isSolidAt(x + 1, y, z)
                || y == 0
                || !occupancy.isSolidAt(x, y - 1, z)
                || y + 1 == grid.ySamples()
                || !occupancy.isSolidAt(x, y + 1, z)
                || z == 0
                || !occupancy.isSolidAt(x, y, z - 1)
                || z + 1 == grid.zSamples()
                || !occupancy.isSolidAt(x, y, z + 1);
    }

    private static void paintProjected(
            BufferedImage image,
            int[] depth,
            int centerX,
            int centerY,
            int sampleDepth,
            int color) {
        for (int offsetY = -2; offsetY <= 2; offsetY++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                int x = centerX + offsetX;
                int y = centerY + offsetY;
                if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
                    continue;
                }
                int index = y * image.getWidth() + x;
                if (sampleDepth >= depth[index]) {
                    depth[index] = sampleDepth;
                    image.setRGB(x, y, color);
                }
            }
        }
    }

    private static int nearestVerticalIndex(VolumeSlice slice, double coordinate) {
        int best = 0;
        double distance = Math.abs(slice.verticalCoordinateAt(0) - coordinate);
        for (int index = 1; index < slice.height(); index++) {
            double candidate = Math.abs(slice.verticalCoordinateAt(index) - coordinate);
            if (candidate < distance) {
                best = index;
                distance = candidate;
            }
        }
        return best;
    }

    private static void fill(BufferedImage image, int color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color);
            }
        }
    }

    private static void fillBlock(
            BufferedImage image, int startX, int startY, int size, int color) {
        for (int y = startY; y < startY + size; y++) {
            for (int x = startX; x < startX + size; x++) {
                image.setRGB(x, y, color);
            }
        }
    }

    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static double normalize(double value, double minimum, double maximum) {
        return maximum > minimum ? clamp01((value - minimum) / (maximum - minimum)) : 0.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static LinkedHashMap<String, String> hashArtifacts(Map<String, Path> artifacts)
            throws IOException {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            result.put(artifact.getKey(), sha256(artifact.getValue()));
        }
        return result;
    }

    private static String checksumListing(Map<String, String> hashes) {
        StringBuilder listing = new StringBuilder();
        hashes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> listing
                        .append(entry.getValue())
                        .append("  ")
                        .append(entry.getKey())
                        .append('\n'));
        return listing.toString();
    }

    private static String descriptorJson(SkyIslandVolumeDescriptor descriptor) {
        StringBuilder json = new StringBuilder();
        appendDescriptor(json, descriptor);
        return json.append('\n').toString();
    }

    private static String provenanceJson(Map<String, List<NodeId>> provenance) {
        StringBuilder json = new StringBuilder("{");
        int controlIndex = 0;
        for (Map.Entry<String, List<NodeId>> entry : provenance.entrySet()) {
            if (controlIndex++ != 0) {
                json.append(',');
            }
            appendJsonString(json, entry.getKey());
            json.append(":");
            json.append('[');
            for (int nodeIndex = 0; nodeIndex < entry.getValue().size(); nodeIndex++) {
                if (nodeIndex != 0) {
                    json.append(',');
                }
                appendJsonString(json, entry.getValue().get(nodeIndex).value());
            }
            json.append(']');
        }
        return json.append("}\n").toString();
    }

    private static String manifestJson(
            SuspendedVolumeEvidence evidence,
            String engineVersion,
            Map<String, String> hashes) {
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":").append(MANIFEST_SCHEMA_VERSION);
        json.append(",\"engineVersion\":");
        appendJsonString(json, engineVersion);
        json.append(",\"evidenceId\":\"signal-free-suspended-volume-v1\"");
        json.append(",\"descriptor\":");
        appendDescriptor(json, evidence.compiledVolume().descriptor());
        json.append(",\"recipeVersion\":").append(evidence.compiledVolume().recipeVersion());
        json.append(",\"graphSchemaVersion\":")
                .append(evidence.compiledVolume().graphSchemaVersion());
        appendSampling(json, evidence.density().specification());
        appendMetrics(json, evidence.metrics());
        json.append(",\"canonicalChecksums\":{");
        json.append("\"densityVolume\":\"").append(evidence.density().sha256()).append('"');
        json.append(",\"occupancyVolume\":\"")
                .append(evidence.occupancy().sha256()).append('"');
        json.append(",\"upperSurfaceGrid\":\"")
                .append(evidence.upperSurface().sha256()).append('"');
        json.append(",\"undersideSurfaceGrid\":\"")
                .append(evidence.undersideSurface().sha256()).append('"');
        json.append(",\"suspensionDensityGrid\":\"")
                .append(evidence.suspensionDensity().sha256()).append("\"}");
        json.append(",\"artifacts\":[");
        int artifactIndex = 0;
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            if (artifactIndex++ != 0) {
                json.append(',');
            }
            json.append("{\"path\":");
            appendJsonString(json, entry.getKey());
            json.append(",\"sha256\":\"").append(entry.getValue()).append("\"}");
        }
        return json.append("]}\n").toString();
    }

    private static void appendDescriptor(
            StringBuilder json, SkyIslandVolumeDescriptor descriptor) {
        json.append("{\"schemaVersion\":").append(descriptor.schemaVersion());
        json.append(",\"seed\":\"0x")
                .append(HexFormat.of().toHexDigits(descriptor.seed())).append('"');
        appendHexMember(json, "centerX", descriptor.centerX());
        appendHexMember(json, "centerZ", descriptor.centerZ());
        appendHexMember(json, "suspensionElevation", descriptor.suspensionElevation());
        appendHexMember(json, "nominalRadius", descriptor.nominalRadius());
        appendHexMember(json, "upperElevation", descriptor.upperElevation());
        appendHexMember(json, "undersideDepth", descriptor.undersideDepth());
        appendHexMember(json, "coastalFalloff", descriptor.coastalFalloff());
        appendHexMember(json, "ridgeAzimuth", descriptor.ridgeAzimuth());
        appendHexMember(json, "ridgeStrength", descriptor.ridgeStrength());
        appendHexMember(json, "undersideTaper", descriptor.undersideTaper());
        appendHexMember(json, "undersideAsymmetry", descriptor.undersideAsymmetry());
        appendHexMember(json, "signalAmplitude", descriptor.signalAmplitude());
        appendHexMember(json, "signalScale", descriptor.signalScale());
        json.append('}');
    }

    private static void appendSampling(StringBuilder json, VolumeGridSpec grid) {
        json.append(",\"sampling\":{");
        json.append("\"densityBinarySchemaVersion\":")
                .append(ScalarVolumeGrid.BINARY_SCHEMA_VERSION);
        json.append(",\"occupancyBinarySchemaVersion\":")
                .append(OccupancyVolumeGrid.BINARY_SCHEMA_VERSION);
        json.append(",\"layout\":\"x-fastest-then-z-then-y\"");
        json.append(",\"bounds\":{");
        json.append("\"minimumX\":\"").append(Double.toHexString(grid.minimumX())).append('"');
        appendHexMember(json, "maximumX", grid.maximumX());
        appendHexMember(json, "minimumY", grid.minimumY());
        appendHexMember(json, "maximumY", grid.maximumY());
        appendHexMember(json, "minimumZ", grid.minimumZ());
        appendHexMember(json, "maximumZ", grid.maximumZ());
        json.append("},\"xSamples\":").append(grid.xSamples());
        json.append(",\"ySamples\":").append(grid.ySamples());
        json.append(",\"zSamples\":").append(grid.zSamples());
        appendHexMember(json, "spacingX", grid.spacingX());
        appendHexMember(json, "spacingY", grid.spacingY());
        appendHexMember(json, "spacingZ", grid.spacingZ());
        json.append('}');
    }

    private static void appendMetrics(StringBuilder json, VolumeMetrics metrics) {
        json.append(",\"morphology\":{");
        json.append("\"solidSampleCount\":").append(metrics.solidSampleCount());
        json.append(",\"connectedSolidComponents\":")
                .append(metrics.connectedSolidComponents());
        appendHexMember(json, "estimatedSolidVolume", metrics.estimatedSolidVolume());
        appendHexMember(json, "solidCentroidX", metrics.solidCentroidX());
        appendHexMember(json, "solidCentroidY", metrics.solidCentroidY());
        appendHexMember(json, "solidCentroidZ", metrics.solidCentroidZ());
        VolumeMetrics.Bounds bounds = metrics.bounds();
        json.append(",\"solidBounds\":{");
        json.append("\"minimumX\":\"").append(Double.toHexString(bounds.minimumX())).append('"');
        appendHexMember(json, "maximumX", bounds.maximumX());
        appendHexMember(json, "minimumY", bounds.minimumY());
        appendHexMember(json, "maximumY", bounds.maximumY());
        appendHexMember(json, "minimumZ", bounds.minimumZ());
        appendHexMember(json, "maximumZ", bounds.maximumZ());
        json.append('}');
        VolumeMetrics.FaceContacts contacts = metrics.faceContacts();
        json.append(",\"faceContacts\":{");
        appendIntMembers(json, contacts.minimumX(), contacts.maximumX(), contacts.minimumY(),
                contacts.maximumY(), contacts.minimumZ(), contacts.maximumZ());
        json.append(",\"total\":").append(contacts.total()).append('}');
        VolumeMetrics.AirClearance clearance = metrics.airClearance();
        json.append(",\"airClearance\":{");
        json.append("\"minimumX\":\"").append(Double.toHexString(clearance.minimumX())).append('"');
        appendHexMember(json, "maximumX", clearance.maximumX());
        appendHexMember(json, "minimumY", clearance.minimumY());
        appendHexMember(json, "maximumY", clearance.maximumY());
        appendHexMember(json, "minimumZ", clearance.minimumZ());
        appendHexMember(json, "maximumZ", clearance.maximumZ());
        appendHexMember(json, "minimum", clearance.minimum());
        json.append("}}");
    }

    private static void appendIntMembers(
            StringBuilder json,
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ) {
        json.append("\"minimumX\":").append(minimumX);
        json.append(",\"maximumX\":").append(maximumX);
        json.append(",\"minimumY\":").append(minimumY);
        json.append(",\"maximumY\":").append(maximumY);
        json.append(",\"minimumZ\":").append(minimumZ);
        json.append(",\"maximumZ\":").append(maximumZ);
    }

    private static void appendHexMember(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":\"")
                .append(Double.toHexString(value)).append('"');
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

    private static String galleryHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Skyforge suspended-volume evidence</title>
                <style>body{font:16px system-ui;margin:2rem;max-width:1100px;background:#f4f0e6;color:#30343b}h1{margin-bottom:.25rem}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.25rem}.card{background:white;padding:1rem;border-radius:10px;box-shadow:0 2px 8px #0002}.card img{width:100%;image-rendering:auto}.note{color:#654}</style>
                </head><body><h1>Signal-free suspended sky-island evidence</h1>
                <p class="note">Review images explain the canonical grids; hashes and numerical metrics remain authoritative.</p>
                <div class="grid">
                <section class="card"><h2>Upper surface</h2><img src="upper-surface.png" alt="Top-down upper-surface elevation"></section>
                <section class="card"><h2>Underside depth</h2><img src="underside.png" alt="Top-down underside depth"></section>
                <section class="card"><h2>Suspension plane</h2><img src="suspension-occupancy.png" alt="Occupancy at the suspension elevation"></section>
                <section class="card"><h2>East-west slice</h2><img src="east-west.png" alt="East-west vertical occupancy slice"></section>
                <section class="card"><h2>North-south slice</h2><img src="north-south.png" alt="North-south vertical occupancy slice"></section>
                <section class="card"><h2>Isometric occupancy</h2><img src="isometric.png" alt="Isometric projection of boundary occupancy"></section>
                </div></body></html>
                """;
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
    }
}
