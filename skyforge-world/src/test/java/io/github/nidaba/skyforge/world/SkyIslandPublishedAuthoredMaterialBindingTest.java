package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedAuthoredMaterialBindingTest {
    private static final long AUTHORED_WORLD = 0x4155544830303837L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void exactPublishedCoverageCreatesTheAcceptedMaterialComposer() {
        SkyIslandCompiledWorldPublication publication = publication(87001L);
        SkyIslandAuthoredRealizationCatalog associations =
                exactAssociations(publication, AUTHORED_WORLD);

        SkyIslandPublishedAuthoredMaterialBinding binding =
                new SkyIslandPublishedAuthoredMaterialBinding(publication, associations);

        assertEquals(publication, binding.publication());
        assertSame(associations, binding.associationCatalog());
        assertEquals(publication.volumeCount(), binding.volumeCount());
        assertEquals(AUTHORED_WORLD, binding.authoredWorldSeed());
        assertNotEquals(AUTHORED_WORLD, publication.catalog().rootSeed());
        assertSame(associations, binding.materialComposer().catalog());
    }

    @Test
    void missingPublishedAssociationFailsClosed() {
        SkyIslandCompiledWorldPublication publication = publication(87002L);
        SkyIslandAuthoredRealizationCatalog missing =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedAuthoredMaterialBinding(publication, missing));
    }

    @Test
    void extraAssociationOutsideThePublicationFailsClosed() {
        SkyIslandCompiledWorldPublication publication = publication(87003L);
        SkyIslandAuthoredRealizationCatalog exact =
                exactAssociations(publication, AUTHORED_WORLD);
        SkyIslandWorldVolume source = publication.catalog().volumes().getFirst();
        SkyIslandWorldVolume extraVolume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                publication.catalog().rootSeed(),
                                "auth87-extra",
                                99,
                                99,
                                source.id().geometrySeed()),
                        source.bounds(),
                        source.compiledVolume());
        SkyIslandAuthoredRealizationAssociation extra =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored(
                                AUTHORED_WORLD,
                                999L,
                                source.compiledVolume().descriptor().nominalRadius()),
                        extraVolume);
        List<SkyIslandAuthoredRealizationAssociation> combined =
                new ArrayList<>(exact.associations());
        combined.add(extra);
        SkyIslandAuthoredRealizationCatalog oversized =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        combined);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedAuthoredMaterialBinding(publication, oversized));
    }

    @Test
    void sameVolumeIdWithSubstitutedPublishedValueFailsClosed() {
        SkyIslandCompiledWorldPublication publication = publication(87004L);
        SkyIslandWorldVolume source = publication.catalog().volumes().getFirst();
        WorldBounds bounds = source.bounds();
        SkyIslandWorldVolume substituted =
                new SkyIslandWorldVolume(
                        source.id(),
                        new WorldBounds(
                                bounds.minimumX() - 1.0,
                                bounds.maximumX(),
                                bounds.minimumY(),
                                bounds.maximumY(),
                                bounds.minimumZ(),
                                bounds.maximumZ()),
                        source.compiledVolume());
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored(
                                AUTHORED_WORLD,
                                1L,
                                source.compiledVolume().descriptor().nominalRadius()),
                        substituted);
        SkyIslandAuthoredRealizationCatalog counterfeit =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        List.of(association));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedAuthoredMaterialBinding(publication, counterfeit));
    }

    @Test
    void realizationRootMismatchFailsBeforeAnyAssociationInference() {
        SkyIslandCompiledWorldPublication publication = publication(87005L);
        SkyIslandWorldVolume source = publication.catalog().volumes().getFirst();
        long foreignRoot = publication.catalog().rootSeed() + 1L;
        SkyIslandWorldVolume foreign =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                foreignRoot,
                                source.id().groupIdentifier(),
                                source.id().groupOrdinal(),
                                source.id().memberOrdinal(),
                                source.id().geometrySeed()),
                        source.bounds(),
                        source.compiledVolume());
        SkyIslandAuthoredRealizationCatalog associations =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        foreignRoot,
                        List.of(SkyIslandAuthoredRealizationAssociation.of(
                                authored(
                                        AUTHORED_WORLD,
                                        1L,
                                        source.compiledVolume().descriptor().nominalRadius()),
                                foreign)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedAuthoredMaterialBinding(publication, associations));
    }

    private static SkyIslandAuthoredRealizationCatalog exactAssociations(
            SkyIslandCompiledWorldPublication publication,
            long authoredWorldSeed) {
        List<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(
                            authoredWorldSeed,
                            100L + ordinal,
                            volume.compiledVolume().descriptor().nominalRadius()),
                    volume));
            ordinal++;
        }
        return new SkyIslandAuthoredRealizationCatalog(
                authoredWorldSeed,
                publication.catalog().rootSeed(),
                associations);
    }

    private static SkyIslandDescriptor authored(
            long worldSeed,
            long islandKey,
            double nominalRadius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(worldSeed, 8L, 87L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                nominalRadius,
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

    private static SkyIslandCompiledWorldPublication publication(long rootSeed) {
        return new SkyIslandCompiledWorldPublisher()
                .publish(acceptedCompilation(rootSeed), 1L);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
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
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0087 test fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth87",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }
}
