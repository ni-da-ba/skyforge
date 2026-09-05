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

final class SkyIslandSupportReservationPreflightTest {
    private static final double ADEQUATE_HORIZONTAL = 360.0;
    private static final double ADEQUATE_GROUP_RADIUS = 440.0;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void fullyReservedBuiltInPlanIsAdmittedBeforeWorldCompilation() {
        SkyIslandArchipelagoPlan plan =
                plan(
                        53001L,
                        direct(MorphologyFamily.MASSIF),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP_RADIUS);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var compiler = new SkyIslandWorldCatalogCompiler();

        SkyIslandSupportReservationPreflightReport report =
                compiler.preflightSupportReservations(
                        plan, registry, ADEQUATE_VERTICAL);

        assertTrue(report.admitted());
        assertEquals(0, report.uncertifiedMemberCount());
        assertEquals(0, report.undersizedMemberHorizontalCount());
        assertEquals(0, report.undersizedVerticalCount());
        assertEquals(0, report.undersizedGroupCount());
        assertFalse(report.consumedReservationDefect());
        assertTrue(report.memberChecks().get(0).certified());
        assertTrue(report.memberChecks().get(0).admitted());
        assertTrue(report.groupChecks().get(0).admitted());

        SkyIslandWorldCatalogSupportBundle bundle =
                compiler.compileProofBacked(
                        plan, registry, ADEQUATE_VERTICAL);
        assertTrue(bundle.fullyCertified());
    }

    @Test
    void undersizedMemberAndGroupReservationsAreReportedWithoutMutation() {
        SkyIslandArchipelagoPlan plan =
                plan(
                        53002L,
                        direct(MorphologyFamily.MASSIF),
                        256.0,
                        280.0);
        var report =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry(),
                                ADEQUATE_VERTICAL);

        SkyIslandSupportReservationMemberCheck member =
                report.memberChecks().get(0);
        SkyIslandSupportReservationGroupCheck group =
                report.groupChecks().get(0);

        assertTrue(member.certified());
        assertTrue(member.requiredHorizontalRadius() > member.reservedHorizontalRadius());
        assertFalse(member.horizontalReservationAdequate());
        assertTrue(group.requiredGroupRadius().isPresent());
        assertTrue(group.requiredGroupRadius().orElseThrow() > group.reservedGroupRadius());
        assertFalse(group.groupReservationAdequate());
        assertFalse(report.admitted());
        assertEquals(1, report.undersizedMemberHorizontalCount());
        assertEquals(1, report.undersizedGroupCount());
        assertTrue(report.consumedReservationDefect());

