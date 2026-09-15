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

final class SkyforgeAircraftTailJunctionLowererTest {
    @Test
    void retainedOneBlockTranslationResolvesExactlyTheTailIntersectionConflict() {
        AircraftAssemblyPlanIR assembly = assembly(false, false);
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.tail.v1");
        SkyforgeAircraftSurfaceStateIR surface = surface(assembly, preflight);
        SkyforgeAircraftTailLoweringIR first = lower(preflight, surface);
        SkyforgeAircraftTailLoweringIR second = lower(preflight, surface);

        SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement retainedJunction =
                placement(first, new AircraftBlockspaceIR.LatticePoint(5, 0, 0));
        SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement shiftedRoot =
                placement(first, new AircraftBlockspaceIR.LatticePoint(5, 1, 0));

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.topologyChecks().passed()),
                () -> assertTrue(first.readiness().tailJunctionLoweringPassed()),
                () -> assertTrue(first.readiness().surfaceStateResolutionComplete()),
                () -> assertEquals(5, first.metrics().v06AerodynamicPlacementCount()),
                () -> assertEquals(6, first.metrics().v07AerodynamicPlacementCount()),
                () -> assertEquals(3, first.metrics().verticalTailCellCount()),
                () -> assertEquals(3, first.metrics().horizontalTailCellCount()),
                () -> assertEquals(1, first.metrics().resolvedFormerConflictCount()),
                () -> assertEquals(15, first.metrics().verticalXFirstMomentBeforeBlocks()),
                () -> assertEquals(15, first.metrics().verticalXFirstMomentAfterBlocks()),
                () -> assertEquals(3, first.metrics().verticalYFirstMomentBeforeBlocks()),
                () -> assertEquals(6, first.metrics().verticalYFirstMomentAfterBlocks()),
                () -> assertEquals(0.0, first.metrics().verticalCentroidShiftXBlocks()),
                () -> assertEquals(1.0, first.metrics().verticalCentroidShiftYBlocks()),
                () -> assertEquals(0.0, first.metrics().verticalCentroidShiftZBlocks()),
                () -> assertEquals(
                        List.of(new AircraftBlockspaceIR.LatticePoint(5, 1, 0)),
                        first.rootFaceContacts()),
                () -> assertEquals(
                        List.of(AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT),
                        retainedJunction.roles()),
                () -> assertEquals("up", retainedJunction.blockState().get("facing")),
                () -> assertEquals(
                        List.of(AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                        shiftedRoot.roles()),
                () -> assertEquals("south", shiftedRoot.blockState().get("facing")),
                () -> assertFalse(first.readiness().staticBlockers().contains("unresolved_surface_state_conflicts")),
                () -> assertTrue(first.readiness().staticBlockers().contains("unresolved_required_station_providers")),
                () -> assertFalse(first.readiness().probeSchematicEmissionReady()),
                () -> assertEquals(preflight.sha256(), first.sourceTargetPreflightDigestSha256()),
                () -> assertEquals(surface.sha256(), first.sourceSurfaceStateDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));
    }

    @Test
    void immutablePlacementCollisionFailsClosed() {
        AircraftAssemblyPlanIR assembly = assembly(true, false);
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.tail.collision");
        SkyforgeAircraftTailLoweringIR result = lower(preflight, surface(assembly, preflight));

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().noImmutablePlacementCollision()),
                () -> assertEquals(
                        List.of(new AircraftBlockspaceIR.LatticePoint(5, 3, 0)),
                        result.collisions()),
                () -> assertTrue(result.readiness().staticBlockers().contains("tail_junction_lowering_failed")));
    }

    @Test
    void disconnectedVerticalTailFailsClosed() {
        AircraftAssemblyPlanIR assembly = assembly(false, true);
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.tail.disconnected");
        SkyforgeAircraftTailLoweringIR result = lower(preflight, surface(assembly, preflight));

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.topologyChecks().verticalTailInternallyConnected()),
                () -> assertTrue(result.topologyChecks().verticalRootFaceAttachedToHorizontalTail()),
                () -> assertTrue(result.readiness().staticBlockers().contains("tail_junction_lowering_failed")));
    }

    @Test
    void conflictSetMustExactlyEqualHorizontalVerticalIntersections() {
        AircraftAssemblyPlanIR assembly = assembly(false, false);
        SkyforgeAircraftTargetPreflightIR preflight = preflight(assembly, "target.tail.mismatch");
        SkyforgeAircraftSurfaceStateIR valid = surface(assembly, preflight);
        SkyforgeAircraftSurfaceStateIR mismatched = new SkyforgeAircraftSurfaceStateIR(
                valid.schemaVersion(),
                valid.assetId(),
                valid.compilerVersion(),
                valid.sourceTargetPreflightDigestSha256(),
                valid.sourcePropulsionDigestSha256(),
                valid.surfaceStateProfileId(),
                valid.providerId(),
                valid.stateProperty(),
                valid.resolvedPlacements(),
                List.of(),
                valid.metrics(),
                valid.readiness(),
                valid.validation());

        assertThrows(
                IllegalArgumentException.class,
                () -> lower(preflight, mismatched));
    }

    @Test
    void boundedProfileRejectsAnyTranslationOtherThanOneBlockUp() {
        SkyforgeAircraftTailLoweringProfile retained = SkyforgeAircraftTailLoweringProfile.retainedC11();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAircraftTailLoweringProfile(
                        "invalid-shift",
                        retained.targetProfileId(),
                        retained.providerId(),
                        retained.resourceId(),
                        retained.stateProperty(),
                        retained.translatedRole(),
                        retained.junctionRetainedRole(),
                        retained.translatedState(),
                        retained.junctionRetainedState(),
                        new SkyforgeAircraftTailLoweringProfile.Translation(0, 2, 0)));
    }

    @Test
    void mismatchedTargetProvenanceIsRejected() {
        AircraftAssemblyPlanIR assembly = assembly(false, false);
        SkyforgeAircraftTargetPreflightIR first = preflight(assembly, "target.tail.first");
        SkyforgeAircraftTargetPreflightIR second = preflight(assembly, "target.tail.second");
        SkyforgeAircraftSurfaceStateIR surface = surface(assembly, first);

        assertThrows(
                IllegalArgumentException.class,
                () -> lower(second, surface));
    }

    private static SkyforgeAircraftTailLoweringIR lower(
            SkyforgeAircraftTargetPreflightIR preflight,
            SkyforgeAircraftSurfaceStateIR surface) {
        return new SkyforgeAircraftTailJunctionLowerer().lower(
                preflight,
                surface,
                "skyforge.aircraft.guild_utility_monoplane.tail_lowering.v1",
                SkyforgeAircraftTailLoweringProfile.retainedC11());
    }

    private static SkyforgeAircraftSurfaceStateIR surface(
            AircraftAssemblyPlanIR assembly,
            SkyforgeAircraftTargetPreflightIR preflight) {
        SkyforgeAircraftPropulsionIR propulsion = new SkyforgeAircraftPropulsionRealizer().realize(
                assembly,
                preflight,
                "skyforge.aircraft.guild_utility_monoplane.propulsion.v1",
                SkyforgeAircraftPropulsionProfile.retainedC11());
        return new SkyforgeAircraftSurfaceStateResolver().resolve(
                preflight,
                propulsion,
                "skyforge.aircraft.guild_utility_monoplane.surface_state.v1",
                SkyforgeAircraftSurfaceStateProfile.retainedC11());
    }

    private static SkyforgeAircraftTargetPreflightIR preflight(
            AircraftAssemblyPlanIR assembly,
            String assetId) {
        return new SkyforgeAircraftTargetPreflight().lower(
                assembly,
                assetId,
                SkyforgeAircraftTargetProfile.retainedC11());
    }

    private static SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement placement(
            SkyforgeAircraftTailLoweringIR result,
            AircraftBlockspaceIR.LatticePoint point) {
        return result.resolvedAerodynamicPlacements().stream()
                .filter(placement -> point.equals(placement.point()))
                .findFirst()
                .orElseThrow();
    }

    private static AircraftAssemblyPlanIR assembly(boolean collision, boolean disconnected) {
        ArrayList<AircraftAssemblyPlanIR.Site> sites = new ArrayList<>();
        sites.add(site(8, 0, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        sites.add(site(5, 0, -1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        sites.add(new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(5, 0, 0),
                List.of(
                        AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT,
                        AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                List.of("aerodynamic_lift_surface", "rigid_physics_member")));
        sites.add(site(5, 0, 1, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT));
        if (!disconnected) {
            sites.add(site(5, 1, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
        }
        sites.add(site(5, 2, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT));
        if (collision) {
            sites.add(site(5, 3, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE));
        }

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
                        station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, 8, 0, 0),
                        station(AircraftBlockspaceIR.AnchorType.CARGO_STATION, 10, 0, 0)),
                new AircraftAssemblyPlanIR.Metrics(
                        sites.size(),
                        sites.size(),
                        0,
                        1,
                        true,
                        true),
                new AircraftAssemblyPlanIR.Validation(
                        true,
                        "coordinate_unique_semantic_assembly_only",
                        List.of("target resource selection", "runtime assembly")));
    }

    private static AircraftAssemblyPlanIR.Site site(
            int x,
            int y,
            int z,
            AircraftBlockspaceIR.Role role) {
        return new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                List.of(role),
                role.capabilities());
    }

    private static AircraftAssemblyPlanIR.Station station(
            AircraftBlockspaceIR.AnchorType type,
            int x,
            int y,
            int z) {
        return new AircraftAssemblyPlanIR.Station(
                type,
                new AircraftBlockspaceIR.ContinuousPoint(x / 2.0, y / 2.0, z / 2.0),
                new AircraftBlockspaceIR.LatticePoint(x, y, z),
                type.capabilities(),
                "anchor_requirement_not_occupied_site");
    }
}
