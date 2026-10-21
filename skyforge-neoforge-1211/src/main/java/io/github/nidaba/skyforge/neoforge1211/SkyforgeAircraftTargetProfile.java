package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Static exact-stack provider authority for aircraft target preflight. */
public record SkyforgeAircraftTargetProfile(
        String profileId,
        TargetStack target,
        List<SiteProvider> siteProviders,
        Map<AircraftBlockspaceIR.AnchorType, StationProvider> stationProviders,
        Set<AircraftBlockspaceIR.AnchorType> requiredStations,
        List<CompanionRequirement> companionRequirements,
        List<RuntimeObligation> runtimeObligations) {

    public SkyforgeAircraftTargetProfile {
        profileId = requireText("profileId", profileId);
        target = Objects.requireNonNull(target, "target");
        siteProviders = List.copyOf(Objects.requireNonNull(siteProviders, "siteProviders"));
        if (siteProviders.isEmpty()) {
            throw new IllegalArgumentException("siteProviders must not be empty");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (SiteProvider provider : siteProviders) {
            if (!ids.add(provider.providerId())) {
                throw new IllegalArgumentException("duplicate providerId: " + provider.providerId());
            }
        }
        EnumMap<AircraftBlockspaceIR.AnchorType, StationProvider> stations =
                new EnumMap<>(AircraftBlockspaceIR.AnchorType.class);
        stations.putAll(Objects.requireNonNull(stationProviders, "stationProviders"));
        stationProviders = Map.copyOf(new LinkedHashMap<>(stations));
        requiredStations = Set.copyOf(Objects.requireNonNull(requiredStations, "requiredStations"));
        companionRequirements = List.copyOf(
                Objects.requireNonNull(companionRequirements, "companionRequirements"));
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
    }

    public static SkyforgeAircraftTargetProfile retainedC11() {
        return new SkyforgeAircraftTargetProfile(
                "skyforge.target.create_aeronautics_1_3_2_mc1_21_1.v1",
                new TargetStack(
                        "1.21.1",
                        "NeoForge 21.1.249",
                        "6.0.10+mc1.21.1",
                        "2.0.5+mc1.21.1",
                        "bundled_with_create_aeronautics_1.3.2",
                        "1.3.2+mc1.21.1"),
                List.of(
                        new SiteProvider(
                                "minecraft_spruce_structure_v1",
                                "minecraft:spruce_planks",
                                Set.of("rigid_physics_member", "rigid_load_path"),
                                "registry_identity_known_runtime_mass_unverified",
                                "none",
                                50),
                        new SiteProvider(
                                "create_white_sail_lift_v1",
                                "create:white_sail",
                                Set.of("aerodynamic_lift_surface", "rigid_physics_member", "rigid_load_path"),
                                "registry_identity_known_runtime_force_sign_unverified",
                                "unresolved:derive_facing_from_surface_role_and_verify_force_sign_in_runtime",
                                10)),
                Map.of(
                        AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS,
                        new StationProvider(
                                StationStatus.SOURCE_VERIFIED,
                                "aeronautics:propeller_bearing",
                                "facing=west; thrust_direction_requires_runtime_probe",
                                "released source identity only"),
                        AircraftBlockspaceIR.AnchorType.PILOT_STATION,
                        new StationProvider(
                                StationStatus.UNRESOLVED,
                                null,
                                null,
                                "control-interface resource/binding remains downstream"),
                        AircraftBlockspaceIR.AnchorType.CARGO_STATION,
                        new StationProvider(
                                StationStatus.SEMANTIC_ONLY,
                                null,
                                null,
                                "payload interface may remain volume-only"),
                        AircraftBlockspaceIR.AnchorType.CG_REFERENCE,
                        new StationProvider(
                                StationStatus.SEMANTIC_ONLY,
                                null,
                                null,
                                "measurement reference; never a block placement")),
                Set.of(AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS, AircraftBlockspaceIR.AnchorType.PILOT_STATION),
                List.of(new CompanionRequirement(
                        "propeller_requires_sail_geometry", "propeller_sail_intent", 2,
                        "propeller payload is downstream of the v0.3 airframe site set")),
                List.of(
                        new RuntimeObligation("registry_and_blockstate_probe", "resolve emitted resources and legal states"),
                        new RuntimeObligation("physics_assembly_probe", "assemble complete vehicle and verify Sable body"),
                        new RuntimeObligation("propeller_assembly_probe", "assemble propeller child with sufficient sail geometry"),
                        new RuntimeObligation("propeller_force_direction_probe", "measure target force direction without fitting analytical constants"),
                        new RuntimeObligation("sail_orientation_probe", "measure sail force direction for compiled surface roles"),
                        new RuntimeObligation("runtime_mass_cg_probe", "measure runtime mass/CG separately from analytical CG"),
                        new RuntimeObligation("kinetic_stress_probe", "verify network connectivity, sign, RPM, and stress"),
                        new RuntimeObligation("control_binding_probe", "verify pilot input reaches intended mechanisms")));
    }

    public record TargetStack(
            String minecraft, String loader, String create, String sable, String simulated, String aeronautics) {
        public TargetStack {
            minecraft = requireText("minecraft", minecraft);
            loader = requireText("loader", loader);
            create = requireText("create", create);
            sable = requireText("sable", sable);
            simulated = requireText("simulated", simulated);
            aeronautics = requireText("aeronautics", aeronautics);
        }
    }

    public record SiteProvider(
            String providerId,
            String resourceId,
            Set<String> capabilities,
            String evidenceLevel,
            String stateRule,
            int priority) {
        public SiteProvider {
            providerId = requireText("providerId", providerId);
            resourceId = requireResourceId(resourceId);
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            if (capabilities.isEmpty()) {
                throw new IllegalArgumentException("provider capabilities must not be empty");
            }
            for (String capability : capabilities) {
                requireText("capability", capability);
            }
            evidenceLevel = requireText("evidenceLevel", evidenceLevel);
            stateRule = requireText("stateRule", stateRule);
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
        }

        int specificityAgainst(Set<String> required) {
            return (int) capabilities.stream().filter(capability -> !required.contains(capability)).count();
        }

        static final Comparator<SiteProvider> ORDER = Comparator.comparing(SiteProvider::providerId);
    }

    public enum StationStatus { SOURCE_VERIFIED, SEMANTIC_ONLY, UNRESOLVED }

    public record StationProvider(StationStatus status, String resourceId, String stateRule, String evidence) {
        public StationProvider {
            status = Objects.requireNonNull(status, "status");
            if (resourceId != null) {
                resourceId = requireResourceId(resourceId);
            }
            if (stateRule != null) {
                stateRule = requireText("stateRule", stateRule);
            }
            evidence = requireText("evidence", evidence);
            if (status == StationStatus.SOURCE_VERIFIED && resourceId == null) {
                throw new IllegalArgumentException("source-verified station requires resourceId");
            }
        }
    }

    public record CompanionRequirement(String id, String role, int minimumCount, String evidence) {
        public CompanionRequirement {
            id = requireText("companion id", id);
            role = requireText("companion role", role);
            if (minimumCount < 1) {
                throw new IllegalArgumentException("minimumCount must be positive");
            }
            evidence = requireText("companion evidence", evidence);
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtime obligation id", id);
            method = requireText("runtime obligation method", method);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireResourceId(String value) {
        String resource = requireText("resourceId", value);
        if (!resource.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid resourceId: " + value);
        }
        return resource;
    }
}
