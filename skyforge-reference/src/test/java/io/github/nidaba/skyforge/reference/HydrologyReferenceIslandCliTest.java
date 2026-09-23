package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyReferenceIslandCliTest {
    @Test
    void fixedReferencePairExercisesRequiredHydrologyBeforeVisualIteration() throws Exception {
        Path out = Path.of("build", "evidence", "hydrology-reference-island-smoke");
        var result = HydrologyReferenceIslandCli.generate(out);
        var primary = result.primary();
        var confluence = result.confluenceControl();

        assertEquals(HydrologyReferenceIslandCli.PRIMARY_ISLAND_KEY, primary.islandKey());
        assertTrue(primary.reaches() > 0, result.summary());
        assertTrue(primary.retainedWater() >= 1);
        assertTrue(primary.interiorDrops() >= 1);
        assertTrue(primary.edgeFalls() >= 1);

        assertTrue(confluence.reaches() > 0);
        assertTrue(confluence.maxStreamOrder() >= 1);
        assertTrue(confluence.confluences() >= 1, result.summary());

        assertEquals(0.0, primary.endpointUphillReachFraction(), 1.0e-12, result.summary());
        assertTrue(primary.uphillStepFraction() <= 0.05, result.summary());
        assertTrue(primary.ridgeCrossingFraction() <= 0.25, result.summary());
        assertTrue(primary.meanValleyAdvantage() >= 0.0, result.summary());

        assertEquals(0.0, confluence.endpointUphillReachFraction(), 1.0e-12, result.summary());
        assertTrue(confluence.uphillStepFraction() <= 0.02, result.summary());
        assertTrue(confluence.ridgeCrossingFraction() <= 0.40, result.summary());

        assertTrue(Files.exists(out.resolve("diagnostics.csv")));
        assertTrue(Files.readString(out.resolve("diagnostics.csv")).lines().count() == 3);
        assertTrue(Files.readString(out.resolve("summary.txt")).contains("primary=287"));
    }
}
