package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldWaterProjection;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandProjectedFluvialWaterSample;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F4E world-space water projection and longitudinal-head diagnostics. */
public final class HydrologyWorldWaterProjectionCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-world-water-projection-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    private HydrologyWorldWaterProjectionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder summary = new StringBuilder(
                "specimen,islandKey,qualifiedTerrainComponents,authorizedTerrainColumns,"
                        + "wetIntegerColumns,minWaterDepthWorld,maxWaterDepthWorld,"
                        + "minWaterOffsetFromOriginalUpper,maxWaterOffsetFromOriginalUpper\n");
        StringBuilder reaches = new StringBuilder(
                "specimen,islandKey,startCell,endCell,centerlineSamples,wetCenterlineSamples,"
                        + "minimumWaterHeadWorld,maximumWaterHeadWorld,uphillSegments,"
                        + "maximumUpclimbWorld,maximumUpclimbGrade,maximumAbsoluteGrade\n");

        for (Specimen specimen : List.of(
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L)),
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)))) {
            SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(specimen.descriptor());
            SkyIslandAuthoredRealizationAssociation association =
                    productionAssociation(
                            specimen.descriptor(),
                            910_000L + specimen.descriptor().identity().islandKey());
            SkyIslandFluvialVoxelQuantizationPlan terrainVoxelPlan =
                    SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
            SkyIslandComponentFluvialWorldWaterProjection water =
                    new SkyIslandComponentFluvialWorldWaterProjection(
                            association, candidate);

            int wetColumns = 0;
            double minDepth = Double.POSITIVE_INFINITY;
            double maxDepth = 0.0;
            double minOffset = Double.POSITIVE_INFINITY;
            double maxOffset = Double.NEGATIVE_INFINITY;
            for (var column : terrainVoxelPlan.authorizedColumns()) {
                SkyIslandProjectedFluvialWaterSample sample =
                        water.sampleWorld(column.worldX(), column.worldZ());
                if (!sample.wet()) {
                    continue;
                }
                wetColumns++;
                minDepth = Math.min(minDepth, sample.waterDepthWorldUnits());
                maxDepth = Math.max(maxDepth, sample.waterDepthWorldUnits());
                double offset =
                        sample.waterSurfaceWorldY().orElseThrow()
                                - sample.terrainProjection().originalUpperSurfaceWorldY();
                minOffset = Math.min(minOffset, offset);
                maxOffset = Math.max(maxOffset, offset);
            }
            if (wetColumns == 0) {
                minDepth = 0.0;
                maxDepth = 0.0;
                minOffset = 0.0;
                maxOffset = 0.0;
            }

            long qualifiedComponents = terrainVoxelPlan.components().stream()
                    .filter(component -> component.status()
                            == io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelComponentStatus.QUALIFIED)
                    .count();
            summary.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(qualifiedComponents).append(',')
                    .append(terrainVoxelPlan.authorizedColumns().size()).append(',')
                    .append(wetColumns).append(',')
                    .append(format(minDepth)).append(',')
                    .append(format(maxDepth)).append(',')
                    .append(format(minOffset)).append(',')
                    .append(format(maxOffset))
                    .append('\n');

            for (SkyIslandHydraulicReachGeometry reach :
                    candidate.terrainField().acceptedReaches()) {
                int wetCenterline = 0;
                int uphillSegments = 0;
                double minHead = Double.POSITIVE_INFINITY;
                double maxHead = Double.NEGATIVE_INFINITY;
                double maxUpclimb = 0.0;
                double maxUpclimbGrade = 0.0;
                double maxAbsoluteGrade = 0.0;
                Double previousHead = null;
                io.github.nidaba.skyforge.world.SkyIslandLocalPosition previous = null;

                for (var local : reach.centerline().points()) {
                    double worldX =
                            association.realizedVolume().compiledVolume().descriptor().centerX()
                                    + local.x();
                    double worldZ =
                            association.realizedVolume().compiledVolume().descriptor().centerZ()
                                    + local.z();
                    SkyIslandProjectedFluvialWaterSample sample =
                            water.sampleWorld(worldX, worldZ);
                    if (!sample.wet()) {
                        throw new IllegalStateException(
                                "F4A realized centerline unexpectedly lost F4E water authority");
                    }
                    wetCenterline++;
                    double head = sample.waterSurfaceWorldY().orElseThrow();
                    minHead = Math.min(minHead, head);
                    maxHead = Math.max(maxHead, head);
                    if (previousHead != null) {
                        double ds = Math.hypot(
                                local.x() - previous.x(),
                                local.z() - previous.z());
                        if (!(ds > 0.0)) {
                            throw new IllegalStateException(
                                    "F4E centerline must advance by positive distance");
                        }
                        double rise = head - previousHead;
                        if (rise > 1.0e-9) {
                            uphillSegments++;
                            maxUpclimb = Math.max(maxUpclimb, rise);
                            maxUpclimbGrade =
                                    Math.max(maxUpclimbGrade, rise / ds);
                        }
                        maxAbsoluteGrade =
                                Math.max(maxAbsoluteGrade, Math.abs(rise) / ds);
                    }
                    previousHead = head;
                    previous = local;
                }

                var semantic = reach.geomorphicRoute().semanticReach();
                reaches.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(semantic.startCellIndex()).append(',')
                        .append(semantic.endCellIndex()).append(',')
                        .append(reach.centerline().points().size()).append(',')
                        .append(wetCenterline).append(',')
                        .append(format(minHead)).append(',')
                        .append(format(maxHead)).append(',')
                        .append(uphillSegments).append(',')
                        .append(format(maxUpclimb)).append(',')
                        .append(format(maxUpclimbGrade)).append(',')
                        .append(format(maxAbsoluteGrade))
                        .append('\n');
            }
        }

        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("reaches.csv"), reaches, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology world water projection v1

                F4E maps only the accepted F4A hydraulic water-surface offset into the exact F4B
                compiled world frame. It does not solve a new grade and grants no voxel/Minecraft
                authority.

                World-space longitudinal-head diagnostics are intentionally reported. A physical
                association that reintroduces uphill flow must be corrected upstream of water
                voxelization; Minecraft may not reconcile it.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        1200.0,
                        -900.0,
                        256.0,
                        radius,
                        72.0,
                        104.0,
                        Math.min(32.0, radius),
                        0.43,
                        0.62,
                        0.57,
                        0.18,
                        descriptor.morphologyFamily(),
                        0.22,
                        38.0,
                        0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "f4e-corpus",
                                0,
                                0,
                                geometrySeed),
                        new WorldBounds(
                                1200.0 - radius,
                                1200.0 + radius,
                                64.0,
                                448.0,
                                -900.0 - radius,
                                -900.0 + radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
