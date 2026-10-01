package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Runtime binding that composes accepted native structure creation and placement for one exact volume. */
final class SkyforgeNativeStructureRuntimeOperation {
    private SkyforgeNativeStructureRuntimeOperation() {}

    static Result execute(ServerLevel level, LevelChunk chunk, SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(volumeId, "volumeId");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("native structure runtime target belongs to another level");
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
            throw new IllegalStateException("native structure runtime operation requires an admitted exact volume");
        }

        var chunkSource = level.getChunkSource();
        if (!(chunkSource.getGenerator() instanceof SkyforgeNoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("native structure runtime operation requires SkyforgeNoiseBasedChunkGenerator");
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var saved = SkyforgeNativeStructurePlacementSavedData.forLevel(level);
        Map<Structure, StructureStart> before = new HashMap<>(chunk.getAllStarts());

        var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
        int registered = 0;
        int placed = 0;
        try {
            generator.createStructuresForExactSkyforgeVolume(
                    level.registryAccess(),
                    chunkSource.getGeneratorState(),
                    level.structureManager(),
                    chunk,
                    level.getStructureManager(),
                    volumeId);

            for (var entry : chunk.getAllStarts().entrySet()) {
                StructureStart start = entry.getValue();
                if (start == null || !start.isValid()) {
                    continue;
                }
                var identity = identity(volumeId, chunk, registry.getKey(entry.getKey()), start);
                StructureStart previous = before.get(entry.getKey());
                if (previous == null || !sameStart(previous, start)) {
                    if (saved.registerOwned(identity)) {
                        registered++;
                    }
                }
            }

            Map<SkyforgeNativeStructurePlacementSavedData.PlacementIdentity, StructureStart> current = new HashMap<>();
            for (var entry : chunk.getAllStarts().entrySet()) {
                StructureStart start = entry.getValue();
                if (start != null && start.isValid()) {
                    current.put(identity(volumeId, chunk, registry.getKey(entry.getKey()), start), start);
                }
            }
            for (var identity : saved.ownedFor(volumeId, chunk.getPos().toLong())) {
                if (saved.completed(identity)) {
                    continue;
                }
                StructureStart start = current.get(identity);
                if (start == null) {
                    throw new IllegalStateException("owned native Skyforge structure start disappeared before placement");
                }
                generator.placeStructureStartForExactSkyforgeVolume(
                        level, chunk, level.structureManager(), start, volumeId);
                saved.complete(identity);
                placed++;
            }
        } finally {
            domain.close();
        }
        return new Result(registered, placed);
    }

    private static SkyforgeNativeStructurePlacementSavedData.PlacementIdentity identity(
            SkyIslandWorldVolumeId volumeId,
            LevelChunk chunk,
            net.minecraft.resources.ResourceLocation structureId,
            StructureStart start) {
        if (structureId == null) {
            throw new IllegalStateException("native structure start has no registry identity");
        }
        var bounds = start.getBoundingBox();
        return new SkyforgeNativeStructurePlacementSavedData.PlacementIdentity(
                volumeId,
                chunk.getPos().toLong(),
                structureId,
                start.getChunkPos().toLong(),
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static boolean sameStart(StructureStart left, StructureStart right) {
        return left.getChunkPos().equals(right.getChunkPos())
                && left.getStructure() == right.getStructure()
                && left.getBoundingBox().equals(right.getBoundingBox());
    }

    record Result(int registeredStarts, int placedStarts) {
        Result {
            if (registeredStarts < 0 || placedStarts < 0) {
                throw new IllegalArgumentException("native structure runtime counts must be nonnegative");
            }
        }
    }
}
