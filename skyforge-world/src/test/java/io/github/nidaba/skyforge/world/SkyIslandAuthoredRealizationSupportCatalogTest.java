package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandAuthoredRealizationSupportCatalogTest {
    private static final long AUTHORED_WORLD = 0x4155544830303531L;
    private static final long REALIZATION_ROOT = 0x5245414C30303531L;

    @Test
    void acceptedSchema2AssociationsReceiveCertificates() {
        SkyIslandAuthoredRealizationAssociation first =
                semanticAssociation(101L, SkyIslandMorphologyFamily.MASSIF, 0.0, 0.0, 220.0, 51101L);
        SkyIslandAuthoredRealizationAssociation second =
                semanticAssociation(102L, SkyIslandMorphologyFamily.SPINE, 500.0, 0.0, 260.0, 51102L);
        SkyIslandAuthoredRealizationCatalog associations =
                catalog(List.of(second, first));

        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations);

        assertEquals(2, support.certifiedCount());
        assertEquals(0, support.uncertifiedCount());
        assertTrue(support.certificateFor(first).isPresent());
        assertTrue(support.certificateFor(second).isPresent());
        assertEquals(
                associations.associations().stream()
                        .map(SkyIslandAuthoredRealizationAssociation::canonicalToken)
                        .toList(),
                support.certificates().stream()
                        .map(SkyIslandAuthoredRealizationSupportCertificate::associationToken)
                        .toList());
    }

    @Test
    void unsupportedLegacyRecipeRemainsUncertified() {
        SkyIslandAuthoredRealizationAssociation legacy = legacyAssociation(201L, 0.0, 0.0, 220.0, 51201L);
        SkyIslandAuthoredRealizationCatalog associations = catalog(List.of(legacy));

        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations);

        assertEquals(0, support.certifiedCount());
        assertEquals(1, support.uncertifiedCount());
        assertTrue(support.certificateFor(legacy).isEmpty());
    }

    @Test
    void certifiedBoundsContainSampledCompiledColumnsAcrossAllBuiltInFamilies() {
        int sampledColumns = 0;
        int ordinal = 0;
        for (SkyIslandMorphologyFamily family : SkyIslandMorphologyFamily.values()) {
            SkyIslandAuthoredRealizationAssociation association =
                    semanticAssociation(
                            300L + ordinal,
                            family,
                            700.0 * ordinal,
                            -300.0 * ordinal,
                            250.0 + 10.0 * ordinal,
                            51300L + ordinal);
            SkyIslandAuthoredRealizationCatalog associations = catalog(List.of(association));
            SkyIslandAuthoredRealizationSupportCertificate certificate =
                    SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations)
                            .certificateFor(association)
                            .orElseThrow();
            WorldBounds support = certificate.supportBounds();
            SkyIslandCompiledVolumeColumnField columns =
                    new SkyIslandCompiledVolumeColumnField(
                            association.realizedVolume().compiledVolume());
            double radius = certificate.envelope().maximumHorizontalRadius();

            for (int iz = 0; iz < 45; iz++) {
                double z = -radius + iz * (2.0 * radius / 44.0);
                for (int ix = 0; ix < 45; ix++) {
                    double x = -radius + ix * (2.0 * radius / 44.0);
                    var column = columns.columnAt(new SkyIslandLocalPosition(x, z));
                    if (column.isEmpty()) {
                        continue;
                    }
                    sampledColumns++;
                    assertTrue(column.orElseThrow().upperY() <= support.maximumY() + 1.0e-9);
                    assertTrue(column.orElseThrow().undersideY() >= support.minimumY() - 1.0e-9);
                }
            }
            ordinal++;
        }
        assertTrue(sampledColumns > 200);
    }

    @Test
    void supportCertificateIsIndependentOfBroaderQueryReservation() {
        SkyIslandAuthoredRealizationAssociation association =
                semanticAssociation(401L, SkyIslandMorphologyFamily.TABLELAND, 0.0, 0.0, 240.0, 51401L);
        SkyIslandAuthoredRealizationSupportCertificate certificate =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(catalog(List.of(association)))
                        .certificateFor(association)
                        .orElseThrow();

        WorldBounds query = association.realizedVolume().bounds();
        WorldBounds proof = certificate.supportBounds();

        assertTrue(query.minimumY() < proof.minimumY());
        assertTrue(query.maximumY() > proof.maximumY());
        assertFalse(query.equals(proof));
    }

    @Test
    void manualSupportCatalogRejectsForeignAssociation() {
        SkyIslandAuthoredRealizationAssociation catalogAssociation =
                semanticAssociation(501L, SkyIslandMorphologyFamily.MASSIF, 0.0, 0.0, 240.0, 51501L);
        SkyIslandAuthoredRealizationAssociation foreign =
                semanticAssociation(502L, SkyIslandMorphologyFamily.MASSIF, 500.0, 0.0, 240.0, 51502L);
        CertifiedSkyIslandSupportEnvelope envelope =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(catalog(List.of(foreign)))
                        .certificateFor(foreign)
                        .orElseThrow()
                        .envelope();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationSupportCatalog(
                        catalog(List.of(catalogAssociation)),
                        List.of(
                                new SkyIslandAuthoredRealizationSupportCertificate(
                                        foreign, envelope))));
    }

    private static SkyIslandAuthoredRealizationCatalog catalog(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        return new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD, REALIZATION_ROOT, associations);
    }

    private static SkyIslandAuthoredRealizationAssociation semanticAssociation(
            long islandKey,
            SkyIslandMorphologyFamily family,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed) {
        double radius = 100.0;
        SkyIslandDescriptor authored = authored(islandKey, family, radius);
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        radius,
                        38.0,
                        52.0,
                        24.0,
                        0.21,
                        0.64,
                        0.55,
                        -0.24,
                        family,
                        0.55,
                        34.0,
                        0.60);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth51-" + islandKey,
                                0,
                                (int) islandKey,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 180.0,
                                centerX + 180.0,
                                suspension - 220.0,
                                suspension + 220.0,
                                centerZ - 180.0,
                                centerZ + 180.0),
                        compiled));
    }

    private static SkyIslandAuthoredRealizationAssociation legacyAssociation(
            long islandKey,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed) {
        double radius = 100.0;
        SkyIslandDescriptor authored =
                authored(islandKey, SkyIslandMorphologyFamily.MASSIF, radius);
        SkyIslandVolumeDescriptor physical =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        radius,
                        38.0,
                        52.0,
                        24.0,
                        0.21,
                        0.64,
                        0.55,
                        -0.24,
                        0.40,
                        34.0);
        var compiled = new SeededSkyIslandVolumeRecipe().compile(physical);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth51-legacy-" + islandKey,
                                1,
                                (int) islandKey,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 180.0,
                                centerX + 180.0,
                                suspension - 220.0,
                                suspension + 220.0,
                                centerZ - 180.0,
                                centerZ + 180.0),
                        compiled));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandMorphologyFamily family,
            double radius) {
        return new SkyIslandDescriptor(
                SkyIslandDescriptor.SCHEMA_VERSION,
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                0x5100000000000000L ^ islandKey,
                family,
                radius,
                82.0,
                0.72,
                0.42,
                0.54,
                0.58,
                0.50,
                0.46,
                0.57,
                0.63);
    }
}
