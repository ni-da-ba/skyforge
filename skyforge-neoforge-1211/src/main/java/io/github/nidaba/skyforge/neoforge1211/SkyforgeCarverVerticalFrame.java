package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Thread-confined vertical frame for registry-native carver execution inside one exact Skyforge
 * volume.
 *
 * <p>Carver HeightProviders sample against Minecraft's native dimension frame first. Mixins observe
 * only the returned sample and map that integer into the caller-selected exact-volume interior
 * interval. This preserves the number and order of native RNG draws. X/Z are never changed here.
 *
 * <p>The target interval is supplied by the island/carver plan rather than inferred from a carver
 * ID. SF-IMP-0061's development fixture uses a conservative owner-solid interior band; later
 * Skyforge cave authorship can choose a richer interval without changing the Minecraft execution
 * seam.
 */
public final class SkyforgeCarverVerticalFrame {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private SkyforgeCarverVerticalFrame() {}

    static Scope open(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId,
            int targetMinimumY,
            int targetMaximumY) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalArgumentException("carver target frame must be ordered");
        }
        if (targetMinimumY < level.getMinBuildHeight()
                || targetMaximumY >= level.getMinBuildHeight() + level.getHeight()) {
            throw new IllegalArgumentException("carver target frame exceeds active Minecraft build height");
        }
        var activeDomain = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        if (activeDomain.isEmpty() || !activeDomain.orElseThrow().equals(volumeId)) {
            throw new IllegalStateException("carver vertical frame requires its exact island generation-domain scope");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge carver vertical frames are not supported");
        }

        int sourceMinimumY = level.getMinBuildHeight();
        int sourceMaximumY = Math.addExact(sourceMinimumY, Math.subtractExact(level.getHeight(), 1));
        Frame frame = new Frame(
                volumeId,
                sourceMinimumY,
                sourceMaximumY,
                targetMinimumY,
                targetMaximumY);
        ACTIVE.set(frame);
        return new Scope(frame);
    }

    /** Returns true only while one exact-volume carver frame is active on this thread. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /**
     * Maps one already-sampled native HeightProvider Y into the active exact-volume frame.
     *
     * <p>The caller must invoke this after the native provider returns so the provider's RNG stream
     * remains unchanged.
     */
    public static int mapSampledY(int nativeY) {
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return nativeY;
        }
        int mapped = mapY(
                nativeY,
                frame.sourceMinimumY,
                frame.sourceMaximumY,
                frame.targetMinimumY,
                frame.targetMaximumY);
        frame.recordSample(nativeY, mapped);
        return mapped;
    }

    /** Maps a non-random standalone carver anchor such as the configured lava level. */
    public static int mapStandaloneAnchorY(int nativeY) {
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return nativeY;
        }
        int mapped = mapY(
                nativeY,
                frame.sourceMinimumY,
                frame.sourceMaximumY,
                frame.targetMinimumY,
                frame.targetMaximumY);
        frame.recordAnchor(nativeY, mapped);
        return mapped;
    }

    static int mapYForTest(
            int nativeY,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        return mapY(nativeY, sourceMinimumY, sourceMaximumY, targetMinimumY, targetMaximumY);
    }

    private static int mapY(
            int nativeY,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY) {
        if (sourceMaximumY < sourceMinimumY) {
            throw new IllegalArgumentException("carver source frame must be ordered");
        }
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalArgumentException("carver target frame must be ordered");
        }
        if (sourceMaximumY == sourceMinimumY || targetMaximumY == targetMinimumY) {
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

    record Snapshot(
            SkyIslandWorldVolumeId volumeId,
            int sourceMinimumY,
            int sourceMaximumY,
            int targetMinimumY,
            int targetMaximumY,
            int sampledHeights,
            int minimumNativeSampleY,
            int maximumNativeSampleY,
            int minimumMappedSampleY,
            int maximumMappedSampleY,
            int mappedSamplesOutsideTarget,
            int standaloneAnchors,
            long transformDigest) {
        Snapshot {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    private static final class Frame {
        private final SkyIslandWorldVolumeId volumeId;
        private final int sourceMinimumY;
        private final int sourceMaximumY;
        private final int targetMinimumY;
        private final int targetMaximumY;
        private int sampledHeights;
        private int minimumNativeSampleY = Integer.MAX_VALUE;
        private int maximumNativeSampleY = Integer.MIN_VALUE;
        private int minimumMappedSampleY = Integer.MAX_VALUE;
        private int maximumMappedSampleY = Integer.MIN_VALUE;
        private int mappedSamplesOutsideTarget;
        private int standaloneAnchors;
        private long transformDigest = FNV_OFFSET_BASIS;

        private Frame(
                SkyIslandWorldVolumeId volumeId,
                int sourceMinimumY,
                int sourceMaximumY,
                int targetMinimumY,
                int targetMaximumY) {
            this.volumeId = volumeId;
            this.sourceMinimumY = sourceMinimumY;
            this.sourceMaximumY = sourceMaximumY;
            this.targetMinimumY = targetMinimumY;
            this.targetMaximumY = targetMaximumY;
        }

        private void recordSample(int nativeY, int mappedY) {
            sampledHeights++;
            minimumNativeSampleY = Math.min(minimumNativeSampleY, nativeY);
            maximumNativeSampleY = Math.max(maximumNativeSampleY, nativeY);
            minimumMappedSampleY = Math.min(minimumMappedSampleY, mappedY);
            maximumMappedSampleY = Math.max(maximumMappedSampleY, mappedY);
            if (mappedY < targetMinimumY || mappedY > targetMaximumY) {
                mappedSamplesOutsideTarget++;
            }
            transformDigest = mix(transformDigest, nativeY);
            transformDigest = mix(transformDigest, mappedY);
        }

        private void recordAnchor(int nativeY, int mappedY) {
            standaloneAnchors++;
            transformDigest = mix(transformDigest, nativeY);
            transformDigest = mix(transformDigest, mappedY);
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    volumeId,
                    sourceMinimumY,
                    sourceMaximumY,
                    targetMinimumY,
                    targetMaximumY,
                    sampledHeights,
                    sampledHeights == 0 ? Integer.MIN_VALUE : minimumNativeSampleY,
                    sampledHeights == 0 ? Integer.MIN_VALUE : maximumNativeSampleY,
                    sampledHeights == 0 ? Integer.MIN_VALUE : minimumMappedSampleY,
                    sampledHeights == 0 ? Integer.MIN_VALUE : maximumMappedSampleY,
                    mappedSamplesOutsideTarget,
                    standaloneAnchors,
                    transformDigest);
        }
    }

    private static long mix(long digest, int value) {
        long mixed = digest ^ Integer.toUnsignedLong(value);
        return mixed * FNV_PRIME;
    }

    static final class Scope implements AutoCloseable {
        private final Frame frame;
        private boolean closed;

        private Scope(Frame frame) {
            this.frame = frame;
        }

        Snapshot snapshot() {
            requireActive();
            return frame.snapshot();
        }

        void requireActive() {
            if (closed || ACTIVE.get() != frame) {
                throw new IllegalStateException("Skyforge carver vertical frame is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            ACTIVE.remove();
        }
    }
}
