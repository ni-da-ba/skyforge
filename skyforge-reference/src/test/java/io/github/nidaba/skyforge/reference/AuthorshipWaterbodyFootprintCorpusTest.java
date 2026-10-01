package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorshipWaterbodyFootprintCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-waterbody-footprints-v1");
        AuthorshipWaterbodyFootprintCorpusCli.main(new String[] {output.toString()});
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("footprints.csv")));

        List<String> manifest = Files.readAllLines(output.resolve("manifest.csv"));
        List<String> footprints = Files.readAllLines(output.resolve("footprints.csv"));
        assertTrue(manifest.size() >= 7);
        assertEquals(2, footprints.size());

        String key83 = manifest.stream()
                .filter(line -> line.startsWith("83,"))
                .findFirst()
                .orElseThrow();
        String[] columns = key83.split(",");
        assertEquals("1", columns[2]);
        assertEquals("2", columns[3]);

        String[] footprintColumns = footprints.get(1).split(",");
        assertEquals("83", footprintColumns[0]);
        assertEquals("2", footprintColumns[3]);
    }
}
