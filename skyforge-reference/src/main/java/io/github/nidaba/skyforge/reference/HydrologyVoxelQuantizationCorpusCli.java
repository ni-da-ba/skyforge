package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelColumn;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F4C ceiling-quantization evidence over the accepted F4B projection envelope. */
public final class HydrologyVoxelQuantizationCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-voxel-quantization-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    private HydrologyVoxelQuantizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder summary = new StringBuilder(
                "specimen,islandKey,qualifiedComponents,rejectedComponents,authorizedColumns,mutatedColumns,totalRemovedBlocks,"
                        + "maxRemovedPerColumn,maxUndercutResidualWorld,minUndercutResidualWorld,"
                        + "extraExcavationBelowQualifiedTarget\n");

        for (Specimen specimen : List.of(
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L), false),
                new Specimen("ordinary-77-weak", descriptor(8L, 81L, 77L), true),
                new Specimen("primary-287", descriptor(8L, 81L, 287L), false),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L), false),
                new Specimen("lake-609", descriptor(8L, 81L, 609L), false))) {
            SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                            specimen.descriptor());
            SkyIslandFluvialVoxelQuantizationPlan plan =
                    SkyIslandFluvialVoxelQuantizationPlanner.plan(
                            specimen.weak()
                                    ? weakAssociation(
                                            specimen.descriptor(),
                                            920_000L + specimen.descriptor().identity().islandKey())
                                    : productionAssociation(
                                            specimen.descriptor(),
                                            910_000L + specimen.descriptor().identity().islandKey()),
                            candidate);

            int maxRemoved = plan.authorizedColumns().stream()
                    .mapToInt(SkyIslandFluvialVoxelColumn::removedSolidBlocks)
                    .max()
                    .orElse(0);
            double minResidual = plan.authorizedColumns().stream()
                    .mapToDouble(SkyIslandFluvialVoxelColumn::undercutResidualWorld)
                    .min()
                    .orElse(0.0);

            long qualifiedComponents = plan.components().stream()
                    .filter(component -> component.status()
                            == io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelComponentStatus.QUALIFIED)
                    .count();

            summary.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(qualifiedComponents).append(',')
                    .append(plan.rejectedComponents().size()).append(',')
                    .append(plan.authorizedColumns().size()).append(',')
                    .append(plan.mutatedColumns().size()).append(',')
                    .append(plan.totalRemovedSolidBlocks()).append(',')
                    .append(maxRemoved).append(',')
                    .append(format(plan.maximumUndercutResidualWorld())).append(',')
                    .append(format(minResidual)).append(',')
                    .append("0.000000000")
                    .append('\n');
        }

        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology voxel quantization v1

                F4C uses removal-only ceiling quantization:
                  quantized upper boundary = ceil(F4B target upper surface)
                  target max solid Y        = quantized upper boundary - 1

                Therefore the backend never excavates below the already-qualified continuous target.
                The residual is nonnegative and strictly less than one block. This evidence grants
                no Minecraft mutation authority.
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
                                "f4c-corpus",
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

    private static SkyIslandAuthoredRealizationAssociation weakAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        96.0,
                        -64.0,
                        220.0,
                        radius,
                        1.0,
                        1.0,
                        Math.min(18.0, radius * 0.10),
                        0.0,
                        0.24,
                        0.62,
                        0.0,
                        descriptor.morphologyFamily(),
                        0.0,
                        28.0,
                        0.0);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "f4c-corpus-weak",
                                0,
                                0,
                                geometrySeed),
                        new WorldBounds(
                                96.0 - radius,
                                96.0 + radius,
                                28.0,
                                412.0,
                                -64.0 - radius,
                                -64.0 + radius),
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

    private record Specimen(String name, SkyIslandDescriptor descriptor, boolean weak) {}
}
