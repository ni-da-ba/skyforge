package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Fixed F4A component-gated continuous terrain-candidate evidence. */
public final class HydrologyComponentTerrainCandidateCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-component-terrain-candidate-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyComponentTerrainCandidateCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L)),
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder components = new StringBuilder(
                "specimen,islandKey,terminalCell,f3eStatus,f4aDisposition,reasons\n");
        StringBuilder reaches = new StringBuilder(
                "specimen,islandKey,terminalCell,startCell,endCell,samples,affectedSamples,"
                        + "minDeltaPotential,meanDeltaPotential,maxDeltaPotential\n");

        for (Specimen specimen : specimens) {
            SkyIslandComponentFluvialTerrainCandidatePlan plan =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(specimen.descriptor());

            Map<Integer, String> disposition = new HashMap<>();
            Map<Integer, String> reasons = new HashMap<>();
            plan.realizedComponents().forEach(component ->
                    disposition.put(
                            component.terminalFate().channelTerminalCellIndex(),
                            "REALIZED"));
            plan.deferredQualifiedComponents().forEach(deferral -> {
                int terminal =
                        deferral.component().terminalFate().channelTerminalCellIndex();
                disposition.put(terminal, "DEFERRED");
                reasons.put(
                        terminal,
                        deferral.reasons().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining("|")));
            });

            plan.assemblyPlan().terminalComponents().forEach(component -> {
                int terminal =
                        component.terminalFate().channelTerminalCellIndex();
                components.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(terminal).append(',')
                        .append(component.status().name()).append(',')
                        .append(disposition.getOrDefault(terminal, "EXCLUDED")).append(',')
                        .append(reasons.getOrDefault(terminal, ""))
                        .append('\n');
            });

            for (var component : plan.realizedComponents()) {
                int terminal =
                        component.terminalFate().channelTerminalCellIndex();
                for (var assembly : component.reaches()) {
                    SkyIslandHydraulicReachGeometry reach =
                            plan.terrainField().acceptedReaches().stream()
                                    .filter(candidate -> {
                                        var semantic =
                                                candidate.geomorphicRoute().semanticReach();
                                        return semantic.startCellIndex()
                                                        == assembly.semanticReach()
                                                                .startCellIndex()
                                                && semantic.endCellIndex()
                                                        == assembly.semanticReach()
                                                                .endCellIndex();
                                    })
                                    .findFirst()
                                    .orElseThrow();

                    double min = 0.0;
                    double max = 0.0;
                    double sum = 0.0;
                    int affected = 0;
                    boolean first = true;
                    for (var point : reach.centerline().points()) {
                        var sample = plan.terrainField().sampleDetailed(point);
                        double delta = sample.terrainDeltaPotential();
                        if (first) {
                            min = delta;
                            max = delta;
                            first = false;
                        } else {
                            min = Math.min(min, delta);
                            max = Math.max(max, delta);
                        }
                        sum += delta;
                        if (sample.provenance().isPresent()) {
                            affected++;
                        }
                    }
                    reaches.append(specimen.name()).append(',')
                            .append(specimen.descriptor().identity().islandKey()).append(',')
                            .append(terminal).append(',')
                            .append(assembly.semanticReach().startCellIndex()).append(',')
                            .append(assembly.semanticReach().endCellIndex()).append(',')
                            .append(reach.centerline().points().size()).append(',')
                            .append(affected).append(',')
                            .append(format(min)).append(',')
                            .append(format(sum / reach.centerline().points().size())).append(',')
                            .append(format(max))
                            .append('\n');
                }
            }
        }

        Files.writeString(out.resolve("component-candidates.csv"), components, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("realized-reaches.csv"), reaches, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology component terrain candidate v1

                F4A realizes only complete F3E-qualified components that require no transition-
                specific terrain primitive. Hydraulic geometry is reconstructed from F3D solved
                full-span samples on accepted C2 centerlines and requalified after continuous
                cross-section realization.

                This is continuous candidate evidence only. It is not Minecraft authority.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("component-candidates.csv").toAbsolutePath());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
