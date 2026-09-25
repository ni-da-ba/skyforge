package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandContinuousWaterbodyDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandContinuousWaterbodyDiagnosticsPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed retained-basin evidence corpus for E2 qualification calibration. */
public final class HydrologyWaterbodyDiagnosticsCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-waterbody-diagnostics-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyWaterbodyDiagnosticsCorpusCli() {}

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
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,morphology,basinIndex,kind,area,equivalentDiameter,"
                        + "maxDepthWorld,depthToDiameter,maxShorelineGrade,spillHeadroomWorld,"
                        + "reachesSearchBoundary,shorelineCrossings\n");

        for (Specimen specimen : specimens) {
            List<SkyIslandContinuousWaterbodyDiagnostics> diagnostics =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(specimen.descriptor());
            for (int i = 0; i < diagnostics.size(); i++) {
                SkyIslandContinuousWaterbodyDiagnostics d = diagnostics.get(i);
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(specimen.descriptor().morphologyFamily().identifier()).append(',')
                        .append(i).append(',')
                        .append(d.basin().sourceCandidate().kind().name().toLowerCase(Locale.ROOT)).append(',')
                        .append(format(d.basin().approximateArea())).append(',')
                        .append(format(d.equivalentDiameter())).append(',')
                        .append(format(d.maximumDepthWorldUnits())).append(',')
                        .append(format(d.depthToEquivalentDiameterRatio())).append(',')
                        .append(format(d.maximumShorelineGrade())).append(',')
                        .append(format(d.spillHeadroomWorldUnits())).append(',')
                        .append(d.reachesSearchBoundary()).append(',')
                        .append(d.shorelineCrossingCount()).append('\n');
            }
        }

        Files.writeString(out.resolve("basin-manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology retained-basin diagnostics v1

                Evidence only. This manifest records continuous retained-water candidate geometry
                before littoral grading, bathymetry authoring, or Minecraft realization.
                It is not an acceptance oracle; E2 freezes basin thresholds only after this
                distribution is reviewed.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("basin-manifest.csv").toAbsolutePath());
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
