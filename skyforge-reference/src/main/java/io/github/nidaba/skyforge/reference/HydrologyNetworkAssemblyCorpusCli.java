package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicNetworkAssemblyPlan;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicNetworkAssemblyPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixed F3E complete terminal-component admission evidence corpus. */
public final class HydrologyNetworkAssemblyCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-network-assembly-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyNetworkAssemblyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L)),
                new Specimen("control-241", descriptor(8L, 81L, 241L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("legacy-control-649", descriptor(8L, 81L, 649L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder terminal = new StringBuilder(
                "specimen,islandKey,terminalCell,watershedTerminal,fate,status,reachCount,blockers\n");
        StringBuilder reach = new StringBuilder(
                "specimen,islandKey,startCell,endCell,status,ordinarySpans,cascades,blockers\n");

        for (Specimen specimen : specimens) {
            SkyIslandHydraulicNetworkAssemblyPlan plan =
                    SkyIslandHydraulicNetworkAssemblyPlanner.plan(specimen.descriptor());

            for (var component : plan.terminalComponents()) {
                terminal.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(component.terminalFate().channelTerminalCellIndex()).append(',')
                        .append(component.terminalFate().watershedTerminalCellIndex()).append(',')
                        .append(component.terminalFate().kind().name()).append(',')
                        .append(component.status().name()).append(',')
                        .append(component.reaches().size()).append(',')
                        .append(sanitize(String.join(" | ", component.blockers())))
                        .append('\n');
            }

            for (var assembly : plan.reachAssemblies()) {
                reach.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(assembly.semanticReach().startCellIndex()).append(',')
                        .append(assembly.semanticReach().endCellIndex()).append(',')
                        .append(assembly.status().name()).append(',')
                        .append(assembly.ordinarySpans().size()).append(',')
                        .append(assembly.cascades().size()).append(',')
                        .append(sanitize(String.join(" | ", assembly.blockers())))
                        .append('\n');
            }
        }

        Files.writeString(out.resolve("terminal-components.csv"), terminal, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("reach-assemblies.csv"), reach, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology network assembly v1

                F3E evaluates complete upstream semantic drainage components ending at authoritative
                terminal fates. QUALIFIED requires every ordinary span, explicit drop, incident
                confluence, and terminal authority in that component to clear its current gate.

                This is admission evidence only. No F3E result mutates terrain or authorizes
                Minecraft discretization.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("terminal-components.csv").toAbsolutePath());
    }

    private static String sanitize(String value) {
        return value.replace(',', ';').replace('\n', ' ');
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
