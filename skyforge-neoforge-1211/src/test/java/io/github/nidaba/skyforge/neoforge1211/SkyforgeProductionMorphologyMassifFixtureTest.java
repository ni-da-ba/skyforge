package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionMorphologyMassifFixtureTest {
    @Test
    void firstMinecraftMorphologyCarrierPreservesExactAuth0083SmallMassifIntent() {
        var fixture = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.fixture();
        var source = fixture.sourceDescriptor();
        var translated = fixture.translatedDescriptor();

        assertEquals("builtin-massif-small-seed-skyforge", fixture.memberId());
        assertEquals(0x534b59464f524745L, source.seed());
        assertEquals("skyforge:massif", fixture.morphologyIdentifier());
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

        assertTrue(fixture.exactSupport().certificateKind().contains("massif"));
    }

    @Test
    void tightIntegerSupportFitsMinecraftAndBoundedAcceptanceHarness() {
        var fixture = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.fixture();
        var source = fixture.sourceSupport().bounds();
        var translated = fixture.exactSupport().bounds();

        assertEquals(96.0, translated.minimumY());
        assertTrue(translated.maximumY() < 320.0);
        assertEquals(source.minimumX(), translated.minimumX());
        assertEquals(source.maximumX(), translated.maximumX());
        assertEquals(source.minimumZ(), translated.minimumZ());
        assertEquals(source.maximumZ(), translated.maximumZ());
        assertEquals(source.minimumY() + fixture.verticalTranslation(), translated.minimumY());
        assertEquals(source.maximumY() + fixture.verticalTranslation(), translated.maximumY());

        var keys = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.footprintChunkKeys(translated);
        assertTrue(keys.size() > 0);
        for (long key : keys) {
            assertTrue(Math.abs(ChunkPos.getX(key)) <= 12);
            assertTrue(Math.abs(ChunkPos.getZ(key)) <= 12);
        }
    }
}
