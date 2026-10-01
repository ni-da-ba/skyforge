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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftYawControlLowererTest {
    @Test
    void retainedCorrectedYawTopologyIsDeterministicFailClosedAndRuntimeUnqualified() {
        SkyforgeAircraftRetainedGuildUtilityFixture.YawFixture compiled =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileV0131();
        SkyforgeAircraftRetainedGuildUtilityFixture.YawFixture repeated =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileV0131();
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture v012 = compiled.v012();
        SkyforgeAircraftYawControlIR yaw = compiled.yawControl();

        Set<String> glueNames = yaw.runtimeGlueDomains().stream()
                .map(SkyforgeAircraftYawControlIR.GlueDomain::name)
                .collect(Collectors.toSet());
        assertAll(
                () -> assertTrue(yaw.validation().passed()),
                () -> assertTrue(yaw.topologyChecks().passed()),
                () -> assertTrue(yaw.glueChecks().passed()),
                () -> assertTrue(yaw.countChecks().passed()),
                () -> assertTrue(yaw.readiness().yawControlStaticTopologyPassed()),
                () -> assertTrue(yaw.readiness().rudderChildCaptureProbeReady()),
                () -> assertFalse(yaw.readiness().controlActuationProbeReady()),
                () -> assertFalse(yaw.readiness().runtimeQualificationReady()),
                () -> assertFalse(yaw.readiness().flightQualified()),
                () -> assertEquals(SkyforgeAircraftYawControlProfile.PROFILE_ID, yaw.profileId()),
                () -> assertEquals(SkyforgeAircraftYawControlProfile.SUPERSEDED_V013_DIGEST_SHA256, yaw.supersededYawDigestSha256()),
                () -> assertTrue(yaw.correctionReason().contains("34764106343")),
                () -> assertEquals(v012.manifest().sha256(), yaw.sourceProbeManifestDigestSha256()),
                () -> assertEquals(v012.assemblyFixture().sha256(), yaw.sourceAssemblyFixtureDigestSha256()),
                () -> assertEquals(v012.glue().sha256(), yaw.sourceGlueEncodingDigestSha256()),
                () -> assertEquals(v012.powertrain().sha256(), yaw.sourcePowertrainDigestSha256()),
                () -> assertEquals(point(18, 3, 0), yaw.swivelBearingCoordinate()),
                () -> assertEquals(point(18, 4, 0), yaw.rudderSeedCoordinate()),
                () -> assertEquals(List.of(point(18, 4, 0), point(18, 5, 0), point(18, 6, 0), point(18, 7, 0)), yaw.rudderChildCoordinates()),
                () -> assertEquals(List.of(point(17, 4, 0), point(17, 5, 0), point(17, 6, 0), point(17, 7, 0)), yaw.airGapCoordinates()),
                () -> assertEquals(7, yaw.metrics().runtimeGlueDomainCount()),
                () -> assertEquals(Set.of("fuselage_core", "wing", "horizontal_tail", "powerplant", "vertical_tail_fixed", "yaw_bearing_mount", "rudder_child"), glueNames),
                () -> assertFalse(glueNames.contains("vertical_tail")),
                () -> assertEquals(118, yaw.metrics().v012MovingParentMainBodyPlacementCount()),
                () -> assertEquals(118, yaw.metrics().v0131MovingParentMainBodyPlacementCount()),
                () -> assertEquals(9, yaw.metrics().nestedPropellerChildPlacementCount()),
                () -> assertEquals(4, yaw.metrics().yawControlChildPlacementCount()),
                () -> assertEquals(130, yaw.metrics().resultingManifestPlacementCount()),
                () -> assertEquals(131, yaw.metrics().expectedPrimarySableTransferCount()),
                () -> assertEquals(1, yaw.metrics().parentMainAdditionCount()),
                () -> assertEquals(1, yaw.metrics().parentMainRemovalCount()),
                () -> assertEquals(4, yaw.metrics().controlChildAdditionCount()),
                () -> assertEquals(5, yaw.metrics().runtimeObligationCount()),
                () -> assertEquals(0, yaw.metrics().runtimeObligationVerifiedCount()),
                () -> assertEquals(7, yaw.aerodynamicAccounting().fixedVerticalTailRegularSailCells()),
                () -> assertEquals(19, yaw.aerodynamicAccounting().horizontalTailRegularSailCells()),
                () -> assertEquals(4, yaw.aerodynamicAccounting().rudderSymmetricSailCells()),
                () -> assertEquals(4, yaw.aerodynamicAccounting().airGapCells()),
                () -> assertTrue(yaw.readiness().runtimeBlockers().contains("v0131_primary_sable_recapture_unverified")),
                () -> assertTrue(yaw.readiness().runtimeBlockers().contains("rudder_actuation_unverified")),
                () -> assertTrue(yaw.readiness().runtimeBlockers().contains("rudder_yaw_force_unverified")),
                () -> assertEquals(yaw, repeated.yawControl()),
                () -> assertEquals(yaw.sha256(), repeated.yawControl().sha256()),
                () -> assertEquals(64, yaw.sha256().length()));
    }

    @Test
    void trailingFinSourceStateMismatchIsRejectedAfterRebuildingCurrentJavaProvenance() {
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture source =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileV012();
        SkyforgeAircraftProbeManifestIR tamperedManifest = replaceTrailingFinState(source.manifest(), Map.of("facing", "north"));
        SkyforgeAircraftAssemblyFixtureIR tamperedFixture = new SkyforgeAircraftAssemblyFixturePlanner().plan(
                tamperedManifest,
                "yaw-control.tampered.fixture",
                SkyforgeAircraftAssemblyFixtureProfile.retainedC11());
        SkyforgeAircraftGlueEncodingIR tamperedGlue = new SkyforgeAircraftGlueEncoder().encode(
                tamperedFixture,
                "yaw-control.tampered.glue",
                SkyforgeAircraftGlueEncodingProfile.retainedC11());
        SkyforgeAircraftPowertrainIR tamperedPowertrain = new SkyforgeAircraftPowertrainLowerer().lower(
                tamperedManifest,
                tamperedFixture,
                tamperedGlue,
                "yaw-control.tampered.powertrain",
                SkyforgeAircraftPowertrainProfile.retainedC11());

        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftYawControlLowerer().lower(
                tamperedManifest,
                tamperedFixture,
                tamperedGlue,
                tamperedPowertrain,
                "yaw-control.tampered",
                SkyforgeAircraftYawControlProfile.retainedC11()));
    }

    @Test
    void mixedUpstreamProvenanceIsRejectedBeforeTopologyLowering() {
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture source =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileV012();
        SkyforgeAircraftProbeManifestIR tamperedManifest = copyManifest(
                source.manifest(), source.manifest().assetId() + ".different", source.manifest().placements());
        assertNotEquals(source.manifest().sha256(), tamperedManifest.sha256());

        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftYawControlLowerer().lower(
                tamperedManifest,
                source.assemblyFixture(),
                source.glue(),
                source.powertrain(),
                "yaw-control.bad-provenance",
                SkyforgeAircraftYawControlProfile.retainedC11()));
    }

    @Test
    void bearingCoordinateDriftIsRejectedBySourceBackedProfile() {
        SkyforgeAircraftYawControlProfile retained = SkyforgeAircraftYawControlProfile.retainedC11();
        ArrayList<SkyforgeAircraftYawControlProfile.Placement> placements = new ArrayList<>(retained.placements());
        int index = roleIndex(placements, "yaw_swivel_bearing");
        SkyforgeAircraftYawControlProfile.Placement bearing = placements.get(index);
        placements.set(index, new SkyforgeAircraftYawControlProfile.Placement(
                bearing.role(), bearing.mode(), point(17, 3, 0), bearing.kind(), bearing.resourceId(),
                bearing.blockState(), bearing.replaces(), bearing.sourceContract()));
        assertThrows(IllegalArgumentException.class, () -> copyProfile(retained, placements, retained.replacementGlueDomains(), retained.airGapCoordinates()));
    }

    @Test
    void reintroducedAirGapGeometryIsRejectedBySourceBackedProfile() {
        SkyforgeAircraftYawControlProfile retained = SkyforgeAircraftYawControlProfile.retainedC11();
        ArrayList<AircraftBlockspaceIR.LatticePoint> gaps = new ArrayList<>(retained.airGapCoordinates());
        gaps.set(gaps.size() - 1, point(18, 7, 0));
        assertThrows(IllegalArgumentException.class, () -> copyProfile(retained, retained.placements(), retained.replacementGlueDomains(), gaps));
    }

    @Test
    void rudderGlueIncludingBearingIsRejectedBySourceBackedProfile() {
        SkyforgeAircraftYawControlProfile retained = SkyforgeAircraftYawControlProfile.retainedC11();
        ArrayList<SkyforgeAircraftYawControlProfile.GlueDomain> domains = new ArrayList<>(retained.replacementGlueDomains());
        int index = glueIndex(domains, "rudder_child");
        domains.set(index, new SkyforgeAircraftYawControlProfile.GlueDomain(
                "rudder_child", point(18, 3, 0), point(18, 7, 0), SkyforgeAircraftYawControlProfile.GlueOwner.CONTROL_CHILD));
        assertThrows(IllegalArgumentException.class, () -> copyProfile(retained, retained.placements(), domains, retained.airGapCoordinates()));
    }

    @Test
    void supersededFailureDigestCannotBeRewritten() {
        SkyforgeAircraftYawControlProfile retained = SkyforgeAircraftYawControlProfile.retainedC11();
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftYawControlProfile(
                retained.profileId(), "0".repeat(64), retained.correctionReason(), retained.supersededGlueDomainName(),
                retained.fixedFinCoordinates(), retained.airGapCoordinates(), retained.placements(), retained.replacementGlueDomains(),
                retained.sourceEvidence(), retained.runtimeObligations()));
    }

    private static SkyforgeAircraftProbeManifestIR replaceTrailingFinState(
            SkyforgeAircraftProbeManifestIR source, Map<String, String> state) {
        ArrayList<SkyforgeAircraftProbeManifestIR.Placement> placements = new ArrayList<>(source.placements());
        for (int i = 0; i < placements.size(); i++) {
            SkyforgeAircraftProbeManifestIR.Placement placement = placements.get(i);
            if (point(17, 4, 0).equals(placement.point())) {
                placements.set(i, new SkyforgeAircraftProbeManifestIR.Placement(
                        placement.kind(), placement.point(), placement.resourceId(), state, placement.sourceLayer()));
                return copyManifest(source, source.assetId() + ".tampered-fin", placements);
            }
        }
        throw new AssertionError("production manifest does not contain expected trailing-fin source [17,4,0]");
    }

    private static SkyforgeAircraftProbeManifestIR copyManifest(
            SkyforgeAircraftProbeManifestIR source,
            String assetId,
            List<SkyforgeAircraftProbeManifestIR.Placement> placements) {
        return new SkyforgeAircraftProbeManifestIR(
                source.schemaVersion(),
                assetId,
                source.compilerVersion(),
                source.sourceTargetPreflightDigestSha256(),
                source.sourcePropulsionDigestSha256(),
                source.sourceTailLoweringDigestSha256(),
                source.sourcePilotStationDigestSha256(),
                source.profileId(),
                source.originContract(),
                placements,
                source.bounds(),
                source.resourceIds(),
                source.kindCounts(),
                source.duplicateCoordinates(),
                source.validationChecks(),
                source.readiness(),
                source.validation());
    }

    private static SkyforgeAircraftYawControlProfile copyProfile(
            SkyforgeAircraftYawControlProfile source,
            List<SkyforgeAircraftYawControlProfile.Placement> placements,
            List<SkyforgeAircraftYawControlProfile.GlueDomain> glueDomains,
            List<AircraftBlockspaceIR.LatticePoint> airGapCoordinates) {
        return new SkyforgeAircraftYawControlProfile(
                source.profileId(),
                source.supersededYawDigestSha256(),
                source.correctionReason(),
                source.supersededGlueDomainName(),
                source.fixedFinCoordinates(),
                airGapCoordinates,
                placements,
                glueDomains,
                source.sourceEvidence(),
                source.runtimeObligations());
    }

    private static int roleIndex(List<SkyforgeAircraftYawControlProfile.Placement> placements, String role) {
        for (int i = 0; i < placements.size(); i++) if (role.equals(placements.get(i).role())) return i;
        throw new AssertionError("missing role " + role);
    }

    private static int glueIndex(List<SkyforgeAircraftYawControlProfile.GlueDomain> domains, String name) {
        for (int i = 0; i < domains.size(); i++) if (name.equals(domains.get(i).name())) return i;
        throw new AssertionError("missing glue domain " + name);
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
