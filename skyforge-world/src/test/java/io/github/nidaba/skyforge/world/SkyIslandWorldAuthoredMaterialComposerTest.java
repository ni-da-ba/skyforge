package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkyIslandWorldAuthoredMaterialComposerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303439L;
    private static final long REALIZATION_ROOT = 0x5245414C30303439L;

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
    void uniqueOwnershipComposesTheExactAuth0047Sample() {
        SkyIslandAuthoredRealizationAssociation association =
                association(authored(2332L), 900.0, -500.0, 240.0, 49001L, "unique", 0, 0);
        SkyIslandWorldAuthoredMaterialComposer composer =
                composer(List.of(association));
        SkyIslandSubsurfacePosition semantic = firstMaterialSemantic(association);
        Coordinate3 world = toWorld(association, semantic);

        SkyIslandWorldAuthoredMaterialComposition composition =
                composer.compose(world, SkyIslandWorldAuthoredMaterialComposerTest::decision);

        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE,
                composition.status());
        assertTrue(composition.authoredSample().isPresent());
        assertTrue(composition.materialPresent());
        assertFalse(composition.authoredVoid());
        assertTrue(composition.materialApplication().isPresent());
        assertEquals(
                association.authoredIdentity(),
                composition
                        .ownership()
                        .uniqueOwner()
                        .orElseThrow()
                        .association()
                        .authoredIdentity());
        assertEquals(
                composition
                        .ownership()
                        .uniqueOwner()
                        .orElseThrow()
                        .semantic()
                        .orElseThrow(),
                composition
                        .authoredSample()
                        .orElseThrow()
                        .semantic()
                        .orElseThrow());
        assertEquals(
                composition
                        .authoredSample()
                        .orElseThrow()
                        .materialRealization()
                        .orElseThrow()
                        .winnerBindingKey(),
                composition.applicationKey());
    }

    @Test
    void noneOwnershipDoesNotConsultMaterialDecisionProvider() {
        SkyIslandAuthoredRealizationAssociation association =
                association(authored(653L), 0.0, 0.0, 220.0, 49002L, "none", 0, 0);
        AtomicInteger calls = new AtomicInteger();
        SkyIslandMaterialResolutionDecisionProvider forbidden =
                request -> {
                    calls.incrementAndGet();
                    throw new AssertionError("NONE ownership must not consult material decisions");
                };

        SkyIslandWorldAuthoredMaterialComposition composition =
                composer(List.of(association))
                        .compose(
                                new Coordinate3(50_000.0, 50_000.0, 50_000.0),
                                forbidden);

        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.NONE,
                composition.status());
        assertEquals(0, calls.get());
        assertTrue(composition.authoredSample().isEmpty());
        assertFalse(composition.materialPresent());
        assertTrue(composition.materialApplication().isEmpty());
    }

    @Test
    void ambiguousOwnershipDoesNotConsultMaterialDecisionProvider() {
        SkyIslandDescriptor firstAuthored = authored(1051L);
        SkyIslandDescriptor secondAuthored = authored(2211L);
        double sharedRadius = firstAuthored.nominalRadius();
        secondAuthored =
                new SkyIslandDescriptor(
                        secondAuthored.schemaVersion(),
                        secondAuthored.identity(),
                        secondAuthored.authorshipSeed(),
                        firstAuthored.morphologyFamily(),
                        sharedRadius,
                        secondAuthored.reliefBudget(),
                        secondAuthored.rockCompetence(),
                        secondAuthored.permeability(),
                        secondAuthored.temperatureTendency(),
                        secondAuthored.moistureTendency(),
                        secondAuthored.exposureTendency(),
                        secondAuthored.erosionMaturity(),
                        secondAuthored.hydrologicalPotential(),
                        secondAuthored.ecologicalPotential());

        SkyIslandAuthoredRealizationAssociation first =
                association(firstAuthored, 1300.0, 700.0, 250.0, 49003L, "ambiguous/a", 1, 0);
        SkyIslandAuthoredRealizationAssociation second =
                association(secondAuthored, 1300.0, 700.0, 250.0, 49004L, "ambiguous/b", 1, 1);
        Coordinate3 world = commonCenterInterior(first, second);
        AtomicInteger calls = new AtomicInteger();
        SkyIslandMaterialResolutionDecisionProvider forbidden =
                request -> {
                    calls.incrementAndGet();
                    throw new AssertionError(
                            "AMBIGUOUS ownership must not consult material decisions");
                };

        SkyIslandWorldAuthoredMaterialComposition composition =
                composer(List.of(second, first)).compose(world, forbidden);

        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS,
                composition.status());
        assertEquals(0, calls.get());
        assertEquals(2, composition.ownership().authoredOwners().size());
        assertTrue(composition.authoredSample().isEmpty());
        assertTrue(composition.applicationKey().isEmpty());
    }

    @Test
    void authoredCaveVoidRetainsUniqueOwnerWithoutApplication() {
        SkyIslandAuthoredRealizationAssociation association =
                association(authored(1439L), -800.0, 1100.0, 245.0, 49005L, "void", 2, 0);
        SkyIslandSubsurfacePosition semantic = firstAuthoredVoidSemantic(association);
        Coordinate3 world = toWorld(association, semantic);
        AtomicInteger calls = new AtomicInteger();

        SkyIslandWorldAuthoredMaterialComposition composition =
                composer(List.of(association))
                        .compose(
                                world,
                                request -> {
                                    calls.incrementAndGet();
                                    return decision(request);
                                });

        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE,
                composition.status());
        assertTrue(composition.authoredSample().isPresent());
        assertTrue(composition.authoredVoid());
        assertFalse(composition.materialPresent());
        assertTrue(composition.materialApplication().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void uniqueCompositionInvokesProviderOnlyForTheUniqueOwnersRequests() {
        SkyIslandAuthoredRealizationAssociation selected =
                association(authored(3670L), 0.0, 0.0, 240.0, 49006L, "selected", 3, 0);
        SkyIslandAuthoredRealizationAssociation distant =
                association(authored(653L), 5000.0, 5000.0, 240.0, 49007L, "distant", 3, 1);
        Coordinate3 world = toWorld(selected, firstMaterialSemantic(selected));
        AtomicInteger calls = new AtomicInteger();

        SkyIslandWorldAuthoredMaterialComposition composition =
                composer(List.of(distant, selected))
                        .compose(
                                world,
                                request -> {
                                    calls.incrementAndGet();
                                    assertEquals(
                                            selected.authoredIdentity(),
                                            request.bindingKey().islandIdentity());
                                    return decision(request);
                                });

        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE,
                composition.status());
        assertTrue(calls.get() > 0);
        assertEquals(
                selected.authoredIdentity(),
                composition
                        .authoredSample()
                        .orElseThrow()
                        .association()
                        .authoredIdentity());
    }

    @Test
    void compositionEnvelopeRejectsSampleFromAnotherAssociation() {
        SkyIslandAuthoredRealizationAssociation owner =
                association(authored(2332L), 0.0, 0.0, 240.0, 49008L, "owner", 4, 0);
        SkyIslandAuthoredRealizationAssociation foreign =
                association(authored(653L), 0.0, 0.0, 240.0, 49009L, "foreign", 4, 1);
        Coordinate3 world = toWorld(owner, firstMaterialSemantic(owner));
        SkyIslandAuthoredRealizationOwnershipSelection ownership =
                new SkyIslandAuthoredRealizationOwnershipResolver(
                                new SkyIslandAuthoredRealizationCatalog(
                                        AUTHORED_WORLD,
                                        REALIZATION_ROOT,
                                        List.of(owner)))
                        .resolve(world);
        SkyIslandWorldAuthoredMaterialSample foreignSample =
                new SkyIslandWorldAuthoredMaterialSampler(foreign)
                        .sample(world, SkyIslandWorldAuthoredMaterialComposerTest::decision);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWorldAuthoredMaterialComposition(
                        ownership, foreignSample));
    }

    private static SkyIslandWorldAuthoredMaterialComposer composer(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        return new SkyIslandWorldAuthoredMaterialComposer(
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, REALIZATION_ROOT, associations));
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

    private static SkyIslandSubsurfacePosition firstMaterialSemantic(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(
                        association.authoredDescriptor());
        double radius = association.authoredDescriptor().nominalRadius();
        for (int iz = 0; iz < 31; iz++) {
            double z = -radius + iz * (2.0 * radius / 30.0);
            for (int ix = 0; ix < 31; ix++) {
                double x = -radius + ix * (2.0 * radius / 30.0);
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                if (field.sample(semantic).materialPresent()
                        && toWorldOrNull(association, semantic) != null) {
                    return semantic;
                }
            }
        }
        throw new IllegalStateException(
                "fixture produced no physically mappable authored material point");
    }

    private static SkyIslandSubsurfacePosition firstAuthoredVoidSemantic(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(
                        association.authoredDescriptor());
        double radius = association.authoredDescriptor().nominalRadius();
        for (int depthIndex = 1; depthIndex < 20; depthIndex++) {
            double depth = depthIndex / 20.0;
            for (int iz = 0; iz < 41; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix < 41; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandSubsurfacePosition semantic =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandMaterialBindingRequestSelection source =
                            field.sample(semantic);
                    if (source.owned()
                            && !source.materialPresent()
                            && toWorldOrNull(association, semantic) != null) {
                        return semantic;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "fixture produced no physically mappable authored cave void");
    }

    private static Coordinate3 commonCenterInterior(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        SkyIslandVerticalColumn firstColumn =
                new SkyIslandCompiledVolumeColumnField(
                                first.realizedVolume().compiledVolume())
                        .columnAt(new SkyIslandLocalPosition(0.0, 0.0))
                        .orElseThrow();
        SkyIslandVerticalColumn secondColumn =
                new SkyIslandCompiledVolumeColumnField(
                                second.realizedVolume().compiledVolume())
                        .columnAt(new SkyIslandLocalPosition(0.0, 0.0))
                        .orElseThrow();
        double upper = Math.min(firstColumn.upperY(), secondColumn.upperY());
        double lower = Math.max(firstColumn.undersideY(), secondColumn.undersideY());
        if (!(upper > lower)) {
            throw new IllegalStateException("ambiguous fixture lacks physical overlap");
        }
        var descriptor =
                first.realizedVolume().compiledVolume().descriptor();
        return new Coordinate3(
                descriptor.centerX(),
                0.5 * (upper + lower),
                descriptor.centerZ());
    }

    private static Coordinate3 toWorld(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        Coordinate3 result = toWorldOrNull(association, semantic);
        if (result == null) {
            throw new IllegalStateException("semantic fixture lacks physical realization");
        }
        return result;
    }

    private static Coordinate3 toWorldOrNull(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(
                        new SkyIslandCompiledVolumeColumnField(
                                association.realizedVolume().compiledVolume()));
        var physical = transform.toPhysical(semantic);
        if (physical.isEmpty()) {
            return null;
        }
        return new Coordinate3(
                descriptor.centerX() + semantic.x(),
                physical.orElseThrow().physicalY(),
                descriptor.centerZ() + semantic.z());
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor authored,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed,
            String group,
            int groupOrdinal,
            int memberOrdinal) {
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        authored.nominalRadius(),
                        Math.max(48.0, 0.62 * authored.reliefBudget()),
                        Math.max(72.0, 0.86 * authored.reliefBudget()),
                        Math.min(32.0, authored.nominalRadius()),
                        0.0,
                        0.55,
                        0.58,
                        0.08,
                        authored.morphologyFamily(),
                        0.16,
                        34.0,
                        0.28);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        double radius = authored.nominalRadius();
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                group,
                                groupOrdinal,
                                memberOrdinal,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 1.4 * radius,
                                centerX + 1.4 * radius,
                                suspension - 200.0,
                                suspension + 200.0,
                                centerZ - 1.4 * radius,
                                centerZ + 1.4 * radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(authored, volume);
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(
                        AUTHORED_WORLD, 8L, 81L, islandKey));
    }
}
