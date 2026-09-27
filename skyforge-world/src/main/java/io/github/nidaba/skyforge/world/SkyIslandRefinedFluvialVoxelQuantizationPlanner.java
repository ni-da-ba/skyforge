package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds F4H removal-only ceiling re-quantization evidence from one exact F4G field. */
public final class SkyIslandRefinedFluvialVoxelQuantizationPlanner {
    private SkyIslandRefinedFluvialVoxelQuantizationPlanner() {}

    public static SkyIslandFluvialVoxelQuantizationPlan plan(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan,\n            SkyIslandQualifiedFluvialTerrainField refinedField) {
        Objects.requireNonNull(association, "association");
        Objects.requireNonNull(candidatePlan, "candidatePlan");\n        Objects.requireNonNull(refinedField, "refinedField");
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4C association and candidate descriptor must match");
        }

        List<SkyIslandHydraulicTerminalComponent> realizedComponents =
                candidatePlan.realizedComponents();
        if (realizedComponents.isEmpty()) {
            return new SkyIslandFluvialVoxelQuantizationPlan(
                    association, candidatePlan, List.of());
        }

        SkyIslandComponentFluvialWorldSurfaceProjection projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidatePlan);
        CompiledSkyIslandVolume volume =
                association.realizedVolume().compiledVolume();
        SkyIslandTerrainInterpreter terrain =
                new SkyIslandTerrainInterpreter(volume, SkyIslandTerrainProfile.reference());

        Map<Long, SkyIslandHydraulicTerminalComponent> componentByReach =
                componentByReach(realizedComponents);
        Map<Integer, List<SkyIslandFluvialVoxelColumn>> columnsByTerminal =
                new LinkedHashMap<>();
        Map<Integer, List<String>> blockersByTerminal =
                new LinkedHashMap<>();
        for (SkyIslandHydraulicTerminalComponent component : realizedComponents) {
            int terminal = component.terminalFate().channelTerminalCellIndex();
            columnsByTerminal.put(terminal, new ArrayList<>());
            blockersByTerminal.put(terminal, new ArrayList<>());
        }

        Bounds bounds = bounds(candidatePlan, volume);
        for (int worldX = bounds.minimumX(); worldX <= bounds.maximumX(); worldX++) {
            for (int worldZ = bounds.minimumZ(); worldZ <= bounds.maximumZ(); worldZ++) {
                double localX = worldX - volume.descriptor().centerX();
                double localZ = worldZ - volume.descriptor().centerZ();
                SkyIslandQualifiedFluvialSample semantic =
                        refinedField.sampleDetailed(
                                new SkyIslandLocalPosition(localX, localZ));
                if (semantic.zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED) {
                    continue;
                }

                SkyIslandQualifiedFluvialProvenance provenance =
                        semantic.provenance().orElseThrow(() -> new IllegalStateException(
                                "affected F4A sample lost reach provenance"));
                long reachIdentity = identity(
                        provenance.startCellIndex(), provenance.endCellIndex());
                SkyIslandHydraulicTerminalComponent component =
                        componentByReach.get(reachIdentity);
                if (component == null) {
                    throw new IllegalStateException(
                            "F4C affected sample references no realized terminal component");
                }
                int terminal = component.terminalFate().channelTerminalCellIndex();
                List<String> blockers = blockersByTerminal.get(terminal);
                if (!blockers.isEmpty()) {
                    continue;
                }

                try {
                    SkyIslandProjectedFluvialTerrainSample sample =
                            projection.sampleWorld(worldX, worldZ);
                    SkyIslandIntegerColumnSupport original =
                            SkyIslandIntegerColumnSupport.measure(terrain, worldX, worldZ)
                                    .orElseThrow(() -> new IllegalStateException(
                                            "authorized hydrology column has no compiled solid support"));

                    int targetMaximumSolidY =
                            ceilToInt(sample.targetUpperSurfaceWorldY()) - 1;
                    if (targetMaximumSolidY < original.minimumSolidY()) {
                        throw new IllegalStateException(
                                "qualified hydrology target exhausts exact compiled support");
                    }
                    targetMaximumSolidY =
                            Math.min(targetMaximumSolidY, original.maximumSolidY());
                    if (!terrain.classify(worldX, targetMaximumSolidY, worldZ).isSolid()) {
                        throw new IllegalStateException(
                                "F4C quantized retained surface is not exact compiled solid");
                    }
                    for (int y = targetMaximumSolidY + 1;
                            y <= original.maximumSolidY();
                            y++) {
                        if (!terrain.classify(worldX, y, worldZ).isSolid()) {
                            throw new IllegalStateException(
                                    "F4C surface removal band contains non-owned AIR");
                        }
                    }
                    double quantizedUpper = targetMaximumSolidY + 1.0;
                    double residual =
                            quantizedUpper - sample.targetUpperSurfaceWorldY();
                    int removed =
                            original.maximumSolidY() - targetMaximumSolidY;
                    columnsByTerminal.get(terminal).add(
                            new SkyIslandFluvialVoxelColumn(
                                    worldX,
                                    worldZ,
                                    sample,
                                    original,
                                    targetMaximumSolidY,
                                    quantizedUpper,
                                    residual,
                                    removed));
                } catch (RuntimeException exception) {
                    blockers.add(
                            "column "
                                    + worldX
                                    + ","
                                    + worldZ
                                    + ": "
                                    + exception.getClass().getSimpleName()
                                    + ": "
                                    + exception.getMessage());
                    columnsByTerminal.get(terminal).clear();
                }
            }
        }

        List<SkyIslandFluvialVoxelComponentPlan> outcomes = new ArrayList<>();
        for (SkyIslandHydraulicTerminalComponent component : realizedComponents) {
            int terminal = component.terminalFate().channelTerminalCellIndex();
            List<String> blockers = blockersByTerminal.get(terminal);
            List<SkyIslandFluvialVoxelColumn> columns = columnsByTerminal.get(terminal);
            if (blockers.isEmpty() && columns.isEmpty()) {
                blockers.add("realized component produced no authorized integer columns");
            }
            if (blockers.isEmpty()) {
                columns.sort(Comparator
                        .comparingInt(SkyIslandFluvialVoxelColumn::worldX)
                        .thenComparingInt(SkyIslandFluvialVoxelColumn::worldZ));
                outcomes.add(new SkyIslandFluvialVoxelComponentPlan(
                        component,
                        SkyIslandFluvialVoxelComponentStatus.QUALIFIED,
                        columns,
                        List.of()));
            } else {
                outcomes.add(new SkyIslandFluvialVoxelComponentPlan(
                        component,
                        SkyIslandFluvialVoxelComponentStatus.PHYSICAL_REJECTION,
                        List.of(),
                        blockers));
            }
        }
        outcomes.sort(Comparator.comparingInt(
                SkyIslandFluvialVoxelComponentPlan::terminalCellIndex));
        return new SkyIslandFluvialVoxelQuantizationPlan(
                association, candidatePlan, outcomes);
    }

    private static Map<Long, SkyIslandHydraulicTerminalComponent> componentByReach(
            List<SkyIslandHydraulicTerminalComponent> components) {
        Map<Long, SkyIslandHydraulicTerminalComponent> result = new HashMap<>();
        for (SkyIslandHydraulicTerminalComponent component : components) {
            for (SkyIslandHydraulicReachAssembly reach : component.reaches()) {
                if (result.put(reach.identity(), component) != null) {
                    throw new IllegalStateException(
                            "F4A realized reach belongs to multiple terminal components");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Bounds bounds(
            SkyIslandComponentFluvialTerrainCandidatePlan candidate,
            CompiledSkyIslandVolume volume) {
        double minimumLocalX = Double.POSITIVE_INFINITY;
        double maximumLocalX = Double.NEGATIVE_INFINITY;
        double minimumLocalZ = Double.POSITIVE_INFINITY;
        double maximumLocalZ = Double.NEGATIVE_INFINITY;

        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            double margin = reach.maximumBankfullHalfWidth() * 3.5 + 1.0;
            for (SkyIslandLocalPosition point : reach.centerline().points()) {
                minimumLocalX = Math.min(minimumLocalX, point.x() - margin);
                maximumLocalX = Math.max(maximumLocalX, point.x() + margin);
                minimumLocalZ = Math.min(minimumLocalZ, point.z() - margin);
                maximumLocalZ = Math.max(maximumLocalZ, point.z() + margin);
            }
        }
        return new Bounds(
                floorToInt(volume.descriptor().centerX() + minimumLocalX),
                ceilToInt(volume.descriptor().centerX() + maximumLocalX),
                floorToInt(volume.descriptor().centerZ() + minimumLocalZ),
                ceilToInt(volume.descriptor().centerZ() + maximumLocalZ));
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private static int floorToInt(double value) {
        double v = Math.floor(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("coordinate exceeds integer range");
        }
        return (int) v;
    }

    private static int ceilToInt(double value) {
        double v = Math.ceil(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("coordinate exceeds integer range");
        }
        return (int) v;
    }

    private record Bounds(int minimumX, int maximumX, int minimumZ, int maximumZ) {}
}
