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
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftPowertrainLowererTest {
    @Test
    void retainedPowertrainPatchIsDeterministicAndRuntimeUnqualified() {
        Chain chain = chain("powertrain.manifest.v1", "minecraft:spruce_planks");
        SkyforgeAircraftPowertrainIR first = lower(chain, SkyforgeAircraftPowertrainProfile.retainedC11());
        SkyforgeAircraftPowertrainIR second = lower(chain, SkyforgeAircraftPowertrainProfile.retainedC11());

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.topologyChecks().passed()),
                () -> assertTrue(first.glueChecks().passed()),
                () -> assertTrue(first.readiness().powertrainStaticTopologyPassed()),
                () -> assertTrue(first.readiness().powertrainPlacementPatchReady()),
                () -> assertTrue(first.readiness().governor128RuntimeProbeReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(32, first.governor().sourceRpmPerPortableEngine()),
                () -> assertEquals(128, first.governor().firstAcceptedTargetRpm()),
                () -> assertEquals(List.of(160, 192, 224, 256), first.governor().higherRpmPoints()),
                () -> assertEquals(1, first.replacementCount()),
                () -> assertEquals(4, first.additionCount()),
                () -> assertEquals(7, first.metrics().inputManifestPlacementCount()),
                () -> assertEquals(11, first.metrics().resultingManifestPlacementCount()),
                () -> assertEquals(6, first.metrics().priorMovingMainBodyPlacementCount()),
                () -> assertEquals(10, first.metrics().resultingMovingMainBodyPlacementCount()),
                () -> assertEquals(2, first.metrics().nestedPropellerChildPlacementCount()),
                () -> assertEquals(12, first.metrics().expectedPrimarySableTransferCount()),
                () -> assertEquals(chain.manifest().sha256(), first.sourceProbeManifestDigestSha256()),
                () -> assertEquals(chain.fixture().sha256(), first.sourceAssemblyFixtureDigestSha256()),
                () -> assertEquals(chain.glue().sha256(), first.sourceGlueEncodingDigestSha256()),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("governor_128_rpm_unverified")),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("kinetic_stress_margin_unverified")),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void replacementSourceMismatchIsRejected() {
        Chain chain = chain("powertrain.manifest.replacement", "minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> lower(chain, SkyforgeAircraftPowertrainProfile.retainedC11()));
    }

    @Test
    void childBoundaryCollisionIsRejected() {
        Chain chain = chain("powertrain.manifest.child", "minecraft:spruce_planks");
        SkyforgeAircraftPowertrainProfile retained = SkyforgeAircraftPowertrainProfile.retainedC11();
        ArrayList<SkyforgeAircraftPowertrainProfile.Placement> placements = new ArrayList<>(retained.placements());
        SkyforgeAircraftPowertrainProfile.Placement engine = placements.getFirst();
        placements.set(0, new SkyforgeAircraftPowertrainProfile.Placement(
                engine.role(), engine.mode(), point(-1, 3, 0), engine.resourceId(), engine.blockState(), engine.replaces(), engine.sourceContract()));
        assertThrows(IllegalArgumentException.class, () -> lower(chain, copyProfile(retained, placements, retained.powerplantGlueDomain())));
    }

    @Test
    void invalidGovernorPointIsRejectedByProfile() {
        SkyforgeAircraftPowertrainProfile retained = SkyforgeAircraftPowertrainProfile.retainedC11();
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftPowertrainProfile(
                "invalid-governor",
                retained.portableEngineSourceRpm(),
                160,
                retained.laterGovernorRpmPoints(),
                retained.existingPropellerBearingCoordinate(),
                retained.placements(),
                retained.powerplantGlueDomain(),
                retained.sourceEvidence(),
                retained.runtimeObligations()));
    }

    @Test
    void separatedPowerplantGlueIsRejected() {
        Chain chain = chain("powertrain.manifest.glue", "minecraft:spruce_planks");
        SkyforgeAircraftPowertrainProfile retained = SkyforgeAircraftPowertrainProfile.retainedC11();
        SkyforgeAircraftPowertrainProfile.GlueDomain separated = new SkyforgeAircraftPowertrainProfile.GlueDomain(
                "separated", point(20, 20, 20), point(22, 22, 22));
        assertThrows(IllegalArgumentException.class, () -> lower(chain, copyProfile(retained, retained.placements(), separated)));
    }

    @Test
    void mismatchedManifestFixtureProvenanceIsRejected() {
        Chain first = chain("powertrain.manifest.first", "minecraft:spruce_planks");
        SkyforgeAircraftProbeManifestIR second = manifest("powertrain.manifest.second", "minecraft:spruce_planks");
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftPowertrainLowerer().lower(
                second,
                first.fixture(),
                first.glue(),
                "skyforge.aircraft.guild_utility_monoplane.powertrain.bad",
                SkyforgeAircraftPowertrainProfile.retainedC11()));
    }

    private static SkyforgeAircraftPowertrainIR lower(Chain chain, SkyforgeAircraftPowertrainProfile profile) {
        return new SkyforgeAircraftPowertrainLowerer().lower(
                chain.manifest(), chain.fixture(), chain.glue(),
                "skyforge.aircraft.guild_utility_monoplane.powertrain.v1", profile);
    }

    private static SkyforgeAircraftPowertrainProfile copyProfile(
            SkyforgeAircraftPowertrainProfile source,
            List<SkyforgeAircraftPowertrainProfile.Placement> placements,
            SkyforgeAircraftPowertrainProfile.GlueDomain glueDomain) {
        return new SkyforgeAircraftPowertrainProfile(
                source.profileId(), source.portableEngineSourceRpm(), source.governorTargetRpm(),
                source.laterGovernorRpmPoints(), source.existingPropellerBearingCoordinate(), placements,
                glueDomain, source.sourceEvidence(), source.runtimeObligations());
    }

    private static Chain chain(String assetId, String governorSourceResource) {
        SkyforgeAircraftProbeManifestIR manifest = manifest(assetId, governorSourceResource);
        SkyforgeAircraftAssemblyFixtureIR fixture = fixture(manifest);
        SkyforgeAircraftGlueEncodingIR glue = glue(fixture);
        return new Chain(manifest, fixture, glue);
    }

    private static SkyforgeAircraftProbeManifestIR manifest(String assetId, String governorSourceResource) {
        List<SkyforgeAircraftProbeManifestIR.Placement> placements = List.of(
                placement("airframe_structure", 0, 2, 0, "minecraft:spruce_planks"),
                placement("airframe_structure", 1, 2, 0, "minecraft:spruce_planks"),
                placement("airframe_structure", 2, 2, 0, governorSourceResource),
                placement("pilot_occupancy_station", 3, 2, 0, "create:brown_seat"),
                placement("propeller_bearing", 0, 3, 0, "aeronautics:propeller_bearing"),
                placement("propeller_hub", -1, 3, 0, "minecraft:spruce_planks"),
                placement("propeller_sail", -1, 4, 0, "simulated:white_symmetric_sail"));
        SkyforgeAircraftProbeManifestIR.ValidationChecks checks = new SkyforgeAircraftProbeManifestIR.ValidationChecks(
                true, true, true, true, true, true);
        return new SkyforgeAircraftProbeManifestIR(
                SkyforgeAircraftProbeManifestIR.SCHEMA_VERSION,
                assetId,
                SkyforgeAircraftProbeManifestIR.COMPILER_VERSION,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64),
                "probe-test",
                SkyforgeAircraftProbeManifestProfile.retainedC11().originContract(),
                placements,
                new SkyforgeAircraftProbeManifestIR.Bounds(point(-1, 2, 0), point(3, 4, 0), point(5, 3, 1)),
                List.of("aeronautics:propeller_bearing", "create:brown_seat", "minecraft:spruce_planks", governorSourceResource, "simulated:white_symmetric_sail"),
                Map.of(
                        "airframe_structure", 3,
                        "pilot_occupancy_station", 1,
                        "propeller_bearing", 1,
                        "propeller_hub", 1,
                        "propeller_sail", 1),
                List.of(),
                checks,
                new SkyforgeAircraftProbeManifestIR.Readiness(
                        true, true, false, false, false, List.of(),
                        List.of("physics_assembler_placement_unresolved", "airframe_adhesion_graph_unresolved"),
                        List.of("runtime_unverified")),
                new SkyforgeAircraftProbeManifestIR.Validation(true, "test manifest", List.of("runtime")));
    }

    private static SkyforgeAircraftProbeManifestIR.Placement placement(String kind, int x, int y, int z, String resource) {
        return new SkyforgeAircraftProbeManifestIR.Placement(kind, point(x, y, z), resource, Map.of(), "test");
    }

    private static SkyforgeAircraftAssemblyFixtureIR fixture(SkyforgeAircraftProbeManifestIR manifest) {
        List<AircraftBlockspaceIR.LatticePoint> main = List.of(
                point(0, 1, 0), point(0, 2, 0), point(0, 3, 0), point(1, 2, 0), point(2, 2, 0), point(3, 2, 0));
        List<AircraftBlockspaceIR.LatticePoint> child = List.of(point(-1, 3, 0), point(-1, 4, 0));
        List<SkyforgeAircraftAssemblyFixtureIR.Edge> edges = List.of(
                edge(0, 1, 0, 0, 2, 0),
                edge(0, 2, 0, 0, 3, 0),
                edge(0, 2, 0, 1, 2, 0),
                edge(1, 2, 0, 2, 2, 0),
                edge(2, 2, 0, 3, 2, 0));
        return new SkyforgeAircraftAssemblyFixtureIR(
                SkyforgeAircraftAssemblyFixtureIR.SCHEMA_VERSION,
                "powertrain.fixture.v1",
                SkyforgeAircraftAssemblyFixtureIR.COMPILER_VERSION,
                manifest.sha256(),
                "fixture-test",
                new SkyforgeAircraftAssemblyFixtureIR.PhysicsAssemblerPlacement(
                        point(0, 1, 0), "simulated:physics_assembler",
                        Map.of("face", "ceiling", "facing", "north"), point(0, 2, 0), "required", "test"),
                new SkyforgeAircraftAssemblyFixtureIR.MainBody(6, 5, 1, main, point(0, 2, 0), 6, List.of()),
                new SkyforgeAircraftAssemblyFixtureIR.NestedPropellerChild(
                        2, child, List.of(new SkyforgeAircraftAssemblyFixtureIR.FaceAdjacency(point(0, 3, 0), point(-1, 3, 0)))),
                new SkyforgeAircraftAssemblyFixtureIR.AdhesionIntent(
                        "face_adjacent_spanning_tree_over_moving_main_body_including_physics_assembler",
                        5, edges, "logical_graph_only_super_glue_entity_encoding_unresolved"),
                new SkyforgeAircraftAssemblyFixtureIR.TopologyChecks(true, true, true, true, true, true, true, true, true, true, true),
                List.of(),
                new SkyforgeAircraftAssemblyFixtureIR.Metrics(7, 8, 5, 6, 2, 5, 0, 0, 0),
                new SkyforgeAircraftAssemblyFixtureIR.Readiness(
                        true, true, true, false, false, false, false,
                        List.of("adhesion_application_encoding_unresolved", "control_surface_child_body_topology_unresolved")),
                new SkyforgeAircraftAssemblyFixtureIR.Validation(true, "test fixture", List.of("runtime")));
    }

    private static SkyforgeAircraftGlueEncodingIR glue(SkyforgeAircraftAssemblyFixtureIR fixture) {
        SkyforgeAircraftAssemblyFixtureIR.Edge[] edges = fixture.adhesionIntent().edges().toArray(SkyforgeAircraftAssemblyFixtureIR.Edge[]::new);
        SkyforgeAircraftGlueEncodingIR.GlueDomain domain = new SkyforgeAircraftGlueEncodingIR.GlueDomain(
                "main", point(0, 1, -1), point(3, 3, 1), "create glue ~ ~1 ~-1 ~3 ~3 ~1",
                point(0, 1, -1), point(3, 3, 1), point(4, 3, 3), List.of(edges));
        return new SkyforgeAircraftGlueEncodingIR(
                SkyforgeAircraftGlueEncodingIR.SCHEMA_VERSION,
                "powertrain.glue.v1",
                SkyforgeAircraftGlueEncodingIR.COMPILER_VERSION,
                fixture.sha256(),
                "glue-test",
                new SkyforgeAircraftGlueEncodingIR.EncodingPolicy(
                        "bounded_super_glue_domain_cover_of_main_body_tree", "create glue", 2, 24, false, "test", "test"),
                "setblock ~ ~1 ~ simulated:physics_assembler[face=ceiling,facing=north] replace",
                List.of(domain.command()),
                List.of(domain),
                new SkyforgeAircraftGlueEncodingIR.AdhesionProof(5, 5, List.of()),
                List.of(),
                new SkyforgeAircraftGlueEncodingIR.Checks(true, true, true, true, true, true, true, true),
                List.of(),
                new SkyforgeAircraftGlueEncodingIR.Metrics(6, 2, 5, 5, 1, 1, 1, 0, 0),
                new SkyforgeAircraftGlueEncodingIR.Readiness(
                        true, true, true, false, false,
                        List.of("control_surface_child_body_topology_unresolved"),
                        List.of("runtime_unverified")),
                new SkyforgeAircraftGlueEncodingIR.Validation(true, "test glue", List.of("runtime")));
    }

    private static SkyforgeAircraftAssemblyFixtureIR.Edge edge(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new SkyforgeAircraftAssemblyFixtureIR.Edge(point(x1, y1, z1), point(x2, y2, z2));
    }
    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }

    private record Chain(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue) {}
}
