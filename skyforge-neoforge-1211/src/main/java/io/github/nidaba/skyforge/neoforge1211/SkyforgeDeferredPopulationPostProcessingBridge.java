package io.github.nidaba.skyforge.neoforge1211;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Preserves Minecraft's native post-processing contract for deferred Skyforge population.
 *
 * <p>Ordinary biome population runs during Minecraft's world-generation lifecycle, where
 * {@link ProtoChunk#markPosForPostprocessing(BlockPos)} records positions for later neighbor-shape
 * resolution. SF-IMP-0056 may instead replay native surface population after an admitted terrain
 * volume has caught up into already-loaded {@link LevelChunk}s. LevelChunk intentionally does not
 * implement that generation-time recording path.
 *
 * <p>This bridge is active only around that deferred population path. While one exact-volume
 * population execution is active, a mixin redirects LevelChunk post-processing requests into the
 * chunk's native packed post-processing queue. Pre-existing native marks are detached temporarily so
 * Skyforge resolves only work produced by the deferred feature, then restored unchanged for
 * Minecraft's own lifecycle. Direct world-generation population never opens this bridge and
 * therefore remains unchanged.
 */
public final class SkyforgeDeferredPopulationPostProcessingBridge {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private SkyforgeDeferredPopulationPostProcessingBridge() {}

    static Scope open(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested deferred Skyforge post-processing scopes are not supported");
        }
        State state = new State(level);
        ACTIVE.set(state);
        return new Scope(state);
    }

    /**
     * Called from the ChunkAccess mixin. Returns true only when the vanilla LevelChunk fallback has
     * been replaced by an equivalent deferred Skyforge recording operation.
     */
    public static boolean capture(ChunkAccess chunk, BlockPos position) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        State state = ACTIVE.get();
        if (state == null || SkyforgePopulationExecutionStage.activeExecution().isEmpty()) {
            return false;
        }
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return false;
        }
        if (levelChunk.getLevel() != state.level) {
            throw new IllegalStateException("deferred Skyforge population attempted post-processing in another level");
        }
        if (state.flushing) {
            throw new IllegalStateException("deferred native post-processing recursively requested another mark");
        }
        if (levelChunk.isOutsideBuildHeight(position)) {
            throw new IllegalStateException("deferred native post-processing requested an out-of-build-height position: "
                    + position);
        }

        long chunkKey = levelChunk.getPos().toLong();
        state.touched.computeIfAbsent(
                chunkKey,
                ignored -> new DeferredChunk(levelChunk, NativeQueueSnapshot.detach(levelChunk)));
        levelChunk.addPackedPostProcess(
                ProtoChunk.packOffsetCoordinates(position),
                levelChunk.getSectionIndex(position.getY()));
        return true;
    }

    /** Flushes captured marks while the caller's exact-volume population execution is still open. */
    static void flushIfActive() {
        State state = ACTIVE.get();
        if (state == null || state.touched.isEmpty()) {
            return;
        }
        if (SkyforgePopulationExecutionStage.activeExecution().isEmpty()) {
            throw new IllegalStateException("deferred post-processing flush requires an active population execution");
        }
        state.flush();
    }

    private static boolean nativeQueueEmpty(LevelChunk chunk) {
        for (var section : chunk.getPostProcessing()) {
            if (section != null && !section.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void clearNativeQueue(LevelChunk chunk) {
        for (var section : chunk.getPostProcessing()) {
            if (section != null) {
                section.clear();
            }
        }
    }

    private static final class State {
        private final ServerLevel level;
        private final Map<Long, DeferredChunk> touched = new LinkedHashMap<>();
        private boolean flushing;

        private State(ServerLevel level) {
            this.level = level;
        }

        private void flush() {
            if (flushing) {
                throw new IllegalStateException("recursive deferred post-processing flush");
            }
            flushing = true;
            try {
                var chunks = java.util.List.copyOf(touched.values());
                touched.clear();
                for (DeferredChunk deferred : chunks) {
                    flush(deferred);
                }
                if (!touched.isEmpty()) {
                    throw new IllegalStateException("deferred post-processing produced new pending chunk work while flushing");
                }
            } finally {
                flushing = false;
            }
        }

        private static void flush(DeferredChunk deferred) {
            LevelChunk chunk = deferred.chunk();
            try {
                chunk.postProcessGeneration();
                if (!nativeQueueEmpty(chunk)) {
                    throw new IllegalStateException(
                            "deferred Skyforge post-processing did not drain its isolated queue: " + chunk.getPos());
                }
            } finally {
                // On either success or failure, never let Skyforge consume or overwrite native work
                // that was already pending when the deferred feature began.
                clearNativeQueue(chunk);
                deferred.nativeQueue().restore(chunk);
            }
        }

        private void abandon() {
            for (DeferredChunk deferred : touched.values()) {
                clearNativeQueue(deferred.chunk());
                deferred.nativeQueue().restore(deferred.chunk());
            }
            touched.clear();
        }
    }

    private record DeferredChunk(LevelChunk chunk, NativeQueueSnapshot nativeQueue) {
        private DeferredChunk {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(nativeQueue, "nativeQueue");
        }
    }

    /** Exact per-section snapshot of native marks that pre-date one deferred Skyforge feature. */
    private record NativeQueueSnapshot(short[][] entries) {
        private NativeQueueSnapshot {
            Objects.requireNonNull(entries, "entries");
        }

        private static NativeQueueSnapshot detach(LevelChunk chunk) {
            var sections = chunk.getPostProcessing();
            short[][] entries = new short[sections.length][];
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                var section = sections[sectionIndex];
                if (section == null || section.isEmpty()) {
                    entries[sectionIndex] = new short[0];
                } else {
                    entries[sectionIndex] = section.toShortArray();
                    section.clear();
                }
            }
            return new NativeQueueSnapshot(entries);
        }

        private void restore(LevelChunk chunk) {
            if (!nativeQueueEmpty(chunk)) {
                throw new IllegalStateException(
                        "cannot restore native post-processing over pending deferred work: " + chunk.getPos());
            }
            if (entries.length != chunk.getPostProcessing().length) {
                throw new IllegalStateException(
                        "post-processing section count changed during deferred population: " + chunk.getPos());
            }
            for (int sectionIndex = 0; sectionIndex < entries.length; sectionIndex++) {
                for (short packedPosition : entries[sectionIndex]) {
                    chunk.addPackedPostProcess(packedPosition, sectionIndex);
                }
            }
        }
    }

    static final class Scope implements AutoCloseable {
        private final State state;
        private boolean closed;

        private Scope(State state) {
            this.state = state;
        }

        private void requireActive() {
            if (closed || ACTIVE.get() != state) {
                throw new IllegalStateException("deferred Skyforge post-processing scope is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            ACTIVE.remove();
            if (!state.touched.isEmpty()) {
                state.abandon();
                throw new IllegalStateException("deferred Skyforge population closed with unflushed post-processing work");
            }
        }
    }
}
