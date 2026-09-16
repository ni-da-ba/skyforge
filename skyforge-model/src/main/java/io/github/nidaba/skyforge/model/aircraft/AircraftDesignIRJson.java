package io.github.nidaba.skyforge.model.aircraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Canonical deterministic JSON and content identity for {@link AircraftDesignIR}. */
public final class AircraftDesignIRJson {
    /** Serializes one design IR as deterministic UTF-8 JSON followed by one newline. */
    public byte[] write(AircraftDesignIR design) {
        return writeString(design).getBytes(StandardCharsets.UTF_8);
    }

    /** Serializes one design IR as deterministic JSON followed by one newline. */
    public String writeString(AircraftDesignIR design) {
        Objects.requireNonNull(design, "design");
        StringBuilder json = new StringBuilder(4096);
        json.append("{\"schemaVersion\":").append(design.schemaVersion());
        appendStringMember(json, "assetId", design.assetId());
        appendStringMember(json, "compilerVersion", design.compilerVersion());
        appendConfiguration(json, design.configuration());
        appendMission(json, design.mission());
        appendAssumptions(json, design.declaredAssumptions());
        appendReferenceTargets(json, design.referenceTargets());
        appendGeometry(json, design.geometry());
        appendMassLedger(json, design.massLedger());
        appendMetrics(json, design.metrics());
        appendSolver(json, design.solver());
        appendValidation(json, design.validation());
        appendTargetBoundary(json, design.targetBoundary());
        return json.append("}\n").toString();
    }

