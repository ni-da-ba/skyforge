package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementComponent;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlan;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementReach;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementSample;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterProjectionQualificationPlanner;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F4F centerline joint head/bed refinement evidence. */
public final class HydrologyWorldHeadRefinementCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-world-head-refinement-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    private HydrologyWorldHeadRefinementCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder summary = new StringBuilder(
                "specimen,islandKey,directRefinementRequired,solvedComponents,blockedComponents\n");
        StringBuilder components = new StringBuilder(
                "specimen,islandKey,terminalCell,status,startCell,endCell,samples,"
                        + "maxTerrainRaiseWorld,meanTerrainRaiseWorld,refinedUphillSegments,"
                        + "maxRefinedUpclimbWorld,maxRefinedAbsoluteGrade,qpPrimalResidual,diagnostic\n");

        for (Specimen specimen : List.of(
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L)),
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)))) {
            var candidate = SkyIslandComponentFluvialTerrainCandidatePlanner.plan(specimen.descriptor());
            var association = productionAssociation(
                    specimen.descriptor(),
                    910_000L + specimen.descriptor().identity().islandKey());
            var terrain = SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
            var direct = SkyIslandWorldWaterProjectionQualificationPlanner.plan(terrain);
            SkyIslandWorldWaterHeadRefinementPlan refinement =
                    SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);

            long solved = refinement.solvedComponents().size();
            summary.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(direct.refinementRequiredComponents().size()).append(',')
                    .append(solved).append(',')
                    .append(refinement.components().size() - solved)
                    .append('\n');

            for (SkyIslandWorldWaterHeadRefinementComponent component :
                    refinement.components()) {
                if (component.reaches().isEmpty()) {
                    components.append(specimen.name()).append(',')
                            .append(specimen.descriptor().identity().islandKey()).append(',')
                            .append(component.terminalCellIndex()).append(',')
                            .append(component.status()).append(',')
                            .append("-1,-1,0,0.000000000,0.000000000,0,0.000000000,0.000000000,0.000000000,")
                            .append(quote(String.join("; ", component.blockers())))
                            .append('\n');
                    continue;
                }

                SkyIslandWorldWaterHeadRefinementReach reach =
                        component.reaches().getFirst();
                var semantic = reach.reach().geomorphicRoute().semanticReach();
                int samples = reach.samples().size();
                double maxRaise = reach.maximumTerrainRaiseWorld();
                double meanRaise = reach.samples().stream()
                        .mapToDouble(SkyIslandWorldWaterHeadRefinementSample::terrainRaiseWorld)
                        .average()
                        .orElse(0.0);
                int uphill = 0;
                double maxUpclimb = 0.0;
                double maxGrade = 0.0;
                for (int i = 0; i + 1 < reach.samples().size(); i++) {
                    var a = reach.samples().get(i);
                    var b = reach.samples().get(i + 1);
                    double ds = Math.hypot(
                            b.localPosition().x() - a.localPosition().x(),
                            b.localPosition().z() - a.localPosition().z());
                    double rise = b.refinedWaterHeadWorld() - a.refinedWaterHeadWorld();
                    if (rise > 1.0e-9) {
                        uphill++;
                        maxUpclimb = Math.max(maxUpclimb, rise);
                    }
                    maxGrade = Math.max(maxGrade, Math.abs(rise) / ds);
                }
                double residual = reach.solve()
                        .map(value -> value.primalResidual())
                        .orElse(0.0);
                String diagnostic = reach.diagnostic().orElse(
                        component.blockers().isEmpty()
                                ? ""
                                : String.join("; ", component.blockers()));

                components.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(component.terminalCellIndex()).append(',')
                        .append(component.status()).append(',')
                        .append(semantic.startCellIndex()).append(',')
                        .append(semantic.endCellIndex()).append(',')
                        .append(samples).append(',')
                        .append(format(maxRaise)).append(',')
                        .append(format(meanRaise)).append(',')
                        .append(uphill).append(',')
                        .append(format(maxUpclimb)).append(',')
                        .append(format(maxGrade)).append(',')
                        .append(format(residual)).append(',')
                        .append(quote(diagnostic))
                        .append('\n');
            }
        }

        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("components.csv"), components, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology world-head refinement v1

                F4F is centerline evidence only. It solves an upward-only, depth-preserving joint
                water-head / bed correction for F4E components that fail direct world-head
                qualification. It grants no terrain field, voxel, water-block, or Minecraft authority.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
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
                                "f4f-corpus",
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

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String quote(String value) {
        return '"' + value.replace(""", """") + '"';
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
