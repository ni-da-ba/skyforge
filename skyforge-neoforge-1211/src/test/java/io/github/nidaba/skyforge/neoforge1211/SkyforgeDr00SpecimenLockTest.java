package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import org.junit.jupiter.api.Test;

final class SkyforgeDr00SpecimenLockTest {
    @Test
    void lockedProductionFixtureRegeneratesWithItsExactIdentityAndBounds() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var descriptor = fixture.descriptor();
        var volume = fixture.volume();
        var identity = SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 1471L);
        var regeneratedDescriptor = SkyIslandDescriptorGenerator.derive(identity);
        var physical = volume.compiledVolume().descriptor();
        var bounds = volume.bounds();

        assertAll(
                () -> assertEquals(1471L, fixture.islandKey()),
                () -> assertEquals(identity, descriptor.identity()),
                () -> assertEquals(regeneratedDescriptor, descriptor),
                () -> assertEquals(389656995481184958L, descriptor.authorshipSeed()),
                () -> assertEquals(SkyIslandMorphologyFamily.MASSIF, descriptor.morphologyFamily()),
                () -> assertEquals(96.23348220259993, descriptor.nominalRadius()),
                () -> assertEquals(1, fixture.field().exposureGeometry().connectionCount()),
                () -> assertEquals(0x534B59464F524745L, volume.id().archipelagoRootSeed()),
                () -> assertEquals("sf-imp-0068-production-composed-cave", volume.id().groupIdentifier()),
                () -> assertEquals(0, volume.id().groupOrdinal()),
                () -> assertEquals(0, volume.id().memberOrdinal()),
                () -> assertEquals(680068L, volume.id().geometrySeed()),
                () -> assertEquals(
                        "6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068",
                        volume.id().path()),
                () -> assertEquals(2, physical.schemaVersion()),
                () -> assertEquals(680068L, physical.seed()),
                () -> assertEquals(0.0, physical.centerX()),
                () -> assertEquals(0.0, physical.centerZ()),
                () -> assertEquals(220.0, physical.suspensionElevation()),
                () -> assertEquals(58.0, physical.upperElevation()),
                () -> assertEquals(82.0, physical.undersideDepth()),
                () -> assertEquals(17.322026796467988, physical.coastalFalloff()),
                () -> assertEquals(0.0, physical.ridgeAzimuth()),
                () -> assertEquals(0.24, physical.ridgeStrength()),
                () -> assertEquals(0.62, physical.undersideTaper()),
                () -> assertEquals(0.0, physical.undersideAsymmetry()),
                () -> assertEquals(0.10, physical.detailAmplitude()),
                () -> assertEquals(28.0, physical.detailScale()),
                () -> assertEquals(0.18, physical.secondaryMorphologyAmplitude()),
                () -> assertEquals(SkyIslandMorphologyFamily.MASSIF, physical.morphologyFamily()),
                () -> assertEquals(-103.93216077880793, bounds.minimumX()),
                () -> assertEquals(103.93216077880793, bounds.maximumX()),
                () -> assertEquals(110.0, bounds.minimumY()),
                () -> assertEquals(300.0, bounds.maximumY()),
                () -> assertEquals(-103.93216077880793, bounds.minimumZ()),
                () -> assertEquals(103.93216077880793, bounds.maximumZ()));
    }
}
