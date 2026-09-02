package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Resolves a terrain-matching projection top while preserving vertical world-volume ownership.
 *
 * <p>The resolver knows nothing about villages, jigsaw templates, or structure registry identity.
 * It receives only the placement anchor Y, Minecraft's ordinary top surface, block opacity, and
 * exact Skyforge solid-volume provenance. A Skyforge surface is skipped only when exactly one
 * volume owns that solid sample and the placement anchor is proven at or below that volume's
 * underside. Ambiguous ownership or unavailable evidence fails open to the caller's vanilla path.
 */
final class MinecraftTerrainProjectionResolver {
    private static final int MAXIMUM_SKIPPED_VOLUME_COUNT = 64;

    private MinecraftTerrainProjectionResolver() {}

    static OptionalInt resolveTop(
            int placementAnchorY,
            int vanillaTopY,
            int minimumBuildY,
            IntPredicate opaqueAtY,
            OwnerLookup ownerLookup,
            UndersideLookup undersideLookup) {
        Objects.requireNonNull(opaqueAtY, "opaqueAtY");
        Objects.requireNonNull(ownerLookup, "ownerLookup");
        Objects.requireNonNull(undersideLookup, "undersideLookup");
        if (vanillaTopY <= minimumBuildY) {
            return OptionalInt.of(vanillaTopY);
        }

        int candidateTopY = vanillaTopY;
        for (int skippedVolumes = 0; skippedVolumes <= MAXIMUM_SKIPPED_VOLUME_COUNT; skippedVolumes++) {
            int candidateSurfaceY = candidateTopY - 1;
            Optional<List<SkyIslandWorldVolumeId>> ownersResult = ownerLookup.ownersAt(candidateSurfaceY);
            if (ownersResult.isEmpty()) {
                return OptionalInt.empty();
            }

            List<SkyIslandWorldVolumeId> owners = ownersResult.orElseThrow();
            if (owners.isEmpty()) {
                return OptionalInt.of(candidateTopY);
            }
            if (owners.size() != 1) {
                // Overlapping solid ownership is intentionally not interpreted here.
                return OptionalInt.empty();
            }

            SkyIslandWorldVolumeId owner = owners.getFirst();
            OptionalDouble undersideResult = undersideLookup.underside(owner);
            if (undersideResult.isEmpty()) {
                return OptionalInt.empty();
            }
            double undersideY = undersideResult.orElseThrow();
            if (!Double.isFinite(undersideY)) {
                return OptionalInt.empty();
            }

            // The anchor lies within/above this volume's vertical envelope, so this surface is not
            // positively proven to belong to an unrelated upper terrain body.
            if (placementAnchorY > undersideY) {
                return OptionalInt.of(candidateTopY);
            }

            if (skippedVolumes == MAXIMUM_SKIPPED_VOLUME_COUNT) {
                return OptionalInt.empty();
            }

            long floor = (long) Math.floor(undersideY);
            int searchY = (int) Math.max(
                    minimumBuildY,
                    Math.min((long) candidateSurfaceY - 1L, floor));
            int lowerSolidY = highestOpaqueAtOrBelow(searchY, minimumBuildY, opaqueAtY);
            if (lowerSolidY == Integer.MIN_VALUE) {
                return OptionalInt.empty();
            }
            candidateTopY = Math.addExact(lowerSolidY, 1);
        }
        return OptionalInt.empty();
    }

    private static int highestOpaqueAtOrBelow(
            int startingY,
            int minimumBuildY,
            IntPredicate opaqueAtY) {
        for (int worldY = startingY; worldY >= minimumBuildY; worldY--) {
            if (opaqueAtY.test(worldY)) {
                return worldY;
            }
        }
        return Integer.MIN_VALUE;
    }

    @FunctionalInterface
    interface OwnerLookup {
        Optional<List<SkyIslandWorldVolumeId>> ownersAt(int worldY);
    }

    @FunctionalInterface
    interface UndersideLookup {
        OptionalDouble underside(SkyIslandWorldVolumeId volumeId);
    }
}
