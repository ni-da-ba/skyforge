package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionMorphologyAtlasFixtureTest {
    @Test
    void remainingBuiltInsPreserveExactAuth0083SmallSeedSkyforgeIntent() {
        Set<String> ids = new HashSet<>();
        Set<Object> volumeIds = new HashSet<>();

        for (var member : SkyforgeProductionMorphologyAtlasFixture.Member.values()) {
            var fixture = SkyforgeProductionMorphologyAtlasFixture.fixture(member);
            var source = fixture.sourceDescriptor();
            var translated = fixture.translatedDescriptor();

            assertTrue(ids.add(member.id()));
            assertTrue(volumeIds.add(fixture.volumeId()));
            assertEquals("builtin-" + member.commandId() + "-small-seed-skyforge", member.id());
            assertEquals("skyforge:" + member.commandId(), fixture.morphologyIdentifier());

            assertEquals(SkyforgeProductionMorphologyAtlasFixture.GEOMETRY_SEED, source.seed());
            assertEquals(512.0, source.suspensionElevation());
            assertEquals(160.0, source.nominalRadius());
            assertEquals(60.0, source.upperElevation());
            assertEquals(80.0, source.undersideDepth());
            assertEquals(40.0, source.coastalFalloff());
            assertEquals(Math.PI / 6.0, source.ridgeAzimuth());
            assertEquals(0.65, source.ridgeStrength());
            assertEquals(0.60, source.undersideTaper());
            assertEquals(0.25, source.undersideAsymmetry());
            assertEquals(0.0, source.signalAmplitude());
            assertEquals(20.0, source.signalScale());

            assertEquals(source.seed(), translated.seed());
            assertEquals(source.centerX(), translated.centerX());
            assertEquals(source.centerZ(), translated.centerZ());
            assertEquals(source.nominalRadius(), translated.nominalRadius());
            assertEquals(source.upperElevation(), translated.upperElevation());
            assertEquals(source.undersideDepth(), translated.undersideDepth());
            assertEquals(source.coastalFalloff(), translated.coastalFalloff());
            assertEquals(source.ridgeAzimuth(), translated.ridgeAzimuth());
            assertEquals(source.ridgeStrength(), translated.ridgeStrength());
            assertEquals(source.undersideTaper(), translated.undersideTaper());
            assertEquals(source.undersideAsymmetry(), translated.undersideAsymmetry());
            assertEquals(source.signalAmplitude(), translated.signalAmplitude());
            assertEquals(source.signalScale(), translated.signalScale());
            assertEquals(
                    source.suspensionElevation() + fixture.verticalTranslation(),
                    translated.suspensionElevation());
        }
    }

    @Test
    void exactSupportsFitMinecraftWithoutClippingAndUseFiniteExplicitFootprints() {
        int largestFootprint = 0;
        for (var member : SkyforgeProductionMorphologyAtlasFixture.Member.values()) {
            var fixture = SkyforgeProductionMorphologyAtlasFixture.fixture(member);
            var source = fixture.sourceSupport().bounds();
            var translated = fixture.exactSupport().bounds();

            assertEquals(96.0, translated.minimumY(), member.id());
            assertTrue(translated.maximumY() < 320.0, member.id());
            assertEquals(source.minimumX(), translated.minimumX(), member.id());
            assertEquals(source.maximumX(), translated.maximumX(), member.id());
            assertEquals(source.minimumZ(), translated.minimumZ(), member.id());
            assertEquals(source.maximumZ(), translated.maximumZ(), member.id());
            assertEquals(
                    source.minimumY() + fixture.verticalTranslation(),
                    translated.minimumY(),
                    member.id());
            assertEquals(
                    source.maximumY() + fixture.verticalTranslation(),
                    translated.maximumY(),
                    member.id());

            assertTrue(fixture.exactSupport().occupiedColumns() > 0, member.id());
            assertTrue(
                    fixture.exactSupport().scannedColumns()
                            > fixture.exactSupport().occupiedColumns(),
                    member.id());
            assertTrue(!fixture.footprintChunkKeys().isEmpty(), member.id());
            assertEquals(
                    SkyforgeProductionMorphologyAtlasFixture.footprintChunkKeys(translated),
                    fixture.footprintChunkKeys(),
                    member.id());

            largestFootprint = Math.max(largestFootprint, fixture.footprintChunkKeys().size());
            System.out.println(
                    "SF-IMP-0082 FIXTURE " + member.id()
                            + " bounds=" + translated
                            + " footprintChunks=" + fixture.footprintChunkKeys().size()
                            + " occupiedColumns=" + fixture.exactSupport().occupiedColumns()
                            + " scannedColumns=" + fixture.exactSupport().scannedColumns());
        }
        assertTrue(largestFootprint > 0);
    }

    @Test
    void atlasDoesNotCollapseDistinctFamiliesOntoOneSupport() {
        var tableland = SkyforgeProductionMorphologyAtlasFixture.fixture(
                SkyforgeProductionMorphologyAtlasFixture.Member.TABLELAND);
        var spine = SkyforgeProductionMorphologyAtlasFixture.fixture(
                SkyforgeProductionMorphologyAtlasFixture.Member.SPINE);
        var basin = SkyforgeProductionMorphologyAtlasFixture.fixture(
                SkyforgeProductionMorphologyAtlasFixture.Member.BASIN);
        var lobed = SkyforgeProductionMorphologyAtlasFixture.fixture(
                SkyforgeProductionMorphologyAtlasFixture.Member.LOBED);

        Set<String> supportSignatures = new HashSet<>();
        supportSignatures.add(tableland.exactSupport().bounds().toString());
        supportSignatures.add(spine.exactSupport().bounds().toString());
        supportSignatures.add(basin.exactSupport().bounds().toString());
        supportSignatures.add(lobed.exactSupport().bounds().toString());
        assertTrue(supportSignatures.size() >= 2);
        assertNotEquals(tableland.morphologyIdentifier(), spine.morphologyIdentifier());
        assertNotEquals(basin.morphologyIdentifier(), lobed.morphologyIdentifier());
    }
}
