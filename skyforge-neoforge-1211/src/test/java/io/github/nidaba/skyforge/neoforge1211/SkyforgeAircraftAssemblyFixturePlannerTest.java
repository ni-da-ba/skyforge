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

final class SkyforgeAircraftAssemblyFixturePlannerTest {
    @Test
    void retainedFixtureIncludesAssemblerInMainBodyAndExcludesNestedPropeller() {
        SkyforgeAircraftProbeManifestIR manifest = manifest(List.of());
        SkyforgeAircraftAssemblyFixtureIR first = plan(manifest);
        SkyforgeAircraftAssemblyFixtureIR second = plan(manifest);

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.topologyChecks().passed()),
                () -> assertTrue(first.readiness().assemblyFixtureTopologyPassed()),
                () -> assertTrue(first.readiness().physicsAssemblerPlacementResolved()),
                () -> assertTrue(first.readiness().mainBodyAdhesionGraphResolved()),
                () -> assertFalse(first.readiness().adhesionApplicationEncodingReady()),
                () -> assertFalse(first.readiness().physicsAssemblyProbeReady()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(0, 1, 0), first.physicsAssemblerPlacement().point()),
                () -> assertEquals("simulated:physics_assembler", first.physicsAssemblerPlacement().resourceId()),
                () -> assertEquals("ceiling", first.physicsAssemblerPlacement().blockState().get("face")),
                () -> assertEquals(6, first.metrics().manifestPlacementCount()),
                () -> assertEquals(7, first.metrics().fixturePlacementCount()),
                () -> assertEquals(4, first.metrics().mainBodyManifestPlacementCount()),
                () -> assertEquals(5, first.metrics().mainBodyPlacementCount()),
                () -> assertEquals(2, first.metrics().nestedChildPlacementCount()),
                () -> assertEquals(4, first.metrics().adhesionIntentEdgeCount()),
                () -> assertEquals(0, first.metrics().mainBodyUnreachableCount()),
                () -> assertEquals(4, first.adhesionIntent().edgeCount()),
                () -> assertTrue(first.topologyChecks().physicsAssemblerIncludedInMovingMainBody()),
                () -> assertTrue(first.topologyChecks().physicsAssemblerHasAdhesionEdgeToSeed()),
                () -> assertTrue(first.topologyChecks().nestedChildExcludedFromMainAdhesionGraph()),
                () -> assertEquals(1, first.nestedPropellerChild().bearingToHubFaceAdjacency().size()),
                () -> assertEquals(
                        List.of("adhesion_application_encoding_unresolved", "control_surface_child_body_topology_unresolved"),
                        first.readiness().mechanicalBlockers()),
                () -> assertEquals(manifest.sha256(), first.sourceProbeManifestDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void disconnectedMainBodyIsCompileVisible() {
        SkyforgeAircraftProbeManifestIR.Placement isolated = placement(
                "airframe_structure", 20, 20, 20, "minecraft:spruce_planks", Map.of());
        SkyforgeAircraftAssemblyFixtureIR result = plan(manifest(List.of(isolated)));

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().mainBodyAdjacencyGraphConnected()),
                () -> assertEquals(1, result.metrics().mainBodyUnreachableCount()),
                () -> assertEquals(List.of("assembly_fixture_topology_failed"), result.readiness().mechanicalBlockers()));
    }

    @Test
    void unclassifiedPlacementKindIsRejected() {
        SkyforgeAircraftProbeManifestIR.Placement unknown = placement(
                "future_control_child", 2, 2, 0, "minecraft:spruce_planks", Map.of());
        assertThrows(IllegalArgumentException.class, () -> plan(manifest(List.of(unknown))));
    }

    @Test
    void assemblerCollisionIsRejected() {
        SkyforgeAircraftProbeManifestIR.Placement collision = placement(
                "airframe_structure", 0, 1, 0, "minecraft:spruce_planks", Map.of());
        assertThrows(IllegalArgumentException.class, () -> plan(manifest(List.of(collision))));
    }

    @Test
    void nonAdjacentNestedHubFailsClosed() {
        SkyforgeAircraftProbeManifestIR source = manifest(List.of());
        ArrayList<SkyforgeAircraftProbeManifestIR.Placement> changed = new ArrayList<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : source.placements()) {
            if ("propeller_hub".equals(placement.kind())) {
                changed.add(placement("propeller_hub", -4, 3, 0, placement.resourceId(), placement.blockState()));
            } else {
                changed.add(placement);
            }
        }
        SkyforgeAircraftAssemblyFixtureIR result = plan(copyManifest(source, changed));
        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().bearingFaceAdjacentToChildHub()),
                () -> assertEquals(List.of("assembly_fixture_topology_failed"), result.readiness().mechanicalBlockers()));
    }

    @Test
    void boundedProfileRejectsWrongAssemblerOffset() {
        SkyforgeAircraftAssemblyFixtureProfile retained = SkyforgeAircraftAssemblyFixtureProfile.retainedC11();
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftAssemblyFixtureProfile(
                "bad-offset",
                retained.mainBodyKinds(),
                retained.nestedChildKinds(),
                retained.seedCoordinate(),
                new SkyforgeAircraftAssemblyFixtureProfile.Translation(0, -2, 0),
                retained.physicsAssembler(),
                retained.runtimeObligations()));
    }

    private static SkyforgeAircraftAssemblyFixtureIR plan(SkyforgeAircraftProbeManifestIR manifest) {
        return new SkyforgeAircraftAssemblyFixturePlanner().plan(
                manifest,
                "skyforge.aircraft.guild_utility_monoplane.assembly_fixture.v1",
                SkyforgeAircraftAssemblyFixtureProfile.retainedC11());
    }

    private static SkyforgeAircraftProbeManifestIR manifest(List<SkyforgeAircraftProbeManifestIR.Placement> extras) {
        ArrayList<SkyforgeAircraftProbeManifestIR.Placement> placements = new ArrayList<>(List.of(
                placement("airframe_structure", 0, 2, 0, "minecraft:spruce_planks", Map.of()),
                placement("airframe_aerodynamic_surface", 1, 2, 0, "create:white_sail", Map.of("facing", "up")),
                placement("propeller_bearing", 0, 3, 0, "aeronautics:propeller_bearing", Map.of("facing", "west")),
                placement("pilot_occupancy_station", 1, 3, 0, "create:brown_seat", Map.of("waterlogged", "false")),
                placement("propeller_hub", -1, 3, 0, "minecraft:spruce_planks", Map.of()),
                placement("propeller_sail", -1, 4, 0, "simulated:white_symmetric_sail", Map.of("axis", "x"))));
        placements.addAll(extras);
        return new SkyforgeAircraftProbeManifestIR(
                SkyforgeAircraftProbeManifestIR.SCHEMA_VERSION,
                "skyforge.aircraft.test.probe_manifest.v1",
                SkyforgeAircraftProbeManifestIR.COMPILER_VERSION,
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                "test.profile",
                SkyforgeAircraftProbeManifestProfile.retainedC11().originContract(),
                placements,
                new SkyforgeAircraftProbeManifestIR.Bounds(
                        new AircraftBlockspaceIR.LatticePoint(-1, 2, 0),
                        new AircraftBlockspaceIR.LatticePoint(20, 20, 20),
                        new AircraftBlockspaceIR.LatticePoint(22, 19, 21)),
                List.of("aeronautics:propeller_bearing", "create:brown_seat", "create:white_sail", "minecraft:spruce_planks", "simulated:white_symmetric_sail"),
                Map.of(
                        "airframe_structure", 1 + (int) extras.stream().filter(p -> "airframe_structure".equals(p.kind())).count(),
                        "airframe_aerodynamic_surface", 1,
                        "propeller_bearing", 1,
                        "pilot_occupancy_station", 1,
                        "propeller_hub", 1,
                        "propeller_sail", 1),
                List.of(),
                new SkyforgeAircraftProbeManifestIR.ValidationChecks(true, true, true, true, true, true),
                new SkyforgeAircraftProbeManifestIR.Readiness(
                        true, true, false, false, false, List.of(),
                        List.of("physics_assembler_placement_unresolved", "airframe_adhesion_graph_unresolved", "control_surface_child_body_topology_unresolved"),
                        List.of("aircraft_runtime_obligations_unverified")),
                new SkyforgeAircraftProbeManifestIR.Validation(true, "test_manifest", List.of("runtime")));
    }

    private static SkyforgeAircraftProbeManifestIR copyManifest(
            SkyforgeAircraftProbeManifestIR source,
            List<SkyforgeAircraftProbeManifestIR.Placement> placements) {
        return new SkyforgeAircraftProbeManifestIR(
                source.schemaVersion(), source.assetId(), source.compilerVersion(),
                source.sourceTargetPreflightDigestSha256(), source.sourcePropulsionDigestSha256(),
                source.sourceTailLoweringDigestSha256(), source.sourcePilotStationDigestSha256(),
                source.profileId(), source.originContract(), placements, source.bounds(), source.resourceIds(),
                source.kindCounts(), source.duplicateCoordinates(), source.validationChecks(), source.readiness(), source.validation());
    }

    private static SkyforgeAircraftProbeManifestIR.Placement placement(
            String kind, int x, int y, int z, String resourceId, Map<String, String> state) {
        return new SkyforgeAircraftProbeManifestIR.Placement(
                kind,
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                resourceId,
                state,
                "test");
    }
}
