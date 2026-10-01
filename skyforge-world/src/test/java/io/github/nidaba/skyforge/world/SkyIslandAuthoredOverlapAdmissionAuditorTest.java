package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandAuthoredOverlapAdmissionAuditorTest {
    private static final long AUTHORED_WORLD = 0x4155544830303530L;
    private static final long REALIZATION_ROOT = 0x5245414C30303530L;
    private static final double RADIUS = 100.0;

    @Test
    void strictPolicyCertifiesDisjointConservativeBounds() {
        var first = association(101L, -400.0, 0.0, 200.0, 50101L, bounds(-550, -250, 80, 320, -150, 150));
        var second = association(102L, 400.0, 0.0, 200.0, 50102L, bounds(250, 550, 80, 320, -150, 150));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(first, second), SkyIslandAuthoredOverlapAdmissionPolicy.strict())
                        .pairAudits()
                        .get(0);

        assertEquals(SkyIslandAuthoredOverlapPairStatus.CERTIFIED_SEPARATE, audit.status());
        assertFalse(audit.conservativeBoundsIntersect());
        assertTrue(audit.nativeSupportDiscsDisjoint());
        assertTrue(audit.admitted());
        assertTrue(audit.witness().isEmpty());
    }

    @Test
    void overlappingConservativeBoundsMayStillBeCertifiedByNativeSupport() {
        var first = association(201L, -110.0, 0.0, 200.0, 50201L, bounds(-280, 60, 80, 320, -170, 170));
        var second = association(202L, 110.0, 0.0, 200.0, 50202L, bounds(-60, 280, 80, 320, -170, 170));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(first, second), SkyIslandAuthoredOverlapAdmissionPolicy.strict())
                        .pairAudits()
                        .get(0);

        assertTrue(audit.conservativeBoundsIntersect());
        assertTrue(audit.nativeSupportDiscsDisjoint());
        assertEquals(SkyIslandAuthoredOverlapPairStatus.CERTIFIED_SEPARATE, audit.status());
        assertTrue(audit.admitted());
    }

    @Test
    void intentionalSameCenterStackRequiresAndAcceptsCertifiedVerticalGap() {
        var lower = association(301L, 0.0, 0.0, 140.0, 50301L, bounds(-140, 140, 50, 210, -140, 140));
        var upper = association(302L, 0.0, 0.0, 360.0, 50302L, bounds(-140, 140, 290, 440, -140, 140));
        var policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(SkyIslandAuthoredOverlapPairRule.stacked(lower, upper, 60.0)));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(upper, lower), policy).pairAudits().get(0);

        assertEquals(SkyIslandAuthoredOverlapPairStatus.CERTIFIED_STACKED, audit.status());
        assertEquals(80.0, audit.conservativeVerticalGap());
        assertTrue(audit.admitted());
        assertFalse(audit.conservativeBoundsIntersect());
    }

    @Test
    void stackedRuleFailsClosedWhenBroadBoundsCannotCertifyRequiredGap() {
        var lower = association(401L, 0.0, 0.0, 140.0, 50401L, bounds(-140, 140, 0, 300, -140, 140));
        var upper = association(402L, 0.0, 0.0, 360.0, 50402L, bounds(-140, 140, 180, 500, -140, 140));
        var policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(SkyIslandAuthoredOverlapPairRule.stacked(lower, upper, 40.0)));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(lower, upper), policy).pairAudits().get(0);

        assertTrue(audit.conservativeBoundsIntersect());
        assertEquals(0.0, audit.conservativeVerticalGap());
        assertEquals(SkyIslandAuthoredOverlapPairStatus.REJECTED_STACK_REQUIREMENT, audit.status());
        assertFalse(audit.admitted());
        assertTrue(audit.witness().isEmpty());
    }

    @Test
    void strictTrueNativeOverlapIsRejectedWithExactAuth0048Witness() {
        var first = association(501L, 500.0, -300.0, 240.0, 50501L, bounds(350, 650, 80, 400, -450, -150));
        var second = association(502L, 500.0, -300.0, 240.0, 50502L, bounds(350, 650, 80, 400, -450, -150));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(first, second), SkyIslandAuthoredOverlapAdmissionPolicy.strict())
                        .pairAudits()
                        .get(0);

        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.REJECTED_WITNESSED_OVERLAP,
                audit.status());
        assertFalse(audit.admitted());
        Coordinate3 witness = audit.witness().orElseThrow();
        var pairCatalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, REALIZATION_ROOT, List.of(first, second));
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS,
                new SkyIslandAuthoredRealizationOwnershipResolver(pairCatalog)
                        .resolve(witness)
                        .status());
    }

    @Test
    void finiteWitnessMissNeverBecomesSeparationProof() {
        var lower = association(601L, 0.0, 0.0, 140.0, 50601L, bounds(-140, 140, 0, 300, -140, 140));
        var upper = association(602L, 0.0, 0.0, 360.0, 50602L, bounds(-140, 140, 180, 500, -140, 140));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(lower, upper), SkyIslandAuthoredOverlapAdmissionPolicy.strict())
                        .pairAudits()
                        .get(0);

        assertTrue(audit.conservativeBoundsIntersect());
        assertFalse(audit.nativeSupportDiscsDisjoint());
        assertTrue(audit.witness().isEmpty());
        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.REJECTED_UNCERTIFIED_SEPARATION,
                audit.status());
        assertFalse(audit.admitted());
    }

    @Test
    void explicitComposeIsTheOnlyPolicyThatAdmitsGenuineOverlap() {
        var first = association(701L, -600.0, 500.0, 250.0, 50701L, bounds(-760, -440, 80, 420, 340, 660));
        var second = association(702L, -600.0, 500.0, 250.0, 50702L, bounds(-760, -440, 80, 420, 340, 660));
        var policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(SkyIslandAuthoredOverlapPairRule.compose(first, second)));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(second, first), policy).pairAudits().get(0);

        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.ACCEPTED_EXPLICIT_COMPOSITION,
                audit.status());
        assertTrue(audit.admitted());
        assertTrue(audit.witness().isPresent());
    }

    @Test
    void stackedRuleRejectsDifferentHorizontalCentersEvenWhenBoundsAreSeparated() {
        var lower = association(801L, 0.0, 0.0, 120.0, 50801L, bounds(-140, 140, 30, 190, -140, 140));
        var upper = association(802L, 25.0, 0.0, 360.0, 50802L, bounds(-115, 165, 290, 440, -140, 140));
        var policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(SkyIslandAuthoredOverlapPairRule.stacked(lower, upper, 80.0)));

        SkyIslandAuthoredOverlapPairAudit audit =
                audit(List.of(lower, upper), policy).pairAudits().get(0);

        assertEquals(
                SkyIslandAuthoredOverlapPairStatus.REJECTED_STACK_REQUIREMENT,
                audit.status());
        assertFalse(audit.admitted());
    }

    @Test
    void policyCannotReferenceAssociationOutsideCatalog() {
        var first = association(901L, 0.0, 0.0, 200.0, 50901L, bounds(-150, 150, 80, 320, -150, 150));
        var second = association(902L, 400.0, 0.0, 200.0, 50902L, bounds(250, 550, 80, 320, -150, 150));
        var foreign = association(903L, 800.0, 0.0, 200.0, 50903L, bounds(650, 950, 80, 320, -150, 150));
        var policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(SkyIslandAuthoredOverlapPairRule.compose(first, foreign)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredOverlapAdmissionAuditor(
                        catalog(List.of(first, second)), policy));
    }

    @Test
    void reportIsCanonicalAndRejectsCatalogWhenAnyPairFails() {
        var first = association(1001L, -350.0, 0.0, 220.0, 51001L, bounds(-500, -200, 60, 380, -150, 150));
        var second = association(1002L, 350.0, 0.0, 220.0, 51002L, bounds(200, 500, 60, 380, -150, 150));
        var overlap = association(1003L, 350.0, 0.0, 220.0, 51003L, bounds(200, 500, 60, 380, -150, 150));

        SkyIslandAuthoredOverlapAdmissionReport forward =
                audit(List.of(first, second, overlap), SkyIslandAuthoredOverlapAdmissionPolicy.strict());
        SkyIslandAuthoredOverlapAdmissionReport reversed =
                audit(List.of(overlap, second, first), SkyIslandAuthoredOverlapAdmissionPolicy.strict());

        assertEquals(
                forward.pairAudits().stream().map(audit -> audit.pair()).toList(),
                reversed.pairAudits().stream().map(audit -> audit.pair()).toList());
        assertFalse(forward.admitted());
        assertTrue(forward.rejectedPairCount() > 0);
        assertTrue(forward.witnessedOverlapCount() > 0);
    }

    private static SkyIslandAuthoredOverlapAdmissionReport audit(
            List<SkyIslandAuthoredRealizationAssociation> associations,
            SkyIslandAuthoredOverlapAdmissionPolicy policy) {
        return new SkyIslandAuthoredOverlapAdmissionAuditor(catalog(associations), policy)
                .audit();
    }

    private static SkyIslandAuthoredRealizationCatalog catalog(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        return new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD, REALIZATION_ROOT, associations);
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed,
            WorldBounds bounds) {
        SkyIslandDescriptor authored =
                new SkyIslandDescriptor(
                        SkyIslandDescriptor.SCHEMA_VERSION,
                        SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                        0x5000000000000000L ^ islandKey,
                        SkyIslandMorphologyFamily.MASSIF,
                        RADIUS,
                        80.0,
                        0.72,
                        0.42,
                        0.54,
                        0.58,
                        0.50,
                        0.46,
                        0.57,
                        0.63);
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        RADIUS,
                        32.0,
                        44.0,
                        28.0,
                        0.0,
                        0.45,
                        0.60,
                        0.06,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.10,
                        30.0,
                        0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth50-" + islandKey,
                                0,
                                (int) (islandKey & 0x7fff),
                                geometrySeed),
                        bounds,
                        compiled));
    }

    private static WorldBounds bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {
        return new WorldBounds(
                minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ);
    }
}
