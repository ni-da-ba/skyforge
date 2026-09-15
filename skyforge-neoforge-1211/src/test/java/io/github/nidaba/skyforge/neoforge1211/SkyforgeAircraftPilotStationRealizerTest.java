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
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftPilotStationRealizerTest {
    @Test
    void retainedPilotSeatClearsOnlyTheRequiredStationStaticBlocker() {
        Fixture fixture = fixture(false, true, "target.pilot.v1");
        SkyforgeAircraftPilotStationIR first = realize(fixture);
        SkyforgeAircraftPilotStationIR second = realize(fixture);

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.topologyChecks().passed()),
                () -> assertTrue(first.readiness().pilotStationStaticPlacementPassed()),
                () -> assertTrue(first.readiness().probeSchematicEmissionReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(8, 0, 0), first.semanticStation().anchorLattice()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(8, 1, 0), first.placement().point()),
                () -> assertEquals("create:brown_seat", first.placement().resourceId()),
                () -> assertEquals(Map.of("waterlogged", "false"), first.placement().blockState()),
                () -> assertEquals(List.of("unresolved_required_station_providers"), first.readiness().resolvedUpstreamBlockers()),
                () -> assertTrue(first.readiness().staticBlockers().isEmpty()),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("pilot_occupancy_runtime_unverified")),
                () -> assertTrue(first.readiness().runtimeBlockers().contains("control_binding_runtime_unverified")),
                () -> assertEquals(2, first.metrics().runtimeObligationCount()),
                () -> assertEquals(0, first.metrics().runtimeObligationVerifiedCount()),
                () -> assertEquals(fixture.preflight().sha256(), first.sourceTargetPreflightDigestSha256()),
                () -> assertEquals(fixture.propulsion().sha256(), first.sourcePropulsionDigestSha256()),
                () -> assertEquals(fixture.tail().sha256(), first.sourceTailLoweringDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void occupiedSeatCoordinateFailsClosedAndDoesNotResolveStationBlocker() {
        Fixture fixture = fixture(true, true, "target.pilot.occupied");
        SkyforgeAircraftPilotStationIR result = realize(fixture);

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().seatCoordinateFree()),
                () -> assertTrue(result.readiness().staticBlockers().contains("pilot_station_static_placement_failed")),
                () -> assertTrue(result.readiness().staticBlockers().contains("unresolved_required_station_providers")),
                () -> assertTrue(result.readiness().resolvedUpstreamBlockers().isEmpty()),
                () -> assertFalse(result.readiness().probeSchematicEmissionReady()));
    }

    @Test
    void nonstructuralPilotAnchorFailsClosed() {
        Fixture fixture = fixture(false, false, "target.pilot.nonstructural");
        SkyforgeAircraftPilotStationIR result = realize(fixture);

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().pilotAnchorIsStructuralAirframeSite()),
                () -> assertFalse(result.topologyChecks().seatSupportedByPilotAnchor()),
                () -> assertTrue(result.readiness().staticBlockers().contains("pilot_station_static_placement_failed")));
    }

    @Test
    void boundedProfileRejectsWrongOffsetOrSeatState() {
        SkyforgeAircraftPilotStationProfile retained = SkyforgeAircraftPilotStationProfile.retainedC11();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftPilotStationProfile(
                        "bad-offset", retained.targetProfileId(), retained.airframeAerodynamicProviderId(), retained.stationType(),
                        new SkyforgeAircraftPilotStationProfile.Offset(0, 2, 0), retained.seatResourceId(), retained.seatBlockState(),
                        retained.evidenceLevel(), retained.controlBindingContract(), retained.runtimeObligations())),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftPilotStationProfile(
                        "bad-state", retained.targetProfileId(), retained.airframeAerodynamicProviderId(), retained.stationType(),
                        retained.installationOffsetBlocks(), retained.seatResourceId(), Map.of("waterlogged", "true"),
                        retained.evidenceLevel(), retained.controlBindingContract(), retained.runtimeObligations())));
    }

    @Test
    void mismatchedTargetProvenanceIsRejected() {
        Fixture first = fixture(false, true, "target.pilot.first");
        SkyforgeAircraftTargetPreflightIR second = preflight(first.assembly(), "target.pilot.second");
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftPilotStationRealizer().realize(
                second,
                first.propulsion(),
                first.tail(),
                "skyforge.aircraft.guild_utility_monoplane.pilot_station.bad",
                SkyforgeAircraftPilotStationProfile.retainedC11()));
    }

    private static SkyforgeAircraftPilotStationIR realize(Fixture fixture) {
        return new SkyforgeAircraftPilotStationRealizer().realize(
                fixture.preflight(),
                fixture.propulsion(),
                fixture.tail(),
                "skyforge.aircraft.guild_utility_monoplane.pilot_station.v1",
                SkyforgeAircraftPilotStationProfile.retainedC11());
    }

    private static Fixture fixture(boolean seatCollision, boolean pilotAnchorStructural, String targetAssetId) {
        AircraftAssemblyPlanIR assembly = assembly(seatCollision, pilotAnchorStructural);
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, targetAssetId);
        SkyforgeAircraftPropulsionIR propulsion = new SkyforgeAircraftPropulsionRealizer().realize(
                assembly, preflight, "skyforge.aircraft.guild_utility_monoplane.propulsion.v1", SkyforgeAircraftPropulsionProfile.retainedC11());
        SkyforgeAircraftSurfaceStateIR surface = new SkyforgeAircraftSurfaceStateResolver().resolve(
                preflight, propulsion, "skyforge.aircraft.guild_utility_monoplane.surface_state.v1", SkyforgeAircraftSurfaceStateProfile.retainedC11());
        SkyforgeAircraftTailLoweringIR tail = new SkyforgeAircraftTailJunctionLowerer().lower(
                preflight, surface, "skyforge.aircraft.guild_utility_monoplane.tail_lowering.v1", SkyforgeAircraftTailLoweringProfile.retainedC11());
        return new Fixture(assembly, preflight, propulsion, tail);
    }

    private static SkyforgeAircraftTargetPreflightIR preflight(AircraftAssemblyPlanIR assembly, String assetId) {
        return new SkyforgeAircraftTargetPreflight().lower(assembly, assetId, SkyforgeAircraftTargetProfile.retainedC11());
    }

    private static AircraftAssemblyPlanIR assembly(boolean seatCollision, boolean pilotAnchorStructural) {
        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>();
        sites.add(site(8, 0, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        if (seatCollision) sites.add(site(8, 1, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        sites.add(site(5, 0, -1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        sites.add(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(5, 0, 0),
                List.of(AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                List.of("aerodynamic_lift_surface", "rigid_physics_member")));
        sites.add(site(5, 0, 1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        sites.add(site(5, 1, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
        sites.add(site(5, 2, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
        int pilotX = pilotAnchorStructural ? 8 : 9;
        return new AircraftAssemblyPlanIR(
                AircraftAssemblyPlanIR.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.assembly.v1",
                "skyforge.aircraft.guild_utility_monoplane.blockspace.v1",
                "a".repeat(64),
                AircraftAssemblyPlanIR.COMPILER_VERSION,
                new AircraftBlockspaceIR.CoordinateSystem("nose_to_tail", "up", "starboard_positive", "integer_lattice", 2.0),
                sites,
                List.of(
                        station(AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS, 0, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.CG_REFERENCE, 8, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, pilotX, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.CARGO_STATION, 10, 0, 0)),
                new AircraftAssemblyPlanIR.Metrics(sites.size(), sites.size(), 0, 1, true, true),
                new AircraftAssemblyPlanIR.Validation(true, "coordinate_unique_semantic_assembly_only", List.of("target resource selection", "runtime assembly")));
    }

    private static AircraftAssemblyPlanIR.Site site(int x, int y, int z, AircraftBlockspaceIR.Role role) {
        return new AircraftAssemblyPlanIR.Site(new AircraftBlockspaceIR.LatticePoint(x, y, z), List.of(role), role.capabilities());
    }

    private static AircraftAssemblyPlanIR.Station station(AircraftBlockspaceIR.AnchorType type, int x, int y, int z) {
        return new AircraftAssemblyPlanIR.Station(
                type,
                new AircraftBlockspaceIR.ContinuousPoint(x / 2.0, y / 2.0, z / 2.0),
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                type.capabilities(),
                "anchor_requirement_not_occupied_site");
    }

    private record Fixture(
            AircraftAssemblyPlanIR assembly,
            SkyforgeAircraftTargetPreflightIR preflight,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftTailLoweringIR tail) {}
}
