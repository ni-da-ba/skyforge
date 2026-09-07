package io.github.nidaba.skyforge.reference.volume;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ProductionMorphologyRegionalReviewCorpusTest {

    @Test
    void corpusHasExactlyTheFiveRequiredIssue214Contexts() {
        assertEquals(
                Set.of("sparse", "chain", "cluster"),
                ProductionMorphologyRegionalReviewCorpus.groupContexts().stream()
                        .map(ProductionMorphologyRegionalReviewCorpus.GroupContext::id)
                        .collect(Collectors.toSet()));
        assertEquals(
                Set.of("hub", "arc"),
                ProductionMorphologyRegionalReviewCorpus.archipelagoContexts().stream()
                        .map(ProductionMorphologyRegionalReviewCorpus.ArchipelagoContext::id)
                        .collect(Collectors.toSet()));

        assertEquals(3, ProductionMorphologyRegionalReviewCorpus.groupContexts().size());
        assertEquals(
                2,
                ProductionMorphologyRegionalReviewCorpus.archipelagoContexts().size());
    }

    @Test
    void acceptedChainClusterHubAndArcRequestsAreReusedExactly() {
        long seed = ProductionMorphologyRegionalReviewCorpus.SKYFORGE_SEED;

        var groups =
                ProductionMorphologyRegionalReviewCorpus.groupContexts().stream()
                        .collect(
                                Collectors.toMap(
                                        ProductionMorphologyRegionalReviewCorpus.GroupContext::id,
                                        ProductionMorphologyRegionalReviewCorpus.GroupContext::request));
        assertEquals(SkyIslandGroupReferenceCorpus.chain(seed), groups.get("chain"));
        assertEquals(SkyIslandGroupReferenceCorpus.cluster(seed), groups.get("cluster"));

        var archipelagos =
                ProductionMorphologyRegionalReviewCorpus.archipelagoContexts().stream()
                        .collect(
                                Collectors.toMap(
                                        ProductionMorphologyRegionalReviewCorpus.ArchipelagoContext::id,
                                        ProductionMorphologyRegionalReviewCorpus.ArchipelagoContext::request));
        assertEquals(SkyIslandArchipelagoReferenceCorpus.hub(seed), archipelagos.get("hub"));
        assertEquals(SkyIslandArchipelagoReferenceCorpus.arc(seed), archipelagos.get("arc"));
    }

    @Test
    void sparseContextUsesAcceptedPlannerWithLowDensityMixedMorphologyIntent() {
        var sparse =
                ProductionMorphologyRegionalReviewCorpus.sparse(
                        ProductionMorphologyRegionalReviewCorpus.SKYFORGE_SEED);

        assertEquals(5, sparse.memberCount());
        assertTrue(
                sparse.layout().minimumCenterSpacing() >= sparse.requiredCenterSpacing());
        assertTrue(
                sparse.minimumGap() > SkyIslandGroupReferenceCorpus.MINIMUM_GAP);
        assertTrue(
                sparse.layout().minimumCenterSpacing()
                        > SkyIslandGroupReferenceCorpus.chain(
                                        ProductionMorphologyRegionalReviewCorpus.SKYFORGE_SEED)
                                .layout()
                                .minimumCenterSpacing());

        assertEquals(
                5,
                sparse.memberMorphologies().stream()
                        .map(morphology -> morphology.stableIdentifier())
                        .collect(Collectors.toSet())
                        .size());

        for (var morphology : sparse.memberMorphologies()) {
            assertEquals(1.0, morphology.detailAmplitude());
            assertEquals(1.0, morphology.secondaryMorphologyAmplitude());
        }
    }

    @Test
    void groupAndArchipelagoPlanningAreDeterministic() {
        var sparse =
                ProductionMorphologyRegionalReviewCorpus.sparse(
                        ProductionMorphologyRegionalReviewCorpus.SKYFORGE_SEED);
        var groupPlanner = new SkyIslandGroupPlanner();
        assertEquals(groupPlanner.plan(sparse), groupPlanner.plan(sparse));

        var hub =
                SkyIslandArchipelagoReferenceCorpus.hub(
                        ProductionMorphologyRegionalReviewCorpus.SKYFORGE_SEED);
        var archipelagoPlanner = new SkyIslandArchipelagoPlanner();
        assertEquals(archipelagoPlanner.plan(hub), archipelagoPlanner.plan(hub));
    }
}
