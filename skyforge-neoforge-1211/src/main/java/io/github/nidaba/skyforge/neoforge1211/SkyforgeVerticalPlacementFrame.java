package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Thread-confined vertical coordinate frame for native underground/local placement inside one exact
 * Skyforge volume.
 *
 * <p>Minecraft placed features commonly sample absolute dimension Y coordinates. A floating
 * Skyforge volume can occupy an unrelated altitude, so those samples must be interpreted in the
 * owning volume's local vertical frame before configured features inspect or write terrain. The
 * transform is monotone and affine across the dimension build span and consumes no additional
 * random values.
 *
 * <p>Phase admission is deliberately explicit. SF-IMP-0059 admitted
 * {@link GenerationStep.Decoration#UNDERGROUND_ORES}; that accepted path continues to map against
 * the finite volume envelope exactly as before. SF-IMP-0060 additionally admitted
 * {@link GenerationStep.Decoration#LOCAL_MODIFICATIONS}. SF-IMP-0062 admits
 * {@link GenerationStep.Decoration#UNDERGROUND_DECORATION} after SF-IMP-0061 established persistent
 * owner-local cave topology. SF-IMP-0063 admits {@link GenerationStep.Decoration#FLUID_SPRINGS}
 * against the same exact compiled owner-column support; asynchronous propagation is separately
 * fenced by the generated-fluid provenance stage. SF-IMP-0064 admits
 * {@link GenerationStep.Decoration#LAKES}; lake placement uses the exact owner-column frame and a
 * separate whole-footprint admission gate before native LakeFeature mutation begins.
 *
 * <p>Sparse local modifications and cave-surface decoration use the exact compiled solid span of
 * their already-selected X/Z column when one exists, because a conservative volume bounding box
 * may contain large amounts of non-owned air below an island. The compiled support span remains
 * stable after carving, while ordinary Level reads still expose the actual post-carver AIR state;
 * native cave predicates can therefore discover real interior surfaces without changing X/Z,
 * feature identity, placement ordering, or RNG consumption. If the sampled X/Z has no owner
 * column, the ordinary envelope mapping is retained and the existing exact write fence remains
 * authoritative.
 *
 * <p>Surface population and BASE_WORLD generation remain outside this frame unless separately
 * admitted by their established surface scheduler.
 */
public final class SkyforgeVerticalPlacementFrame {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private SkyforgeVerticalPlacementFrame() {}

    static Scope open(WorldGenLevel level, SkyforgePopulationOperation operation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(operation, "operation");
        if (!usesLocalVerticalFrame(operation.generationStep())) {
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
                operation.volumeId(),
                operation.volumeId().path(),
                sourceMinimumY,
                sourceMaximumY,
                targetMinimumY,
                targetMaximumY,
                usesExactSolidColumnFrame(operation.generationStep()));
        ACTIVE.set(frame);
        return new Scope(frame);
    }

    /** Returns whether a generation step is explicitly admitted to the local vertical frame. */
    static boolean usesLocalVerticalFrame(int generationStep) {
        return generationStep == GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()
                || generationStep == GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()
                || generationStep == GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                || generationStep == GenerationStep.Decoration.FLUID_SPRINGS.ordinal()
                || generationStep == GenerationStep.Decoration.LAKES.ordinal();
    }

    /**
     * Returns whether one admitted phase maps against exact solid owner-column support.
     *
     * <p>SF-IMP-0059 ore mapping remains byte-for-byte on the accepted finite volume-envelope frame.
     * SF-IMP-0060 local modifications, SF-IMP-0062 cave-surface decoration, and SF-IMP-0063 fluid
     * springs and SF-IMP-0064 lakes opt into the stricter compiled owner-column support frame.
     */
    static boolean usesExactSolidColumnFrame(int generationStep) {
        return generationStep == GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()
                || generationStep == GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                || generationStep == GenerationStep.Decoration.FLUID_SPRINGS.ordinal()
                || generationStep == GenerationStep.Decoration.LAKES.ordinal();
    }

    /** Returns whether an exact-volume local vertical frame is active on the current thread. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /**
     * Maps one native HeightRangePlacement result into the active exact-volume vertical frame.
     *
     * <p>X/Z are preserved exactly. Outside an explicitly admitted frame, the original immutable
     * position object is returned unchanged.
     */
    public static BlockPos mapHeightRangePosition(BlockPos position) {
        Objects.requireNonNull(position, "position");
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return position;
        }
        int mappedY = frame.mapY(position);
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
        return mapY(
                nativeY,
                sourceMinimumY,
                sourceMaximumY,
                targetMinimumY,
                targetMaximumY);
    }

    static Optional<TargetSpan> findSolidSpanForTest(
            int minimumY,
            int maximumY,
            IntPredicate ownerSolidAtY) {
        return findSolidSpan(minimumY, maximumY, ownerSolidAtY);
    }

    private static Optional<TargetSpan> findSolidSpan(
            int minimumY,
            int maximumY,
            IntPredicate ownerSolidAtY) {
        Objects.requireNonNull(ownerSolidAtY, "ownerSolidAtY");
        if (maximumY < minimumY) {
            throw new IllegalArgumentException("target vertical span must be ordered");
        }

        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (int y = minimumY; y <= maximumY; y++) {
            if (!ownerSolidAtY.test(y)) {
                continue;
            }
            first = Math.min(first, y);
            last = y;
        }
        return first == Integer.MAX_VALUE
                ? Optional.empty()
                : Optional.of(new TargetSpan(first, last));
    }

    private static int mapY(
            int nativeY,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        if (sourceMaximumY < sourceMinimumY) {
            throw new IllegalArgumentException("source vertical frame must be ordered");
        }
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalArgumentException("target vertical frame must be ordered");
        }
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

    record TargetSpan(int minimumY, int maximumY) {
        TargetSpan {
            if (maximumY < minimumY) {
                throw new IllegalArgumentException("target vertical span must be ordered");
            }
        }
    }

    private record Frame(
            SkyIslandWorldVolumeId volumeId,
            String volumePath,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY,
            boolean exactSolidColumnFrame) {
        private Frame {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(volumePath, "volumePath");
            if (sourceMaximumY < sourceMinimumY) {
                throw new IllegalArgumentException("source vertical frame must be ordered");
            }
            if (targetMaximumY < targetMinimumY) {
                throw new IllegalArgumentException("target vertical frame must be ordered");
            }
        }

        private int mapY(BlockPos position) {
            TargetSpan target = exactSolidColumnFrame
                    ? exactSolidTarget(position.getX(), position.getZ())
                            .orElse(new TargetSpan(targetMinimumY, targetMaximumY))
                    : new TargetSpan(targetMinimumY, targetMaximumY);
            return SkyforgeVerticalPlacementFrame.mapY(
                    position.getY(),
                    sourceMinimumY,
                    sourceMaximumY,
                    target.minimumY(),
                    target.maximumY());
        }

        private Optional<TargetSpan> exactSolidTarget(int worldX, int worldZ) {
            return findSolidSpan(
                    targetMinimumY,
                    targetMaximumY,
                    worldY -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volumeId,
                                    worldX,
                                    worldY,
                                    worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "exact-solid vertical frame lost the active terrain binding for "
                                            + volumePath)));
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
