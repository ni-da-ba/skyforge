package io.github.nidaba.skyforge.model.aircraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Canonical deterministic JSON writer for {@link AircraftAssemblyPlanIR}. */
public final class AircraftAssemblyPlanIRJson {
    public byte[] write(AircraftAssemblyPlanIR ir) {
        return writeString(ir).getBytes(StandardCharsets.UTF_8);
    }

    public String writeString(AircraftAssemblyPlanIR ir) {
        StringBuilder json = new StringBuilder(16384);
        json.append('{');
        field(json, "schemaVersion", ir.schemaVersion()).append(',');
        field(json, "assetId", ir.assetId()).append(',');
        field(json, "sourceBlockspaceAssetId", ir.sourceBlockspaceAssetId()).append(',');
        field(json, "sourceBlockspaceDigestSha256", ir.sourceBlockspaceDigestSha256()).append(',');
        field(json, "compilerVersion", ir.compilerVersion()).append(',');
        coordinateSystem(json, ir.coordinateSystem()).append(',');
        quoted(json, "siteSemantics").append(":{");
        field(json, "coordinateAuthority", "one_assembly_site_per_integer_lattice_coordinate").append(',');
        field(json, "roleComposition", "union_all_source_roles_at_coordinate").append(',');
        field(json, "capabilityComposition", "set_union_of_role_capabilities").append(',');
        field(json, "targetResourceIdentity", "deferred_to_target_lowering");
        json.append("},");
        sites(json, ir.sites()).append(',');
        stations(json, ir.stations()).append(',');
        metrics(json, ir.metrics()).append(',');
        validation(json, ir.validation());
        json.append("}\n");
        return json.toString();
    }

    public static String sha256(AircraftAssemblyPlanIR ir) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(new AircraftAssemblyPlanIRJson().write(ir)));
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

    private static StringBuilder sites(StringBuilder json, List<AircraftAssemblyPlanIR.Site> sites) {
        quoted(json, "sites").append(": [");
        for (int index = 0; index < sites.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftAssemblyPlanIR.Site site = sites.get(index);
            json.append('{');
            field(json, "x", site.point().x()).append(',');
            field(json, "y", site.point().y()).append(',');
            field(json, "z", site.point().z()).append(',');
            quoted(json, "roles").append(':');
            json.append('[');
            for (int roleIndex = 0; roleIndex < site.roles().size(); roleIndex++) {
                if (roleIndex > 0) {
                    json.append(',');
                }
                quoted(json, site.roles().get(roleIndex).id());
            }
            json.append("],");
            quoted(json, "capabilities").append(':');
            strings(json, site.capabilities());
            json.append('}');
        }
        return json.append(']');
    }

    private static StringBuilder stations(StringBuilder json, List<AircraftAssemblyPlanIR.Station> stations) {
        quoted(json, "stations").append(": [");
        for (int index = 0; index < stations.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            AircraftAssemblyPlanIR.Station station = stations.get(index);
            json.append('{');
            field(json, "name", station.type().id()).append(',');
            quoted(json, "continuousM").append(':');
            point(json, station.continuousM());
            json.append(',');
            quoted(json, "lattice").append(':');
            lattice(json, station.lattice());
            json.append(',');
            quoted(json, "capabilities").append(':');
            strings(json, station.capabilities());
            json.append(',');
            field(json, "placementSemantics", station.placementSemantics());
            json.append('}');
        }
        return json.append(']');
    }

    private static StringBuilder metrics(StringBuilder json, AircraftAssemblyPlanIR.Metrics metrics) {
        quoted(json, "metrics").append(":{");
        field(json, "inputCellRecordCount", metrics.inputCellRecordCount()).append(',');
        field(json, "uniqueAssemblySiteCount", metrics.uniqueAssemblySiteCount()).append(',');
        field(json, "collapsedRoleRecordCount", metrics.collapsedRoleRecordCount()).append(',');
        field(json, "multiRoleSiteCount", metrics.multiRoleSiteCount()).append(',');
        field(json, "coordinateUniquenessSatisfied", metrics.coordinateUniquenessSatisfied()).append(',');
        field(json, "allSourceRolesPreserved", metrics.allSourceRolesPreserved());
        return json.append('}');
    }

    private static StringBuilder validation(StringBuilder json, AircraftAssemblyPlanIR.Validation validation) {
        quoted(json, "validation").append(":{");
        field(json, "passed", validation.passed()).append(',');
        field(json, "scope", validation.scope()).append(',');
        quoted(json, "doesNotProve").append(':');
        strings(json, validation.doesNotProve());
        return json.append('}');
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
