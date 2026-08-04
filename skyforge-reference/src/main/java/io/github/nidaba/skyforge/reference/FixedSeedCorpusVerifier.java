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
import java.util.Map;
import java.util.Objects;

/** Verifies canonical fixed-seed corpus artifacts against the checked-in golden hashes. */
public final class FixedSeedCorpusVerifier {
    /** Classpath location of the accepted v1 corpus checksums. */
    public static final String GOLDEN_RESOURCE = "/io/github/nidaba/skyforge/reference/fixed-seed-island-v1.sha256";

    private FixedSeedCorpusVerifier() {}

    /** Verifies every accepted artifact and returns the number of checked paths. */
    public static int verify(Path corpusDirectory) throws IOException {
        Objects.requireNonNull(corpusDirectory, "corpusDirectory");
        Map<String, String> expected = readGoldenResource();
        Map<String, String> actual = checksums(corpusDirectory);
        if (!expected.keySet().equals(actual.keySet())) {
            throw new IOException("fixed-seed corpus path set differs from the accepted golden corpus");
        }
        for (Map.Entry<String, String> artifact : expected.entrySet()) {
            String actualHash = actual.get(artifact.getKey());
            if (!artifact.getValue().equals(actualHash)) {
                throw new IOException("fixed-seed corpus drift: " + artifact.getKey()
                        + " expected " + artifact.getValue() + " but found " + actualHash);
            }
        }
        return expected.size();
    }

    /** Writes the canonical checksum listing used while accepting a new versioned corpus. */
    public static Path writeChecksums(Path corpusDirectory, Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> artifact : checksums(corpusDirectory).entrySet()) {
            content.append(artifact.getValue()).append("  ").append(artifact.getKey()).append('\n');
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    private static Map<String, String> checksums(Path corpusDirectory) throws IOException {
        LinkedHashMap<String, String> checksums = new LinkedHashMap<>();
        checksums.put("corpus-manifest.json", sha256(corpusDirectory.resolve("corpus-manifest.json")));
        for (FixedSeedReferenceCorpus.Member member : FixedSeedReferenceCorpus.members()) {
            for (String artifact : FixedSeedCorpusGenerator.NORMATIVE_MEMBER_ARTIFACTS) {
                String path = member.id() + "/" + artifact;
                checksums.put(path, sha256(corpusDirectory.resolve(path)));
            }
        }
        return checksums;
    }

    private static Map<String, String> readGoldenResource() throws IOException {
        InputStream stream = FixedSeedCorpusVerifier.class.getResourceAsStream(GOLDEN_RESOURCE);
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
            throw new IOException("missing fixed-seed corpus artifact: " + path);
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
