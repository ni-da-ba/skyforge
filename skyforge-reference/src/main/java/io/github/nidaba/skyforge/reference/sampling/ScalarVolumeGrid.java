package io.github.nidaba.skyforge.reference.sampling;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable binary64 samples stored with x fastest, then z, then y. */
public final class ScalarVolumeGrid {
    /** Schema for the canonical binary volume representation. */
    public static final int BINARY_SCHEMA_VERSION = 1;

    private static final byte[] MAGIC = {'S', 'F', 'V', 'O', 'L', 0, 0, 1};

    private final VolumeGridSpec specification;
    private final double[] values;

    /** Creates a defensive, finite-valued scalar volume. */
    public ScalarVolumeGrid(VolumeGridSpec specification, double[] values) {
        this.specification = Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(values, "values");
        if (values.length != specification.sampleCount()) {
            throw new IllegalArgumentException("value count does not match volume dimensions");
        }
        this.values = values.clone();
        for (double value : this.values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("volume values must be finite");
            }
        }
    }

    /** Returns the immutable volume specification. */
    public VolumeGridSpec specification() {
        return specification;
    }

    /** Returns the scalar at one grid coordinate. */
    public double valueAt(int xIndex, int yIndex, int zIndex) {
        return values[specification.linearIndex(xIndex, yIndex, zIndex)];
    }

    /** Returns a defensive canonical-order value copy. */
    public double[] values() {
        return values.clone();
    }

    /** Compares specifications and every sample by raw binary64 bits. */
    public boolean rawValuesEqual(ScalarVolumeGrid other) {
        Objects.requireNonNull(other, "other");
        if (!specification.equals(other.specification) || values.length != other.values.length) {
            return false;
        }
        for (int index = 0; index < values.length; index++) {
            if (Double.doubleToRawLongBits(values[index])
                    != Double.doubleToRawLongBits(other.values[index])) {
                return false;
            }
        }
        return true;
    }

    /** Writes the versioned big-endian representation used for evidence checksums. */
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
        for (double value : values) {
            output.writeLong(Double.doubleToRawLongBits(value));
        }
        output.flush();
    }

    /** Returns the lowercase SHA-256 checksum of the canonical representation. */
    public String sha256() {
        MessageDigest digest = sha256Digest();
        try {
            writeCanonical(new DigestOutputStreamAdapter(digest));
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory checksum failed", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class DigestOutputStreamAdapter extends OutputStream {
        private final MessageDigest digest;

        private DigestOutputStreamAdapter(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int value) {
            digest.update((byte) value);
        }

        @Override
        public void write(byte[] values, int offset, int length) {
            digest.update(values, offset, length);
        }
    }
}
