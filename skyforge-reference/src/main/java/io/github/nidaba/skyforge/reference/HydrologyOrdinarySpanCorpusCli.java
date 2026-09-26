package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandOrdinarySpanOutcome;
import io.github.nidaba.skyforge.world.SkyIslandOrdinarySpanPlan;
import io.github.nidaba.skyforge.world.SkyIslandOrdinarySpanPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Fixed F3D ordinary-span partition/solve evidence corpus. */
public final class HydrologyOrdinarySpanCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-ordinary-span-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyOrdinarySpanCorpusCli() {}

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
                new Specimen("lake-609", descriptor(8L, 81L, 609L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,parentStart,parentEnd,startFraction,endFraction,"
                        + "classification,status,upstreamBoundary,downstreamBoundary,samples,"
                        + "violations,maxLowering,maxLateralGrade,maxContainment,maxDepthWidth,"
                        + "maxReliefValley,burden,maxCurvatureWidth,ridgeLengthFraction,"
                        + "maxLongitudinalGrade,objective,primalResidual,diagnostic\n");

        for (Specimen specimen : specimens) {
            SkyIslandOrdinarySpanPlan plan =
                    SkyIslandOrdinarySpanPlanner.plan(specimen.descriptor());
            for (SkyIslandOrdinarySpanOutcome outcome : plan.outcomes()) {
                var span = outcome.span();
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(span.parentReachStartCellIndex()).append(',')
                        .append(span.parentReachEndCellIndex()).append(',')
                        .append(format(span.parentStartStationFraction())).append(',')
                        .append(format(span.parentEndStationFraction())).append(',')
                        .append(span.qualificationClass().name()).append(',')
                        .append(outcome.status().name()).append(',')
                        .append(span.upstreamBoundary().status().name()).append(',')
                        .append(span.downstreamBoundary().status().name()).append(',')
                        .append(span.samples().size()).append(',')
                        .append(outcome.violations().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining("|")))
                        .append(',');

                if (outcome.measurements().isPresent()) {
                    var m = outcome.measurements().orElseThrow();
                    csv.append(format(m.maximumCenterlineLoweringPotential())).append(',')
                            .append(format(m.maximumLateralRecoveryGrade())).append(',')
                            .append(format(m.maximumBankContainmentDeficitWorldUnits())).append(',')
                            .append(format(m.maximumDepthToBankfullWidthRatio())).append(',')
                            .append(format(m.maximumReliefToValleyWidthRatio())).append(',')
                            .append(format(m.normalizedExcavationBurden())).append(',')
                            .append(format(m.maximumCurvatureWidthRatio())).append(',')
                            .append(format(m.ridgeLengthFraction())).append(',')
                            .append(format(m.maximumLongitudinalGrade())).append(',');
                } else {
                    csv.append(",,,,,,,,,");
                }

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
                Hydrology ordinary-span v1

                F3D removes explicit confluence/CASCADE transition intervals from parent F2B reaches,
                applies only solved transition boundary heads, solves the remaining ordinary spans,
                and re-applies the shared D1/D2 measurement and violation logic independently.

                BOUNDARY_DEFERRED is evidence of unresolved transition ownership, not acceptance.
                No F3D result grants terrain or Minecraft authority.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
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
