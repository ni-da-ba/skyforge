package io.github.nidaba.skyforge.model.aircraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Canonical deterministic JSON writer for {@link AircraftBlockspaceIR}. */
public final class AircraftBlockspaceIRJson {
    public byte[] write(AircraftBlockspaceIR ir) {
        return writeString(ir).getBytes(StandardCharsets.UTF_8);
    }

    public String writeString(AircraftBlockspaceIR ir) {
        StringBuilder json = new StringBuilder(16384);
        json.append('{');
        field(json, "schemaVersion", ir.schemaVersion()).append(',');
        field(json, "assetId", ir.assetId()).append(',');
        field(json, "sourceDesignAssetId", ir.sourceDesignAssetId()).append(',');
        field(json, "sourceDesignDigestSha256", ir.sourceDesignDigestSha256()).append(',');
        field(json, "compilerVersion", ir.compilerVersion()).append(',');
        coordinateSystem(json, ir.coordinateSystem()).append(',');
        assumptions(json, ir.declaredTranscriptionAssumptions()).append(',');
        cells(json, ir.cells()).append(',');
        anchors(json, ir.anchors()).append(',');
        roleCapabilities(json).append(',');
        metrics(json, ir.metrics()).append(',');
        validation(json, ir.validation());
        json.append("}\n");
        return json.toString();
    }

    public static String sha256(AircraftBlockspaceIR ir) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(new AircraftBlockspaceIRJson().write(ir)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static StringBuilder coordinateSystem(
            StringBuilder json, AircraftBlockspaceIR.CoordinateSystem coordinateSystem) {
        quoted(json, "coordinateSystem").append(":{");
        field(json, "x", coordinateSystem.x()).append(',');
        field(json, "y", coordinateSystem.y()).append(',');
        field(json, "z", coordinateSystem.z()).append(',');
        field(json, "cellCenters", coordinateSystem.cellCenters()).append(',');
        hexField(json, "blocksPerMeter", coordinateSystem.blocksPerMeter());
        return json.append('}');
    }

    private static StringBuilder assumptions(
            StringBuilder json, AircraftBlockspaceIR.TranscriptionAssumptions assumptions) {
        quoted(json, "declaredTranscriptionAssumptions").append(":{");
        field(json, "surfaceRasterization", assumptions.surfaceRasterization()).append(',');
        field(json, "fuselageRepresentation", assumptions.fuselageRepresentation()).append(',');
        field(json, "connectors", assumptions.connectors()).append(',');
        quoted(json, "mounts").append(":{");
        hexField(json, "fuselageCenterYM", assumptions.mounts().fuselageCenterYM()).append(',');
        hexField(json, "wingYM", assumptions.mounts().wingYM()).append(',');
        hexField(json, "horizontalTailYM", assumptions.mounts().horizontalTailYM()).append(',');
        hexField(json, "verticalTailRootYM", assumptions.mounts().verticalTailRootYM());
        json.append("},");
        field(json, "containsConcreteTargetResourceIdentity", assumptions.containsConcreteTargetResourceIdentity());
        return json.append('}');
    }

    private static StringBuilder cells(StringBuilder json, List<AircraftBlockspaceIR.Cell> cells) {
        quoted(json, "cells").append(": [");
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftBlockspaceIR.Cell cell = cells.get(index);
            json.append('{');
            field(json, "x", cell.x()).append(',');
            field(json, "y", cell.y()).append(',');
            field(json, "z", cell.z()).append(',');
            field(json, "role", cell.role().id());
            json.append('}');
        }
        return json.append(']');
    }

    private static StringBuilder anchors(
            StringBuilder json, Map<AircraftBlockspaceIR.AnchorType, AircraftBlockspaceIR.Anchor> anchors) {
        quoted(json, "anchors").append(":{");
        boolean first = true;
        for (AircraftBlockspaceIR.AnchorType type : AircraftBlockspaceIR.AnchorType.values()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            AircraftBlockspaceIR.Anchor anchor = anchors.get(type);
            quoted(json, type.id()).append(":{");
            quoted(json, "continuousM").append(':');
            point(json, anchor.continuousM());
            json.append(',');
            quoted(json, "lattice").append(':');
            lattice(json, anchor.lattice());
            json.append('}');
        }
        return json.append('}');
    }

