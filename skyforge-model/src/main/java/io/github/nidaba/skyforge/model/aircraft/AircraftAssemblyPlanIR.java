package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Coordinate-unique target-neutral semantic assembly plan derived from aircraft blockspace. */
public record AircraftAssemblyPlanIR(
        int schemaVersion,
        String assetId,
        String sourceBlockspaceAssetId,
        String sourceBlockspaceDigestSha256,
        String compilerVersion,
        AircraftBlockspaceIR.CoordinateSystem coordinateSystem,
        List<Site> sites,
        List<Station> stations,
        Metrics metrics,
        Validation validation) {
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPILER_VERSION = "aircraft-assembly-planner-1";

    public AircraftAssemblyPlanIR {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported aircraft assembly plan schema: " + schemaVersion);
        }
        assetId = requireText("assetId", assetId);
        sourceBlockspaceAssetId = requireText("sourceBlockspaceAssetId", sourceBlockspaceAssetId);
        sourceBlockspaceDigestSha256 = requireSha256(sourceBlockspaceDigestSha256);
        compilerVersion = requireText("compilerVersion", compilerVersion);
        if (!COMPILER_VERSION.equals(compilerVersion)) {
            throw new IllegalArgumentException("unsupported aircraft assembly planner version: " + compilerVersion);
        }
        coordinateSystem = Objects.requireNonNull(coordinateSystem, "coordinateSystem");
        ArrayList<Site> orderedSites = new ArrayList<>(Objects.requireNonNull(sites, "sites"));
        if (orderedSites.isEmpty()) {
            throw new IllegalArgumentException("sites must not be empty");
        }
        orderedSites.sort(Comparator.comparing(Site::point));
        sites = List.copyOf(orderedSites);
        ArrayList<Station> orderedStations = new ArrayList<>(Objects.requireNonNull(stations, "stations"));
        orderedStations.sort(Comparator.comparing(station -> station.type().id()));
        stations = List.copyOf(orderedStations);
        metrics = Objects.requireNonNull(metrics, "metrics");
        validation = Objects.requireNonNull(validation, "validation");
    }

    public String sha256() {
        return AircraftAssemblyPlanIRJson.sha256(this);
    }

    public record Site(
            AircraftBlockspaceIR.LatticePoint point,
            List<AircraftBlockspaceIR.Role> roles,
            List<String> capabilities) {
        public Site {
            point = Objects.requireNonNull(point, "point");
            ArrayList<AircraftBlockspaceIR.Role> orderedRoles = new ArrayList<>(
                    Objects.requireNonNull(roles, "roles"));
            if (orderedRoles.isEmpty()) {
                throw new IllegalArgumentException("assembly site roles must not be empty");
            }
            orderedRoles.sort(Comparator.comparing(AircraftBlockspaceIR.Role::id));
            roles = List.copyOf(orderedRoles);
            ArrayList<String> orderedCapabilities = new ArrayList<>(
                    Objects.requireNonNull(capabilities, "capabilities"));
            orderedCapabilities.forEach(value -> requireText("capability", value));
            orderedCapabilities.sort(String::compareTo);
            capabilities = List.copyOf(orderedCapabilities);
        }
    }

    public record Station(
            AircraftBlockspaceIR.AnchorType type,
            AircraftBlockspaceIR.ContinuousPoint continuousM,
            AircraftBlockspaceIR.LatticePoint lattice,
            List<String> capabilities,
            String placementSemantics) {
        public Station {
            type = Objects.requireNonNull(type, "type");
            continuousM = Objects.requireNonNull(continuousM, "continuousM");
            lattice = Objects.requireNonNull(lattice, "lattice");
            capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            placementSemantics = requireText("placementSemantics", placementSemantics);
            if (!"anchor_requirement_not_occupied_site".equals(placementSemantics)) {
                throw new IllegalArgumentException("unsupported station placement semantics: " + placementSemantics);
            }
        }
    }

    public record Metrics(
            int inputCellRecordCount,
            int uniqueAssemblySiteCount,
            int collapsedRoleRecordCount,
            int multiRoleSiteCount,
            boolean coordinateUniquenessSatisfied,
            boolean allSourceRolesPreserved) {
        public Metrics {
            if (inputCellRecordCount < 1
                    || uniqueAssemblySiteCount < 1
                    || collapsedRoleRecordCount < 0
                    || multiRoleSiteCount < 0) {
                throw new IllegalArgumentException("invalid aircraft assembly metrics");
            }
        }
    }

    public record Validation(boolean passed, String scope, List<String> doesNotProve) {
        public Validation {
            scope = requireText("validation scope", scope);
            doesNotProve = List.copyOf(Objects.requireNonNull(doesNotProve, "doesNotProve"));
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
        String normalized = requireText("sha256", value).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return normalized;
    }
}
