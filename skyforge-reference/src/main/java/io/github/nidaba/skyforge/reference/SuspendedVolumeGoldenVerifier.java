package io.github.nidaba.skyforge.reference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

/** Verifies the accepted signal-free suspended-volume specimen against pinned artifact hashes. */
public final class SuspendedVolumeGoldenVerifier {
    /** Classpath location of the accepted signal-free suspended-volume checksum set. */
    public static final String GOLDEN_RESOURCE =
            "/io/github/nidaba/skyforge/reference/signal-free-suspended-volume-v1.sha256";

    /** Artifacts whose bytes define the accepted specimen independently of engine-version metadata. */
    public static final List<String> NORMATIVE_ARTIFACTS = List.of(
            "density-graph.json",
            "density.volume",
            "descriptor.json",
            "east-west.csv",
            "east-west.png",
            "index.html",
            "isometric.png",
            "north-south.csv",
            "north-south.png",
            "occupancy.volume",
            "provenance.json",
            "suspension-density.grid",
            "suspension-occupancy.png",
            "underside-surface-graph.json",
            "underside-surface.grid",
            "underside.png",
            "upper-surface-graph.json",
            "upper-surface.grid",
            "upper-surface.png");

    private SuspendedVolumeGoldenVerifier() {}

    /** Verifies every normative artifact and returns the number of checked paths. */
    public static int verify(Path evidenceDirectory) throws IOException {
        Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
        Map<String, String> expected = readGoldenResource();
        if (expected.size() != NORMATIVE_ARTIFACTS.size()
                || !expected.keySet().containsAll(NORMATIVE_ARTIFACTS)) {
            throw new IOException("suspended-volume golden path set differs from the accepted contract");
        }
        for (String artifact : NORMATIVE_ARTIFACTS) {
            String expectedHash = expected.get(artifact);
            String actualHash = sha256(evidenceDirectory.resolve(artifact));
            if (!expectedHash.equals(actualHash)) {
                throw new IOException("suspended-volume specimen drift: " + artifact
                        + " expected " + expectedHash + " but found " + actualHash);
            }
        }
        return expected.size();
    }

    private static Map<String, String> readGoldenResource() throws IOException {
        InputStream stream = SuspendedVolumeGoldenVerifier.class.getResourceAsStream(GOLDEN_RESOURCE);
        if (stream == null) {
            throw new IOException("missing golden checksum resource: " + GOLDEN_RESOURCE);
        }
        LinkedHashMap<String, String> checksums = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("  ", 2);
                if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}")) {
                    throw new IOException("invalid golden checksum line: " + line);
                }
                if (checksums.put(parts[1], parts[0]) != null) {
                    throw new IOException("duplicate golden checksum path: " + parts[1]);
                }
            }
        }
        return checksums;
    }

    private static String sha256(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("missing suspended-volume artifact: " + path);
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
