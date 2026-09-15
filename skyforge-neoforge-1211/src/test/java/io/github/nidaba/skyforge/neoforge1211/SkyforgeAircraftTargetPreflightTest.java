package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftTargetPreflightTest {
    @Test
    void retainedTargetMapsCurrentAircraftVocabularyWithoutClaimingRuntimeReadiness() {
        AircraftAssemblyPlanIR assembly = productionVocabularyAssembly();
        SkyforgeAircraftTargetPreflightIR first = new SkyforgeAircraftTargetPreflight().lower(
                assembly,
                "skyforge.aircraft.guild_utility_monoplane.target_preflight.v1",
                SkyforgeAircraftTargetProfile.retainedC11());
        SkyforgeAircraftTargetPreflightIR second = new SkyforgeAircraftTargetPreflight().lower(
                assembly,
                "skyforge.aircraft.guild_utility_monoplane.target_preflight.v1",
                SkyforgeAircraftTargetProfile.retainedC11());

        assertAll(
                () -> assertTrue(first.validation().passed()),
                () -> assertTrue(first.readiness().staticCapabilityCoveragePassed()),
                () -> assertFalse(first.readiness().schematicEmissionReady()),
                () -> assertFalse(first.readiness().runtimeQualificationReady()),
                () -> assertFalse(first.readiness().flightQualified()),
                () -> assertEquals(assembly.assetId(), first.sourceAssemblyAssetId()),
                () -> assertEquals(assembly.sha256(), first.sourceAssemblyDigestSha256()),
                () -> assertEquals(assembly.sites().size(), first.metrics().mappedSiteCount()),
                () -> assertEquals(0, first.metrics().unresolvedSiteCount()),
                () -> assertEquals(
                        List.of("create:white_sail", "minecraft:spruce_planks"),
                        first.metrics().staticResourceIds()),
                () -> assertTrue(first.readiness().blockers().contains("unresolved_blockstate_or_resource_rules")),
                () -> assertTrue(first.readiness().blockers().contains("unsatisfied_contraption_companion_requirements")),
                () -> assertTrue(first.readiness().blockers().contains("unresolved_required_station_providers")),
                () -> assertEquals(first, second),
                () -> assertEquals(first.sha256(), second.sha256()),
                () -> assertEquals(64, first.sha256().length()));

        Set<AircraftBlockspaceIR.Role> covered = assembly.sites().stream()
                .flatMap(site -> site.roles().stream())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(AircraftBlockspaceIR.Role.values()), covered);
    }

    @Test
    void providerSelectionUsesSemanticSpecificityBeforePriority() {
        SkyforgeAircraftTargetProfile.SiteProvider exact = new SkyforgeAircraftTargetProfile.SiteProvider(
                "exact",
                "minecraft:stone",
                Set.of("rigid_physics_member"),
                "test",
                "none",
                100);
        SkyforgeAircraftTargetProfile.SiteProvider generic = new SkyforgeAircraftTargetProfile.SiteProvider(
                "generic",
                "minecraft:oak_planks",
                Set.of("rigid_physics_member", "rigid_load_path"),
                "test",
                "none",
                0);

        assertEquals(
                exact,
                SkyforgeAircraftTargetPreflight.selectProvider(Set.of("rigid_physics_member"), List.of(generic, exact)));
    }

    @Test
    void unresolvedCapabilityFailsStaticCoverage() {
        AircraftAssemblyPlanIR valid = productionVocabularyAssembly();
        AircraftAssemblyPlanIR.Site unresolved = new AircraftAssemblyPlanIR.Site(
                new AircraftBlockspaceIR.LatticePoint(99, 0, 0),
                List.of(AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                List.of("unknown_aircraft_capability"));
        AircraftAssemblyPlanIR invalidForTarget = new AircraftAssemblyPlanIR(
                valid.schemaVersion(),
                valid.assetId(),
                valid.sourceBlockspaceAssetId(),
                valid.sourceBlockspaceDigestSha256(),
                valid.compilerVersion(),
                valid.coordinateSystem(),
                append(valid.sites(), unresolved),
                valid.stations(),
                new AircraftAssemblyPlanIR.Metrics(9, 8, 1, 1, true, true),
                valid.validation());

        SkyforgeAircraftTargetPreflightIR result = new SkyforgeAircraftTargetPreflight().lower(
                invalidForTarget,
                "skyforge.aircraft.unresolved.target_preflight.v1",
                SkyforgeAircraftTargetProfile.retainedC11());

        assertAll(
                () -> assertFalse(result.validation().passed()),
                () -> assertFalse(result.readiness().staticCapabilityCoveragePassed()),
                () -> assertEquals(1, result.unresolvedSites().size()),
                () -> assertTrue(result.readiness().blockers().contains("unmapped_site_capability_combinations")));
    }

    @Test
    void duplicateProvidersAndMissingStationContractsFailClosed() {
        SkyforgeAircraftTargetProfile.SiteProvider provider = new SkyforgeAircraftTargetProfile.SiteProvider(
                "duplicate",
                "minecraft:stone",
                Set.of("rigid_physics_member"),
                "test",
                "none",
                1);
        SkyforgeAircraftTargetProfile retained = SkyforgeAircraftTargetProfile.retainedC11();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAircraftTargetProfile(
                        "duplicates",
                        retained.target(),
                        List.of(provider, provider),
                        retained.stationProviders(),
                        retained.requiredStations(),
                        retained.companionRequirements(),
                        retained.runtimeObligations()));

        EnumMap<AircraftBlockspaceIR.AnchorType, SkyforgeAircraftTargetProfile.StationProvider> missing =
                new EnumMap<>(AircraftBlockspaceIR.AnchorType.class);
        missing.putAll(retained.stationProviders());
        missing.remove(AircraftBlockspaceIR.AnchorType.PILOT_STATION);
        SkyforgeAircraftTargetProfile incomplete = new SkyforgeAircraftTargetProfile(
                "missing-station",
                retained.target(),
                retained.siteProviders(),
                missing,
                retained.requiredStations(),
                retained.companionRequirements(),
                retained.runtimeObligations());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAircraftTargetPreflight().lower(
                        productionVocabularyAssembly(),
                        "skyforge.aircraft.missing_station.target_preflight.v1",
                        incomplete));
    }

    @Test
    void noProviderReturnsNullRatherThanInventingFallback() {
        assertNull(SkyforgeAircraftTargetPreflight.selectProvider(
                Set.of("not_provided"), SkyforgeAircraftTargetProfile.retainedC11().siteProviders()));
    }

    private static AircraftAssemblyPlanIR productionVocabularyAssembly() {
        List<AircraftAssemblyPlanIR.Site> sites = List.of(
                site(0, 0, 0, AircraftBlockspaceIR.Role.FUSELAGE_SPINE),
                site(1, 1, 0, AircraftBlockspaceIR.Role.WING_SURFACE_INTENT),
                site(2, 1, 0, AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT),
                site(3, 2, 0, AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT),
                site(4, 0, 0, AircraftBlockspaceIR.Role.WING_ATTACH_INTENT),
                site(5, 0, 0, AircraftBlockspaceIR.Role.TAIL_ATTACH_INTENT),
                new AircraftAssemblyPlanIR.Site(
                        new AircraftBlockspaceIR.LatticePoint(6, 1, 0),
                        List.of(AircraftBlockspaceIR.Role.WING_SURFACE_INTENT, AircraftBlockspaceIR.Role.WING_ATTACH_INTENT),
                        List.of("aerodynamic_lift_surface", "rigid_load_path", "rigid_physics_member")));
        List<AircraftAssemblyPlanIR.Station> stations = List.of(
                station(AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS, 0, 0, 0),
                station(AircraftBlockspaceIR.AnchorType.CG_REFERENCE, 6, 0, 0),
                station(AircraftBlockspaceIR.AnchorType.PILOT_STATION, 8, 1, 0),
                station(AircraftBlockspaceIR.AnchorType.CARGO_STATION, 10, 1, 0));
        return new AircraftAssemblyPlanIR(
                AircraftAssemblyPlanIR.SCHEMA_VERSION,
                "skyforge.aircraft.guild_utility_monoplane.assembly.v1",
                "skyforge.aircraft.guild_utility_monoplane.blockspace.v1",
                "a".repeat(64),
                AircraftAssemblyPlanIR.COMPILER_VERSION,
                new AircraftBlockspaceIR.CoordinateSystem(
                        "nose_to_tail", "up", "starboard_positive", "integer_lattice", 2.0),
                sites,
                stations,
                new AircraftAssemblyPlanIR.Metrics(8, 7, 1, 1, true, true),
                new AircraftAssemblyPlanIR.Validation(
                        true,
                        "coordinate_unique_semantic_assembly_only",
                        List.of("target resource selection", "runtime assembly")));
    }

    private static AircraftAssemblyPlanIR.Site site(int x, int y, int z, AircraftBlockspaceIR.Role role) {
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

    private static <T> List<T> append(List<T> values, T extra) {
        java.util.ArrayList<T> copy = new java.util.ArrayList<>(values);
        copy.add(extra);
        return List.copyOf(copy);
    }
}
