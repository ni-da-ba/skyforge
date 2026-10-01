package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialResolutionDecisionFactoryTest {
    private static final SkyIslandIdentity IDENTITY =
            SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 2211L);

    private static final SkyIslandMaterialCapabilityProfile MATRIX =
            new SkyIslandMaterialCapabilityProfile(0.94, 0.20, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile FABRIC =
            new SkyIslandMaterialCapabilityProfile(0.92, 0.94, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ALTERATION =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.94, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile WATER =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.94, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ACCENT =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.20, 0.94);
    private static final SkyIslandMaterialCapabilityProfile GENERALIST =
            SkyIslandMaterialCapabilityProfile.uniform(0.86);

    @Test
    void uniqueSemanticWinnerProducesAuditableDecision() {
        SkyIslandMaterialBindingRequest request =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);

        SkyIslandMaterialResolutionDecision decision =
                SkyIslandMaterialResolutionDecisionFactory.decide(
                        request,
                        List.of(MATRIX, FABRIC, GENERALIST),
                        MATRIX,
                        SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);

        assertEquals(request, decision.request());
        assertEquals(MATRIX, decision.selectedProfile());
        assertTrue(decision.selectedCompatibility().compatible());
        assertEquals(
                SkyIslandMaterialCandidateRanker.rank(request, MATRIX),
                decision.selectedRank());
        assertEquals(3, decision.compatibleCandidateCount());
        assertEquals(1, decision.topSemanticTieCount());
        assertFalse(decision.backendStableTieBreakApplied());
    }

    @Test
    void duplicateTopProfilesRequireBackendStableTieBreakProvenance() {
        SkyIslandMaterialBindingRequest request =
                request(
                        2,
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT);

        List<SkyIslandMaterialCapabilityProfile> candidates =
                List.of(ALTERATION, ALTERATION, GENERALIST);

        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, candidates);
        assertEquals(3, frontier.compatibleCandidateCount());
        assertEquals(2, frontier.topSemanticTieCount());
        assertTrue(frontier.requiresBackendStableTieBreak());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandMaterialResolutionDecisionFactory.decide(
                                frontier,
                                ALTERATION,
                                SkyIslandMaterialResolutionSelectionMethod
                                        .SEMANTIC_RANK_WINNER));

        SkyIslandMaterialResolutionDecision decision =
                SkyIslandMaterialResolutionDecisionFactory.decide(
                        frontier,
                        ALTERATION,
                        SkyIslandMaterialResolutionSelectionMethod
                                .BACKEND_STABLE_IDENTITY_TIE_BREAK);
        assertTrue(decision.backendStableTieBreakApplied());
        assertEquals(2, decision.topSemanticTieCount());
    }

    @Test
    void nonTopCompatibleProfileCannotBeSelected() {
        SkyIslandMaterialBindingRequest request =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandMaterialResolutionDecisionFactory.decide(
                                request,
                                List.of(MATRIX, GENERALIST),
                                GENERALIST,
                                SkyIslandMaterialResolutionSelectionMethod
                                        .SEMANTIC_RANK_WINNER));
    }

    @Test
    void candidateEncounterOrderDoesNotChangeFrontierOrDecision() {
        SkyIslandMaterialBindingRequest request =
                request(
                        1,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX);
        List<SkyIslandMaterialCapabilityProfile> forward =
                List.of(MATRIX, FABRIC, GENERALIST);
        List<SkyIslandMaterialCapabilityProfile> reverse =
                new ArrayList<>(forward);
        Collections.reverse(reverse);

        SkyIslandMaterialResolutionDecision first =
                SkyIslandMaterialResolutionDecisionFactory.decide(
                        request,
                        forward,
                        FABRIC,
                        SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
        SkyIslandMaterialResolutionDecision second =
                SkyIslandMaterialResolutionDecisionFactory.decide(
                        request,
                        reverse,
                        FABRIC,
                        SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);

        assertEquals(first, second);
    }

    @Test
    void incompatibleCandidatesNeverEnterTheFrontier() {
        SkyIslandMaterialBindingRequest request =
                request(
                        3,
                        SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                        SkyIslandLithologicRealizationChannel.WATER_CONDITIONING);

        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, List.of(MATRIX, ALTERATION, WATER, GENERALIST));

        assertEquals(2, frontier.compatibleCandidateCount());
        assertTrue(frontier.containsProfile(WATER));
        assertTrue(frontier.containsProfile(GENERALIST));
        assertFalse(frontier.containsProfile(MATRIX));
        assertFalse(frontier.containsProfile(ALTERATION));
    }

    @Test
    void noCompatibleCandidateIsRejected() {
        SkyIslandMaterialBindingRequest request =
                request(
                        4,
                        SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                        SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandMaterialResolutionDecisionFactory.frontier(
                                request, List.of(MATRIX, WATER)));
    }

    @Test
    void canonicalRequestsRemainStablePerBindingKey() {
        List<SkyIslandMaterialCapabilityProfile> candidates =
                diagnosticCandidates();

        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            var descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(
                                    0x534B59464F524745L, 8L, 81L, key));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            double radius = descriptor.nominalRadius();
            var decisions = new java.util.HashMap<
                    String, SkyIslandMaterialResolutionDecision>();

            for (int iz = 0; iz < 15; iz++) {
                double z = -radius + iz * (2.0 * radius / 14.0);
                for (int ix = 0; ix < 15; ix++) {
                    double x = -radius + ix * (2.0 * radius / 14.0);
                    SkyIslandMaterialBindingRequestSelection selection =
                            field.sample(new SkyIslandSubsurfacePosition(x, z, 0.52));
                    for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                        SkyIslandMaterialResolutionFrontier frontier =
                                SkyIslandMaterialResolutionDecisionFactory.frontier(
                                        use.request(), candidates);
                        SkyIslandMaterialCapabilityProfile selected =
                                frontier.topRank().profile();
                        SkyIslandMaterialResolutionSelectionMethod method =
                                frontier.requiresBackendStableTieBreak()
                                        ? SkyIslandMaterialResolutionSelectionMethod
                                                .BACKEND_STABLE_IDENTITY_TIE_BREAK
                                        : SkyIslandMaterialResolutionSelectionMethod
                                                .SEMANTIC_RANK_WINNER;
                        SkyIslandMaterialResolutionDecision decision =
                                SkyIslandMaterialResolutionDecisionFactory.decide(
                                        frontier, selected, method);
                        String token = use.request().bindingKey().canonicalToken();
                        SkyIslandMaterialResolutionDecision previous =
                                decisions.putIfAbsent(token, decision);
                        if (previous != null) {
                            assertEquals(previous, decision);
                        }
                    }
                }
            }
        }
    }

    private static List<SkyIslandMaterialCapabilityProfile> diagnosticCandidates() {
        return List.of(
                MATRIX,
                FABRIC,
                ALTERATION,
                ALTERATION,
                WATER,
                ACCENT,
                ACCENT,
                GENERALIST);
    }

    private static SkyIslandMaterialBindingRequest request(
            int anchor,
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel channel) {
        SkyIslandSemanticPaletteBindingDomainKind domainKind =
                role == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                                || role == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX
                        ? SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION
                        : SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION;
        SkyIslandSemanticPaletteBindingKey key =
                SkyIslandSemanticPaletteBindingKey.of(
                        IDENTITY, role, channel, domainKind, anchor);
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
