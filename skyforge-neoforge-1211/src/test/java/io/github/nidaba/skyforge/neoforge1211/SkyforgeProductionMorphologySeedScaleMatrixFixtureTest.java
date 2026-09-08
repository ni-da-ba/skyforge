package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionMorphologySeedScaleMatrixFixtureTest {
    @Test
    void definesExactlyTheTwentyRemainingAuth0083BuiltInsWithoutInventingSeedsOrScales() {
        var members = SkyforgeProductionMorphologySeedScaleMatrixFixture.members();
        assertEquals(20, members.size());

        Set<String> ids = new HashSet<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            var familyMembers = SkyforgeProductionMorphologySeedScaleMatrixFixture.members(family);
            assertEquals(4, familyMembers.size());

            assertEquals("builtin-" + family.identifier() + "-medium-seed-min", familyMembers.get(0).id());
            assertEquals("builtin-" + family.identifier() + "-medium-seed-zero", familyMembers.get(1).id());
            assertEquals("builtin-" + family.identifier() + "-medium-seed-skyforge", familyMembers.get(2).id());
            assertEquals("builtin-" + family.identifier() + "-large-seed-skyforge", familyMembers.get(3).id());

            assertEquals(Long.MIN_VALUE, familyMembers.get(0).seed());
            assertEquals(0L, familyMembers.get(1).seed());
            assertEquals(SkyforgeProductionMorphologySeedScaleMatrixFixture.SKYFORGE_SEED, familyMembers.get(2).seed());
            assertEquals(SkyforgeProductionMorphologySeedScaleMatrixFixture.SKYFORGE_SEED, familyMembers.get(3).seed());

            for (var member : familyMembers) {
                assertTrue(ids.add(member.id()));
                var descriptor = SkyforgeProductionMorphologySeedScaleMatrixFixture.sourceDescriptor(member);
                double scale = member.scale() == SkyforgeProductionMorphologySeedScaleMatrixFixture.Scale.MEDIUM
                        ? 1.0
                        : 1.5;
                assertEquals(member.seed(), descriptor.seed());
                assertEquals(512.0, descriptor.suspensionElevation());
                assertEquals(256.0 * scale, descriptor.nominalRadius());
                assertEquals(96.0 * scale, descriptor.upperElevation());
                assertEquals(128.0 * scale, descriptor.undersideDepth());
                assertEquals(64.0 * scale, descriptor.coastalFalloff());
                assertEquals(Math.PI / 6.0, descriptor.ridgeAzimuth());
                assertEquals(0.65, descriptor.ridgeStrength());
                assertEquals(0.60, descriptor.undersideTaper());
                assertEquals(0.25, descriptor.undersideAsymmetry());
                assertEquals(0.0, descriptor.signalAmplitude());
                assertEquals(32.0 * scale, descriptor.signalScale());
            }
        }
        assertEquals(20, ids.size());
    }

    @Test
    void everyMemberFitsTheShortSingleVolumeReviewCarrierWithExactOccupiedChunkWarmup() {
        boolean observedStrictReduction = false;
        for (var member : SkyforgeProductionMorphologySeedScaleMatrixFixture.members()) {
            var fixture = SkyforgeProductionMorphologySeedScaleMatrixFixture.buildMember(
                    member.family(), member.commandId());
            assertEquals(1, fixture.members().size());
            assertEquals(member.id(), fixture.member().member().id());
            assertEquals(336, SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(
                    fixture.member().exactSupport().bounds().minimumY()));
            assertTrue(
                    fixture.member().exactSupport().bounds().maximumY() + 1.0
                            < SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MAX_Y_EXCLUSIVE);

            Set<Long> exact = fixture.member().exactSupport().occupiedChunkKeys();
            Set<Long> rectangular = SkyforgeProductionMorphologySeedScaleMatrixFixture.footprintChunkKeys(
                    fixture.member().exactSupport().bounds());
            assertEquals(exact, fixture.member().footprintChunkKeys());
            assertEquals(exact, fixture.footprintChunkKeys());
            assertTrue(rectangular.containsAll(exact));
            assertTrue(exact.size() <= rectangular.size());
            observedStrictReduction |= exact.size() < rectangular.size();
        }
        assertTrue(
                observedStrictReduction,
                "exact occupied-chunk warmup should eliminate at least one empty bounding-box chunk");
    }

    @Test
    void reviewDimensionIsDevelopmentOnlyCarrierDisjointFromVanillaNoiseTerrain() {
        assertEquals(320, SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MIN_Y);
        assertEquals(544, SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_HEIGHT);
        assertEquals(864, SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MAX_Y_EXCLUSIVE);
    }

    @Test
    void morphologyOnlySurfaceEvidenceDoesNotRequireNativeGrassPresentation() {
        var evidence =
                new SkyforgeNeoForge1211ProductionMorphologySeedScaleDevRuntime.SurfaceEvidence(
                        4,
                        4,
                        4,
                        4,
                        4,
                        0,
                        3,
                        0,
                        "none",
                        439,
                        548,
                        0x1234L);
        assertTrue(evidence.valid());
    }

    @Test
    void representativePolicyCoversAllFamiliesAllSeedCasesAndTwoLargeStressMembers() {
        Set<String> ids = SkyforgeProductionMorphologySeedScaleMatrixFixture.representativeMemberIds();
        assertEquals(7, ids.size());

        Set<MorphologyFamily> mediumFamilies = new HashSet<>();
        Set<Long> mediumSeeds = new HashSet<>();
        int largeCount = 0;
        Set<MorphologyFamily> largeFamilies = new HashSet<>();
        for (var member : SkyforgeProductionMorphologySeedScaleMatrixFixture.members()) {
            if (!ids.contains(member.id())) {
                continue;
            }
            if (member.scale() == SkyforgeProductionMorphologySeedScaleMatrixFixture.Scale.MEDIUM) {
                mediumFamilies.add(member.family());
                mediumSeeds.add(member.seed());
            } else {
                largeCount++;
                largeFamilies.add(member.family());
            }
        }

        assertEquals(Set.of(MorphologyFamily.values()), mediumFamilies);
        assertEquals(
                Set.of(Long.MIN_VALUE, 0L, SkyforgeProductionMorphologySeedScaleMatrixFixture.SKYFORGE_SEED),
                mediumSeeds);
        assertEquals(2, largeCount);
        assertEquals(Set.of(MorphologyFamily.MASSIF, MorphologyFamily.SPINE), largeFamilies);
        assertTrue(ids.contains("builtin-tableland-medium-seed-skyforge"));
        assertTrue(ids.contains("builtin-massif-medium-seed-skyforge"));
    }
}
