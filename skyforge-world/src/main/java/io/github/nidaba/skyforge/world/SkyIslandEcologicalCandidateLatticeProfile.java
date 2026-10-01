package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Explicit deterministic island-local candidate lattice for ecological discrete realization.
 *
 * <p>The lattice defines candidate geometry only. It does not imply species identity, occupancy,
 * abundance, or backend feature placement.
 */
public record SkyIslandEcologicalCandidateLatticeProfile(
        int samplerVersion,
        int seedVersion,
        long rootSeed,
        String namespace,
        double cellPitch,
        double maxAxisJitter) {

    /** Version of the deterministic candidate sampler defined by this record. */
    public static final int VERSION = 1;

    /**
     * Maximum conservative lattice cells materialized by one query.
     *
     * <p>This is a technical API/resource bound, not ecological meaning. Larger consumers should
     * tile their query windows.
     */
    public static final int MAX_QUERY_SEARCH_CELLS = 1_000_000;

    private static final double LONG_MIN_AS_DOUBLE = -0x1.0p63;
    private static final double LONG_MAX_EXCLUSIVE_AS_DOUBLE = 0x1.0p63;
    private static final long X_DOMAIN = 0x9e3779b97f4a7c15L;
    private static final long Z_DOMAIN = 0xc2b2ae3d27d4eb4fL;

    public SkyIslandEcologicalCandidateLatticeProfile {
        if (samplerVersion != VERSION) {
            throw new IllegalArgumentException("unsupported ecological candidate sampler version");
        }
        if (seedVersion != SeedDerivation.VERSION) {
            throw new IllegalArgumentException("unsupported ecological candidate seed version");
        }
        SeedDerivation.requireNamespace(namespace);
        if (!Double.isFinite(cellPitch) || cellPitch <= 0.0) {
            throw new IllegalArgumentException("cellPitch must be finite and positive");
        }
        if (!Double.isFinite(maxAxisJitter)
                || maxAxisJitter < 0.0
                || maxAxisJitter >= 0.5 * cellPitch) {
            throw new IllegalArgumentException(
                    "maxAxisJitter must be finite, non-negative, and less than half cellPitch");
        }
    }

    /** Creates a profile using the currently accepted sampler and seed versions. */
    public static SkyIslandEcologicalCandidateLatticeProfile current(
            long rootSeed,
            String namespace,
            double cellPitch,
            double maxAxisJitter) {
        return new SkyIslandEcologicalCandidateLatticeProfile(
                VERSION,
                SeedDerivation.VERSION,
                rootSeed,
                namespace,
                cellPitch,
                maxAxisJitter);
    }

    /**
     * Guaranteed lower bound on center-to-center distance between candidates in distinct cells.
     */
    public double guaranteedMinimumSpacing() {
        return cellPitch - 2.0 * maxAxisJitter;
    }

    /** Generates the exact deterministic candidate assigned to one integer lattice cell. */
    public SkyIslandEcologicalPlacementCandidate candidate(long cellX, long cellZ) {
        return new SkyIslandEcologicalPlacementCandidate(
                this,
                cellX,
                cellZ,
                candidatePosition(cellX, cellZ),
                candidateAdmissionValue(cellX, cellZ));
    }

    /**
     * Enumerates every canonical candidate whose exact position lies inside one half-open local
     * window.
     *
     * <p>The lattice inversion is deliberately conservative: candidate cells that cannot be ruled
     * out from pitch/phase/jitter geometry are generated through the canonical #1223 path, then
     * exact candidate positions are filtered by the query window.
     */
    public SkyIslandEcologicalCandidateWindowResult query(
            SkyIslandEcologicalCandidateQueryWindow window) {
        Objects.requireNonNull(window, "window");

        CellRange xRange = candidateCellRange(
                window.minimumX(),
                window.maximumX(),
                phaseX(),
                "x");
        CellRange zRange = candidateCellRange(
                window.minimumZ(),
                window.maximumZ(),
                phaseZ(),
                "z");

        long searchCells;
        try {
            searchCells = Math.multiplyExact(xRange.count(), zRange.count());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "candidate query search range is too large to materialize",
                    exception);
        }
        if (searchCells > MAX_QUERY_SEARCH_CELLS) {
            throw new IllegalArgumentException(
                    "candidate query exceeds technical search-cell materialization bound of "
                            + MAX_QUERY_SEARCH_CELLS);
        }

        ArrayList<SkyIslandEcologicalPlacementCandidate> candidates =
                new ArrayList<>((int) searchCells);
        for (long cellX = xRange.minimum(); ; cellX++) {
            for (long cellZ = zRange.minimum(); ; cellZ++) {
                SkyIslandEcologicalPlacementCandidate candidate = candidate(cellX, cellZ);
                if (window.contains(candidate.position())) {
                    candidates.add(candidate);
                }
                if (cellZ == zRange.maximum()) {
                    break;
                }
            }
            if (cellX == xRange.maximum()) {
                break;
            }
        }

        return new SkyIslandEcologicalCandidateWindowResult(
                this,
                window,
                candidates);
    }

    SkyIslandLocalPosition candidatePosition(long cellX, long cellZ) {
        double jitterX = signedUnit(cellSample(cellX, cellZ, "jitter-x")) * maxAxisJitter;
        double jitterZ = signedUnit(cellSample(cellX, cellZ, "jitter-z")) * maxAxisJitter;
        double x = (((double) cellX + 0.5) * cellPitch) + phaseX() + jitterX;
        double z = (((double) cellZ + 0.5) * cellPitch) + phaseZ() + jitterZ;
        return new SkyIslandLocalPosition(x, z);
    }

    double candidateAdmissionValue(long cellX, long cellZ) {
        return unitInterval(cellSample(cellX, cellZ, "admission"));
    }

    private double phaseX() {
        return unitInterval(SeedDerivation.derive(rootSeed, namespace + ".phase-x"))
                * cellPitch;
    }

    private double phaseZ() {
        return unitInterval(SeedDerivation.derive(rootSeed, namespace + ".phase-z"))
                * cellPitch;
    }

    private CellRange candidateCellRange(
            double minimum,
            double maximum,
            double phase,
            String axis) {
        double lowerRaw = ((minimum - maxAxisJitter - phase) / cellPitch) - 0.5;
        double upperRaw = ((maximum + maxAxisJitter - phase) / cellPitch) - 0.5;
        if (!Double.isFinite(lowerRaw) || !Double.isFinite(upperRaw)) {
            throw new IllegalArgumentException(
                    "candidate query " + axis + " range is not representable as lattice cells");
        }

        long lower = ceilToLongExact(lowerRaw, axis);
        long upper = floorToLongExact(upperRaw, axis);
        if (lower == Long.MIN_VALUE || upper == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "candidate query " + axis + " range cannot be conservatively padded");
        }

        // One-cell padding absorbs floating-point inversion roundoff. Exact candidate positions
        // are filtered afterward, so padding cannot create false returned candidates.
        return new CellRange(lower - 1L, upper + 1L);
    }

    private static long ceilToLongExact(double value, String axis) {
        return roundedToLongExact(Math.ceil(value), axis);
    }

    private static long floorToLongExact(double value, String axis) {
        return roundedToLongExact(Math.floor(value), axis);
    }

    private static long roundedToLongExact(double value, String axis) {
        if (!Double.isFinite(value)
                || value < LONG_MIN_AS_DOUBLE
                || value >= LONG_MAX_EXCLUSIVE_AS_DOUBLE) {
            throw new IllegalArgumentException(
                    "candidate query " + axis + " lattice range exceeds signed 64-bit cells");
        }
        return (long) value;
    }

    private long cellSample(long cellX, long cellZ, String channel) {
        long channelSeed = SeedDerivation.derive(rootSeed, namespace + "." + channel);
        long mixedX = SeedDerivation.mix64(cellX ^ X_DOMAIN);
        long mixedZ = SeedDerivation.mix64(cellZ ^ Z_DOMAIN);
        return SeedDerivation.mix64(channelSeed ^ mixedX ^ Long.rotateLeft(mixedZ, 29));
    }

    private static double signedUnit(long bits) {
        return 2.0 * unitInterval(bits) - 1.0;
    }

    private static double unitInterval(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private record CellRange(long minimum, long maximum) {
        private CellRange {
            if (maximum < minimum) {
                throw new IllegalArgumentException("candidate cell range must not be inverted");
            }
        }

        long count() {
            try {
                return Math.addExact(Math.subtractExact(maximum, minimum), 1L);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "candidate cell range size exceeds signed 64-bit arithmetic",
                        exception);
            }
        }
    }
}
