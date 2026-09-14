package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

/** Deterministic, exact-owner, save/reload-style evidence for the DR-20 representative tranche. */
final class SkyforgeAuthoredVisibleHydrologyAdapterTest {
    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long[] REPRESENTATIVE_KEYS = {77L, 118L, 241L, 512L, 811L, 83L};

    @Test
    void canonicalSpecimenConsumesExactlyTheAuthoredKindsItActuallySupports() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var intent = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        var first = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        var replay = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);

        assertEquals(first, replay);
        assertEquals(expectedFeatures(intent), first.stream()
                .map(SkyforgeAuthoredVisibleHydrologyAdapter.Deployment::feature)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(intent.retainedWater().isEmpty(),
                "the locked DR-00 specimen must not gain a fabricated retained-waterbody intent");
        assertOwned(first, terrain);
    }

    @Test
    void boundedAcceptedFixturesCoverEveryRequiredHydrologyRiskThroughTheNormalLifecycle() throws Exception {
        for (var feature : SkyforgeAuthoredVisibleHydrologyAdapter.Feature.values()) {
            var fixture = representativeFixture(feature);
            var terrain = terrain(fixture.catalog(), fixture.descriptor());
            var deployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain);
            var target = deployments.stream()
                    .filter(candidate -> candidate.feature() == feature)
                    .findFirst()
                    .orElseThrow();

            assertOwned(List.of(target), terrain);
            if (feature == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE) {
                assertTrue(target.positions().size() >= 2);
            }

            var chunks = new java.util.HashMap<Long, ProtoChunk>();
            for (var position : target.positions()) {
                chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(), key -> {
                    try {
                        return realizedChunk(terrain, new net.minecraft.world.level.ChunkPos(key));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
            for (var position : target.positions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position).is(Blocks.WATER));
            }

            var replay = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain).stream()
                    .filter(candidate -> candidate.feature() == feature)
                    .findFirst()
                    .orElseThrow();
            assertEquals(target, replay);
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, replay))
                    .sum());
        }
    }

    @Test
    void canonicalLifecycleRealizesOnlyAvailableChunksAndReplayRetainsAuthoredWater() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        for (var deployment : deployments) {
            var chunks = new java.util.HashMap<Long, ProtoChunk>();
            for (var position : deployment.positions()) {
                chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(), key -> {
                    try {
                        return realizedChunk(terrain, new net.minecraft.world.level.ChunkPos(key));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
            for (var position : deployment.positions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position).is(Blocks.WATER));
            }

            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain))
                    .sum());

            var reloaded = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain);
            var reloadedDeployment = reloaded.stream()
                    .filter(candidate -> candidate.feature() == deployment.feature())
                    .findFirst()
                    .orElseThrow();
            assertEquals(deployment, reloadedDeployment);
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, reloadedDeployment))
                    .sum());
        }
    }

    @Test
    void retainedWaterCanBePersistedWithoutAChunkLifecycleBinding() {
        var fixture = representativeFixture(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployment = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                fixture.descriptor(), fixture.volume(), terrain).stream()
                .filter(candidate -> candidate.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .findFirst()
                .orElseThrow();
        var chunks = new java.util.HashMap<Long, ProtoChunk>();
        for (var position : deployment.positions()) {
            chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(),
                    key -> MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(key)));
        }

        assertTrue(chunks.values().stream()
                .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment))
                .sum() > 0);
        assertEquals(0, chunks.values().stream()
                .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment))
                .sum());
    }

    @Test
    void stackedVolumesKeepAuthoredWaterExactOwnerLocal() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var lower = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.lower(), terrain);
        var upper = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.upper(), terrain);

        assertNotEquals(fixture.lower().id(), fixture.upper().id());
        assertOwned(lower, terrain);
        assertOwned(upper, terrain);
    }

    private static java.util.Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> expectedFeatures(
            SkyIslandVisibleHydrologicRealizationPlan intent) {
        var expected = EnumSet.noneOf(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.class);
        if (!intent.channels().isEmpty()) {
            expected.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL);
        }
        if (!intent.retainedWater().isEmpty()) {
            expected.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER);
        }
        if (intent.drops().stream().anyMatch(drop ->
                drop.kind() == SkyIslandVisibleHydrologicRealizationKind.CASCADE
                        || drop.kind() == SkyIslandVisibleHydrologicRealizationKind.WATERFALL)) {
            expected.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE);
        }
        if (intent.drops().stream().anyMatch(drop ->
                drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)) {
            expected.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.EDGE_DISCHARGE);
        }
        return expected;
    }

    private static HydrologyFixture representativeFixture(
            SkyforgeAuthoredVisibleHydrologyAdapter.Feature feature) {
        for (long key : REPRESENTATIVE_KEYS) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(WORLD_SEED, 6L, 61L, key));
            var intent = SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
            if (supports(feature, intent)) {
                return physicalFixture(key, descriptor);
            }
        }
        throw new AssertionError("accepted representative corpus has no " + feature + " intent");
    }

    private static boolean supports(
            SkyforgeAuthoredVisibleHydrologyAdapter.Feature feature,
            SkyIslandVisibleHydrologicRealizationPlan intent) {
        return switch (feature) {
            case CHANNEL -> !intent.channels().isEmpty();
            case RETAINED_WATER -> !intent.retainedWater().isEmpty();
            case VERTICAL_DISCHARGE -> intent.drops().stream().anyMatch(drop ->
                    drop.kind() == SkyIslandVisibleHydrologicRealizationKind.CASCADE
                            || drop.kind() == SkyIslandVisibleHydrologicRealizationKind.WATERFALL);
            case EDGE_DISCHARGE -> intent.drops().stream().anyMatch(drop ->
                    drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE);
        };
    }

    private static HydrologyFixture physicalFixture(long key, SkyIslandDescriptor descriptor) {
        double radius = descriptor.nominalRadius();
        long physicalSeed = 780000L + key;
        SkyIslandVolumeDescriptor physicalDescriptor = SkyIslandVolumeDescriptor.schema2(
                physicalSeed,
                0.0,
                0.0,
                220.0,
                radius,
                58.0,
                82.0,
                Math.min(54.0, radius * 0.18),
                0.0,
                0.24,
                0.62,
                0.0,
                descriptor.morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physicalDescriptor);
        var id = new SkyIslandWorldVolumeId(
                WORLD_SEED, "dr20-hydrology-risk-" + key, 0, 0, physicalSeed);
        var bounds = new WorldBounds(
                -radius * 1.08,
                radius * 1.08,
                110.0,
                300.0,
                -radius * 1.08,
                radius * 1.08);
        var volume = new SkyIslandWorldVolume(id, bounds, compiled);
        return new HydrologyFixture(
                descriptor,
                volume,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of(volume)));
    }

    private static void assertOwned(
            List<SkyforgeAuthoredVisibleHydrologyAdapter.Deployment> deployments,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        for (var deployment : deployments) {
            assertFalse(deployment.positions().isEmpty());
            for (var position : deployment.positions()) {
                assertTrue(terrain.isSolidOwnedBy(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
            }
        }
    }

    private static SkyforgeNeoForge1211ChunkAdapter terrain(
            SkyIslandWorldCatalog catalog,
            SkyIslandDescriptor descriptor) {
        var authoredDescriptors = new java.util.LinkedHashMap<
                SkyIslandWorldVolumeId,
                SkyIslandDescriptor>();
        for (var volume : catalog.volumes()) {
            authoredDescriptors.put(volume.id(), descriptor);
        }
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                authoredDescriptors);
    }

    private static ProtoChunk realizedChunk(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            net.minecraft.world.level.ChunkPos pos) throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(pos);
        try (AutoCloseable installedSurfaceStage = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(installedSurfaceStage);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }
        return chunk;
    }

    private record HydrologyFixture(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog) {}
}
