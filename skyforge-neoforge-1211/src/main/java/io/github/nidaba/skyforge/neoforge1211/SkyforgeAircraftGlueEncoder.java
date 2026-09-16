package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Lowers the accepted v0.10 adhesion proof tree to bounded Create Super Glue domains/commands. */
public final class SkyforgeAircraftGlueEncoder {
    public SkyforgeAircraftGlueEncodingIR encode(
            SkyforgeAircraftAssemblyFixtureIR fixture,
            String glueAssetId,
            SkyforgeAircraftGlueEncodingProfile profile) {
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(profile, "profile");
        if (!fixture.validation().passed() || !fixture.readiness().assemblyFixtureTopologyPassed()) {
            throw new IllegalArgumentException("refusing glue encoding from failed v0.10 topology");
        }
        if (!fixture.readiness().mainBodyAdhesionGraphResolved()) {
            throw new IllegalArgumentException("refusing glue encoding from unresolved v0.10 adhesion graph");
        }

        SkyforgeAircraftGlueEncodingProfile.Encoding encoding = profile.encoding();
        int maxDimension = encoding.maxSelectionDimensionBlocks();
        Set<AircraftBlockspaceIR.LatticePoint> mainCoordinates = Set.copyOf(fixture.mainBody().coordinates());
        Set<AircraftBlockspaceIR.LatticePoint> childCoordinates = Set.copyOf(fixture.nestedPropellerChild().coordinates());
        if (mainCoordinates.isEmpty()) throw new IllegalArgumentException("fixture has no main-body coordinates");

        TreeSet<SkyforgeAircraftAssemblyFixtureIR.Edge> treeEdges = new TreeSet<>(Comparator
                .comparing(SkyforgeAircraftAssemblyFixtureIR.Edge::a)
                .thenComparing(SkyforgeAircraftAssemblyFixtureIR.Edge::b));
        for (SkyforgeAircraftAssemblyFixtureIR.Edge edge : fixture.adhesionIntent().edges()) {
            if (!treeEdges.add(edge)) throw new IllegalArgumentException("duplicate adhesion edge: " + edge);
            if (manhattan(edge.a(), edge.b()) != 1) {
                throw new IllegalArgumentException("non-face-adjacent adhesion proof edge: " + edge);
            }
            if (!mainCoordinates.contains(edge.a()) || !mainCoordinates.contains(edge.b())) {
                throw new IllegalArgumentException("adhesion proof edge endpoint not on main body: " + edge);
            }
            if (childCoordinates.contains(edge.a()) || childCoordinates.contains(edge.b())) {
                throw new IllegalArgumentException("adhesion proof edge crosses nested-child boundary: " + edge);
            }
        }
        boolean treeIsSpanning = treeEdges.size() == fixture.adhesionIntent().edgeCount()
                && treeEdges.size() == Math.max(0, mainCoordinates.size() - 1);
        if (!treeIsSpanning) throw new IllegalArgumentException("v0.10 adhesion proof is not an N-1 main-body spanning tree");

        Set<SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge> forbidden = Set.copyOf(profile.forbiddenGlueEdges());
        ArrayList<SkyforgeAircraftGlueEncodingIR.GlueDomain> domainRecords = new ArrayList<>();
        ArrayList<String> commands = new ArrayList<>();
        Set<SkyforgeAircraftAssemblyFixtureIR.Edge> coveredEdges = new HashSet<>();
        boolean allWithinLimit = true;
        boolean noChildContainment = true;
        boolean forbiddenClear = true;

        for (SkyforgeAircraftGlueEncodingProfile.GlueDomain domain : profile.glueDomains()) {
            AircraftBlockspaceIR.LatticePoint min = boundsMin(domain.from(), domain.to());
            AircraftBlockspaceIR.LatticePoint max = boundsMax(domain.from(), domain.to());
            AircraftBlockspaceIR.LatticePoint size = new AircraftBlockspaceIR.LatticePoint(
                    max.x() - min.x() + 1,
                    max.y() - min.y() + 1,
                    max.z() - min.z() + 1);
            boolean withinLimit = size.x() <= maxDimension && size.y() <= maxDimension && size.z() <= maxDimension;
            allWithinLimit &= withinLimit;
            if (!withinLimit) {
                throw new IllegalArgumentException("glue domain " + domain.name() + " exceeds selection bound: " + size);
            }

            List<AircraftBlockspaceIR.LatticePoint> containedChildren = childCoordinates.stream()
                    .filter(point -> contains(min, max, point))
                    .sorted()
                    .toList();
            noChildContainment &= containedChildren.isEmpty();
            if (!containedChildren.isEmpty()) {
                throw new IllegalArgumentException("glue domain " + domain.name() + " contains nested propeller payload cells: " + containedChildren);
            }

            List<SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge> crossedForbidden = forbidden.stream()
                    .filter(edge -> contains(min, max, edge.a()) && contains(min, max, edge.b()))
                    .sorted(Comparator.comparing(SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge::a)
                            .thenComparing(SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge::b))
                    .toList();
            forbiddenClear &= crossedForbidden.isEmpty();
            if (!crossedForbidden.isEmpty()) {
                throw new IllegalArgumentException("glue domain " + domain.name() + " crosses forbidden dynamic boundary: " + crossedForbidden);
            }

            List<SkyforgeAircraftAssemblyFixtureIR.Edge> domainCovered = treeEdges.stream()
                    .filter(edge -> contains(min, max, edge.a()) && contains(min, max, edge.b()))
                    .toList();
            if (domainCovered.isEmpty()) {
                throw new IllegalArgumentException("glue domain " + domain.name() + " covers no accepted adhesion proof edge");
            }
            coveredEdges.addAll(domainCovered);
            String command = encoding.commandRoot() + " " + relativeCoordinate(domain.from()) + " " + relativeCoordinate(domain.to());
            commands.add(command);
            domainRecords.add(new SkyforgeAircraftGlueEncodingIR.GlueDomain(
                    domain.name(), domain.from(), domain.to(), command, min, max, size, domainCovered));
        }

        ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> uncovered = new ArrayList<>(treeEdges);
        uncovered.removeAll(coveredEdges);
        uncovered.sort(Comparator.comparing(SkyforgeAircraftAssemblyFixtureIR.Edge::a)
                .thenComparing(SkyforgeAircraftAssemblyFixtureIR.Edge::b));
        boolean allEdgesCovered = uncovered.isEmpty();
        if (!allEdgesCovered) {
            throw new IllegalArgumentException("bounded glue domains leave adhesion proof edges uncovered: " + uncovered);
        }

        SkyforgeAircraftAssemblyFixtureIR.PhysicsAssemblerPlacement assembler = fixture.physicsAssemblerPlacement();
        if (!"simulated:physics_assembler".equals(assembler.resourceId())) {
            throw new IllegalArgumentException("unexpected Physics Assembler resource");
        }
        if (!"ceiling".equals(assembler.blockState().get("face"))) {
            throw new IllegalArgumentException("v0.11 requires accepted face=ceiling assembler fixture");
        }
        StringBuilder assemblerCommand = new StringBuilder("setblock ")
                .append(relativeCoordinate(assembler.point()))
                .append(' ')
                .append(assembler.resourceId());
        if (!assembler.blockState().isEmpty()) {
            assemblerCommand.append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : new TreeMap<>(assembler.blockState()).entrySet()) {
                if (!first) assemblerCommand.append(',');
                assemblerCommand.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            assemblerCommand.append(']');
        }
        assemblerCommand.append(" replace");

        boolean sourceBacked = "Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span".equals(encoding.sourceContract());
        SkyforgeAircraftGlueEncodingIR.Checks checks = new SkyforgeAircraftGlueEncodingIR.Checks(
                commands.size() == domainRecords.size(),
                treeIsSpanning,
                allEdgesCovered,
                allWithinLimit,
                noChildContainment,
                forbiddenClear,
                assemblerCommand.toString().startsWith("setblock "),
                sourceBacked);
        boolean passed = checks.passed();

        List<SkyforgeAircraftGlueEncodingIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftGlueEncodingIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        ArrayList<String> mechanicalBlockers = new ArrayList<>();
        if (!passed) mechanicalBlockers.add("adhesion_application_encoding_failed");
        mechanicalBlockers.add("control_surface_child_body_topology_unresolved");

        return new SkyforgeAircraftGlueEncodingIR(
                SkyforgeAircraftGlueEncodingIR.SCHEMA_VERSION,
                glueAssetId,
                SkyforgeAircraftGlueEncodingIR.COMPILER_VERSION,
                fixture.sha256(),
                profile.profileId(),
                new SkyforgeAircraftGlueEncodingIR.EncodingPolicy(
                        encoding.policy(), encoding.commandRoot(), encoding.requiredPermissionLevel(),
                        maxDimension, false,
                        "remove_prior_fixture_glue_before_reapplying; do not accumulate duplicate SuperGlueEntity instances",
                        "retain the exact N-1 face-adjacent tree as the connectivity proof, then lower it to bounded runtime-backed Super Glue domains that cover every proof edge without enclosing dynamic child cells"),
                assemblerCommand.toString(),
                commands,
                domainRecords,
                new SkyforgeAircraftGlueEncodingIR.AdhesionProof(treeEdges.size(), coveredEdges.size(), uncovered),
                profile.forbiddenGlueEdges(),
                checks,
                obligations,
                new SkyforgeAircraftGlueEncodingIR.Metrics(
                        mainCoordinates.size(), childCoordinates.size(), treeEdges.size(), coveredEdges.size(),
                        domainRecords.size(), commands.size(), 1, obligations.size(), 0),
                new SkyforgeAircraftGlueEncodingIR.Readiness(
                        passed, passed, passed, false, false,
                        mechanicalBlockers,
                        List.of(
                                "exact_stack_glue_realization_unverified",
                                "physics_assembler_capture_unverified",
                                "nested_propeller_capture_unverified",
                                "pilot_occupancy_runtime_unverified",
                                "control_binding_runtime_unverified",
                                "aircraft_runtime_forces_unverified")),
                new SkyforgeAircraftGlueEncodingIR.Validation(
                        passed,
                        "source_and_runtime_backed_bounded_create_super_glue_domain_cover_of_the_exact_main_body_connectivity_proof",
                        List.of(
                                "that emitted SuperGlueEntity domains survive placement and assembly",
                                "that Physics Assembler transfers the exact intended primary Sable payload",
                                "that the Propeller Bearing subsequently re-forms its child contraption inside the Sable body",
                                "pilot occupancy on the assembled Sable body",
                                "control authority",
                                "runtime aerodynamic or propulsive force magnitude/sign",
                                "flight qualification")));
    }

    private static int manhattan(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
    private static AircraftBlockspaceIR.LatticePoint boundsMin(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        return new AircraftBlockspaceIR.LatticePoint(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
    }
    private static AircraftBlockspaceIR.LatticePoint boundsMax(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        return new AircraftBlockspaceIR.LatticePoint(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }
    private static boolean contains(AircraftBlockspaceIR.LatticePoint min, AircraftBlockspaceIR.LatticePoint max, AircraftBlockspaceIR.LatticePoint point) {
        return min.x() <= point.x() && point.x() <= max.x()
                && min.y() <= point.y() && point.y() <= max.y()
                && min.z() <= point.z() && point.z() <= max.z();
    }
    private static String relativeCoordinate(AircraftBlockspaceIR.LatticePoint point) {
        return relativeScalar(point.x()) + " " + relativeScalar(point.y()) + " " + relativeScalar(point.z());
    }
    private static String relativeScalar(int value) {
        return value == 0 ? "~" : "~" + value;
    }
}
