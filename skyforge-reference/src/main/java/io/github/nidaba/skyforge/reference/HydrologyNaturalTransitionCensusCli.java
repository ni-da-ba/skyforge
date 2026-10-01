package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceCascadeHeadCompatibilityPlanner;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceCascadeHeadCompatibilityStatus;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicAssemblyStatus;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicNetworkAssemblyPlanner;
import io.github.nidaba.skyforge.world.SkyIslandSemanticChannelReach;
import io.github.nidaba.skyforge.world.SkyIslandSemanticChannelReachPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Fixed-grid discovery of naturally emitted, internally coupled confluence/CASCADE components.
 *
 * <p>The scan is diagnostic only. Every potentially relevant identity is evaluated by the
 * accepted F3H/F3I/F3E planners; no synthetic transition geometry or policy changes are made.
 */
public final class HydrologyNaturalTransitionCensusCli {
    public static final String EVIDENCE_ID = "hydrology-natural-transition-census-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final int FIRST_KEY = 1;
    private static final int LAST_KEY = 256;
    private static final Namespace[] NAMESPACES = {
        new Namespace(6L, 61L),
        new Namespace(8L, 81L)
    };

    private HydrologyNaturalTransitionCensusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder identities = new StringBuilder(
                "namespaceProvince,namespaceCluster,islandKey,semanticReachCount,confluenceNodeCount,"
                        + "cascadeProfileCount,deepSolve,statusCounts,qualifiedComponents,componentOutcomes,diagnostics\n");
        long identityCount = 0;
        long topologyCandidateCount = 0;
        long solvedIdentityCount = 0;
        long qualifiedComponentCount = 0;
        long planningFailureCount = 0;
        long constraintRejectedIdentityCount = 0;

