package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandCommunityAssemblyEvidenceTest {
    private static final long AUTHORED_WORLD = 0x415353454D424C59L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void binderPreservesExactAssociationAndRawNearestNeighborEvidence() {
        Fixture fixture = fixture(120001L, 3);
        SkyIslandAuthoredRealizationIsolationProfile profile =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry source = profile.islands().getFirst();

        SkyIslandCommunitySuitabilityFieldSet communities =
                SkyIslandCommunitySuitabilityFieldSet.create(
                        source.association().authoredDescriptor());
        SkyIslandCommunityAssemblyEvidence evidence =
                new SkyIslandCommunityAssemblyEvidenceBinder().bind(communities, profile);

        assertEquals(source.association(), evidence.association());
        assertEquals(source.association().authoredIdentity(), evidence.authoredIdentity());
        assertEquals(source.association().realizedVolumeId(), evidence.realizedVolumeId());
        assertTrue(evidence.hasNeighborEvidence());
        assertEquals(source.nearestNeighbor(), evidence.nearestNeighbor());
        assertEquals(
                Double.doubleToLongBits(
                        source.nearestNeighbor().orElseThrow().centerDistance()),
                Double.doubleToLongBits(
                        evidence.nearestCenterDistance().orElseThrow()));
        assertEquals(
                Double.doubleToLongBits(
                        source.nearestNeighbor().orElseThrow().nominalRadialGap()),
                Double.doubleToLongBits(
                        evidence.nearestNominalRadialGap().orElseThrow()));
    }

    @Test
    void singletonCatalogPreservesAbsenceOfNeighborEvidence() {
        Fixture fixture = fixture(120002L, 1);
        SkyIslandAuthoredRealizationIsolationProfile profile =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry source = profile.islands().getFirst();

        SkyIslandCommunityAssemblyEvidence evidence =
                new SkyIslandCommunityAssemblyEvidenceBinder().bind(
                        SkyIslandCommunitySuitabilityFieldSet.create(
                                source.association().authoredDescriptor()),
                        profile);

        assertFalse(evidence.hasNeighborEvidence());
        assertTrue(evidence.nearestNeighbor().isEmpty());
        assertTrue(evidence.nearestCenterDistance().isEmpty());
        assertTrue(evidence.nearestNominalRadialGap().isEmpty());
    }

    @Test
    void evidenceRejectsMismatchedCommunityDescriptor() {
        Fixture fixture = fixture(120003L, 2);
        SkyIslandAuthoredRealizationIsolationProfile profile =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry first = profile.islands().get(0);
        SkyIslandRegionalIsolationEntry second = profile.islands().get(1);

        SkyIslandCommunitySuitabilityFieldSet wrong =
                SkyIslandCommunitySuitabilityFieldSet.create(
                        second.association().authoredDescriptor());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityAssemblyEvidence(wrong, first));
    }

    @Test
    void binderRejectsCommunityDescriptorAbsentFromIsolationProfile() {
        Fixture fixture = fixture(120004L, 2);
        SkyIslandAuthoredRealizationIsolationProfile profile =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandDescriptor absent = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 12L, 120L, 999_999L));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityAssemblyEvidenceBinder().bind(
                        SkyIslandCommunitySuitabilityFieldSet.create(absent),
                        profile));
    }

    @Test
    void hydrologicBinderPreservesExactAssemblyAssociationAndAuth0096Cells() {
        Fixture fixture = fixture(120006L, 2);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry source = isolation.islands().getFirst();
        SkyIslandCommunityAssemblyEvidence assembly =
                new SkyIslandCommunityAssemblyEvidenceBinder().bind(
                        SkyIslandCommunitySuitabilityFieldSet.create(
                                source.association().authoredDescriptor()),
                        isolation);
        SkyIslandSurfaceSiteCapabilityProfile surface =
                new SkyIslandSurfaceSiteCapabilityProfiler().profile(source.association());

        SkyIslandCommunityHydrologicEvidence evidence =
                new SkyIslandCommunityHydrologicEvidenceBinder().bind(
                        assembly,
                        surface);

        assertEquals(assembly, evidence.assemblyEvidence());
        assertEquals(surface, evidence.surfaceSiteProfile());
        assertEquals(source.association(), evidence.association());
        assertEquals(surface.cells().size(), evidence.surfaceSiteProfile().cells().size());

        for (int index = 0; index < surface.cells().size(); index++) {
            SkyIslandSurfaceSiteCapabilityCell sourceCell = surface.cells().get(index);
            assertEquals(sourceCell, evidence.cell(index));
            assertEquals(
                    sourceCell,
                    evidence.cellAt(sourceCell.position()).orElseThrow());
        }
    }

    @Test
    void hydrologicBinderRejectsForeignAssociationEvidence() {
        Fixture fixture = fixture(120007L, 2);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry first = isolation.islands().get(0);
        SkyIslandRegionalIsolationEntry second = isolation.islands().get(1);
        SkyIslandCommunityAssemblyEvidence assembly =
                new SkyIslandCommunityAssemblyEvidenceBinder().bind(
                        SkyIslandCommunitySuitabilityFieldSet.create(
                                first.association().authoredDescriptor()),
                        isolation);
        SkyIslandSurfaceSiteCapabilityProfile foreignSurface =
                new SkyIslandSurfaceSiteCapabilityProfiler().profile(second.association());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityHydrologicEvidenceBinder().bind(
                        assembly,
                        foreignSurface));
    }

    @Test
    void hydrologicEvidenceExactLookupDoesNotInterpolateOrUseNearestCell() {
        Fixture fixture = fixture(120008L, 1);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());
        SkyIslandRegionalIsolationEntry source = isolation.islands().getFirst();
        SkyIslandCommunityAssemblyEvidence assembly =
                new SkyIslandCommunityAssemblyEvidenceBinder().bind(
                        SkyIslandCommunitySuitabilityFieldSet.create(
                                source.association().authoredDescriptor()),
                        isolation);
        SkyIslandSurfaceSiteCapabilityProfile surface =
                new SkyIslandSurfaceSiteCapabilityProfiler().profile(source.association());
        SkyIslandCommunityHydrologicEvidence evidence =
                new SkyIslandCommunityHydrologicEvidenceBinder().bind(
                        assembly,
                        surface);
        SkyIslandSurfaceSiteCapabilityCell cell = surface.cells().get(surface.cells().size() / 2);
        SkyIslandLocalPosition nearby =
                new SkyIslandLocalPosition(
                        cell.position().x() + 1.0e-9,
                        cell.position().z());

        assertTrue(evidence.cellAt(cell.position()).isPresent());
        assertTrue(evidence.cellAt(nearby).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> evidence.cell(-1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> evidence.cell(surface.cells().size()));
    }

    @Test
    void currentSemanticsBindingPreservesCanonicalIsolationOrder() {
        Fixture fixture = fixture(120005L, 3);
        SkyIslandAuthoredRealizationIsolationProfile profile =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(fixture.catalog());

        List<SkyIslandCommunityAssemblyEvidence> evidence =
                new SkyIslandCommunityAssemblyEvidenceBinder()
                        .bindCurrentSemantics(profile);

        assertEquals(profile.islandCount(), evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            SkyIslandRegionalIsolationEntry source = profile.islands().get(index);
            SkyIslandCommunityAssemblyEvidence bound = evidence.get(index);
            assertEquals(source, bound.isolationEntry());
            assertEquals(
                    source.association().authoredDescriptor(),
                    bound.communitySuitability().descriptor());
        }
    }

    private static Fixture fixture(long rootSeed, int memberCount) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed, memberCount), 1L);

        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(40_000L + ordinal, volume),
                    volume));
            ordinal++;
        }

        Collections.reverse(associations);
        return new Fixture(new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD,
                publication.catalog().rootSeed(),
                associations));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 12L, 120L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        var morphology = realized.hasSemanticMorphologyFamily()
                ? realized.morphologyFamily()
                : base.morphologyFamily();
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                morphology,
                realized.nominalRadius(),
                base.reliefBudget(),
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            int memberCount) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, memberCount, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor()
                        .executeOnce(proposal, registry);
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException(
                    "community assembly evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "assembly",
                        SkyIslandGroupRole.ANCHOR,
                        volumeDescriptor(),
                        360.0,
                        48.0,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor volumeDescriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0,
                48.0,
                64.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0);
    }

    private record Fixture(SkyIslandAuthoredRealizationCatalog catalog) {}
}
