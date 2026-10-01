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

final class SkyforgeAircraftProbeManifestEmitterTest {
    @Test
    void retainedManifestIsCoordinateUniqueAndDeterministic() {
        Fixture fixture = fixture("target.manifest.v1");
        SkyforgeAircraftProbeManifestIR first = emit(fixture, fixture.propulsion(), fixture.pilot());
        SkyforgeAircraftProbeManifestIR second = emit(fixture, fixture.propulsion(), fixture.pilot());

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.validationChecks().passed()),
                () -> assertTrue(first.readiness().probePlacementManifestReady()),
                () -> assertTrue(first.readiness().probePlacementCommandsReady()),
                () -> assertFalse(first.readiness().physicsAssemblyProbeReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(18, first.placements().size()),
                () -> assertEquals(1, first.kindCounts().get("airframe_structure").intValue()),
                () -> assertEquals(6, first.kindCounts().get("airframe_aerodynamic_surface").intValue()),
                () -> assertEquals(1, first.kindCounts().get("propeller_bearing").intValue()),
                () -> assertEquals(1, first.kindCounts().get("propeller_hub").intValue()),
                () -> assertEquals(8, first.kindCounts().get("propeller_sail").intValue()),
                () -> assertEquals(1, first.kindCounts().get("pilot_occupancy_station").intValue()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(-1, -2, -2), first.bounds().min()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(8, 3, 2), first.bounds().max()),
                () -> assertEquals(new AircraftBlockspaceIR.LatticePoint(10, 6, 5), first.bounds().sizeBlocks()),
                () -> assertTrue(first.duplicateCoordinates().isEmpty()),
                () -> assertTrue(first.readiness().staticBlockers().isEmpty()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void duplicateCoordinateFailsClosed() {
        Fixture fixture = fixture("target.manifest.duplicate");
        SkyforgeAircraftPilotStationIR collided = withPilotPlacement(
                fixture.pilot(), new AircraftBlockspaceIR.LatticePoint(8, 0, 0), "create:brown_seat");
        SkyforgeAircraftProbeManifestIR result = emit(fixture, fixture.propulsion(), collided);

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.validationChecks().allPlacementsCoordinateUnique()),
                () -> assertEquals(List.of(new AircraftBlockspaceIR.LatticePoint(8, 0, 0)), result.duplicateCoordinates()),
                () -> assertEquals(List.of("probe_manifest_static_validation_failed"), result.readiness().staticBlockers()));
    }

