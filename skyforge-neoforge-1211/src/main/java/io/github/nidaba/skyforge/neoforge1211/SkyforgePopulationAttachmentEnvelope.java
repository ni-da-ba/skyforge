package io.github.nidaba.skyforge.neoforge1211;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Bounded topological write envelope for one island-owned population operation.
 *
 * <p>Writes inside the operation's authorized owner terrain are admitted. Writes outside that
 * writable region are admitted only when they remain connected to same-volume terrain or previously
 * admitted attachment writes, up to a configured maximum attachment depth. Same-volume terrain in a
 * neighboring chunk may anchor an attachment but remains read-only to this operation. Foreign
 * Skyforge terrain is a hard veto even when it lies inside that attachment radius.
 */
final class SkyforgePopulationAttachmentEnvelope {
    private final Predicate<BlockPos> ownerSolid;
    private final Predicate<BlockPos> attachmentRootSolid;
    private final Predicate<BlockPos> foreignSolid;
    private final int maximumAttachmentDepth;
    private final Map<BlockPos, Integer> attachmentDepths = new HashMap<>();

    SkyforgePopulationAttachmentEnvelope(
            Predicate<BlockPos> ownerSolid,
            int maximumAttachmentDepth) {
        this(ownerSolid, ignored -> false, ownerSolid, maximumAttachmentDepth);
    }

    SkyforgePopulationAttachmentEnvelope(
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            int maximumAttachmentDepth) {
        this(ownerSolid, foreignSolid, ownerSolid, maximumAttachmentDepth);
    }

    SkyforgePopulationAttachmentEnvelope(
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            Predicate<BlockPos> attachmentRootSolid,
            int maximumAttachmentDepth) {
        this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
        this.attachmentRootSolid = Objects.requireNonNull(attachmentRootSolid, "attachmentRootSolid");
        this.foreignSolid = Objects.requireNonNull(foreignSolid, "foreignSolid");
        if (maximumAttachmentDepth < 0) {
            throw new IllegalArgumentException("maximumAttachmentDepth must be non-negative");
        }
        this.maximumAttachmentDepth = maximumAttachmentDepth;
    }

    /**
     * Returns whether the position could be admitted without mutating attachment provenance.
     *
     * <p>Some optimized native features call {@code WorldGenLevel.ensureCanWrite} before acquiring a
     * raw chunk section and then mutate that section directly. That preflight must be able to reject
     * foreign/base-world coordinates without prematurely recording an attachment that the feature may
     * never actually write.
     */
    boolean canAcceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        BlockPos immutable = position.immutable();
        if (foreignSolid.test(immutable)
                || (attachmentRootSolid.test(immutable) && !ownerSolid.test(immutable))) {
            return false;
        }
        if (ownerSolid.test(immutable) || attachmentDepths.containsKey(immutable)) {
            return true;
        }
        if (maximumAttachmentDepth == 0) {
            return false;
        }
        int parentDepth = minimumAdjacentDepth(immutable);
        return parentDepth >= 0 && parentDepth < maximumAttachmentDepth;
    }

    /** Returns whether the position may be written, recording attachment provenance on success. */
    boolean acceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        BlockPos immutable = position.immutable();
        if (!canAcceptWrite(immutable)) {
            return false;
        }
        if (ownerSolid.test(immutable) || attachmentDepths.containsKey(immutable)) {
            return true;
        }

        int parentDepth = minimumAdjacentDepth(immutable);
        if (parentDepth < 0 || parentDepth >= maximumAttachmentDepth) {
            throw new IllegalStateException("write preflight and attachment admission diverged");
        }
        attachmentDepths.put(immutable, parentDepth + 1);
        return true;
    }

    boolean ownsAttachment(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return attachmentDepths.containsKey(position);
    }

    String rejectionEvidence(BlockPos position) {
        Objects.requireNonNull(position, "position");
        int parentDepth = minimumAdjacentDepth(position);
        return "attachmentDepthLimit=" + maximumAttachmentDepth
                + ", attachmentParentDepth=" + (parentDepth < 0 ? "none" : parentDepth)
                + ", existingAttachment=" + attachmentDepths.containsKey(position.immutable());
    }

    int attachmentCount() {
        return attachmentDepths.size();
    }

    private int minimumAdjacentDepth(BlockPos position) {
        int minimum = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos neighbor = position.offset(dx, dy, dz);
                    if (foreignSolid.test(neighbor)) {
                        continue;
                    }
                    if (attachmentRootSolid.test(neighbor) || ownerSolid.test(neighbor)) {
                        minimum = 0;
                        continue;
                    }
                    Integer attachmentDepth = attachmentDepths.get(neighbor);
                    if (attachmentDepth != null) {
                        minimum = Math.min(minimum, attachmentDepth);
                    }
                }
            }
        }
        return minimum == Integer.MAX_VALUE ? -1 : minimum;
    }
}
