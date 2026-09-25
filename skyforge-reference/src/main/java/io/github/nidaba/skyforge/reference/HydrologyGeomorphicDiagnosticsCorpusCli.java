package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicNetworkNodeKind;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicReachDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicReachDiagnosticsPlanner;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicReachRoute;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Generates pre-carving geomorphic/hydraulic diagnostics for reset-threshold calibration.
 *
 * <p>The manifest is evidence only. It intentionally does not classify specimens as accepted/rejected;
 * hard envelopes are a later decision under #1084 after the metric distributions are inspected.
 */
public final class HydrologyGeomorphicDiagnosticsCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-geomorphic-diagnostics-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyGeomorphicDiagnosticsCorpusCli() {}

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

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,morphology,nodes,confluences,reaches,"
                        + "maxRequiredLowering,meanRequiredLowering,maxWaterSlope,"
                        + "meanUphillFraction,maxRidgeFraction,meanValleyAdvantage,"
                        + "maxGuidanceDeviation,maxBankfullHalfWidth,maxWaterDepth,"
                        + "maxLateralRecoveryGrade,maxContainmentDeficitWorld,"
                        + "maxDepthToBankfullWidth,maxReliefToValleyWidth,"
                        + "maxExcavationBurden,maxExcavationVolume,maxCurvatureWidthRatio,"
                        + "maxLongitudinalGradeWorld\n");

        for (Specimen specimen : specimens) {
            SkyIslandGeomorphicChannelNetworkPlan geometry =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(specimen.descriptor());
            SkyIslandHydraulicChannelNetworkPlan hydraulics =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(specimen.descriptor());

            List<SkyIslandGeomorphicReachDiagnostics> geomorphicDiagnostics =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measure(specimen.descriptor());

            double meanUphill = geometry.routes().stream()
                    .mapToDouble(route -> route.route().uphillStepFraction())
                    .average()
                    .orElse(0.0);
            double maxRidge = geometry.routes().stream()
                    .mapToDouble(route -> route.route().ridgeSampleFraction())
                    .max()
                    .orElse(0.0);
            double meanValley = geometry.routes().stream()
                    .mapToDouble(route -> route.route().meanValleyFloorAdvantage())
                    .average()
                    .orElse(0.0);
            double maxDeviation = geometry.routes().stream()
                    .mapToDouble(route -> route.route().maxGuidanceDeviation())
                    .max()
                    .orElse(0.0);
            double maxWidth = hydraulics.reaches().stream()
                    .mapToDouble(SkyIslandHydraulicReachGeometry::maximumBankfullHalfWidth)
                    .max()
                    .orElse(0.0);
            double maxDepth = hydraulics.reaches().stream()
                    .mapToDouble(SkyIslandHydraulicReachGeometry::maximumWaterDepthPotential)
                    .max()
                    .orElse(0.0);

            double maxLateralRecoveryGrade = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumLateralRecoveryGrade)
                    .max()
                    .orElse(0.0);
            double maxContainmentDeficit = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumBankContainmentDeficitWorldUnits)
                    .max()
                    .orElse(0.0);
            double maxDepthToWidth = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumDepthToBankfullWidthRatio)
                    .max()
                    .orElse(0.0);
            double maxReliefToValleyWidth = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumReliefToValleyWidthRatio)
                    .max()
                    .orElse(0.0);
            double maxExcavationBurden = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::normalizedExcavationBurden)
                    .max()
                    .orElse(0.0);
            double maxExcavationVolume = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::excavationVolumeProxyWorldUnitsCubed)
                    .max()
                    .orElse(0.0);
            double maxCurvatureWidthRatio = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumCurvatureWidthRatio)
                    .max()
                    .orElse(0.0);
            double maxLongitudinalGradeWorld = geomorphicDiagnostics.stream()
                    .mapToDouble(SkyIslandGeomorphicReachDiagnostics::maximumLongitudinalGrade)
                    .max()
                    .orElse(0.0);

            csv.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(specimen.descriptor().morphologyFamily().identifier()).append(',')
                    .append(geometry.nodes().size()).append(',')
                    .append(geometry.count(SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE)).append(',')
                    .append(geometry.routes().size()).append(',')
                    .append(format(hydraulics.maximumRequiredLowering())).append(',')
                    .append(format(hydraulics.meanRequiredLowering())).append(',')
                    .append(format(hydraulics.maximumWaterSurfaceSlope())).append(',')
                    .append(format(meanUphill)).append(',')
                    .append(format(maxRidge)).append(',')
                    .append(format(meanValley)).append(',')
                    .append(format(maxDeviation)).append(',')
                    .append(format(maxWidth)).append(',')
                    .append(format(maxDepth)).append(',')
                    .append(format(maxLateralRecoveryGrade)).append(',')
                    .append(format(maxContainmentDeficit)).append(',')
                    .append(format(maxDepthToWidth)).append(',')
                    .append(format(maxReliefToValleyWidth)).append(',')
                    .append(format(maxExcavationBurden)).append(',')
                    .append(format(maxExcavationVolume)).append(',')
                    .append(format(maxCurvatureWidthRatio)).append(',')
                    .append(format(maxLongitudinalGradeWorld)).append('\n');
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), readme(), StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String readme() {
        return """
                Hydrology geomorphic diagnostics v1

                This corpus records pre-carving route and hydraulic metrics for threshold calibration.
                It is not an acceptance oracle. Do not infer pass/fail from one specimen or tune a
                threshold merely to preserve the current corpus.

                Metrics are intended to support the next #1084 qualification tranche, particularly
                hard envelopes for ridge occupancy, required lowering, longitudinal grade, lateral recovery,
                bank containment, width/depth compatibility, excavation burden/volume, and curvature.
                """;
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
