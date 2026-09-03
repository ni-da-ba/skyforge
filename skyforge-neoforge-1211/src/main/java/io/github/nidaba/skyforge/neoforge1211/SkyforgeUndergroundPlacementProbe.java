package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;

/**
 * Development evidence collector for native underground placement inside one exact Skyforge volume.
 *
 * <p>The collector does not alter placement. A HeightRangePlacement mixin observes the positions
 * produced by Minecraft's own final-registry placement stack while an explicit probe scope is open.
 * Population write decisions are observed at the already-accepted exact-volume write envelope. The
 * resulting evidence distinguishes absolute-height placement mismatch from configured-feature
 * failure without feature-ID compatibility rules or coordinate-specific rewrites.
 */
public final class SkyforgeUndergroundPlacementProbe {
    static final String ENABLE_PROPERTY = "skyforge.dev.undergroundPlacement";
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

    /**
     * Wraps one native HeightRangePlacement result stream with a non-mutating observation step.
     *
     * <p>The stream remains lazy. Its coordinates, ordering and random consumption are unchanged.
     */
    public static Stream<BlockPos> observeHeightRangePositions(Stream<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        State state = ACTIVE.get();
        if (state == null) {
            return positions;
        }
        return positions.peek(position -> {
            var execution = SkyforgePopulationExecutionStage.activeExecution();
            if (execution.isEmpty()
                    || !execution.orElseThrow().operation().volumeId().equals(state.volumeId)) {
                return;
            }
            state.recordHeightRange(position);
        });
    }

    /** Observes the existing exact-volume write decision without changing it. */
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
            int samplesBelowEnvelope,
            int samplesInsideEnvelope,
            int samplesAboveEnvelope,
            int minimumSampleY,
            int maximumSampleY,
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
        private int samplesBelowEnvelope;
        private int samplesInsideEnvelope;
        private int samplesAboveEnvelope;
        private int minimumSampleY = Integer.MAX_VALUE;
        private int maximumSampleY = Integer.MIN_VALUE;
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

        private void recordHeightRange(BlockPos position) {
            int y = position.getY();
            heightRangeSamples++;
            minimumSampleY = Math.min(minimumSampleY, y);
            maximumSampleY = Math.max(maximumSampleY, y);
            if (y < minimumEnvelopeY) {
                samplesBelowEnvelope++;
            } else if (y > maximumEnvelopeY) {
                samplesAboveEnvelope++;
            } else {
                samplesInsideEnvelope++;
            }
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
                    samplesBelowEnvelope,
                    samplesInsideEnvelope,
                    samplesAboveEnvelope,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : minimumSampleY,
                    heightRangeSamples == 0 ? Integer.MIN_VALUE : maximumSampleY,
                    acceptedWriteAttempts,
                    rejectedWriteAttempts,
                    uniqueAcceptedWrites.size(),
                    acceptedWriteAttempts == 0 ? Integer.MIN_VALUE : minimumAcceptedWriteY,
                    acceptedWriteAttempts == 0 ? Integer.MIN_VALUE : maximumAcceptedWriteY);
        }
    }
}
