package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceCascadeHeadCompatibilityPlanner;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceCascadeHeadCompatibilityStatus;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationPolicy;
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
    private static final int LAST_KEY = 1024;
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

                    if (deepSolve) {
                        topologyCandidateCount++;
                        var joint = SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
                        Map<SkyIslandConfluenceCascadeHeadCompatibilityStatus, Integer> counts =
                                new java.util.EnumMap<>(SkyIslandConfluenceCascadeHeadCompatibilityStatus.class);
                        joint.outcomes().forEach(outcome ->
                                counts.merge(outcome.status(), 1, Integer::sum));
                        statusCounts = formatCounts(counts);
                        boolean solved = joint.outcomes().stream().anyMatch(outcome ->
                                outcome.status()
                                        == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED);
                        if (solved) {
                            solvedIdentityCount++;
                            var assembly = SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
                            StringJoiner outcomes = new StringJoiner("|");
                            for (var component : assembly.terminalComponents()) {
                                outcomes.add(component.terminalFate().channelTerminalCellIndex()
                                        + ":" + component.status().name()
                                        + ":" + clean(String.join(" / ", component.blockers())));
                                if (component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                                    qualifiedComponentCount++;
                                }
                            }
                            componentOutcomes = outcomes.toString();
                            diagnostics = joint.outcomes().stream()
                                    .filter(outcome -> outcome.status()
                                            == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED)
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
                            .append(countQualified(componentOutcomes)).append(',')
                            .append(clean(componentOutcomes)).append(',')
                            .append(clean(diagnostics)).append('\n');
                } catch (RuntimeException failure) {
                    planningFailureCount++;
                    identities.append(namespace.province()).append(',')
                            .append(namespace.cluster()).append(',')
                            .append(key).append(',')
                            .append("0,0,0,false,,0,PLANNING_FAILURE,")
                            .append(clean(failure.getClass().getSimpleName() + ": " + failure.getMessage()))
                            .append('\n');
                }
            }
        }

        String summary = "seedHex,namespaceCount,keysPerNamespace,identityCount,topologyCandidateCount,"
                + "solvedIdentityCount,qualifiedComponentCount,planningFailureCount\n"
                + String.format(Locale.ROOT, "%016X", SEED) + ","
                + NAMESPACES.length + ","
                + (LAST_KEY - FIRST_KEY + 1) + ","
                + identityCount + ","
                + topologyCandidateCount + ","
                + solvedIdentityCount + ","
                + qualifiedComponentCount + ","
                + planningFailureCount + "\n";
        Files.writeString(out.resolve("identity-manifest.csv"), identities, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology natural-transition census v1

                Fixed sample: seed 0x534B59464F524745; namespaces 6/61 and 8/81;
                island keys 1 through 1,024 in each namespace.
                The manifest records every identity. The deep F3H/F3I/F3E planners run only
                when semantic topology contains both a confluence and a CASCADE profile.
                A qualified component is still continuous mathematical evidence only:
                it grants no terrain, water, voxel, Minecraft, or human-review authority.
                No D2/E2 limits, corridors, terminal-CASCADE rules, or geometry were changed.
                """, StandardCharsets.UTF_8);
        System.out.println(summary);
    }

    private static int countConfluences(java.util.List<SkyIslandSemanticChannelReach> reaches) {
        Map<Integer, Integer> incoming = new HashMap<>();
        Map<Integer, Integer> outgoing = new HashMap<>();
        for (SkyIslandSemanticChannelReach reach : reaches) {
            outgoing.merge(reach.startCellIndex(), 1, Integer::sum);
            incoming.merge(reach.endCellIndex(), 1, Integer::sum);
            incoming.putIfAbsent(reach.startCellIndex(), 0);
            outgoing.putIfAbsent(reach.endCellIndex(), 0);
        }
        return (int) java.util.stream.Stream.concat(incoming.keySet().stream(), outgoing.keySet().stream())
                .distinct()
                .filter(node -> incoming.getOrDefault(node, 0) > 1
                        || outgoing.getOrDefault(node, 0) > 1)
                .count();
    }

    private static String formatCounts(
            Map<SkyIslandConfluenceCascadeHeadCompatibilityStatus, Integer> counts) {
        StringJoiner result = new StringJoiner("|");
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(entry.getKey().name() + ":" + entry.getValue()));
        return result.toString();
    }

    private static int countQualified(String outcomes) {
        if (outcomes.isBlank()) {
            return 0;
        }
        return (int) java.util.Arrays.stream(outcomes.split("\\|"))
                .filter(value -> value.contains(":QUALIFIED:"))
                .count();
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