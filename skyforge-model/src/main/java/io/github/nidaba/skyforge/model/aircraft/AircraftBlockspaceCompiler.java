package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic target-neutral transcription from analytical design IR to integer blockspace. */
public final class AircraftBlockspaceCompiler {
    private static final double TOLERANCE = 1.0e-12;

    public AircraftBlockspaceIR compile(AircraftDesignIR design, AircraftBlockspaceSpec spec) {
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(spec, "spec");
        if (!spec.sourceDesignAssetId().equals(design.assetId())) {
            throw new IllegalArgumentException("blockspace sourceDesignAssetId does not match design IR");
        }
        if (!spec.sourceDesignDigestSha256().equals(design.sha256())) {
            throw new IllegalArgumentException("blockspace sourceDesignDigestSha256 does not match design IR");
        }

        double scale = spec.blocksPerMeter();
        AircraftDesignIR.Geometry geometry = design.geometry();
        AircraftBlockspaceSpec.Mounts mounts = spec.mounts();
        TreeSet<AircraftBlockspaceIR.Cell> cells = new TreeSet<>(AircraftBlockspaceIR.Cell.ORDER);

        cells.addAll(horizontalSurfaceCells(
                geometry.wing().spanM(),
                geometry.wing().rootChordM(),
                geometry.wing().tipChordM(),
                geometry.wing().leadingEdgeXM(),
                mounts.wingYM(),
                scale,
                AircraftBlockspaceIR.Role.WING_SURFACE_INTENT));
        cells.addAll(horizontalSurfaceCells(
                geometry.horizontalTail().spanM(),
                geometry.horizontalTail().rootChordM(),
                geometry.horizontalTail().tipChordM(),
                geometry.horizontalTail().leadingEdgeXM(),
                mounts.horizontalTailYM(),
                scale,
                AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        cells.addAll(verticalSurfaceCells(
                geometry.verticalTail().heightM(),
                geometry.verticalTail().rootChordM(),
                geometry.verticalTail().tipChordM(),
                geometry.verticalTail().leadingEdgeXM(),
                mounts.verticalTailRootYM(),
                scale,
                AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));

        int centerY = quantizeNonNegative(mounts.fuselageCenterYM(), scale);
        int lengthIndex = quantizeNonNegative(geometry.fuselage().lengthM(), scale);
        for (int x = 0; x <= lengthIndex; x++) {
            cells.add(new AircraftBlockspaceIR.Cell(
                    x, centerY, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        }

        int wingAttachX = quantizeNonNegative(
                geometry.wing().leadingEdgeXM() + 0.25 * geometry.wing().rootChordM(), scale);
        int wingY = quantizeNonNegative(mounts.wingYM(), scale);
        cells.addAll(axisAlignedLine(
                new AircraftBlockspaceIR.LatticePoint(wingAttachX, centerY, 0),
                new AircraftBlockspaceIR.LatticePoint(wingAttachX, wingY, 0),
                AircraftBlockspaceIR.Role.WING_ATTACH_INTENT));

        int horizontalTailAttachX = quantizeNonNegative(
                geometry.horizontalTail().leadingEdgeXM()
                        + 0.25 * geometry.horizontalTail().rootChordM(),
                scale);
        int horizontalTailY = quantizeNonNegative(mounts.horizontalTailYM(), scale);
        cells.addAll(axisAlignedLine(
                new AircraftBlockspaceIR.LatticePoint(horizontalTailAttachX, centerY, 0),
                new AircraftBlockspaceIR.LatticePoint(horizontalTailAttachX, horizontalTailY, 0),
                AircraftBlockspaceIR.Role.TAIL_ATTACH_INTENT));

        int verticalTailAttachX = quantizeNonNegative(
                geometry.verticalTail().leadingEdgeXM()
                        + 0.25 * geometry.verticalTail().rootChordM(),
                scale);
        int verticalTailRootY = quantizeNonNegative(mounts.verticalTailRootYM(), scale);
        cells.addAll(axisAlignedLine(
                new AircraftBlockspaceIR.LatticePoint(verticalTailAttachX, centerY, 0),
                new AircraftBlockspaceIR.LatticePoint(verticalTailAttachX, verticalTailRootY, 0),
                AircraftBlockspaceIR.Role.TAIL_ATTACH_INTENT));

        Map<AircraftBlockspaceIR.AnchorType, AircraftBlockspaceIR.Anchor> anchors = anchors(design, spec, centerY);
        List<AircraftBlockspaceIR.Cell> orderedCells = List.copyOf(cells);
        double wingExtent = extent(orderedCells, AircraftBlockspaceIR.Role.WING_SURFACE_INTENT, Axis.Z, scale);
        double horizontalTailExtent = extent(
                orderedCells, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT, Axis.Z, scale);
        double verticalTailExtent = extent(
                orderedCells, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT, Axis.Y, scale);
        AircraftBlockspaceIR.Anchor cgAnchor = anchors.get(AircraftBlockspaceIR.AnchorType.CG_REFERENCE);
        double cgQuantizationErrorBlocks = Math.abs(
                meter(cgAnchor.lattice().x(), scale) - design.metrics().cgXM()) * scale;
        int components = connectedComponents(orderedCells);
        boolean symmetric = mirrorComplete(orderedCells);
        List<AircraftBlockspaceIR.PropellerDiskViolation> propellerViolations = propellerViolations(
                orderedCells, design.geometry().propellerEnvelope(), scale, spec.propellerHubRadiusM());
        boolean propellerClear = propellerViolations.isEmpty();

        AircraftBlockspaceIR.DimensionErrors dimensionErrors = new AircraftBlockspaceIR.DimensionErrors(
                Math.abs(wingExtent - geometry.wing().spanM()) * scale,
                Math.abs(horizontalTailExtent - geometry.horizontalTail().spanM()) * scale,
                Math.abs(verticalTailExtent - geometry.verticalTail().heightM()) * scale);
        AircraftBlockspaceSpec.ValidationLimits limits = spec.validationLimits();
        boolean passed = components == 1
                && symmetric
                && propellerClear
                && cgQuantizationErrorBlocks <= limits.maxCgStationErrorBlocks() + TOLERANCE
                && dimensionErrors.wingSpanBlocks() <= limits.maxDimensionErrorBlocks() + TOLERANCE
                && dimensionErrors.horizontalTailSpanBlocks() <= limits.maxDimensionErrorBlocks() + TOLERANCE
                && dimensionErrors.verticalTailHeightBlocks() <= limits.maxDimensionErrorBlocks() + TOLERANCE;

        AircraftBlockspaceIR.Metrics metrics = new AircraftBlockspaceIR.Metrics(
                orderedCells.size(),
                components,
                symmetric,
                propellerClear,
                propellerViolations.size(),
                cgQuantizationErrorBlocks,
                geometry.wing().spanM(),
                wingExtent,
                geometry.horizontalTail().spanM(),
                horizontalTailExtent,
                geometry.verticalTail().heightM(),
                verticalTailExtent,
                dimensionErrors);
        AircraftBlockspaceIR.Validation validation = new AircraftBlockspaceIR.Validation(
                passed,
                "deterministic_blockspace_transcription_and_capability_contract_only",
                List.of(
                        "concrete target block compatibility",
                        "in-engine assembly connectivity",
                        "in-engine center of mass",
                        "in-engine aerodynamic forces",
                        "flight stability or control authority",
                        "structural strength"),
                propellerViolations);

        return new AircraftBlockspaceIR(
                AircraftBlockspaceIR.SCHEMA_VERSION,
                spec.assetId(),
                design.assetId(),
                design.sha256(),
                AircraftBlockspaceIR.COMPILER_VERSION,
                new AircraftBlockspaceIR.CoordinateSystem(
                        "nose_to_tail", "up", "starboard_positive", "integer_lattice", scale),
                new AircraftBlockspaceIR.TranscriptionAssumptions(
                        "cell_center_scanline_exact_linear_taper",
                        "centerline_structural_spine_only",
                        "orthogonal_axis_aligned_only",
                        mounts,
                        false),
                orderedCells,
                anchors,
                metrics,
                validation);
    }

    static List<AircraftBlockspaceIR.Cell> axisAlignedLine(
            AircraftBlockspaceIR.LatticePoint from,
            AircraftBlockspaceIR.LatticePoint to,
            AircraftBlockspaceIR.Role role) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(role, "role");
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int changedAxes = (dx == 0 ? 0 : 1) + (dy == 0 ? 0 : 1) + (dz == 0 ? 0 : 1);
        if (changedAxes > 1) {
            throw new IllegalArgumentException("connector must be axis-aligned: " + from + " -> " + to);
        }
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int sx = Integer.compare(dx, 0);
        int sy = Integer.compare(dy, 0);
        int sz = Integer.compare(dz, 0);
        ArrayList<AircraftBlockspaceIR.Cell> result = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            result.add(new AircraftBlockspaceIR.Cell(
                    from.x() + sx * step,
                    from.y() + sy * step,
                    from.z() + sz * step,
                    role));
        }
        return List.copyOf(result);
    }

    private static Set<AircraftBlockspaceIR.Cell> horizontalSurfaceCells(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM,
            double yM,
            double scale,
            AircraftBlockspaceIR.Role role) {
        LinkedHashSet<AircraftBlockspaceIR.Cell> result = new LinkedHashSet<>();
        double halfSpan = spanM / 2.0;
        int y = quantizeNonNegative(yM, scale);
        for (int z : indicesBetween(-halfSpan, halfSpan, scale)) {
            double eta = halfSpan == 0.0 ? 0.0 : Math.abs(meter(z, scale)) / halfSpan;
            if (eta > 1.0 + TOLERANCE) {
                continue;
            }
            double chord = surfaceChord(rootChordM, tipChordM, eta);
            for (int x : indicesBetween(leadingEdgeXM, leadingEdgeXM + chord, scale)) {
                result.add(new AircraftBlockspaceIR.Cell(x, y, z, role));
            }
        }
        return result;
    }

    private static Set<AircraftBlockspaceIR.Cell> verticalSurfaceCells(
            double heightM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM,
            double rootYM,
            double scale,
            AircraftBlockspaceIR.Role role) {
        LinkedHashSet<AircraftBlockspaceIR.Cell> result = new LinkedHashSet<>();
        for (int y : indicesBetween(rootYM, rootYM + heightM, scale)) {
            double eta = heightM == 0.0 ? 0.0 : (meter(y, scale) - rootYM) / heightM;
            if (eta < -TOLERANCE || eta > 1.0 + TOLERANCE) {
                continue;
            }
            double chord = surfaceChord(rootChordM, tipChordM, eta);
            for (int x : indicesBetween(leadingEdgeXM, leadingEdgeXM + chord, scale)) {
                result.add(new AircraftBlockspaceIR.Cell(x, y, 0, role));
            }
        }
        return result;
    }

    private static Map<AircraftBlockspaceIR.AnchorType, AircraftBlockspaceIR.Anchor> anchors(
            AircraftDesignIR design,
            AircraftBlockspaceSpec spec,
            int centerY) {
        double scale = spec.blocksPerMeter();
        AircraftDesignSpec.PropellerEnvelope propeller = design.geometry().propellerEnvelope();
        EnumMap<AircraftBlockspaceIR.AnchorType, AircraftBlockspaceIR.Anchor> anchors =
                new EnumMap<>(AircraftBlockspaceIR.AnchorType.class);
        anchors.put(
                AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS,
                anchor(propeller.centerXM(), propeller.centerYM(), 0.0, scale));
        anchors.put(
                AircraftBlockspaceIR.AnchorType.CG_REFERENCE,
                new AircraftBlockspaceIR.Anchor(
                        new AircraftBlockspaceIR.ContinuousPoint(
                                design.metrics().cgXM(), spec.mounts().fuselageCenterYM(), 0.0),
                        new AircraftBlockspaceIR.LatticePoint(
                                quantizeNonNegative(design.metrics().cgXM(), scale), centerY, 0)));
        anchors.put(
                AircraftBlockspaceIR.AnchorType.PILOT_STATION,
                anchor(spec.semanticStations().pilotXM(), spec.mounts().fuselageCenterYM(), 0.0, scale));
        anchors.put(
                AircraftBlockspaceIR.AnchorType.CARGO_STATION,
                anchor(spec.semanticStations().cargoXM(), spec.mounts().fuselageCenterYM(), 0.0, scale));
        return anchors;
    }

    private static AircraftBlockspaceIR.Anchor anchor(double xM, double yM, double zM, double scale) {
        return new AircraftBlockspaceIR.Anchor(
                new AircraftBlockspaceIR.ContinuousPoint(xM, yM, zM),
                new AircraftBlockspaceIR.LatticePoint(
                        quantizeNonNegative(xM, scale), quantizeNonNegative(yM, scale), 0));
    }

    private static int connectedComponents(List<AircraftBlockspaceIR.Cell> cells) {
        Set<AircraftBlockspaceIR.LatticePoint> unseen = new HashSet<>();
        for (AircraftBlockspaceIR.Cell cell : cells) {
            unseen.add(cell.point());
        }
        int components = 0;
        ArrayDeque<AircraftBlockspaceIR.LatticePoint> queue = new ArrayDeque<>();
        while (!unseen.isEmpty()) {
            components++;
            AircraftBlockspaceIR.LatticePoint start = unseen.iterator().next();
            unseen.remove(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                AircraftBlockspaceIR.LatticePoint point = queue.removeFirst();
                for (AircraftBlockspaceIR.LatticePoint neighbor : neighbors(point)) {
                    if (unseen.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    private static List<AircraftBlockspaceIR.LatticePoint> neighbors(AircraftBlockspaceIR.LatticePoint point) {
        return List.of(
                new AircraftBlockspaceIR.LatticePoint(point.x() + 1, point.y(), point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x() - 1, point.y(), point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() + 1, point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() - 1, point.z()),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() + 1),
                new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() - 1));
    }

    private static boolean mirrorComplete(List<AircraftBlockspaceIR.Cell> cells) {
        Set<AircraftBlockspaceIR.Cell> relevant = new HashSet<>();
        for (AircraftBlockspaceIR.Cell cell : cells) {
            if (cell.role() == AircraftBlockspaceIR.Role.WING_SURFACE_INTENT
                    || cell.role() == AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT) {
                relevant.add(cell);
            }
        }
        for (AircraftBlockspaceIR.Cell cell : relevant) {
            if (!relevant.contains(new AircraftBlockspaceIR.Cell(
                    cell.x(), cell.y(), -cell.z(), cell.role()))) {
                return false;
            }
        }
        return true;
    }

    private static List<AircraftBlockspaceIR.PropellerDiskViolation> propellerViolations(
            List<AircraftBlockspaceIR.Cell> cells,
            AircraftDesignSpec.PropellerEnvelope propeller,
            double scale,
            double hubRadiusM) {
        int planeX = quantizeNonNegative(propeller.centerXM(), scale);
        double radius = propeller.diameterM() / 2.0;
        ArrayList<AircraftBlockspaceIR.PropellerDiskViolation> violations = new ArrayList<>();
        for (AircraftBlockspaceIR.Cell cell : cells) {
            if (cell.x() != planeX) {
                continue;
            }
            double dy = meter(cell.y(), scale) - propeller.centerYM();
            double dz = meter(cell.z(), scale);
            double radialDistance = Math.hypot(dy, dz);
            if (hubRadiusM + TOLERANCE < radialDistance && radialDistance <= radius + TOLERANCE) {
                violations.add(new AircraftBlockspaceIR.PropellerDiskViolation(
                        cell.x(), cell.y(), cell.z(), cell.role(), radialDistance));
            }
        }
        return List.copyOf(violations);
    }

    private static double extent(
            List<AircraftBlockspaceIR.Cell> cells,
            AircraftBlockspaceIR.Role role,
            Axis axis,
            double scale) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (AircraftBlockspaceIR.Cell cell : cells) {
            if (cell.role() != role) {
                continue;
            }
            int value = axis == Axis.Y ? cell.y() : cell.z();
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (min == Integer.MAX_VALUE) {
            return 0.0;
        }
        return (max - min + 1) / scale;
    }

    private static int quantizeNonNegative(double valueM, double scale) {
        if (!Double.isFinite(valueM) || valueM < 0.0) {
            throw new IllegalArgumentException("expected finite non-negative coordinate");
        }
        return (int) Math.floor(valueM * scale + 0.5);
    }

    private static List<Integer> indicesBetween(double loM, double hiM, double scale) {
        double lo = Math.min(loM, hiM);
        double hi = Math.max(loM, hiM);
        int first = (int) Math.ceil(lo * scale - TOLERANCE);
        int last = (int) Math.floor(hi * scale + TOLERANCE);
        ArrayList<Integer> result = new ArrayList<>(Math.max(0, last - first + 1));
        for (int index = first; index <= last; index++) {
            result.add(index);
        }
        return List.copyOf(result);
    }

    private static double meter(int index, double scale) {
        return index / scale;
    }

    private static double surfaceChord(double rootChordM, double tipChordM, double eta) {
        double clamped = Math.min(1.0, Math.max(0.0, eta));
        return rootChordM + (tipChordM - rootChordM) * clamped;
    }

    private enum Axis {
        Y,
        Z
    }
}
