package io.github.nidaba.skyforge.world;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable sampled backend-neutral terrain semantics for one world lattice. */
public record WorldRegionTerrain(
        WorldSampleGrid grid,
        byte[] semantics,
        int spatialQueries,
        int candidateVolumeReferences) {

    public WorldRegionTerrain {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(semantics, "semantics");
        if (semantics.length != grid.sampleCount()) {
            throw new IllegalArgumentException("semantic length differs from grid sample count");
        }
        if (spatialQueries <= 0) {
            throw new IllegalArgumentException("spatialQueries must be positive");
        }
        if (candidateVolumeReferences < 0) {
            throw new IllegalArgumentException("candidateVolumeReferences must be non-negative");
        }
        int semanticCount = SkyIslandTerrainSemantic.values().length;
        for (byte encoded : semantics) {
            int ordinal = Byte.toUnsignedInt(encoded);
            if (ordinal >= semanticCount) {
                throw new IllegalArgumentException("unknown encoded terrain semantic ordinal " + ordinal);
            }
        }
        semantics = semantics.clone();
    }

    @Override
    public byte[] semantics() {
        return semantics.clone();
    }

    /** Semantic value at one lattice index. */
    public SkyIslandTerrainSemantic semanticAt(int xIndex, int yIndex, int zIndex) {
        return SkyIslandTerrainSemantic.values()[
                Byte.toUnsignedInt(semantics[grid.linearIndex(xIndex, yIndex, zIndex)])];
    }

    /** Minimal backend-visible sample context at one lattice index. */
    public SkyIslandTerrainSampleContext sampleContextAt(int xIndex, int yIndex, int zIndex) {
        return new SkyIslandTerrainSampleContext(
                grid.xAt(xIndex),
                grid.yAt(yIndex),
                grid.zAt(zIndex),
                semanticAt(xIndex, yIndex, zIndex));
    }

    /** Number of samples carrying one semantic value. */
    public int count(SkyIslandTerrainSemantic semantic) {
        Objects.requireNonNull(semantic, "semantic");
        int expected = semantic.ordinal();
        int count = 0;
        for (byte encoded : semantics) {
            if (Byte.toUnsignedInt(encoded) == expected) {
                count++;
            }
        }
        return count;
    }

    /** Number of non-air sampled points. */
    public int solidSampleCount() {
        return semantics.length - count(SkyIslandTerrainSemantic.AIR);
    }

    /** Occupancy projection proving material interpretation does not alter geometry. */
    public WorldRegionOccupancy occupancyProjection() {
        byte[] occupancy = new byte[semantics.length];
        for (int index = 0; index < semantics.length; index++) {
            SkyIslandTerrainSemantic semantic = SkyIslandTerrainSemantic.values()[
                    Byte.toUnsignedInt(semantics[index])];
            occupancy[index] = semantic.isSolid() ? (byte) 1 : 0;
        }
        return new WorldRegionOccupancy(grid, occupancy, spatialQueries, candidateVolumeReferences);
    }

    /** Stable identity for exact semantic comparisons across backend tilings. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(semantics));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
