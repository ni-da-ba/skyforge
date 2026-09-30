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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fixed F4H evidence comparing refined and baseline voxel quantization. */
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
        Map<Long, SkyIslandFluvialVoxelColumn> baselineByColumn = new HashMap<>();
        for (SkyIslandFluvialVoxelColumn column : voxel.authorizedColumns()) {
            long key = columnKey(column);
            if (baselineByColumn.put(key, column) != null) {
                throw new IllegalStateException("F4C produced duplicate authorized columns");
            }
        }
        int quantizedShallower = 0;
        int quantizedUnchanged = 0;
        int quantizedDeeper = 0;
        for (SkyIslandFluvialVoxelColumn column : requantized.authorizedColumns()) {
            SkyIslandFluvialVoxelColumn baseline = baselineByColumn.remove(columnKey(column));
            if (baseline == null) {
                throw new IllegalStateException("F4H introduced a column outside F4C authority");
            }
            if (column.targetMaximumSolidY() > baseline.targetMaximumSolidY()) {
                quantizedShallower++;
            } else if (column.targetMaximumSolidY() < baseline.targetMaximumSolidY()) {
                quantizedDeeper++;
            } else {
                quantizedUnchanged++;
            }
        }
        if (!baselineByColumn.isEmpty()) {
            throw new IllegalStateException("F4H dropped F4C-authorized columns");
        }
        if (quantizedDeeper != 0) {
            throw new IllegalStateException("F4H voxel target is deeper than accepted F4C quantization");
        }
        if (requantized.totalRemovedSolidBlocks() > voxel.totalRemovedSolidBlocks()) {
            throw new IllegalStateException("F4H removes more blocks than accepted F4C quantization");
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
                        + "authorizedColumns,requantizedColumns,baselineRemovedBlocks,requantizedRemovedBlocks,"
                        + "quantizedShallowerColumns,quantizedUnchangedColumns,quantizedDeeperColumns,"
                        + "continuousShallowerColumns,continuousUnchangedColumns,continuousDeeperColumns,"
                        + "maxRecoveryWorld,meanRecoveryWorld\n"
                        + String.format(
                                Locale.ROOT,
                                "ordinary-77,77,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                                refined.refinedReachCount(),
                                refined.reaches().size(),
                                refined.postRefinementQualifications().stream()
                                        .filter(q -> !q.accepted())
                                        .count(),
                                voxel.authorizedColumns().size(),
                                requantized.authorizedColumns().size(),
                                voxel.totalRemovedSolidBlocks(),
                                requantized.totalRemovedSolidBlocks(),
                                quantizedShallower,
                                quantizedUnchanged,
                                quantizedDeeper,
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
                The F4H integer plan must preserve the exact F4C authorized-column set and may only\n                retain or raise each target solid surface; aggregate removed-block count cannot increase.\n                This is evidence only and grants no Minecraft mutation authority.\n                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
    }

    private static long columnKey(SkyIslandFluvialVoxelColumn column) {
        return ((long) column.worldX() << 32) ^ Integer.toUnsignedLong(column.worldZ());
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
