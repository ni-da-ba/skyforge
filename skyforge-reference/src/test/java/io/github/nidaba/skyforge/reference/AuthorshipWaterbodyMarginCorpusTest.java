package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorshipWaterbodyMarginCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-waterbody-margins-v1");
        AuthorshipWaterbodyMarginCorpusCli.main(new String[] {output.toString()});
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("margins.csv")));

        List<String> manifest = Files.readAllLines(output.resolve("manifest.csv"));
        assertTrue(manifest.size() >= 7);
        String[] key83 = manifest.stream()
                .filter(line -> line.startsWith("83,"))
                .findFirst()
                .orElseThrow()
                .split(",");
        assertEquals("1", key83[2]);
        assertEquals("241", key83[3]);
        assertTrue(Integer.parseInt(key83[4]) > 0);
        assertTrue(Integer.parseInt(key83[5]) > 0);

        List<String> details = Files.readAllLines(output.resolve("margins.csv"));
        assertEquals(2, details.size());
        assertTrue(details.get(1).startsWith("83,"));
    }
}
