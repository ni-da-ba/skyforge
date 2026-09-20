package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/**
 * CI-executed physical admissibility gate for the ranked AUTH-0105 DR-70 review finalists.
 *
 * <p>The Authorship ranking is already accepted. This test adds only the Minecraft-facing
 * requirement discovered by runtime qualification: the bounded physical volume must realize at
 * least one dry exterior cave-mouth witness while retaining the required hydrology workload.
 */
final class SkyforgeDr70SpecimenSearchTest {
    private static final long[] RANKED_AUTH_0105_FINALISTS = {
        287L, 2839L, 999L, 449L, 649L, 3990L, 421L, 2820L
    };

    @Test
    void selectedReviewSpecimenIsFirstRankedFinalistWithPhysicalDryCaveMouth() {
        Candidate selected = null;
        for (long key : RANKED_AUTH_0105_FINALISTS) {
            Candidate candidate = characterize(key);
            if (candidate != null) {
                selected = candidate;
                break;
            }
        }

        assertNotNull(
                selected,
                "none of the accepted AUTH-0105 finalists satisfies bounded hydrology + physical dry cave-mouth constraints");
        assertEquals(
                selected.key(),
                SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review().islandKey(),
                () -> "DR-70 review fixture must use first physically admissible AUTH-0105 finalist: " + selected);
    }

    private static Candidate characterize(long key) {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70ReviewCandidate(key);
        if (fixture.descriptor().nominalRadius() >= 120.0
                || fixture.field().exposureGeometry().connectionCount() <= 0) {
            return null;
        }

        var visible = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        long interiorDrops = visible.drops().stream()
                .filter(drop -> drop.kind() != SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .count();
        if (visible.channels().size() < 20 || interiorDrops < 1) {
            return null;
        }

        BlockPos mouth = physicalDryMouth(fixture);
        return mouth == null
                ? null
                : new Candidate(
                        key,
                        fixture.descriptor().nominalRadius(),
                        visible.channels().size(),
                        interiorDrops,
                        fixture.field().exposureGeometry().connectionCount(),
                        mouth);
    }

    private static BlockPos physicalDryMouth(
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture) {
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        Set<Long> wet = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .flatMap(deployment -> deployment.positions().stream())
                .map(BlockPos::asLong)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                fixture.field(),
                new SkyIslandCompiledVolumeColumnField(fixture.volume().compiledVolume()));
        var spatial = SkyforgeExteriorConnectedCaveSpatialIndex.create(fixture.field());

        LinkedHashSet<Long> candidateChunks = new LinkedHashSet<>();
        var physical = fixture.volume().compiledVolume().descriptor();
        for (var connection : fixture.field().exposureGeometry().connections()) {
            var mouth = realized.transform()
                    .toPhysical(connection.mouthPoint().position())
                    .orElseThrow(() -> new IllegalStateException(
                            "accepted exposure mouth has no physical column for candidate " + fixture.islandKey()));
            int worldX = (int) Math.floor(physical.centerX() + mouth.localX());
            int worldZ = (int) Math.floor(physical.centerZ() + mouth.localZ());
            int centerChunkX = Math.floorDiv(worldX, 16);
            int centerChunkZ = Math.floorDiv(worldZ, 16);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    candidateChunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
                }
            }
        }

        int minBuildY = (int) Math.floor(fixture.volume().bounds().minimumY());
        int maxBuildYExclusive = (int) Math.ceil(fixture.volume().bounds().maximumY()) + 1;
        for (long chunkKey : candidateChunks) {
            var cursor = new SkyforgeExteriorConnectedCavePreparationCursor(
                    fixture.volume(),
                    realized,
                    spatial,
                    new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)),
                    minBuildY,
                    maxBuildYExclusive,
                    position -> terrain.isSolidOwnedBy(
                            fixture.volume().id(),
                            position.getX(),
                            position.getY(),
                            position.getZ()),
                    position -> terrain.isSolidOwnedByOtherVolume(
                            fixture.volume().id(),
                            position.getX(),
                            position.getY(),
                            position.getZ()));
            while (!cursor.complete()) {
                cursor.advance(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            var prepared = cursor.prepared();
            if (prepared.firstMouthPosition() != null
                    && !wet.contains(prepared.firstMouthPosition().asLong())) {
                return prepared.firstMouthPosition();
            }
        }
        return null;
    }

    private record Candidate(
            long key,
            double radius,
            int channels,
            long interiorDrops,
            int abstractConnections,
            BlockPos physicalDryMouth) {}
}
