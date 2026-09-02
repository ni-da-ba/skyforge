package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Thread-confined execution state for one exact-volume native population attempt. */
final class SkyforgePopulationExecutionStage {
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private SkyforgePopulationExecutionStage() {}

    static Scope open(SkyforgePopulationOperation operation, int maximumAttachmentDepth) {
        Objects.requireNonNull(operation, "operation");
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
        return open(operation, ownerSolid, foreignSolid, maximumAttachmentDepth);
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
        return open(operation, ownerSolid, foreignSolid, maximumAttachmentDepth);
    }

    static Optional<Execution> activeExecution() {
        return Optional.ofNullable(ACTIVE.get());
    }

    private static Scope open(
            SkyforgePopulationOperation operation,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(ownerSolid, "ownerSolid");
        Objects.requireNonNull(foreignSolid, "foreignSolid");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge population executions are not supported");
        }
        Execution execution = new Execution(
                operation,
                ownerSolid,
                new SkyforgePopulationAttachmentEnvelope(ownerSolid, foreignSolid, maximumAttachmentDepth));
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static final class Execution {
        private final SkyforgePopulationOperation operation;
        private final Predicate<BlockPos> ownerSolid;
        private final SkyforgePopulationAttachmentEnvelope attachmentEnvelope;

        private Execution(
                SkyforgePopulationOperation operation,
                Predicate<BlockPos> ownerSolid,
                SkyforgePopulationAttachmentEnvelope attachmentEnvelope) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
            this.attachmentEnvelope = Objects.requireNonNull(attachmentEnvelope, "attachmentEnvelope");
        }

        SkyforgePopulationOperation operation() {
            return operation;
        }

        boolean isVisible(BlockPos position) {
            Objects.requireNonNull(position, "position");
            return ownerSolid.test(position) || attachmentEnvelope.ownsAttachment(position);
        }

        boolean acceptWrite(BlockPos position) {
            return attachmentEnvelope.acceptWrite(Objects.requireNonNull(position, "position"));
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
