package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Retained corrected v0.13.1 static yaw-control topology. Live actuation remains a downstream gate. */
public record SkyforgeAircraftYawControlProfile(
        String profileId,
        String supersededYawDigestSha256,
        String correctionReason,
        String supersededGlueDomainName,
        List<AircraftBlockspaceIR.LatticePoint> fixedFinCoordinates,
        List<AircraftBlockspaceIR.LatticePoint> airGapCoordinates,
        List<Placement> placements,
        List<GlueDomain> replacementGlueDomains,
        List<String> sourceEvidence,
        List<RuntimeObligation> runtimeObligations) {
    public static final String PROFILE_ID = "skyforge.yaw_control.simulated.guild_utility.v0_13_1";
    public static final String SUPERSEDED_V013_DIGEST_SHA256 =
            "59f451bb7bb71433016af249f5ded7a87aed47b69b2a26215b4f573322f2d951";

    public SkyforgeAircraftYawControlProfile {
        profileId = requireText("profileId", profileId);
        if (!PROFILE_ID.equals(profileId)) throw new IllegalArgumentException("unsupported yaw-control profile: " + profileId);
        supersededYawDigestSha256 = requireSha256(supersededYawDigestSha256);
        if (!SUPERSEDED_V013_DIGEST_SHA256.equals(supersededYawDigestSha256)) {
            throw new IllegalArgumentException("v0.13.1 must retain the rejected v0.13 digest lineage");
        }
        correctionReason = requireText("correctionReason", correctionReason);
        if (!correctionReason.contains("34764106343") || !correctionReason.contains("[16,4,0]")) {
            throw new IllegalArgumentException("v0.13.1 correction must retain the exact failed predecessor evidence");
        }
        supersededGlueDomainName = requireText("supersededGlueDomainName", supersededGlueDomainName);
        if (!"vertical_tail".equals(supersededGlueDomainName)) {
            throw new IllegalArgumentException("v0.13.1 must supersede the vertical_tail glue domain");
        }

        fixedFinCoordinates = sortedUniquePoints("fixedFinCoordinates", fixedFinCoordinates);
        airGapCoordinates = sortedUniquePoints("airGapCoordinates", airGapCoordinates);
        if (!fixedFinCoordinates.equals(List.of(
                point(15, 4, 0), point(15, 5, 0), point(15, 6, 0), point(15, 7, 0),
                point(16, 4, 0), point(16, 5, 0), point(16, 6, 0)))) {
            throw new IllegalArgumentException("v0.13.1 fixed-fin coordinate contract changed");
        }
        if (!airGapCoordinates.equals(List.of(
                point(17, 4, 0), point(17, 5, 0), point(17, 6, 0), point(17, 7, 0)))) {
            throw new IllegalArgumentException("v0.13.1 air-gap coordinate contract changed");
        }

        ArrayList<Placement> orderedPlacements = new ArrayList<>(Objects.requireNonNull(placements, "placements"));
        orderedPlacements.sort(Comparator.comparing(Placement::point).thenComparing(Placement::role));
        placements = List.copyOf(orderedPlacements);
        Set<String> roles = new HashSet<>();
        Set<AircraftBlockspaceIR.LatticePoint> points = new HashSet<>();
        for (Placement placement : placements) {
            if (!roles.add(placement.role())) throw new IllegalArgumentException("duplicate yaw-control role: " + placement.role());
            if (!points.add(placement.point())) throw new IllegalArgumentException("duplicate yaw-control coordinate: " + placement.point());
        }
        Set<String> requiredRoles = Set.of("yaw_swivel_bearing", "yaw_air_gap", "rudder_seed", "rudder_1", "rudder_2", "rudder_3");
        if (!roles.equals(requiredRoles)) throw new IllegalArgumentException("v0.13.1 yaw-control roles changed: " + roles);
        Map<String, Placement> byRole = placements.stream().collect(java.util.stream.Collectors.toMap(Placement::role, value -> value));
        requirePlacement(byRole.get("yaw_swivel_bearing"), Mode.ADD_MAIN, point(18, 3, 0), "yaw_control_hinge",
                "simulated:swivel_bearing", Map.of("assembled", "false", "facing", "up", "powered", "false"), null);
        requirePlacement(byRole.get("yaw_air_gap"), Mode.REMOVE_MAIN, point(17, 4, 0), "yaw_control_air_gap",
                "minecraft:air", Map.of(), new ReplaceExpectation(
                        "airframe_aerodynamic_surface", "create:white_sail", Map.of("facing", "south")));
        requirePlacement(byRole.get("rudder_seed"), Mode.ADD_CONTROL_CHILD, point(18, 4, 0), "yaw_control_surface",
                "simulated:white_symmetric_sail", Map.of("axis", "z"), null);
        requirePlacement(byRole.get("rudder_1"), Mode.ADD_CONTROL_CHILD, point(18, 5, 0), "yaw_control_surface",
                "simulated:white_symmetric_sail", Map.of("axis", "z"), null);
        requirePlacement(byRole.get("rudder_2"), Mode.ADD_CONTROL_CHILD, point(18, 6, 0), "yaw_control_surface",
                "simulated:white_symmetric_sail", Map.of("axis", "z"), null);
        requirePlacement(byRole.get("rudder_3"), Mode.ADD_CONTROL_CHILD, point(18, 7, 0), "yaw_control_surface",
                "simulated:white_symmetric_sail", Map.of("axis", "z"), null);

        ArrayList<GlueDomain> orderedGlue = new ArrayList<>(Objects.requireNonNull(replacementGlueDomains, "replacementGlueDomains"));
        orderedGlue.sort(Comparator.comparing(GlueDomain::name));
        replacementGlueDomains = List.copyOf(orderedGlue);
        Map<String, GlueDomain> glueByName = replacementGlueDomains.stream()
                .collect(java.util.stream.Collectors.toMap(GlueDomain::name, value -> value));
        if (!glueByName.keySet().equals(Set.of("vertical_tail_fixed", "yaw_bearing_mount", "rudder_child"))) {
            throw new IllegalArgumentException("v0.13.1 replacement glue-domain set changed");
        }
        requireGlue(glueByName.get("vertical_tail_fixed"), point(15, 3, 0), point(16, 7, 0), GlueOwner.PARENT);
        requireGlue(glueByName.get("yaw_bearing_mount"), point(17, 3, 0), point(18, 3, 0), GlueOwner.PARENT);
        requireGlue(glueByName.get("rudder_child"), point(18, 4, 0), point(18, 7, 0), GlueOwner.CONTROL_CHILD);

        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        if (sourceEvidence.stream().noneMatch(value -> value.contains("50443d00afa06e0982b45f40cd686f7ecf978132"))) {
            throw new IllegalArgumentException("v0.13.1 profile must cite exact Simulated source authority");
        }
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        Set<String> obligationIds = new HashSet<>();
        for (RuntimeObligation obligation : runtimeObligations) {
            if (!obligationIds.add(obligation.id())) throw new IllegalArgumentException("duplicate runtime obligation: " + obligation.id());
        }
        if (!obligationIds.equals(Set.of(
                "yaw_v0131_primary_sable_recapture_probe",
                "rudder_swivel_child_capture_probe",
                "rudder_neutral_constraint_probe",
                "rudder_actuation_probe",
                "rudder_yaw_force_probe"))) {
            throw new IllegalArgumentException("v0.13.1 runtime-obligation set changed");
        }
    }

    public static SkyforgeAircraftYawControlProfile retainedC11() {
        String symmetricContract = "Simulated exact source 50443d00afa06e0982b45f40cd686f7ecf978132: symmetric sail axis Z; runtime force/actuation remains unverified here";
        return new SkyforgeAircraftYawControlProfile(
                PROFILE_ID,
                SUPERSEDED_V013_DIGEST_SHA256,
                "Exact-stack run 34764106343 disproved v0.13 parent capture: relative hinge-spar cell [16,4,0] remained at the source. v0.13.1 preserves that failure as evidence and replaces the spruce separator with a true x=17 air-gap hinge plus an aft UP-facing Swivel Bearing.",
                "vertical_tail",
                List.of(
                        point(15, 4, 0), point(15, 5, 0), point(15, 6, 0), point(15, 7, 0),
                        point(16, 4, 0), point(16, 5, 0), point(16, 6, 0)),
                List.of(point(17, 4, 0), point(17, 5, 0), point(17, 6, 0), point(17, 7, 0)),
                List.of(
                        placement("yaw_swivel_bearing", Mode.ADD_MAIN, 18, 3, 0, "yaw_control_hinge", "simulated:swivel_bearing",
                                Map.of("assembled", "false", "facing", "up", "powered", "false"), null,
                                "Simulated exact source 50443d00afa06e0982b45f40cd686f7ecf978132: Swivel assembly seeds pos.relative(FACING); UP seeds [18,4,0]"),
                        placement("yaw_air_gap", Mode.REMOVE_MAIN, 17, 4, 0, "yaw_control_air_gap", "minecraft:air", Map.of(),
                                new ReplaceExpectation("airframe_aerodynamic_surface", "create:white_sail", Map.of("facing", "south")),
                                "Rejected run 34764106343 requires a true x=17 air gap; no ordinary block or regular-sail path may bridge fixed fin to rudder"),
                        placement("rudder_seed", Mode.ADD_CONTROL_CHILD, 18, 4, 0, "yaw_control_surface", "simulated:white_symmetric_sail", Map.of("axis", "z"), null, symmetricContract),
                        placement("rudder_1", Mode.ADD_CONTROL_CHILD, 18, 5, 0, "yaw_control_surface", "simulated:white_symmetric_sail", Map.of("axis", "z"), null, symmetricContract),
                        placement("rudder_2", Mode.ADD_CONTROL_CHILD, 18, 6, 0, "yaw_control_surface", "simulated:white_symmetric_sail", Map.of("axis", "z"), null, symmetricContract),
                        placement("rudder_3", Mode.ADD_CONTROL_CHILD, 18, 7, 0, "yaw_control_surface", "simulated:white_symmetric_sail", Map.of("axis", "z"), null, symmetricContract)),
                List.of(
                        new GlueDomain("vertical_tail_fixed", point(15, 3, 0), point(16, 7, 0), GlueOwner.PARENT),
                        new GlueDomain("yaw_bearing_mount", point(17, 3, 0), point(18, 3, 0), GlueOwner.PARENT),
                        new GlueDomain("rudder_child", point(18, 4, 0), point(18, 7, 0), GlueOwner.CONTROL_CHILD)),
                List.of(
                        "Exact-stack run 34764106343 rejected v0.13: relative spruce hinge-spar cell [16,4,0] remained at source.",
                        "Simulated exact source commit 50443d00afa06e0982b45f40cd686f7ecf978132 registers simulated:swivel_bearing, seeds child assembly at pos.relative(FACING), and supplies symmetric-sail axis semantics.",
                        "Accepted PLATFORM-008 SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE proves an UP-facing Swivel with four symmetric-sail child cells can be captured and driven only through real Create kinetics on the exact stack.",
                        "Create regular SailBlock movement attachment propagates within the sail plane; the true one-block x=17 air gap removes the rejected parent attachment path."),
                List.of(
                        new RuntimeObligation("yaw_v0131_primary_sable_recapture_probe", "apply the corrected one-add/one-remove parent patch, four-cell rudder and seven-domain glue plan; verify 118 parent + 9 propeller + 4 rudder cells transfer into one Sable sublevel"),
                        new RuntimeObligation("rudder_swivel_child_capture_probe", "assemble the moved UP-facing Swivel Bearing and verify exactly the four rudder cells form one attached child while all 118 parent cells remain"),
                        new RuntimeObligation("rudder_neutral_constraint_probe", "verify the newly assembled Swivel starts at finite zero target angle without unintended deflection"),
                        new RuntimeObligation("rudder_actuation_probe", "drive the Swivel only through accepted Create kinetics and measure signed actuation plus inverse commanded neutral return"),
                        new RuntimeObligation("rudder_yaw_force_probe", "under controlled airflow measure aircraft-specific rudder pose and yaw response without retuning the analytical model")));
    }

    private static void requirePlacement(
            Placement placement,
            Mode mode,
            AircraftBlockspaceIR.LatticePoint point,
            String kind,
            String resourceId,
            Map<String, String> blockState,
            ReplaceExpectation replaces) {
        if (placement == null || placement.mode() != mode || !point.equals(placement.point())
                || !kind.equals(placement.kind()) || !resourceId.equals(placement.resourceId())
                || !blockState.equals(placement.blockState()) || !Objects.equals(replaces, placement.replaces())) {
            throw new IllegalArgumentException("v0.13.1 source-backed placement contract mismatch for " + (placement == null ? "missing role" : placement.role()));
        }
    }

    private static void requireGlue(GlueDomain domain, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to, GlueOwner owner) {
        if (domain == null || !from.equals(domain.from()) || !to.equals(domain.to()) || owner != domain.owner()) {
            throw new IllegalArgumentException("v0.13.1 glue contract mismatch");
        }
    }

    private static Placement placement(String role, Mode mode, int x, int y, int z, String kind, String resourceId,
            Map<String, String> blockState, ReplaceExpectation replaces, String sourceContract) {
        return new Placement(role, mode, point(x, y, z), kind, resourceId, blockState, replaces, sourceContract);
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }

    private static List<AircraftBlockspaceIR.LatticePoint> sortedUniquePoints(String property, List<AircraftBlockspaceIR.LatticePoint> values) {
        ArrayList<AircraftBlockspaceIR.LatticePoint> copy = new ArrayList<>(Objects.requireNonNull(values, property));
        copy.sort(Comparator.naturalOrder());
        if (new HashSet<>(copy).size() != copy.size()) throw new IllegalArgumentException(property + " contains duplicates");
        return List.copyOf(copy);
    }

    public enum Mode { ADD_MAIN, REMOVE_MAIN, ADD_CONTROL_CHILD }
    public enum GlueOwner { PARENT, CONTROL_CHILD }

    public record Placement(
            String role,
            Mode mode,
            AircraftBlockspaceIR.LatticePoint point,
            String kind,
            String resourceId,
            Map<String, String> blockState,
            ReplaceExpectation replaces,
            String sourceContract) {
        public Placement {
            role = requireText("placement.role", role);
            mode = Objects.requireNonNull(mode, "mode");
            point = Objects.requireNonNull(point, "point");
            kind = requireText("placement.kind", kind);
            resourceId = requireNamespaced("placement.resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
            if (mode == Mode.REMOVE_MAIN && replaces == null) throw new IllegalArgumentException("REMOVE_MAIN requires a source expectation");
            if (mode != Mode.REMOVE_MAIN && replaces != null) throw new IllegalArgumentException("only REMOVE_MAIN may declare a source expectation");
            sourceContract = requireText("placement.sourceContract", sourceContract);
        }
    }

    public record ReplaceExpectation(String kind, String resourceId, Map<String, String> blockState) {
        public ReplaceExpectation {
            kind = requireText("replace.kind", kind);
            resourceId = requireNamespaced("replace.resourceId", resourceId);
            blockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(blockState, "blockState")));
        }
    }

    public record GlueDomain(String name, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to, GlueOwner owner) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            owner = Objects.requireNonNull(owner, "owner");
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }

    private static String requireNamespaced(String property, String value) {
        value = requireText(property, value);
        if (!value.contains(":")) throw new IllegalArgumentException(property + " must be namespaced");
        return value;
    }

    private static String requireSha256(String value) {
        String sha = requireText("sha256", value).toLowerCase(java.util.Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return sha;
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
