package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused proof of hierarchical, provider-neutral archipelago planning. */
final class SkyIslandArchipelagoPlannerTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final double TOLERANCE = 1.0e-9;

    private final SkyIslandArchipelagoPlanner planner = new SkyIslandArchipelagoPlanner();

    @Test
    void arcPlanningIsExactDeterministicCenteredAndRaisesUnsafePreferredSpacing() {
        SkyIslandArchipelagoRequest request = arcRequest(ROOT_SEED);
        SkyIslandArchipelagoPlan first = planner.plan(request);
        SkyIslandArchipelagoPlan second = planner.plan(request);

        assertEquals(first, second);
        assertEquals(3, first.groupCount());
        assertEquals(9, first.totalMemberCount());
        assertEquals(List.of("anchor-chain", "secondary-chain", "outlier-chain"),
                first.groups().stream().map(SkyIslandArchipelagoGroupPlan::identifier).toList());
        assertEquals(List.of(
                        SkyIslandGroupRole.ANCHOR,
                        SkyIslandGroupRole.SECONDARY,
                        SkyIslandGroupRole.OUTLIER),
                first.groups().stream().map(SkyIslandArchipelagoGroupPlan::role).toList());
        assertTrue(first.minimumObservedGroupGap() + TOLERANCE >= request.minimumGroupGap());

        double meanX = first.groups().stream()
                .mapToDouble(SkyIslandArchipelagoGroupPlan::centerX)
                .average().orElseThrow();
        double meanZ = first.groups().stream()
                .mapToDouble(SkyIslandArchipelagoGroupPlan::centerZ)
                .average().orElseThrow();
        assertEquals(request.centerX(), meanX, TOLERANCE);
        assertEquals(request.centerZ(), meanZ, TOLERANCE);

        // Preferred spacing is intentionally only 100. Dynamic reservations require much more.
        assertEquals(100.0,
                ((SkyIslandArchipelagoLayout.Arc) request.layout()).preferredCenterSpacing(),
                0.0);
        assertTrue(distance(first.groups().get(0), first.groups().get(1)) >= 390.0 - TOLERANCE);
        assertTrue(distance(first.groups().get(1), first.groups().get(2)) >= 370.0 - TOLERANCE);
    }

    @Test
    void hubKeepsDominantAnchorAtRequestedCenterAndSeparatesSatellites() {
        SkyIslandArchipelagoRequest request = hubRequest(ROOT_SEED);
        SkyIslandArchipelagoPlan plan = planner.plan(request);
        SkyIslandArchipelagoGroupPlan anchor = plan.groups().get(0);

        assertEquals(SkyIslandGroupRole.ANCHOR, anchor.role());
        assertEquals(request.centerX(), anchor.centerX(), 0.0);
        assertEquals(request.centerZ(), anchor.centerZ(), 0.0);
        assertEquals(request.baseSuspensionElevation(), anchor.baseSuspensionElevation(), 0.0);
        assertTrue(plan.minimumObservedGroupGap() + TOLERANCE >= request.minimumGroupGap());
        assertEquals(4, plan.groupCount());
        assertEquals(13, plan.totalMemberCount());

        for (int first = 0; first < plan.groupCount(); first++) {
            for (int second = first + 1; second < plan.groupCount(); second++) {
                SkyIslandArchipelagoGroupPlan a = plan.groups().get(first);
                SkyIslandArchipelagoGroupPlan b = plan.groups().get(second);
                double required = a.reservedGroupRadius()
                        + b.reservedGroupRadius()
                        + request.minimumGroupGap();
                assertTrue(distance(a, b) + TOLERANCE >= required);
            }
        }
    }

    @Test
    void hierarchyDerivesUniqueGroupAndMemberIdentityWithoutChangingRequestedOrder() {
        SkyIslandArchipelagoPlan first = planner.plan(hubRequest(ROOT_SEED));
        SkyIslandArchipelagoPlan changed = planner.plan(hubRequest(ROOT_SEED + 1));

        assertEquals(
                first.groups().stream().map(SkyIslandArchipelagoGroupPlan::identifier).toList(),
                changed.groups().stream().map(SkyIslandArchipelagoGroupPlan::identifier).toList());
        assertNotEquals(
                first.groups().stream().map(SkyIslandArchipelagoGroupPlan::groupRootSeed).toList(),
                changed.groups().stream().map(SkyIslandArchipelagoGroupPlan::groupRootSeed).toList());

        Set<Long> groupSeeds = new HashSet<>();
        Set<Long> memberSeeds = new HashSet<>();
        for (SkyIslandArchipelagoGroupPlan group : first.groups()) {
            assertTrue(groupSeeds.add(group.groupRootSeed()));
            group.groupPlan().members().forEach(member -> assertTrue(memberSeeds.add(member.descriptor().seed())));
        }
        assertEquals(first.groupCount(), groupSeeds.size());
        assertEquals(first.totalMemberCount(), memberSeeds.size());
    }

    @Test
    void plannerAcceptsOpaqueExternalProviderMorphologyIntentAtEveryHierarchyLevel() {
        SkyIslandArchipelagoPlan plan = planner.plan(hubRequest(ROOT_SEED));
        assertEquals("example:anchor", plan.groups().get(0).groupPlan().members().get(0)
                .morphology().stableIdentifier());
        assertEquals("example:satellite", plan.groups().get(2).groupPlan().members().get(0)
                .morphology().stableIdentifier());
    }

    @Test
    void childGroupMustFitItsDeclaredHigherLevelReservation() {
        SkyIslandGroupTemplate tooSmall = new SkyIslandGroupTemplate(
                "too-small",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                40.0,
                20.0,
                8.0,
                morphologies("example", "oversized", 3),
                new SkyIslandGroupLayout.Chain(0.0, 120.0, 0.0, 0.0, 0.0, 0.0),
                100.0);
        SkyIslandArchipelagoRequest request = new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                300.0,
                40.0,
                List.of(tooSmall),
                new SkyIslandArchipelagoLayout.Hub(400.0, 0.0, 0.0, 0.0, 0.0));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> planner.plan(request));
        assertTrue(exception.getMessage().contains("exceeds reserved archipelago envelope"));
    }

    @Test
    void hubRequiresExplicitAnchorRoleAndTemplateIdentifiersAreUnique() {
        SkyIslandGroupTemplate secondary = template(
                "not-anchor", SkyIslandGroupRole.SECONDARY, 180.0, "example", "secondary", 3);
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                300.0,
                40.0,
                List.of(secondary),
                new SkyIslandArchipelagoLayout.Hub(400.0, 0.0, 0.0, 0.0, 0.0)));

        SkyIslandGroupTemplate duplicateA = template(
                "duplicate", SkyIslandGroupRole.ANCHOR, 180.0, "example", "a", 3);
        SkyIslandGroupTemplate duplicateB = template(
                "duplicate", SkyIslandGroupRole.SATELLITE, 180.0, "example", "b", 3);
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                300.0,
                40.0,
                List.of(duplicateA, duplicateB),
                new SkyIslandArchipelagoLayout.Arc(0.0, 400.0, 0.0, 0.0, 0.0, 0.0, 0.0)));
    }

    private static SkyIslandArchipelagoRequest arcRequest(long seed) {
        return new SkyIslandArchipelagoRequest(
                seed,
                1200.0,
                -800.0,
                340.0,
                50.0,
                List.of(
                        template("anchor-chain", SkyIslandGroupRole.ANCHOR, 180.0, "example", "anchor", 3),
                        template("secondary-chain", SkyIslandGroupRole.SECONDARY, 160.0, "example", "secondary", 3),
                        template("outlier-chain", SkyIslandGroupRole.OUTLIER, 160.0, "example", "outlier", 3)),
                new SkyIslandArchipelagoLayout.Arc(
                        Math.PI / 5.0,
                        100.0,
                        0.15,
                        35.0,
                        90.0,
                        0.20,
                        24.0));
    }

    private static SkyIslandArchipelagoRequest hubRequest(long seed) {
        return new SkyIslandArchipelagoRequest(
                seed,
                250.0,
                -175.0,
                360.0,
                60.0,
                List.of(
                        template("central-anchor", SkyIslandGroupRole.ANCHOR, 300.0, "example", "anchor", 4),
                        template("secondary-west", SkyIslandGroupRole.SECONDARY, 220.0, "example", "secondary", 3),
                        template("satellite-north", SkyIslandGroupRole.SATELLITE, 180.0, "example", "satellite", 3),
                        template("outlier-east", SkyIslandGroupRole.OUTLIER, 180.0, "example", "outlier", 3)),
                new SkyIslandArchipelagoLayout.Hub(
                        300.0,
                        Math.PI / 8.0,
                        0.15,
                        0.25,
                        32.0));
    }

    private static SkyIslandGroupTemplate template(
            String identifier,
            SkyIslandGroupRole role,
            double groupRadius,
            String namespace,
            String path,
            int memberCount) {
        SkyIslandGroupLayout layout = role == SkyIslandGroupRole.ANCHOR && memberCount >= 4
                ? new SkyIslandGroupLayout.Cluster(110.0, 0.1, 0.10, 0.15)
                : new SkyIslandGroupLayout.Chain(0.15, 110.0, 0.05, 8.0, 14.0, 0.12);
        return new SkyIslandGroupTemplate(
                identifier,
                role,
                descriptor(),
                40.0,
                20.0,
                8.0,
                morphologies(namespace, path, memberCount),
                layout,
                groupRadius);
    }

    private static List<SkyIslandMorphologySpec> morphologies(
            String namespace, String path, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> (SkyIslandMorphologySpec) ProviderMorphologySpec.full(
                        new MorphologyProviderId(namespace, index == 0 ? path : path + "-" + index)))
                .toList();
    }

    private static SkyIslandVolumeDescriptor descriptor() {
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
                0.2,
                0.55,
                0.60,
                0.15,
                0.0,
                24.0);
    }

    private static double distance(
            SkyIslandArchipelagoGroupPlan first, SkyIslandArchipelagoGroupPlan second) {
        return Math.hypot(first.centerX() - second.centerX(), first.centerZ() - second.centerZ());
    }
}
