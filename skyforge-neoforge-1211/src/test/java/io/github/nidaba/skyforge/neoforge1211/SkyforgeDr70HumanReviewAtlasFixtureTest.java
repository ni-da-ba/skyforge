package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.nio.file.Path;
import java.util.EnumSet;
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
    void frozenCorpusCoversEveryBuiltInMorphologyAndBroadSizeBand() {
        EnumSet<SkyIslandMorphologyFamily> morphologies =
                EnumSet.noneOf(SkyIslandMorphologyFamily.class);
        boolean small = false;
        boolean medium = false;
        boolean large = false;

        for (int index = 1; index <= 100; index++) {
            var descriptor = SkyforgeDr70HumanReviewAtlasFixture.member(index).descriptor();
            morphologies.add(descriptor.morphologyFamily());
            if (descriptor.nominalRadius() < 160.0) {
                small = true;
            } else if (descriptor.nominalRadius() < 320.0) {
                medium = true;
            } else {
                large = true;
            }
        }

        assertEquals(EnumSet.allOf(SkyIslandMorphologyFamily.class), morphologies);
        assertTrue(small, "DR-70 corpus must include compact baseline islands");
        assertTrue(medium, "DR-70 corpus must include middle-scale baseline islands");
        assertTrue(large, "DR-70 corpus must include large baseline islands");
    }

    @Test
    void reviewOrderIsCompleteUniqueAndFrontLoadsCrossScaleMorphologyCoverage() {
        List<Integer> order = SkyforgeDr70HumanReviewAtlasFixture.reviewOrder();
        assertEquals(100, order.size());
        assertEquals(100, order.stream().distinct().count());

        var firstWave = order.subList(0, 15).stream()
                .map(SkyforgeDr70HumanReviewAtlasFixture::member)
                .toList();
        var morphologySizeCells = firstWave.stream()
                .map(member -> member.descriptor().morphologyFamily().name()
                        + ":" + sizeBand(member.descriptor().nominalRadius()))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(15, morphologySizeCells.size(),
                "first 15 review specimens must cover every morphology x size cell");

        var earlyBuckets = order.subList(0, 25).stream()
                .map(SkyforgeDr70HumanReviewAtlasFixture::member)
                .map(SkyforgeDr70HumanReviewAtlasFixture.Member::selectionBucket)
                .collect(java.util.stream.Collectors.toSet());
        for (String bucket : List.of(
                "substantial-channel",
                "retained-water",
                "cave-sealed",
                "cave-upper",
                "cave-underside",
                "channel-and-cave",
                "channel-and-upper",
                "channel-and-underside",
                "channel-water-cave",
                "wet-large-relief-channel")) {
            assertTrue(earlyBuckets.contains(bucket), "early review wave must include " + bucket);
        }
    }

    private static int sizeBand(double nominalRadius) {
        if (nominalRadius < 160.0) {
            return 0;
        }
        if (nominalRadius < 320.0) {
            return 1;
        }
        return 2;
    }

    @Test
    void reviewPresetSeparatesVoidReviewCarrierFromNativeSurfaceDonor() throws Exception {
        String json = Files.readString(PROJECT.resolve(
                "src/development/resources/data/skyforge/worldgen/world_preset/dr70_review_atlas.json"));
        assertTrue(json.contains("\"minecraft:overworld\""));
        assertTrue(json.contains("\"biome\": \"minecraft:the_void\""));
        assertTrue(json.contains("\"settings\": \"skyforge:dr70_review_void\""));
        assertTrue(json.contains("\"skyforge:dr70_surface_donor\""));
        assertTrue(json.contains("\"preset\": \"minecraft:overworld\""));

        String noise = Files.readString(PROJECT.resolve(
                "src/development/resources/data/skyforge/worldgen/noise_settings/dr70_review_void.json"));
        assertTrue(noise.contains("\"default_block\": {"));
        assertTrue(noise.contains("\"Name\": \"minecraft:air\""));
        assertTrue(noise.contains("\"final_density\": -1.0"));
    }
}
