package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

/** Machine evidence for issue #491's intentionally single-deposit Copper realization tranche. */
final class SkyforgeCopperDepositAdapterTest {
    private static final SkyforgeCopperDepositAdapter.Specification SPECIFICATION =
            SkyforgeCopperDepositAdapter.Specification.representative();

    @Test
    void productionCopperIsEligibleDeterministicExplicitAndActuallySurfaceAccessible() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());

        var first = SkyforgeCopperDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        var replay = SkyforgeCopperDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();

        assertEquals(ResourceLocation.withDefaultNamespace("copper_ore"), SkyforgeCopperDepositAdapter.COPPER_ORE);
        assertEquals(first, replay, "the production deposit address is deterministic");
        assertEquals(1, first.specification().blockCount());
        assertEquals(SkyforgeCopperDepositAdapter.Grade.STANDARD, first.specification().grade());
        assertEquals(SkyforgeCopperDepositAdapter.Accessibility.SURFACE_EXPOSED, first.specification().accessibility());
        assertTrue(terrain.isSolidOwnedBy(first.volumeId(), first.position().getX(), first.position().getY(), first.position().getZ()));
        assertFalse(terrain.isSolidOwnedByOtherVolume(first.volumeId(), first.position().getX(), first.position().getY(), first.position().getZ()));
        assertEquals(
                terrain.integerSolidRange(first.volumeId(), first.position().getX(), first.position().getZ())
                        .orElseThrow().maximumY(),
                first.position().getY(),
                "the bounded specimen is a real exposed production-geography top block");
    }

    @Test
    void zeroOpportunityFailsClosedRatherThanInjectingCopper() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var source = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor()).sourcePlan();
        var zeroCells = source.cells().stream()
                .map(cell -> new io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityCell(cell, 0.0, 0.0, 0.0))
                .toList();
        var constructor = io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile.class
                .getDeclaredConstructor(io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyPlan.class, java.util.List.class);
        constructor.setAccessible(true);
        var zero = constructor.newInstance(source, zeroCells);

        assertEquals(0.0, zero.peakOpportunity(io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind.COPPER));
        assertTrue(SkyforgeCopperDepositAdapter.plan(
                zero, fixture.volume(), terrain(fixture.catalog()), SPECIFICATION).isEmpty());
    }

    @Test
    void placementIsIdempotentAndSaveReloadReproducesTheAuthoritativeBlock() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var deployment = SkyforgeCopperDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        ProtoChunk chunk = realizedChunk(terrain, deployment);

        assertTrue(SkyforgeCopperDepositAdapter.apply(chunk, deployment).writtenNow());
        assertFalse(SkyforgeCopperDepositAdapter.apply(chunk, deployment).writtenNow());
        assertTrue(chunk.getBlockState(deployment.position()).is(Blocks.COPPER_ORE));

        var reloaded = SkyforgeCopperDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        assertEquals(deployment, reloaded);
        assertFalse(SkyforgeCopperDepositAdapter.apply(chunk, reloaded).writtenNow());
    }

    @Test
    void stackedProductionVolumesRemainExactOwnerIsolated() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var lower = SkyforgeCopperDepositAdapter.plan(profile, fixture.lower(), terrain, SPECIFICATION).orElseThrow();
        var upper = SkyforgeCopperDepositAdapter.plan(profile, fixture.upper(), terrain, SPECIFICATION).orElseThrow();

        assertNotEquals(lower.volumeId(), upper.volumeId());
        assertNotEquals(lower.position(), upper.position());
        assertTrue(terrain.isSolidOwnedBy(lower.volumeId(), lower.position().getX(), lower.position().getY(), lower.position().getZ()));
        assertFalse(terrain.isSolidOwnedByOtherVolume(lower.volumeId(), lower.position().getX(), lower.position().getY(), lower.position().getZ()));
        assertTrue(terrain.isSolidOwnedBy(upper.volumeId(), upper.position().getX(), upper.position().getY(), upper.position().getZ()));
        assertFalse(terrain.isSolidOwnedByOtherVolume(upper.volumeId(), upper.position().getX(), upper.position().getY(), upper.position().getZ()));
    }

    private static SkyforgeNeoForge1211ChunkAdapter terrain(io.github.nidaba.skyforge.world.SkyIslandWorldCatalog catalog) {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog, io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(), new SkyforgeMinecraftBlockPalette());
    }

    private static ProtoChunk realizedChunk(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyforgeCopperDepositAdapter.Deployment deployment) throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(deployment.chunkPos());
        try (AutoCloseable binding = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(binding);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk).orElseThrow();
        }
        return chunk;
    }
}
