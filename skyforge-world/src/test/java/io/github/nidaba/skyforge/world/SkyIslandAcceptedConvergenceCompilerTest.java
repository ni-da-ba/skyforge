package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SkyIslandAcceptedConvergenceCompilerTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void acceptedConvergenceCompilesExactFreshPlanOnce() {
        MorphologyProviderId id =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProvider delegate =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);
        AtomicInteger primaryCompiles = new AtomicInteger();
        SkyIslandMorphologyProvider counting =
                countingProvider(delegate, primaryCompiles);
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(counting)
                        .build();

        SkyIslandSupportConvergenceReport convergence =
                acceptedConvergence(
                        57001L,
                        new ProviderMorphologySpec(id, 1.0, 1.0),
                        registry,
                        360.0,
                        440.0);

        assertEquals(0, primaryCompiles.get());

        SkyIslandAcceptedConvergenceCompilation compilation =
                new SkyIslandAcceptedConvergenceCompiler()
                        .compileOnce(convergence, registry);

        assertEquals(1, primaryCompiles.get());
        assertEquals(convergence, compilation.convergence());
        assertEquals(
                convergence.freshPreflight().orElseThrow(),
                compilation.reproducedPreflight());
        assertTrue(compilation.supportBundle().fullyCertified());
        assertEquals(1, compilation.compiledVolumeCount());
        assertEquals(1, compilation.certifiedVolumeCount());

        SkyIslandArchipelagoPlan fresh = convergence.freshPlan().orElseThrow();
        SkyIslandWorldVolume volume =
                compilation.supportBundle().catalog().volumes().get(0);
        assertEquals(fresh.rootSeed(), volume.id().archipelagoRootSeed());
        assertEquals(
                fresh.groups().get(0).identifier(),
                volume.id().groupIdentifier());
        assertEquals(
                fresh.groups().get(0).groupPlan().members().get(0).descriptor().seed(),
                volume.id().geometrySeed());
        assertTrue(compilation.supportBundle().certificateFor(volume).isPresent());
    }

    @Test
    void nonAcceptedConvergenceIsRejectedBeforePrimaryCompilation() {
        MorphologyProviderId id =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProvider delegate =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);
        AtomicInteger primaryCompiles = new AtomicInteger();
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(countingProvider(delegate, primaryCompiles))
                        .build();

        SkyIslandSupportConvergenceReport rejected =
                convergence(
                        twoRequest(
                                57002L,
                                new ProviderMorphologySpec(id, 1.0, 1.0),
                                120.0,
                                280.0,
                                260.0),
                        registry,
                        SkyIslandSupportReplanMargin.ZERO);

        assertEquals(
                SkyIslandSupportConvergenceOutcome.PLANNER_REJECTED,
                rejected.outcome());
        assertEquals(0, primaryCompiles.get());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandAcceptedConvergenceCompiler()
                                .compileOnce(rejected, registry));
        assertEquals(0, primaryCompiles.get());
    }

    @Test
    void suppliedRegistryMustReproduceAcceptedFreshPreflight() {
        MorphologyProviderId id =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProvider original =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProviderRegistry originalRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(original)
                        .build();
        SkyIslandSupportConvergenceReport convergence =
                acceptedConvergence(
                        57003L,
                        new ProviderMorphologySpec(id, 1.0, 1.0),
                        originalRegistry,
                        360.0,
                        440.0);

        AtomicInteger primaryCompiles = new AtomicInteger();
        SkyIslandMorphologyProvider changed =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return id;
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor descriptor) {
                        primaryCompiles.incrementAndGet();
                        return original.compilePrimary(descriptor);
                    }

                    @Override
                    public Optional<PrimaryMorphologySupportEnvelope>
                            certifiedPrimarySupportEnvelope(
                                    SkyIslandVolumeDescriptor descriptor) {
                        PrimaryMorphologySupportEnvelope base =
                                original.certifiedPrimarySupportEnvelope(descriptor)
                                        .orElseThrow();
                        return Optional.of(
                                new PrimaryMorphologySupportEnvelope(
                                        Math.nextDown(
                                                base.maximumHorizontalRadius() * 0.95),
                                        base.maximumUpperOffset(),
                                        base.maximumUndersideDepth()));
                    }

                    @Override
                    public Optional<SecondaryMorphologyContribution>
                            compileSecondaryMorphology(
                                    SkyIslandVolumeDescriptor descriptor,
                                    double amplitude) {
                        return original.compileSecondaryMorphology(descriptor, amplitude);
                    }
                };
        SkyIslandMorphologyProviderRegistry changedRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(changed)
                        .build();

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SkyIslandAcceptedConvergenceCompiler()
                                        .compileOnce(convergence, changedRegistry));
        assertTrue(failure.getMessage().contains("does not reproduce"));
        assertEquals(0, primaryCompiles.get());
    }

    @Test
    void primaryCompilationFailureAfterReproducedPreflightIsExplicit() {
        MorphologyProviderId id =
                new MorphologyProviderId("test", "auth57-primary-failure");
        SkyIslandMorphologyProvider failing =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return id;
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor descriptor) {
                        throw new IllegalStateException("intentional AUTH-0057 primary failure");
                    }

                    @Override
                    public Optional<PrimaryMorphologySupportEnvelope>
                            certifiedPrimarySupportEnvelope(
                                    SkyIslandVolumeDescriptor descriptor) {
                        return Optional.of(
                                new PrimaryMorphologySupportEnvelope(
                                        120.0, 50.0, 50.0));
                    }
                };
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(failing)
                        .build();
        SkyIslandSupportConvergenceReport convergence =
                acceptedConvergence(
                        57004L,
                        new ProviderMorphologySpec(id, 0.0, 0.0),
                        registry,
                        200.0,
                        240.0);

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SkyIslandAcceptedConvergenceCompiler()
                                        .compileOnce(convergence, registry));
        assertTrue(
                failure.getMessage()
                        .contains("failed after accepted preflight reproduced"));
        assertNotNull(failure.getCause());
        assertEquals(
                "intentional AUTH-0057 primary failure",
                failure.getCause().getMessage());
    }

    @Test
    void repeatedExplicitHandoffsAreDeterministicAndEachCompileOnce() {
        MorphologyProviderId id =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandMorphologyProvider delegate =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.BASIN);
        AtomicInteger primaryCompiles = new AtomicInteger();
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(countingProvider(delegate, primaryCompiles))
                        .build();
        SkyIslandSupportConvergenceReport convergence =
                acceptedConvergence(
                        57005L,
                        new ProviderMorphologySpec(id, 1.0, 1.0),
                        registry,
                        360.0,
                        440.0);
        var compiler = new SkyIslandAcceptedConvergenceCompiler();

        SkyIslandAcceptedConvergenceCompilation first =
                compiler.compileOnce(convergence, registry);
        assertEquals(1, primaryCompiles.get());
        SkyIslandAcceptedConvergenceCompilation second =
                compiler.compileOnce(convergence, registry);
        assertEquals(2, primaryCompiles.get());

        assertEquals(
                first.supportBundle().catalog().volumes(),
                second.supportBundle().catalog().volumes());
        assertEquals(
                first.supportBundle().certificates(),
                second.supportBundle().certificates());
        assertEquals(first.reproducedPreflight(), second.reproducedPreflight());
    }

    private static SkyIslandMorphologyProvider countingProvider(
            SkyIslandMorphologyProvider delegate,
            AtomicInteger primaryCompiles) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return delegate.id();
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                primaryCompiles.incrementAndGet();
                return delegate.compilePrimary(descriptor);
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                return delegate.certifiedPrimarySupportEnvelope(descriptor);
            }

            @Override
            public Optional<SecondaryMorphologyContribution>
                    compileSecondaryMorphology(
                            SkyIslandVolumeDescriptor descriptor, double amplitude) {
                return delegate.compileSecondaryMorphology(descriptor, amplitude);
            }
        };
    }

    private static SkyIslandSupportConvergenceReport acceptedConvergence(
            long rootSeed,
            ProviderMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry,
            double horizontal,
            double groupRadius) {
        SkyIslandSupportConvergenceReport result =
                convergence(
                        singleRequest(rootSeed, morphology, horizontal, groupRadius),
                        registry,
                        SkyIslandSupportReplanMargin.ZERO);
        assertEquals(SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS, result.outcome());
        return result;
    }

    private static SkyIslandSupportConvergenceReport convergence(
            SkyIslandArchipelagoRequest request,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandSupportReplanMargin margin) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(request, plan, synthesis, ADEQUATE_VERTICAL, margin);
        return new SkyIslandSupportConvergenceExecutor()
                .executeOnce(proposal, registry);
    }

    private static SkyIslandArchipelagoRequest singleRequest(
            long rootSeed,
            ProviderMorphologySpec morphology,
            double horizontal,
            double groupRadius) {
        return request(
                rootSeed,
                List.of(morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                0.0);
    }

    private static SkyIslandArchipelagoRequest twoRequest(
            long rootSeed,
            ProviderMorphologySpec morphology,
            double horizontal,
            double groupRadius,
            double spacing) {
        return request(
                rootSeed,
                List.of(morphology, morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(spacing, 0.0, 0.0, 0.0),
                20.0);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            List<SkyIslandMorphologySpec> morphologies,
            double horizontal,
            double groupRadius,
            SkyIslandGroupLayout layout,
            double minimumGap) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth57",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        horizontal,
                        minimumGap,
                        0.0,
                        morphologies,
                        layout,
                        groupRadius);
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
