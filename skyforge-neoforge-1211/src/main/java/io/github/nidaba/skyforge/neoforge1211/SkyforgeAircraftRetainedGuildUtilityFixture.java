package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanner;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceCompiler;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceSpec;
import io.github.nidaba.skyforge.model.aircraft.AircraftDesignCompiler;
import io.github.nidaba.skyforge.model.aircraft.AircraftDesignIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftDesignSpec;
import java.util.List;

/** Canonical retained Guild utility-monoplane compiler chain through AIRCRAFT-PROD-011. */
final class SkyforgeAircraftRetainedGuildUtilityFixture {
    private SkyforgeAircraftRetainedGuildUtilityFixture() {}

    static Fixture compileV012() {
        AircraftDesignIR design = new AircraftDesignCompiler().compile(designSpec());
        AircraftBlockspaceIR blockspace = new AircraftBlockspaceCompiler().compile(design, blockspaceSpec(design));
        AircraftAssemblyPlanIR assembly = new AircraftAssemblyPlanner().plan(
                blockspace, "skyforge.aircraft.guild_utility_monoplane.assembly.v1");
        SkyforgeAircraftTargetPreflightIR target = new SkyforgeAircraftTargetPreflight().lower(
                assembly,
                "skyforge.aircraft.guild_utility_monoplane.target_preflight.v1",
                SkyforgeAircraftTargetProfile.retainedC11());
        SkyforgeAircraftPropulsionIR propulsion = new SkyforgeAircraftPropulsionRealizer().realize(
                assembly,
                target,
                "skyforge.aircraft.guild_utility_monoplane.propulsion.v1",
                SkyforgeAircraftPropulsionProfile.retainedC11());
        SkyforgeAircraftSurfaceStateIR surface = new SkyforgeAircraftSurfaceStateResolver().resolve(
                target,
                propulsion,
                "skyforge.aircraft.guild_utility_monoplane.surface_state.v1",
                SkyforgeAircraftSurfaceStateProfile.retainedC11());
        SkyforgeAircraftTailLoweringIR tail = new SkyforgeAircraftTailJunctionLowerer().lower(
                target,
                surface,
                "skyforge.aircraft.guild_utility_monoplane.tail_lowering.v1",
                SkyforgeAircraftTailLoweringProfile.retainedC11());
        SkyforgeAircraftPilotStationIR pilot = new SkyforgeAircraftPilotStationRealizer().realize(
                target,
                propulsion,
                tail,
                "skyforge.aircraft.guild_utility_monoplane.pilot_station.v1",
                SkyforgeAircraftPilotStationProfile.retainedC11());
        SkyforgeAircraftProbeManifestIR manifest = new SkyforgeAircraftProbeManifestEmitter().emit(
                target,
                propulsion,
                tail,
                pilot,
                "skyforge.aircraft.guild_utility_monoplane.probe_manifest.v1",
                SkyforgeAircraftProbeManifestProfile.retainedC11());
        SkyforgeAircraftAssemblyFixtureIR fixture = new SkyforgeAircraftAssemblyFixturePlanner().plan(
                manifest,
                "skyforge.aircraft.guild_utility_monoplane.assembly_fixture.v1",
                SkyforgeAircraftAssemblyFixtureProfile.retainedC11());
        SkyforgeAircraftGlueEncodingIR glue = new SkyforgeAircraftGlueEncoder().encode(
                fixture,
                "skyforge.aircraft.guild_utility_monoplane.glue_encoding.v1",
                SkyforgeAircraftGlueEncodingProfile.retainedC11());
        SkyforgeAircraftPowertrainIR powertrain = new SkyforgeAircraftPowertrainLowerer().lower(
                manifest,
                fixture,
                glue,
                "skyforge.aircraft.guild_utility_monoplane.powertrain.v1",
                SkyforgeAircraftPowertrainProfile.retainedC11());
        return new Fixture(design, blockspace, assembly, target, propulsion, surface, tail, pilot, manifest, fixture, glue, powertrain);
    }

    static YawFixture compileV0131() {
        Fixture v012 = compileV012();
        SkyforgeAircraftYawControlIR yawControl = new SkyforgeAircraftYawControlLowerer().lower(
                v012.manifest(),
                v012.assemblyFixture(),
                v012.glue(),
                v012.powertrain(),
                "skyforge.aircraft.guild_utility_monoplane.yaw_control.v1",
                SkyforgeAircraftYawControlProfile.retainedC11());
        return new YawFixture(v012, yawControl);
    }

    static SteeringYawSourceFixture compileSteeringYawSource() {
        YawFixture v0131 = compileV0131();
        SkyforgeAircraftRudderControlAuthority authority =
                SkyforgeAircraftRudderControlAuthority.accepted(v0131.yawControl());
        SkyforgeAircraftSteeringYawSourceIR steeringYawSource = new SkyforgeAircraftSteeringYawSourceLowerer().lower(
                v0131.yawControl(),
                authority,
                "skyforge.aircraft.guild_utility_monoplane.steering_yaw_source.v1",
                SkyforgeAircraftSteeringYawSourceProfile.retainedC11());
        return new SteeringYawSourceFixture(v0131, authority, steeringYawSource);
    }

    private static AircraftDesignSpec designSpec() {
        return new AircraftDesignSpec(
                AircraftDesignSpec.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.v0_1",
                new AircraftDesignSpec.Configuration(
                        "small_utility_monoplane", "high", 1, "nose_tractor", "conventional", 2, "light_regional_utility"),
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
                        0.7, 0.04, "representative_general_aviation_single_engine_reference_not_universal_acceptance_truth"),
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
                        1.8, 2.0, new AircraftDesignSpec.PropellerEnvelope(2.1, 0.15, 1.35)),
                List.of(
                        AircraftDesignSpec.MassItemSpec.absolute("engine_and_propulsion", 180.0, 0.8),
                        AircraftDesignSpec.MassItemSpec.absolute("crew_and_seats", 170.0, 2.7),
                        AircraftDesignSpec.MassItemSpec.absolute("fuel_and_power_reserve", 100.0, 3.4),
                        AircraftDesignSpec.MassItemSpec.absolute("utility_cargo", 250.0, 4.6),
                        AircraftDesignSpec.MassItemSpec.fuselageFraction("airframe_structure_and_systems", 400.0, 0.48)));
    }

    private static AircraftBlockspaceSpec blockspaceSpec(AircraftDesignIR design) {
        return new AircraftBlockspaceSpec(
                AircraftBlockspaceSpec.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.blockspace.v1",
                design.assetId(),
                design.sha256(),
                2.0,
                new AircraftBlockspaceSpec.Mounts(1.2, 1.85, 1.58, 1.45),
                new AircraftBlockspaceSpec.SemanticStations(2.7, 4.6),
                0.5,
                new AircraftBlockspaceSpec.ValidationLimits(1.0, 0.5));
    }

    record Fixture(
            AircraftDesignIR design,
            AircraftBlockspaceIR blockspace,
            AircraftAssemblyPlanIR assembly,
            SkyforgeAircraftTargetPreflightIR target,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftSurfaceStateIR surface,
            SkyforgeAircraftTailLoweringIR tail,
            SkyforgeAircraftPilotStationIR pilot,
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR assemblyFixture,
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain) {}

    record YawFixture(Fixture v012, SkyforgeAircraftYawControlIR yawControl) {}

    record SteeringYawSourceFixture(
            YawFixture v0131,
            SkyforgeAircraftRudderControlAuthority rudderAuthority,
            SkyforgeAircraftSteeringYawSourceIR steeringYawSource) {}
}
