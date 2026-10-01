package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic backend-neutral observer for the sampled 3-D relationship between finite boxes and
 * independently compiled Skyforge island volumes.
 *
 * <p>The observer reports only sampled facts. In particular, air between the compiled upper and
 * underside surfaces is intentionally not classified as lateral exterior versus cave/open interior;
 * both are represented as open-between-surfaces because the density field is authoritative and no
 * structure semantics belong at this layer.
 */
public final class SkyIslandTerrainBoxObserver {
    private static final long MAXIMUM_SAMPLE_COUNT = 1_000_000L;

    /** Observes every conservatively relevant catalog volume in stable catalog order. */
    public List<TerrainBoxObservation> observe(
            SkyIslandWorldCatalog catalog,
            SkyIslandTerrainProfile terrainProfile,
            TerrainBoxObservationRequirements requirements) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(terrainProfile, "terrainProfile");
        Objects.requireNonNull(requirements, "requirements");
        ArrayList<TerrainBoxObservation> result = new ArrayList<>();
        for (SkyIslandWorldVolume volume : catalog.query(requirements.bounds())) {
            result.add(observe(volume, terrainProfile, requirements));
        }
        return List.copyOf(result);
    }

    /** Observes one island volume without consulting or combining any other volume. */
    public TerrainBoxObservation observe(
            SkyIslandWorldVolume volume,
            SkyIslandTerrainProfile terrainProfile,
            TerrainBoxObservationRequirements requirements) {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrainProfile, "terrainProfile");
        Objects.requireNonNull(requirements, "requirements");

        WorldBounds bounds = requirements.bounds();
        double[] xSamples = sampleAxis(bounds.minimumX(), bounds.maximumX(), requirements.sampleSpacing());
        double[] ySamples = sampleAxis(bounds.minimumY(), bounds.maximumY(), requirements.sampleSpacing());
        double[] zSamples = sampleAxis(bounds.minimumZ(), bounds.maximumZ(), requirements.sampleSpacing());
        long requestedSamples = Math.multiplyExact(
                Math.multiplyExact((long) xSamples.length, ySamples.length),
                zSamples.length);
        if (requestedSamples > MAXIMUM_SAMPLE_COUNT) {
            throw new IllegalArgumentException("terrain box observation exceeds maximum sample count");
        }

        SkyIslandTerrainInterpreter interpreter =
                new SkyIslandTerrainInterpreter(volume.compiledVolume(), terrainProfile);
        int solid = 0;
        int atOrAboveUpper = 0;
        int atOrBelowUnderside = 0;
        int openBetween = 0;
        for (double z : zSamples) {
            for (double y : ySamples) {
                for (double x : xSamples) {
                    double upper = interpreter.upperSurfaceHeight(x, z);
                    double underside = interpreter.undersideSurfaceHeight(x, z);
                    double density = interpreter.density(x, y, z);
                    if (density > 0.0) {
                        if (!(y < upper) || !(y > underside)) {
                            throw new IllegalStateException(
                                    "compiled density is positive outside the compiled upper/underside surfaces");
                        }
                        solid++;
                    } else if (y >= upper) {
                        atOrAboveUpper++;
                    } else if (y <= underside) {
                        atOrBelowUnderside++;
                    } else {
                        openBetween++;
                    }
                }
            }
        }

        return new TerrainBoxObservation(
                volume.id(),
                Math.toIntExact(requestedSamples),
                solid,
                atOrAboveUpper,
                atOrBelowUnderside,
                openBetween);
    }

    private static double[] sampleAxis(double minimum, double maximum, double spacing) {
        if (Double.doubleToLongBits(minimum) == Double.doubleToLongBits(maximum)) {
            return new double[] {minimum};
        }
        double intervalCount = Math.ceil((maximum - minimum) / spacing);
        if (!Double.isFinite(intervalCount) || intervalCount > Integer.MAX_VALUE - 1.0) {
            throw new IllegalArgumentException("terrain observation sampling axis is too large");
        }
        int intervals = (int) intervalCount;
        double[] result = new double[intervals + 1];
        for (int index = 0; index <= intervals; index++) {
            result[index] = index == intervals ? maximum : minimum + spacing * index;
        }
        return result;
    }
}
