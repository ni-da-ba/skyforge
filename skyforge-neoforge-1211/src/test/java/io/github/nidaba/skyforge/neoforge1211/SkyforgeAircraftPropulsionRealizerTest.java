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
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftPropulsionRealizerTest {
    @Test
    void retainedPropellerGeometryIsBalancedConnectedAndFailClosedAtRuntimeBoundary() {
        AircraftAssemblyPlanIR assembly = assembly(List.of());
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly);
        SkyforgeAircraftPropulsionIR first = realize(assembly, preflight, SkyforgeAircraftPropulsionProfile.retainedC11());
        SkyforgeAircraftPropulsionIR second = realize(assembly, preflight, SkyforgeAircraftPropulsionProfile.retainedC11());

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.topologyChecks().passed()),
                () -> assertEquals(8, first.metrics().propellerSailCount()),
                () -> assertEquals(8, first.metrics().propellerSailPower()),
                () -> assertEquals(0, first.geometry().transverseFirstMomentA()),
                () -> assertEquals(0, first.geometry().transverseFirstMomentB()),
                () -> assertTrue(first.topologyChecks().centralSymmetry()),
                () -> assertTrue(first.topologyChecks().bladeGraphConnectedToHub()),
                () -> assertEquals("west", first.bearing().blockState().get("facing")),
                () -> assertTrue(first.sails().stream().allMatch(sail -> "x".equals(sail.blockState().get("axis")))),
                () -> assertTrue(first.readiness().resolvedUpstreamBlockers()
                        .contains("unsatisfied_contraption_companion_requirements")),
                () -> assertFalse(first.readiness().blockers()
                        .contains("unsatisfied_contraption_companion_requirements")),
                () -> assertTrue(first.readiness().blockers().contains("propulsion_runtime_obligations_unverified")),
                () -> assertFalse(first.readiness().propulsionRuntimeQualified()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void airframeCollisionFailsStaticTopology() {
        AircraftAssemblyPlanIR collisionAssembly = assembly(List.of(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(-1, 0, 0),
                List.of(AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                AircraftBlockspaceIR.Role.FUSELAGE_SPINE.capabilities())));
        SkyforgeAircraftPropulsionIR result = realize(
                collisionAssembly, preflight(collisionAssembly), SkyforgeAircraftPropulsionProfile.retainedC11());

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().hubCoordinateFree()),
                () -> assertFalse(result.topologyChecks().noSourceAssemblyCollision()),
                () -> assertTrue(result.readiness().blockers().contains("propulsion_static_topology_failed")));
    }

    @Test
    void wrongAxisAndWrongTargetProfileFailClosed() {
        SkyforgeAircraftPropulsionProfile retained = SkyforgeAircraftPropulsionProfile.retainedC11();
        SkyforgeAircraftPropulsionProfile wrongAxis = new SkyforgeAircraftPropulsionProfile(
                "wrong-axis",
                retained.targetProfileId(),
                retained.bearing(),
                new SkyforgeAircraftPropulsionProfile.Propeller(
                        retained.propeller().hubResourceId(),
                        retained.propeller().hubBlockState(),
                        retained.propeller().sailResourceId(),
                        SkyforgeAircraftPropulsionProfile.Axis.Z,
                        retained.propeller().sailPowerPerBlock(),
                        retained.propeller().minimumSailPower(),
                        retained.propeller().targetSailPower(),
                        retained.propeller().bladeOffsets()),
                retained.runtimeObligations());
        SkyforgeAircraftPropulsionProfile wrongTarget = new SkyforgeAircraftPropulsionProfile(
                "wrong-target",
                "not-the-target-profile",
                retained.bearing(),
                retained.propeller(),
                retained.runtimeObligations());
        AircraftAssemblyPlanIR assembly = assembly(List.of());
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> realize(assembly, preflight, wrongAxis)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> realize(assembly, preflight, wrongTarget)));
    }

    @Test
    void unbalancedOrUnderpoweredGeometryFailsStaticTopology() {
        SkyforgeAircraftPropulsionProfile retained = SkyforgeAircraftPropulsionProfile.retainedC11();
        List<SkyforgeAircraftPropulsionProfile.BladeOffset> unbalancedOffsets = new ArrayList<>(
                retained.propeller().bladeOffsets());
        unbalancedOffsets.set(unbalancedOffsets.size() - 1, new SkyforgeAircraftPropulsionProfile.BladeOffset(0, -3));
        SkyforgeAircraftPropulsionProfile unbalanced = profileWithOffsets(retained, unbalancedOffsets, 8);
        List<SkyforgeAircraftPropulsionProfile.BladeOffset> sixOffsets =
                retained.propeller().bladeOffsets().subList(0, 6);
        SkyforgeAircraftPropulsionProfile underpowered = profileWithOffsets(retained, sixOffsets, 8);
        AircraftAssemblyPlanIR assembly = assembly(List.of());
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly);
        SkyforgeAircraftPropulsionIR unbalancedResult = realize(assembly, preflight, unbalanced);
        SkyforgeAircraftPropulsionIR underpoweredResult = realize(assembly, preflight, underpowered);

        assertAll(
                () -> assertFalse(unbalancedResult.validation().passed()),
                () -> assertFalse(unbalancedResult.topologyChecks().zeroTransverseFirstMoment()),
                () -> assertFalse(underpoweredResult.validation().passed()),
                () -> assertFalse(underpoweredResult.topologyChecks().targetSailPowerSatisfied()));
    }

    @Test
    void bladeOffsetOrderingDoesNotChangeOutput() {
        SkyforgeAircraftPropulsionProfile retained = SkyforgeAircraftPropulsionProfile.retainedC11();
        List<SkyforgeAircraftPropulsionProfile.BladeOffset> reversed = new ArrayList<>(retained.propeller().bladeOffsets());
        java.util.Collections.reverse(reversed);
        SkyforgeAircraftPropulsionProfile reordered = profileWithOffsets(retained, reversed, 8);
        AircraftAssemblyPlanIR assembly = assembly(List.of());
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly);

        assertEquals(
                realize(assembly, preflight, retained),
                realize(assembly, preflight, reordered));
    }

    private static SkyforgeAircraftPropulsionProfile profileWithOffsets(
            SkyforgeAircraftPropulsionProfile retained,
            List<SkyforgeAircraftPropulsionProfile.BladeOffset> offsets,
            int targetPower) {
        return new SkyforgeAircraftPropulsionProfile(
                retained.profileId(),
                retained.targetProfileId(),
                retained.bearing(),
                new SkyforgeAircraftPropulsionProfile.Propeller(
                        retained.propeller().hubResourceId(),
                        retained.propeller().hubBlockState(),
                        retained.propeller().sailResourceId(),
                        retained.propeller().sailAxis(),
                        retained.propeller().sailPowerPerBlock(),
                        retained.propeller().minimumSailPower(),
                        targetPower,
                        offsets),
                retained.runtimeObligations());
    }

    private static SkyforgeAircraftPropulsionIR realize(
            AircraftAssemblyPlanIR assembly,
            SkyforgeAircraftTargetPreflightIR preflight,
            SkyforgeAircraftPropulsionProfile profile) {
        return new SkyforgeAircraftPropulsionRealizer().realize(
                assembly,
                preflight,
                "skyforge.aircraft.guild_utility_monoplane.propulsion.v1",
                profile);
    }

    private static SkyforgeAircraftTargetPreflightIR preflight(AircraftAssemblyPlanIR assembly) {
        return new SkyforgeAircraftTargetPreflight().lower(
                assembly,
                "skyforge.aircraft.guild_utility_monoplane.target_preflight.v1",
                SkyforgeAircraftTargetProfile.retainedC11());
    }

    private static AircraftAssemblyPlanIR assembly(List<AircraftAssemblyPlanIR.Site> extras) {
        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>(List.of(
                site(3, 0, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                site(4, 1, 0, AircraftBlockspaceIR.Role.WING_SURFACE_INTENT),
                site(5, 0, 0, AircraftBlockspaceIR.Role.WING_ATTACH_INTENT)));
        sites.addAll(extras);
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
                        station(AircraftBlockspaceIR.AnchorType.CG_REFERENCE, 6, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, 8, 1, 0),
                        station(AircraftBlockspaceIR.AnchorType.CARGO_STATION, 10, 1, 0)),
                new AircraftAssemblyPlanIR.Metrics(sites.size(), sites.size(), 0, 0, true, true),
                new AircraftAssemblyPlanIR.Validation(
                        true,
                        "coordinate_unique_semantic_assembly_only",
                        List.of("target resource selection", "runtime assembly")));
    }

    private static AircraftAssemblyPlanIR.Site site(int x, int y, int z, AircraftBlockspaceIR.Role role) {
        return new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(x, y, z), List.of(role), role.capabilities());
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
