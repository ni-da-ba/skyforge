package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayList;
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
        assetId = Objects.requireNonNull(assetId, "assetId");
        compilerVersion = Objects.requireNonNull(compilerVersion, "compilerVersion");
        configuration = Objects.requireNonNull(configuration, "configuration");
        mission = Objects.requireNonNull(mission, "mission");
        declaredAssumptions = Objects.requireNonNull(declaredAssumptions, "declaredAssumptions");
        referenceTargets = Objects.requireNonNull(referenceTargets, "referenceTargets");
        geometry = Objects.requireNonNull(geometry, "geometry");
        massLedger = List.copyOf(Objects.requireNonNull(massLedger, "massLedger"));
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

    public record Fuselage(double lengthM, double maxWidthM, double maxHeightM) {}

    public record Wing(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM,
            String mount,
            String sweep) {
        public Wing {
            mount = Objects.requireNonNull(mount, "mount");
            sweep = Objects.requireNonNull(sweep, "sweep");
        }
    }

    public record HorizontalTail(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {}

    public record VerticalTail(
            double heightM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {}

    public record MassItem(String name, double massKg, double stationXM, String source) {
        public MassItem {
            name = Objects.requireNonNull(name, "name");
            source = Objects.requireNonNull(source, "source");
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
            double verticalTailReferenceErrorFraction) {}

    public record SolverEvidence(
            String method,
            int candidateCountFeasible,
            Map<String, Integer> rejectionCounts,
            List<String> objectiveOrder,
            List<Double> selectedObjective) {
        public SolverEvidence {
            method = Objects.requireNonNull(method, "method");
            if (candidateCountFeasible < 1) {
                throw new IllegalArgumentException("candidateCountFeasible must be positive");
            }
            TreeMap<String, Integer> sorted = new TreeMap<>(
                    Objects.requireNonNull(rejectionCounts, "rejectionCounts"));
            rejectionCounts = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
            objectiveOrder = List.copyOf(Objects.requireNonNull(objectiveOrder, "objectiveOrder"));
            selectedObjective = List.copyOf(Objects.requireNonNull(selectedObjective, "selectedObjective"));
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = Objects.requireNonNull(scope, "scope");
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
        }
    }

    public record TargetBoundary(
            boolean containsConcreteTargetResourceNames,
            boolean targetAdapterApplied) {}
}
