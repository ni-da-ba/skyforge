package io.github.nidaba.skyforge.reference.volume;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProductionMorphologyVisualReviewCorpusTest {

    @Test
    void corpusHasExpectedProductionCoverageWithoutCombinatorialExplosion() {
        List<ProductionMorphologyVisualReviewCorpus.Member> members =
                ProductionMorphologyVisualReviewCorpus.members();

        assertEquals(41, members.size());
        assertEquals(
                41,
                members.stream()
                        .map(ProductionMorphologyVisualReviewCorpus.Member::id)
                        .collect(java.util.stream.Collectors.toSet())
                        .size());

        assertEquals(
                25,
                count(
                        members,
                        ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN));
        assertEquals(
                10,
                count(
                        members,
                        ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN_HYBRID));
        assertEquals(
                1,
                count(
                        members,
                        ProductionMorphologyVisualReviewCorpus.Kind.EXTERNAL_PROVIDER));
        assertEquals(
                5,
                count(
                        members,
                        ProductionMorphologyVisualReviewCorpus.Kind.EXTERNAL_PROVIDER_BLEND));

        assertEquals(
                5,
                members.stream()
                        .filter(
                                member ->
                                        member.scale()
                                                == ProductionMorphologyVisualReviewCorpus.Scale.SMALL)
                        .count());
        assertEquals(
                31,
                members.stream()
                        .filter(
                                member ->
                                        member.scale()
                                                == ProductionMorphologyVisualReviewCorpus.Scale.MEDIUM)
                        .count());
        assertEquals(
                5,
                members.stream()
                        .filter(
                                member ->
                                        member.scale()
                                                == ProductionMorphologyVisualReviewCorpus.Scale.LARGE)
                        .count());
    }

    @Test
    void everyBuiltInFamilyHasThreeSeedsAndThreePhysicalScales() {
        List<ProductionMorphologyVisualReviewCorpus.Member> builtIns =
                ProductionMorphologyVisualReviewCorpus.members().stream()
                        .filter(
                                member ->
                                        member.kind()
                                                == ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN)
                        .toList();

        for (MorphologyFamily family : MorphologyFamily.values()) {
            List<ProductionMorphologyVisualReviewCorpus.Member> familyMembers =
                    builtIns.stream()
                            .filter(member -> member.id().contains("-" + family.identifier() + "-"))
                            .toList();

            assertEquals(5, familyMembers.size(), family.identifier());

            Set<Long> mediumSeeds =
                    familyMembers.stream()
                            .filter(
                                    member ->
                                            member.scale()
                                                    == ProductionMorphologyVisualReviewCorpus.Scale.MEDIUM)
                            .map(ProductionMorphologyVisualReviewCorpus.Member::seed)
                            .collect(java.util.stream.Collectors.toSet());
            assertEquals(
                    Set.of(
                            Long.MIN_VALUE,
                            0L,
                            ProductionMorphologyVisualReviewCorpus.SKYFORGE_SEED),
                    mediumSeeds,
                    family.identifier());

            Set<ProductionMorphologyVisualReviewCorpus.Scale> scales =
                    familyMembers.stream()
                            .map(ProductionMorphologyVisualReviewCorpus.Member::scale)
                            .collect(java.util.stream.Collectors.toSet());
            assertEquals(
                    Set.of(
                            ProductionMorphologyVisualReviewCorpus.Scale.SMALL,
                            ProductionMorphologyVisualReviewCorpus.Scale.MEDIUM,
                            ProductionMorphologyVisualReviewCorpus.Scale.LARGE),
                    scales,
                    family.identifier());
        }
    }

    @Test
    void allTenBuiltInMidpointPairsAndProviderAxisArePresent() {
        List<ProductionMorphologyVisualReviewCorpus.Member> members =
                ProductionMorphologyVisualReviewCorpus.members();

        Set<String> hybridIds =
                members.stream()
                        .filter(
                                member ->
                                        member.kind()
                                                == ProductionMorphologyVisualReviewCorpus.Kind
                                                        .BUILT_IN_HYBRID)
                        .map(ProductionMorphologyVisualReviewCorpus.Member::id)
                        .collect(java.util.stream.Collectors.toSet());

        assertEquals(10, hybridIds.size());
        for (HybridMorphologyReferenceCorpus.Pair pair :
                HybridMorphologyReferenceCorpus.pairs()) {
            assertTrue(
                    hybridIds.contains("hybrid-" + pair.id() + "-midpoint"),
                    pair.id());
        }

        assertTrue(
                members.stream()
                        .anyMatch(
                                member ->
                                        member.id()
                                                .equals(
                                                        "provider-crescent-medium-seed-skyforge")));

        Set<String> providerBlendIds =
                members.stream()
                        .filter(
                                member ->
                                        member.kind()
                                                == ProductionMorphologyVisualReviewCorpus.Kind
                                                        .EXTERNAL_PROVIDER_BLEND)
                        .map(ProductionMorphologyVisualReviewCorpus.Member::id)
                        .collect(java.util.stream.Collectors.toSet());
        for (MorphologyFamily family : MorphologyFamily.values()) {
            assertTrue(
                    providerBlendIds.contains(
                            "provider-crescent-to-"
                                    + family.identifier()
                                    + "-midpoint"));
        }
    }

    @Test
    void everyMemberUsesFullEnrichmentAndCompilesThroughProductionSpecCompiler() {
        for (ProductionMorphologyVisualReviewCorpus.Member member :
                ProductionMorphologyVisualReviewCorpus.members()) {
            assertEquals(1.0, member.morphology().detailAmplitude(), member.id());
            assertEquals(
                    1.0,
                    member.morphology().secondaryMorphologyAmplitude(),
                    member.id());

            var descriptor = ProductionMorphologyVisualReviewCorpus.descriptor(member);
            assertEquals(0.0, descriptor.signalAmplitude(), member.id());
            assertEquals(0.0, descriptor.secondaryMorphologyAmplitude(), member.id());

            var compiled = ProductionMorphologyVisualReviewCorpus.compile(member);
            assertNotNull(compiled.upperSurfaceGraph(), member.id());
            assertNotNull(compiled.undersideSurfaceGraph(), member.id());
            assertNotNull(compiled.densityGraph(), member.id());
        }
    }

    @Test
    void reviewGridScalesWithPhysicalSpecimenAndDoesNotEncodeAestheticThresholds() {
        Set<Double> radii = new HashSet<>();

        for (ProductionMorphologyVisualReviewCorpus.Member member :
                ProductionMorphologyVisualReviewCorpus.members()) {
            var descriptor = ProductionMorphologyVisualReviewCorpus.descriptor(member);
            var grid = ProductionMorphologyVisualReviewCorpus.reviewGrid(member);
            radii.add(descriptor.nominalRadius());

            assertTrue(grid.minimumX() < -descriptor.nominalRadius(), member.id());
            assertTrue(grid.maximumX() > descriptor.nominalRadius(), member.id());
            assertTrue(
                    grid.minimumY() < descriptor.suspensionElevation(),
                    member.id());
            assertTrue(
                    grid.maximumY() > descriptor.suspensionElevation(),
                    member.id());
        }

        assertEquals(3, radii.size());
    }

    private static long count(
            List<ProductionMorphologyVisualReviewCorpus.Member> members,
            ProductionMorphologyVisualReviewCorpus.Kind kind) {
        return members.stream().filter(member -> member.kind() == kind).count();
    }
}
