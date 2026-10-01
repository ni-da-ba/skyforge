package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Coalesces role-bearing blockspace records into one target-neutral site per lattice coordinate. */
public final class AircraftAssemblyPlanner {
    public AircraftAssemblyPlanIR plan(AircraftBlockspaceIR blockspace, String assetId) {
        Objects.requireNonNull(blockspace, "blockspace");
        if (!blockspace.validation().passed()) {
            throw new IllegalArgumentException("refusing to plan assembly from invalid blockspace IR");
        }
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("assembly assetId must not be blank");
        }

        TreeMap<AircraftBlockspaceIR.LatticePoint, EnumSet<AircraftBlockspaceIR.Role>> grouped = new TreeMap<>();
        for (AircraftBlockspaceIR.Cell cell : blockspace.cells()) {
            grouped.computeIfAbsent(cell.point(), ignored -> EnumSet.noneOf(AircraftBlockspaceIR.Role.class))
                    .add(cell.role());
        }
        if (grouped.isEmpty()) {
            throw new IllegalArgumentException("blockspace IR contains no occupied cells");
        }

        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>(grouped.size());
        int multiRoleSiteCount = 0;
        int sourceRoleMembershipCount = 0;
        for (Map.Entry<AircraftBlockspaceIR.LatticePoint, EnumSet<AircraftBlockspaceIR.Role>> entry
                : grouped.entrySet()) {
            ArrayList<AircraftBlockspaceIR.Role> roles = new ArrayList<>(entry.getValue());
            roles.sort(Comparator.comparing(AircraftBlockspaceIR.Role::id));
            sourceRoleMembershipCount += roles.size();
            if (roles.size() > 1) {
                multiRoleSiteCount++;
            }
            TreeSet<String> capabilities = new TreeSet<>();
            for (AircraftBlockspaceIR.Role role : roles) {
                capabilities.addAll(role.capabilities());
            }
            sites.add(new AircraftAssemblyPlanIR.Site(
                    entry.getKey(), roles, List.copyOf(capabilities)));
        }

        ArrayList<AircraftAssemblyPlanIR.Station> stations = new ArrayList<>();
        for (AircraftBlockspaceIR.AnchorType type : AircraftBlockspaceIR.AnchorType.values()) {
            AircraftBlockspaceIR.Anchor anchor = blockspace.anchors().get(type);
            stations.add(new AircraftAssemblyPlanIR.Station(
                    type,
                    anchor.continuousM(),
                    anchor.lattice(),
                    type.capabilities(),
                    "anchor_requirement_not_occupied_site"));
        }

        int inputRecords = blockspace.cells().size();
        int uniqueSites = sites.size();
        int collapsedRecords = inputRecords - uniqueSites;
        boolean unique = sites.stream().map(AircraftAssemblyPlanIR.Site::point).distinct().count() == uniqueSites;
        int plannedRoleMembershipCount = sites.stream().mapToInt(site -> site.roles().size()).sum();
        boolean rolesPreserved = sourceRoleMembershipCount == plannedRoleMembershipCount;
        AircraftAssemblyPlanIR.Metrics metrics = new AircraftAssemblyPlanIR.Metrics(
                inputRecords,
                uniqueSites,
                collapsedRecords,
                multiRoleSiteCount,
                unique,
                rolesPreserved);
        AircraftAssemblyPlanIR.Validation validation = new AircraftAssemblyPlanIR.Validation(
                unique && rolesPreserved,
                "coordinate_unique_semantic_assembly_planning_only",
                List.of(
                        "concrete target block legality",
                        "runtime assembly connectivity",
                        "runtime center of mass",
                        "runtime aerodynamic forces",
                        "propulsion performance",
                        "flight stability or control authority",
                        "structural strength"));

        return new AircraftAssemblyPlanIR(
                AircraftAssemblyPlanIR.SCHEMA_VERSION,
                assetId,
                blockspace.assetId(),
                blockspace.sha256(),
                AircraftAssemblyPlanIR.COMPILER_VERSION,
                blockspace.coordinateSystem(),
                sites,
                stations,
                metrics,
                validation);
    }
}
