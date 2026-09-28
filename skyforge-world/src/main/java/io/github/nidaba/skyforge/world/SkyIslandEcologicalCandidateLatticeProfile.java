package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;

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

    SkyIslandLocalPosition candidatePosition(long cellX, long cellZ) {
        double phaseX = unitInterval(SeedDerivation.derive(rootSeed, namespace + ".phase-x"))
                * cellPitch;
        double phaseZ = unitInterval(SeedDerivation.derive(rootSeed, namespace + ".phase-z"))
                * cellPitch;
        double jitterX = signedUnit(cellSample(cellX, cellZ, "jitter-x")) * maxAxisJitter;
        double jitterZ = signedUnit(cellSample(cellX, cellZ, "jitter-z")) * maxAxisJitter;
        double x = (((double) cellX + 0.5) * cellPitch) + phaseX + jitterX;
        double z = (((double) cellZ + 0.5) * cellPitch) + phaseZ + jitterZ;
        return new SkyIslandLocalPosition(x, z);
    }

    double candidateAdmissionValue(long cellX, long cellZ) {
        return unitInterval(cellSample(cellX, cellZ, "admission"));
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
}
