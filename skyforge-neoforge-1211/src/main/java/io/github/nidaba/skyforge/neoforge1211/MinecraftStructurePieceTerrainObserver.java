package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Read-only native-piece observer backed by the active compiled Skyforge runtime.
 *
 * <p>Every ordinary native `StructurePiece` is treated identically. This class records sampled
 * physical relationships only; it does not decide whether a piece or structure is valid.
 */
final class MinecraftStructurePieceTerrainObserver {
    private MinecraftStructurePieceTerrainObserver() {}

    static List<PieceObservation> observe(
            StructureStart start,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(volumeId, "volumeId");
        ArrayList<PieceObservation> result = new ArrayList<>(start.getPieces().size());
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            var requirements = MinecraftStructureTerrainObservationPolicy.requirements(box);
            TerrainBoxObservation observation = SkyforgeNeoForge1211SurfaceStage.observeTerrainBox(
                            volumeId,
                            requirements)
                    .orElseThrow(() -> new IllegalStateException(
                            "native piece terrain observation requires an active Skyforge runtime binding"));
            result.add(new PieceObservation(requirements.bounds(), observation));
        }
        return List.copyOf(result);
    }

    record PieceObservation(
            WorldBounds pieceBounds,
            TerrainBoxObservation observation) {
        PieceObservation {
            Objects.requireNonNull(pieceBounds, "pieceBounds");
            Objects.requireNonNull(observation, "observation");
        }
    }
}
