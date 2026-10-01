package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandRouteFunctionalDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandRouteResolutionConvergenceDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandRouteResolutionConvergencePlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class HydrologyRouteResolutionCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-route-resolution-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyRouteResolutionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen(
                        "ordinary-77-a",
                        descriptor(8L, 81L, 77L),
                        709,
                        559),
                new Specimen(
                        "ordinary-77-b",
                        descriptor(8L, 81L, 77L),
                        1742,
                        1842));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,"
                        + "coarseObjective,mediumObjective,fineObjective,"
                        + "coarseLength,mediumLength,fineLength,"
                        + "coarsePositiveVariation,mediumPositiveVariation,finePositiveVariation,"
                        + "coarseMaxUphillGrade,mediumMaxUphillGrade,fineMaxUphillGrade,"
                        + "coarseRidgeFraction,mediumRidgeFraction,fineRidgeFraction,"
                        + "coarseMeanValley,mediumMeanValley,fineMeanValley,"
                        + "coarseMaxGuidanceDeviation,mediumMaxGuidanceDeviation,fineMaxGuidanceDeviation,"
                        + "coarseMediumPolylineDistance,mediumFinePolylineDistance\n");

        for (Specimen specimen : specimens) {
            SkyIslandRouteResolutionConvergenceDiagnostics d =
                    SkyIslandRouteResolutionConvergencePlanner.measureReach(
                            specimen.descriptor(),
                            specimen.startCell(),
                            specimen.endCell());
            csv.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(specimen.startCell()).append(',')
                    .append(specimen.endCell()).append(',');
            appendTriplet(csv, d.coarse().objective(), d.medium().objective(), d.fine().objective());
            appendTriplet(csv, d.coarse().pathLength(), d.medium().pathLength(), d.fine().pathLength());
            appendTriplet(
                    csv,
                    d.coarse().positiveElevationVariation(),
                    d.medium().positiveElevationVariation(),
                    d.fine().positiveElevationVariation());
            appendTriplet(
                    csv,
                    d.coarse().maximumUphillGrade(),
                    d.medium().maximumUphillGrade(),
                    d.fine().maximumUphillGrade());
            appendTriplet(
                    csv,
                    d.coarse().ridgeLengthFraction(),
                    d.medium().ridgeLengthFraction(),
                    d.fine().ridgeLengthFraction());
            appendTriplet(
                    csv,
                    d.coarse().meanValleyFloorAdvantage(),
                    d.medium().meanValleyFloorAdvantage(),
                    d.fine().meanValleyFloorAdvantage());
            appendTriplet(
                    csv,
                    d.coarse().maximumGuidanceDeviation(),
                    d.medium().maximumGuidanceDeviation(),
                    d.fine().maximumGuidanceDeviation());
            csv.append(format(d.coarseToMediumPolylineDistance())).append(',')
                    .append(format(d.mediumToFinePolylineDistance())).append('\n');
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("README.txt"),
                """
                C3 terrain-aware route-resolution evidence.

                The same semantic guidance, corridor, endpoint anchors, planning scale, fields, and
                objective weights are solved on 4/8/16 numerical search lattices. Raw values are
                evidence for later scale-aware convergence thresholds; this corpus is not itself a
                production acceptance oracle and does not retune D2/E2/F2C thresholds.
                """,
                StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static void appendTriplet(
            StringBuilder csv,
            double coarse,
            double medium,
            double fine) {
        csv.append(format(coarse)).append(',')
                .append(format(medium)).append(',')
                .append(format(fine)).append(',');
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private record Specimen(
            String name,
            SkyIslandDescriptor descriptor,
            int startCell,
            int endCell) {}
}
