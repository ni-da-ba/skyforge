package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

/** Machine evidence for issue #491's intentionally single-deposit Iron realization tranche. */
final class SkyforgeIronDepositAdapterTest {
    private static final SkyforgeIronDepositAdapter.Specification SPECIFICATION =
            SkyforgeIronDepositAdapter.Specification.representative();

    @Test
    void productionIronIsEligibleDeterministicExplicitAndActuallySurfaceAccessible() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());

        var first = SkyforgeIronDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        var replay = SkyforgeIronDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();

        assertEquals(first, replay, "the production deposit address is deterministic");
        assertEquals(1, first.specification().blockCount());
        assertEquals(SkyforgeIronDepositAdapter.Grade.STANDARD, first.specification().grade());
        assertEquals(SkyforgeIronDepositAdapter.Accessibility.SURFACE_EXPOSED, first.specification().accessibility());
        assertTrue(terrain.isSolidOwnedBy(first.volumeId(), first.position().getX(), first.position().getY(), first.position().getZ()));
        assertFalse(terrain.isSolidOwnedByOtherVolume(first.volumeId(), first.position().getX(), first.position().getY(), first.position().getZ()));
        assertEquals(
                terrain.integerSolidRange(first.volumeId(), first.position().getX(), first.position().getZ())
                        .orElseThrow().maximumY(),
                first.position().getY(),
                "the bounded specimen is a real exposed production-geography top block");
    }

    @Test
    void zeroOpportunityFailsClosedRatherThanInjectingIron() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var source = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor()).sourcePlan();
        var zeroCells = source.cells().stream()
                .map(cell -> new io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityCell(cell, 0.0, 0.0, 0.0))
                .toList();
        var constructor = io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile.class
                .getDeclaredConstructor(io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyPlan.class, java.util.List.class);
        constructor.setAccessible(true);
        var zero = (io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile) constructor.newInstance(source, zeroCells);

        assertEquals(0.0, zero.peakOpportunity(io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind.IRON));
        assertTrue(SkyforgeIronDepositAdapter.plan(
                zero, fixture.volume(), terrain(fixture.catalog()), SPECIFICATION).isEmpty());
    }


    @Test
    void placementIsIdempotentAndSaveReloadReproducesTheAuthoritativeBlock() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var deployment = SkyforgeIronDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        ProtoChunk chunk = realizedChunk(terrain, deployment);

        assertTrue(SkyforgeIronDepositAdapter.apply(chunk, deployment).writtenNow());
        assertFalse(SkyforgeIronDepositAdapter.apply(chunk, deployment).writtenNow());
        assertTrue(chunk.getBlockState(deployment.position()).is(Blocks.IRON_ORE));

        // Chunk block state is persisted by Minecraft; rebuilding the deterministic authoritative
        // deployment after reload identifies exactly the same saved coordinate and replays cleanly.
        var reloaded = SkyforgeIronDepositAdapter.plan(profile, fixture.volume(), terrain, SPECIFICATION).orElseThrow();
        assertEquals(deployment, reloaded);
        assertFalse(SkyforgeIronDepositAdapter.apply(chunk, reloaded).writtenNow());
    }

    @Test
    void stackedProductionVolumesRemainExactOwnerIsolated() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();
        var terrain = terrain(fixture.catalog());
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var lower = SkyforgeIronDepositAdapter.plan(profile, fixture.lower(), terrain, SPECIFICATION).orElseThrow();
        var upper = SkyforgeIronDepositAdapter.plan(profile, fixture.upper(), terrain, SPECIFICATION).orElseThrow();

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
            SkyforgeIronDepositAdapter.Deployment deployment) throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(deployment.chunkPos());
        try (AutoCloseable binding = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            SkyforgeNeoForge1211SurfaceStage.realize(chunk).orElseThrow();
        }
        return chunk;
    }
}
