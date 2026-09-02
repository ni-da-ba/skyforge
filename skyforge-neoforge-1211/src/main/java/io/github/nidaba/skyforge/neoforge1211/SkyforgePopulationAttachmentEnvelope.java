package io.github.nidaba.skyforge.neoforge1211;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Bounded topological write envelope for one island-owned population operation.
 *
 * <p>Writes inside exact owner terrain are always admitted. Writes outside owner terrain are
 * admitted only when they remain connected to owner terrain through previously admitted attachment
 * writes, up to a configured maximum attachment depth. Foreign Skyforge terrain is a hard veto even
 * when it lies inside that attachment radius. This lets trees, leaves and similar native content
 * extend beyond strict island density without granting an unrestricted geometric box or allowing one
 * island's population stream to enter another island.
 */
final class SkyforgePopulationAttachmentEnvelope {
    private final Predicate<BlockPos> ownerSolid;
    private final Predicate<BlockPos> foreignSolid;
    private final int maximumAttachmentDepth;
    private final Map<BlockPos, Integer> attachmentDepths = new HashMap<>();

    SkyforgePopulationAttachmentEnvelope(
            Predicate<BlockPos> ownerSolid,
            int maximumAttachmentDepth) {
        this(ownerSolid, ignored -> false, maximumAttachmentDepth);
    }

    SkyforgePopulationAttachmentEnvelope(
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            int maximumAttachmentDepth) {
        this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
        this.foreignSolid = Objects.requireNonNull(foreignSolid, "foreignSolid");
        if (maximumAttachmentDepth < 0) {
            throw new IllegalArgumentException("maximumAttachmentDepth must be non-negative");
        }
        this.maximumAttachmentDepth = maximumAttachmentDepth;
    }

    /** Returns whether the position may be written, recording attachment provenance on success. */
    boolean acceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        BlockPos immutable = position.immutable();
        if (foreignSolid.test(immutable)) {
            return false;
        }
        if (ownerSolid.test(immutable)) {
            return true;
        }
        Integer existing = attachmentDepths.get(immutable);
        if (existing != null) {
            return true;
        }
        if (maximumAttachmentDepth == 0) {
            return false;
        }

        int parentDepth = minimumAdjacentDepth(immutable);
        if (parentDepth < 0 || parentDepth >= maximumAttachmentDepth) {
            return false;
        }
        attachmentDepths.put(immutable, parentDepth + 1);
        return true;
    }

    boolean ownsAttachment(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return attachmentDepths.containsKey(position);
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
                    if (ownerSolid.test(neighbor)) {
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
