package io.github.nidaba.skyforge.neoforge1211;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/** Selects exact shared surface columns against the active provider biome at both island surfaces. */
final class SkyforgeSharedSurfaceBiomeSampleSelector {
    private SkyforgeSharedSurfaceBiomeSampleSelector() {}

    static SearchResult select(
            ChunkPos chunkPos,
            List<Column> sharedColumns,
            int minimumSharedColumns,
            ResourceKey<Biome> requiredLowerProviderBiome,
            ResourceKey<Biome> requiredUpperProviderBiome) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(sharedColumns, "sharedColumns");
        if (minimumSharedColumns <= 0 || minimumSharedColumns > 256) {
            throw new IllegalArgumentException("minimum shared columns must be within 1..256");
        }
        int lowerMatches = 0;
        int upperMatches = 0;
        int pairMatches = 0;
        Column selected = null;
        Comparator<Column> preference = Comparator
                .comparing(Column::lowerTreePlantingSupport).reversed()
                .thenComparingInt(column -> manhattanDistance(chunkPos, column))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z);
        for (Column column : sharedColumns) {
            Objects.requireNonNull(column, "shared column");
            if (column.x() < chunkPos.getMinBlockX() || column.x() > chunkPos.getMaxBlockX()
                    || column.z() < chunkPos.getMinBlockZ() || column.z() > chunkPos.getMaxBlockZ()) {
                throw new IllegalArgumentException("shared surface candidate is outside its chunk");
            }

            boolean lowerMatchesRequired = requiredLowerProviderBiome == null;
            if (requiredLowerProviderBiome != null) {
                if (column.lowerProviderBiome() == null) {
                    throw new IllegalArgumentException("lower provider biome is required for every provider-mode candidate");
                }
                lowerMatchesRequired = requiredLowerProviderBiome.equals(column.lowerProviderBiome());
                if (lowerMatchesRequired) {
                    lowerMatches++;
                }
            }

            boolean upperMatchesRequired = requiredUpperProviderBiome == null;
            if (requiredUpperProviderBiome != null) {
                if (column.upperProviderBiome() == null) {
                    throw new IllegalArgumentException("upper provider biome is required for every provider-mode candidate");
                }
                upperMatchesRequired = requiredUpperProviderBiome.equals(column.upperProviderBiome());
                if (upperMatchesRequired) {
                    upperMatches++;
                }
            }

            if (requiredLowerProviderBiome != null
                    && requiredUpperProviderBiome != null
                    && lowerMatchesRequired
                    && upperMatchesRequired) {
                pairMatches++;
            }
            if (lowerMatchesRequired && upperMatchesRequired
                    && (selected == null || preference.compare(column, selected) < 0)) {
                selected = column;
            }
        }
        boolean enoughSharedTerrain = sharedColumns.size() >= minimumSharedColumns;
        return new SearchResult(
                sharedColumns.size(),
                lowerMatches,
                upperMatches,
                pairMatches,
                enoughSharedTerrain && selected != null ? Optional.of(selected) : Optional.empty());
    }

    private static int manhattanDistance(ChunkPos chunkPos, Column column) {
        return Math.abs(column.x() - chunkPos.getMiddleBlockX())
                + Math.abs(column.z() - chunkPos.getMiddleBlockZ());
    }

    record Column(
            int x,
            int z,
            int lowerSurfaceY,
            int upperSurfaceY,
            ResourceKey<Biome> lowerProviderBiome,
            ResourceKey<Biome> upperProviderBiome,
            boolean lowerTreePlantingSupport) {
        Column(
                int x,
                int z,
                int lowerSurfaceY,
                int upperSurfaceY,
                ResourceKey<Biome> lowerProviderBiome,
                ResourceKey<Biome> upperProviderBiome) {
            this(x, z, lowerSurfaceY, upperSurfaceY, lowerProviderBiome, upperProviderBiome, false);
        }

        Column {
            if (lowerSurfaceY == upperSurfaceY) {
                throw new IllegalArgumentException("stacked surface Y values must differ");
            }
        }
    }

    record SearchResult(
            int sharedColumns,
            int lowerProviderBiomeMatches,
            int providerBiomeMatches,
            int providerBiomePairMatches,
            Optional<Column> selected) {
        SearchResult {
            if (sharedColumns < 0
                    || lowerProviderBiomeMatches < 0
                    || providerBiomeMatches < 0
                    || providerBiomePairMatches < 0
                    || lowerProviderBiomeMatches > sharedColumns
                    || providerBiomeMatches > sharedColumns
                    || providerBiomePairMatches > sharedColumns) {
                throw new IllegalArgumentException("invalid shared surface search counts");
            }
            Objects.requireNonNull(selected, "selected");
        }
    }
}
