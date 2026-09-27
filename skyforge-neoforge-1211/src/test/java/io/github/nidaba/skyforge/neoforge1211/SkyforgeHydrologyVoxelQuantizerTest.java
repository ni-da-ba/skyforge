package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialZone;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyforgeHydrologyVoxelQuantizerTest {
    private static final long AUTHORED_WORLD = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77QuantizationIsDeterministicOneSidedAndAuthorityBound() throws Exception {
        Path first = Path.of(
                "build", "evidence", "hydrology-voxel-quantization-test-a");
        Path second = Path.of(
                "build", "evidence", "hydrology-voxel-quantization-test-b");

        Evidence a = writeEvidence(first);
        Evidence b = writeEvidence(second);

        assertEquals(a.summary(), b.summary());
        assertEquals(a.reaches(), b.reaches());
        assertTrue(a.authorizedColumns() > 0);
        assertTrue(a.mutatedColumns() > 0);
        assertTrue(a.totalRemovedVoxels() > 0);
        assertTrue(a.maximumRemovedVoxels() > 0);
        assertTrue(a.minimumQuantizationError() >= -EPSILON);
        assertTrue(a.maximumQuantizationError() < 1.0);
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }

    @Test
    void primary287CannotAcquireVoxelMutationWithoutF4ATerrainAuthority() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        assertTrue(candidate.terrainField().acceptedReaches().isEmpty());

        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_287L);
        SkyforgeHydrologyVoxelQuantizer quantizer =
                new SkyforgeHydrologyVoxelQuantizer(association, candidate);

        var skeleton = io.github.nidaba.skyforge.world.SkyIslandHydraulicGeometrySkeletonPlanner
                .plan(descriptor)
                .reaches()
                .getFirst();
        var physical = association.realizedVolume().compiledVolume().descriptor();

        int supportedProbes = 0;
        for (SkyIslandLocalPosition local : skeleton.centerline().points()) {
            int worldX = (int) Math.round(physical.centerX() + local.x());
            int worldZ = (int) Math.round(physical.centerZ() + local.z());
            var sample = quantizer.sample(worldX, worldZ);
            if (sample.isEmpty()) {
                continue;
            }
            supportedProbes++;
            assertFalse(sample.orElseThrow().hydrologyMutatesColumn());
            assertEquals(0, sample.orElseThrow().removedSolidVoxels());
            assertEquals(
                    SkyIslandQualifiedFluvialZone.UNAFFECTED,
                    sample.orElseThrow().projection().semanticSample().zone());
        }
        assertTrue(supportedProbes > 0);
    }

    @Test
    void candidateFromDifferentAuthoredIslandIsRejected() {
        SkyIslandDescriptor descriptor77 = descriptor(77L);
        SkyIslandDescriptor descriptor287 = descriptor(287L);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor77, 910_077L);
        SkyIslandComponentFluvialTerrainCandidatePlan wrong =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor287);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeHydrologyVoxelQuantizer(association, wrong));
    }

    private static Evidence writeEvidence(Path output) throws Exception {
        Files.createDirectories(output);

        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_077L);
        SkyforgeHydrologyVoxelQuantizer quantizer =
                new SkyforgeHydrologyVoxelQuantizer(association, candidate);
        CompiledSkyIslandVolume volume = association.realizedVolume().compiledVolume();

        IntegerBounds bounds = integerBounds(candidate, volume);
        int scanned = 0;
        int supported = 0;
        int authorized = 0;
        int mutated = 0;
        long removed = 0L;
        int maximumRemoved = 0;
        double minimumError = Double.POSITIVE_INFINITY;
        double maximumError = Double.NEGATIVE_INFINITY;

        Map<Long, ReachEvidence> byReach = new LinkedHashMap<>();

        for (int worldX = bounds.minimumX(); worldX <= bounds.maximumX(); worldX++) {
            for (int worldZ = bounds.minimumZ(); worldZ <= bounds.maximumZ(); worldZ++) {
                scanned++;
                var optional = quantizer.sample(worldX, worldZ);
                if (optional.isEmpty()) {
                    continue;
                }
                supported++;
                var sample = optional.orElseThrow();
                boolean hasAuthority =
                        sample.projection().semanticSample().zone()
                                != SkyIslandQualifiedFluvialZone.UNAFFECTED;
                if (hasAuthority) {
                    authorized++;
                }
                if (!hasAuthority) {
                    assertEquals(0, sample.removedSolidVoxels());
                }

                minimumError = Math.min(minimumError, sample.quantizationErrorWorldUnits());
                maximumError = Math.max(maximumError, sample.quantizationErrorWorldUnits());
                assertTrue(sample.quantizationErrorWorldUnits() >= -EPSILON);
                assertTrue(sample.quantizationErrorWorldUnits() < 1.0);
                assertTrue(
                        sample.discreteTopBoundaryWorldY()
                                + EPSILON
                                >= sample.projection().targetUpperSurfaceWorldY());

                if (sample.hydrologyMutatesColumn()) {
                    assertTrue(hasAuthority);
                    mutated++;
                    removed += sample.removedSolidVoxels();
                    maximumRemoved =
                            Math.max(maximumRemoved, sample.removedSolidVoxels());
                }

                sample.projection()
                        .semanticSample()
                        .provenance()
                        .ifPresent(provenance -> {
                            long key = identity(
                                    provenance.startCellIndex(),
                                    provenance.endCellIndex());
                            ReachEvidence evidence = byReach.computeIfAbsent(
                                    key,
                                    ignored -> new ReachEvidence(
                                            provenance.startCellIndex(),
                                            provenance.endCellIndex()));
                            evidence.observe(sample);
                        });
            }
        }

        if (supported == 0) {
            minimumError = 0.0;
            maximumError = 0.0;
        }

        String summary = String.format(
                Locale.ROOT,
                "specimen,islandKey,scannedColumns,supportedColumns,authorizedColumns,"
                        + "mutatedColumns,totalRemovedVoxels,maxRemovedVoxels,"
                        + "minQuantizationError,maxQuantizationError%n"
                        + "ordinary-77,77,%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                scanned,
                supported,
                authorized,
                mutated,
                removed,
                maximumRemoved,
                minimumError,
                maximumError);

        StringBuilder reaches = new StringBuilder(
                "startCell,endCell,authorizedColumns,mutatedColumns,"
                        + "removedVoxels,maxRemovedVoxels,minQuantizationError,"
                        + "maxQuantizationError\n");
        byReach.values().stream()
                .sorted(Comparator.comparingInt(ReachEvidence::startCell)
                        .thenComparingInt(ReachEvidence::endCell))
                .forEach(evidence -> reaches.append(evidence.csv()));

        Files.writeString(output.resolve("summary.csv"), summary, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("reaches.csv"), reaches, StandardCharsets.UTF_8);
        Files.writeString(
                output.resolve("README.txt"),
                """
                Hydrology F4C voxel quantization evidence

                The quantizer is read-only. It truncates original exact compiled support only at
                ceil(F4B.targetUpperWorld)-1. It performs no carrier solve, bank fill, water
                placement, connectivity repair, or extra excavation. Retained top-face error is
                one-sided and strictly less than one block.
                """,
                StandardCharsets.UTF_8);

        return new Evidence(
                summary,
                reaches.toString(),
                authorized,
                mutated,
                removed,
                maximumRemoved,
                minimumError,
                maximumError);
    }

    private static IntegerBounds integerBounds(
            SkyIslandComponentFluvialTerrainCandidatePlan candidate,
            CompiledSkyIslandVolume volume) {
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;

        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            double margin = reach.maximumBankfullHalfWidth() * 3.5 + 1.0;
            for (SkyIslandLocalPosition point : reach.centerline().points()) {
                minimumX = Math.min(minimumX, point.x() - margin);
                maximumX = Math.max(maximumX, point.x() + margin);
                minimumZ = Math.min(minimumZ, point.z() - margin);
                maximumZ = Math.max(maximumZ, point.z() + margin);
            }
        }

        var physical = volume.descriptor();
        return new IntegerBounds(
                (int) Math.floor(physical.centerX() + minimumX),
                (int) Math.ceil(physical.centerX() + maximumX),
                (int) Math.floor(physical.centerZ() + minimumZ),
                (int) Math.ceil(physical.centerZ() + maximumZ));
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
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "f4c-quantization",
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
        return SkyIslandAuthoredRealizationAssociation.of(
                descriptor,
                new SkyIslandWorldVolume(id, bounds, compiled));
    }

    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey));
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private record IntegerBounds(
            int minimumX, int maximumX, int minimumZ, int maximumZ) {}

    private record Evidence(
            String summary,
            String reaches,
            int authorizedColumns,
            int mutatedColumns,
            long totalRemovedVoxels,
            int maximumRemovedVoxels,
            double minimumQuantizationError,
            double maximumQuantizationError) {}

    private static final class ReachEvidence {
        private final int startCell;
        private final int endCell;
        private int authorizedColumns;
        private int mutatedColumns;
        private long removedVoxels;
        private int maximumRemoved;
        private double minimumError = Double.POSITIVE_INFINITY;
        private double maximumError = Double.NEGATIVE_INFINITY;

        private ReachEvidence(int startCell, int endCell) {
            this.startCell = startCell;
            this.endCell = endCell;
        }

        private int startCell() {
            return startCell;
        }

        private int endCell() {
            return endCell;
        }

        private void observe(SkyforgeHydrologyVoxelQuantizationSample sample) {
            authorizedColumns++;
            if (sample.hydrologyMutatesColumn()) {
                mutatedColumns++;
                removedVoxels += sample.removedSolidVoxels();
                maximumRemoved = Math.max(maximumRemoved, sample.removedSolidVoxels());
            }
            minimumError = Math.min(minimumError, sample.quantizationErrorWorldUnits());
            maximumError = Math.max(maximumError, sample.quantizationErrorWorldUnits());
        }

        private String csv() {
            double min = authorizedColumns == 0 ? 0.0 : minimumError;
            double max = authorizedColumns == 0 ? 0.0 : maximumError;
            return String.format(
                    Locale.ROOT,
                    "%d,%d,%d,%d,%d,%d,%.9f,%.9f%n",
                    startCell,
                    endCell,
                    authorizedColumns,
                    mutatedColumns,
                    removedVoxels,
                    maximumRemoved,
                    min,
                    max);
        }
    }
}
