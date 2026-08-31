package io.github.nidaba.skyforge.world;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable sampled occupancy plus backend-query accounting for one world lattice. */
public record WorldRegionOccupancy(
        WorldSampleGrid grid,
        byte[] occupancy,
        int spatialQueries,
        int candidateVolumeReferences) {

    public WorldRegionOccupancy {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(occupancy, "occupancy");
        if (occupancy.length != grid.sampleCount()) {
            throw new IllegalArgumentException("occupancy length differs from grid sample count");
        }
        if (spatialQueries <= 0) {
            throw new IllegalArgumentException("spatialQueries must be positive");
        }
        if (candidateVolumeReferences < 0) {
            throw new IllegalArgumentException("candidateVolumeReferences must be non-negative");
        }
        occupancy = occupancy.clone();
    }

    @Override
    public byte[] occupancy() {
        return occupancy.clone();
    }

    /** Number of solid sampled lattice points. */
    public int solidSampleCount() {
        int count = 0;
        for (byte value : occupancy) {
            if (value != 0) {
                count++;
            }
        }
        return count;
    }

    /** Stable identity for exact tiled-versus-monolithic comparisons. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(occupancy));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
