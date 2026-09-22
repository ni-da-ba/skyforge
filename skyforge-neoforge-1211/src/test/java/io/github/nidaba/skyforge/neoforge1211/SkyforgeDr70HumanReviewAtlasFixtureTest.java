package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyforgeDr70HumanReviewAtlasFixtureTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("skyforge.test.projectDirectory", "."));

    @Test
    void runtimeMembersExactlyMatchFrozenHumanReviewCorpus() throws Exception {
        Path corpus = PROJECT.getParent().resolve("docs/reviews/DR70_HUMAN_REVIEW_CORPUS.csv");
        List<String> rows = Files.readAllLines(corpus).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(101, rows.size());
        assertEquals("selectionBucket,islandKey", rows.getFirst());

        for (int index = 1; index <= 100; index++) {
            String[] columns = rows.get(index).split(",", -1);
            var member = SkyforgeDr70HumanReviewAtlasFixture.member(index);
            assertEquals(columns[0], member.selectionBucket());
            assertEquals(Long.parseLong(columns[1]), member.islandKey());
            assertEquals(index, member.reviewIndex());
        }
    }

    @Test
    void reviewOrderIsCompleteUniqueAndNondecreasingByRadius() {
        List<Integer> order = SkyforgeDr70HumanReviewAtlasFixture.reviewOrder();
        assertEquals(100, order.size());
        assertEquals(100, order.stream().distinct().count());

        double previousRadius = Double.NEGATIVE_INFINITY;
        for (int index : order) {
            double radius = SkyforgeDr70HumanReviewAtlasFixture.member(index).descriptor().nominalRadius();
            assertTrue(radius >= previousRadius);
            previousRadius = radius;
        }
    }

    @Test
    void reviewPresetIsNeutralVoidCarrier() throws Exception {
        String json = Files.readString(PROJECT.resolve(
                "src/development/resources/data/skyforge/worldgen/world_preset/dr70_review_atlas.json"));
        assertTrue(json.contains("\"type\": \"skyforge:noise_overlay\""));
        assertTrue(json.contains("\"biome\": \"minecraft:the_void\""));
        assertTrue(json.contains("\"settings\": \"skyforge:dr70_review_void\""));

        String noise = Files.readString(PROJECT.resolve(
                "src/development/resources/data/skyforge/worldgen/noise_settings/dr70_review_void.json"));
        assertTrue(noise.contains("\"default_block\": {"));
        assertTrue(noise.contains("\"Name\": \"minecraft:air\""));
        assertTrue(noise.contains("\"final_density\": -1.0"));
    }
}
