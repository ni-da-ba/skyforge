package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCascadeHeadCompatibilityOutcome;
import io.github.nidaba.skyforge.world.SkyIslandCascadeHeadCompatibilityPlan;
import io.github.nidaba.skyforge.world.SkyIslandCascadeHeadCompatibilityPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F3C authored-CASCADE head-discontinuity evidence corpus. */
public final class HydrologyCascadeCompatibilityCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-cascade-compatibility-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyCascadeCompatibilityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("control-241", descriptor(8L, 81L, 241L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("legacy-control-649", descriptor(8L, 81L, 649L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,reachStart,reachEnd,firstProfile,lastProfileExclusive,"
                        + "profileCount,status,pathLength,authoredMaxDropWorld,solvedDropWorld,"
                        + "upstreamHeadWorld,downstreamHeadWorld,objective,primalResidual,diagnostic\n");

        for (Specimen specimen : specimens) {
            SkyIslandCascadeHeadCompatibilityPlan plan =
                    SkyIslandCascadeHeadCompatibilityPlanner.plan(specimen.descriptor());
            for (SkyIslandCascadeHeadCompatibilityOutcome outcome : plan.outcomes()) {
                var site = outcome.geometry().transitionSite();
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(site.reachStartCellIndex()).append(',')
                        .append(site.reachEndCellIndex()).append(',')
                        .append(site.firstProfileIndex()).append(',')
                        .append(site.lastProfileIndexExclusive()).append(',')
                        .append(site.profileCount()).append(',')
                        .append(outcome.status().name()).append(',')
                        .append(format(outcome.geometry().pathLength())).append(',')
                        .append(format(outcome.authoredMaximumDropWorldUnits())).append(',')
                        .append(outcome.solvedDropWorldUnits()
                                .map(HydrologyCascadeCompatibilityCorpusCli::format)
                                .orElse(""))
                        .append(',')
                        .append(outcome.upstreamHeadWorldUnits()
                                .map(HydrologyCascadeCompatibilityCorpusCli::format)
                                .orElse(""))
                        .append(',')
                        .append(outcome.downstreamHeadWorldUnits()
                                .map(HydrologyCascadeCompatibilityCorpusCli::format)
                                .orElse(""))
                        .append(',');
                if (outcome.solve().isPresent()) {
                    var solve = outcome.solve().orElseThrow();
                    csv.append(format(solve.objective())).append(',')
                            .append(format(solve.primalResidual())).append(',');
                } else {
                    csv.append(",,");
                }
                csv.append(outcome.diagnostic().orElse("").replace(',', ';')).append('\n');
            }
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology CASCADE compatibility v1

                Evidence only. F3C removes ordinary longitudinal-grade continuity only across
                explicitly authored CASCADE intervals, while keeping ordinary-side D2 envelopes
                and bounding the solved discontinuity by authored downhill-drop authority.
                BOUNDARY_COUPLED remains deferred. No terrain or Minecraft authority is granted.
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

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
