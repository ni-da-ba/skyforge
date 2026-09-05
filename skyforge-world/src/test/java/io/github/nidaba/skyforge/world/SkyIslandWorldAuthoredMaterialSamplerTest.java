package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandWorldAuthoredMaterialSamplerTest {
    private static final long AUTHORED_WORLD = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x574F524C4453414DL;

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(
                    new SkyIslandMaterialCapabilityProfile(
                            0.94, 0.20, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.92, 0.94, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.94, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.94, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.20, 0.94),
                    SkyIslandMaterialCapabilityProfile.uniform(0.86));

    @Test
    void worldSampleRoundTripsThroughAuthoritativeColumnAndFinalWinner() {
        Fixture fixture = fixture(1439L, 1200.0, -800.0, 0, 0, 0.62);
        SkyIslandSubsurfacePosition semantic = firstMaterialSemantic(fixture);
        Coordinate3 world = toWorld(fixture.association(), semantic);

        SkyIslandWorldAuthoredMaterialSample sample =
                fixture.sampler().sample(world, SkyIslandWorldAuthoredMaterialSamplerTest::decision);

        assertTrue(sample.physicalInterior());
        assertTrue(sample.authoredOwned());
        assertTrue(sample.materialPresent());
        assertFalse(sample.authoredVoid());
        assertHorizontalRoundTrip(
                semantic,
                sample.semantic().orElseThrow(),
                world,
                fixture.physical());
        assertEquals(
                semantic.depthFraction(),
                sample.semantic().orElseThrow().depthFraction(),
                1.0e-12);
        assertEquals(
                sample.materialRealization().orElseThrow().winnerBindingKey().orElseThrow(),
                sample.applicationKey().orElseThrow());
    }

    @Test
    void sampleEnvelopeRejectsForgedWorldLocalFrame() {
        Fixture fixture = fixture(1439L, 1200.0, -800.0, 0, 0, 0.62);
        SkyIslandSubsurfacePosition semantic = firstMaterialSemantic(fixture);
        SkyIslandWorldAuthoredMaterialSample valid =
                fixture.sampler()
                        .sample(
                                toWorld(fixture.association(), semantic),
                                SkyIslandWorldAuthoredMaterialSamplerTest::decision);
        Coordinate3 forgedWorld =
                new Coordinate3(
                        valid.worldPosition().x() + 1.0,
                        valid.worldPosition().y(),
                        valid.worldPosition().z());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWorldAuthoredMaterialSample(
                        valid.association(),
                        forgedWorld,
                        valid.realizedPosition(),
                        valid.semanticPosition(),
                        valid.realization(),
                        valid.application()));
    }

    @Test
    void worldTranslationDoesNotEnterStableSemanticIdentity() {
        SkyIslandDescriptor authored = authored(2211L);
        Fixture first = fixture(authored, 1200.0, -800.0, 0, 0, 0.58);
        Fixture second = fixture(authored, -3400.0, 2700.0, 1, 0, 0.58);
        int compared = 0;
        double radius = authored.nominalRadius();
        SkyIslandMaterialBindingRequestField nativeField =
                SkyIslandMaterialBindingRequestField.create(authored);

        for (int z = -(int) radius; z <= (int) radius; z += 16) {
            for (int x = -(int) radius; x <= (int) radius; x += 16) {
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(x, z, 0.5);
                if (!nativeField.sample(semantic).materialPresent()) {
                    continue;
                }
                Coordinate3 firstWorld = toWorldOrNull(first.association(), semantic);
                Coordinate3 secondWorld = toWorldOrNull(second.association(), semantic);
                if (firstWorld == null || secondWorld == null) {
                    continue;
                }

                SkyIslandWorldAuthoredMaterialSample a =
                        first.sampler()
                                .sample(
                                        firstWorld,
                                        SkyIslandWorldAuthoredMaterialSamplerTest::decision);
                SkyIslandWorldAuthoredMaterialSample b =
                        second.sampler()
                                .sample(
                                        secondWorld,
                                        SkyIslandWorldAuthoredMaterialSamplerTest::decision);

                assertHorizontalRoundTrip(
                        semantic, a.semantic().orElseThrow(), firstWorld, first.physical());
                assertHorizontalRoundTrip(
                        semantic, b.semantic().orElseThrow(), secondWorld, second.physical());
                assertEquals(
                        authored.identity(),
                        a.applicationKey()
                                .map(SkyIslandSemanticPaletteBindingKey::islandIdentity)
                                .orElseThrow());
                assertEquals(
                        authored.identity(),
                        b.applicationKey()
                                .map(SkyIslandSemanticPaletteBindingKey::islandIdentity)
                                .orElseThrow());
                compared++;
            }
        }
        assertTrue(compared > 20);
    }

    @Test
    void physicalAirProducesNoSemanticOrMaterialState() {
        Fixture fixture = fixture(2332L, 100.0, 200.0, 0, 0, 0.52);
        Coordinate3 world =
                new Coordinate3(
                        fixture.physical().centerX(),
                        fixture.physical().suspensionElevation() + 10000.0,
                        fixture.physical().centerZ());

        SkyIslandWorldAuthoredMaterialSample sample =
                fixture.sampler().sample(world, SkyIslandWorldAuthoredMaterialSamplerTest::decision);

        assertFalse(sample.physicalInterior());
        assertFalse(sample.authoredOwned());
        assertFalse(sample.materialPresent());
        assertTrue(sample.semantic().isEmpty());
        assertTrue(sample.materialRealization().isEmpty());
        assertTrue(sample.materialApplication().isEmpty());
    }

    @Test
    void compiledPhysicalColumnOutsideNativeDomainRemainsUnowned() {
        SkyIslandDescriptor authored = authored(2332L);
        Fixture fixture = fixture(authored, 600.0, -500.0, 0, 0, 1.0);
        double radius = authored.nominalRadius();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        fixture.association().realizedVolume().compiledVolume());

        SkyIslandNaturalizedDomainField nativeDomain =
                SkyIslandNaturalizedDomainField.create(authored);
        SkyIslandLocalPosition outsideNative = null;
        SkyIslandVerticalColumn column = null;
        for (int iz = 0; iz < 31 && outsideNative == null; iz++) {
            double z = -1.30 * radius + iz * (2.60 * radius / 30.0);
            for (int ix = 0; ix < 31; ix++) {
                double x = -1.30 * radius + ix * (2.60 * radius / 30.0);
                SkyIslandLocalPosition candidate = new SkyIslandLocalPosition(x, z);
                var candidateColumn = columns.columnAt(candidate);
                if (candidateColumn.isPresent() && nativeDomain.sample(candidate) <= 0.0) {
                    outsideNative = candidate;
                    column = candidateColumn.orElseThrow();
                    break;
                }
            }
        }
        if (outsideNative == null || column == null) {
            throw new IllegalStateException(
                    "fixture produced no physical column outside native authored ownership");
        }
        Coordinate3 world =
                new Coordinate3(
                        fixture.physical().centerX() + outsideNative.x(),
                        column.physicalYAt(0.50),
                        fixture.physical().centerZ() + outsideNative.z());

        SkyIslandWorldAuthoredMaterialSample sample =
                fixture.sampler().sample(world, SkyIslandWorldAuthoredMaterialSamplerTest::decision);

        assertTrue(sample.physicalInterior());
        assertFalse(sample.authoredOwned());
        assertFalse(sample.materialPresent());
        assertTrue(sample.materialApplication().isEmpty());
    }

    @Test
    void authoredCaveVoidSurvivesWorldSpaceSampling() {
        Fixture fixture = fixture(1439L, 900.0, -1100.0, 0, 0, 0.64);
        SkyIslandSubsurfacePosition voidSemantic =
                firstAuthoredVoidSemantic(fixture.authored(), fixture.association());
        Coordinate3 world = toWorld(fixture.association(), voidSemantic);

        SkyIslandWorldAuthoredMaterialSample sample =
                fixture.sampler().sample(world, SkyIslandWorldAuthoredMaterialSamplerTest::decision);

        assertTrue(sample.physicalInterior());
        assertTrue(sample.authoredOwned());
        assertFalse(sample.materialPresent());
        assertTrue(sample.authoredVoid());
        assertTrue(sample.applicationKey().isEmpty());
    }

    @Test
    void conditionedFinalWinnerRemainsAuthoritativeInWorldSpace() {
        Fixture fixture = fixture(1439L, -750.0, 1400.0, 0, 0, 0.57);
        SkyIslandWorldAuthoredMaterialSample conditioned = firstConditionedWorldSample(fixture);

        assertTrue(conditioned.materialPresent());
        assertTrue(
                conditioned.materialRealization().orElseThrow().conditionedWinner());
        assertEquals(
                conditioned.materialRealization()
                        .orElseThrow()
                        .winnerBindingKey()
                        .orElseThrow(),
                conditioned.applicationKey().orElseThrow());
        assertFalse(
                conditioned.materialRealization()
                        .orElseThrow()
                        .structuralWinner()
                        .bindingKey()
                        .equals(conditioned.applicationKey().orElseThrow()));
    }

    @Test
    void providerDecisionForDifferentRequestIsRejected() {
        Fixture fixture = fixture(2332L, 0.0, 0.0, 0, 0, 0.55);
        SkyIslandSubsurfacePosition semantic = firstMaterialSemantic(fixture);
        Coordinate3 world = toWorld(fixture.association(), semantic);
        SkyIslandMaterialBindingRequest foreignRequest =
                SkyIslandMaterialBindingRequestCatalog.create(authored(653L))
                        .plannedRequests()
                        .get(0);
        SkyIslandMaterialResolutionDecision foreignDecision = decision(foreignRequest);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.sampler().sample(world, ignored -> foreignDecision));
    }

    @Test
    void worldSamplerMatchesDirectNativeChainAcrossCanonicalGrid() {
        Fixture fixture = fixture(1051L, 2200.0, 1700.0, 0, 0, 0.60);
        SkyIslandMaterialBindingRequestField directField =
                SkyIslandMaterialBindingRequestField.create(fixture.authored());
        int compared = 0;
        double radius = fixture.authored().nominalRadius();

        for (int iz = 0; iz < 17; iz++) {
            double z = -radius + iz * (2.0 * radius / 16.0);
            for (int ix = 0; ix < 17; ix++) {
                double x = -radius + ix * (2.0 * radius / 16.0);
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                Coordinate3 world = toWorldOrNull(fixture.association(), semantic);
                if (world == null) {
                    continue;
                }

                SkyIslandWorldAuthoredMaterialSample actual =
                        fixture.sampler().sample(
                                world,
                                SkyIslandWorldAuthoredMaterialSamplerTest::decision);
                SkyIslandSubsurfacePosition recovered =
                        actual.semantic().orElseThrow();
                assertHorizontalRoundTrip(
                        semantic, recovered, world, fixture.physical());
                SkyIslandMaterialBindingRequestSelection source =
                        directField.sample(recovered);
                java.util.Map<
                                SkyIslandSemanticPaletteBindingKey,
                                SkyIslandMaterialResolutionDecision>
                        decisions = new java.util.HashMap<>();
                for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
                    decisions.put(use.request().bindingKey(), decision(use.request()));
                }
                SkyIslandMaterialRealizationSelection expected =
                        SkyIslandMaterialExpressionRealizer.realize(
                                recovered,
                                SkyIslandMaterialExpressionAllocator.allocate(
                                        source, decisions));

                assertEquals(
                        expected.winnerBindingKey(),
                        actual.materialRealization().orElseThrow().winnerBindingKey());
                assertEquals(expected.materialPresent(), actual.materialPresent());
                compared++;
            }
        }
        assertTrue(compared > 40);
    }

    private static SkyIslandWorldAuthoredMaterialSample firstConditionedWorldSample(
            Fixture fixture) {
        double radius = fixture.authored().nominalRadius();
        for (int depthIndex = 2; depthIndex < 19; depthIndex++) {
            double depth = depthIndex / 20.0;
            for (int iz = 0; iz < 31; iz++) {
                double z = -radius + iz * (2.0 * radius / 30.0);
                for (int ix = 0; ix < 31; ix++) {
                    double x = -radius + ix * (2.0 * radius / 30.0);
                    SkyIslandSubsurfacePosition semantic =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    Coordinate3 world = toWorldOrNull(fixture.association(), semantic);
                    if (world == null) {
                        continue;
                    }
                    SkyIslandWorldAuthoredMaterialSample sample =
                            fixture.sampler().sample(
                                    world,
                                    SkyIslandWorldAuthoredMaterialSamplerTest::decision);
                    if (sample.materialPresent()
                            && sample.materialRealization().orElseThrow().conditionedWinner()) {
                        return sample;
                    }
                }
            }
        }
        throw new IllegalStateException("canonical fixture produced no conditioned winner");
    }

    private static SkyIslandSubsurfacePosition firstMaterialSemantic(
            Fixture fixture) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(fixture.authored());
        double radius = fixture.authored().nominalRadius();
        for (int iz = 0; iz < 25; iz++) {
            double z = -radius + iz * (2.0 * radius / 24.0);
            for (int ix = 0; ix < 25; ix++) {
                double x = -radius + ix * (2.0 * radius / 24.0);
                SkyIslandSubsurfacePosition position =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                if (field.sample(position).materialPresent()
                        && toWorldOrNull(fixture.association(), position) != null) {
                    return position;
                }
            }
        }
        throw new IllegalStateException(
                "canonical fixture produced no physically mappable material point");
    }

    private static SkyIslandSubsurfacePosition firstAuthoredVoidSemantic(
            SkyIslandDescriptor descriptor,
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(descriptor);
        double radius = descriptor.nominalRadius();
        for (int depthIndex = 1; depthIndex < 20; depthIndex++) {
            double depth = depthIndex / 20.0;
            for (int iz = 0; iz < 41; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix < 41; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandMaterialBindingRequestSelection source = field.sample(position);
                    if (source.owned()
                            && !source.materialPresent()
                            && toWorldOrNull(association, position) != null) {
                        return position;
                    }
                }
            }
        }
        throw new IllegalStateException("canonical fixture produced no authored cave void");
    }

    private static Coordinate3 toWorld(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        Coordinate3 result = toWorldOrNull(association, semantic);
        if (result == null) {
            throw new IllegalStateException("semantic fixture does not map to a physical column");
        }
        return result;
    }

    private static Coordinate3 toWorldOrNull(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        SkyIslandVolumeDescriptor physical =
                association.realizedVolume().compiledVolume().descriptor();
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(
                        new SkyIslandCompiledVolumeColumnField(
                                association.realizedVolume().compiledVolume()));
        var realized = transform.toPhysical(semantic);
        if (realized.isEmpty()) {
            return null;
        }
        return new Coordinate3(
                physical.centerX() + semantic.x(),
                realized.orElseThrow().physicalY(),
                physical.centerZ() + semantic.z());
    }

    private static void assertHorizontalRoundTrip(
            SkyIslandSubsurfacePosition input,
            SkyIslandSubsurfacePosition recovered,
            Coordinate3 world,
            SkyIslandVolumeDescriptor physical) {
        double tolerance =
                8.0
                        * Math.max(
                                Math.max(Math.ulp(world.x()), Math.ulp(physical.centerX())),
                                Math.max(Math.ulp(world.z()), Math.ulp(physical.centerZ())));
        assertEquals(input.x(), recovered.x(), tolerance);
        assertEquals(input.z(), recovered.z(), tolerance);
    }

    private static SkyIslandMaterialResolutionDecision decision(
            SkyIslandMaterialBindingRequest request) {
        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, CANDIDATES);
        return SkyIslandMaterialResolutionDecisionFactory.decide(
                frontier,
                frontier.topRank().profile(),
                SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
    }

    private static Fixture fixture(
            long islandKey,
            double centerX,
            double centerZ,
            int groupOrdinal,
            int memberOrdinal,
            double ridgeStrength) {
        return fixture(
                authored(islandKey),
                centerX,
                centerZ,
                groupOrdinal,
                memberOrdinal,
                ridgeStrength);
    }

    private static Fixture fixture(
            SkyIslandDescriptor authored,
            double centerX,
            double centerZ,
            int groupOrdinal,
            int memberOrdinal,
            double ridgeStrength) {
        long geometrySeed =
                0x4700000000000000L
                        ^ authored.identity().islandKey()
                        ^ ((long) groupOrdinal << 32)
                        ^ memberOrdinal;
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        280.0,
                        authored.nominalRadius(),
                        Math.max(48.0, 0.62 * authored.reliefBudget()),
                        Math.max(72.0, 0.86 * authored.reliefBudget()),
                        Math.min(32.0, authored.nominalRadius()),
                        0.0,
                        ridgeStrength,
                        0.57,
                        0.12,
                        authored.morphologyFamily(),
                        0.18,
                        36.0,
                        0.34);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth47-" + groupOrdinal,
                                groupOrdinal,
                                memberOrdinal,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 1.5 * authored.nominalRadius(),
                                centerX + 1.5 * authored.nominalRadius(),
                                0.0,
                                600.0,
                                centerZ - 1.5 * authored.nominalRadius(),
                                centerZ + 1.5 * authored.nominalRadius()),
                        compiled);
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(authored, volume);
        return new Fixture(
                authored,
                physical,
                association,
                new SkyIslandWorldAuthoredMaterialSampler(association));
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey));
    }

    private record Fixture(
            SkyIslandDescriptor authored,
            SkyIslandVolumeDescriptor physical,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandWorldAuthoredMaterialSampler sampler) {}
}
