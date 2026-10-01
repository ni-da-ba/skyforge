package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandAuthoredRealizationAssociationTest {
    private static final long AUTHORED_WORLD = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    @Test
    void explicitAssociationRetainsIndependentIdentityDomains() {
        SkyIslandAuthoredRealizationAssociation association =
                association(17L, 3, 1, 9001L);

        assertEquals(AUTHORED_WORLD, association.authoredIdentity().worldSeed());
        assertEquals(
                REALIZATION_ROOT,
                association.realizedVolumeId().archipelagoRootSeed());
        assertNotEquals(
                association.authoredIdentity().worldSeed(),
                association.realizedVolumeId().archipelagoRootSeed());
        assertEquals(
                association.authoredDescriptor().nominalRadius(),
                association.realizedVolume()
                        .compiledVolume()
                        .descriptor()
                        .nominalRadius(),
                0.0);
        assertTrue(association.canonicalToken().startsWith("sfassoc:v1:"));
    }

    @Test
    void associationRejectsDirectLocalScaleMismatch() {
        SkyIslandDescriptor authored = authored(23L);
        SkyIslandWorldVolume realized =
                realizedVolume(
                        authored,
                        4,
                        0,
                        9002L,
                        authored.nominalRadius() + 1.0,
                        authored.morphologyFamily(),
                        true);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandAuthoredRealizationAssociation.of(authored, realized));
    }

    @Test
    void associationRejectsDeclaredMorphologyMismatch() {
        SkyIslandDescriptor authored = authored(29L);
        SkyIslandMorphologyFamily other =
                authored.morphologyFamily() == SkyIslandMorphologyFamily.MASSIF
                        ? SkyIslandMorphologyFamily.TABLELAND
                        : SkyIslandMorphologyFamily.MASSIF;
        SkyIslandWorldVolume realized =
                realizedVolume(
                        authored,
                        5,
                        0,
                        9003L,
                        authored.nominalRadius(),
                        other,
                        true);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandAuthoredRealizationAssociation.of(authored, realized));
    }

    @Test
    void legacyRealizationCanAssociateWithoutInventingMissingMorphology() {
        SkyIslandDescriptor authored = authored(31L);
        SkyIslandWorldVolume legacy =
                realizedVolume(
                        authored,
                        6,
                        0,
                        9004L,
                        authored.nominalRadius(),
                        authored.morphologyFamily(),
                        false);

        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(authored, legacy);

        assertEquals(authored.identity(), association.authoredIdentity());
        assertTrue(
                !association.realizedVolume()
                        .compiledVolume()
                        .descriptor()
                        .hasSemanticMorphologyFamily());
    }

    @Test
    void catalogIsCanonicalAndOneToOneIndependentOfInputOrder() {
        SkyIslandAuthoredRealizationAssociation first =
                association(41L, 0, 0, 9101L);
        SkyIslandAuthoredRealizationAssociation second =
                association(42L, 0, 1, 9102L);
        SkyIslandAuthoredRealizationAssociation third =
                association(43L, 1, 0, 9103L);

        SkyIslandAuthoredRealizationCatalog forward =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        List.of(first, second, third));
        SkyIslandAuthoredRealizationCatalog reverse =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        List.of(third, second, first));

        assertEquals(forward.associations(), reverse.associations());
        assertEquals(3, forward.size());
        for (SkyIslandAuthoredRealizationAssociation association :
                forward.associations()) {
            assertEquals(
                    association,
                    forward.associationFor(association.authoredIdentity()).orElseThrow());
            assertEquals(
                    association,
                    forward.associationFor(association.realizedVolumeId()).orElseThrow());
        }
    }

    @Test
    void catalogRejectsDuplicateAuthoredIslandAndDuplicateRealizedVolume() {
        SkyIslandAuthoredRealizationAssociation first =
                association(51L, 0, 0, 9201L);
        SkyIslandDescriptor sameAuthored = first.authoredDescriptor();
        SkyIslandWorldVolume anotherVolume =
                realizedVolume(
                        sameAuthored,
                        0,
                        1,
                        9202L,
                        sameAuthored.nominalRadius(),
                        sameAuthored.morphologyFamily(),
                        true);
        SkyIslandAuthoredRealizationAssociation duplicateAuthored =
                SkyIslandAuthoredRealizationAssociation.of(
                        sameAuthored,
                        anotherVolume);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        List.of(first, duplicateAuthored)));

        SkyIslandDescriptor anotherAuthored = authored(52L);
        SkyIslandVolumeDescriptor physical =
                first.realizedVolume().compiledVolume().descriptor();
        SkyIslandDescriptor sameScaleAuthored =
                authoredWithRadiusAndMorphology(
                        anotherAuthored.identity(),
                        anotherAuthored,
                        physical.nominalRadius(),
                        physical.morphologyFamily());
        SkyIslandAuthoredRealizationAssociation duplicateRealized =
                SkyIslandAuthoredRealizationAssociation.of(
                        sameScaleAuthored,
                        first.realizedVolume());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        List.of(first, duplicateRealized)));
    }

    @Test
    void catalogEnforcesEachRootWithoutRequiringNumericEqualityBetweenRoots() {
        SkyIslandAuthoredRealizationAssociation association =
                association(61L, 0, 0, 9301L);

        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        List.of(association));
        assertEquals(AUTHORED_WORLD, catalog.authoredWorldSeed());
        assertEquals(REALIZATION_ROOT, catalog.realizationRootSeed());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD + 1L,
                        REALIZATION_ROOT,
                        List.of(association)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT + 1L,
                        List.of(association)));
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            int groupOrdinal,
            int memberOrdinal,
            long geometrySeed) {
        SkyIslandDescriptor authored = authored(islandKey);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                realizedVolume(
                        authored,
                        groupOrdinal,
                        memberOrdinal,
                        geometrySeed,
                        authored.nominalRadius(),
                        authored.morphologyFamily(),
                        true));
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey));
    }

    private static SkyIslandDescriptor authoredWithRadiusAndMorphology(
            SkyIslandIdentity identity,
            SkyIslandDescriptor source,
            double radius,
            SkyIslandMorphologyFamily morphology) {
        return new SkyIslandDescriptor(
                source.schemaVersion(),
                identity,
                source.authorshipSeed(),
                morphology,
                radius,
                source.reliefBudget(),
                source.rockCompetence(),
                source.permeability(),
                source.temperatureTendency(),
                source.moistureTendency(),
                source.exposureTendency(),
                source.erosionMaturity(),
                source.hydrologicalPotential(),
                source.ecologicalPotential());
    }

    private static SkyIslandWorldVolume realizedVolume(
            SkyIslandDescriptor authored,
            int groupOrdinal,
            int memberOrdinal,
            long geometrySeed,
            double radius,
            SkyIslandMorphologyFamily morphology,
            boolean schema2) {
        double centerX = 1200.0 + 800.0 * groupOrdinal + 240.0 * memberOrdinal;
        double centerZ = -900.0 + 520.0 * groupOrdinal - 180.0 * memberOrdinal;
        SkyIslandVolumeDescriptor physical =
                schema2
                        ? SkyIslandVolumeDescriptor.schema2(
                                geometrySeed,
                                centerX,
                                centerZ,
                                256.0,
                                radius,
                                72.0,
                                104.0,
                                Math.min(32.0, radius),
                                0.43,
                                0.62,
                                0.57,
                                0.18,
                                morphology,
                                0.22,
                                38.0,
                                0.31)
                        : new SkyIslandVolumeDescriptor(
                                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                                geometrySeed,
                                centerX,
                                centerZ,
                                256.0,
                                radius,
                                72.0,
                                104.0,
                                Math.min(32.0, radius),
                                0.43,
                                0.62,
                                0.57,
                                0.18,
                                0.22,
                                38.0);
        CompiledSkyIslandVolume compiled =
                schema2
                        ? new SemanticSkyIslandVolumeRecipe().compile(physical)
                        : new SeededSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "group-" + groupOrdinal,
                        groupOrdinal,
                        memberOrdinal,
                        geometrySeed);
        WorldBounds bounds =
                new WorldBounds(
                        centerX - radius,
                        centerX + radius,
                        0.0,
                        512.0,
                        centerZ - radius,
                        centerZ + radius);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }
}
