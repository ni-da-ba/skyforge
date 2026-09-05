package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialCandidateRankerTest {
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
    void incompatibleProfilesNeverEnterRanking() {
        SkyIslandMaterialBindingRequest request =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandMaterialCandidateRanker.rank(request, MATRIX));

        List<SkyIslandMaterialCandidateRank> ranks =
                SkyIslandMaterialCandidateRanker.rankCompatible(
                        request, List.of(MATRIX, ALTERATION, GENERALIST));
        assertEquals(2, ranks.size());
        assertFalse(ranks.stream().anyMatch(rank -> rank.profile().equals(MATRIX)));
    }

    @Test
    void semanticSpecialistsOutrankTheEvidenceGeneralist() {
        assertWinner(
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX),
                MATRIX);
        assertWinner(
                request(
                        1,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX),
                FABRIC);
        assertWinner(
                request(
                        2,
                        SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                        SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT),
                ALTERATION);
        assertWinner(
                request(
                        3,
                        SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                        SkyIslandLithologicRealizationChannel.WATER_CONDITIONING),
                WATER);
        assertWinner(
                request(
                        4,
                        SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                        SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE),
                ACCENT);
    }

    @Test
    void rankingIsIndependentOfCandidateEncounterOrder() {
        SkyIslandMaterialBindingRequest request =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);
        List<SkyIslandMaterialCapabilityProfile> forward =
                List.of(MATRIX, FABRIC, GENERALIST);
        List<SkyIslandMaterialCapabilityProfile> reverse =
                new ArrayList<>(forward);
        Collections.reverse(reverse);

        assertEquals(
                SkyIslandMaterialCandidateRanker.rankCompatible(request, forward),
                SkyIslandMaterialCandidateRanker.rankCompatible(request, reverse));
    }

    @Test
    void requestScopedAffinityIsStableAndCanVaryTiedSemanticPreference() {
        SkyIslandMaterialCapabilityProfile first =
                new SkyIslandMaterialCapabilityProfile(0.90, 0.10, 0.20, 0.30, 0.40);
        SkyIslandMaterialCapabilityProfile second =
                new SkyIslandMaterialCapabilityProfile(0.90, 0.40, 0.30, 0.20, 0.10);

        boolean firstPreferred = false;
        boolean secondPreferred = false;

        for (int anchor = 0; anchor < 64; anchor++) {
            SkyIslandMaterialBindingRequest request =
                    request(
                            anchor,
                            SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                            SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);
            SkyIslandMaterialCandidateRank firstRank =
                    SkyIslandMaterialCandidateRanker.rank(request, first);
            SkyIslandMaterialCandidateRank secondRank =
                    SkyIslandMaterialCandidateRanker.rank(request, second);

            assertEquals(
                    firstRank.minimumRequiredHeadroom(),
                    secondRank.minimumRequiredHeadroom());
            assertEquals(
                    firstRank.meanRequiredHeadroom(),
                    secondRank.meanRequiredHeadroom());
            assertEquals(firstRank.specializationPurity(), secondRank.specializationPurity());
            assertEquals(
                    firstRank.requestAffinity(),
                    SkyIslandMaterialCandidateRanker.rank(request, first).requestAffinity());

            int comparison =
                    SkyIslandMaterialCandidateRanker.compareBestFirst(firstRank, secondRank);
            firstPreferred |= comparison < 0;
            secondPreferred |= comparison > 0;
        }

        assertTrue(firstPreferred);
        assertTrue(secondPreferred);
    }

    @Test
    void differentStableRequestsChangeAffinityWithoutChangingSemanticFit() {
        SkyIslandMaterialBindingRequest firstRequest =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);
        SkyIslandMaterialBindingRequest secondRequest =
                request(
                        11,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);

        SkyIslandMaterialCandidateRank first =
                SkyIslandMaterialCandidateRanker.rank(firstRequest, MATRIX);
        SkyIslandMaterialCandidateRank second =
                SkyIslandMaterialCandidateRanker.rank(secondRequest, MATRIX);

        assertEquals(first.minimumRequiredHeadroom(), second.minimumRequiredHeadroom());
        assertEquals(first.meanRequiredHeadroom(), second.meanRequiredHeadroom());
        assertEquals(first.specializationPurity(), second.specializationPurity());
        assertNotEquals(first.requestAffinity(), second.requestAffinity());
    }

    @Test
    void identicalSemanticProfilesRemainTrueTies() {
        SkyIslandMaterialBindingRequest request =
                request(
                        0,
                        SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                        SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX);
        SkyIslandMaterialCapabilityProfile copy =
                new SkyIslandMaterialCapabilityProfile(0.94, 0.20, 0.20, 0.20, 0.20);

        SkyIslandMaterialCandidateRank first =
                SkyIslandMaterialCandidateRanker.rank(request, MATRIX);
        SkyIslandMaterialCandidateRank second =
                SkyIslandMaterialCandidateRanker.rank(request, copy);

        assertTrue(first.semanticallyTiedWith(second));
        assertEquals(0, SkyIslandMaterialCandidateRanker.compareBestFirst(first, second));
    }

    private static void assertWinner(
            SkyIslandMaterialBindingRequest request,
            SkyIslandMaterialCapabilityProfile expected) {
        List<SkyIslandMaterialCandidateRank> ranks =
                SkyIslandMaterialCandidateRanker.rankCompatible(
                        request,
                        List.of(MATRIX, FABRIC, ALTERATION, WATER, ACCENT, GENERALIST));
        assertFalse(ranks.isEmpty());
        assertEquals(expected, ranks.get(0).profile());
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
