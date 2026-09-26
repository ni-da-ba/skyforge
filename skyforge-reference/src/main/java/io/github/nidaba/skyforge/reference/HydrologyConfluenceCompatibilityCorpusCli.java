package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceHeadCompatibilityOutcome;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceHeadCompatibilityPlan;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceHeadCompatibilityPlanner;
import io.github.nidaba.skyforge.world.SkyIslandConfluenceHeadCompatibilityStatus;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F3B confluence head-compatibility evidence corpus. */
public final class HydrologyConfluenceCompatibilityCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-confluence-compatibility-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyConfluenceCompatibilityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("control-241", descriptor(8L, 81L, 241L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("legacy-control-649", descriptor(8L, 81L, 649L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,nodeCell,status,legs,nodeLowerWorld,nodeUpperWorld,"
                        + "nodeHeadWorld,objective,primalResidual,stationarityResidual,"
                        + "complementarityResidual,dualResidual,diagnostic\n");

        for (Specimen specimen : specimens) {
            SkyIslandConfluenceHeadCompatibilityPlan plan =
                    SkyIslandConfluenceHeadCompatibilityPlanner.plan(specimen.descriptor());
            for (SkyIslandConfluenceHeadCompatibilityOutcome outcome : plan.outcomes()) {
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(outcome.geometry().transitionSite().nodeCellIndex()).append(',')
                        .append(outcome.status().name()).append(',')
                        .append(outcome.geometry().legs().size()).append(',')
                        .append(format(outcome.nodeLowerHeadWorldUnits())).append(',')
                        .append(format(outcome.nodeUpperHeadWorldUnits())).append(',')
                        .append(outcome.nodeHeadWorldUnits().map(HydrologyConfluenceCompatibilityCorpusCli::format).orElse(""))
                        .append(',');

                if (outcome.solve().isPresent()) {
                    var solve = outcome.solve().orElseThrow();
                    csv.append(format(solve.objective())).append(',')
                            .append(format(solve.primalResidual())).append(',')
                            .append(format(solve.stationarityResidual())).append(',')
                            .append(format(solve.complementarityResidual())).append(',')
                            .append(format(solve.dualFeasibilityResidual())).append(',');
                } else {
                    csv.append(",,,,,");
                }
                csv.append(escape(outcome.diagnostic().orElse(""))).append('\n');
            }
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology confluence compatibility v1

                Evidence only. F3B solves finite confluence head compatibility with one shared node
                variable, D2-derived pointwise envelopes, and directional ordinary-grade bounds.
                CASCADE_COUPLED is an explicit defer state, not acceptance. This corpus grants no
                terrain or Minecraft authority.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String escape(String value) {
        return '"' + value.replace(""", """") + '"';
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
