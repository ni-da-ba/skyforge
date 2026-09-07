package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandSupportReservationRequirementSynthesizerTest {
    private static final double ADEQUATE_HORIZONTAL = 360.0;
    private static final double ADEQUATE_GROUP = 440.0;

    @Test
    void completeBuiltInSynthesisProducesAdmissionSafeRequirements() {
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54001L,
                        direct(MorphologyFamily.MASSIF),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP,
                        0.0,
                        0.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);

        assertTrue(synthesis.fullySynthesized());
        assertEquals(0, synthesis.uncertifiedMemberCount());
        assertFalse(synthesis.requiresFreshReplan());
        assertTrue(synthesis.requiredBelowSuspension().isPresent());
        assertTrue(synthesis.requiredAboveSuspension().isPresent());
        assertTrue(synthesis.synthesizedVerticalReservation().isPresent());

        var member = synthesis.memberRequirements().get(0);
        var group = synthesis.groupRequirements().get(0);
        assertTrue(member.certified());
        assertTrue(member.currentHorizontalReservationAdequate());
        assertTrue(group.currentMemberHorizontalReservationAdequate());
        assertTrue(group.currentGroupReservationAdequate());

        var preflight =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                plan,
                                registry,
                                synthesis.synthesizedVerticalReservation().orElseThrow());
        assertTrue(preflight.admitted());
    }

    @Test
    void undersizedPlanSynthesizesLargerValuesWithoutMutatingPlan() {
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54002L,
                        direct(MorphologyFamily.MASSIF),
                        256.0,
                        280.0,
                        0.0,
                        0.0);
        var before = plan;
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        var member = synthesis.memberRequirements().get(0);
        var group = synthesis.groupRequirements().get(0);

        assertEquals(before, plan);
        assertTrue(member.requiredHorizontalRadius().orElseThrow() > 256.0);
        assertTrue(group.requiredMemberHorizontalRadius().orElseThrow() > 256.0);
        assertTrue(group.exactPlanRequiredGroupRadius().orElseThrow() > 280.0);
        assertTrue(group.requiresFreshReplan());
        assertTrue(synthesis.requiresFreshReplan());
    }

    @Test
    void incompleteInteriorBlendDoesNotInventGlobalOrGroupRequirements() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandMorphologySpec interior =
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(massif, basin, 0.35));
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54003L,
                        interior,
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP,
                        0.0,
                        0.0);

        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        assertFalse(synthesis.fullySynthesized());
        assertEquals(1, synthesis.uncertifiedMemberCount());
        assertTrue(synthesis.requiredBelowSuspension().isEmpty());
        assertTrue(synthesis.requiredAboveSuspension().isEmpty());
        assertTrue(synthesis.synthesizedVerticalReservation().isEmpty());
        assertTrue(
                synthesis.memberRequirements().get(0).requiredHorizontalRadius().isEmpty());
        assertTrue(
                synthesis.groupRequirements().get(0).requiredMemberHorizontalRadius().isEmpty());
        assertTrue(
                synthesis.groupRequirements().get(0).exactPlanRequiredGroupRadius().isEmpty());
    }

    @Test
    void exactDerivedSeedChangesSynthesizedRequirementForSeedAwareProvider() {
        MorphologyProviderId id =
                new MorphologyProviderId("test", "auth54-seed-aware");
        SkyIslandMorphologySpec morphology = ProviderMorphologySpec.full(id);

        SkyIslandArchipelagoPlan first =
                singleMemberPlan(54101L, morphology, 400.0, 440.0, 0.0, 0.0);
        SkyIslandArchipelagoPlan second =
                singleMemberPlan(54102L, morphology, 400.0, 440.0, 0.0, 0.0);
        long firstSeed =
                first.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        long secondSeed =
                second.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        assertNotEquals(firstSeed, secondSeed);

        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(seedAwareProvider(id, firstSeed))
                        .build();
        var synthesizer = new SkyIslandSupportReservationRequirementSynthesizer();

        var firstRequirement =
                synthesizer.synthesize(first, registry).memberRequirements().get(0);
        var secondRequirement =
                synthesizer.synthesize(second, registry).memberRequirements().get(0);

        assertEquals(firstSeed, firstRequirement.descriptorSeed());
        assertEquals(secondSeed, secondRequirement.descriptorSeed());
        assertTrue(
                firstRequirement.requiredHorizontalRadius().orElseThrow()
                        > secondRequirement.requiredHorizontalRadius().orElseThrow());
        assertTrue(
                firstRequirement.requiredHorizontalRadius().orElseThrow() >= 300.0);
        assertTrue(
                secondRequirement.requiredHorizontalRadius().orElseThrow() >= 120.0);
    }

    @Test
    void synthesisDoesNotCompileProviderPrimaryMorphology() {
        MorphologyProviderId id = new MorphologyProviderId("test", "auth54-no-graph");
        SkyIslandMorphologyProvider provider =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return id;
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor descriptor) {
                        throw new AssertionError(
                                "AUTH-0054 synthesis must not compile primary morphology");
                    }

                    @Override
                    public Optional<PrimaryMorphologySupportEnvelope>
                            certifiedPrimarySupportEnvelope(
                                    SkyIslandVolumeDescriptor descriptor) {
                        return Optional.of(
                                new PrimaryMorphologySupportEnvelope(
                                        150.0, 60.0, 70.0));
                    }
                };
        var registry =
                SkyIslandMorphologyProviderRegistry.builder().register(provider).build();
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54005L,
                        ProviderMorphologySpec.full(id),
                        200.0,
                        240.0,
                        0.0,
                        0.0);

        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);

        assertTrue(synthesis.fullySynthesized());
        assertTrue(
                synthesis.memberRequirements().get(0).requiredHorizontalRadius().isPresent());
    }

    @Test
    void largeWorldCoordinatesStillSynthesizeValuesAcceptedByAuth0053() {
        double world = 1.0e15;
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54006L,
                        direct(MorphologyFamily.TABLELAND),
                        400.0,
                        440.0,
                        world,
                        -world);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);

        var vertical = synthesis.synthesizedVerticalReservation().orElseThrow();
        SkyIslandArchipelagoPlan memberPlan = plan;
        var preflight =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(memberPlan, registry, vertical);

        assertTrue(preflight.memberChecks().get(0).verticalReservationAdequate());
        assertTrue(preflight.memberChecks().get(0).horizontalReservationAdequate());
    }

    @Test
    void exactPlanGroupRadiusMustBeResynthesizedAfterFreshReplan() {
        SkyIslandMorphologySpec morphology = direct(MorphologyFamily.MASSIF);
        SkyIslandArchipelagoPlan original =
                twoMemberPlan(54007L, morphology, 120.0, 280.0, 260.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesizer = new SkyIslandSupportReservationRequirementSynthesizer();
        var originalSynthesis = synthesizer.synthesize(original, registry);

        double originalExactGroup =
                originalSynthesis
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();

        SkyIslandArchipelagoPlan fresh =
                twoMemberPlan(54007L, morphology, 360.0, 900.0, 820.0);
        var freshSynthesis = synthesizer.synthesize(fresh, registry);
        double freshExactGroup =
                freshSynthesis
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow();

        assertNotEquals(
                original.groups().get(0).groupPlan().minimumObservedCenterSpacing(),
                fresh.groups().get(0).groupPlan().minimumObservedCenterSpacing());
        assertNotEquals(originalExactGroup, freshExactGroup);
    }

    @Test
    void synthesisIsDeterministic() {
        SkyIslandArchipelagoPlan plan =
                singleMemberPlan(
                        54008L,
                        direct(MorphologyFamily.BASIN),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP,
                        0.0,
                        0.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesizer = new SkyIslandSupportReservationRequirementSynthesizer();

        assertEquals(
                synthesizer.synthesize(plan, registry),
                synthesizer.synthesize(plan, registry));
    }

    private static SkyIslandMorphologyProvider seedAwareProvider(
            MorphologyProviderId id, long largeSeed) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                throw new AssertionError("AUTH-0054 must not compile primary morphology");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(
                                descriptor.seed() == largeSeed ? 300.0 : 120.0,
                                50.0,
                                50.0));
            }
        };
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
    }

    private static SkyIslandArchipelagoPlan singleMemberPlan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup,
            double centerX,
            double centerZ) {
        SkyIslandVolumeDescriptor descriptor = descriptor(centerX, centerZ);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth54",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontal,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                Math.max(800.0, 2.0 * reservedHorizontal),
                                0.0,
                                0.0,
                                0.0),
                        reservedGroup);
        return new SkyIslandArchipelagoPlanner()
                .plan(
                        new SkyIslandArchipelagoRequest(
                                rootSeed,
                                centerX,
                                centerZ,
                                320.0,
                                500.0,
                                List.of(template),
                                new SkyIslandArchipelagoLayout.Hub(
                                        1_600.0, 0.0, 0.0, 0.0, 0.0)));
    }

    private static SkyIslandArchipelagoPlan twoMemberPlan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup,
            double minimumCenterSpacing) {
        SkyIslandVolumeDescriptor descriptor = descriptor(0.0, 0.0);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth54-two",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontal,
                        20.0,
                        0.0,
                        List.of(morphology, morphology),
                        new SkyIslandGroupLayout.Cluster(
                                minimumCenterSpacing,
                                0.0,
                                0.0,
                                0.0),
                        reservedGroup);
        return new SkyIslandArchipelagoPlanner()
                .plan(
                        new SkyIslandArchipelagoRequest(
                                rootSeed,
                                0.0,
                                0.0,
                                320.0,
                                500.0,
                                List.of(template),
                                new SkyIslandArchipelagoLayout.Hub(
                                        1_600.0, 0.0, 0.0, 0.0, 0.0)));
    }

    private static SkyIslandVolumeDescriptor descriptor(double centerX, double centerZ) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                centerX,
                centerZ,
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
