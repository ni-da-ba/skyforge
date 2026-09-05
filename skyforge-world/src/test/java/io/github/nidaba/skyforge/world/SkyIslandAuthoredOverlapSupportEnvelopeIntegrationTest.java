package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandAuthoredOverlapSupportEnvelopeIntegrationTest {
    private static final long AUTHORED_WORLD = 0x4155544830303531L;
    private static final long REALIZATION_ROOT = 0x5245414C30303531L;

    @Test
    void certifiedSupportConvertsBroadReservationStackFromRejectedToCertified() {
        SkyIslandAuthoredRealizationAssociation lower =
                semanticAssociation(
                        601L,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.0,
                        0.0,
                        150.0,
                        51601L);
        SkyIslandAuthoredRealizationAssociation upper =
                semanticAssociation(
                        602L,
                        SkyIslandMorphologyFamily.TABLELAND,
                        0.0,
                        0.0,
                        360.0,
                        51602L);
        SkyIslandAuthoredRealizationCatalog associations = catalog(List.of(upper, lower));
        SkyIslandAuthoredOverlapAdmissionPolicy policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(
                                SkyIslandAuthoredOverlapPairRule.stacked(
                                        lower, upper, 40.0)));

        SkyIslandAuthoredOverlapPairAudit broadOnly =
                new SkyIslandAuthoredOverlapAdmissionAuditor(associations, policy)
                        .audit()
                        .pairAudits()
                        .get(0);
        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations);
        SkyIslandAuthoredOverlapPairAudit certified =
                new SkyIslandAuthoredOverlapAdmissionAuditor(
                                associations, policy, support)
                        .audit()
                        .pairAudits()
                        .get(0);

        assertTrue(broadOnly.conservativeBoundsIntersect());
        assertEquals(0.0, broadOnly.conservativeVerticalGap());
        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.REJECTED_STACK_REQUIREMENT,
                broadOnly.status());
        assertFalse(broadOnly.admitted());

        assertTrue(certified.conservativeBoundsIntersect());
        assertTrue(certified.conservativeVerticalGap() >= 40.0);
        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.CERTIFIED_STACKED,
                certified.status());
        assertTrue(certified.admitted());
        assertEquals(2, support.certifiedCount());
    }

    @Test
    void strictPairMayUseCertifiedVerticalSupportWithoutDeclaringStack() {
        SkyIslandAuthoredRealizationAssociation lower =
                semanticAssociation(
                        701L,
                        SkyIslandMorphologyFamily.SPINE,
                        400.0,
                        -200.0,
                        150.0,
                        51701L);
        SkyIslandAuthoredRealizationAssociation upper =
                semanticAssociation(
                        702L,
                        SkyIslandMorphologyFamily.BASIN,
                        400.0,
                        -200.0,
                        360.0,
                        51702L);
        SkyIslandAuthoredRealizationCatalog associations = catalog(List.of(lower, upper));

        SkyIslandAuthoredOverlapPairAudit audit =
                new SkyIslandAuthoredOverlapAdmissionAuditor(
                                associations,
                                SkyIslandAuthoredOverlapAdmissionPolicy.strict(),
                                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(
                                        associations))
                        .audit()
                        .pairAudits()
                        .get(0);

        assertTrue(audit.conservativeBoundsIntersect());
        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.CERTIFIED_SEPARATE,
                audit.status());
        assertTrue(audit.admitted());
    }

    @Test
    void uncertifiedLegacyMemberStillFallsBackToBroadReservation() {
        SkyIslandAuthoredRealizationAssociation lower =
                semanticAssociation(
                        801L,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.0,
                        0.0,
                        150.0,
                        51801L);
        SkyIslandAuthoredRealizationAssociation upper =
                legacyAssociation(802L, 0.0, 0.0, 360.0, 51802L);
        SkyIslandAuthoredRealizationCatalog associations = catalog(List.of(lower, upper));
        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations);
        SkyIslandAuthoredOverlapAdmissionPolicy policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(
                                SkyIslandAuthoredOverlapPairRule.stacked(
                                        lower, upper, 40.0)));

        SkyIslandAuthoredOverlapPairAudit audit =
                new SkyIslandAuthoredOverlapAdmissionAuditor(
                                associations, policy, support)
                        .audit()
                        .pairAudits()
                        .get(0);

        assertEquals(1, support.certifiedCount());
        assertEquals(1, support.uncertifiedCount());
        assertEquals(0.0, audit.conservativeVerticalGap());
        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.REJECTED_STACK_REQUIREMENT,
                audit.status());
        assertFalse(audit.admitted());
    }

    @Test
    void auditorRejectsSupportCatalogForDifferentAssociationCatalog() {
        SkyIslandAuthoredRealizationAssociation first =
                semanticAssociation(
                        901L,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.0,
                        0.0,
                        150.0,
                        51901L);
        SkyIslandAuthoredRealizationAssociation second =
                semanticAssociation(
                        902L,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.0,
                        0.0,
                        360.0,
                        51902L);
        SkyIslandAuthoredRealizationAssociation foreign =
                semanticAssociation(
                        903L,
                        SkyIslandMorphologyFamily.MASSIF,
                        500.0,
                        0.0,
                        360.0,
                        51903L);
        SkyIslandAuthoredRealizationCatalog expected = catalog(List.of(first, second));
        SkyIslandAuthoredRealizationSupportCatalog wrong =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(
                        catalog(List.of(first, foreign)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredOverlapAdmissionAuditor(
                        expected,
                        SkyIslandAuthoredOverlapAdmissionPolicy.strict(),
                        wrong));
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
                        30.0,
                        40.0,
                        24.0,
                        0.17,
                        0.56,
                        0.55,
                        0.12,
                        family,
                        0.20,
                        34.0,
                        0.20);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        return association(
                authored,
                physical,
                compiled,
                islandKey,
                geometrySeed,
                centerX,
                centerZ,
                suspension);
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
                        30.0,
                        40.0,
                        24.0,
                        0.17,
                        0.56,
                        0.55,
                        0.12,
                        0.20,
                        34.0);
        var compiled = new SeededSkyIslandVolumeRecipe().compile(physical);
        return association(
                authored,
                physical,
                compiled,
                islandKey,
                geometrySeed,
                centerX,
                centerZ,
                suspension);
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor authored,
            SkyIslandVolumeDescriptor physical,
            io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiled,
            long islandKey,
            long geometrySeed,
            double centerX,
            double centerZ,
            double suspension) {
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth51-stack-" + islandKey,
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
