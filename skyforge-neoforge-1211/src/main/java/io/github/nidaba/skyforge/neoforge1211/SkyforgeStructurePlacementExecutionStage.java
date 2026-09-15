package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Thread-confined exact-volume fence for native StructureStart placement. */
final class SkyforgeStructurePlacementExecutionStage {
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private SkyforgeStructurePlacementExecutionStage() {}

    static Scope open(SkyIslandWorldVolumeId volumeId, BoundingBox placementBounds) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(placementBounds, "placementBounds");
        SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("native structure placement requires an active Skyforge runtime binding");
        }
        Predicate<BlockPos> foreignSolid = position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                        volumeId, position.getX(), position.getY(), position.getZ())
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge runtime binding disappeared during native structure placement"));
        return open(volumeId, placementBounds, foreignSolid);
    }

    static Scope openForTest(
            SkyIslandWorldVolumeId volumeId,
            BoundingBox placementBounds,
            Predicate<BlockPos> foreignSolid) {
        Objects.requireNonNull(volumeId, "volumeId");
        SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);
        return open(volumeId, placementBounds, foreignSolid);
    }

    private static Scope open(
            SkyIslandWorldVolumeId volumeId,
            BoundingBox placementBounds,
            Predicate<BlockPos> foreignSolid) {
        Objects.requireNonNull(placementBounds, "placementBounds");
        Objects.requireNonNull(foreignSolid, "foreignSolid");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested native structure placement scopes are not supported");
        }
        Execution execution = new Execution(volumeId, placementBounds, foreignSolid);
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    static boolean isVisible(BlockPos position) {
        Execution execution = ACTIVE.get();
        return execution == null || execution.accepts(position);
    }

    static boolean canWrite(BlockPos position) {
        Execution execution = ACTIVE.get();
        return execution == null || execution.accepts(position);
    }

    private record Execution(
            SkyIslandWorldVolumeId volumeId,
            BoundingBox placementBounds,
            Predicate<BlockPos> foreignSolid) {
        private Execution {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(placementBounds, "placementBounds");
            Objects.requireNonNull(foreignSolid, "foreignSolid");
        }

        private boolean accepts(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return placementBounds.isInside(position) && !foreignSolid.test(position);
        }
    }

    static final class Scope implements AutoCloseable {
        private final Execution execution;
        private boolean closed;

        private Scope(Execution execution) {
            this.execution = execution;
        }

        @Override
        public void close() {
            if (closed || ACTIVE.get() != execution) {
                throw new IllegalStateException("native structure placement scope is not active");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
