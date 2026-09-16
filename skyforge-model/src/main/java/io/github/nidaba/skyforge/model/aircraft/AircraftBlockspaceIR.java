package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Target-neutral discrete aircraft geometry and semantic capability requirements. */
public record AircraftBlockspaceIR(
        int schemaVersion,
        String assetId,
        String sourceDesignAssetId,
        String sourceDesignDigestSha256,
        String compilerVersion,
        CoordinateSystem coordinateSystem,
        TranscriptionAssumptions declaredTranscriptionAssumptions,
        List<Cell> cells,
        Map<AnchorType, Anchor> anchors,
        Metrics metrics,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "aircraft-blockspace-compiler-1";

    public AircraftBlockspaceIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aircraft blockspace IR schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        sourceDesignAssetId = requireText("sourceDesignAssetId", sourceDesignAssetId);
        sourceDesignDigestSha256 = requireSha256(sourceDesignDigestSha256);
        compilerVersion = requireText("compilerVersion", compilerVersion);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported aircraft blockspace compiler version: " + compilerVersion);
        }
        coordinateSystem = Objects.requireNonNull(coordinateSystem, "coordinateSystem");
        declaredTranscriptionAssumptions = Objects.requireNonNull(
                declaredTranscriptionAssumptions, "declaredTranscriptionAssumptions");
        ArrayList<Cell> sortedCells = new ArrayList<>(Objects.requireNonNull(cells, "cells"));
        if (sortedCells.isEmpty()) {
            throw new IllegalArgumentException("cells must not be empty");
        }
        sortedCells.sort(Cell.ORDER);
        cells = List.copyOf(sortedCells);
        EnumMap<AnchorType, Anchor> copiedAnchors = new EnumMap<>(AnchorType.class);
        copiedAnchors.putAll(Objects.requireNonNull(anchors, "anchors"));
        for (AnchorType required : AnchorType.values()) {
            if (!copiedAnchors.containsKey(required)) {
                throw new IllegalArgumentException("missing aircraft blockspace anchor: " + required.id());
            }
        }
        anchors = Collections.unmodifiableMap(new LinkedHashMap<>(copiedAnchors));
        metrics = Objects.requireNonNull(metrics, "metrics");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        return AircraftBlockspaceIRJson.sha256(this);
    }

    public record CoordinateSystem(
            String x,
            String y,
            String z,
            String cellCenters,
            double blocksPerMeter) {
        public CoordinateSystem {
            x = requireText("coordinateSystem.x", x);
            y = requireText("coordinateSystem.y", y);
            z = requireText("coordinateSystem.z", z);
            cellCenters = requireText("coordinateSystem.cellCenters", cellCenters);
            requirePositive("coordinateSystem.blocksPerMeter", blocksPerMeter);
        }
    }

    public record TranscriptionAssumptions(
            String surfaceRasterization,
            String fuselageRepresentation,
            String connectors,
            AircraftBlockspaceSpec.Mounts mounts,
            boolean containsConcreteTargetResourceIdentity) {
        public TranscriptionAssumptions {
            surfaceRasterization = requireText("surfaceRasterization", surfaceRasterization);
            fuselageRepresentation = requireText("fuselageRepresentation", fuselageRepresentation);
            connectors = requireText("connectors", connectors);
            mounts = Objects.requireNonNull(mounts, "mounts");
            if (containsConcreteTargetResourceIdentity) {
                throw new IllegalArgumentException("blockspace transcription must remain target-resource-neutral");
            }
        }
    }

    public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {
        @Override
        public int compareTo(LatticePoint other) {
            int xCompare = Integer.compare(x, other.x);
            if (xCompare != 0) {
                return xCompare;
            }
            int yCompare = Integer.compare(y, other.y);
            return yCompare != 0 ? yCompare : Integer.compare(z, other.z);
        }
    }

    public record Cell(int x, int y, int z, Role role) {
        static final Comparator<Cell> ORDER = Comparator
                .comparingInt(Cell::x)
                .thenComparingInt(Cell::y)
                .thenComparingInt(Cell::z)
                .thenComparing(cell -> cell.role().id());

        public Cell {
            role = Objects.requireNonNull(role, "role");
        }

        public LatticePoint point() {
            return new LatticePoint(x, y, z);
        }
    }

    public enum Role {
        FUSELAGE_SPINE("fuselage_spine", List.of("rigid_physics_member")),
        WING_SURFACE_INTENT("wing_surface_intent", List.of("aerodynamic_lift_surface", "rigid_physics_member")),
        HORIZONTAL_TAIL_SURFACE_INTENT(
                "horizontal_tail_surface_intent", List.of("aerodynamic_lift_surface", "rigid_physics_member")),
        VERTICAL_TAIL_SURFACE_INTENT(
                "vertical_tail_surface_intent", List.of("aerodynamic_lift_surface", "rigid_physics_member")),
        WING_ATTACH_INTENT("wing_attach_intent", List.of("rigid_load_path")),
        TAIL_ATTACH_INTENT("tail_attach_intent", List.of("rigid_load_path"));

        private final String id;
        private final List<String> capabilities;

        Role(String id, List<String> capabilities) {
            this.id = id;
            this.capabilities = List.copyOf(capabilities);
        }

        public String id() {
            return id;
        }

        public List<String> capabilities() {
            return capabilities;
        }

        public static Role fromId(String id) {
            for (Role role : values()) {
                if (role.id.equals(id)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("unknown aircraft blockspace role: " + id);
        }
    }

    public enum AnchorType {
        PROPELLER_AXIS("propeller_axis", List.of("rotational_thrust_producer", "shaft_power_input", "clearance_disk")),
        CG_REFERENCE("cg_reference", List.of()),
        PILOT_STATION("pilot_station", List.of("vehicle_control_station")),
        CARGO_STATION("cargo_station", List.of("payload_volume_or_interface"));

        private final String id;
        private final List<String> capabilities;

        AnchorType(String id, List<String> capabilities) {
            this.id = id;
            this.capabilities = List.copyOf(capabilities);
        }

        public String id() {
            return id;
        }

        public List<String> capabilities() {
            return capabilities;
        }
    }

    public record ContinuousPoint(double xM, double yM, double zM) {
        public ContinuousPoint {
            requireFinite("continuous xM", xM);
            requireFinite("continuous yM", yM);
            requireFinite("continuous zM", zM);
        }
    }

    public record Anchor(ContinuousPoint continuousM, LatticePoint lattice) {
        public Anchor {
            continuousM = Objects.requireNonNull(continuousM, "continuousM");
            lattice = Objects.requireNonNull(lattice, "lattice");
        }
    }

    public record DimensionErrors(
            double wingSpanBlocks,
            double horizontalTailSpanBlocks,
            double verticalTailHeightBlocks) {
        public DimensionErrors {
            requireNonNegative("wingSpanBlocks", wingSpanBlocks);
            requireNonNegative("horizontalTailSpanBlocks", horizontalTailSpanBlocks);
            requireNonNegative("verticalTailHeightBlocks", verticalTailHeightBlocks);
        }
    }

    public record Metrics(
            int cellCount,
            int connectedComponents6Neighbor,
            boolean mirrorSymmetrySatisfied,
            boolean propellerDiskClear,
            int propellerDiskViolationCount,
            double cgStationQuantizationErrorBlocks,
            double continuousWingSpanM,
            double realizedWingSpanM,
            double continuousHorizontalTailSpanM,
            double realizedHorizontalTailSpanM,
            double continuousVerticalTailHeightM,
            double realizedVerticalTailHeightM,
            DimensionErrors dimensionErrorBlocks) {
        public Metrics {
            if (cellCount < 1 || connectedComponents6Neighbor < 1 || propellerDiskViolationCount < 0) {
                throw new IllegalArgumentException("blockspace counts must be positive/non-negative");
            }
            requireNonNegative("cgStationQuantizationErrorBlocks", cgStationQuantizationErrorBlocks);
            requirePositive("continuousWingSpanM", continuousWingSpanM);
            requirePositive("realizedWingSpanM", realizedWingSpanM);
            requirePositive("continuousHorizontalTailSpanM", continuousHorizontalTailSpanM);
            requirePositive("realizedHorizontalTailSpanM", realizedHorizontalTailSpanM);
            requirePositive("continuousVerticalTailHeightM", continuousVerticalTailHeightM);
            requirePositive("realizedVerticalTailHeightM", realizedVerticalTailHeightM);
            dimensionErrorBlocks = Objects.requireNonNull(dimensionErrorBlocks, "dimensionErrorBlocks");
        }
    }

    public record PropellerDiskViolation(int x, int y, int z, Role role, double radiusM) {
        public PropellerDiskViolation {
            role = Objects.requireNonNull(role, "role");
            requireNonNegative("radiusM", radiusM);
        }
    }

    public record Validation(
            boolean passed,
            String scope,
            List<String> doesNotProve,
            List<PropellerDiskViolation> propellerDiskViolations) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
            propellerDiskViolations = List.copyOf(
                    Objects.requireNonNull(propellerDiskViolations, "propellerDiskViolations"));
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireSha256(String value) {
        String normalized = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return normalized;
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
