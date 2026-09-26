package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationEvaluator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationPolicy;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicReachDiagnosticsPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Fixed D2 classification corpus over the current D1 diagnostic authority. */
public final class HydrologyGeomorphicQualificationCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-geomorphic-qualification-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyGeomorphicQualificationCorpusCli() {}

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
                new Specimen("stress-811", descriptor(8L, 81L, 811L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)),
                new Specimen("pure-incised-2084", descriptor(8L, 81L, 2084L)),
                new Specimen("pure-incised-2093", descriptor(8L, 81L, 2093L)));

        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,accepted,violations,ridgeLengthFraction\n");

        for (Specimen specimen : specimens) {
            var diagnostics =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measure(specimen.descriptor());
            for (var diagnostic : diagnostics) {
                var reach =
                        diagnostic.hydraulicReach()
                                .geomorphicRoute()
                                .semanticReach();
                var qualification =
                        SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
                String violations =
                        qualification.violations().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining("|"));
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(reach.startCellIndex()).append(',')
                        .append(reach.endCellIndex()).append(',')
                        .append(qualification.accepted()).append(',')
                        .append(violations).append(',')
                        .append(String.format(
                                java.util.Locale.ROOT,
                                "%.9f",
                                diagnostic.ridgeSampleFraction()))
                        .append('\n');
            }
        }

        Files.writeString(out.resolve("qualification-manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology geomorphic qualification v1

                Fixed D2 classification evidence over the current D1 metric authority.
                ridgeLengthFraction is the C3 arc-length-weighted fixed-physical-probe metric,
                carried through the compatibility-named D1 ridge accessor.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("qualification-manifest.csv").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
