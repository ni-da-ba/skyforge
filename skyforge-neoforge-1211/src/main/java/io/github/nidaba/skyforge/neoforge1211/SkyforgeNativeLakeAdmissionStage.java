package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Whole-feature pre-mutation admission for native Minecraft LakeFeature population.
 *
 * <p>The exact per-position population fence remains authoritative, but it must not be used to
 * "clip" a lake. LakeFeature is therefore allowed to begin only when a conservative finite envelope
 * around its final placed origin belongs entirely to the same compiled Skyforge owner.
 *
 * <p>Minecraft 1.21.1 LakeFeature uses the final placed X/Z as the 16x16 mask's lower corner and
 * shifts its Y origin down four blocks before operating on eight vertical cells. The preflight
 * therefore covers exactly that finite 16x8x16 mutation box. Candidates that need air/exterior
 * beyond compiled owner support fail closed before native mutation.
 */
public final class SkyforgeNativeLakeAdmissionStage {
    static final int MIN_X_OFFSET = 0;
    static final int MAX_X_OFFSET = 15;
    static final int MIN_Y_OFFSET = -4;
    static final int MAX_Y_OFFSET = 3;
    static final int MIN_Z_OFFSET = 0;
    static final int MAX_Z_OFFSET = 15;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private SkyforgeNativeLakeAdmissionStage() {}

    static Scope open(SkyforgePopulationOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (operation.generationStep() != GenerationStep.Decoration.LAKES.ordinal()) {
            return Scope.inactive();
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested native lake admission scopes are not supported");
        }
        State state = new State(operation.volumeId());
        ACTIVE.set(state);
        return new Scope(state);
    }

    /**
     * First admitted capability is Minecraft's native LakeFeature type, not a registry-ID allowlist.
     * Unknown/modded configured feature classes in the LAKES phase fail closed until they expose an
     * equivalent bounded-footprint contract.
     */
    static boolean supports(PlacedFeature feature) {
        Objects.requireNonNull(feature, "feature");
        return feature.feature().value().feature() == Feature.LAKE;
    }

    /**
     * Deterministic acceptance-only probe for one native LakeFeature origin.
     *
     * <p>This opens the same production admission stage but performs no feature mutation. It exists
     * so acceptance can exercise a known edge-crossing candidate without altering the live biome
     * feature RNG stream merely to manufacture a rejection.
     */
    static Snapshot probe(
            SkyforgePopulationOperation operation,
            BlockPos origin) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(origin, "origin");
        try (var scope = open(operation)) {
            scope.requireActive();
            admit(origin);
            return scope.snapshot();
        }
    }

    /** Called at LakeFeature.place HEAD before any LakeFeature mutation is possible. */
    public static boolean admit(BlockPos origin) {
        Objects.requireNonNull(origin, "origin");
        State state = ACTIVE.get();
        if (state == null) {
            return true;
        }

        state.attempted++;
        state.decisionDigest = mix(state.decisionDigest, origin.asLong());

        BlockPos firstRejected = null;
        int inspected = 0;
        outer:
        for (int dx = MIN_X_OFFSET; dx <= MAX_X_OFFSET; dx++) {
            for (int dz = MIN_Z_OFFSET; dz <= MAX_Z_OFFSET; dz++) {
                for (int dy = MIN_Y_OFFSET; dy <= MAX_Y_OFFSET; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    inspected++;
                    boolean owner = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    state.volumeId,
                                    candidate.getX(),
                                    candidate.getY(),
                                    candidate.getZ())
                            .orElseThrow(() -> new IllegalStateException(
                                    "native lake admission lost the active Skyforge terrain binding"));
                    boolean foreign = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                                    state.volumeId,
                                    candidate.getX(),
                                    candidate.getY(),
                                    candidate.getZ())
                            .orElseThrow(() -> new IllegalStateException(
                                    "native lake admission lost the active Skyforge terrain binding"));
                    if (!owner || foreign) {
                        firstRejected = candidate;
                        break outer;
                    }
                }
            }
        }

        state.inspectedPositions += inspected;
        if (firstRejected != null) {
            state.rejected++;
            state.decisionDigest = mix(state.decisionDigest, 0L);
            state.decisionDigest = mix(state.decisionDigest, firstRejected.asLong());
            state.lastRejectedOrigin = origin.immutable();
            state.lastRejectedPosition = firstRejected.immutable();
            state.rejectedOrigins.add(origin.immutable());
            return false;
        }

        state.admitted++;
        state.admittedOrigins.add(origin.immutable());
        state.decisionDigest = mix(state.decisionDigest, 1L);
        return true;
    }

    /** Snapshot for deterministic runtime/acceptance evidence. */
    static Snapshot snapshot() {
        State state = ACTIVE.get();
        if (state == null) {
            throw new IllegalStateException("no native lake admission scope is active");
        }
        return state.snapshot();
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    record Snapshot(
            int attempted,
            int admitted,
            int rejected,
            int inspectedPositions,
            long decisionDigest,
            BlockPos lastRejectedOrigin,
            BlockPos lastRejectedPosition,
            List<BlockPos> admittedOrigins,
            List<BlockPos> rejectedOrigins) {
        Snapshot {
            admittedOrigins = List.copyOf(admittedOrigins);
            rejectedOrigins = List.copyOf(rejectedOrigins);
        }
    }

    private static final class State {
        private final SkyIslandWorldVolumeId volumeId;
        private int attempted;
        private int admitted;
        private int rejected;
        private int inspectedPositions;
        private long decisionDigest = FNV_OFFSET_BASIS;
        private BlockPos lastRejectedOrigin;
        private BlockPos lastRejectedPosition;
        private final List<BlockPos> admittedOrigins = new ArrayList<>();
        private final List<BlockPos> rejectedOrigins = new ArrayList<>();

        private State(SkyIslandWorldVolumeId volumeId) {
            this.volumeId = Objects.requireNonNull(volumeId, "volumeId");
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    attempted,
                    admitted,
                    rejected,
                    inspectedPositions,
                    decisionDigest,
                    lastRejectedOrigin,
                    lastRejectedPosition,
                    admittedOrigins,
                    rejectedOrigins);
        }
    }

    static final class Scope implements AutoCloseable {
        private final State state;
        private boolean closed;

        private Scope(State state) {
            this.state = state;
        }

        static Scope inactive() {
            return new Scope(null);
        }

        void requireActive() {
            if (closed) {
                throw new IllegalStateException("native lake admission scope is closed");
            }
            if (state != null && ACTIVE.get() != state) {
                throw new IllegalStateException("native lake admission scope changed before completion");
            }
        }

        Snapshot snapshot() {
            requireActive();
            if (state == null) {
                return new Snapshot(0, 0, 0, 0, FNV_OFFSET_BASIS, null, null, List.of(), List.of());
            }
            return state.snapshot();
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            if (state != null) {
                ACTIVE.remove();
            }
        }
    }
}
