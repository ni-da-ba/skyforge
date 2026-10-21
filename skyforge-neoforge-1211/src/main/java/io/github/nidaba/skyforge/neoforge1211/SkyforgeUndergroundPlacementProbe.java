package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Development evidence collector for native underground placement inside one exact Skyforge volume.
 *
 * <p>The collector observes both Minecraft's native HeightRangePlacement result and the mapped
 * exact-volume result, plus optimized write preflights and ordinary high-level write decisions. It
 * never changes feature policy itself. The resulting evidence proves vertical-frame confinement,
 * deterministic mapping and raw chunk-section write isolation without feature-ID compatibility
 * rules.
 */
public final class SkyforgeUndergroundPlacementProbe {
    static final String ENABLE_PROPERTY = "skyforge.dev.undergroundPlacement";
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private SkyforgeUndergroundPlacementProbe() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static Scope open(
            SkyIslandWorldVolumeId volumeId,
            int minimumEnvelopeY,
            int maximumEnvelopeY) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (maximumEnvelopeY < minimumEnvelopeY) {
            throw new IllegalArgumentException("maximumEnvelopeY must be >= minimumEnvelopeY");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested SF-IMP-0059 underground-placement probes are not supported");
        }
        State state = new State(volumeId, minimumEnvelopeY, maximumEnvelopeY);
        ACTIVE.set(state);
        return new Scope(state);
    }

    /** Returns true only while an explicit development probe is collecting one population attempt. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /** Observes one native HeightRangePlacement position and its volume-local mapped counterpart. */
    public static void observeHeightRangeTransform(
            BlockPos nativePosition,
            BlockPos mappedPosition) {
        Objects.requireNonNull(nativePosition, "nativePosition");
        Objects.requireNonNull(mappedPosition, "mappedPosition");
        State state = ACTIVE.get();
        if (state == null) {
            return;
        }
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()
                || !execution.orElseThrow().operation().volumeId().equals(state.volumeId)) {
            return;
        }
        state.recordHeightRange(nativePosition, mappedPosition);
    }

    /** Observes a non-mutating {@code ensureCanWrite} domain preflight. */
    static void observeWritePreflight(
            SkyforgePopulationOperation operation,
            BlockPos position,
            boolean accepted) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        State state = ACTIVE.get();
        if (state == null || !operation.volumeId().equals(state.volumeId)) {
            return;
        }
        state.recordPreflight(position, accepted);
    }

    /** Observes the existing exact-volume high-level write decision without changing it. */
    static void observeWriteDecision(
            SkyforgePopulationOperation operation,
            BlockPos position,
            boolean accepted) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        State state = ACTIVE.get();
        if (state == null || !operation.volumeId().equals(state.volumeId)) {
            return;
        }
        state.recordWrite(position, accepted);
    }

    public record Snapshot(
            SkyIslandWorldVolumeId volumeId,
            int minimumEnvelopeY,
            int maximumEnvelopeY,
            int heightRangeSamples,
            int nativeSamplesBelowEnvelope,
            int nativeSamplesInsideEnvelope,
            int nativeSamplesAboveEnvelope,
            int minimumNativeSampleY,
            int maximumNativeSampleY,
            int mappedSamplesBelowEnvelope,
            int mappedSamplesInsideEnvelope,
            int mappedSamplesAboveEnvelope,
            int minimumMappedSampleY,
            int maximumMappedSampleY,
            int transformedHeightSamples,
            long heightTransformDigest,
            int writePreflightChecks,
            int acceptedWritePreflights,
            int rejectedWritePreflights,
            int uniqueAcceptedPreflightPositions,
            int minimumAcceptedPreflightY,
            int maximumAcceptedPreflightY,
            int uniqueRejectedPreflightPositions,
            int minimumRejectedPreflightY,
            int maximumRejectedPreflightY,
            int acceptedWriteAttempts,
            int rejectedWriteAttempts,
            int uniqueAcceptedWritePositions,
            int minimumAcceptedWriteY,
            int maximumAcceptedWriteY) {
        public Snapshot {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    static final class Scope implements AutoCloseable {
        private final State state;
        private boolean closed;

        private Scope(State state) {
            this.state = state;
        }

        Snapshot snapshot() {
            requireActive();
            return state.snapshot();
        }

        private void requireActive() {
            if (closed || ACTIVE.get() != state) {
                throw new IllegalStateException("SF-IMP-0059 underground-placement probe scope is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            ACTIVE.remove();
        }
    }

    private static final class State {
        private final SkyIslandWorldVolumeId volumeId;
        private final int minimumEnvelopeY;
        private final int maximumEnvelopeY;
        private int heightRangeSamples;
        private int nativeSamplesBelowEnvelope;
        private int nativeSamplesInsideEnvelope;
        private int nativeSamplesAboveEnvelope;
        private int minimumNativeSampleY = Integer.MAX_VALUE;
        private int maximumNativeSampleY = Integer.MIN_VALUE;
        private int mappedSamplesBelowEnvelope;
        private int mappedSamplesInsideEnvelope;
        private int mappedSamplesAboveEnvelope;
        private int minimumMappedSampleY = Integer.MAX_VALUE;
        private int maximumMappedSampleY = Integer.MIN_VALUE;
        private int transformedHeightSamples;
        private long heightTransformDigest = FNV_OFFSET_BASIS;
        private int writePreflightChecks;
        private int acceptedWritePreflights;
        private int rejectedWritePreflights;
        private int minimumAcceptedPreflightY = Integer.MAX_VALUE;
        private int maximumAcceptedPreflightY = Integer.MIN_VALUE;
        private int minimumRejectedPreflightY = Integer.MAX_VALUE;
        private int maximumRejectedPreflightY = Integer.MIN_VALUE;
        private final Set<Long> uniqueAcceptedPreflights = new HashSet<>();
        private final Set<Long> uniqueRejectedPreflights = new HashSet<>();
        private int acceptedWriteAttempts;
        private int rejectedWriteAttempts;
        private int minimumAcceptedWriteY = Integer.MAX_VALUE;
        private int maximumAcceptedWriteY = Integer.MIN_VALUE;
        private final Set<Long> uniqueAcceptedWrites = new HashSet<>();

        private State(
                SkyIslandWorldVolumeId volumeId,
                int minimumEnvelopeY,
                int maximumEnvelopeY) {
            this.volumeId = volumeId;
            this.minimumEnvelopeY = minimumEnvelopeY;
            this.maximumEnvelopeY = maximumEnvelopeY;
        }

        private void recordHeightRange(
                BlockPos nativePosition,
                BlockPos mappedPosition) {
            if (nativePosition.getX() != mappedPosition.getX()
                    || nativePosition.getZ() != mappedPosition.getZ()) {
                throw new IllegalStateException("vertical placement transform changed native X/Z coordinates");
            }
            int nativeY = nativePosition.getY();
            int mappedY = mappedPosition.getY();
            heightRangeSamples++;
            minimumNativeSampleY = Math.min(minimumNativeSampleY, nativeY);
            maximumNativeSampleY = Math.max(maximumNativeSampleY, nativeY);
            minimumMappedSampleY = Math.min(minimumMappedSampleY, mappedY);
            maximumMappedSampleY = Math.max(maximumMappedSampleY, mappedY);
            if (nativeY < minimumEnvelopeY) {
                nativeSamplesBelowEnvelope++;
            } else if (nativeY > maximumEnvelopeY) {
                nativeSamplesAboveEnvelope++;
            } else {
                nativeSamplesInsideEnvelope++;
            }
            if (mappedY < minimumEnvelopeY) {
                mappedSamplesBelowEnvelope++;
            } else if (mappedY > maximumEnvelopeY) {
                mappedSamplesAboveEnvelope++;
            } else {
                mappedSamplesInsideEnvelope++;
            }
            if (nativeY != mappedY) {
                transformedHeightSamples++;
            }
            heightTransformDigest = mix(heightTransformDigest, nativePosition.getX());
            heightTransformDigest = mix(heightTransformDigest, nativeY);
            heightTransformDigest = mix(heightTransformDigest, nativePosition.getZ());
            heightTransformDigest = mix(heightTransformDigest, mappedY);
        }

        private void recordPreflight(BlockPos position, boolean accepted) {
            writePreflightChecks++;
            if (accepted) {
                acceptedWritePreflights++;
                uniqueAcceptedPreflights.add(position.asLong());
                minimumAcceptedPreflightY = Math.min(minimumAcceptedPreflightY, position.getY());
                maximumAcceptedPreflightY = Math.max(maximumAcceptedPreflightY, position.getY());
                if (position.getY() < minimumEnvelopeY || position.getY() > maximumEnvelopeY) {
                    throw new IllegalStateException("optimized native write preflight escaped exact volume Y envelope: "
                            + position + " not in [" + minimumEnvelopeY + ", " + maximumEnvelopeY + "]");
                }
                return;
            }
            rejectedWritePreflights++;
            uniqueRejectedPreflights.add(position.asLong());
            minimumRejectedPreflightY = Math.min(minimumRejectedPreflightY, position.getY());
            maximumRejectedPreflightY = Math.max(maximumRejectedPreflightY, position.getY());
        }

        private void recordWrite(BlockPos position, boolean accepted) {
            if (!accepted) {
                rejectedWriteAttempts++;
                return;
            }
            acceptedWriteAttempts++;
            uniqueAcceptedWrites.add(position.asLong());
            minimumAcceptedWriteY = Math.min(minimumAcceptedWriteY, position.getY());
            maximumAcceptedWriteY = Math.max(maximumAcceptedWriteY, position.getY());
            if (position.getY() < minimumEnvelopeY || position.getY() > maximumEnvelopeY) {
                throw new IllegalStateException("exact-volume population admitted a write outside its volume Y envelope: "
                        + position + " not in [" + minimumEnvelopeY + ", " + maximumEnvelopeY + "]");
            }
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    volumeId,
                    minimumEnvelopeY,
                    maximumEnvelopeY,
                    heightRangeSamples,
                    nativeSamplesBelowEnvelope,
                    nativeSamplesInsideEnvelope,
                    nativeSamplesAboveEnvelope,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : minimumNativeSampleY,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : maximumNativeSampleY,
                    mappedSamplesBelowEnvelope,
                    mappedSamplesInsideEnvelope,
                    mappedSamplesAboveEnvelope,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : minimumMappedSampleY,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : maximumMappedSampleY,
                    transformedHeightSamples,
                    heightTransformDigest,
                    writePreflightChecks,
                    acceptedWritePreflights,
                    rejectedWritePreflights,
                    uniqueAcceptedPreflights.size(),
                    acceptedWritePreflights == 0 ? Integer.MIN_VALUE : minimumAcceptedPreflightY,
                    acceptedWritePreflights == 0 ? Integer.MIN_VALUE : maximumAcceptedPreflightY,
                    uniqueRejectedPreflights.size(),
                    rejectedWritePreflights == 0 ? Integer.MIN_VALUE : minimumRejectedPreflightY,
                    rejectedWritePreflights == 0 ? Integer.MIN_VALUE : maximumRejectedPreflightY,
                    acceptedWriteAttempts,
                    rejectedWriteAttempts,
                    uniqueAcceptedWrites.size(),
                    acceptedWriteAttempts == 0 ? Integer.MIN_VALUE : minimumAcceptedWriteY,
                    acceptedWriteAttempts == 0 ? Integer.MIN_VALUE : maximumAcceptedWriteY);
        }
    }

    private static long mix(long digest, int value) {
        long mixed = digest ^ Integer.toUnsignedLong(value);
        return mixed * FNV_PRIME;
    }
}
