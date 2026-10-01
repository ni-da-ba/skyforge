package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Supplies the stable-chunk runtime side effects that ordinary generation-time writes do not need.
 *
 * <p>Skyforge's concrete chunk writer deliberately uses {@link ChunkAccess#setBlockState} so direct
 * world-generation realization remains a low-level deterministic materialization step. SF-IMP-0056
 * may replay that same writer after a target chunk has already become a client-visible
 * {@link LevelChunk}. In that deferred lifecycle, each actual block change must also be submitted to
 * Minecraft's light engine and block-change broadcaster; otherwise the authoritative server chunk
 * can contain correct collision while a tracking client retains stale section geometry or lighting.
 *
 * <p>This scope is opened only around deferred terrain catch-up. Native population runs after it is
 * closed, and ordinary WorldGenRegion realization never enters it.
 */
final class SkyforgeDeferredChunkMutationLifecycle {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private SkyforgeDeferredChunkMutationLifecycle() {}

    static Scope open(ServerLevel level, LevelChunk chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("deferred mutation chunk belongs to another level");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested deferred stable-chunk mutation scopes are not supported");
        }
        State state = new State(level, chunk);
        ACTIVE.set(state);
        return new Scope(state);
    }

    static void afterWrite(
            ChunkAccess chunk,
            BlockPos position,
            BlockState previousState,
            BlockState storedState) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(storedState, "storedState");

        State state = ACTIVE.get();
        if (state == null || previousState.equals(storedState)) {
            return;
        }
        if (chunk != state.chunk) {
            throw new IllegalStateException("deferred stable-chunk mutation escaped its target chunk");
        }

        // The writer reuses one MutableBlockPos. Both downstream systems may retain/schedule work,
        // so never hand them that mutable instance.
        BlockPos immutablePosition = position.immutable();
        state.level.getChunkSource().getLightEngine().checkBlock(immutablePosition);
        state.level.getChunkSource().blockChanged(immutablePosition);
        state.changedBlocks++;
    }

    private static final class State {
        private final ServerLevel level;
        private final LevelChunk chunk;
        private int changedBlocks;

        private State(ServerLevel level, LevelChunk chunk) {
            this.level = level;
            this.chunk = chunk;
        }
    }

    static final class Scope implements AutoCloseable {
        private final State state;
        private boolean closed;

        private Scope(State state) {
            this.state = state;
        }

        @Override
        public void close() {
            if (closed || ACTIVE.get() != state) {
                throw new IllegalStateException("deferred stable-chunk mutation scope is not active");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
