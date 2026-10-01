package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.reference.benchmark.ReferenceBenchmarkObservation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FixedSeedCorpusContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void goldenResourcePinsManifestAndEveryNormativeMemberArtifact() throws IOException {
        InputStream stream = FixedSeedCorpusContractTest.class.getResourceAsStream(
                FixedSeedCorpusVerifier.GOLDEN_RESOURCE);
        if (stream == null) {
            throw new IOException("missing golden resource");
        }
        Set<String> paths = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] parts = line.split("  ", 2);
                assertEquals(2, parts.length);
                assertTrue(parts[0].matches("[0-9a-f]{64}"));
                assertTrue(paths.add(parts[1]));
            }
        }
        assertEquals(
                1 + FixedSeedReferenceCorpus.members().size()
                        * FixedSeedCorpusGenerator.NORMATIVE_MEMBER_ARTIFACTS.size(),
                paths.size());
        assertTrue(paths.contains("corpus-manifest.json"));
        for (FixedSeedReferenceCorpus.Member member : FixedSeedReferenceCorpus.members()) {
            for (String artifact : FixedSeedCorpusGenerator.NORMATIVE_MEMBER_ARTIFACTS) {
                assertTrue(paths.contains(member.id() + "/" + artifact));
            }
        }
    }

    @Test
    void verifierFailsClosedWhenTheCorpusIsAbsent() {
        assertThrows(IOException.class, () -> FixedSeedCorpusVerifier.verify(temporaryDirectory));
    }

    @Test
    void benchmarkObservationIsPositiveButHasNoPerformanceThreshold() {
        ReferenceBenchmarkObservation observation =
                new ReferenceBenchmarkObservation("seed-one", 1L, 1_048_576, 9_000_000_000L, 116_508.4);
        assertEquals(9_000_000_000L, observation.wallTimeNanoseconds());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReferenceBenchmarkObservation("seed-one", 1L, 1, 0L, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReferenceBenchmarkObservation("seed-one", 1L, 1, 1L, Double.NaN));
    }
}
