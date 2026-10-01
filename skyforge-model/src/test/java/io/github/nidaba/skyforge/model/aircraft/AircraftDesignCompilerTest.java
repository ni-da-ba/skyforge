package io.github.nidaba.skyforge.model.aircraft;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AircraftDesignCompilerTest {
    private static final double EPS = 1.0e-12;

    @Test
    void analyticalRelationsPreserveAcceptedFirstPrinciplesEquations() {
        assertAll(
                () -> assertEquals(1240.3125,
                        AircraftAnalyticalMath.dynamicPressure(1.225, 45.0), EPS),
                () -> assertEquals(16.0,
                        AircraftAnalyticalMath.trapezoidArea(10.0, 2.0, 1.2), EPS),
                () -> assertEquals(1.6333333333333333,
                        AircraftAnalyticalMath.meanAerodynamicChord(2.0, 1.2), EPS),
                () -> assertEquals(6.25,
                        AircraftAnalyticalMath.aspectRatio(10.0, 16.0), EPS),
                () -> assertEquals(10787.315,
                        AircraftAnalyticalMath.weightForce(1100.0, 9.80665), 1.0e-9),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> AircraftAnalyticalMath.taperRatio(0.0, 1.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> AircraftAnalyticalMath.inducedDragCoefficient(0.55, 6.25, 1.01)));
    }

    @Test
    void guildUtilitySpecReproducesFrozenAnalyticalSelection() {
        AircraftDesignIR ir = new AircraftDesignCompiler().compile(guildUtilitySpec());

        assertAll(
                () -> assertEquals("skyforge.aircraft.guild_utility_monoplane.v0_1", ir.assetId()),
                () -> assertEquals(9.0, ir.geometry().fuselage().lengthM(), EPS),
                () -> assertEquals(10.0, ir.geometry().wing().spanM(), EPS),
                () -> assertEquals(2.0, ir.geometry().wing().rootChordM(), EPS),
                () -> assertEquals(1.2, ir.geometry().wing().tipChordM(), EPS),
                () -> assertEquals(3.2, ir.geometry().wing().leadingEdgeXM(), EPS),
                () -> assertEquals(3.5, ir.geometry().horizontalTail().spanM(), EPS),
                () -> assertEquals(1.6, ir.geometry().horizontalTail().rootChordM(), EPS),
                () -> assertEquals(1.0, ir.geometry().horizontalTail().tipChordM(), EPS),
                () -> assertEquals(7.3, ir.geometry().horizontalTail().leadingEdgeXM(), EPS),
                () -> assertEquals(1.75, ir.geometry().verticalTail().heightM(), EPS),
                () -> assertEquals(1.4, ir.geometry().verticalTail().rootChordM(), EPS),
                () -> assertEquals(0.5, ir.geometry().verticalTail().tipChordM(), EPS),
                () -> assertEquals(7.2, ir.geometry().verticalTail().leadingEdgeXM(), EPS),
                () -> assertEquals(102, ir.solver().candidateCountFeasible()),
                () -> assertEquals(
                        Map.of(
                                "cruise_lift_residual", 312,
                                "specimen_cg_mac_fraction", 75,
                                "aspect_ratio", 84,
                                "vertical_tail_volume_reference", 3),
                        ir.solver().rejectionCounts()));
    }

    @Test
    void guildUtilityMetricsMatchFrozenAnalyticalAuthority() {
        AircraftDesignIR ir = new AircraftDesignCompiler().compile(guildUtilitySpec());
        AircraftDesignIR.Metrics metrics = ir.metrics();

        assertAll(
                () -> assertEquals(1240.3125, metrics.dynamicPressurePa(), EPS),
                () -> assertEquals(10787.315, metrics.weightN(), 1.0e-9),
                () -> assertEquals(15.813192239858905,
                        metrics.requiredWingAreaM2AtDesignCL(), 1.0e-12),
                () -> assertEquals(16.0, metrics.wingAreaM2(), EPS),
                () -> assertEquals(0.6, metrics.wingTaperRatio(), EPS),
                () -> assertEquals(6.25, metrics.wingAspectRatio(), EPS),
                () -> assertEquals(1.6333333333333333, metrics.wingMacM(), EPS),
                () -> assertEquals(10914.75, metrics.cruiseLiftN(), 1.0e-9),
                () -> assertEquals(0.01181341232734942,
                        metrics.cruiseLiftResidualFraction(), 1.0e-15),
                () -> assertEquals(0.01925774811411934,
                        metrics.analyticalInducedDragCoefficient(), 1.0e-15),
                () -> assertEquals(3.4736363636363636, metrics.cgXM(), EPS),
                () -> assertEquals(0.16753246753246742, metrics.cgMacFraction(), 1.0e-15),
                () -> assertEquals(0.7003348214285713,
                        metrics.horizontalTailVolume(), 1.0e-15),
                () -> assertEquals(0.03997200520833333,
                        metrics.verticalTailVolume(), 1.0e-15));
    }

    @Test
    void productionIrIsDeterministicExplicitlyBoundedAndTargetNeutral() {
        AircraftDesignCompiler compiler = new AircraftDesignCompiler();
        AircraftDesignIR first = compiler.compile(guildUtilitySpec());
        AircraftDesignIR second = compiler.compile(guildUtilitySpec());
        String diagnosticText = new AircraftDesignIRJson().writeString(first);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertTrue(first.validation().passed()),
                () -> assertEquals("analytical_geometry_and_balance_only", first.validation().scope()),
                () -> assertTrue(first.validation().doesNotProve().contains("dynamic stability")),
                () -> assertFalse(first.targetBoundary().containsConcreteTargetResourceNames()),
                () -> assertFalse(first.targetBoundary().targetAdapterApplied()),
                () -> assertFalse(diagnosticText.contains("minecraft:")),
                () -> assertFalse(diagnosticText.contains("create:")),
                () -> assertFalse(diagnosticText.contains("sable:")),
                () -> assertFalse(diagnosticText.contains("aeronautics:")),
                () -> assertEquals(1100.0,
                        first.massLedger().stream().mapToDouble(AircraftDesignIR.MassItem::massKg).sum(),
                        EPS),
                () -> assertEquals("fraction_of_fuselage_length",
                        first.massLedger().get(4).source()),
                () -> assertEquals(4.32, first.massLedger().get(4).stationXM(), EPS));
    }

    @Test
    void canonicalJsonAndDigestProvideStableProductionIdentity() {
        AircraftDesignCompiler compiler = new AircraftDesignCompiler();
        AircraftDesignIR first = compiler.compile(guildUtilitySpec());
        AircraftDesignIR second = compiler.compile(guildUtilitySpec());
        AircraftDesignIRJson json = new AircraftDesignIRJson();
        AircraftDesignIR renamed = new AircraftDesignIR(
                first.schemaVersion(),
                first.assetId() + ".variant",
                first.compilerVersion(),
                first.configuration(),
                first.mission(),
                first.declaredAssumptions(),
                first.referenceTargets(),
                first.geometry(),
                first.massLedger(),
                first.metrics(),
                first.solver(),
                first.validation(),
                first.targetBoundary());

        String canonical = json.writeString(first);
        assertAll(
                () -> assertEquals(canonical, json.writeString(second)),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()),
                () -> assertTrue(canonical.endsWith("\n")),
                () -> assertTrue(canonical.contains("\"schemaVersion\":1")),
                () -> assertTrue(canonical.contains("\"compilerVersion\":\"aircraft-design-compiler-1\"")),
                () -> assertFalse(canonical.contains("minecraft:")),
                () -> assertFalse(canonical.contains("create:")),
                () -> assertFalse(canonical.contains("sable:")),
                () -> assertFalse(canonical.contains("aeronautics:")),
                () -> assertNotEquals(first.sha256(), renamed.sha256()));
    }

    @Test
    void productionIrRejectsMalformedConstructedState() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftDesignIR.Fuselage(Double.NaN, 1.0, 1.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftDesignIR.Wing(10.0, 1.0, 2.0, 3.0, "high", "zero")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftDesignIR.MassItem("invalid", 0.0, 1.0, "test")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new AircraftDesignIR.SolverEvidence(
                                "deterministic",
                                1,
                                Map.of(),
                                List.of("objective"),
                                List.of(Double.NaN))));
    }

    @Test
    void compilerFailsClosedWhenDeclaredMassLedgerDoesNotMatchGrossMass() {
        AircraftDesignSpec valid = guildUtilitySpec();
        List<AircraftDesignSpec.MassItemSpec> invalidLedger = List.of(
                AircraftDesignSpec.MassItemSpec.absolute("engine_and_propulsion", 181.0, 0.8),
                AircraftDesignSpec.MassItemSpec.absolute("crew_and_seats", 170.0, 2.7),
                AircraftDesignSpec.MassItemSpec.absolute("fuel_and_power_reserve", 100.0, 3.4),
                AircraftDesignSpec.MassItemSpec.absolute("utility_cargo", 250.0, 4.6),
                AircraftDesignSpec.MassItemSpec.fuselageFraction(
                        "airframe_structure_and_systems", 400.0, 0.48));
        AircraftDesignSpec invalid = new AircraftDesignSpec(
                valid.schemaVersion(),
                valid.assetId(),
                valid.configuration(),
                valid.mission(),
                valid.declaredAssumptions(),
                valid.referenceTargets(),
                valid.constraints(),
                valid.designDomains(),
                valid.geometryEnvelope(),
                invalidLedger);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new AircraftDesignCompiler().compile(invalid));
        assertTrue(exception.getMessage().contains("does not equal gross mass"));
    }

    static AircraftDesignSpec guildUtilitySpec() {
        return new AircraftDesignSpec(
                AircraftDesignSpec.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.v0_1",
                new AircraftDesignSpec.Configuration(
                        "small_utility_monoplane",
                        "high",
                        1,
                        "nose_tractor",
                        "conventional",
                        2,
                        "light_regional_utility"),
                new AircraftDesignSpec.Mission(1100.0, 1.225, 9.80665, 45.0, 0.55),
                new AircraftDesignSpec.AnalyticalAssumptions(
                        "1976_US_Standard_Atmosphere_sea_level_density_input",
                        "straight_unswept_linear_taper",
                        "straight_unswept_linear_taper",
                        0.8,
                        "declared_specimen_assumption_not_a_fitted_runtime_constant",
                        "declared_component_lumped_masses",
                        "none_v0.1"),
                new AircraftDesignSpec.ReferenceTargets(
                        0.7,
                        0.04,
                        "representative_general_aviation_single_engine_reference_not_universal_acceptance_truth"),
                new AircraftDesignSpec.Constraints(
                        new AircraftDesignSpec.DoubleRange(6.0, 9.5),
                        0.08,
                        new AircraftDesignSpec.DoubleRange(0.15, 0.45),
                        0.2,
                        0.2),
                new AircraftDesignSpec.DesignDomains(
                        List.of(8.0, 9.0, 10.0),
                        List.of(10.0, 11.0, 12.0),
                        List.of(1.8, 2.0, 2.2, 2.4),
                        List.of(0.8, 1.0, 1.2, 1.4),
                        List.of(2.6, 2.8, 3.0, 3.2),
                        List.of(3.5, 4.0, 4.5),
                        List.of(1.2, 1.4, 1.6),
                        List.of(0.6, 0.8, 1.0),
                        1.7,
                        List.of(1.5, 1.75, 2.0),
                        List.of(1.4, 1.6, 1.8),
                        List.of(0.5, 0.7, 0.9),
                        1.8),
                new AircraftDesignSpec.GeometryEnvelope(
                        1.8,
                        2.0,
                        new AircraftDesignSpec.PropellerEnvelope(2.1, 0.15, 1.35)),
                List.of(
                        AircraftDesignSpec.MassItemSpec.absolute(
                                "engine_and_propulsion", 180.0, 0.8),
                        AircraftDesignSpec.MassItemSpec.absolute(
                                "crew_and_seats", 170.0, 2.7),
                        AircraftDesignSpec.MassItemSpec.absolute(
                                "fuel_and_power_reserve", 100.0, 3.4),
                        AircraftDesignSpec.MassItemSpec.absolute(
                                "utility_cargo", 250.0, 4.6),
                        AircraftDesignSpec.MassItemSpec.fuselageFraction(
                                "airframe_structure_and_systems", 400.0, 0.48)));
    }
}
