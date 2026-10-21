package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftCockpitYawRouteLowererTest {
    @Test
    void retainedRouteIsDeterministicCollisionFreeSignCorrectAndRuntimeUnqualified() {
        SkyforgeAircraftRetainedGuildUtilityFixture.CockpitYawRouteFixture compiled =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileCockpitYawRoute();
        SkyforgeAircraftRetainedGuildUtilityFixture.CockpitYawRouteFixture repeated =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileCockpitYawRoute();
        SkyforgeAircraftCockpitYawRouteIR route = compiled.cockpitYawRoute();
        List<AircraftBlockspaceIR.LatticePoint> expectedPath = new ArrayList<>();
        for (int x = 5; x <= 18; x++) expectedPath.add(point(x, 2, 1));

        assertAll(
                () -> assertTrue(route.validation().passed()),
                () -> assertTrue(route.readiness().cockpitRouteStaticSearchPassed()),
                () -> assertTrue(route.readiness().cockpitRoutePlacementPatchReady()),
                () -> assertFalse(route.readiness().cockpitRouteRuntimeProbeReady()),
                () -> assertFalse(route.readiness().cockpitRouteRuntimeQualified()),
                () -> assertFalse(route.readiness().pilotInteractionBindingReady()),
                () -> assertFalse(route.readiness().runtimeQualificationReady()),
                () -> assertFalse(route.readiness().flightQualified()),
                () -> assertEquals(point(5, 3, 0), route.pilotSeatCoordinate()),
                () -> assertEquals("starboard_adjacent_floor", route.selectedCandidate()),
                () -> assertEquals(point(5, 3, 1), route.steeringWheelCoordinate()),
                () -> assertEquals(expectedPath, route.routePath()),
                () -> assertEquals(point(18, 3, 1), route.driveCogCoordinate()),
                () -> assertEquals(point(18, 3, 0), route.swivelBearingCoordinate()),
                () -> assertEquals(-1, route.signContract().positiveWheelCommandGeneratedSign()),
                () -> assertEquals(1, route.signContract().expectedTailDriveCogSign()),
                () -> assertEquals(-1, route.signContract().expectedSwivelExtraCogSign()),
                () -> assertEquals(16, route.signContract().expectedRpmMagnitude()),
                () -> assertEquals(SkyforgeAircraftCockpitYawRouteProfile.CREATE_SOURCE_COMMIT, route.signContract().createSourceCommit()),
                () -> assertEquals(16, route.metrics().routePlacementCount()),
                () -> assertEquals(14, route.metrics().routePlaneCellCount()),
                () -> assertEquals(0, route.metrics().routeTurnCount()),
                () -> assertEquals(2, route.metrics().gearboxCount()),
                () -> assertEquals(12, route.metrics().shaftCount()),
                () -> assertEquals(118, route.metrics().priorMovingParentMainBodyPlacementCount()),
                () -> assertEquals(134, route.metrics().resultingMovingParentMainBodyPlacementCount()),
                () -> assertEquals(9, route.metrics().nestedPropellerChildPlacementCount()),
                () -> assertEquals(4, route.metrics().yawControlChildPlacementCount()),
                () -> assertEquals(147, route.metrics().expectedPrimarySableTransferCount()),
                () -> assertEquals(point(14, 2, 2), route.routeGlueDomain().selectionSizeBlocks()),
                () -> assertEquals(4, route.runtimeObligations().size()),
                () -> assertTrue(route.runtimeObligations().stream().allMatch(value -> "unverified".equals(value.status()))),
                () -> assertEquals(route, repeated.cockpitYawRoute()),
                () -> assertEquals(route.sha256(), repeated.cockpitYawRoute().sha256()),
                () -> assertEquals(64, route.sha256().length()));
    }

    @Test
    void declaredServicePlaneObstacleForcesDeterministicDetour() {
        SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture steering =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileSteeringYawSource();
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture v012 = steering.v0131().v012();
        SkyforgeAircraftCockpitYawRouteIR route = lower(steering,
                SkyforgeAircraftCockpitYawRouteProfile.retainedC11WithForbidden(List.of(point(10, 2, 1))),
                "detour");
        assertAll(
                () -> assertFalse(route.routePath().contains(point(10, 2, 1))),
                () -> assertTrue(route.metrics().routeTurnCount() >= 2),
                () -> assertEquals(-1, route.signContract().expectedSwivelExtraCogSign()),
                () -> assertEquals(v012.pilot().sha256(), route.sourcePilotStationDigestSha256()));
    }

    @Test
    void completeServicePlaneWallFailsCompileVisible() {
        SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture steering =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileSteeringYawSource();
        List<AircraftBlockspaceIR.LatticePoint> wall = new ArrayList<>();
        for (int z = -3; z <= 3; z++) wall.add(point(10, 2, z));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> lower(
                steering, SkyforgeAircraftCockpitYawRouteProfile.retainedC11WithForbidden(wall), "blocked"));
        assertTrue(error.getMessage().contains("no cockpit-to-rudder route accepted"));
    }

    @Test
    void mixedSteeringYawProvenanceFailsClosed() {
        SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture current =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileSteeringYawSource();
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture v012 = current.v0131().v012();
        SkyforgeAircraftYawControlIR alternateYaw = new SkyforgeAircraftYawControlLowerer().lower(
                v012.manifest(), v012.assemblyFixture(), v012.glue(), v012.powertrain(),
                "skyforge.aircraft.guild_utility_monoplane.yaw_control.route-alternate",
                SkyforgeAircraftYawControlProfile.retainedC11());
        SkyforgeAircraftRudderControlAuthority alternateAuthority = SkyforgeAircraftRudderControlAuthority.accepted(alternateYaw);
        SkyforgeAircraftSteeringYawSourceIR alternateSteering = new SkyforgeAircraftSteeringYawSourceLowerer().lower(
                alternateYaw, alternateAuthority, "alternate-steering", SkyforgeAircraftSteeringYawSourceProfile.retainedC11());
        assertNotEquals(current.steeringYawSource().sha256(), alternateSteering.sha256());
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftCockpitYawRouteLowerer().lower(
                v012.pilot(), v012.manifest(), v012.assemblyFixture(), v012.glue(), v012.powertrain(),
                current.v0131().yawControl(), alternateSteering, "mixed", SkyforgeAircraftCockpitYawRouteProfile.retainedC11()));
    }

    @Test
    void sourcePinnedGearboxEquationRetainsExpectedModifiers() {
        assertAll(
                () -> assertEquals(-1, SkyforgeAircraftCockpitYawRouteLowerer.gearboxModifier(
                        SkyforgeAircraftCockpitYawRouteLowerer.Direction.UP, SkyforgeAircraftCockpitYawRouteLowerer.Direction.EAST)),
                () -> assertEquals(1, SkyforgeAircraftCockpitYawRouteLowerer.gearboxModifier(
                        SkyforgeAircraftCockpitYawRouteLowerer.Direction.WEST, SkyforgeAircraftCockpitYawRouteLowerer.Direction.UP)),
                () -> assertEquals(-1, SkyforgeAircraftCockpitYawRouteLowerer.gearboxModifier(
                        SkyforgeAircraftCockpitYawRouteLowerer.Direction.EAST, SkyforgeAircraftCockpitYawRouteLowerer.Direction.WEST)));
    }

    private static SkyforgeAircraftCockpitYawRouteIR lower(
            SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture steering,
            SkyforgeAircraftCockpitYawRouteProfile profile,
            String assetId) {
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture v012 = steering.v0131().v012();
        return new SkyforgeAircraftCockpitYawRouteLowerer().lower(
                v012.pilot(), v012.manifest(), v012.assemblyFixture(), v012.glue(), v012.powertrain(),
                steering.v0131().yawControl(), steering.steeringYawSource(), assetId, profile);
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
