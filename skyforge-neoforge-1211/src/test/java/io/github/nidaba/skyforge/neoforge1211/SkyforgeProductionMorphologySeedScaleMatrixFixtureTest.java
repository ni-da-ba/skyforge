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

            assertEquals(
                    "builtin-" + family.identifier() + "-medium-seed-min",
                    familyMembers.get(0).id());
            assertEquals(
                    "builtin-" + family.identifier() + "-medium-seed-zero",
                    familyMembers.get(1).id());
            assertEquals(
                    "builtin-" + family.identifier() + "-medium-seed-skyforge",
                    familyMembers.get(2).id());
            assertEquals(
                    "builtin-" + family.identifier() + "-large-seed-skyforge",
                    familyMembers.get(3).id());

            assertEquals(Long.MIN_VALUE, familyMembers.get(0).seed());
            assertEquals(0L, familyMembers.get(1).seed());
            assertEquals(
                    SkyforgeProductionMorphologySeedScaleMatrixFixture.SKYFORGE_SEED,
                    familyMembers.get(2).seed());
            assertEquals(
                    SkyforgeProductionMorphologySeedScaleMatrixFixture.SKYFORGE_SEED,
                    familyMembers.get(3).seed());

            for (var member : familyMembers) {
                assertTrue(ids.add(member.id()));
                var descriptor =
                        SkyforgeProductionMorphologySeedScaleMatrixFixture.sourceDescriptor(member);
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
    void reviewDimensionIsDevelopmentOnlyTallSpaceWithVanillaGenerationBelowTheStack() {
        assertEquals(-64, SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MIN_Y);
        assertEquals(2048, SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_HEIGHT);
        assertEquals(
                1984,
                SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MAX_Y_EXCLUSIVE);
    }
}
