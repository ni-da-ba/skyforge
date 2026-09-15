package io.github.nidaba.skyforge.model.aircraft;

import java.util.List;

/**
 * First-principles analytical relations used by the backend-neutral aircraft design compiler.
 *
 * <p>These equations are analytical design authority only. They do not model or fit Create,
 * Sable, Aeronautics, or any other runtime backend.
 */
public final class AircraftAnalyticalMath {
    private AircraftAnalyticalMath() {}

    /** q = 1/2 rho V^2. */
    public static double dynamicPressure(double densityKgM3, double speedMS) {
        return 0.5 * densityKgM3 * speedMS * speedMS;
    }

    /** L = q S C_L. */
    public static double liftForce(double densityKgM3, double speedMS, double areaM2, double liftCoefficient) {
        return dynamicPressure(densityKgM3, speedMS) * areaM2 * liftCoefficient;
    }

    public static double weightForce(double massKg, double gravityMS2) {
        return massKg * gravityMS2;
    }

    /** Area of a straight, linearly tapered symmetric planform. */
    public static double trapezoidArea(double spanM, double rootChordM, double tipChordM) {
        return 0.5 * spanM * (rootChordM + tipChordM);
    }

    /** Area of one trapezoidal surface, such as a single vertical fin. */
    public static double singleTrapezoidArea(double heightOrSpanM, double rootChordM, double tipChordM) {
        return 0.5 * heightOrSpanM * (rootChordM + tipChordM);
    }

    public static double taperRatio(double rootChordM, double tipChordM) {
        if (rootChordM <= 0.0) {
            throw new IllegalArgumentException("root chord must be positive");
        }
        return tipChordM / rootChordM;
    }

    /** Mean aerodynamic chord for a straight, linearly tapered planform. */
    public static double meanAerodynamicChord(double rootChordM, double tipChordM) {
        double lambda = taperRatio(rootChordM, tipChordM);
        return (2.0 / 3.0)
                * rootChordM
                * (1.0 + lambda + lambda * lambda)
                / (1.0 + lambda);
    }

    public static double aspectRatio(double spanM, double areaM2) {
        if (areaM2 <= 0.0) {
            throw new IllegalArgumentException("area must be positive");
        }
        return spanM * spanM / areaM2;
    }

    /** C_Di = C_L^2/(pi e AR); retained only as an analytical comparison proxy. */
    public static double inducedDragCoefficient(
            double liftCoefficient, double aspectRatio, double spanEfficiency) {
        if (aspectRatio <= 0.0 || spanEfficiency <= 0.0 || spanEfficiency > 1.0) {
            throw new IllegalArgumentException(
                    "aspect ratio and span efficiency must be positive; span efficiency must be <= 1");
        }
        return liftCoefficient * liftCoefficient / (Math.PI * spanEfficiency * aspectRatio);
    }

    /** One-dimensional center of mass from mass/station pairs. */
    public static double massCenter(List<MassPoint> points) {
        double totalMass = 0.0;
        double moment = 0.0;
        for (MassPoint point : List.copyOf(points)) {
            totalMass += point.massKg();
            moment += point.massKg() * point.stationXM();
        }
        if (totalMass <= 0.0) {
            throw new IllegalArgumentException("total mass must be positive");
        }
        return moment / totalMass;
    }

    public static double horizontalTailVolume(
            double tailAreaM2, double tailArmM, double wingAreaM2, double wingMacM) {
        return tailAreaM2 * tailArmM / (wingAreaM2 * wingMacM);
    }

    public static double verticalTailVolume(
            double tailAreaM2, double tailArmM, double wingAreaM2, double wingSpanM) {
        return tailAreaM2 * tailArmM / (wingAreaM2 * wingSpanM);
    }

    /** Mass and longitudinal station used by the one-dimensional balance relation. */
    public record MassPoint(double massKg, double stationXM) {
        public MassPoint {
            requireFinite("massKg", massKg);
            requireFinite("stationXM", stationXM);
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