    @Test
    void malformedResourceFailsClosed() {
        Fixture fixture = fixture("target.manifest.resource");
        SkyforgeAircraftPilotStationIR malformed = withPilotPlacement(
                fixture.pilot(), fixture.pilot().placement().point(), "brown_seat");
        SkyforgeAircraftProbeManifestIR result = emit(fixture, fixture.propulsion(), malformed);

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.validationChecks().allResourceIdsNamespaced()),
                () -> assertTrue(result.readiness().staticBlockers().contains("probe_manifest_static_validation_failed")));
    }

    @Test
    void propulsionCountMismatchFailsClosed() {
        Fixture fixture = fixture("target.manifest.count");
        SkyforgeAircraftPropulsionIR source = fixture.propulsion();
        SkyforgeAircraftPropulsionIR.Metrics metrics = source.metrics();
        SkyforgeAircraftPropulsionIR changed = new SkyforgeAircraftPropulsionIR(
                source.schemaVersion(), source.assetId(), source.compilerVersion(), source.sourceAssemblyDigestSha256(),
                source.sourceTargetPreflightDigestSha256(), source.propulsionProfileId(), source.bearing(), source.hub(),
                source.sails(), source.geometry(), source.topologyChecks(), source.sourceAssemblyCollisions(),
                source.runtimeObligations(),
                new SkyforgeAircraftPropulsionIR.Metrics(
                        metrics.generatedPlacementCount() + 1,
                        metrics.propellerSailCount(),
                        metrics.propellerSailPower(),
                        metrics.runtimeObligationCount(),
                        metrics.runtimeObligationVerifiedCount()),
                source.readiness(), source.validation());
        SkyforgeAircraftPilotStationIR rebound = rebindPilot(fixture.pilot(), changed.sha256(), fixture.pilot().placement());
        SkyforgeAircraftProbeManifestIR result = emit(fixture, changed, rebound);

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.validationChecks().propulsionPlacementCountPreserved()));
    }

    @Test
    void mismatchedTargetProvenanceIsRejected() {
        Fixture fixture = fixture("target.manifest.first");
        SkyforgeAircraftTargetPreflightIR second = preflight(fixture.assembly(), "target.manifest.second");
        assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftProbeManifestEmitter().emit(
                second,
                fixture.propulsion(),
                fixture.tail(),
                fixture.pilot(),
                "skyforge.aircraft.guild_utility_monoplane.probe_manifest.bad",
                SkyforgeAircraftProbeManifestProfile.retainedC11()));
    }

    private static SkyforgeAircraftProbeManifestIR emit(
            Fixture fixture,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftPilotStationIR pilot) {
        return new SkyforgeAircraftProbeManifestEmitter().emit(
                fixture.preflight(),
                propulsion,
                fixture.tail(),
                pilot,
                "skyforge.aircraft.guild_utility_monoplane.probe_manifest.v1",
                SkyforgeAircraftProbeManifestProfile.retainedC11());
    }

    private static SkyforgeAircraftPilotStationIR withPilotPlacement(
            SkyforgeAircraftPilotStationIR source,
            AircraftBlockspaceIR.LatticePoint point,
            String resourceId) {
        SkyforgeAircraftPilotStationIR.Placement placement = new SkyforgeAircraftPilotStationIR.Placement(
                source.placement().kind(), point, resourceId, source.placement().blockState(),
                source.placement().staticCapability(), source.placement().runtimeBehaviorStatus());
        return rebindPilot(source, source.sourcePropulsionDigestSha256(), placement);
    }

    private static SkyforgeAircraftPilotStationIR rebindPilot(
            SkyforgeAircraftPilotStationIR source,
            String propulsionDigest,
            SkyforgeAircraftPilotStationIR.Placement placement) {
        return new SkyforgeAircraftPilotStationIR(
                source.schemaVersion(), source.assetId(), source.compilerVersion(), source.sourceTargetPreflightDigestSha256(),
                propulsionDigest, source.sourceTailLoweringDigestSha256(), source.profileId(), source.semanticStation(),
                placement, source.controlBindingContract(), source.topologyChecks(), source.runtimeObligations(),
                source.metrics(), source.readiness(), source.validation());
    }

    private static Fixture fixture(String targetAssetId) {
        AircraftAssemblyPlanIR assembly = assembly();
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, targetAssetId);
        SkyforgeAircraftPropulsionIR propulsion = new SkyforgeAircraftPropulsionRealizer().realize(
                assembly, preflight, "skyforge.aircraft.guild_utility_monoplane.propulsion.v1", SkyforgeAircraftPropulsionProfile.retainedC11());
        SkyforgeAircraftSurfaceStateIR surface = new SkyforgeAircraftSurfaceStateResolver().resolve(
                preflight, propulsion, "skyforge.aircraft.guild_utility_monoplane.surface_state.v1", SkyforgeAircraftSurfaceStateProfile.retainedC11());
        SkyforgeAircraftTailLoweringIR tail = new SkyforgeAircraftTailJunctionLowerer().lower(
                preflight, surface, "skyforge.aircraft.guild_utility_monoplane.tail_lowering.v1", SkyforgeAircraftTailLoweringProfile.retainedC11());
        SkyforgeAircraftPilotStationIR pilot = new SkyforgeAircraftPilotStationRealizer().realize(
                preflight, propulsion, tail, "skyforge.aircraft.guild_utility_monoplane.pilot_station.v1", SkyforgeAircraftPilotStationProfile.retainedC11());
        return new Fixture(assembly, preflight, propulsion, tail, pilot);
    }

    private static SkyforgeAircraftTargetPreflightIR preflight(AircraftAssemblyPlanIR assembly, String assetId) {
        return new SkyforgeAircraftTargetPreflight().lower(assembly, assetId, SkyforgeAircraftTargetProfile.retainedC11());
    }

    private static AircraftAssemblyPlanIR assembly() {
        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>();
        sites.add(site(8, 0, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        sites.add(site(5, 0, -1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        sites.add(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(5, 0, 0),
                List.of(AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                List.of("aerodynamic_lift_surface", "rigid_physics_member")));
        sites.add(site(5, 0, 1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        sites.add(site(5, 1, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
        sites.add(site(5, 2, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
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
                        station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, 8, 0, 0),
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
            SkyforgeAircraftTailLoweringIR tail,
            SkyforgeAircraftPilotStationIR pilot) {}
}
