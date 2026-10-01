package io.github.nidaba.skyforge.reference.sampling;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable finite scalar samples stored in canonical z-row, x-column order. */
public final class ScalarGrid {
    /** Schema for the canonical binary grid representation. */
    public static final int BINARY_SCHEMA_VERSION = 1;

    private static final byte[] MAGIC = {'S', 'F', 'G', 'R', 'I', 'D', 0, 1};

    private final GridSpec specification;
    private final double[] values;

    /** Creates a defensive, finite-valued grid. */
    public ScalarGrid(GridSpec specification, double[] values) {
        this.specification = Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(values, "values");
        if (values.length != specification.sampleCount()) {
            throw new IllegalArgumentException("value count does not match grid dimensions");
        }
        this.values = values.clone();
        for (double value : this.values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("grid values must be finite");
            }
        }
    }

    /** Returns the immutable grid specification. */
    public GridSpec specification() {
        return specification;
    }

    /** Returns the value at a grid coordinate. */
    public double valueAt(int xIndex, int zIndex) {
        if (xIndex < 0 || xIndex >= specification.width()) {
            throw new IndexOutOfBoundsException("xIndex outside grid");
        }
        if (zIndex < 0 || zIndex >= specification.height()) {
            throw new IndexOutOfBoundsException("zIndex outside grid");
        }
        return values[zIndex * specification.width() + xIndex];
    }

    /** Returns a defensive row-major value copy. */
    public double[] values() {
        return values.clone();
    }

    /** Compares every sample by raw binary64 bits. */
    public boolean rawValuesEqual(ScalarGrid other) {
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

    /** Writes the versioned big-endian binary representation used for evidence checksums. */
    public void writeCanonical(OutputStream destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        DataOutputStream output = new DataOutputStream(destination);
        output.write(MAGIC);
        output.writeInt(BINARY_SCHEMA_VERSION);
        output.writeInt(specification.width());
        output.writeInt(specification.height());
        output.writeLong(Double.doubleToRawLongBits(specification.minimumX()));
        output.writeLong(Double.doubleToRawLongBits(specification.maximumX()));
        output.writeLong(Double.doubleToRawLongBits(specification.minimumZ()));
        output.writeLong(Double.doubleToRawLongBits(specification.maximumZ()));
        for (double value : values) {
            output.writeLong(Double.doubleToRawLongBits(value));
        }
        output.flush();
    }

    /** Returns the lowercase SHA-256 checksum of the canonical binary representation. */
    public String sha256() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try {
            writeCanonical(new DigestOutputStreamAdapter(digest));
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory checksum failed", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
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
