package io.github.nidaba.skyforge.model.aircraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class AircraftBlockspaceAssemblyTest {
    private static final double EPS = 1.0e-12;

    @Test
    void guildUtilityBlockspacePreservesAcceptedDiscreteInvariants() {
        AircraftDesignIR design = design();
        AircraftBlockspaceIR blockspace = new AircraftBlockspaceCompiler().compile(design, blockspaceSpec(design, 0.5));
        AircraftBlockspaceIR.Metrics metrics = blockspace.metrics();

        assertAll(
                () -> assertTrue(blockspace.validation().passed()),
                () -> assertEquals(1, metrics.connectedComponents6Neighbor()),
                () -> assertTrue(metrics.mirrorSymmetrySatisfied()),
                () -> assertTrue(metrics.propellerDiskClear()),
                () -> assertTrue(metrics.cellCount() > 50),
                () -> assertTrue(metrics.cgStationQuantizationErrorBlocks() <= 0.5 + EPS),
                () -> assertTrue(metrics.dimensionErrorBlocks().wingSpanBlocks() <= 1.0 + EPS),
                () -> assertTrue(metrics.dimensionErrorBlocks().horizontalTailSpanBlocks() <= 1.0 + EPS),
                () -> assertTrue(metrics.dimensionErrorBlocks().verticalTailHeightBlocks() <= 1.0 + EPS),
                () -> assertEquals("nose_to_tail", blockspace.coordinateSystem().x()),
                () -> assertEquals("up", blockspace.coordinateSystem().y()),
                () -> assertEquals("starboard_positive", blockspace.coordinateSystem().z()),
                () -> assertEquals("integer_lattice", blockspace.coordinateSystem().cellCenters()),
                () -> assertEquals(2.0, blockspace.coordinateSystem().blocksPerMeter(), EPS));
    }

    @Test
    void blockspaceIdentityIsExplicitDeterministicAndTargetNeutral() {
        AircraftDesignIR design = design();
        AircraftBlockspaceSpec spec = blockspaceSpec(design, 0.5);
        AircraftBlockspaceCompiler compiler = new AircraftBlockspaceCompiler();
        AircraftBlockspaceIR first = compiler.compile(design, spec);
        AircraftBlockspaceIR second = compiler.compile(design, spec);
        String json = new AircraftBlockspaceIRJson().writeString(first);

        assertAll(
                () -> assertEquals("skyforge.aircraft.guild_utility_monoplane.blockspace.v1", first.assetId()),
                () -> assertEquals(design.assetId(), first.sourceDesignAssetId()),
                () -> assertEquals(design.sha256(), first.sourceDesignDigestSha256()),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()),
                () -> assertFalse(json.contains("minecraft:")),
                () -> assertFalse(json.contains("create:")),
                () -> assertFalse(json.contains("sable:")),
                () -> assertFalse(json.contains("aeronautics:")),
                () -> assertFalse(first.declaredTranscriptionAssumptions().containsConcreteTargetResourceIdentity()),
                () -> assertEquals(
                        List.of("aerodynamic_lift_surface", "rigid_physics_member"),
                        AircraftBlockspaceIR.Role.WING_SURFACE_INTENT.capabilities()));
    }

    @Test
    void blockspaceRejectsMismatchedSourceIdentityAndUnknownRoles() {
        AircraftDesignIR design = design();
        AircraftBlockspaceSpec valid = blockspaceSpec(design, 0.5);
        AircraftBlockspaceSpec wrongAsset = new AircraftBlockspaceSpec(
                valid.schemaVersion(),
                valid.assetId(),
                design.assetId() + ".wrong",
                design.sha256(),
                valid.blocksPerMeter(),
                valid.mounts(),
                valid.semanticStations(),
                valid.propellerHubRadiusM(),
                valid.validationLimits());
        AircraftBlockspaceSpec wrongDigest = new AircraftBlockspaceSpec(
                valid.schemaVersion(),
                valid.assetId(),
                design.assetId(),
                "0".repeat(64),
                valid.blocksPerMeter(),
                valid.mounts(),
                valid.semanticStations(),
                valid.propellerHubRadiusM(),
                valid.validationLimits());

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftBlockspaceCompiler().compile(design, wrongAsset)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftBlockspaceCompiler().compile(design, wrongDigest)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> AircraftBlockspaceIR.Role.fromId("invented_role")));
    }

    @Test
    void diagonalConnectorAndPropellerIntrusionFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AircraftBlockspaceCompiler.axisAlignedLine(
                        new AircraftBlockspaceIR.LatticePoint(0, 0, 0),
                        new AircraftBlockspaceIR.LatticePoint(1, 1, 0),
                        AircraftBlockspaceIR.Role.WING_ATTACH_INTENT));

        AircraftDesignIR design = design();
        AircraftBlockspaceIR obstructed = new AircraftBlockspaceCompiler().compile(
                design, blockspaceSpec(design, 0.3));
        assertAll(
                () -> assertFalse(obstructed.validation().passed()),
                () -> assertFalse(obstructed.metrics().propellerDiskClear()),
                () -> assertTrue(obstructed.metrics().propellerDiskViolationCount() > 0));
    }

    @Test
    void assemblyCoalescesCoordinatesWithoutInventingAnchorSites() {
        AircraftDesignIR design = design();
        AircraftBlockspaceIR blockspace = new AircraftBlockspaceCompiler().compile(
                design, blockspaceSpec(design, 0.5));
        AircraftAssemblyPlanIR plan = new AircraftAssemblyPlanner().plan(
                blockspace, "skyforge.aircraft.guild_utility_monoplane.assembly.v1");

        assertAll(
                () -> assertTrue(plan.validation().passed()),
                () -> assertEquals(blockspace.assetId(), plan.sourceBlockspaceAssetId()),
                () -> assertEquals(blockspace.sha256(), plan.sourceBlockspaceDigestSha256()),
                () -> assertEquals(blockspace.cells().size(), plan.metrics().inputCellRecordCount()),
                () -> assertTrue(plan.metrics().uniqueAssemblySiteCount() < plan.metrics().inputCellRecordCount()),
                () -> assertTrue(plan.metrics().collapsedRoleRecordCount() > 0),
                () -> assertTrue(plan.metrics().multiRoleSiteCount() > 0),
                () -> assertTrue(plan.metrics().coordinateUniquenessSatisfied()),
                () -> assertTrue(plan.metrics().allSourceRolesPreserved()),
                () -> assertEquals(4, plan.stations().size()),
                () -> assertTrue(plan.stations().stream().allMatch(
                        station -> "anchor_requirement_not_occupied_site".equals(station.placementSemantics()))));
    }

    @Test
    void assemblyIdentityIsDeterministicAndInvalidBlockspaceIsRejected() {
        AircraftDesignIR design = design();
        AircraftBlockspaceIR blockspace = new AircraftBlockspaceCompiler().compile(
                design, blockspaceSpec(design, 0.5));
        AircraftAssemblyPlanner planner = new AircraftAssemblyPlanner();
        AircraftAssemblyPlanIR first = planner.plan(blockspace, "skyforge.aircraft.guild_utility_monoplane.assembly.v1");
        AircraftAssemblyPlanIR second = planner.plan(blockspace, "skyforge.aircraft.guild_utility_monoplane.assembly.v1");
        AircraftAssemblyPlanIR renamed = planner.plan(blockspace, "skyforge.aircraft.guild_utility_monoplane.assembly.variant");
        String json = new AircraftAssemblyPlanIRJson().writeString(first);
        AircraftBlockspaceIR invalid = new AircraftBlockspaceIR(
                blockspace.schemaVersion(),
                blockspace.assetId(),
                blockspace.sourceDesignAssetId(),
                blockspace.sourceDesignDigestSha256(),
                blockspace.compilerVersion(),
                blockspace.coordinateSystem(),
                blockspace.declaredTranscriptionAssumptions(),
                blockspace.cells(),
                blockspace.anchors(),
                blockspace.metrics(),
                new AircraftBlockspaceIR.Validation(
                        false,
                        blockspace.validation().scope(),
                        blockspace.validation().doesNotProve(),
                        blockspace.validation().propellerDiskViolations()));

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertNotEquals(first.sha256(), renamed.sha256()),
                () -> assertFalse(json.contains("minecraft:")),
                () -> assertFalse(json.contains("create:")),
                () -> assertFalse(json.contains("sable:")),
                () -> assertFalse(json.contains("aeronautics:")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> planner.plan(invalid, "skyforge.aircraft.invalid.assembly")));
    }

    private static AircraftDesignIR design() {
        return new AircraftDesignCompiler().compile(AircraftDesignCompilerTest.guildUtilitySpec());
    }

    private static AircraftBlockspaceSpec blockspaceSpec(AircraftDesignIR design, double hubRadiusM) {
        return new AircraftBlockspaceSpec(
                AircraftBlockspaceSpec.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.blockspace.v1",
                design.assetId(),
                design.sha256(),
                2.0,
                new AircraftBlockspaceSpec.Mounts(1.2, 1.85, 1.58, 1.45),
                new AircraftBlockspaceSpec.SemanticStations(2.7, 4.6),
                hubRadiusM,
                new AircraftBlockspaceSpec.ValidationLimits(1.0, 0.5));
    }
}
