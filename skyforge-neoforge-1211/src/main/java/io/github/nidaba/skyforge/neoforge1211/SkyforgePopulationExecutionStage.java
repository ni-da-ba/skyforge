package io.github.nidaba.skyforge.neoforge1211;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Thread-confined execution state for one exact-volume native population attempt. */
final class SkyforgePopulationExecutionStage {
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();
    private static final int MAX_WRITE_PREFLIGHT_EVIDENCE_SAMPLES = 32;
    private static final AtomicInteger WRITE_PREFLIGHT_EVIDENCE_COUNT = new AtomicInteger();
    private static final ConcurrentLinkedQueue<String> WRITE_PREFLIGHT_EVIDENCE = new ConcurrentLinkedQueue<>();

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

    static String writePreflightRejectionEvidence() {
        return String.join(" | ", WRITE_PREFLIGHT_EVIDENCE);
    }

    private static boolean originChunkContains(
            SkyforgePopulationOperation operation,
            BlockPos position) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        return (position.getX() >> 4) == operation.originChunk().x
                && (position.getZ() >> 4) == operation.originChunk().z;
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
        Predicate<BlockPos> attachmentRootSolid = cachedOwnerSolid;
        if (operation.generationStep()
                == net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
            // Surface ecology may attach to an exposed same-volume surface across a chunk boundary,
            // but the neighboring terrain remains outside this operation's write authority. Keep
            // foreign volumes as hard barriers and admit only bounded attachments rooted in this
            // operation's owner terrain or read-only neighboring owner terrain.
            attachmentOwnerSolid =
                    position -> cachedOwnerSolid.test(position) && originChunkContains(operation, position);
        }
        Execution execution = new Execution(
                level,
                operation,
                domainBiome,
                cachedOwnerSolid,
                cachedForeignSolid,
                new SkyforgePopulationAttachmentEnvelope(
                        attachmentOwnerSolid,
                        cachedForeignSolid,
                        attachmentRootSolid,
                        maximumAttachmentDepth));
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static final class Execution {
        private final Optional<WorldGenLevel> level;
        private final SkyforgePopulationOperation operation;
        private final Optional<Holder<Biome>> domainBiome;
        private final CachedBlockPredicate ownerSolid;
        private final CachedBlockPredicate foreignSolid;
        private final SkyforgePopulationAttachmentEnvelope attachmentEnvelope;
        private final int surfaceRandomPatchHeightSpread;
        private int writePreflightRejections;
        private int writeDecisionRejections;

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
            this.surfaceRandomPatchHeightSpread = resolveSurfaceRandomPatchHeightSpread(this.level, this.operation);
        }

        private static int resolveSurfaceRandomPatchHeightSpread(
                Optional<WorldGenLevel> level,
                SkyforgePopulationOperation operation) {
            if (level.isEmpty()
                    || operation.generationStep()
                            != net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()) {
                return 0;
            }
            var activeLevel = level.orElseThrow();
            var registry = activeLevel.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            var key = ResourceKey.create(Registries.PLACED_FEATURE, operation.nativeDefinitionKey());
            return registry.getHolder(key)
                    .map(holder -> holder.value().feature().value().config())
                    .filter(RandomPatchConfiguration.class::isInstance)
                    .map(RandomPatchConfiguration.class::cast)
                    .map(RandomPatchConfiguration::ySpread)
                    .orElse(0);
        }

        int surfaceRandomPatchHeightSpread() {
            return surfaceRandomPatchHeightSpread;
        }

        SkyforgePopulationOperation operation() {
            return operation;
        }

        Optional<Holder<Biome>> domainBiome() {
            return domainBiome;
        }

        boolean isVisible(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return (ownerSolid.test(position) && originChunkContains(position))
                    || attachmentEnvelope.ownsAttachment(position);
        }

        /**
         * Returns the deterministic virtual state for a read hidden from this population operation.
         *
         * <p>Same-volume solid terrain in a neighboring chunk must not expose its live materialization
         * state: that chunk may or may not have completed deferred Skyforge realization yet, depending
         * on Minecraft scheduling. For exposed vegetation support only, use the bounded immutable
         * pre-decoration provider-surface snapshot; all other neighboring owner terrain remains an
         * inert solid barrier. Non-owner exterior keeps the phase-specific virtualization (AIR for
         * surface vegetation, BEDROCK for sensitive underground phases), so native attachments can
         * cross chunk seams without observing neighboring materialization order.
         */
        BlockState hiddenBlockState(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if (ownerSolid.test(position)) {
                if (isVegetalDecoration()
                        && !originChunkContains(position)
                        && !ownerSolid.test(position.above())
                        && level.isPresent()) {
                    var providerSurface = SkyforgeNativeSurfaceSnapshotStage.surfaceBlockAt(position);
                    if (providerSurface.isPresent()) {
                        return providerSurface.orElseThrow();
                    }
                }
                return net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
            }
            return SkyforgeNativeInteriorPlacementPolicy.hiddenExteriorBlockState(operation);
        }

        private boolean isVegetalDecoration() {
            return operation.generationStep()
                    == net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        }

        private boolean originChunkContains(BlockPos position) {
            return SkyforgePopulationExecutionStage.originChunkContains(operation, position);
        }