        assertEquals(256.0, member.reservedHorizontalRadius());
        assertEquals(280.0, group.reservedGroupRadius());
    }

    @Test
    void undersizedVerticalReservationIsRejectedSeparately() {
        SkyIslandArchipelagoPlan plan =
                plan(
                        53003L,
                        direct(MorphologyFamily.SPINE),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP_RADIUS);
        SkyIslandWorldVerticalReservation small =
                new SkyIslandWorldVerticalReservation(180.0, 140.0);

        var report =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry(),
                                small);

        assertTrue(report.memberChecks().get(0).certified());
        assertTrue(report.memberChecks().get(0).horizontalReservationAdequate());
        assertFalse(report.memberChecks().get(0).verticalReservationAdequate());
        assertEquals(1, report.undersizedVerticalCount());
        assertFalse(report.admitted());
        assertTrue(report.consumedReservationDefect());
    }

    @Test
    void interiorBlendRemainsUncertifiedRatherThanInventingReservationRequirements() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandMorphologySpec interior =
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(massif, basin, 0.35));
        SkyIslandArchipelagoPlan plan =
                plan(
                        53004L,
                        interior,
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP_RADIUS);

        var report =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry(),
                                ADEQUATE_VERTICAL);

        assertEquals(1, report.uncertifiedMemberCount());
        assertFalse(report.memberChecks().get(0).certified());
        assertTrue(Double.isNaN(report.memberChecks().get(0).requiredHorizontalRadius()));
        assertTrue(report.groupChecks().get(0).requiredGroupRadius().isEmpty());
        assertFalse(report.admitted());
        assertFalse(report.consumedReservationDefect());
    }

    @Test
    void exactDerivedMemberSeedCanChangeSupportAdmission() {
        MorphologyProviderId providerId =
                new MorphologyProviderId("test", "seed-aware");
        SkyIslandMorphologySpec morphology =
                ProviderMorphologySpec.full(providerId);

        SkyIslandArchipelagoPlan firstPlan =
                plan(53101L, morphology, 200.0, 240.0);
        SkyIslandArchipelagoPlan secondPlan =
                plan(53102L, morphology, 200.0, 240.0);
        long firstSeed =
                firstPlan.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        long secondSeed =
                secondPlan.groups().get(0).groupPlan().members().get(0).descriptor().seed();
        assertNotEquals(firstSeed, secondSeed);

        SkyIslandMorphologyProvider provider =
                seedAwareProvider(providerId, firstSeed);
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(provider)
                        .build();
        SkyIslandWorldVerticalReservation vertical =
                new SkyIslandWorldVerticalReservation(100.0, 100.0);
        var preflight = new SkyIslandSupportReservationPreflight();

        var first = preflight.evaluate(firstPlan, registry, vertical);
        var second = preflight.evaluate(secondPlan, registry, vertical);

        assertEquals(firstSeed, first.memberChecks().get(0).descriptorSeed());
        assertEquals(secondSeed, second.memberChecks().get(0).descriptorSeed());
        assertEquals(300.0, first.memberChecks().get(0).requiredHorizontalRadius());
        assertEquals(120.0, second.memberChecks().get(0).requiredHorizontalRadius());
        assertFalse(first.admitted());
        assertTrue(second.admitted());
    }

    @Test
    void proofBackedCompileRejectsBeforeCallingUncertifiedProviderGraphCompiler() {
        MorphologyProviderId providerId =
                new MorphologyProviderId("test", "no-support");
        SkyIslandMorphologyProvider provider =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return providerId;
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor descriptor) {
                        throw new AssertionError(
                                "AUTH-0053 must reject before procedural graph compilation");
                    }
                };
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(provider)
                        .build();
        SkyIslandArchipelagoPlan plan =
                plan(
                        53006L,
                        ProviderMorphologySpec.full(providerId),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP_RADIUS);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new SkyIslandWorldCatalogCompiler()
                                        .compileProofBacked(
                                                plan,
                                                registry,
                                                ADEQUATE_VERTICAL));
        assertTrue(error.getMessage().contains("AUTH-0053"));
    }

    @Test
    void repeatedPreflightIsDeterministic() {
        SkyIslandArchipelagoPlan plan =
                plan(
                        53007L,
                        direct(MorphologyFamily.BASIN),
                        ADEQUATE_HORIZONTAL,
                        ADEQUATE_GROUP_RADIUS);
        var preflight = new SkyIslandSupportReservationPreflight();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();

        assertEquals(
                preflight.evaluate(plan, registry, ADEQUATE_VERTICAL),
                preflight.evaluate(plan, registry, ADEQUATE_VERTICAL));
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
                throw new AssertionError("preflight must not compile procedural graphs");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                double horizontal = descriptor.seed() == largeSeed ? 300.0 : 120.0;
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(
                                horizontal, 50.0, 50.0));
            }
        };
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
    }

    private static SkyIslandArchipelagoPlan plan(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontalRadius,
            double reservedGroupRadius) {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
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
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth53",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontalRadius,
                        96.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                640.0, 0.0, 0.0, 0.0),
                        reservedGroupRadius);
        SkyIslandArchipelagoRequest request =
                new SkyIslandArchipelagoRequest(
                        rootSeed,
                        0.0,
                        0.0,
                        320.0,
                        500.0,
                        List.of(template),
                        new SkyIslandArchipelagoLayout.Hub(
                                1_600.0, 0.0, 0.0, 0.0, 0.0));
        return new SkyIslandArchipelagoPlanner().plan(request);
    }
}
