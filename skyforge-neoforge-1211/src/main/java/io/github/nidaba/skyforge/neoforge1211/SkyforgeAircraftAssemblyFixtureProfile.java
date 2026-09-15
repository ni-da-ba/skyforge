package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Retained static profile for AIRCRAFT v0.10 Physics Assembler fixture planning. */
public record SkyforgeAircraftAssemblyFixtureProfile(
        String profileId,
        List<String> mainBodyKinds,
        List<String> nestedChildKinds,
        AircraftBlockspaceIR.LatticePoint seedCoordinate,
        Translation assemblerOffsetFromSeed,
        PhysicsAssembler physicsAssembler,
        List<RuntimeObligation> runtimeObligations) {

    public SkyforgeAircraftAssemblyFixtureProfile {
        profileId = requireText("profileId", profileId);
        mainBodyKinds = sortedNonEmptyDistinct("mainBodyKinds", mainBodyKinds);
        nestedChildKinds = sortedNonEmptyDistinct("nestedChildKinds", nestedChildKinds);
        Set<String> overlap = new HashSet<>(mainBodyKinds);
        overlap.retainAll(nestedChildKinds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("mainBodyKinds and nestedChildKinds must be disjoint: " + overlap);
        }
        seedCoordinate = Objects.requireNonNull(seedCoordinate, "seedCoordinate");
        assemblerOffsetFromSeed = Objects.requireNonNull(assemblerOffsetFromSeed, "assemblerOffsetFromSeed");
        if (!assemblerOffsetFromSeed.equals(new Translation(0, -1, 0))) {
            throw new IllegalArgumentException("bounded v0.10 requires assembler one block below seed");
        }
        physicsAssembler = Objects.requireNonNull(physicsAssembler, "physicsAssembler");
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        Set<String> ids = new HashSet<>();
        for (RuntimeObligation obligation : runtimeObligations) {
            if (!ids.add(obligation.id())) {
                throw new IllegalArgumentException("duplicate runtime obligation id: " + obligation.id());
            }
        }
        Set<String> required = Set.of(
                "physics_assembler_capture_probe",
                "adhesion_application_probe",
                "nested_propeller_capture_probe");
        if (!ids.containsAll(required)) {
            throw new IllegalArgumentException("v0.10 profile missing required runtime obligations");
        }
    }

    public static SkyforgeAircraftAssemblyFixtureProfile retainedC11() {
        return new SkyforgeAircraftAssemblyFixtureProfile(
                "skyforge.assembly_fixture.simulated_1_3_2.v0_10",
                List.of(
                        "airframe_structure",
                        "airframe_aerodynamic_surface",
                        "propeller_bearing",
                        "pilot_occupancy_station"),
                List.of("propeller_hub", "propeller_sail"),
                new AircraftBlockspaceIR.LatticePoint(0, 2, 0),
                new Translation(0, -1, 0),
                new PhysicsAssembler(
                        "simulated:physics_assembler",
                        Map.of("face", "ceiling", "facing", "north"),
                        "released Simulated source registers physics_assembler; face=ceiling sticky direction is UP and assembleOrDisassemble seeds assembly from that neighbor"),
                List.of(
                        new RuntimeObligation(
                                "physics_assembler_capture_probe",
                                "place the exact compiled fixture, actuate Physics Assembler, and verify the intended main-body block count enters one Sable sublevel"),
                        new RuntimeObligation(
                                "adhesion_application_probe",
                                "encode the logical adhesion intent using supported Super Glue/attachment semantics, then prove every main-body placement is captured without crossing nested-bearing or future control-hinge boundaries"),
                        new RuntimeObligation(
                                "nested_propeller_capture_probe",
                                "after main-body assembly, assemble the Propeller Bearing and verify hub plus eight symmetric sails form the child BearingContraption rather than joining the main adhesion graph")));
    }

    public record Translation(int dx, int dy, int dz) {}

    public record PhysicsAssembler(String resourceId, Map<String, String> blockState, String evidence) {
        public PhysicsAssembler {
            resourceId = requireText("physicsAssembler.resourceId", resourceId);
            if (!"simulated:physics_assembler".equals(resourceId)) {
                throw new IllegalArgumentException("bounded v0.10 requires source-verified simulated:physics_assembler");
            }
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
            if (!"ceiling".equals(blockState.get("face"))) {
                throw new IllegalArgumentException("assembler must use face=ceiling so sticky facing is UP");
            }
            String facing = blockState.get("facing");
            if (!Set.of("north", "south", "east", "west").contains(facing)) {
                throw new IllegalArgumentException("assembler horizontal facing must be legal");
            }
            evidence = requireText("physicsAssembler.evidence", evidence);
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }

    private static List<String> sortedNonEmptyDistinct(String property, List<String> values) {
        Objects.requireNonNull(values, property);
        if (values.isEmpty()) throw new IllegalArgumentException(property + " must not be empty");
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) copy.add(requireText(property + " value", value));
        copy.sort(Comparator.naturalOrder());
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(property + " must be distinct");
        }
        return List.copyOf(copy);
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
