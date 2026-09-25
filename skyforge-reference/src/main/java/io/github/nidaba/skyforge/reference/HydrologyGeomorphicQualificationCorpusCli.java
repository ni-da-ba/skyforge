package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationPlan;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationPolicy;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicQualificationViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Emits D2 hard-safety qualification outcomes for the fixed post-C1 hydrology corpus. */
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
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("legacy-control-649", descriptor(8L, 81L, 649L)),
                new Specimen("stress-811", descriptor(8L, 81L, 811L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.safetyV1();
        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,accepted,reaches,rejectedReaches,violations\n");

        for (Specimen specimen : specimens) {
            SkyIslandGeomorphicQualificationPlan plan =
                    SkyIslandGeomorphicQualificationPlanner.qualify(specimen.descriptor(), policy);
            String violations = java.util.Arrays.stream(SkyIslandGeomorphicQualificationViolation.values())
                    .filter(v -> plan.violationCount(v) > 0)
                    .map(v -> v.name() + ":" + plan.violationCount(v))
                    .collect(Collectors.joining("|"));
            csv.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(plan.accepted()).append(',')
                    .append(plan.reaches().size()).append(',')
                    .append(plan.rejectedReachCount()).append(',')
                    .append(violations)
                    .append('\n');
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("README.txt"),
                """
                Hydrology geomorphic qualification v1

                This is a hard-safety classification of the fixed post-C1 diagnostic corpus.
                Failure is expected and is not a reason to loosen thresholds automatically.
                Rejected candidates must return upstream for refinement/re-solving/reclassification.
                """,
                StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
