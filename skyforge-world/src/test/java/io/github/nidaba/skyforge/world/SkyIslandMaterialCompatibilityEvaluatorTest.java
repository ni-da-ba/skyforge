package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialCompatibilityEvaluatorTest {
    private static final SkyIslandIdentity IDENTITY =
            SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 2211L);

    @Test
    void requestRoleAndSourceProduceDeterministicHardConstraints() {
        SkyIslandMaterialCapabilityConstraintSet massivePrimary =
                SkyIslandMaterialCapabilityPolicy.constraints(
                        request(
                                SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                                SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX));
        assertEquals(
                SkyIslandMaterialCapabilityPolicy.PRIMARY_HOST_MINIMUM,
                massivePrimary.minimum(
                        SkyIslandMaterialCapability.HOST_MATRIX_SUITABILITY));
        assertEquals(
                0.0,
                massivePrimary.minimum(
                        SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS));

        SkyIslandMaterialCapabilityConstraintSet fabricPrimary =
                SkyIslandMaterialCapabilityPolicy.constraints(
                        request(
                                SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                                SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX));
        assertEquals(
                SkyIslandMaterialCapabilityPolicy.PRIMARY_HOST_MINIMUM,
                fabricPrimary.minimum(
                        SkyIslandMaterialCapability.HOST_MATRIX_SUITABILITY));
        assertEquals(
                SkyIslandMaterialCapabilityPolicy.PRIMARY_FABRIC_MINIMUM,
                fabricPrimary.minimum(
                        SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS));

        SkyIslandMaterialCapabilityConstraintSet secondaryFabric =
                SkyIslandMaterialCapabilityPolicy.constraints(
                        request(
                                SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX,
                                SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX));
        assertEquals(
                SkyIslandMaterialCapabilityPolicy.SECONDARY_HOST_MINIMUM,
                secondaryFabric.minimum(
                        SkyIslandMaterialCapability.HOST_MATRIX_SUITABILITY));
        assertEquals(
                SkyIslandMaterialCapabilityPolicy.SECONDARY_FABRIC_MINIMUM,
                secondaryFabric.minimum(
                        SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS));
    }

    @Test
    void matchingSemanticProfilesPassAndMissingCapabilitiesFail() {
        SkyIslandMaterialCapabilityProfile matrixProfile =
                new SkyIslandMaterialCapabilityProfile(0.90, 0.20, 0.20, 0.20, 0.20);
        SkyIslandMaterialCapabilityProfile fabricProfile =
                new SkyIslandMaterialCapabilityProfile(0.82, 0.90, 0.20, 0.20, 0.20);
        SkyIslandMaterialCapabilityProfile alterationProfile =
                new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.90, 0.20, 0.20);
        SkyIslandMaterialCapabilityProfile hydrologicProfile =
                new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.90, 0.20);
        SkyIslandMaterialCapabilityProfile accentProfile =
                new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.20, 0.90);

        SkyIslandMaterialBindingRequest massivePrimary =
                request(
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);
        assertTrue(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                massivePrimary, matrixProfile)
                        .compatible());

        SkyIslandMaterialBindingRequest fabricPrimary =
                request(
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX);
        assertTrue(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                fabricPrimary, fabricProfile)
                        .compatible());
        assertFalse(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                fabricPrimary, matrixProfile)
                        .compatible());

        assertTrue(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                request(
                                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT),
                                alterationProfile)
                        .compatible());
        assertTrue(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                request(
                                        SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                                        SkyIslandLithologicRealizationChannel.WATER_CONDITIONING),
                                hydrologicProfile)
                        .compatible());
        assertTrue(
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                request(
                                        SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                                        SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE),
                                accentProfile)
                        .compatible());
    }

    @Test
    void everyRequirementIsHardAndAuditable() {
        SkyIslandMaterialBindingRequest request =
                request(
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX);
        SkyIslandMaterialCapabilityProfile profile =
                new SkyIslandMaterialCapabilityProfile(0.80, 0.50, 1.0, 1.0, 1.0);

        SkyIslandMaterialCompatibilityAssessment assessment =
                SkyIslandMaterialCompatibilityEvaluator.evaluate(request, profile);

        assertFalse(assessment.compatible());
        assertEquals(1, assessment.failedRequirementCount());
        assertEquals(2, assessment.evaluations().size());
        assertEquals(
                SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS,
                assessment.evaluations().get(1).capability());
        assertTrue(assessment.minimumMargin() < 0.0);
    }

    @Test
    void geologicalContextDoesNotSecretlyChangeCapabilityThresholds() {
        SkyIslandMaterialBindingRequest conditioned =
                request(
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT);

        SkyIslandSemanticPaletteBindingKey contactKey =
                SkyIslandSemanticPaletteBindingKey.of(
                        IDENTITY,
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT,
                        SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION,
                        4);
        SkyIslandMaterialBindingRequest contact =
                new SkyIslandMaterialBindingRequest(
                        contactKey,
                        false,
                        SkyIslandMaterialBindingRequestPolicy.minimumEligibleSupport(
                                contactKey.role()),
                        SkyIslandMaterialBindingRequestPolicy.minimumSecondaryHostRatio(
                                contactKey.role()),
                        SkyIslandMaterialBindingRequestPolicy.maximumExpressionCeiling(
                                contactKey.role()),
                        List.of(
                                new SkyIslandMaterialBindingAssemblageContext(
                                        1, SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT),
                                new SkyIslandMaterialBindingAssemblageContext(
                                        2, SkyIslandLithologicAssemblageKind.ALTERED_HOST_UNIT)),
                        4,
                        SkyIslandLithologicContactKind.ALTERATION_FRONT);

        assertEquals(
                SkyIslandMaterialCapabilityPolicy.constraints(conditioned).requirements(),
                SkyIslandMaterialCapabilityPolicy.constraints(contact).requirements());
    }

    @Test
    void allCanonicalRequestsAcceptAUnitCapabilityGeneralist() {
        SkyIslandMaterialCapabilityProfile generalist =
                SkyIslandMaterialCapabilityProfile.uniform(1.0);

        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            var descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(
                                    0x534B59464F524745L, 8L, 81L, key));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);

            for (SkyIslandMaterialBindingRequest request :
                    field.catalog().plannedRequests()) {
                assertTrue(
                        SkyIslandMaterialCompatibilityEvaluator.evaluate(
                                        request, generalist)
                                .compatible());
            }
        }
    }

    private static SkyIslandMaterialBindingRequest request(
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel channel) {
        SkyIslandSemanticPaletteBindingDomainKind domainKind =
                role == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                                || role == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX
                        ? SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION
                        : SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION;
        SkyIslandSemanticPaletteBindingKey key =
                SkyIslandSemanticPaletteBindingKey.of(
                        IDENTITY, role, channel, domainKind, 0);
        return new SkyIslandMaterialBindingRequest(
                key,
                SkyIslandMaterialBindingRequestPolicy.required(role),
                SkyIslandMaterialBindingRequestPolicy.minimumEligibleSupport(role),
                SkyIslandMaterialBindingRequestPolicy.minimumSecondaryHostRatio(role),
                SkyIslandMaterialBindingRequestPolicy.maximumExpressionCeiling(role),
                List.of(
                        new SkyIslandMaterialBindingAssemblageContext(
                                0, SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT)),
                -1,
                null);
    }
}
