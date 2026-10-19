package io.github.nidaba.skyforge.neoforge1211;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/** Selects an exact shared surface column, optionally requiring the active provider biome. */
final class SkyforgeSharedSurfaceBiomeSampleSelector {
    private SkyforgeSharedSurfaceBiomeSampleSelector() {}

    static SearchResult select(
            ChunkPos chunkPos,
            List<Column> sharedColumns,
            int minimumSharedColumns,
            ResourceKey<Biome> requiredProviderBiome) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(sharedColumns, "sharedColumns");
        if (minimumSharedColumns <= 0 || minimumSharedColumns > 256) {
            throw new IllegalArgumentException("minimum shared columns must be within 1..256");
        }
        int matches = 0;
        Column selected = null;
        Comparator<Column> preference = Comparator
                .comparingInt((Column column) -> manhattanDistance(chunkPos, column))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z);
        for (Column column : sharedColumns) {
            Objects.requireNonNull(column, "shared column");
            if (column.x() < chunkPos.getMinBlockX() || column.x() > chunkPos.getMaxBlockX()
                    || column.z() < chunkPos.getMinBlockZ() || column.z() > chunkPos.getMaxBlockZ()) {
                throw new IllegalArgumentException("shared surface candidate is outside its chunk");
            }
            if (requiredProviderBiome != null) {
                if (column.providerBiome() == null) {
                    throw new IllegalArgumentException("provider biome is required for every provider-mode candidate");
                }
                if (!requiredProviderBiome.equals(column.providerBiome())) {
                    continue;
                }
                matches++;
            }
            if (selected == null || preference.compare(column, selected) < 0) {
                selected = column;
            }
        }
        boolean enoughSharedTerrain = sharedColumns.size() >= minimumSharedColumns;
        return new SearchResult(
                sharedColumns.size(),
                matches,
                enoughSharedTerrain && selected != null ? Optional.of(selected) : Optional.empty());
    }

    private static int manhattanDistance(ChunkPos chunkPos, Column column) {
        return Math.abs(column.x() - chunkPos.getMiddleBlockX())
                + Math.abs(column.z() - chunkPos.getMiddleBlockZ());
    }

    record Column(int x, int z, int lowerSurfaceY, int upperSurfaceY, ResourceKey<Biome> providerBiome) {
        Column {
            if (lowerSurfaceY == upperSurfaceY) {
                throw new IllegalArgumentException("stacked surface Y values must differ");
            }
        }
    }

    record SearchResult(int sharedColumns, int providerBiomeMatches, Optional<Column> selected) {
        SearchResult {
            if (sharedColumns < 0 || providerBiomeMatches < 0 || providerBiomeMatches > sharedColumns) {
                throw new IllegalArgumentException("invalid shared surface search counts");
            }
            Objects.requireNonNull(selected, "selected");
        }
    }
}
