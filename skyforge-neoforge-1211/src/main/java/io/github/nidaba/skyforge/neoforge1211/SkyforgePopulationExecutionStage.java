package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/** Thread-confined execution state for one exact-volume native population attempt. */
final class SkyforgePopulationExecutionStage {
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private SkyforgePopulationExecutionStage() {}

    static Scope open(SkyforgePopulationOperation operation, int maximumAttachmentDepth) {
        return open(Optional.empty(), operation, Optional.empty(), maximumAttachmentDepth);
    }

    static Scope open(
            SkyforgePopulationOperation operation,
            Holder<Biome> domainBiome,
            int maximumAttachmentDepth) {
        return open(
                Optional.empty(),
                operation,
                Optional.of(Objects.requireNonNull(domainBiome, "domainBiome")),
                maximumAttachmentDepth);
    }

    static Scope open(
            WorldGenLevel level,
            SkyforgePopulationOperation operation,
            int maximumAttachmentDepth) {
        return open(
                Optional.of(Objects.requireNonNull(level, "level")),
                operation,
                Optional.empty(),
                maximumAttachmentDepth);
    }

    static Scope open(
            WorldGenLevel level,
            SkyforgePopulationOperation operation,
            Holder<Biome> domainBiome,
            int maximumAttachmentDepth) {
        return open(
                Optional.of(Objects.requireNonNull(level, "level")),
                operation,
                Optional.of(Objects.requireNonNull(domainBiome, "domainBiome")),
                maximumAttachmentDepth);
    }

