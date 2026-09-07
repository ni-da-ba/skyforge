package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives the tight closed integer-voxel support bounds of one certified compiled island.
 *
 * <p>The provider certificate remains the proof that the finite horizontal search domain is
 * complete. This adapter then evaluates only integer Minecraft columns inside that certified
 * domain and shrinks the backend query/admission box to coordinates that can actually contain a
 * solid voxel. It changes no authored geometry and introduces no new morphology policy.
 */
final class SkyforgeExactVoxelSupportBounds {
    private SkyforgeExactVoxelSupportBounds() {}

    static Result derive(
            CompiledSkyIslandVolume volume,
            CertifiedSkyIslandSupportEnvelope certificate,
            SkyIslandTerrainProfile terrainProfile) {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(certificate, "certificate");
        Objects.requireNonNull(terrainProfile, "terrainProfile");

        var descriptor = volume.descriptor();
        int scanMinimumX = floorToInt(descriptor.centerX() - certificate.maximumHorizontalRadius());
        int scanMaximumX = ceilToInt(descriptor.centerX() + certificate.maximumHorizontalRadius());
        int scanMinimumZ = floorToInt(descriptor.centerZ() - certificate.maximumHorizontalRadius());
        int scanMaximumZ = ceilToInt(descriptor.centerZ() + certificate.maximumHorizontalRadius());

        SkyIslandTerrainInterpreter interpreter =
                new SkyIslandTerrainInterpreter(volume, terrainProfile);
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        int occupiedColumns = 0;
        long scannedColumns = 0L;

        for (int x = scanMinimumX; x <= scanMaximumX; x++) {
            for (int z = scanMinimumZ; z <= scanMaximumZ; z++) {
                scannedColumns++;
                Optional<ColumnRange> range = integerSolidRange(interpreter, x, z);
                if (range.isEmpty()) {
                    continue;
                }
                ColumnRange solid = range.orElseThrow();
                occupiedColumns++;
                minimumX = Math.min(minimumX, x);
                maximumX = Math.max(maximumX, x);
                minimumY = Math.min(minimumY, solid.minimumY());
                maximumY = Math.max(maximumY, solid.maximumY());
                minimumZ = Math.min(minimumZ, z);
                maximumZ = Math.max(maximumZ, z);
            }
        }

        if (occupiedColumns == 0) {
            throw new IllegalStateException("certified compiled island has no integer Minecraft voxel support");
        }
        if (minimumX == scanMinimumX
                || maximumX == scanMaximumX
                || minimumZ == scanMinimumZ
                || maximumZ == scanMaximumZ) {
            throw new IllegalStateException(
                    "compiled integer support touched the certified horizontal search boundary");
        }

        return new Result(
                new WorldBounds(
                        minimumX,
                        maximumX,
                        minimumY,
                        maximumY,
                        minimumZ,
                        maximumZ),
                occupiedColumns,
                scannedColumns,
                certificate.certificateKind());
    }

    static Optional<ColumnRange> integerSolidRange(
            SkyIslandTerrainInterpreter interpreter,
            int worldX,
            int worldZ) {
        Objects.requireNonNull(interpreter, "interpreter");
        double underside = interpreter.undersideSurfaceHeight(worldX, worldZ);
        double upper = interpreter.upperSurfaceHeight(worldX, worldZ);
        int candidateMinimum = floorToInt(underside) + 1;
        int candidateMaximum = ceilToInt(upper) - 1;
        if (candidateMaximum < candidateMinimum) {
            return Optional.empty();
        }

        int first = candidateMinimum;
        while (first <= candidateMaximum
                && !interpreter.classify(worldX, first, worldZ).isSolid()) {
            first++;
        }
        if (first > candidateMaximum) {
            return Optional.empty();
        }

        int last = candidateMaximum;
        while (last >= first
                && !interpreter.classify(worldX, last, worldZ).isSolid()) {
            last--;
        }
        if (last < first) {
            throw new IllegalStateException("compiled integer support became discontinuous during bounds derivation");
        }
        return Optional.of(new ColumnRange(first, last));
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("support coordinate exceeds integer range: " + value);
        }
        return (int) floored;
    }

    private static int ceilToInt(double value) {
        double ceiled = Math.ceil(value);
        if (ceiled < Integer.MIN_VALUE || ceiled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("support coordinate exceeds integer range: " + value);
        }
        return (int) ceiled;
    }

    record ColumnRange(int minimumY, int maximumY) {
        ColumnRange {
            if (maximumY < minimumY) {
                throw new IllegalArgumentException("solid column maximum precedes minimum");
            }
        }
    }

    record Result(
            WorldBounds bounds,
            int occupiedColumns,
            long scannedColumns,
            String certificateKind) {
        Result {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(certificateKind, "certificateKind");
            if (occupiedColumns <= 0 || scannedColumns < occupiedColumns) {
                throw new IllegalArgumentException("invalid exact-support evidence counts");
            }
        }
    }
}
