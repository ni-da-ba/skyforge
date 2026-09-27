package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelColumn;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandRefinedFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldHeadRefinedTerrainPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterProjectionQualificationPlanner;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Fixed F4G full cross-section shallower-only refinement evidence. */
public final class HydrologyRefinedVoxelQuantizationCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-refined-voxel-quantization-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    private HydrologyRefinedVoxelQuantizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        var candidate = SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        var association = productionAssociation(descriptor, 910_077L);
        var voxel = SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        var direct = SkyIslandWorldWaterProjectionQualificationPlanner.plan(voxel);
        var head = SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);
        var refined = SkyIslandWorldHeadRefinedTerrainPlanner.plan(head);
        var requantized = SkyIslandRefinedFluvialVoxelQuantizationPlanner.plan(
                association, candidate, refined.terrainField());
        if (requantized.authorizedColumns().stream().anyMatch(column -> column.removedSolidBlocks() < 0)) {
            throw new IllegalStateException("F4H produced a negative removal count");
        }

        double centerX =
                association.realizedVolume().compiledVolume().descriptor().centerX();
        double centerZ =
                association.realizedVolume().compiledVolume().descriptor().centerZ();

        int shallower = 0;
        int unchanged = 0;
        int deeper = 0;
        double maxRecoveryWorld = 0.0;
        double meanRecoveryWorld = 0.0;
        for (SkyIslandFluvialVoxelColumn column : voxel.authorizedColumns()) {
            SkyIslandLocalPosition local =
                    new SkyIslandLocalPosition(
                            column.worldX() - centerX,
                            column.worldZ() - centerZ);
            var before = candidate.terrainField().sampleDetailed(local);
            var after = refined.terrainField().sampleDetailed(local);
            double recovery =
                    (after.targetTerrainPotential() - before.targetTerrainPotential())
                            * descriptor.reliefBudget();
            meanRecoveryWorld += recovery;
            maxRecoveryWorld = Math.max(maxRecoveryWorld, recovery);
            if (recovery > 1.0e-9) {
                shallower++;
            } else if (recovery < -1.0e-9) {
                deeper++;
            } else {
                unchanged++;
            }
        }
        if (!voxel.authorizedColumns().isEmpty()) {
            meanRecoveryWorld /= voxel.authorizedColumns().size();
        }

        String summary =
                "specimen,islandKey,refinedReaches,totalReaches,postD2Rejected,"
                        + "authorizedColumns,requantizedColumns,requantizedRemovedBlocks,shallowerColumns,unchangedColumns,deeperColumns,"
                        + "maxRecoveryWorld,meanRecoveryWorld\n"
                        + String.format(
                                Locale.ROOT,
                                "ordinary-77,77,%d,%d,%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                                refined.refinedReachCount(),
                                refined.reaches().size(),
                                refined.postRefinementQualifications().stream()
                                        .filter(q -> !q.accepted())
                                        .count(),
                                voxel.authorizedColumns().size(),
                                requantized.authorizedColumns().size(),
                                requantized.totalRemovedSolidBlocks(),
                                shallower,
                                unchanged,
                                deeper,
                                maxRecoveryWorld,
                                meanRecoveryWorld);

        StringBuilder reaches = new StringBuilder(
                "startCell,endCell,refined,maxCenterlineRaiseWorld,maxLoweringPotential,meanLoweringPotential\n");
        for (var reach : refined.reaches()) {
            var semantic = reach.geomorphicRoute().semanticReach();
            long identity = ((long) semantic.startCellIndex() << 32)
                    ^ Integer.toUnsignedLong(semantic.endCellIndex());
            double maxRaise = 0.0;
            var original = candidate.terrainField().acceptedReaches().stream()
                    .filter(value -> {
                        var s = value.geomorphicRoute().semanticReach();
                        return s.startCellIndex() == semantic.startCellIndex()
                                && s.endCellIndex() == semantic.endCellIndex();
                    })
                    .findFirst()
                    .orElseThrow();
            for (int i = 0; i < reach.samples().size(); i++) {
                maxRaise = Math.max(
                        maxRaise,
                        (reach.samples().get(i).bedElevationPotential()
                                        - original.samples().get(i).bedElevationPotential())
                                * descriptor.reliefBudget());
            }
            reaches.append(semantic.startCellIndex()).append(',')
                    .append(semantic.endCellIndex()).append(',')
                    .append(refined.refinedReachIdentities().contains(identity)).append(',')
                    .append(format(maxRaise)).append(',')
                    .append(format(reach.maximumRequiredLowering())).append(',')
                    .append(format(reach.meanRequiredLowering()))
                    .append('\n');
        }

        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("reaches.csv"), reaches, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology refined voxel quantization v1

                F4G extends solved F4F head/bed corrections through the accepted F4A continuous
                cross-section primitive. Evidence is association-specific and continuous only.
                Every sampled refined target must be equal to or higher than F4A: never a deeper cut.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        double centerX = 1200.0;
        double centerZ = -900.0;
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
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
                                "f4g-corpus",
                                0,
                                0,
                                geometrySeed),
                        new WorldBounds(
                                centerX - radius,
                                centerX + radius,
                                64.0,
                                448.0,
                                centerZ - radius,
                                centerZ + radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
