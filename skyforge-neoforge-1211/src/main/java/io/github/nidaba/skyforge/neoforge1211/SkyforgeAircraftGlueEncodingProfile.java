package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Retained Create 6.0.10 bounded Super Glue lowering contract. */
public record SkyforgeAircraftGlueEncodingProfile(
        String profileId,
        Encoding encoding,
        List<GlueDomain> glueDomains,
        List<ForbiddenGlueEdge> forbiddenGlueEdges,
        List<RuntimeObligation> runtimeObligations) {

    public SkyforgeAircraftGlueEncodingProfile {
        profileId = requireText("profileId", profileId);
        encoding = Objects.requireNonNull(encoding, "encoding");
        glueDomains = List.copyOf(Objects.requireNonNull(glueDomains, "glueDomains"));
        if (glueDomains.isEmpty()) throw new IllegalArgumentException("v0.11 requires at least one bounded glue domain");
        Set<String> names = new HashSet<>();
        for (GlueDomain domain : glueDomains) {
            if (!names.add(domain.name())) throw new IllegalArgumentException("duplicate glue domain name: " + domain.name());
        }
        forbiddenGlueEdges = List.copyOf(Objects.requireNonNull(forbiddenGlueEdges, "forbiddenGlueEdges"));
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        Set<String> ids = new HashSet<>();
        for (RuntimeObligation obligation : runtimeObligations) {
            if (!ids.add(obligation.id())) throw new IllegalArgumentException("duplicate runtime obligation id: " + obligation.id());
        }
        Set<String> required = Set.of(
                "create_glue_command_registry_probe",
                "super_glue_entity_realization_probe",
                "physics_assembler_capture_probe",
                "glue_persistence_after_sublevel_move");
        if (!ids.containsAll(required)) throw new IllegalArgumentException("v0.11 profile missing required runtime obligations");
    }

    public static SkyforgeAircraftGlueEncodingProfile retainedC11() {
        return new SkyforgeAircraftGlueEncodingProfile(
                "skyforge.glue.create_6_0_10.guild_utility.v0_11",
                new Encoding(
                        "bounded_super_glue_domain_cover_of_main_body_tree",
                        "create glue",
                        2,
                        24,
                        "Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span",
                        List.of(
                                "Create 6.0.10 GlueCommand constructs SuperGlueEntity(world, SuperGlueEntity.span(from,to)) and adds it to the level",
                                "Create 6.0.10 SuperGlueEntity.contains tests block centers against the glue AABB",
                                "Simulated SimAssemblyContraption.checkAndCacheGlue traverses adjacent blocks when both centers are contained by one SuperGlueEntity",
                                "Skyforge C12 exact-stack acceptance uses bounded multi-block SuperGlueEntity domains and passes on this target stack",
                                "AIRCRAFT-001 edge-exact runtime probe exposed an aft-fuselage traversal miss, so the tree is retained as the proof graph while runtime glue is lowered to bounded domains")),
                List.of(
                        domain("fuselage_core", 0, 1, 0, 18, 3, 0),
                        domain("wing", 7, 3, -10, 10, 4, 10),
                        domain("horizontal_tail", 15, 3, -3, 17, 3, 3),
                        domain("vertical_tail", 15, 3, 0, 17, 7, 0)),
                List.of(new ForbiddenGlueEdge(
                        new AircraftBlockspaceIR.LatticePoint(0, 3, 0),
                        new AircraftBlockspaceIR.LatticePoint(-1, 3, 0))),
                List.of(
                        new RuntimeObligation(
                                "create_glue_command_registry_probe",
                                "on the exact target stack, verify /create glue is registered and usable at permission level 2 before applying the generated fixture"),
                        new RuntimeObligation(
                                "super_glue_entity_realization_probe",
                                "verify the generated bounded SuperGlueEntity domains realize all accepted proof-tree edges while containing no propeller payload cell or forbidden dynamic boundary"),
                        new RuntimeObligation(
                                "physics_assembler_capture_probe",
                                "activate the generated Physics Assembler and verify the declared main body plus bearing-reachable propeller payload transfer into one Sable sublevel"),
                        new RuntimeObligation(
                                "glue_persistence_after_sublevel_move",
                                "after primary Sable assembly and save/reload, verify moved glue remains sufficient for controlled disassembly/reassembly")));
    }

    private static GlueDomain domain(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new GlueDomain(name, new AircraftBlockspaceIR.LatticePoint(x1, y1, z1), new AircraftBlockspaceIR.LatticePoint(x2, y2, z2));
    }

    public record Encoding(
            String policy,
            String commandRoot,
            int requiredPermissionLevel,
            int maxSelectionDimensionBlocks,
            String sourceContract,
            List<String> sourceEvidence) {
        public Encoding {
            policy = requireText("encoding.policy", policy);
            commandRoot = requireText("encoding.commandRoot", commandRoot);
            if (!"bounded_super_glue_domain_cover_of_main_body_tree".equals(policy)) {
                throw new IllegalArgumentException("v0.11 requires bounded Super Glue domain cover encoding");
            }
            if (!"create glue".equals(commandRoot)) throw new IllegalArgumentException("v0.11 requires Create 6.0.10 /create glue command");
            if (requiredPermissionLevel != 2) throw new IllegalArgumentException("Create glue command requires permission level 2");
            if (maxSelectionDimensionBlocks <= 0) throw new IllegalArgumentException("bounded glue encoding requires positive max selection dimension");
            sourceContract = requireText("encoding.sourceContract", sourceContract);
            sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        }
    }

    public record GlueDomain(String name, AircraftBlockspaceIR.LatticePoint from, AircraftBlockspaceIR.LatticePoint to) {
        public GlueDomain {
            name = requireText("glueDomain.name", name);
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
        }
    }

    public record ForbiddenGlueEdge(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        public ForbiddenGlueEdge {
            a = Objects.requireNonNull(a, "a");
            b = Objects.requireNonNull(b, "b");
            if (a.compareTo(b) > 0) {
                AircraftBlockspaceIR.LatticePoint swap = a;
                a = b;
                b = swap;
            }
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
