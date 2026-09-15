package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftGlueEncoderTest {
    @Test
    void boundedDomainCoverIsProbeReadyWithoutRuntimeClaims() {
        SkyforgeAircraftAssemblyFixtureIR fixture = fixture(defaultEdges(), defaultChildren());
        SkyforgeAircraftGlueEncodingIR first = encode(fixture, profile(domain("main", 0, 1, 0, 1, 3, 0), 24));
        SkyforgeAircraftGlueEncodingIR second = encode(fixture, profile(domain("main", 0, 1, 0, 1, 3, 0), 24));

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.checks().passed()),
                () -> assertEquals(5, first.metrics().mainBodyPlacementCount()),
                () -> assertEquals(4, first.metrics().adhesionIntentEdgeCount()),
                () -> assertEquals(4, first.metrics().coveredAdhesionIntentEdgeCount()),
                () -> assertEquals(1, first.metrics().glueDomainCount()),
                () -> assertEquals(1, first.metrics().glueCommandCount()),
                () -> assertEquals(
                        "setblock ~ ~1 ~ simulated:physics_assembler[face=ceiling,facing=north] replace",
                        first.physicsAssemblerCommand()),
                () -> assertEquals(List.of("create glue ~ ~1 ~ ~1 ~3 ~"), first.glueCommands()),
                () -> assertEquals(4, first.glueDomains().getFirst().coveredAdhesionEdgeCount()),
                () -> assertTrue(first.readiness().adhesionApplicationEncodingReady()),
                () -> assertTrue(first.readiness().physicsAssemblyProbeReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(List.of("control_surface_child_body_topology_unresolved"), first.readiness().remainingMechanicalBlockers()),
                () -> assertEquals(fixture.sha256(), first.sourceAssemblyFixtureDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void nonAdjacentProofEdgeIsRejected() {
        ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = new ArrayList<>(defaultEdges());
        edges.set(1, edge(0, 2, 0, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(edges, defaultChildren()),
                profile(domain("main", 0, 1, 0, 2, 3, 0), 24)));
    }

    @Test
    void nestedChildProofEndpointIsRejected() {
        ArrayList<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = new ArrayList<>(defaultEdges());
        edges.set(1, edge(0, 3, 0, -1, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(edges, defaultChildren()),
                profile(domain("main", -1, 1, 0, 1, 3, 0), 24)));
    }

    @Test
    void uncoveredProofEdgeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(defaultEdges(), defaultChildren()),
                profile(domain("partial", 0, 1, 0, 0, 2, 0), 24)));
    }

    @Test
    void domainExceedingSelectionLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(defaultEdges(), defaultChildren()),
                profile(domain("oversize", 0, 1, 0, 24, 3, 0), 24)));
    }

    @Test
    void domainContainingNestedChildIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(defaultEdges(), defaultChildren()),
                profile(domain("bad", -1, 1, 0, 1, 4, 0), 24)));
    }

    @Test
    void domainCrossingForbiddenDynamicBoundaryIsRejected() {
        List<AircraftBlockspaceIR.LatticePoint> childWithoutHub = List.of(point(-1, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> encode(
                fixture(defaultEdges(), childWithoutHub),
                profile(domain("bad", -1, 1, 0, 1, 3, 0), 24)));
    }

    private static SkyforgeAircraftGlueEncodingIR encode(
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingProfile profile) {
        return new SkyforgeAircraftGlueEncoder().encode(
                fixture,
                "skyforge.aircraft.guild_utility_monoplane.glue_encoding.v1",
                profile);
    }

    private static SkyforgeAircraftGlueEncodingProfile profile(
            SkyforgeAircraftGlueEncodingProfile.GlueDomain domain,
            int maxDimension) {
        return new SkyforgeAircraftGlueEncodingProfile(
                "glue-test",
                new SkyforgeAircraftGlueEncodingProfile.Encoding(
                        "bounded_super_glue_domain_cover_of_main_body_tree",
                        "create glue",
                        2,
                        maxDimension,
                        "Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span",
                        List.of("test evidence")),
                List.of(domain),
                List.of(new SkyforgeAircraftGlueEncodingProfile.ForbiddenGlueEdge(point(0, 3, 0), point(-1, 3, 0))),
                List.of(
                        obligation("create_glue_command_registry_probe"),
                        obligation("super_glue_entity_realization_probe"),
                        obligation("physics_assembler_capture_probe"),
                        obligation("glue_persistence_after_sublevel_move")));
    }

    private static SkyforgeAircraftGlueEncodingProfile.RuntimeObligation obligation(String id) {
        return new SkyforgeAircraftGlueEncodingProfile.RuntimeObligation(id, "test " + id);
    }

    private static SkyforgeAircraftGlueEncodingProfile.GlueDomain domain(
            String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new SkyforgeAircraftGlueEncodingProfile.GlueDomain(name, point(x1, y1, z1), point(x2, y2, z2));
    }

    private static SkyforgeAircraftAssemblyFixtureIR fixture(
            List<SkyforgeAircraftAssemblyFixtureIR.Edge> edges,
            List<AircraftBlockspaceIR.LatticePoint> childCoordinates) {
        List<AircraftBlockspaceIR.LatticePoint> mainCoordinates = List.of(
                point(0, 1, 0), point(0, 2, 0), point(1, 2, 0), point(0, 3, 0), point(1, 3, 0));
        SkyforgeAircraftAssemblyFixtureIR.TopologyChecks checks = new SkyforgeAircraftAssemblyFixtureIR.TopologyChecks(
                true, true, true, true, true, true, true, true, true, true, true);
        return new SkyforgeAircraftAssemblyFixtureIR(
                SkyforgeAircraftAssemblyFixtureIR.SCHEMA_VERSION,
                "test.assembly_fixture.v1",
                SkyforgeAircraftAssemblyFixtureIR.COMPILER_VERSION,
                "a".repeat(64),
                "fixture-test",
                new SkyforgeAircraftAssemblyFixtureIR.PhysicsAssemblerPlacement(
                        point(0, 1, 0),
                        "simulated:physics_assembler",
                        Map.of("face", "ceiling", "facing", "north"),
                        point(0, 2, 0),
                        "required",
                        "test source contract"),
                new SkyforgeAircraftAssemblyFixtureIR.MainBody(
                        mainCoordinates.size(), mainCoordinates.size() - 1, 1, mainCoordinates,
                        point(0, 2, 0), mainCoordinates.size(), List.of()),
                new SkyforgeAircraftAssemblyFixtureIR.NestedPropellerChild(
                        childCoordinates.size(), childCoordinates, List.of()),
                new SkyforgeAircraftAssemblyFixtureIR.AdhesionIntent(
                        "face_adjacent_spanning_tree_over_moving_main_body_including_physics_assembler",
                        edges.size(), edges,
                        "logical_graph_only_super_glue_entity_encoding_unresolved"),
                checks,
                List.of(),
                new SkyforgeAircraftAssemblyFixtureIR.Metrics(
                        mainCoordinates.size() - 1 + childCoordinates.size(),
                        mainCoordinates.size() + childCoordinates.size(),
                        mainCoordinates.size() - 1,
                        mainCoordinates.size(),
                        childCoordinates.size(),
                        edges.size(),
                        0, 0, 0),
                new SkyforgeAircraftAssemblyFixtureIR.Readiness(
                        true, true, true, false, false, false, false,
                        List.of("adhesion_application_encoding_unresolved", "control_surface_child_body_topology_unresolved")),
                new SkyforgeAircraftAssemblyFixtureIR.Validation(true, "test fixture", List.of("runtime")));
    }

    private static List<SkyforgeAircraftAssemblyFixtureIR.Edge> defaultEdges() {
        return List.of(
                edge(0, 1, 0, 0, 2, 0),
                edge(0, 2, 0, 0, 3, 0),
                edge(0, 2, 0, 1, 2, 0),
                edge(0, 3, 0, 1, 3, 0));
    }

    private static List<AircraftBlockspaceIR.LatticePoint> defaultChildren() {
        return List.of(point(-1, 3, 0), point(-1, 4, 0));
    }

    private static SkyforgeAircraftAssemblyFixtureIR.Edge edge(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new SkyforgeAircraftAssemblyFixtureIR.Edge(point(x1, y1, z1), point(x2, y2, z2));
    }
    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
