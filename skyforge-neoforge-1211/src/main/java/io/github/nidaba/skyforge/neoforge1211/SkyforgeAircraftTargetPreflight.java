package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Lowers target-neutral aircraft assembly semantics to a static exact-stack preflight manifest. */
public final class SkyforgeAircraftTargetPreflight {
    public SkyforgeAircraftTargetPreflightIR lower(
            AircraftAssemblyPlanIR assembly,
            String targetAssetId,
            SkyforgeAircraftTargetProfile profile) {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(profile, "profile");
        if (!assembly.validation().passed()) {
            throw new IllegalArgumentException("refusing target preflight from invalid assembly plan");
        }

        List<SkyforgeAircraftTargetPreflightIR.Placement> placements = new ArrayList<>();
        List<SkyforgeAircraftTargetPreflightIR.UnresolvedSite> unresolvedSites = new ArrayList<>();
        List<SkyforgeAircraftTargetPreflightIR.Placement> unresolvedState = new ArrayList<>();
        for (AircraftAssemblyPlanIR.Site site : assembly.sites()) {
            Set<String> required = new LinkedHashSet<>(site.capabilities());
            SkyforgeAircraftTargetProfile.SiteProvider provider = selectProvider(required, profile.siteProviders());
            if (provider == null) {
                unresolvedSites.add(new SkyforgeAircraftTargetPreflightIR.UnresolvedSite(
                        site.point(), site.roles(), sorted(required)));
                continue;
            }
            SkyforgeAircraftTargetPreflightIR.Placement placement = new SkyforgeAircraftTargetPreflightIR.Placement(
                    site.point(),
                    site.roles(),
                    sorted(required),
                    provider.providerId(),
                    provider.resourceId(),
                    provider.evidenceLevel(),
                    provider.stateRule());
            placements.add(placement);
            if (provider.stateRule().startsWith("unresolved:")) {
                unresolvedState.add(placement);
            }
        }

        EnumMap<AircraftBlockspaceIR.AnchorType, AircraftAssemblyPlanIR.Station> assemblyStations =
                new EnumMap<>(AircraftBlockspaceIR.AnchorType.class);
        for (AircraftAssemblyPlanIR.Station station : assembly.stations()) {
            assemblyStations.put(station.type(), station);
        }
        List<SkyforgeAircraftTargetPreflightIR.Station> stations = new ArrayList<>();
        List<SkyforgeAircraftTargetPreflightIR.Station> unresolvedRequiredStations = new ArrayList<>();
        for (AircraftBlockspaceIR.AnchorType type : AircraftBlockspaceIR.AnchorType.values()) {
            AircraftAssemblyPlanIR.Station source = assemblyStations.get(type);
            if (source == null) {
                throw new IllegalArgumentException("assembly missing required station: " + type.id());
            }
            SkyforgeAircraftTargetProfile.StationProvider provider = profile.stationProviders().get(type);
            if (provider == null) {
                throw new IllegalArgumentException("target profile missing station contract: " + type.id());
            }
            SkyforgeAircraftTargetPreflightIR.Station station = new SkyforgeAircraftTargetPreflightIR.Station(
                    type,
                    source.lattice(),
                    source.capabilities(),
                    provider.status(),
                    provider.resourceId(),
                    provider.stateRule(),
                    provider.evidence());
            stations.add(station);
            if (profile.requiredStations().contains(type)
                    && provider.status() != SkyforgeAircraftTargetProfile.StationStatus.SOURCE_VERIFIED) {
                unresolvedRequiredStations.add(station);
            }
        }

        Map<String, Integer> roleCounts = new HashMap<>();
        for (AircraftAssemblyPlanIR.Site site : assembly.sites()) {
            for (AircraftBlockspaceIR.Role role : site.roles()) {
                roleCounts.merge(role.id(), 1, Integer::sum);
            }
        }
        List<SkyforgeAircraftTargetPreflightIR.CompanionCheck> companionChecks = new ArrayList<>();
        int companionFailures = 0;
        for (SkyforgeAircraftTargetProfile.CompanionRequirement requirement : profile.companionRequirements()) {
            int actual = roleCounts.getOrDefault(requirement.role(), 0);
            boolean passed = actual >= requirement.minimumCount();
            if (!passed) {
                companionFailures++;
            }
            companionChecks.add(new SkyforgeAircraftTargetPreflightIR.CompanionCheck(
                    requirement.id(), requirement.role(), requirement.minimumCount(), actual, passed, requirement.evidence()));
        }

        List<SkyforgeAircraftTargetPreflightIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftTargetPreflightIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        boolean staticCoverage = unresolvedSites.isEmpty() && placements.size() == assembly.sites().size();
        boolean schematicReady = staticCoverage
                && unresolvedState.isEmpty()
                && companionFailures == 0
                && unresolvedRequiredStations.isEmpty();
        List<String> blockers = new ArrayList<>();
        if (!unresolvedSites.isEmpty()) blockers.add("unmapped_site_capability_combinations");
        if (!unresolvedState.isEmpty()) blockers.add("unresolved_blockstate_or_resource_rules");
        if (companionFailures > 0) blockers.add("unsatisfied_contraption_companion_requirements");
        if (!unresolvedRequiredStations.isEmpty()) blockers.add("unresolved_required_station_providers");
        TreeSet<String> resources = new TreeSet<>();
        placements.forEach(value -> resources.add(value.resourceId()));

        return new SkyforgeAircraftTargetPreflightIR(
                SkyforgeAircraftTargetPreflightIR.SCHEMA_VERSION,
                targetAssetId,
                assembly.assetId(),
                assembly.sha256(),
                SkyforgeAircraftTargetPreflightIR.COMPILER_VERSION,
                profile.target(),
                profile.profileId(),
                placements,
                stations,
                companionChecks,
                obligations,
                new SkyforgeAircraftTargetPreflightIR.Metrics(
                        assembly.sites().size(),
                        placements.size(),
                        unresolvedSites.size(),
                        unresolvedState.size(),
                        companionFailures,
                        obligations.size(),
                        0,
                        List.copyOf(resources)),
                new SkyforgeAircraftTargetPreflightIR.Readiness(
                        staticCoverage, schematicReady, false, false, blockers),
                new SkyforgeAircraftTargetPreflightIR.Validation(
                        staticCoverage,
                        "target_version_and_capability_preflight_only",
                        List.of(
                                "blockstate orientation correctness where state rules remain unresolved",
                                "Create/Simulated/Aeronautics contraption assembly success",
                                "runtime mass or center of mass",
                                "runtime force magnitude or direction",
                                "kinetic stress sufficiency",
                                "control authority",
                                "stable flight")),
                unresolvedSites,
                unresolvedState,
                unresolvedRequiredStations);
    }

    static SkyforgeAircraftTargetProfile.SiteProvider selectProvider(
            Set<String> required,
            List<SkyforgeAircraftTargetProfile.SiteProvider> providers) {
        return providers.stream()
                .filter(provider -> provider.capabilities().containsAll(required))
                .min(Comparator.comparingInt((SkyforgeAircraftTargetProfile.SiteProvider provider) ->
                                provider.specificityAgainst(required))
                        .thenComparingInt(SkyforgeAircraftTargetProfile.SiteProvider::priority)
                        .thenComparing(SkyforgeAircraftTargetProfile.SiteProvider::providerId))
                .orElse(null);
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}
