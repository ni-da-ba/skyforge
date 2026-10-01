package io.github.nidaba.skyforge.model.aircraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Resolved backend-neutral analytical aircraft design.
 *
 * <p>The IR records design authority and solver evidence. It is intentionally upstream of blockspace,
 * target lowering, assembly/runtime integration, observed target behavior, and handling qualification.
 */
public record AircraftDesignIR(
        int schemaVersion,
        String assetId,
        String compilerVersion,
        AircraftDesignSpec.Configuration configuration,
        AircraftDesignSpec.Mission mission,
        AircraftDesignSpec.AnalyticalAssumptions declaredAssumptions,
        AircraftDesignSpec.ReferenceTargets referenceTargets,
        Geometry geometry,
        List<MassItem> massLedger,
        Metrics metrics,
        SolverEvidence solver,
        Validation validation,
        TargetBoundary targetBoundary) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "aircraft-design-compiler-1";

    public AircraftDesignIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aircraft design IR schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        compilerVersion = requireText("compilerVersion", compilerVersion);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported aircraft compiler version: " + compilerVersion);
        }
        configuration = Objects.requireNonNull(configuration, "configuration");
        mission = Objects.requireNonNull(mission, "mission");
        declaredAssumptions = Objects.requireNonNull(declaredAssumptions, "declaredAssumptions");
        referenceTargets = Objects.requireNonNull(referenceTargets, "referenceTargets");
        geometry = Objects.requireNonNull(geometry, "geometry");
        massLedger = List.copyOf(Objects.requireNonNull(massLedger, "massLedger"));
        if (massLedger.isEmpty()) {
            throw new IllegalArgumentException("massLedger must not be empty");
        }
        metrics = Objects.requireNonNull(metrics, "metrics");
        solver = Objects.requireNonNull(solver, "solver");
        validation = Objects.requireNonNull(validation, "validation");
        targetBoundary = Objects.requireNonNull(targetBoundary, "targetBoundary");
    }

    public record Geometry(
            Fuselage fuselage,
            Wing wing,
            HorizontalTail horizontalTail,
            VerticalTail verticalTail,
            AircraftDesignSpec.PropellerEnvelope propellerEnvelope) {
        public Geometry {
            fuselage = Objects.requireNonNull(fuselage, "fuselage");
            wing = Objects.requireNonNull(wing, "wing");
            horizontalTail = Objects.requireNonNull(horizontalTail, "horizontalTail");
            verticalTail = Objects.requireNonNull(verticalTail, "verticalTail");
            propellerEnvelope = Objects.requireNonNull(propellerEnvelope, "propellerEnvelope");
        }
    }

    /** Stable SHA-256 identity over canonical production JSON. */
    public String sha256() {
        return AircraftDesignIRJson.sha256(this);
    }

    public record Fuselage(double lengthM, double maxWidthM, double maxHeightM) {
        public Fuselage {
            requirePositive("fuselage lengthM", lengthM);
            requirePositive("fuselage maxWidthM", maxWidthM);
            requirePositive("fuselage maxHeightM", maxHeightM);
        }
    }

    public record Wing(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM,
            String mount,
            String sweep) {
        public Wing {
            requirePositive("wing spanM", spanM);
            requirePositive("wing rootChordM", rootChordM);
            requirePositive("wing tipChordM", tipChordM);
            requireNonNegative("wing leadingEdgeXM", leadingEdgeXM);
            if (rootChordM < tipChordM) {
                throw new IllegalArgumentException("wing rootChordM must not be less than tipChordM");
            }
            mount = requireText("wing mount", mount);
            sweep = requireText("wing sweep", sweep);
        }
    }

    public record HorizontalTail(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {
        public HorizontalTail {
            requirePositive("horizontalTail spanM", spanM);
            requirePositive("horizontalTail rootChordM", rootChordM);
            requirePositive("horizontalTail tipChordM", tipChordM);
            requireNonNegative("horizontalTail leadingEdgeXM", leadingEdgeXM);
            if (rootChordM < tipChordM) {
                throw new IllegalArgumentException("horizontalTail rootChordM must not be less than tipChordM");
            }
        }
    }

    public record VerticalTail(
            double heightM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {
        public VerticalTail {
            requirePositive("verticalTail heightM", heightM);
            requirePositive("verticalTail rootChordM", rootChordM);
            requirePositive("verticalTail tipChordM", tipChordM);
            requireNonNegative("verticalTail leadingEdgeXM", leadingEdgeXM);
            if (rootChordM < tipChordM) {
                throw new IllegalArgumentException("verticalTail rootChordM must not be less than tipChordM");
            }
        }
    }

    public record MassItem(String name, double massKg, double stationXM, String source) {
        public MassItem {
            name = requireText("mass item name", name);
            requirePositive("mass item massKg", massKg);
            requireFinite("mass item stationXM", stationXM);
            source = requireText("mass item source", source);
        }
    }

    public record Metrics(
            double dynamicPressurePa,
            double weightN,
            double requiredWingAreaM2AtDesignCL,
            double wingAreaM2,
            double wingTaperRatio,
            double wingAspectRatio,
            double wingMacM,
            double cruiseLiftN,
            double cruiseLiftResidualFraction,
            double analyticalInducedDragCoefficient,
            double cgXM,
            double cgMacFraction,
            double horizontalTailAreaM2,
            double horizontalTailMacM,
            double horizontalTailArmM,
            double horizontalTailVolume,
            double horizontalTailReferenceErrorFraction,
            double verticalTailAreaM2,
            double verticalTailMacM,
            double verticalTailArmM,
            double verticalTailVolume,
            double verticalTailReferenceErrorFraction) {
        public Metrics {
            requirePositive("dynamicPressurePa", dynamicPressurePa);
            requirePositive("weightN", weightN);
            requirePositive("requiredWingAreaM2AtDesignCL", requiredWingAreaM2AtDesignCL);
            requirePositive("wingAreaM2", wingAreaM2);
            requirePositive("wingTaperRatio", wingTaperRatio);
            requirePositive("wingAspectRatio", wingAspectRatio);
            requirePositive("wingMacM", wingMacM);
            requirePositive("cruiseLiftN", cruiseLiftN);
            requireNonNegative("cruiseLiftResidualFraction", cruiseLiftResidualFraction);
            requireNonNegative("analyticalInducedDragCoefficient", analyticalInducedDragCoefficient);
            requireFinite("cgXM", cgXM);
            requireFinite("cgMacFraction", cgMacFraction);
            requirePositive("horizontalTailAreaM2", horizontalTailAreaM2);
            requirePositive("horizontalTailMacM", horizontalTailMacM);
            requirePositive("horizontalTailArmM", horizontalTailArmM);
            requirePositive("horizontalTailVolume", horizontalTailVolume);
            requireNonNegative("horizontalTailReferenceErrorFraction", horizontalTailReferenceErrorFraction);
            requirePositive("verticalTailAreaM2", verticalTailAreaM2);
            requirePositive("verticalTailMacM", verticalTailMacM);
            requirePositive("verticalTailArmM", verticalTailArmM);
            requirePositive("verticalTailVolume", verticalTailVolume);
            requireNonNegative("verticalTailReferenceErrorFraction", verticalTailReferenceErrorFraction);
        }
    }

    public record SolverEvidence(
            String method,
            int candidateCountFeasible,
            Map<String, Integer> rejectionCounts,
            List<String> objectiveOrder,
            List<Double> selectedObjective) {
        public SolverEvidence {
            method = requireText("solver method", method);
            if (candidateCountFeasible < 1) {
                throw new IllegalArgumentException("candidateCountFeasible must be positive");
            }
            TreeMap<String, Integer> sorted = new TreeMap<>(
                    Objects.requireNonNull(rejectionCounts, "rejectionCounts"));
            for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
                requireText("rejection reason", entry.getKey());
                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new IllegalArgumentException("rejection counts must be non-negative");
                }
            }
            rejectionCounts = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
            objectiveOrder = List.copyOf(Objects.requireNonNull(objectiveOrder, "objectiveOrder"));
            if (objectiveOrder.isEmpty()) {
                throw new IllegalArgumentException("objectiveOrder must not be empty");
            }
            objectiveOrder.forEach(value -> requireText("objective name", value));
            selectedObjective = List.copyOf(Objects.requireNonNull(selectedObjective, "selectedObjective"));
            if (selectedObjective.isEmpty()) {
                throw new IllegalArgumentException("selectedObjective must not be empty");
            }
            selectedObjective.forEach(value -> requireFinite("selected objective value", value));
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
            doesNotProve.forEach(value -> requireText("doesNotProve entry", value));
        }
    }

    public record TargetBoundary(
            boolean containsConcreteTargetResourceNames,
            boolean targetAdapterApplied) {}

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requirePositive(String property, double value) {
        requireFinite(property, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException(property + " must be greater than zero");
        }
    }

    private static void requireNonNegative(String property, double value) {
        requireFinite(property, value);
        if (value < 0.0) {
            throw new IllegalArgumentException(property + " must be non-negative");
        }
    }
}
