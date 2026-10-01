package io.github.nidaba.skyforge.model.aircraft;

import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral finite-domain input to the production aircraft analytical compiler.
 *
 * <p>This contract deliberately contains no block coordinates, registry identifiers, target resource
 * names, mutable runtime state, or target force-model constants.
 */
public record AircraftDesignSpec(
        int schemaVersion,
        String assetId,
        Configuration configuration,
        Mission mission,
        AnalyticalAssumptions declaredAssumptions,
        ReferenceTargets referenceTargets,
        Constraints constraints,
        DesignDomains designDomains,
        GeometryEnvelope geometryEnvelope,
        List<MassItemSpec> massLedger) {
    public static final int SCHEMA_VERSION = 1;

    public AircraftDesignSpec {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aircraft design spec schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        configuration = Objects.requireNonNull(configuration, "configuration");
        mission = Objects.requireNonNull(mission, "mission");
        declaredAssumptions = Objects.requireNonNull(declaredAssumptions, "declaredAssumptions");
        referenceTargets = Objects.requireNonNull(referenceTargets, "referenceTargets");
        constraints = Objects.requireNonNull(constraints, "constraints");
        designDomains = Objects.requireNonNull(designDomains, "designDomains");
        geometryEnvelope = Objects.requireNonNull(geometryEnvelope, "geometryEnvelope");
        massLedger = List.copyOf(Objects.requireNonNull(massLedger, "massLedger"));
        if (massLedger.isEmpty()) {
            throw new IllegalArgumentException("massLedger must not be empty");
        }
    }

    public record Configuration(
            String aircraftClass,
            String wingPosition,
            int engineCount,
            String propulsionLayout,
            String tail,
            int crew,
            String cargoRole) {
        public Configuration {
            aircraftClass = requireText("aircraftClass", aircraftClass);
            wingPosition = requireText("wingPosition", wingPosition);
            propulsionLayout = requireText("propulsionLayout", propulsionLayout);
            tail = requireText("tail", tail);
            cargoRole = requireText("cargoRole", cargoRole);
            if (engineCount < 1) {
                throw new IllegalArgumentException("engineCount must be positive");
            }
            if (crew < 1) {
                throw new IllegalArgumentException("crew must be positive");
            }
        }
    }

    public record Mission(
            double grossMassKg,
            double airDensityKgM3,
            double gravityMS2,
            double cruiseSpeedMS,
            double designLiftCoefficient) {
        public Mission {
            requirePositive("grossMassKg", grossMassKg);
            requirePositive("airDensityKgM3", airDensityKgM3);
            requirePositive("gravityMS2", gravityMS2);
            requirePositive("cruiseSpeedMS", cruiseSpeedMS);
            requirePositive("designLiftCoefficient", designLiftCoefficient);
        }
    }

    public record AnalyticalAssumptions(
            String atmosphere,
            String wingPlanform,
            String tailPlanforms,
            double spanEfficiency,
            String spanEfficiencyStatus,
            String massModel,
            String stabilityClaim) {
        public AnalyticalAssumptions {
            atmosphere = requireText("atmosphere", atmosphere);
            wingPlanform = requireText("wingPlanform", wingPlanform);
            tailPlanforms = requireText("tailPlanforms", tailPlanforms);
            spanEfficiencyStatus = requireText("spanEfficiencyStatus", spanEfficiencyStatus);
            massModel = requireText("massModel", massModel);
            stabilityClaim = requireText("stabilityClaim", stabilityClaim);
            if (!Double.isFinite(spanEfficiency) || spanEfficiency <= 0.0 || spanEfficiency > 1.0) {
                throw new IllegalArgumentException("spanEfficiency must be finite and in (0, 1]");
            }
        }
    }

    public record ReferenceTargets(
            double horizontalTailVolume,
            double verticalTailVolume,
            String status) {
        public ReferenceTargets {
            requirePositive("horizontalTailVolume", horizontalTailVolume);
            requirePositive("verticalTailVolume", verticalTailVolume);
            status = requireText("reference target status", status);
        }
    }

    public record DoubleRange(double minimum, double maximum) {
        public DoubleRange {
            requireFinite("minimum", minimum);
            requireFinite("maximum", maximum);
            if (minimum > maximum) {
                throw new IllegalArgumentException("range minimum must not exceed maximum");
            }
        }

        public boolean contains(double value) {
            return value >= minimum && value <= maximum;
        }
    }

    public record Constraints(
            DoubleRange aspectRatioRange,
            double maxCruiseLiftResidualFraction,
            DoubleRange specimenCgMacFractionRange,
            double maxHorizontalTailVolumeReferenceErrorFraction,
            double maxVerticalTailVolumeReferenceErrorFraction) {
        public Constraints {
            aspectRatioRange = Objects.requireNonNull(aspectRatioRange, "aspectRatioRange");
            specimenCgMacFractionRange = Objects.requireNonNull(
                    specimenCgMacFractionRange, "specimenCgMacFractionRange");
            requireNonNegative("maxCruiseLiftResidualFraction", maxCruiseLiftResidualFraction);
            requireNonNegative(
                    "maxHorizontalTailVolumeReferenceErrorFraction",
                    maxHorizontalTailVolumeReferenceErrorFraction);
            requireNonNegative(
                    "maxVerticalTailVolumeReferenceErrorFraction",
                    maxVerticalTailVolumeReferenceErrorFraction);
        }
    }

    public record DesignDomains(
            List<Double> fuselageLengthM,
            List<Double> wingSpanM,
            List<Double> wingRootChordM,
            List<Double> wingTipChordM,
            List<Double> wingLeadingEdgeXM,
            List<Double> horizontalTailSpanM,
            List<Double> horizontalTailRootChordM,
            List<Double> horizontalTailTipChordM,
            double horizontalTailLeadingEdgeInsetM,
            List<Double> verticalTailHeightM,
            List<Double> verticalTailRootChordM,
            List<Double> verticalTailTipChordM,
            double verticalTailLeadingEdgeInsetM) {
        public DesignDomains {
            fuselageLengthM = copyDomain("fuselageLengthM", fuselageLengthM);
            wingSpanM = copyDomain("wingSpanM", wingSpanM);
            wingRootChordM = copyDomain("wingRootChordM", wingRootChordM);
            wingTipChordM = copyDomain("wingTipChordM", wingTipChordM);
            wingLeadingEdgeXM = copyDomain("wingLeadingEdgeXM", wingLeadingEdgeXM);
            horizontalTailSpanM = copyDomain("horizontalTailSpanM", horizontalTailSpanM);
            horizontalTailRootChordM = copyDomain("horizontalTailRootChordM", horizontalTailRootChordM);
            horizontalTailTipChordM = copyDomain("horizontalTailTipChordM", horizontalTailTipChordM);
            verticalTailHeightM = copyDomain("verticalTailHeightM", verticalTailHeightM);
            verticalTailRootChordM = copyDomain("verticalTailRootChordM", verticalTailRootChordM);
            verticalTailTipChordM = copyDomain("verticalTailTipChordM", verticalTailTipChordM);
            requireFinite("horizontalTailLeadingEdgeInsetM", horizontalTailLeadingEdgeInsetM);
            requireFinite("verticalTailLeadingEdgeInsetM", verticalTailLeadingEdgeInsetM);
        }
    }

    public record GeometryEnvelope(
            double fuselageMaxWidthM,
            double fuselageMaxHeightM,
            PropellerEnvelope propellerEnvelope) {
        public GeometryEnvelope {
            requirePositive("fuselageMaxWidthM", fuselageMaxWidthM);
            requirePositive("fuselageMaxHeightM", fuselageMaxHeightM);
            propellerEnvelope = Objects.requireNonNull(propellerEnvelope, "propellerEnvelope");
        }
    }

    public record PropellerEnvelope(double diameterM, double centerXM, double centerYM) {
        public PropellerEnvelope {
            requirePositive("propeller diameterM", diameterM);
            requireFinite("propeller centerXM", centerXM);
            requireFinite("propeller centerYM", centerYM);
        }
    }

    /** A mass item uses exactly one absolute or fuselage-relative station declaration. */
    public record MassItemSpec(
            String name,
            double massKg,
            Double stationXM,
            Double stationFractionFuselage) {
        public MassItemSpec {
            name = requireText("mass item name", name);
            requirePositive("massKg", massKg);
            if ((stationXM == null) == (stationFractionFuselage == null)) {
                throw new IllegalArgumentException(
                        "mass item must declare exactly one of stationXM or stationFractionFuselage");
            }
            if (stationXM != null) {
                requireFinite("stationXM", stationXM);
            }
            if (stationFractionFuselage != null) {
                requireFinite("stationFractionFuselage", stationFractionFuselage);
            }
        }

        public static MassItemSpec absolute(String name, double massKg, double stationXM) {
            return new MassItemSpec(name, massKg, stationXM, null);
        }

        public static MassItemSpec fuselageFraction(String name, double massKg, double fraction) {
            return new MassItemSpec(name, massKg, null, fraction);
        }
    }

    private static List<Double> copyDomain(String name, List<Double> values) {
        List<Double> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("empty design domain: " + name);
        }
        for (double value : copy) {
            requireFinite(name, value);
        }
        return copy;
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and greater than zero");
        }
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
