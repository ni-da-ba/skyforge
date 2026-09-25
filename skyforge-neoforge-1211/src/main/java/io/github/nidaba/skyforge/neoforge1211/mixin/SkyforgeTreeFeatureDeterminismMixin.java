package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Canonicalizes vanilla tree-finalization working sets only for stable deferred Skyforge ecology.
 *
 * <p>TreeFeature collects root, trunk, and foliage positions in unordered sets and then consumes
 * those sets while updating leaf distance and edge shape. Normal worldgen gets Minecraft's native
 * execution environment. Deferred exact-volume population instead runs later against LevelChunks,
 * where repeated A/B runs demonstrated one-cell tree-finalization drift despite identical seed and
 * identical visible pre-state. Sorting the three position sets before TreeFeature consumes them
 * removes that JVM iteration-order input without changing membership or write authority.
 */
@Mixin(TreeFeature.class)
abstract class SkyforgeTreeFeatureDeterminismMixin {
    @ModifyVariable(method = "updateLeaves", at = @At("HEAD"), argsOnly = true, index = 2)
    private static Set<BlockPos> skyforge$canonicalizeRoots(Set<BlockPos> positions) {
        return canonicalize(positions);
    }

    @ModifyVariable(method = "updateLeaves", at = @At("HEAD"), argsOnly = true, index = 3)
    private static Set<BlockPos> skyforge$canonicalizeTrunks(Set<BlockPos> positions) {
        return canonicalize(positions);
    }

    @ModifyVariable(method = "updateLeaves", at = @At("HEAD"), argsOnly = true, index = 4)
    private static Set<BlockPos> skyforge$canonicalizeFoliage(Set<BlockPos> positions) {
        return canonicalize(positions);
    }


    /**
     * Replaces TreeFeature's seven leaf-distance frontier buckets with insertion-ordered sets.
     *
     * <p>Vanilla creates these buckets with Guava HashSet and repeatedly consumes each bucket via
     * iterator().next(). That makes traversal order depend on hash iteration even after the three
     * input sets are canonicalized. In deferred Skyforge tree finalization the resulting write order
     * is observable through exact-volume write admission, so use LinkedHashSet only for this scoped
     * execution. Ordinary Minecraft tree generation keeps the original HashSet behavior.
     */
    @Redirect(
            method = "updateLeaves",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"))
    private static HashSet<BlockPos> skyforge$canonicalizeLeafDistanceFrontier() {
        return SkyforgeWorldGenRegionDomainBridge.deterministicDeferredTreeFinalizationActive()
                ? new LinkedHashSet<>()
                : new HashSet<>();
    }

    private static Set<BlockPos> canonicalize(Set<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        if (!SkyforgeWorldGenRegionDomainBridge.deterministicDeferredTreeFinalizationActive()) {
            return positions;
        }
        var ordered = positions.stream()
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingInt((BlockPos position) -> position.getX())
                        .thenComparingInt(position -> position.getZ())
                        .thenComparingInt(position -> position.getY()))
                .toList();
        return new LinkedHashSet<>(ordered);
    }
}
