package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftSurfaceStateResolverTest {
    @Test
    void retainedProfileResolvesHorizontalAndVerticalSurfaceFacingsWithoutClaimingRuntimeForce() {
        AircraftAssemblyPlanIR assembly = normalAssembly();
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.v1");
        SkyforgeAircraftPropulsionIR propulsion = propulsion(assembly, preflight);
        SkyforgeAircraftSurfaceStateIR first = resolve(preflight, propulsion, SkyforgeAircraftSurfaceStateProfile.retainedC11());
        SkyforgeAircraftSurfaceStateIR second = resolve(preflight, propulsion, SkyforgeAircraftSurfaceStateProfile.retainedC11());

        Map<AircraftBlockspaceIR.Role, String> resolvedByRole = first.resolvedPlacements().stream()
                .collect(java.util.stream.Collectors.toMap(
                        placement -> placement.roles().getFirst(),
                        placement -> placement.blockState().get("facing")));
        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.readiness().surfaceStateResolutionComplete()),
                () -> assertEquals(3, first.metrics().providerPlacementCount()),
                () -> assertEquals(3, first.metrics().stateResolvedCount()),
                () -> assertEquals(0, first.metrics().stateConflictCount()),
                () -> assertEquals(0, first.metrics().remainingUnresolvedStateOrResourceCount()),
                () -> assertEquals("up", resolvedByRole.get(AircraftBlockspaceIR.Role.WING_SURFACE_INTENT)),
                () -> assertEquals("up", resolvedByRole.get(AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT)),
                () -> assertEquals("south", resolvedByRole.get(AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT)),
                () -> assertFalse(first.readiness().staticBlockers().contains("unresolved_blockstate_or_resource_rules")),
                () -> assertFalse(first.readiness().staticBlockers().contains("unresolved_surface_state_conflicts")),
                () -> assertTrue(first.readiness().staticBlockers().contains("unresolved_required_station_providers")),
                () -> assertFalse(first.readiness().probeSchematicEmissionReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("propulsion_runtime_obligations_unverified")),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("aircraft_runtime_obligations_unverified")),
                () -> assertEquals(preflight.sha256(), first.sourceTargetPreflightDigestSha256()),
                () -> assertEquals(propulsion.sha256(), first.sourcePropulsionDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void coLocatedHorizontalAndVerticalSurfacesProduceCompileVisibleConflict() {
        AircraftAssemblyPlanIR assembly = assemblyWithSites(List.of(
                surfaceSite(3, 1, 0, AircraftBlockspaceIR.Role.WING_SURFACE_INTENT),
                new AircraftAssemblyPlanIR.Site(
                        new AircraftBlockspaceIR.LatticePoint(5, 1, 0),
                        List.of(
                                AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT,
                                AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                        List.of("aerodynamic_lift_surface", "rigid_physics_member"))));
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.conflict");
        SkyforgeAircraftSurfaceStateIR result = resolve(
                preflight, propulsion(assembly, preflight), SkyforgeAircraftSurfaceStateProfile.retainedC11());

        assertAll(
                () -> assertFalse(result.readiness().surfaceStateResolutionComplete()),
                () -> assertEquals(1, result.metrics().stateResolvedCount()),
                () -> assertEquals(1, result.metrics().stateConflictCount()),
                () -> assertEquals(1, result.metrics().remainingUnresolvedStateOrResourceCount()),
                () -> assertEquals(List.of("south", "up"), result.conflicts().getFirst().demandedStates()),
                () -> assertEquals(
                        "one_lattice_coordinate_cannot_realize_multiple_surface_orientations",
                        result.conflicts().getFirst().reason()),
                () -> assertTrue(result.readiness().staticBlockers().contains("unresolved_surface_state_conflicts")),
                () -> assertFalse(result.readiness().probeSchematicEmissionReady()));
    }

    @Test
    void aerodynamicProviderWithoutDeclaredSurfaceRoleStateFailsClosedAsConflict() {
        AircraftAssemblyPlanIR assembly = assemblyWithSites(List.of(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(4, 1, 0),
                List.of(AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                List.of("aerodynamic_lift_surface", "rigid_physics_member"))));
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.no-role-state");
        SkyforgeAircraftSurfaceStateIR result = resolve(
                preflight, propulsion(assembly, preflight), SkyforgeAircraftSurfaceStateProfile.retainedC11());

        assertAll(
                () -> assertEquals(0, result.metrics().stateResolvedCount()),
                () -> assertEquals(1, result.metrics().stateConflictCount()),
                () -> assertTrue(result.conflicts().getFirst().demandedStates().isEmpty()),
                () -> assertEquals(
                        "aerodynamic_provider_has_no_declared_surface_role_state",
                        result.conflicts().getFirst().reason()),
                () -> assertTrue(result.readiness().staticBlockers().contains("unresolved_surface_state_conflicts")));
    }

    @Test
    void profileRejectsRoleStateOutsideDeclaredLegalStates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAircraftSurfaceStateProfile(
                        "invalid-profile",
                        SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                        "create_white_sail_lift_v1",
                        "facing",
                        Set.of("up", "south"),
                        Map.of(AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT, "east")));
    }

    @Test
    void mismatchedTargetPreflightProvenanceIsRejected() {
        AircraftAssemblyPlanIR assembly = normalAssembly();
        SkyforgeAircraftTargetPreflightIR firstPreflight = preflight(assembly, "target.first");
        SkyforgeAircraftTargetPreflightIR secondPreflight = preflight(assembly, "target.second");
        SkyforgeAircraftPropulsionIR propulsion = propulsion(assembly, firstPreflight);

        assertThrows(
                IllegalArgumentException.class,
                () -> resolve(secondPreflight, propulsion, SkyforgeAircraftSurfaceStateProfile.retainedC11()));
    }

    private static SkyforgeAircraftSurfaceStateIR resolve(
            SkyforgeAircraftTargetPreflightIR preflight,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftSurfaceStateProfile profile) {
        return new SkyforgeAircraftSurfaceStateResolver().resolve(
                preflight,
                propulsion,
                "skyforge.aircraft.guild_utility_monoplane.surface_state.v1",
                profile);
    }

    private static SkyforgeAircraftPropulsionIR propulsion(
            AircraftAssemblyPlanIR assembly, SkyforgeAircraftTargetPreflightIR preflight) {
        return new SkyforgeAircraftPropulsionRealizer().realize(
                assembly,
                preflight,
                "skyforge.aircraft.guild_utility_monoplane.propulsion.v1",
                SkyforgeAircraftPropulsionProfile.retainedC11());
    }

    private static SkyforgeAircraftTargetPreflightIR preflight(
            AircraftAssemblyPlanIR assembly, String targetAssetId) {
        return new SkyforgeAircraftTargetPreflight().lower(
                assembly, targetAssetId, SkyforgeAircraftTargetProfile.retainedC11());
    }

    private static AircraftAssemblyPlanIR normalAssembly() {
        return assemblyWithSites(List.of(
                surfaceSite(3, 1, 0, AircraftBlockspaceIR.Role.WING_SURFACE_INTENT),
                surfaceSite(5, 1, 0, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT),
                surfaceSite(6, 2, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT)));
    }

    private static AircraftAssemblyPlanIR assemblyWithSites(List<AircraftAssemblyPlanIR.Site> surfaceSites) {
        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>();
        sites.add(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(8, 0, 0),
                List.of(AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                AircraftBlockspaceIR.Role.FUSELAGE_SPINE.capabilities()));
        sites.addAll(surfaceSites);
        return new AircraftAssemblyPlanIR(
                AircraftAssemblyPlanIR.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.assembly.v1",
                "skyforge.aircraft.guild_utility_monoplane.blockspace.v1",
                "a".repeat(64),
                AircraftAssemblyPlanIR.COMPILER_VERSION,
                new AircraftBlockspaceIR.CoordinateSystem(
                        "nose_to_tail", "up", "starboard_positive", "integer_lattice", 2.0),
                sites,
                List.of(
                        station(AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS, 0, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.CG_REFERENCE, 8, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, 10, 1, 0),
                        station(AircraftBlockspaceIR.AnchorType.CARGO_STATION, 12, 1, 0)),
                new AircraftAssemblyPlanIR.Metrics(
                        sites.size(),
                        sites.size(),
                        0,
                        (int) sites.stream().filter(site -> site.roles().size() > 1).count(),
                        true,
                        true),
                new AircraftAssemblyPlanIR.Validation(
                        true,
                        "coordinate_unique_semantic_assembly_only",
                        List.of("target resource selection", "runtime assembly")));
    }

    private static AircraftAssemblyPlanIR.Site surfaceSite(
            int x, int y, int z, AircraftBlockspaceIR.Role role) {
        return new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                List.of(role),
                role.capabilities());
    }

    private static AircraftAssemblyPlanIR.Station station(
            AircraftBlockspaceIR.AnchorType type, int x, int y, int z) {
        return new AircraftAssemblyPlanIR.Station(
                type,
                new AircraftBlockspaceIR.ContinuousPoint(x / 2.0, y / 2.0, z / 2.0),
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                type.capabilities(),
                "anchor_requirement_not_occupied_site");
    }
}