        boolean canWrite(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)
                    && attachmentEnvelope.canAcceptWrite(position);
        }

        boolean acceptWrite(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)
                    && attachmentEnvelope.acceptWrite(position);
        }

        boolean acceptWrite(BlockPos position, BlockState state) {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(state, "state");
            if (!SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)) {
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
            return attachmentEnvelope.acceptWrite(position);
        }

        String writePreflightRejectionReason(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if (!SkyforgeNativeInteriorPlacementPolicy.canWrite(operation, position, ownerSolid)) {
                return "native-interior-policy";
            }
            if (foreignSolid.test(position)) {
                return "foreign-island-volume";
            }
            return "owner-or-attachment-envelope";
        }

        private int adjacentOwnerSolidCount(BlockPos position) {
            int adjacentOwnerCells = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 || dy != 0 || dz != 0)
                                && ownerSolid.test(position.offset(dx, dy, dz))) {
                            adjacentOwnerCells = Math.addExact(adjacentOwnerCells, 1);
                        }
                    }
                }
            }
            return adjacentOwnerCells;
        }

        private String adjacentOwnerChunkEvidence(BlockPos position) {
            int originChunkOwnerCells = 0;
            int neighboringChunkOwnerCells = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos neighbor = position.offset(dx, dy, dz);
                        if (!ownerSolid.test(neighbor)) {
                            continue;
                        }
                        if (originChunkContains(neighbor)) {
                            originChunkOwnerCells = Math.addExact(originChunkOwnerCells, 1);
                        } else {
                            neighboringChunkOwnerCells = Math.addExact(neighboringChunkOwnerCells, 1);
                        }
                    }
                }
            }
            return "attachmentOriginChunk=" + operation.originChunk()
                    + ", adjacentOwnerSolidInOriginChunk=" + originChunkOwnerCells
                    + ", adjacentOwnerSolidOutsideOriginChunk=" + neighboringChunkOwnerCells;
        }

        private String liveHeightEvidence(BlockPos position) {
            if (level.isEmpty()) {
                return "heightmaps=unavailable";
            }
            WorldGenLevel activeLevel = level.orElseThrow();
            return "heightmaps[motionBlocking=" + safeHeight(activeLevel, Heightmap.Types.MOTION_BLOCKING, position)
                    + ", motionBlockingNoLeaves="
                    + safeHeight(activeLevel, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position)
                    + ", oceanFloor=" + safeHeight(activeLevel, Heightmap.Types.OCEAN_FLOOR, position)
                    + ", oceanFloorWg=" + safeHeight(activeLevel, Heightmap.Types.OCEAN_FLOOR_WG, position)
                    + ", worldSurface=" + safeHeight(activeLevel, Heightmap.Types.WORLD_SURFACE, position)
                    + ", worldSurfaceWg=" + safeHeight(activeLevel, Heightmap.Types.WORLD_SURFACE_WG, position)
                    + "]";
        }

        private static String safeHeight(
                WorldGenLevel level,
                Heightmap.Types type,
                BlockPos position) {
            try {
                return Integer.toString(level.getHeight(type, position.getX(), position.getZ()));
            } catch (RuntimeException failure) {
                return "error:" + failure.getClass().getSimpleName();
            }
        }

        void recordWritePreflightRejection(BlockPos position, String reason) {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(reason, "reason");
            writePreflightRejections = Math.addExact(writePreflightRejections, 1);
            if (writePreflightRejections == 1
                    && Boolean.getBoolean(SkyforgeNeoForge1211BiomePopulationDevRuntime.ENABLE_PROPERTY)) {
                int sampleIndex = WRITE_PREFLIGHT_EVIDENCE_COUNT.getAndIncrement();
                if (sampleIndex < MAX_WRITE_PREFLIGHT_EVIDENCE_SAMPLES) {
                    var ownerColumn = SkyforgeNeoForge1211SurfaceStage.integerSolidRange(
                            operation.volumeId(), position.getX(), position.getZ());
                    String ownerColumnEvidence = ownerColumn
                            .map(range -> {
                                int firstFreeY = Math.addExact(range.maximumY(), 1);
                                return "ownerColumnRange=" + range.minimumY() + ".." + range.maximumY()
                                        + ", ownerFirstFreeY=" + firstFreeY
                                        + ", positionDeltaFromFirstFreeY="
                                        + Math.subtractExact(position.getY(), firstFreeY);
                            })
                            .orElse("ownerColumnRange=empty");
                    WRITE_PREFLIGHT_EVIDENCE.add(
                            "volume=" + operation.volumeId().path()
                                    + ", feature=" + operation.nativeDefinitionKey()
                                    + ", position=" + position.toShortString()
                                    + ", reason=" + reason
                                    + ", ownerSolid=" + ownerSolid.test(position)
                                    + ", foreignSolid=" + foreignSolid.test(position)
                                    + ", adjacentOwnerSolidBlocks=" + adjacentOwnerSolidCount(position)
                                    + ", " + adjacentOwnerChunkEvidence(position)
                                    + ", " + ownerColumnEvidence
                                    + ", " + attachmentEnvelope.rejectionEvidence(position)
                                    + ", " + liveHeightEvidence(position));
                }
            }
        }

        void recordWriteDecisionRejection() {
            writeDecisionRejections = Math.addExact(writeDecisionRejections, 1);
        }

        int writePreflightRejections() {
            return writePreflightRejections;
        }

        int writeDecisionRejections() {
            return writeDecisionRejections;
        }

        int attachmentCount() {
            return attachmentEnvelope.attachmentCount();
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
            execution.recordPerformanceEvidence();
            closed = true;
            ACTIVE.remove();
        }
    }
}
