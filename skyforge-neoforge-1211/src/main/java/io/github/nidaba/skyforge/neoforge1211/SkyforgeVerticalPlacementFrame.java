package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Thread-confined vertical coordinate frame for native underground placement inside one exact
 * Skyforge volume.
 *
 * <p>Minecraft placed features commonly sample absolute dimension Y coordinates. A floating
 * Skyforge volume can occupy an unrelated altitude, so those samples must be interpreted in the
 * owning volume's local vertical frame before configured features inspect or write terrain. The
 * transform is monotone and affine across the dimension build span. Samples outside that native
 * span clamp to its nearest endpoint before mapping, which keeps every transformed position inside
 * the admitted volume envelope without changing feature ordering or consuming any additional random
 * values.
 *
 * <p>The first admitted scope is deliberately narrow: only
 * {@link GenerationStep.Decoration#UNDERGROUND_ORES}. Surface population and BASE_WORLD generation
 * never open this frame and therefore retain vanilla coordinates exactly.
 */
public final class SkyforgeVerticalPlacementFrame {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private SkyforgeVerticalPlacementFrame() {}

    static Scope open(WorldGenLevel level, SkyforgePopulationOperation operation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(operation, "operation");
        if (operation.generationStep() != GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()) {
            return Scope.inactive();
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge vertical placement frames are not supported");
        }

        int sourceMinimumY = level.getMinBuildHeight();
        int sourceMaximumY = Math.addExact(sourceMinimumY, Math.subtractExact(level.getHeight(), 1));
        WorldBounds bounds = SkyforgeNeoForge1211SurfaceStage.volumeBounds(operation.volumeId())
                .orElseThrow(() -> new IllegalStateException(
                        "vertical placement frame requires runtime bounds for " + operation.volumeId().path()));
        int targetMinimumY = Math.max(sourceMinimumY, ceilToInt(bounds.minimumY()));
        int targetMaximumY = Math.min(sourceMaximumY, floorToInt(bounds.maximumY()));
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalStateException(
                    "Skyforge volume has no vertical overlap with the active Minecraft build range: "
                            + operation.volumeId().path());
        }

        Frame frame = new Frame(
                operation.volumeId().path(),
                sourceMinimumY,
                sourceMaximumY,
                targetMinimumY,
                targetMaximumY);
        ACTIVE.set(frame);
        return new Scope(frame);
    }

    /** Returns whether an underground exact-volume frame is active on the current thread. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /**
     * Maps one native HeightRangePlacement result into the active exact-volume vertical frame.
     *
     * <p>X/Z are preserved exactly. Outside an explicit underground frame, the original immutable
     * position object is returned unchanged.
     */
    public static BlockPos mapHeightRangePosition(BlockPos position) {
        Objects.requireNonNull(position, "position");
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return position;
        }
        int mappedY = frame.mapY(position.getY());
        return mappedY == position.getY()
                ? position
                : new BlockPos(position.getX(), mappedY, position.getZ());
    }

    /** Snapshot used by deterministic runtime evidence without exposing mutable frame state. */
    static Snapshot snapshot() {
        Frame frame = ACTIVE.get();
        if (frame == null) {
            throw new IllegalStateException("no Skyforge vertical placement frame is active");
        }
        return new Snapshot(
                frame.volumePath(),
                frame.sourceMinimumY(),
                frame.sourceMaximumY(),
                frame.targetMinimumY(),
                frame.targetMaximumY());
    }

    static int mapYForTest(
            int nativeY,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        return new Frame(
                        "test",
                        sourceMinimumY,
                        sourceMaximumY,
                        targetMinimumY,
                        targetMaximumY)
                .mapY(nativeY);
    }

    private static int ceilToInt(double value) {
        double rounded = Math.ceil(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalStateException("Skyforge volume minimum Y is outside Minecraft integer coordinates: " + value);
        }
        return (int) rounded;
    }

    private static int floorToInt(double value) {
        double rounded = Math.floor(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalStateException("Skyforge volume maximum Y is outside Minecraft integer coordinates: " + value);
        }
        return (int) rounded;
    }

    record Snapshot(
            String volumePath,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        Snapshot {
            Objects.requireNonNull(volumePath, "volumePath");
        }
    }

    private record Frame(
            String volumePath,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        private Frame {
            Objects.requireNonNull(volumePath, "volumePath");
            if (sourceMaximumY < sourceMinimumY) {
                throw new IllegalArgumentException("source vertical frame must be ordered");
            }
            if (targetMaximumY < targetMinimumY) {
                throw new IllegalArgumentException("target vertical frame must be ordered");
            }
        }

        private int mapY(int nativeY) {
            if (targetMinimumY == targetMaximumY || sourceMinimumY == sourceMaximumY) {
                return targetMinimumY;
            }
            int clamped = Math.max(sourceMinimumY, Math.min(sourceMaximumY, nativeY));
            long sourceOffset = (long) clamped - sourceMinimumY;
            long sourceSpan = (long) sourceMaximumY - sourceMinimumY;
            long targetSpan = (long) targetMaximumY - targetMinimumY;
            long numerator = Math.multiplyExact(sourceOffset, targetSpan);
            long mappedOffset = Math.floorDiv(Math.addExact(numerator, sourceSpan / 2L), sourceSpan);
            return Math.toIntExact(Math.addExact((long) targetMinimumY, mappedOffset));
        }
    }

    static final class Scope implements AutoCloseable {
        private final Frame frame;
        private boolean closed;

        private Scope(Frame frame) {
            this.frame = frame;
        }

        private static Scope inactive() {
            return new Scope(null);
        }

        void requireActive() {
            if (closed) {
                throw new IllegalStateException("Skyforge vertical placement frame scope is closed");
            }
            if (frame != null && ACTIVE.get() != frame) {
                throw new IllegalStateException("Skyforge vertical placement frame changed before scope completion");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            if (frame != null) {
                ACTIVE.remove();
            }
        }
    }
}