    private static StringBuilder roleCapabilities(StringBuilder json) {
        quoted(json, "capabilityContract").append(":{");
        field(json, "concreteResourceResolution", "deferred_to_target_lowering").append(',');
        quoted(json, "roles").append(":{");
        AircraftBlockspaceIR.Role[] roles = AircraftBlockspaceIR.Role.values();
        for (int index = 0; index < roles.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftBlockspaceIR.Role role = roles[index];
            quoted(json, role.id()).append(':');
            strings(json, role.capabilities());
        }
        json.append("},");
        quoted(json, "anchors").append(":{");
        AircraftBlockspaceIR.AnchorType[] anchors = AircraftBlockspaceIR.AnchorType.values();
        for (int index = 0; index < anchors.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftBlockspaceIR.AnchorType anchor = anchors[index];
            quoted(json, anchor.id()).append(':');
            strings(json, anchor.capabilities());
        }
        return json.append("}}");
    }

    private static StringBuilder metrics(StringBuilder json, AircraftBlockspaceIR.Metrics metrics) {
        quoted(json, "metrics").append(":{");
        field(json, "cellCount", metrics.cellCount()).append(',');
        field(json, "connectedComponents6Neighbor", metrics.connectedComponents6Neighbor()).append(',');
        field(json, "mirrorSymmetrySatisfied", metrics.mirrorSymmetrySatisfied()).append(',');
        field(json, "propellerDiskClear", metrics.propellerDiskClear()).append(',');
        field(json, "propellerDiskViolationCount", metrics.propellerDiskViolationCount()).append(',');
        hexField(json, "cgStationQuantizationErrorBlocks", metrics.cgStationQuantizationErrorBlocks()).append(',');
        hexField(json, "continuousWingSpanM", metrics.continuousWingSpanM()).append(',');
        hexField(json, "realizedWingSpanM", metrics.realizedWingSpanM()).append(',');
        hexField(json, "continuousHorizontalTailSpanM", metrics.continuousHorizontalTailSpanM()).append(',');
        hexField(json, "realizedHorizontalTailSpanM", metrics.realizedHorizontalTailSpanM()).append(',');
        hexField(json, "continuousVerticalTailHeightM", metrics.continuousVerticalTailHeightM()).append(',');
        hexField(json, "realizedVerticalTailHeightM", metrics.realizedVerticalTailHeightM()).append(',');
        quoted(json, "dimensionErrorBlocks").append(":{");
        hexField(json, "wingSpanBlocks", metrics.dimensionErrorBlocks().wingSpanBlocks()).append(',');
        hexField(json, "horizontalTailSpanBlocks", metrics.dimensionErrorBlocks().horizontalTailSpanBlocks()).append(',');
        hexField(json, "verticalTailHeightBlocks", metrics.dimensionErrorBlocks().verticalTailHeightBlocks());
        return json.append("}}");
    }

    private static StringBuilder validation(StringBuilder json, AircraftBlockspaceIR.Validation validation) {
        quoted(json, "validation").append(":{");
        field(json, "passed", validation.passed()).append(',');
        field(json, "scope", validation.scope()).append(',');
        quoted(json, "doesNotProve").append(':');
        strings(json, validation.doesNotProve());
        json.append(',');
        quoted(json, "propellerDiskViolations").append(": [");
        for (int index = 0; index < validation.propellerDiskViolations().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftBlockspaceIR.PropellerDiskViolation violation = validation.propellerDiskViolations().get(index);
            json.append('{');
            field(json, "x", violation.x()).append(',');
            field(json, "y", violation.y()).append(',');
            field(json, "z", violation.z()).append(',');
            field(json, "role", violation.role().id()).append(',');
            hexField(json, "radiusM", violation.radiusM());
            json.append('}');
        }
        return json.append("]}");
    }

    private static StringBuilder point(StringBuilder json, AircraftBlockspaceIR.ContinuousPoint point) {
        json.append('[');
        quoted(json, Double.toHexString(point.xM())).append(',');
        quoted(json, Double.toHexString(point.yM())).append(',');
        quoted(json, Double.toHexString(point.zM()));
        return json.append(']');
    }

    private static StringBuilder lattice(StringBuilder json, AircraftBlockspaceIR.LatticePoint point) {
        return json.append('[').append(point.x()).append(',').append(point.y()).append(',').append(point.z()).append(']');
    }

    private static StringBuilder strings(StringBuilder json, List<String> values) {
        json.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            quoted(json, values.get(index));
        }
        return json.append(']');
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        quoted(json, name).append(':');
        return quoted(json, value);
    }

    private static StringBuilder field(StringBuilder json, String name, int value) {
        quoted(json, name).append(':').append(value);
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, boolean value) {
        quoted(json, name).append(':').append(value);
        return json;
    }

    private static StringBuilder hexField(StringBuilder json, String name, double value) {
        quoted(json, name).append(':');
        return quoted(json, Double.toHexString(value));
    }

    private static StringBuilder quoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                }
            }
        }
        return json.append('"');
    }
}
