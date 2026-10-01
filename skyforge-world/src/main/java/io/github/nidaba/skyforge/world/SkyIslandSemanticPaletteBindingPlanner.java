package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds AUTH-0038 connected semantic palette-binding coherence domains.
 *
 * <p>Matrix roles remain inside one AUTH-0034 assemblage. Conditioned roles may cross assemblage
 * boundaries only through face-connected AUTH-0037 eligibility carrying the same role/source
 * channel.
 */
public final class SkyIslandSemanticPaletteBindingPlanner {
    private SkyIslandSemanticPaletteBindingPlanner() {}

    public static SkyIslandSemanticPaletteBindingPlan plan(
            SkyIslandDescriptor descriptor) {
        SkyIslandLithologicRealizationField realization =
                SkyIslandLithologicRealizationField.create(descriptor);
        SkyIslandLithologicAssemblagePlan assemblagePlan =
                realization.assemblagePlan();
        SkyIslandSemanticMaterialPaletteField palette =
                SkyIslandSemanticMaterialPaletteField.create(descriptor);

        Map<Long, SkyIslandSemanticPaletteBindingCell> nodes = new HashMap<>();
        Map<Integer, SkyIslandLithologicAssemblageCell> cellsByIndex = new HashMap<>();
        for (SkyIslandLithologicAssemblageCell cell : assemblagePlan.cells()) {
            cellsByIndex.put(cell.index(), cell);
            SkyIslandSemanticMaterialPaletteSelection selection =
                    palette.sample(cell.position());
            if (!selection.materialPresent()) {
                throw new IllegalStateException(
                        "AUTH-0034 active host cell lost AUTH-0037 material presence");
            }
            for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                    selection.candidates()) {
                SkyIslandSemanticPaletteBindingCell bindingCell =
                        new SkyIslandSemanticPaletteBindingCell(
                                cell.index(),
                                cell.xIndex(),
                                cell.depthIndex(),
                                cell.zIndex(),
                                cell.assemblageId(),
                                cell.assemblageKind(),
                                candidate);
                nodes.put(nodeKey(cell.index(), candidate.role()), bindingCell);
            }
        }

        List<SkyIslandSemanticPaletteBindingCell> ordered =
                new ArrayList<>(nodes.values());
        ordered.sort(Comparator
                .comparingInt(SkyIslandSemanticPaletteBindingCell::index)
                .thenComparingInt(cell -> cell.candidate().role().ordinal()));

        Set<Long> visited = new HashSet<>();
        List<SkyIslandSemanticPaletteBindingDomain> domains = new ArrayList<>();
        int grid = assemblagePlan.gridSize();
        int depthSamples = assemblagePlan.depthSamples();

        for (SkyIslandSemanticPaletteBindingCell seed : ordered) {
            long seedKey = nodeKey(seed.index(), seed.candidate().role());
            if (!visited.add(seedKey)) {
                continue;
            }

            boolean matrixRole = isMatrixRole(seed.candidate().role());
            List<SkyIslandSemanticPaletteBindingCell> component = new ArrayList<>();
            ArrayDeque<SkyIslandSemanticPaletteBindingCell> queue = new ArrayDeque<>();
            queue.add(seed);

            while (!queue.isEmpty()) {
                SkyIslandSemanticPaletteBindingCell current = queue.removeFirst();
                component.add(current);

                int[][] offsets = {
                    {-1, 0, 0}, {1, 0, 0},
                    {0, -1, 0}, {0, 1, 0},
                    {0, 0, -1}, {0, 0, 1}
                };
                for (int[] offset : offsets) {
                    int x = current.xIndex() + offset[0];
                    int d = current.depthIndex() + offset[1];
                    int z = current.zIndex() + offset[2];
                    if (x < 0 || d < 0 || z < 0
                            || x >= grid || d >= depthSamples || z >= grid) {
                        continue;
                    }
                    int neighborIndex = index(x, d, z, grid, depthSamples);
                    if (!cellsByIndex.containsKey(neighborIndex)) {
                        continue;
                    }
                    long neighborKey =
                            nodeKey(neighborIndex, seed.candidate().role());
                    SkyIslandSemanticPaletteBindingCell neighbor =
                            nodes.get(neighborKey);
                    if (neighbor == null
                            || neighbor.candidate().sourceChannel()
                                    != seed.candidate().sourceChannel()) {
                        continue;
                    }
                    if (matrixRole
                            && neighbor.assemblageId() != seed.assemblageId()) {
                        continue;
                    }
                    if (visited.add(neighborKey)) {
                        queue.addLast(neighbor);
                    }
                }
            }

            component.sort(Comparator.comparingInt(
                    SkyIslandSemanticPaletteBindingCell::index));
            int anchor = component.get(0).index();
            SkyIslandSemanticPaletteBindingDomainKind kind = matrixRole
                    ? SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION
                    : SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION;
            SkyIslandSemanticPaletteBindingKey key =
                    SkyIslandSemanticPaletteBindingKey.of(
                            descriptor.identity(),
                            seed.candidate().role(),
                            seed.candidate().sourceChannel(),
                            kind,
                            anchor);
            domains.add(new SkyIslandSemanticPaletteBindingDomain(key, component));
        }

        domains.sort(Comparator
                .comparingInt((SkyIslandSemanticPaletteBindingDomain domain) ->
                        domain.key().role().ordinal())
                .thenComparingInt(domain -> domain.key().sourceChannel().ordinal())
                .thenComparingInt(domain -> domain.key().anchorId()));

        return new SkyIslandSemanticPaletteBindingPlan(
                descriptor,
                assemblagePlan.gridSize(),
                assemblagePlan.depthSamples(),
                assemblagePlan.horizontalSpacing(),
                assemblagePlan.depthSpacing(),
                domains);
    }

    private static boolean isMatrixRole(SkyIslandSemanticMaterialPaletteRole role) {
        return role == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                || role == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX;
    }

    private static long nodeKey(
            int cellIndex, SkyIslandSemanticMaterialPaletteRole role) {
        return (((long) cellIndex) << 8) | (role.ordinal() & 0xFFL);
    }

    private static int index(
            int x, int depth, int z, int gridSize, int depthSamples) {
        return (z * depthSamples + depth) * gridSize + x;
    }
}
