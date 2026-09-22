package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Dr70IslandPopulationAuditCliTest {
    @Test
    void emitsBoundedDeterministicSmokeAudit() throws Exception {
        Path out = Path.of("build", "evidence", "dr70-island-population-audit-smoke");
        var result = Dr70IslandPopulationAuditCli.generate(out, 64, 20);

        assertEquals(64, result.populationCount());
        assertEquals(20, result.reviewCount());
        assertEquals(20, result.reviewSelections().stream()
                .map(selection -> selection.row().islandKey())
                .distinct()
                .count());
        assertTrue(Files.readString(out.resolve("population.csv")).lines().count() == 65);
        assertTrue(Files.readString(out.resolve("review-corpus.csv")).lines().count() == 21);
        assertTrue(Files.readString(out.resolve("summary.txt")).contains("population=64"));
    }
}