    private static Scope open(
            Optional<WorldGenLevel> level,
            SkyforgePopulationOperation operation,
            Optional<Holder<Biome>> domainBiome,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(domainBiome, "domainBiome");
        var activeDomain = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        if (activeDomain.isEmpty() || !activeDomain.orElseThrow().equals(operation.volumeId())) {
            throw new IllegalStateException("population execution requires its exact island generation-domain scope");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge population executions are not supported");
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("population execution requires an active Skyforge runtime binding");
        }

        Predicate<BlockPos> ownerSolid = position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        operation.volumeId(), position.getX(), position.getY(), position.getZ())
                .orElseThrow(() -> new IllegalStateException("Skyforge runtime binding disappeared during population"));
        Predicate<BlockPos> foreignSolid = position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                        operation.volumeId(), position.getX(), position.getY(), position.getZ())
                .orElseThrow(() -> new IllegalStateException("Skyforge runtime binding disappeared during population"));
        return open(level, operation, domainBiome, ownerSolid, foreignSolid, maximumAttachmentDepth);
    }

    static Scope openForTest(
            SkyforgePopulationOperation operation,
            Predicate<BlockPos> ownerSolid,
            int maximumAttachmentDepth) {
        return openForTest(operation, ownerSolid, ignored -> false, maximumAttachmentDepth);
    }

    static Scope openForTest(
            SkyforgePopulationOperation operation,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(operation, "operation");
        var activeDomain = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        if (activeDomain.isEmpty() || !activeDomain.orElseThrow().equals(operation.volumeId())) {
            throw new IllegalStateException("population execution requires its exact island generation-domain scope");
        }
        return open(Optional.empty(), operation, Optional.empty(), ownerSolid, foreignSolid, maximumAttachmentDepth);
    }

    static Optional<Execution> activeExecution() {
        return Optional.ofNullable(ACTIVE.get());
    }

    private static boolean originChunkContains(
            SkyforgePopulationOperation operation,
            BlockPos position) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        return (position.getX() >> 4) == operation.originChunk().x
                && (position.getZ() >> 4) == operation.originChunk().z;
    }

    static boolean directWriteAuthorityAllows(
            SkyforgePopulationOperation operation,
            BlockPos position,
            boolean stableDeferredLevel) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        // Physically admitted production population executes on stable LevelChunks. Its write
        // authority must not depend on whether an adjacent chunk happens to be resident: that
        // reintroduced the historical Windows 503/714 tree split and made the result scheduler-
        // dependent. Stable deferred population is therefore chunk-local. Legacy WorldGenRegion
        // fixtures keep their bounded cross-chunk attachment semantics.
        return !stableDeferredLevel || originChunkContains(operation, position);
    }

    private static Scope open(
            Optional<WorldGenLevel> level,
            SkyforgePopulationOperation operation,
            Optional<Holder<Biome>> domainBiome,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(domainBiome, "domainBiome");
        Objects.requireNonNull(ownerSolid, "ownerSolid");
        Objects.requireNonNull(foreignSolid, "foreignSolid");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge population executions are not supported");
        }

        CachedBlockPredicate cachedOwnerSolid = new CachedBlockPredicate(ownerSolid);
        CachedBlockPredicate cachedForeignSolid = new CachedBlockPredicate(foreignSolid);
        Predicate<BlockPos> attachmentOwnerSolid = cachedOwnerSolid;
        Predicate<BlockPos> attachmentBarrierSolid = cachedForeignSolid;
        boolean stableDeferredLevel =
                level.isPresent() && level.orElseThrow() instanceof ServerLevel;
        boolean structuralTree = operation.generationStep()
                        == net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()
                && operation.nativeDefinitionKey().getPath().toLowerCase(Locale.ROOT).contains("tree");
        if (operation.generationStep()
                == net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
            // Surface ecology may extend canopy across chunk boundaries, but another chunk's
            // same-volume terrain is not this operation's direct write authority. Treat it as a
            // write barrier so neighboring population cannot mutate owner-solid cells that a later
            // operation would otherwise expose as live origin-chunk state. Exterior air remains
            // reachable through the ordinary bounded attachment envelope.
            attachmentOwnerSolid =
                    position -> cachedOwnerSolid.test(position) && originChunkContains(operation, position);
            attachmentBarrierSolid = position -> cachedForeignSolid.test(position)
                    || (cachedOwnerSolid.test(position) && !originChunkContains(operation, position));
        }
        Predicate<BlockPos> staticAttachmentReachability =
                stableDeferredLevel && structuralTree
                        ? stableDeferredTreeAttachmentReachability(
                                operation, maximumAttachmentDepth)
                        : null;
        Execution execution = new Execution(
                level,
                operation,
                domainBiome,
                cachedOwnerSolid,
                cachedForeignSolid,
                new SkyforgePopulationAttachmentEnvelope(
                        attachmentOwnerSolid,
                        attachmentBarrierSolid,
                        maximumAttachmentDepth,
                        staticAttachmentReachability));
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    /**
     * Order-independent tree attachment domain for stable deferred population.
     *
     * <p>The historical attachment envelope grew from writes that happened to be visited first.
     * Vanilla tree internals use unordered working sets in some foliage/update paths, so two
     * equivalent runs could choose different frontier cells even with the same seed and identical
     * block pre-state. Stable deferred trees already have chunk-local direct-write authority; define
     * their bounded exterior reach instead as the static 26-neighbor graph distance to immutable
     * exact owner terrain in that origin chunk. Chebyshev distance is exactly the shortest distance
     * in a 26-neighbor lattice.
     */
    private static Predicate<BlockPos> stableDeferredTreeAttachmentReachability(
            SkyforgePopulationOperation operation,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(operation, "operation");
        if (maximumAttachmentDepth < 0) {
            throw new IllegalArgumentException("maximumAttachmentDepth must be non-negative");
        }
        ChunkPos chunk = operation.originChunk();
        List<OwnerColumn> ownerColumns = new ArrayList<>(16 * 16);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                SkyforgeNeoForge1211SurfaceStage.integerSolidRange(
                                operation.volumeId(), x, z)
                        .ifPresent(range -> ownerColumns.add(
                                new OwnerColumn(
                                        x,
                                        z,
                                        range.minimumY(),
                                        range.maximumY())));
            }
        }
        List<OwnerColumn> canonicalOwnerColumns = List.copyOf(ownerColumns);
        return position -> {
            if (maximumAttachmentDepth == 0
                    || !originChunkContains(operation, position)) {
                return false;
            }
            for (OwnerColumn column : canonicalOwnerColumns) {
                int verticalDistance = position.getY() < column.minimumY()
                        ? column.minimumY() - position.getY()
                        : position.getY() > column.maximumY()
                                ? position.getY() - column.maximumY()
                                : 0;
                int distance = Math.max(
                        Math.max(
                                Math.abs(position.getX() - column.x()),
                                Math.abs(position.getZ() - column.z())),
                        verticalDistance);
                if (distance <= maximumAttachmentDepth) {
                    return true;
                }
            }
            return false;
        };
    }

    private record OwnerColumn(
            int x,
            int z,
            int minimumY,
            int maximumY) {
        private OwnerColumn {
            if (maximumY < minimumY) {
                throw new IllegalArgumentException("owner column maximum precedes minimum");
            }
        }
    }

    static final class Execution {
        private final Optional<WorldGenLevel> level;
        private final SkyforgePopulationOperation operation;
        private final Optional<Holder<Biome>> domainBiome;
        private final CachedBlockPredicate ownerSolid;
        private final CachedBlockPredicate foreignSolid;
        private final SkyforgePopulationAttachmentEnvelope attachmentEnvelope;
        private final boolean stableDeferredLevel;
        private final LinkedHashSet<BlockPos> acceptedStableWrites = new LinkedHashSet<>();

        private Execution(
                Optional<WorldGenLevel> level,
                SkyforgePopulationOperation operation,
                Optional<Holder<Biome>> domainBiome,
                CachedBlockPredicate ownerSolid,
                CachedBlockPredicate foreignSolid,
                SkyforgePopulationAttachmentEnvelope attachmentEnvelope) {
            this.level = Objects.requireNonNull(level, "level");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.domainBiome = Objects.requireNonNull(domainBiome, "domainBiome");
            this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
            this.foreignSolid = Objects.requireNonNull(foreignSolid, "foreignSolid");
            this.attachmentEnvelope = Objects.requireNonNull(attachmentEnvelope, "attachmentEnvelope");
            this.stableDeferredLevel = level.isPresent() && level.orElseThrow() instanceof ServerLevel;
        }

        SkyforgePopulationOperation operation() {
            return operation;
        }

        Optional<Holder<Biome>> domainBiome() {
            return domainBiome;
        }

        boolean isVisible(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if ((ownerSolid.test(position) && originChunkContains(position))
                    || attachmentEnvelope.ownsAttachment(position)) {
                return true;
            }
            if (!stableDeferredLevel || !originChunkContains(position)) {
                return false;
            }
            // Separate deferred native features must not inherit visibility from the historical
            // attachment-provenance ledger. That ledger records admitted write attempts, including
            // attempts that can leave the same live block state, so exposing it as read authority
            // makes later feature geometry depend on incidental prior feature internals even when
            // the observable pre-state is identical. A feature may see exact owner terrain and its
            // own current attachment envelope only.
            return false;
        }

        /**
         * Returns the deterministic virtual state for a read hidden from this population operation.
         *
         * <p>Same-volume solid terrain in a neighboring chunk must not expose its live materialization
         * state: that chunk may or may not have completed deferred Skyforge realization yet, depending
         * on Minecraft scheduling. Present it as an inert solid barrier. Non-owner exterior keeps the
         * phase-specific historical virtualization (AIR for surface vegetation, BEDROCK for sensitive
         * underground phases), so cross-chunk canopy attachments remain possible without reading
         * neighboring terrain realization order.
         */
        BlockState hiddenBlockState(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if (stableDeferredLevel && !originChunkContains(position)) {
                // Stable deferred population is deliberately chunk-local. Present every neighboring
                // column as an inert barrier so native features reject boundary-crossing geometry
                // before attempting direct writes; this avoids both clipped scheduler-dependent
                // canopies and synchronous neighbor generation.
                return net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
            }
            if (ownerSolid.test(position)) {
                return net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
            }
            return SkyforgeNativeInteriorPlacementPolicy.hiddenExteriorBlockState(operation);
        }

        private boolean originChunkContains(BlockPos position) {
            return SkyforgePopulationExecutionStage.originChunkContains(operation, position);
        }

        private boolean directWriteAuthorityAllows(BlockPos position) {
            return SkyforgePopulationExecutionStage.directWriteAuthorityAllows(
                    operation,
                    position,
                    stableDeferredLevel);
        }

        boolean canWrite(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return directWriteAuthorityAllows(position)
                    && SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)
                    && attachmentEnvelope.canAcceptWrite(position);
        }

        boolean acceptWrite(BlockPos position) {
            Objects.requireNonNull(position, "position");
            boolean accepted = directWriteAuthorityAllows(position)
                    && SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)
                    && attachmentEnvelope.acceptWrite(position);
            if (accepted) {
                recordStableWrite(position);
            }
            return accepted;
        }

        boolean acceptWrite(BlockPos position, BlockState state) {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(state, "state");
            if (!directWriteAuthorityAllows(position)
                    || !SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)) {
                return false;
            }
            if (level.isPresent()
                    && !SkyforgeNativeInteriorPlacementPolicy.canWriteState(
                            level.orElseThrow(),
                            operation,
                            position,
                            state,
                            ownerSolid)) {
                return false;
            }
            boolean accepted = attachmentEnvelope.acceptWrite(position);
            if (accepted) {
                recordStableWrite(position);
            }
            return accepted;
        }

        private void recordStableWrite(BlockPos position) {
            if (stableDeferredLevel) {
                acceptedStableWrites.add(position.immutable());
            }
        }

        private void commitDeferredEffects() {
            if (!stableDeferredLevel) {
                return;
            }
            ServerLevel server = (ServerLevel) level.orElseThrow();
            for (BlockPos position : acceptedStableWrites) {
                server.getChunkSource().getLightEngine().checkBlock(position);
                server.getChunkSource().blockChanged(position);
            }
        }

        int attachmentCount() {
            return attachmentEnvelope.attachmentCount();
        }

        java.util.List<BlockPos> attachmentPositions() {
            return attachmentEnvelope.attachmentPositions().stream()
                    .sorted(java.util.Comparator.comparingLong(BlockPos::asLong))
                    .toList();
        }

        long attachmentPositionDigest() {
            return attachmentEnvelope.attachmentPositionDigest();
        }

        private void recordPerformanceEvidence() {
            ownerSolid.recordPerformanceEvidence("population.ownerSolidCache");
            foreignSolid.recordPerformanceEvidence("population.foreignSolidCache");
        }
    }

    /**
     * Caches one immutable compiled-geometry predicate for the lifetime of one native feature call.
     * Mutable Minecraft block/fluid state and attachment ownership deliberately remain uncached.
     */
    private static final class CachedBlockPredicate implements Predicate<BlockPos> {
        private static final byte UNKNOWN = 0;
        private static final byte FALSE = 1;
        private static final byte TRUE = 2;

        private final Predicate<BlockPos> delegate;
        private final Long2ByteOpenHashMap values = new Long2ByteOpenHashMap();
        private long hits;
        private long misses;

        private CachedBlockPredicate(Predicate<BlockPos> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            values.defaultReturnValue(UNKNOWN);
        }

        @Override
        public boolean test(BlockPos position) {
            Objects.requireNonNull(position, "position");
            long key = position.asLong();
            byte cached = values.get(key);
            if (cached != UNKNOWN) {
                hits++;
                return cached == TRUE;
            }

            boolean value = delegate.test(position);
            values.put(key, value ? TRUE : FALSE);
            misses++;
            return value;
        }

        private void recordPerformanceEvidence(String metricPrefix) {
            if (!SkyforgeRuntimePerformanceMetrics.enabled()) {
                return;
            }
            SkyforgeRuntimePerformanceMetrics.recordSample(metricPrefix + ".queries", hits + misses);
            SkyforgeRuntimePerformanceMetrics.recordSample(metricPrefix + ".hits", hits);
            SkyforgeRuntimePerformanceMetrics.recordSample(metricPrefix + ".misses", misses);
            SkyforgeRuntimePerformanceMetrics.recordSample(metricPrefix + ".uniquePositions", values.size());
        }
    }

    static final class Scope implements AutoCloseable {
        private final Execution execution;
        private boolean closed;

        private Scope(Execution execution) {
            this.execution = execution;
        }

        Execution execution() {
            requireActive();
            return execution;
        }

        void requireActive() {
            if (closed || ACTIVE.get() != execution) {
                throw new IllegalStateException("Skyforge population execution scope is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            execution.commitDeferredEffects();
            execution.recordPerformanceEvidence();
            closed = true;
            ACTIVE.remove();
        }
    }
}
