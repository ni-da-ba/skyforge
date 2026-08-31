package io.github.nidaba.skyforge.recipes.skyisland.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyIslandGroupPlannerTest {
    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final double TOLERANCE = 1.0e-9;

    private final SkyIslandGroupPlanner planner = new SkyIslandGroupPlanner();

    @Test
    void invalidSpecsLayoutsAndReservationsFailEarly() {
        MorphologyProviderId custom = new MorphologyProviderId("example", "crescent");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderMorphologySpec(custom, 1.01, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderBlendMorphologySpec(
                        new MorphologyProviderBlend(
                                custom, new MorphologyProviderId("skyforge", "spine"), 0.5),
                        0.0,
                        -0.01));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGroupLayout.Chain(0.0, -1.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGroupLayout.Cluster(600.0, 0.0, 0.75, 0.0));

        SkyIslandVolumeDescriptor invalidTemplate = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                0.0,
                0.65,
                0.60,
                0.25,
                0.2,
                32.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGroupRequest(
                        0L,
                        invalidTemplate,
                        280.0,
                        64.0,
                        24.0,
                        List.of(ProviderMorphologySpec.full(custom)),
                        chainLayout()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGroupRequest(
                        0L,
                        template(),
                        300.0,
                        80.0,
                        24.0,
                        morphologies(),
                        new SkyIslandGroupLayout.Chain(0.0, 680.0, 0.10, 0.0, 0.0, 0.0)));
    }

    @Test
    void repeatedChainPlanningIsExactlyDeterministicAndSeedsAreUnique() {
        SkyIslandGroupRequest request = request(SKYFORGE_SEED, chainLayout());
        SkyIslandGroupPlan first = planner.plan(request);
        SkyIslandGroupPlan second = planner.plan(request);

        assertEquals(first, second);
        Set<Long> seeds = new HashSet<>();
        for (SkyIslandGroupMemberPlan member : first.members()) {
            assertTrue(seeds.add(member.descriptor().seed()));
        }
        assertEquals(request.memberCount(), seeds.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.members().add(first.members().get(0)));
    }

    @Test
    void changingRootSeedChangesDerivedIdentityAndPlacementButPreservesMorphologyOrder() {
        SkyIslandGroupPlan first = planner.plan(request(0L, chainLayout()));
        SkyIslandGroupPlan second = planner.plan(request(1L, chainLayout()));
        assertEquals(first.members().size(), second.members().size());

        boolean placementChanged = false;
        for (int index = 0; index < first.members().size(); index++) {
            SkyIslandGroupMemberPlan firstMember = first.members().get(index);
            SkyIslandGroupMemberPlan secondMember = second.members().get(index);
            assertEquals(firstMember.morphology(), secondMember.morphology());
            assertNotEquals(firstMember.descriptor().seed(), secondMember.descriptor().seed());
            placementChanged |= Math.abs(firstMember.descriptor().centerX() - secondMember.descriptor().centerX()) > TOLERANCE
                    || Math.abs(firstMember.descriptor().centerZ() - secondMember.descriptor().centerZ()) > TOLERANCE;
        }
        assertTrue(placementChanged);
    }

    @Test
    void chainIsOrderedCenteredAndSatisfiesExplicitReservationSpacing() {
        SkyIslandGroupRequest request = request(SKYFORGE_SEED, chainLayout());
        SkyIslandGroupPlan plan = planner.plan(request);
        double heading = ((SkyIslandGroupLayout.Chain) request.layout()).headingRadians();
        double cosine = Math.cos(heading);
        double sine = Math.sin(heading);
        double previousProjection = Double.NEGATIVE_INFINITY;
        double meanX = 0.0;
        double meanZ = 0.0;

        for (SkyIslandGroupMemberPlan member : plan.members()) {
            double dx = member.descriptor().centerX() - plan.groupCenterX();
            double dz = member.descriptor().centerZ() - plan.groupCenterZ();
            double projection = dx * cosine + dz * sine;
            assertTrue(projection > previousProjection, member.memberIdentifier());
            previousProjection = projection;
            meanX += member.descriptor().centerX();
            meanZ += member.descriptor().centerZ();
        }
        meanX /= plan.members().size();
        meanZ /= plan.members().size();
        assertEquals(plan.groupCenterX(), meanX, TOLERANCE);
        assertEquals(plan.groupCenterZ(), meanZ, TOLERANCE);
        assertTrue(plan.minimumObservedCenterSpacing() + TOLERANCE >= request.requiredCenterSpacing());
    }

    @Test
    void clusterIsCenteredAndEveryPairSatisfiesExplicitReservationSpacing() {
        SkyIslandGroupRequest request = request(SKYFORGE_SEED, clusterLayout());
        SkyIslandGroupPlan plan = planner.plan(request);
        double meanX = plan.members().stream()
                .mapToDouble(member -> member.descriptor().centerX())
                .average()
                .orElseThrow();
        double meanZ = plan.members().stream()
                .mapToDouble(member -> member.descriptor().centerZ())
                .average()
                .orElseThrow();
        assertEquals(plan.groupCenterX(), meanX, TOLERANCE);
        assertEquals(plan.groupCenterZ(), meanZ, TOLERANCE);
        assertTrue(plan.minimumObservedCenterSpacing() + TOLERANCE >= request.requiredCenterSpacing());

        for (int first = 0; first < plan.members().size(); first++) {
            for (int second = first + 1; second < plan.members().size(); second++) {
                var a = plan.members().get(first).descriptor();
                var b = plan.members().get(second).descriptor();
                assertTrue(
                        Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ()) + TOLERANCE
                                >= request.requiredCenterSpacing());
            }
        }
    }

    @Test
    void plannedDescriptorsPreserveTemplateGeometryControlsAndMorphologyIntentExactly() {
        SkyIslandVolumeDescriptor template = template();
        List<SkyIslandMorphologySpec> morphologies = morphologies();
        SkyIslandGroupRequest request = new SkyIslandGroupRequest(
                SKYFORGE_SEED,
                template,
                280.0,
                64.0,
                30.0,
                morphologies,
                chainLayout());
        SkyIslandGroupPlan plan = planner.plan(request);

        for (int index = 0; index < plan.members().size(); index++) {
            SkyIslandGroupMemberPlan member = plan.members().get(index);
            SkyIslandVolumeDescriptor descriptor = member.descriptor();
            assertEquals(SkyIslandVolumeDescriptor.SCHEMA_VERSION_1, descriptor.schemaVersion());
            assertEquals(template.nominalRadius(), descriptor.nominalRadius());
            assertEquals(template.upperElevation(), descriptor.upperElevation());
            assertEquals(template.undersideDepth(), descriptor.undersideDepth());
            assertEquals(template.coastalFalloff(), descriptor.coastalFalloff());
            assertEquals(template.ridgeStrength(), descriptor.ridgeStrength());
            assertEquals(template.undersideTaper(), descriptor.undersideTaper());
            assertEquals(template.undersideAsymmetry(), descriptor.undersideAsymmetry());
            assertEquals(template.signalScale(), descriptor.signalScale());
            assertEquals(0.0, descriptor.signalAmplitude());
            assertEquals(morphologies.get(index), member.morphology());
            assertEquals(280.0, member.reservedHorizontalRadius());
        }
    }

    @Test
    void arbitraryProviderIdsAndProviderBlendsPlanWithoutRegistryOrBuiltInEnum() {
        MorphologyProviderId customA = new MorphologyProviderId("example", "crescent");
        MorphologyProviderId customB = new MorphologyProviderId("example", "forked-spire");
        List<SkyIslandMorphologySpec> arbitrary = List.of(
                ProviderMorphologySpec.full(customA),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(customA, customB, 0.35)),
                new ProviderMorphologySpec(customB, 0.4, 0.7));
        SkyIslandGroupRequest request = new SkyIslandGroupRequest(
                SKYFORGE_SEED,
                template(),
                220.0,
                60.0,
                10.0,
                arbitrary,
                new SkyIslandGroupLayout.Chain(0.3, 600.0, 0.0, 20.0, 40.0, 0.1));
        SkyIslandGroupPlan plan = planner.plan(request);

        assertEquals(arbitrary, plan.members().stream().map(SkyIslandGroupMemberPlan::morphology).toList());
        assertEquals("example:crescent", plan.members().get(0).morphology().stableIdentifier());
        assertTrue(plan.members().get(1).morphology().stableIdentifier().contains("example:"));
    }

    private static SkyIslandGroupRequest request(long rootSeed, SkyIslandGroupLayout layout) {
        return new SkyIslandGroupRequest(
                rootSeed,
                template(),
                280.0,
                64.0,
                30.0,
                morphologies(),
                layout);
    }

    private static SkyIslandGroupLayout.Chain chainLayout() {
        return new SkyIslandGroupLayout.Chain(
                Math.PI / 7.0,
                720.0,
                0.10,
                90.0,
                180.0,
                0.18);
    }

    private static SkyIslandGroupLayout.Cluster clusterLayout() {
        return new SkyIslandGroupLayout.Cluster(
                680.0,
                Math.PI / 9.0,
                0.18,
                0.35);
    }

    private static List<SkyIslandMorphologySpec> morphologies() {
        MorphologyProviderId crescent = new MorphologyProviderId("example", "crescent");
        MorphologyProviderId spine = new MorphologyProviderId("skyforge", "spine");
        MorphologyProviderId basin = new MorphologyProviderId("skyforge", "basin");
        MorphologyProviderId lobed = new MorphologyProviderId("skyforge", "lobed");
        MorphologyProviderId massif = new MorphologyProviderId("skyforge", "massif");
        return List.of(
                ProviderMorphologySpec.full(crescent),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(crescent, spine, 0.35)),
                new ProviderMorphologySpec(basin, 0.8, 1.0),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(basin, lobed, 0.45)),
                ProviderMorphologySpec.full(massif),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(crescent, lobed, 0.60)),
                new ProviderMorphologySpec(spine, 0.7, 0.9));
    }

    private static SkyIslandVolumeDescriptor template() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                1234L,
                1600.0,
                -900.0,
                320.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 11.0,
                0.65,
                0.60,
                0.25,
                0.0,
                32.0);
    }
}
