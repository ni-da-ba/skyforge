package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldSurfaceProjection;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandProjectedFluvialTerrainSample;
import io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialZone;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Fixed F4B world-space projection evidence over the F4A continuous terrain candidate. */
public final class HydrologyWorldSurfaceProjectionCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-world-surface-projection-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    private HydrologyWorldSurfaceProjectionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("ordinary-77", descriptor(8L, 81L, 77L)),
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)));

        StringBuilder summary = new StringBuilder(
                "specimen,islandKey,realizedReaches,projectedSamples,affectedSamples,"
                        + "minDeltaWorld,maxDeltaWorld,minTargetThicknessWorld,maxTargetThicknessWorld,"
                        + "authorizedIntegerColumns,affectedIntegerColumns,minIntegerDeltaWorld,"
                        + "maxIntegerDeltaWorld,minIntegerTargetThicknessWorld\n");
        StringBuilder reaches = new StringBuilder(
                "specimen,islandKey,startCell,endCell,samples,affectedSamples,unsupportedSamples,"
                        + "minDeltaWorld,maxDeltaWorld,minTargetThicknessWorld\n");

        for (Specimen specimen : specimens) {
            try {
                appendSpecimen(specimen, summary, reaches);
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "F4B projection corpus failed for specimen "
                                + specimen.name()
                                + ": "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage(),
                        exception);
            }
        }

        Files.writeString(out.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("reaches.csv"), reaches, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Hydrology world-surface projection v1

                F4B applies only the F4A semantic terrain delta to the compiled upper surface:
                deltaWorld = deltaPotential * descriptor.reliefBudget.

                Absolute compiled placement/morphology and the compiled underside remain unchanged.
                No voxel rounding or Minecraft write authority is present in this evidence.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("summary.csv").toAbsolutePath());
    }

    private static void appendSpecimen(
            Specimen specimen,
            StringBuilder summary,
            StringBuilder reaches) {
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(specimen.descriptor());
            SkyIslandAuthoredRealizationAssociation association =
                    productionAssociation(
                            specimen.descriptor(),
                            910_000L + specimen.descriptor().identity().islandKey());
            CompiledSkyIslandVolume volume =
                    association.realizedVolume().compiledVolume();
            SkyIslandComponentFluvialWorldSurfaceProjection projection =
                    new SkyIslandComponentFluvialWorldSurfaceProjection(
                            association, candidate);

            int projectedSamples = 0;
            int affectedSamples = 0;
            int unsupportedSamples = 0;
            double minDelta = Double.POSITIVE_INFINITY;
            double maxDelta = Double.NEGATIVE_INFINITY;
            double minThickness = Double.POSITIVE_INFINITY;
            double maxThickness = 0.0;

            for (SkyIslandHydraulicReachGeometry reach :
                    candidate.terrainField().acceptedReaches()) {
                int reachSamples = 0;
                int reachAffected = 0;
                int reachUnsupported = 0;
                double reachMinDelta = Double.POSITIVE_INFINITY;
                double reachMaxDelta = Double.NEGATIVE_INFINITY;
                double reachMinThickness = Double.POSITIVE_INFINITY;

                for (var local : reach.centerline().points()) {
                    double worldX = volume.descriptor().centerX() + local.x();
                    double worldZ = volume.descriptor().centerZ() + local.z();
                    SkyIslandProjectedFluvialTerrainSample sample =
                            projection.sampleWorld(worldX, worldZ);

                    projectedSamples++;
                    reachSamples++;
                    minDelta = Math.min(minDelta, sample.terrainDeltaWorldUnits());
                    maxDelta = Math.max(maxDelta, sample.terrainDeltaWorldUnits());
                    reachMinDelta = Math.min(reachMinDelta, sample.terrainDeltaWorldUnits());
                    reachMaxDelta = Math.max(reachMaxDelta, sample.terrainDeltaWorldUnits());
                    minThickness = Math.min(minThickness, sample.targetColumnThicknessWorldUnits());
                    maxThickness = Math.max(maxThickness, sample.targetColumnThicknessWorldUnits());
                    reachMinThickness =
                            Math.min(reachMinThickness, sample.targetColumnThicknessWorldUnits());
                    if (sample.terrainDeltaWorldUnits() < -1.0e-12) {
                        affectedSamples++;
                        reachAffected++;
                    }
                    if (!sample.hasPositiveTargetColumnThickness()) {
                        unsupportedSamples++;
                        reachUnsupported++;
                    }
                }

                var semantic = reach.geomorphicRoute().semanticReach();
                reaches.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(semantic.startCellIndex()).append(',')
                        .append(semantic.endCellIndex()).append(',')
                        .append(reachSamples).append(',')
                        .append(reachAffected).append(',')
                        .append(reachUnsupported).append(',')
                        .append(format(reachMinDelta)).append(',')
                        .append(format(reachMaxDelta)).append(',')
                        .append(format(reachMinThickness))
                        .append('\n');
            }

            IntegerEnvelopeEvidence integerEvidence =
                    scanAuthorizedIntegerColumns(candidate, volume, projection);

            if (projectedSamples == 0) {
                minDelta = 0.0;
                maxDelta = 0.0;
                minThickness = 0.0;
                maxThickness = 0.0;
            }
            summary.append(specimen.name()).append(',')
                    .append(specimen.descriptor().identity().islandKey()).append(',')
                    .append(candidate.terrainField().acceptedReaches().size()).append(',')
                    .append(projectedSamples).append(',')
                    .append(affectedSamples).append(',')
                    .append(unsupportedSamples).append(',')
                    .append(format(minDelta)).append(',')
                    .append(format(maxDelta)).append(',')
                    .append(format(minThickness)).append(',')
                    .append(format(maxThickness)).append(',')
                    .append(integerEvidence.authorizedColumns()).append(',')
                    .append(integerEvidence.affectedColumns()).append(',')
                    .append(format(integerEvidence.minimumDeltaWorld())).append(',')
                    .append(format(integerEvidence.maximumDeltaWorld())).append(',')
                    .append(format(integerEvidence.minimumTargetThicknessWorld()))
                    .append('\n');
        }

    private static IntegerEnvelopeEvidence scanAuthorizedIntegerColumns(
            SkyIslandComponentFluvialTerrainCandidatePlan candidate,
            CompiledSkyIslandVolume volume,
            SkyIslandComponentFluvialWorldSurfaceProjection projection) {
        if (candidate.terrainField().acceptedReaches().isEmpty()) {
            return new IntegerEnvelopeEvidence(0, 0, 0.0, 0.0, 0.0);
        }

        double minimumLocalX = Double.POSITIVE_INFINITY;
        double maximumLocalX = Double.NEGATIVE_INFINITY;
        double minimumLocalZ = Double.POSITIVE_INFINITY;
        double maximumLocalZ = Double.NEGATIVE_INFINITY;
        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            double margin = reach.maximumBankfullHalfWidth() * 3.5 + 1.0;
            for (var point : reach.centerline().points()) {
                minimumLocalX = Math.min(minimumLocalX, point.x() - margin);
                maximumLocalX = Math.max(maximumLocalX, point.x() + margin);
                minimumLocalZ = Math.min(minimumLocalZ, point.z() - margin);
                maximumLocalZ = Math.max(maximumLocalZ, point.z() + margin);
            }
        }

        int minimumWorldX =
                (int) Math.floor(volume.descriptor().centerX() + minimumLocalX);
        int maximumWorldX =
                (int) Math.ceil(volume.descriptor().centerX() + maximumLocalX);
        int minimumWorldZ =
                (int) Math.floor(volume.descriptor().centerZ() + minimumLocalZ);
        int maximumWorldZ =
                (int) Math.ceil(volume.descriptor().centerZ() + maximumLocalZ);

        int authorized = 0;
        int affected = 0;
        double minimumDelta = Double.POSITIVE_INFINITY;
        double maximumDelta = Double.NEGATIVE_INFINITY;
        double minimumThickness = Double.POSITIVE_INFINITY;

        for (int worldX = minimumWorldX; worldX <= maximumWorldX; worldX++) {
            for (int worldZ = minimumWorldZ; worldZ <= maximumWorldZ; worldZ++) {
                double localX = worldX - volume.descriptor().centerX();
                double localZ = worldZ - volume.descriptor().centerZ();
                var semantic = candidate.terrainField().sampleDetailed(
                        new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                                localX, localZ));
                if (semantic.zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED) {
                    continue;
                }

                SkyIslandProjectedFluvialTerrainSample sample =
                        projection.sampleWorld(worldX, worldZ);
                authorized++;
                minimumDelta = Math.min(minimumDelta, sample.terrainDeltaWorldUnits());
                maximumDelta = Math.max(maximumDelta, sample.terrainDeltaWorldUnits());
                minimumThickness =
                        Math.min(
                                minimumThickness,
                                sample.targetColumnThicknessWorldUnits());
                if (sample.terrainDeltaWorldUnits() < -1.0e-12) {
                    affected++;
                }
            }
        }

        if (authorized == 0) {
            return new IntegerEnvelopeEvidence(0, 0, 0.0, 0.0, 0.0);
        }
        return new IntegerEnvelopeEvidence(
                authorized, affected, minimumDelta, maximumDelta, minimumThickness);
    }

    private record IntegerEnvelopeEvidence(
            int authorizedColumns,
            int affectedColumns,
            double minimumDeltaWorld,
            double maximumDeltaWorld,
            double minimumTargetThicknessWorld) {}

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
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "f4b-corpus",
                        0,
                        0,
                        geometrySeed);
        WorldBounds bounds =
                new WorldBounds(
                        centerX - radius,
                        centerX + radius,
                        64.0,
                        448.0,
                        centerZ - radius,
                        centerZ + radius);
        SkyIslandWorldVolume realized =
                new SkyIslandWorldVolume(id, bounds, compiled);
        return SkyIslandAuthoredRealizationAssociation.of(
                descriptor, realized);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
