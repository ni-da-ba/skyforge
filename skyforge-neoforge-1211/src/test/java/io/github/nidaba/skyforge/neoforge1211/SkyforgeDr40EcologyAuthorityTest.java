package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationEcologicalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationIsolationProfiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSurfaceEcologyResolver;
import io.github.nidaba.skyforge.world.SkyIslandEcologicalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandFreshwaterHabitatOpportunityProfiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeDr40EcologyAuthorityTest {
    @Test
    void populationOutcomeDigestIgnoresFeatureCompletionOrdering() {
        var first = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "patch_grass_plain"),
                true,
                3,
                0x1234L);
        var second = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains"),
                false,
                0,
                0x5678L);
        var lakeEvidence = new SkyforgeNativeBiomePopulationRunner.LakeEvidence(
                0, 0, 0, 0, 0, 0xcbf29ce484222325L, List.of(), List.of());

        var resultA = new SkyforgeNativeBiomePopulationRunner.Result(
                Biomes.PLAINS,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                2,
                1,
                3,
                List.of(first, second),
                lakeEvidence);
        var resultB = new SkyforgeNativeBiomePopulationRunner.Result(
                Biomes.PLAINS,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                2,
                1,
                3,
                List.of(second, first),
                lakeEvidence);

        var phaseA = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                new net.minecraft.world.level.ChunkPos(4, -2).toLong(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                resultA);
        var phaseB = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                new net.minecraft.world.level.ChunkPos(4, -2).toLong(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                resultB);
        var phaseC = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                new net.minecraft.world.level.ChunkPos(-3, 7).toLong(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                resultA);
        var phaseD = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                new net.minecraft.world.level.ChunkPos(-3, 7).toLong(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                resultB);

        assertEquals(
                SkyforgeDr40ProductionEcologyEvidence.populationOutcomeDigest(List.of(phaseA, phaseC)),
                SkyforgeDr40ProductionEcologyEvidence.populationOutcomeDigest(List.of(phaseD, phaseB)));
    }

    @Test
    void populationPlanDigestIgnoresNativeDecorativeOutcomesButPreservesPlanIdentity() {
        var grassA = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "patch_grass_plain"),
                true,
                3,
                0x1234L);
        var treeA = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains"),
                true,
                369,
                0x5678L);
        var grassB = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "patch_grass_plain"),
                true,
                9,
                0x9999L);
        var treeB = new SkyforgeNativeBiomePopulationRunner.FeatureResult(
                ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains"),
                true,
                371,
                0xaaaaL);
        var lakeEvidence = new SkyforgeNativeBiomePopulationRunner.LakeEvidence(
                0, 0, 0, 0, 0, 0xcbf29ce484222325L, List.of(), List.of());

        var resultA = new SkyforgeNativeBiomePopulationRunner.Result(
                Biomes.PLAINS,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                2,
                2,
                372,
                List.of(grassA, treeA),
                lakeEvidence);
        var resultB = new SkyforgeNativeBiomePopulationRunner.Result(
                Biomes.PLAINS,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                2,
                2,
                380,
                List.of(grassB, treeB),
                lakeEvidence);
        var reordered = new SkyforgeNativeBiomePopulationRunner.Result(
                Biomes.PLAINS,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                2,
                2,
                372,
                List.of(treeA, grassA),
                lakeEvidence);

        long chunkKey = new net.minecraft.world.level.ChunkPos(4, -2).toLong();
        var phaseA = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                chunkKey, GenerationStep.Decoration.VEGETAL_DECORATION, resultA);
        var phaseB = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                chunkKey, GenerationStep.Decoration.VEGETAL_DECORATION, resultB);
        var phaseReordered = new SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase(
                chunkKey, GenerationStep.Decoration.VEGETAL_DECORATION, reordered);

        assertEquals(
                SkyforgeDr40ProductionEcologyEvidence.populationPlanDigest(List.of(phaseA)),
                SkyforgeDr40ProductionEcologyEvidence.populationPlanDigest(List.of(phaseB)),
                "native attachment/block-footprint outcomes are diagnostic, not authored-plan identity");
        assertNotEquals(
                SkyforgeDr40ProductionEcologyEvidence.populationPlanDigest(List.of(phaseA)),
                SkyforgeDr40ProductionEcologyEvidence.populationPlanDigest(List.of(phaseReordered)),
                "ordered native feature identity remains part of the deterministic Skyforge plan");
    }

    @Test
    void canonicalRuntimeVolumeHasExactNonPublishedEcologyAuthority() throws IOException {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        SkyIslandAuthoredRealizationCatalog catalog = new SkyIslandAuthoredRealizationCatalog(
                fixture.descriptor().identity().worldSeed(),
                fixture.volume().id().archipelagoRootSeed(),
                List.of(association));

        assertEquals(1, catalog.size());
        assertEquals(association, catalog.associationFor(fixture.volume().id()).orElseThrow());
        assertEquals(1471L, association.authoredIdentity().islandKey());
        assertEquals(
                "6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068",
                association.realizedVolumeId().path());

        var realized = fixture.volume().compiledVolume().descriptor();
        var surface = new SkyIslandAuthoredRealizationSurfaceEcologyResolver(catalog)
                .sample(
                        fixture.volume().id(),
                        new Coordinate2(realized.centerX(), realized.centerZ()));
        assertTrue(surface.physicalColumnPresent());
        assertTrue(surface.authoredInteriority() > 0.0);
        assertTrue(surface.authoredSurfacePresent());

        var islandOpportunity =
                new SkyIslandEcologicalOpportunityProfiler().profile(fixture.descriptor());
        var catalogOpportunity =
                new SkyIslandAuthoredRealizationEcologicalOpportunityProfiler().profile(catalog);
        assertEquals(1, catalogOpportunity.islandCount());
        assertEquals(
                islandOpportunity,
                catalogOpportunity.islands().getFirst().islandProfile());
        assertEquals(
                islandOpportunity.horizontalOwnedAreaEstimate(),
                catalogOpportunity.totalHorizontalOwnedAreaEstimate(),
                0.0);

        var freshwater =
                new SkyIslandFreshwaterHabitatOpportunityProfiler().profile(fixture.descriptor());
        assertEquals(fixture.descriptor(), freshwater.descriptor());

        var isolation = new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        assertEquals(1, isolation.islandCount());
        assertFalse(isolation.islands().getFirst().hasNeighbor());
        assertTrue(isolation.minimumNearestCenterDistance().isEmpty());
        assertTrue(isolation.minimumNearestNominalRadialGap().isEmpty());

        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String authority = Files.readString(
                project.resolve("../docs/agent-state/DR40_ECOLOGY_AUTHORITY.json"));
        assertTrue(authority.contains("\"specimen_id\": \"P2_DRESSED_REGION_A\""));
        assertTrue(authority.contains("\"authority\": \"AUTH-0046_EXPLICIT_ASSOCIATION\""));
        assertTrue(authority.contains("\"publication_claim\": \"NONE\""));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationSurfaceEcologyResolver"));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationEcologicalOpportunityProfiler"));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationIsolationProfiler"));
        assertTrue(authority.contains("\"nearest_neighbor\": \"ABSENT\""));
        assertTrue(authority.contains("\"native_detail_determinism\""));
        assertTrue(authority.contains("diagnostic_only_native_outcomes"));
        assertTrue(authority.contains("Do not patch vanilla/mod traversal internals solely to force bit-identical decorative vegetation."));
    }
}
