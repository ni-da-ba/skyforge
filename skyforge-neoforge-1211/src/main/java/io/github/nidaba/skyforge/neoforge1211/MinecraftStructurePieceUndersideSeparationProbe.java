package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Proof-grade probe for native piece geometry that is wholly beneath one exact Skyforge underside.
 *
 * <p>Unlike the sparse descriptive SF-IMP-0048 observer, this probe samples every integer
 * coordinate represented by a Minecraft piece bounding box. It returns evidence only when every
 * sampled coordinate is at or below the supporting volume's underside. Mixed, solid,
 * above-surface, and open-between-surfaces observations are deliberately non-proofs.
 */
final class MinecraftStructurePieceUndersideSeparationProbe {
    private static final long MAXIMUM_PROOF_SAMPLE_COUNT = 1_000_000L;

    private MinecraftStructurePieceUndersideSeparationProbe() {}

    static List<MinecraftStructurePieceUndersideSeparationEvidence> probe(
            StructureStart start,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(volumeId, "volumeId");
        ArrayList<MinecraftStructurePieceUndersideSeparationEvidence> result = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            probe(piece.getBoundingBox(), volumeId).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    static Optional<MinecraftStructurePieceUndersideSeparationEvidence> probe(
            BoundingBox box,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(volumeId, "volumeId");
        if (!withinProofBudget(box)) {
            return Optional.empty();
        }

        var requirements = MinecraftStructureTerrainObservationPolicy.proofRequirements(box);
        TerrainBoxObservation observation = SkyforgeNeoForge1211SurfaceStage.observeTerrainBox(
                        volumeId,
                        requirements)
                .orElseThrow(() -> new IllegalStateException(
                        "underside separation probing requires an active Skyforge runtime binding"));
        return classify(requirements.bounds(), volumeId, observation);
    }

    static Optional<MinecraftStructurePieceUndersideSeparationEvidence> classify(
            io.github.nidaba.skyforge.world.WorldBounds pieceBounds,
            SkyIslandWorldVolumeId volumeId,
            TerrainBoxObservation observation) {
        Objects.requireNonNull(pieceBounds, "pieceBounds");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(observation, "observation");
        if (!volumeId.equals(observation.observedVolumeId())) {
            throw new IllegalArgumentException("observation volume differs from requested supporting volume");
        }
        if (!observation.allSamplesAtOrBelowUndersideSurface()) {
            return Optional.empty();
        }
        return Optional.of(new MinecraftStructurePieceUndersideSeparationEvidence(
                volumeId,
                pieceBounds,
                observation));
    }

    private static boolean withinProofBudget(BoundingBox box) {
        try {
            long x = Math.addExact(Math.subtractExact((long) box.maxX(), box.minX()), 1L);
            long y = Math.addExact(Math.subtractExact((long) box.maxY(), box.minY()), 1L);
            long z = Math.addExact(Math.subtractExact((long) box.maxZ(), box.minZ()), 1L);
            long count = Math.multiplyExact(Math.multiplyExact(x, y), z);
            return count <= MAXIMUM_PROOF_SAMPLE_COUNT;
        } catch (ArithmeticException exception) {
            return false;
        }
    }
}