    /** Stable SHA-256 identity over the canonical production JSON bytes. */
    public static String sha256(AircraftDesignIR design) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(new AircraftDesignIRJson().write(design)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void appendConfiguration(
            StringBuilder json, AircraftDesignSpec.Configuration value) {
        json.append(",\"configuration\":{");
        appendStringField(json, "aircraftClass", value.aircraftClass());
        appendStringMember(json, "wingPosition", value.wingPosition());
        json.append(",\"engineCount\":").append(value.engineCount());
        appendStringMember(json, "propulsionLayout", value.propulsionLayout());
        appendStringMember(json, "tail", value.tail());
        json.append(",\"crew\":").append(value.crew());
        appendStringMember(json, "cargoRole", value.cargoRole());
        json.append('}');
    }

    private static void appendMission(StringBuilder json, AircraftDesignSpec.Mission value) {
        json.append(",\"mission\":{");
        appendHexDoubleField(json, "grossMassKg", value.grossMassKg());
        appendHexDoubleMember(json, "airDensityKgM3", value.airDensityKgM3());
        appendHexDoubleMember(json, "gravityMS2", value.gravityMS2());
        appendHexDoubleMember(json, "cruiseSpeedMS", value.cruiseSpeedMS());
        appendHexDoubleMember(json, "designLiftCoefficient", value.designLiftCoefficient());
        json.append('}');
    }

    private static void appendAssumptions(
            StringBuilder json, AircraftDesignSpec.AnalyticalAssumptions value) {
        json.append(",\"declaredAssumptions\":{");
        appendStringField(json, "atmosphere", value.atmosphere());
        appendStringMember(json, "wingPlanform", value.wingPlanform());
        appendStringMember(json, "tailPlanforms", value.tailPlanforms());
        appendHexDoubleMember(json, "spanEfficiency", value.spanEfficiency());
        appendStringMember(json, "spanEfficiencyStatus", value.spanEfficiencyStatus());
        appendStringMember(json, "massModel", value.massModel());
        appendStringMember(json, "stabilityClaim", value.stabilityClaim());
        json.append('}');
    }

    private static void appendReferenceTargets(
            StringBuilder json, AircraftDesignSpec.ReferenceTargets value) {
        json.append(",\"referenceTargets\":{");
        appendHexDoubleField(json, "horizontalTailVolume", value.horizontalTailVolume());
        appendHexDoubleMember(json, "verticalTailVolume", value.verticalTailVolume());
        appendStringMember(json, "status", value.status());
        json.append('}');
    }

    private static void appendGeometry(StringBuilder json, AircraftDesignIR.Geometry value) {
        json.append(",\"geometry\":{");
        appendFuselage(json, value.fuselage());
        appendWing(json, value.wing());
        appendHorizontalTail(json, value.horizontalTail());
        appendVerticalTail(json, value.verticalTail());
        appendPropellerEnvelope(json, value.propellerEnvelope());
        json.append('}');
    }

    private static void appendFuselage(StringBuilder json, AircraftDesignIR.Fuselage value) {
        json.append("\"fuselage\":{");
        appendHexDoubleField(json, "lengthM", value.lengthM());
        appendHexDoubleMember(json, "maxWidthM", value.maxWidthM());
        appendHexDoubleMember(json, "maxHeightM", value.maxHeightM());
        json.append('}');
    }

    private static void appendWing(StringBuilder json, AircraftDesignIR.Wing value) {
        json.append(",\"wing\":{");
        appendHexDoubleField(json, "spanM", value.spanM());
        appendHexDoubleMember(json, "rootChordM", value.rootChordM());
        appendHexDoubleMember(json, "tipChordM", value.tipChordM());
        appendHexDoubleMember(json, "leadingEdgeXM", value.leadingEdgeXM());
        appendStringMember(json, "mount", value.mount());
        appendStringMember(json, "sweep", value.sweep());
        json.append('}');
    }

    private static void appendHorizontalTail(
            StringBuilder json, AircraftDesignIR.HorizontalTail value) {
        json.append(",\"horizontalTail\":{");
        appendHexDoubleField(json, "spanM", value.spanM());
        appendHexDoubleMember(json, "rootChordM", value.rootChordM());
        appendHexDoubleMember(json, "tipChordM", value.tipChordM());
        appendHexDoubleMember(json, "leadingEdgeXM", value.leadingEdgeXM());
        json.append('}');
    }

    private static void appendVerticalTail(StringBuilder json, AircraftDesignIR.VerticalTail value) {
        json.append(",\"verticalTail\":{");
        appendHexDoubleField(json, "heightM", value.heightM());
        appendHexDoubleMember(json, "rootChordM", value.rootChordM());
        appendHexDoubleMember(json, "tipChordM", value.tipChordM());
        appendHexDoubleMember(json, "leadingEdgeXM", value.leadingEdgeXM());
        json.append('}');
    }

    private static void appendPropellerEnvelope(
            StringBuilder json, AircraftDesignSpec.PropellerEnvelope value) {
        json.append(",\"propellerEnvelope\":{");
        appendHexDoubleField(json, "diameterM", value.diameterM());
        appendHexDoubleMember(json, "centerXM", value.centerXM());
        appendHexDoubleMember(json, "centerYM", value.centerYM());
        json.append('}');
    }

    private static void appendMassLedger(StringBuilder json, List<AircraftDesignIR.MassItem> values) {
        json.append(",\"massLedger\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftDesignIR.MassItem value = values.get(index);
            json.append('{');
            appendStringField(json, "name", value.name());
            appendHexDoubleMember(json, "massKg", value.massKg());
            appendHexDoubleMember(json, "stationXM", value.stationXM());
            appendStringMember(json, "source", value.source());
            json.append('}');
        }
        json.append(']');
    }

    private static void appendMetrics(StringBuilder json, AircraftDesignIR.Metrics value) {
        json.append(",\"metrics\":{");
        appendHexDoubleField(json, "dynamicPressurePa", value.dynamicPressurePa());
        appendHexDoubleMember(json, "weightN", value.weightN());
        appendHexDoubleMember(json, "requiredWingAreaM2AtDesignCL", value.requiredWingAreaM2AtDesignCL());
        appendHexDoubleMember(json, "wingAreaM2", value.wingAreaM2());
        appendHexDoubleMember(json, "wingTaperRatio", value.wingTaperRatio());
        appendHexDoubleMember(json, "wingAspectRatio", value.wingAspectRatio());
        appendHexDoubleMember(json, "wingMacM", value.wingMacM());
        appendHexDoubleMember(json, "cruiseLiftN", value.cruiseLiftN());
        appendHexDoubleMember(json, "cruiseLiftResidualFraction", value.cruiseLiftResidualFraction());
        appendHexDoubleMember(
                json, "analyticalInducedDragCoefficient", value.analyticalInducedDragCoefficient());
        appendHexDoubleMember(json, "cgXM", value.cgXM());
        appendHexDoubleMember(json, "cgMacFraction", value.cgMacFraction());
        appendHexDoubleMember(json, "horizontalTailAreaM2", value.horizontalTailAreaM2());
        appendHexDoubleMember(json, "horizontalTailMacM", value.horizontalTailMacM());
        appendHexDoubleMember(json, "horizontalTailArmM", value.horizontalTailArmM());
        appendHexDoubleMember(json, "horizontalTailVolume", value.horizontalTailVolume());
        appendHexDoubleMember(
                json,
                "horizontalTailReferenceErrorFraction",
                value.horizontalTailReferenceErrorFraction());
        appendHexDoubleMember(json, "verticalTailAreaM2", value.verticalTailAreaM2());
        appendHexDoubleMember(json, "verticalTailMacM", value.verticalTailMacM());
        appendHexDoubleMember(json, "verticalTailArmM", value.verticalTailArmM());
        appendHexDoubleMember(json, "verticalTailVolume", value.verticalTailVolume());
        appendHexDoubleMember(
                json,
                "verticalTailReferenceErrorFraction",
                value.verticalTailReferenceErrorFraction());
        json.append('}');
    }

    private static void appendSolver(StringBuilder json, AircraftDesignIR.SolverEvidence value) {
        json.append(",\"solver\":{");
        appendStringField(json, "method", value.method());
        json.append(",\"candidateCountFeasible\":").append(value.candidateCountFeasible());
        json.append(",\"rejectionCounts\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(value.rejectionCounts()).entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendQuoted(json, entry.getKey());
            json.append(':').append(entry.getValue());
        }
        json.append('}');
        appendStringListMember(json, "objectiveOrder", value.objectiveOrder());
        appendDoubleListMember(json, "selectedObjective", value.selectedObjective());
        json.append('}');
    }

    private static void appendValidation(StringBuilder json, AircraftDesignIR.Validation value) {
        json.append(",\"validation\":{\"passed\":").append(value.passed());
        appendStringMember(json, "scope", value.scope());
        appendStringListMember(json, "doesNotProve", value.doesNotProve());
        json.append('}');
    }

    private static void appendTargetBoundary(StringBuilder json, AircraftDesignIR.TargetBoundary value) {
        json.append(",\"targetBoundary\":{\"containsConcreteTargetResourceNames\":")
                .append(value.containsConcreteTargetResourceNames());
        json.append(",\"targetAdapterApplied\":").append(value.targetAdapterApplied());
        json.append('}');
    }

    private static void appendStringListMember(StringBuilder json, String name, List<String> values) {
        json.append(",\"").append(name).append("\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendQuoted(json, values.get(index));
        }
        json.append(']');
    }

    private static void appendDoubleListMember(StringBuilder json, String name, List<Double> values) {
        json.append(",\"").append(name).append("\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendQuoted(json, Double.toHexString(values.get(index)));
        }
        json.append(']');
    }

    private static void appendStringField(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":");
        appendQuoted(json, value);
    }

    private static void appendStringMember(StringBuilder json, String name, String value) {
        json.append(",\"").append(name).append("\":");
        appendQuoted(json, value);
    }

    private static void appendHexDoubleField(StringBuilder json, String name, double value) {
        json.append('"').append(name).append("\":");
        appendQuoted(json, Double.toHexString(value));
    }

    private static void appendHexDoubleMember(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":");
        appendQuoted(json, Double.toHexString(value));
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        appendUnicodeEscape(json, character);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder json, char character) {
        final char[] hex = "0123456789abcdef".toCharArray();
        json.append("\\u");
        json.append(hex[(character >>> 12) & 0xf]);
        json.append(hex[(character >>> 8) & 0xf]);
        json.append(hex[(character >>> 4) & 0xf]);
        json.append(hex[character & 0xf]);
    }
}
