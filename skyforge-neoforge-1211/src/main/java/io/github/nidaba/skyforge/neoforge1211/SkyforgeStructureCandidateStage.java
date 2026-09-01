package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Thread-confined trace of Skyforge height provenance while one native structure candidate runs. */
final class SkyforgeStructureCandidateStage {
    private static final ThreadLocal<Trace> ACTIVE = new ThreadLocal<>();

    private SkyforgeStructureCandidateStage() {}

    static Scope open() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge structure candidate traces are not supported");
        }
        Trace trace = new Trace();
        ACTIVE.set(trace);
        return new Scope(trace);
    }

    static void record(MinecraftSkyforgeHeightClaim claim) {
        Objects.requireNonNull(claim, "claim");
        Trace trace = ACTIVE.get();
        if (trace != null) {
            trace.claimedVolumeIds.addAll(claim.volumeIds());
        }
    }

    static final class Scope implements AutoCloseable {
        private final Trace trace;
        private boolean closed;

        private Scope(Trace trace) {
            this.trace = trace;
        }

        Set<SkyIslandWorldVolumeId> claimedVolumeIds() {
            requireOpen();
            return Set.copyOf(trace.claimedVolumeIds);
        }

        private void requireOpen() {
            if (closed || ACTIVE.get() != trace) {
                throw new IllegalStateException("Skyforge structure candidate trace is not active");
            }
        }

        @Override
        public void close() {
            requireOpen();
            ACTIVE.remove();
            closed = true;
        }
    }

    private static final class Trace {
        private final LinkedHashSet<SkyIslandWorldVolumeId> claimedVolumeIds = new LinkedHashSet<>();
    }
}