        for (Namespace namespace : NAMESPACES) {
            for (int key = FIRST_KEY; key <= LAST_KEY; key++) {
                identityCount++;
                SkyIslandDescriptor descriptor = descriptor(namespace, key);
                try {
                    var semantic = SkyIslandSemanticChannelReachPlanner.plan(descriptor);
                    int confluenceCount = countConfluences(semantic.reaches());
                    int cascadeProfiles = (int) semantic.reaches().stream()
                            .flatMap(reach -> reach.profiles().stream())
                            .filter(profile -> profile.kind()
                                    == io.github.nidaba.skyforge.world.SkyIslandChannelProfileKind.CASCADE)
                            .count();
                    boolean deepSolve = confluenceCount > 0 && cascadeProfiles > 0;
                    String statusCounts = "";
                    String componentOutcomes = "";
                    String diagnostics = "";
                    int qualifiedComponentsForIdentity = 0;

                    if (deepSolve) {
                        topologyCandidateCount++;
                        try {
                            var joint = SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
                            Map<SkyIslandConfluenceCascadeHeadCompatibilityStatus, Integer> counts =
                                    new java.util.EnumMap<>(SkyIslandConfluenceCascadeHeadCompatibilityStatus.class);
                            joint.outcomes().forEach(outcome ->
                                    counts.merge(outcome.status(), 1, Integer::sum));
                            statusCounts = joint.outcomes().isEmpty()
                                    ? "NO_NATURAL_F3H_BOUNDARY_MATCH:1"
                                    : formatCounts(counts);
                            var solvedOutcomes = joint.outcomes().stream()
                                    .filter(outcome -> outcome.status()
                                            == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED)
                                    .toList();
                            if (!solvedOutcomes.isEmpty()) {
                                solvedIdentityCount++;
                                var assembly = SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
                                StringJoiner outcomes = new StringJoiner("|");
                                var qualifiedComponents = new java.util.HashSet<>();
                                for (var solvedOutcome : solvedOutcomes) {
                                    int node = solvedOutcome.confluence().transitionSite().nodeCellIndex();
                                    var site = solvedOutcome.cascade().transitionSite();
                                    boolean admittedByF3I = assembly.ordinarySpanPlan().jointPlan().outcomes()
                                            .stream()
                                            .anyMatch(value -> value.status()
                                                            == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED
                                                    && value.confluence().transitionSite().nodeCellIndex() == node
                                                    && value.cascade().transitionSite().equals(site));
                                    if (!admittedByF3I) {
                                        outcomes.add(node + "@" + site.reachStartCellIndex() + "->"
                                                + site.reachEndCellIndex() + ":F3I_NOT_ADMITTED");
                                        continue;
                                    }
                                    var owners = assembly.terminalComponents().stream()
                                            .filter(component -> component.reaches().stream().anyMatch(reach ->
                                                    reach.semanticReach().startCellIndex()
                                                            == site.reachStartCellIndex()
                                                            && reach.semanticReach().endCellIndex()
                                                            == site.reachEndCellIndex()))
                                            .toList();
                                    if (owners.size() != 1) {
                                        outcomes.add(node + "@" + site.reachStartCellIndex() + "->"
                                                + site.reachEndCellIndex() + ":COMPONENT_OWNERS="
                                                + owners.size());
                                        continue;
                                    }
                                    var owner = owners.getFirst();
                                    outcomes.add(node + "@" + site.reachStartCellIndex() + "->"
                                            + site.reachEndCellIndex() + ":" + owner.status().name()
                                            + ":" + clean(String.join(" / ", owner.blockers())));
                                    if (owner.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                                        qualifiedComponents.add(owner);
                                    }
                                }
                                qualifiedComponentsForIdentity = qualifiedComponents.size();
                                qualifiedComponentCount += qualifiedComponentsForIdentity;
                                componentOutcomes = outcomes.toString();
                                diagnostics = solvedOutcomes.stream()
                                        .map(outcome -> outcome.confluence().transitionSite().nodeCellIndex()
                                                + "@" + outcome.cascade().transitionSite().reachStartCellIndex()
                                                + "->" + outcome.cascade().transitionSite().reachEndCellIndex())
                                        .distinct()
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining("|"));
                            } else {
                                diagnostics = joint.outcomes().stream()
                                        .map(outcome -> outcome.status().name() + ":"
                                                + clean(outcome.diagnostic().orElse("no F3H boundary match")))
                                        .distinct()
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining("|"));
                                if (joint.outcomes().isEmpty()) {
                                    diagnostics = "no natural F3H boundary match";
                                }
                            }
                        } catch (IllegalStateException rejection) {
                            if (!"relaxed centerline escaped semantic corridor"
                                    .equals(rejection.getMessage())) {
                                throw rejection;
                            }
                            constraintRejectedIdentityCount++;
                            statusCounts = "SEMANTIC_CORRIDOR_HARD_REJECTION:1";
                            componentOutcomes = "F3H_NOT_SOLVED:semantic corridor hard constraint";
                            diagnostics = clean(rejection.getClass().getSimpleName() + ": "
                                    + rejection.getMessage());
                        }
                    }

                    identities.append(namespace.province()).append(',')
                            .append(namespace.cluster()).append(',')
                            .append(key).append(',')
                            .append(semantic.reaches().size()).append(',')
                            .append(confluenceCount).append(',')
                            .append(cascadeProfiles).append(',')
                            .append(deepSolve).append(',')
                            .append(clean(statusCounts)).append(',')
                            .append(qualifiedComponentsForIdentity).append(',')
                            .append(clean(componentOutcomes)).append(',')
                            .append(clean(diagnostics)).append('\n');
                } catch (RuntimeException failure) {
                    planningFailureCount++;
                    String diagnostic = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                    System.err.println(
                            "NATURAL_TRANSITION_CENSUS_PLANNING_FAILURE namespace="
                                    + namespace.province() + "/" + namespace.cluster()
                                    + " key=" + key + " " + clean(diagnostic));
                    failure.printStackTrace(System.err);
                    identities.append(namespace.province()).append(',')
                            .append(namespace.cluster()).append(',')
                            .append(key).append(',')
                            .append("0,0,0,false,,0,PLANNING_FAILURE,")
                            .append(clean(diagnostic))
                            .append('\n');
                }
            }
        }

        String summary = "seedHex,namespaceCount,keysPerNamespace,identityCount,topologyCandidateCount,"
                + "solvedIdentityCount,qualifiedComponentCount,constraintRejectedIdentityCount,planningFailureCount\n"
                + String.format(Locale.ROOT, "%016X", SEED) + ","
                + NAMESPACES.length + ","
                + (LAST_KEY - FIRST_KEY + 1) + ","
                + identityCount + ","
                + topologyCandidateCount + ","
                + solvedIdentityCount + ","
                + qualifiedComponentCount + ","
                + constraintRejectedIdentityCount + ","
                + planningFailureCount + "\n";
        Files.writeString(out.resolve("identity-manifest.csv"), identities, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology natural-transition census v1

                Fixed sample: seed 0x534B59464F524745; namespaces 6/61 and 8/81;
                island keys 1 through 256 in each namespace.
                The manifest records every identity. The deep F3H/F3I/F3E planners run only
                when semantic topology contains both a confluence and a CASCADE profile.
                Expected hard semantic-corridor rejections are recorded as candidate outcomes, not\n                census execution failures. Unexpected planner exceptions still fail the task.\n                A qualified component is still continuous mathematical evidence only:
                it grants no terrain, water, voxel, Minecraft, or human-review authority.
                No D2/E2 limits, corridors, terminal-CASCADE rules, or geometry were changed.
                """, StandardCharsets.UTF_8);
        System.out.println(summary);
        if (planningFailureCount > 0) {
            throw new IllegalStateException(
                    "natural transition census encountered " + planningFailureCount
                            + " planning failures; inspect identity-manifest.csv");
        }
    }

    private static int countConfluences(java.util.List<SkyIslandSemanticChannelReach> reaches) {
        Map<Integer, Integer> incoming = new HashMap<>();
        for (SkyIslandSemanticChannelReach reach : reaches) {
            incoming.merge(reach.endCellIndex(), 1, Integer::sum);
        }
        return (int) incoming.values().stream().filter(count -> count > 1).count();
    }

    private static String formatCounts(
            Map<SkyIslandConfluenceCascadeHeadCompatibilityStatus, Integer> counts) {
        StringJoiner result = new StringJoiner("|");
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(entry.getKey().name() + ":" + entry.getValue()));
        return result.toString();
    }

    private static String clean(String value) {
        return value.replace(',', ';').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static SkyIslandDescriptor descriptor(Namespace namespace, int key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, namespace.province(), namespace.cluster(), key));
    }

    private record Namespace(long province, long cluster) {}
}