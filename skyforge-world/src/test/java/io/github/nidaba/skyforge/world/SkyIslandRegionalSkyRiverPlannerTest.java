package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandRegionalSkyRiverPlannerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303934L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(520.0, 320.0);

    @Test
    void singletonRegionDoesNotFabricateCrossIslandRiver() {
        Fixture singleton = fixture(94001L, 1, 0.0, 0.0, 0.0, 1.0);

        assertTrue(new SkyIslandRegionalSkyRiverPlanner()
                .plan(singleton.binding(), 7L)
                .isEmpty());
    }

    @Test
    void deterministicPlanRetainsExactBindingAndCanonicalParticipants() {
        Fixture fixture = fixture(94002L, 3, 0.0, 0.0, 0.0, 1.0);
        SkyIslandRegionalSkyRiverPlanner planner =
                new SkyIslandRegionalSkyRiverPlanner();

        SkyIslandRegionalSkyRiverPlan first =
                planner.plan(fixture.binding(), 9L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan second =
                planner.plan(fixture.binding(), 9L).orElseThrow();

        assertEquals(fixture.binding(), first.binding());
        assertEquals(first.canonicalToken(), second.canonicalToken());
        assertEquals(first.source(), second.source());
        assertEquals(first.sink(), second.sink());
        assertEquals(first.trajectory(), second.trajectory());

        List<SkyIslandAuthoredRealizationAssociation> canonical =
                fixture.binding().associationCatalog().associations();
        Pair expected = farthestPair(canonical);
        assertTrue(
                (first.source().equals(expected.first()) && first.sink().equals(expected.second()))
                        || (first.source().equals(expected.second())
                                && first.sink().equals(expected.first())));
    }

    @Test
    void sourceToSinkOrientationUsesUpperNominalReferenceElevation() {
        Fixture fixture = fixture(94003L, 3, 0.0, 0.0, 0.0, 1.0);
        SkyIslandRegionalSkyRiverPlan plan =
                new SkyIslandRegionalSkyRiverPlanner()
                        .plan(fixture.binding(), 11L)
                        .orElseThrow();

        assertTrue(upperReferenceY(plan.source()) >= upperReferenceY(plan.sink()));
        assertEquals(upperReferenceY(plan.source()), plan.trajectory().getFirst().worldY(), 0.0);
        assertEquals(upperReferenceY(plan.sink()), plan.trajectory().getLast().worldY(), 0.0);
        assertEquals(0.0, plan.trajectory().getFirst().parameter(), 0.0);
        assertEquals(1.0, plan.trajectory().getLast().parameter(), 0.0);
    }

    @Test
    void openSkyTrajectoryIsOrderedAndCurved() {
        SkyIslandRegionalSkyRiverPlan plan =
                new SkyIslandRegionalSkyRiverPlanner()
                        .plan(fixture(94004L, 3, 0.0, 0.0, 0.0, 1.0).binding(), 13L)
                        .orElseThrow();

        assertEquals(4, plan.trajectory().size());
        assertTrue(plan.trajectory().get(0).parameter() < plan.trajectory().get(1).parameter());
        assertTrue(plan.trajectory().get(1).parameter() < plan.trajectory().get(2).parameter());
        assertTrue(plan.trajectory().get(2).parameter() < plan.trajectory().get(3).parameter());

        SkyIslandRegionalSkyRiverWaypoint source = plan.trajectory().getFirst();
        SkyIslandRegionalSkyRiverWaypoint sink = plan.trajectory().getLast();
        SkyIslandRegionalSkyRiverWaypoint control = plan.trajectory().get(1);
        double linearX = lerp(source.worldX(), sink.worldX(), control.parameter());
        double linearZ = lerp(source.worldZ(), sink.worldZ(), control.parameter());
        assertTrue(
                Math.abs(control.worldX() - linearX) > 1.0e-9
                        || Math.abs(control.worldZ() - linearZ) > 1.0e-9);
    }

    @Test
    void uniformTranslationShiftsTrajectoryWithoutChangingParticipantIdentity() {
        Fixture base = fixture(94005L, 3, 0.0, 0.0, 0.0, 1.0);
        Fixture moved = fixture(94005L, 3, 12_000.0, 180.0, -7_000.0, 1.0);
        SkyIslandRegionalSkyRiverPlanner planner =
                new SkyIslandRegionalSkyRiverPlanner();

        SkyIslandRegionalSkyRiverPlan first = planner.plan(base.binding(), 17L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan second = planner.plan(moved.binding(), 17L).orElseThrow();

        assertEquals(first.source().canonicalToken(), second.source().canonicalToken());
        assertEquals(first.sink().canonicalToken(), second.sink().canonicalToken());
        for (int index = 0; index < first.trajectory().size(); index++) {
            SkyIslandRegionalSkyRiverWaypoint a = first.trajectory().get(index);
            SkyIslandRegionalSkyRiverWaypoint b = second.trajectory().get(index);
            assertEquals(a.worldX() + 12_000.0, b.worldX(), 1.0e-9);
            assertEquals(a.worldY() + 180.0, b.worldY(), 1.0e-9);
            assertEquals(a.worldZ() - 7_000.0, b.worldZ(), 1.0e-9);
            assertEquals(a.parameter(), b.parameter(), 0.0);
        }
    }

    @Test
    void uniformScalingScalesTrajectoryWithoutChangingParticipantIdentity() {
        Fixture base = fixture(94006L, 3, 0.0, 0.0, 0.0, 1.0);
        Fixture scaled = fixture(94006L, 3, 0.0, 0.0, 0.0, 2.0);
        SkyIslandRegionalSkyRiverPlanner planner =
                new SkyIslandRegionalSkyRiverPlanner();

        SkyIslandRegionalSkyRiverPlan first = planner.plan(base.binding(), 19L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan second = planner.plan(scaled.binding(), 19L).orElseThrow();

        assertEquals(first.source().canonicalToken(), second.source().canonicalToken());
        assertEquals(first.sink().canonicalToken(), second.sink().canonicalToken());
        for (int index = 0; index < first.trajectory().size(); index++) {
            SkyIslandRegionalSkyRiverWaypoint a = first.trajectory().get(index);
            SkyIslandRegionalSkyRiverWaypoint b = second.trajectory().get(index);
            assertEquals(a.worldX() * 2.0, b.worldX(), 1.0e-9);
            assertEquals(a.worldY() * 2.0, b.worldY(), 1.0e-9);
            assertEquals(a.worldZ() * 2.0, b.worldZ(), 1.0e-9);
            assertEquals(a.parameter(), b.parameter(), 0.0);
        }
    }

    @Test
    void publicPlannerSurfaceAcceptsOnlyExactBindingAndPhenomenonKey() {
        Method[] publicPlans = Arrays.stream(
                        SkyIslandRegionalSkyRiverPlanner.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("plan"))
                .toArray(Method[]::new);

        assertEquals(1, publicPlans.length);
        assertEquals(
                List.of(SkyIslandPublishedAuthoredRealizationBinding.class, long.class),
                List.of(publicPlans[0].getParameterTypes()));
    }

    private static Pair farthestPair(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        SkyIslandAuthoredRealizationAssociation first = null;
        SkyIslandAuthoredRealizationAssociation second = null;
        double best = -1.0;
        for (int i = 0; i < associations.size(); i++) {
            for (int j = i + 1; j < associations.size(); j++) {
                var a = associations.get(i).realizedVolume().compiledVolume().descriptor();
                var b = associations.get(j).realizedVolume().compiledVolume().descriptor();
                double distance = Math.hypot(b.centerX() - a.centerX(), b.centerZ() - a.centerZ());
                if (Double.compare(distance, best) > 0) {
                    best = distance;
                    first = associations.get(i);
                    second = associations.get(j);
                }
            }
        }
        return new Pair(first, second);
    }

    private static double upperReferenceY(
            SkyIslandAuthoredRealizationAssociation association) {
        var descriptor = association.realizedVolume().compiledVolume().descriptor();
        return descriptor.suspensionElevation() + descriptor.upperElevation();
    }

    private static double lerp(double first, double second, double parameter) {
        return first + (second - first) * parameter;
    }

    private static Fixture fixture(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(
                                acceptedCompilation(
                                        rootSeed,
                                        memberCount,
                                        centerX,
                                        centerY,
                                        centerZ,
                                        scale),
                                1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(50_000L + ordinal, volume),
                    volume));
            ordinal++;
        }
        Collections.reverse(associations);
        return new Fixture(new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations)));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 94L, islandKey));
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
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, memberCount, centerX, centerY, centerZ, scale, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                new SkyIslandWorldVerticalReservation(
                                        ADEQUATE_VERTICAL.downward() * scale,
                                        ADEQUATE_VERTICAL.upward() * scale),
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0094 fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth94",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(scale),
                        360.0 * scale,
                        48.0 * scale,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0 * scale,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0 * scale);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
                centerZ,
                centerY + 320.0 * scale,
                500.0 * scale,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0 * scale,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor(double scale) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0 * scale,
                96.0 * scale,
                48.0 * scale,
                64.0 * scale,
                24.0 * scale,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0 * scale);
    }

    private record Fixture(SkyIslandPublishedAuthoredRealizationBinding binding) {}
    private record Pair(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {}
}
