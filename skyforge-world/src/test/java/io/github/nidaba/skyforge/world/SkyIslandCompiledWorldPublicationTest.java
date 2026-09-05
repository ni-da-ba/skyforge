package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandCompiledWorldPublicationTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void publishesExactAcceptedCompilationAsBackendNeutralCapability() {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(58001L);

        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(compilation, 7L);

        assertEquals(7L, publication.id().publicationRevision());
        assertEquals(
                compilation.supportBundle().catalog().rootSeed(),
                publication.id().archipelagoRootSeed());
        assertEquals(compilation, publication.compilation());
        assertEquals(compilation.convergence(), publication.acceptedConvergence());
        assertEquals(
                compilation.convergence().freshPlan().orElseThrow(),
                publication.acceptedPlan());
        assertEquals(compilation.supportBundle().catalog(), publication.catalog());
        assertEquals(
                compilation.supportBundle().certificates(),
                publication.supportCertificates());
        assertEquals(
                publication.catalog().volumes().stream()
                        .map(SkyIslandWorldVolume::id)
                        .toList(),
                publication.catalogIdentity());
        assertEquals(publication.catalog().volumeCount(), publication.volumeCount());
        assertTrue(publication.compilation().supportBundle().fullyCertified());
    }

    @Test
    void publicationIdentityHasStableExplicitVersionAxis() {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(58002L);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();

        SkyIslandCompiledWorldPublication first = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication repeated = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication revised = publisher.publish(compilation, 2L);

        assertEquals(first.id(), repeated.id());
        assertEquals(first.id().canonicalToken(), repeated.id().canonicalToken());
        assertNotEquals(first.id(), revised.id());
        assertNotEquals(first.id().canonicalToken(), revised.id().canonicalToken());
        assertEquals(first.catalogIdentity(), revised.catalogIdentity());
        assertEquals(first.supportCertificates(), revised.supportCertificates());
    }

    @Test
    void sameRevisionInDifferentRegionalRootIsDifferentPublicationIdentity() {
        SkyIslandCompiledWorldPublication first =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(58003L), 4L);
        SkyIslandCompiledWorldPublication second =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(58004L), 4L);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.id().canonicalToken(), second.id().canonicalToken());
        assertNotEquals(first.catalogIdentity(), second.catalogIdentity());
    }

    @Test
    void identityValidationRejectsUnsupportedSchemaRevisionAndRootDrift() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCompiledWorldPublicationId(2, 1L, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCompiledWorldPublicationId(
                        SkyIslandCompiledWorldPublicationId.SCHEMA_VERSION, 1L, 0L));

        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(58005L);
        long root = compilation.supportBundle().catalog().rootSeed();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandCompiledWorldPublication(
                                SkyIslandCompiledWorldPublicationId.of(root + 1L, 1L),
                                compilation));
    }

    @Test
    void publisherExposesNoRawCatalogOrSupportBundlePublicationOverload() {
        Method[] publicDeclared = Arrays.stream(SkyIslandCompiledWorldPublisher.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertEquals(1, publicDeclared.length);
        Method publish = publicDeclared[0];
        assertEquals("publish", publish.getName());
        assertEquals(
                List.of(
                        SkyIslandAcceptedConvergenceCompilation.class,
                        long.class),
                List.of(publish.getParameterTypes()));
        assertEquals(SkyIslandCompiledWorldPublication.class, publish.getReturnType());
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
                new SkyIslandSupportConvergenceExecutor()
                        .executeOnce(proposal, registry);
        assertEquals(
                SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS,
                convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth58",
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
