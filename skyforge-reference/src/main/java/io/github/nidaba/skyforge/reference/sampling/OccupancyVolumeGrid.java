package io.github.nidaba.skyforge.reference.sampling;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable positive-density classification over a canonical volume grid. */
public final class OccupancyVolumeGrid {
    /** Schema for the canonical byte-per-sample occupancy representation. */
    public static final int BINARY_SCHEMA_VERSION = 1;

    private static final byte[] MAGIC = {'S', 'F', 'O', 'C', 'C', 0, 0, 1};

    private final VolumeGridSpec specification;
    private final byte[] values;

    /** Creates a defensive occupancy grid whose values must be exactly zero or one. */
    public OccupancyVolumeGrid(VolumeGridSpec specification, byte[] values) {
        this.specification = Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(values, "values");
        if (values.length != specification.sampleCount()) {
            throw new IllegalArgumentException("value count does not match volume dimensions");
        }
        this.values = values.clone();
        for (byte value : this.values) {
            if (value != 0 && value != 1) {
                throw new IllegalArgumentException("occupancy values must be zero or one");
            }
        }
    }

    /** Classifies finite density with the accepted strict positive-inside convention. */
    public static OccupancyVolumeGrid fromDensity(ScalarVolumeGrid density) {
        Objects.requireNonNull(density, "density");
        VolumeGridSpec grid = density.specification();
        byte[] occupancy = new byte[grid.sampleCount()];
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                for (int x = 0; x < grid.xSamples(); x++) {
                    int index = grid.linearIndex(x, y, z);
                    occupancy[index] = density.valueAt(x, y, z) > 0.0 ? (byte) 1 : (byte) 0;
                }
            }
        }
        return new OccupancyVolumeGrid(grid, occupancy);
    }

    /** Returns the immutable volume specification. */
    public VolumeGridSpec specification() {
        return specification;
    }

    /** Returns whether one canonical sample is solid. */
    public boolean isSolidAt(int xIndex, int yIndex, int zIndex) {
        return values[specification.linearIndex(xIndex, yIndex, zIndex)] == 1;
    }

    /** Returns a defensive canonical-order byte copy. */
    public byte[] values() {
        return values.clone();
    }

    /** Returns the number of strictly positive-density samples. */
    public int solidSampleCount() {
        int count = 0;
        for (byte value : values) {
            count += value;
        }
        return count;
    }

    /** Writes the versioned canonical byte-per-sample representation. */
    public void writeCanonical(OutputStream destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        DataOutputStream output = new DataOutputStream(destination);
        output.write(MAGIC);
        output.writeInt(BINARY_SCHEMA_VERSION);
        output.writeInt(specification.xSamples());
        output.writeInt(specification.ySamples());
        output.writeInt(specification.zSamples());
        output.writeLong(Double.doubleToRawLongBits(specification.minimumX()));
        output.writeLong(Double.doubleToRawLongBits(specification.maximumX()));
        output.writeLong(Double.doubleToRawLongBits(specification.minimumY()));
        output.writeLong(Double.doubleToRawLongBits(specification.maximumY()));
        output.writeLong(Double.doubleToRawLongBits(specification.minimumZ()));
        output.writeLong(Double.doubleToRawLongBits(specification.maximumZ()));
        output.write(values);
        output.flush();
    }

    /** Returns the lowercase SHA-256 checksum of the canonical representation. */
    public String sha256() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try {
            writeCanonical(new OutputStream() {
                @Override
                public void write(int value) {
                    digest.update((byte) value);
                }

                @Override
                public void write(byte[] buffer, int offset, int length) {
                    digest.update(buffer, offset, length);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory checksum failed", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
