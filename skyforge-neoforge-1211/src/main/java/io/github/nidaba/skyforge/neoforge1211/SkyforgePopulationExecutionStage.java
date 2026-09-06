package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
        Execution execution = new Execution(
                level,
                operation,
                domainBiome,
                ownerSolid,
                new SkyforgePopulationAttachmentEnvelope(ownerSolid, foreignSolid, maximumAttachmentDepth));
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static final class Execution {
        private final Optional<WorldGenLevel> level;
        private final SkyforgePopulationOperation operation;
        private final Optional<Holder<Biome>> domainBiome;
        private final Predicate<BlockPos> ownerSolid;
        private final SkyforgePopulationAttachmentEnvelope attachmentEnvelope;

        private Execution(
                Optional<WorldGenLevel> level,
                SkyforgePopulationOperation operation,
                Optional<Holder<Biome>> domainBiome,
                Predicate<BlockPos> ownerSolid,
                SkyforgePopulationAttachmentEnvelope attachmentEnvelope) {
            this.level = Objects.requireNonNull(level, "level");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.domainBiome = Objects.requireNonNull(domainBiome, "domainBiome");
            this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
            this.attachmentEnvelope = Objects.requireNonNull(attachmentEnvelope, "attachmentEnvelope");
        }

        SkyforgePopulationOperation operation() {
            return operation;
        }

        Optional<Holder<Biome>> domainBiome() {
            return domainBiome;
        }

        boolean isVisible(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return ownerSolid.test(position) || attachmentEnvelope.ownsAttachment(position);
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

        int attachmentCount() {
            return attachmentEnvelope.attachmentCount();
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
            closed = true;
            ACTIVE.remove();
        }
    }
}
