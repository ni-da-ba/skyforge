package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts overlapping AUTH-0033 material-family affinities into connected authored lithologic
 * assemblages and explicit semantic contacts.
 *
 * <p>The assemblage label is a planning interpretation, not a backend material. Every cell retains
 * its complete AUTH-0033 family state so downstream realization can reason about overlap and
 * transition character instead of treating the unit label as a block palette.
 */
public final class SkyIslandLithologicAssemblagePlanner {
    public static final int MIN_SPECIALIZED_ASSEMBLAGE_CELLS = 5;

    private static final double FABRIC_THRESHOLD = 0.48;
    private static final double ALTERED_THRESHOLD = 0.50;
    private static final double WATER_THRESHOLD = 0.50;
    private static final double MINERAL_THRESHOLD = 0.48;

    private SkyIslandLithologicAssemblagePlanner() {}

    public static SkyIslandLithologicAssemblagePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandMaterialFamilyPlan familyPlan = SkyIslandMaterialFamilyPlanner.plan(descriptor);
        int gridSize = familyPlan.gridSize();
        int depthSamples = familyPlan.depthSamples();
        int total = gridSize * depthSamples * gridSize;

        SkyIslandMaterialFamilyCell[] familyByIndex = new SkyIslandMaterialFamilyCell[total];
        SkyIslandLithologicAssemblageKind[] labels =
                new SkyIslandLithologicAssemblageKind[total];

        for (SkyIslandMaterialFamilyCell cell : familyPlan.cells()) {
            familyByIndex[cell.index()] = cell;
            labels[cell.index()] = classify(cell);
        }

        removeSmallSpecializedComponents(labels, familyByIndex, gridSize, depthSamples);

        List<Component> components =
                components(labels, familyByIndex, gridSize, depthSamples);
        int[] assemblageByIndex = new int[total];
        java.util.Arrays.fill(assemblageByIndex, -1);
        List<SkyIslandLithologicAssemblage> assemblages =
                new ArrayList<>(components.size());

        int nextAssemblageId = 0;
        for (Component component : components) {
            component.indices().sort(Integer::compareTo);
            List<SkyIslandLithologicAssemblageCell> cells =
                    new ArrayList<>(component.indices().size());
            for (int index : component.indices()) {
                SkyIslandMaterialFamilyCell family = familyByIndex[index];
                assemblageByIndex[index] = nextAssemblageId;
                cells.add(new SkyIslandLithologicAssemblageCell(
                        nextAssemblageId,
                        component.kind(),
                        family));
            }
            assemblages.add(new SkyIslandLithologicAssemblage(
                    nextAssemblageId,
                    component.kind(),
                    cells));
            nextAssemblageId++;
        }

        List<SkyIslandLithologicContact> contacts = buildContacts(
                assemblages,
                familyByIndex,
                assemblageByIndex,
                gridSize,
                depthSamples);

        return new SkyIslandLithologicAssemblagePlan(
                descriptor,
                gridSize,
                depthSamples,
                familyPlan.horizontalSpacing(),
                familyPlan.depthSpacing(),
                familyPlan.activeHostCells(),
                assemblages,
                contacts);
    }

    /**
     * Semantic support used for planning and evidence. The function only reads AUTH-0033 state.
     */
    public static double semanticSupport(
            SkyIslandMaterialFamilyCell cell,
            SkyIslandLithologicAssemblageKind kind) {
        return switch (kind) {
            case MASSIVE_HOST_UNIT -> cell.coherentMassiveHost();
            case FABRIC_RICH_HOST_UNIT -> cell.layeredFabricRichHost();
            case ALTERED_HOST_UNIT -> cell.stronglyAlteredHost();
            case WATER_CONDITIONED_HOST_UNIT -> cell.waterConditionedHost();
            case MINERAL_BEARING_STRUCTURAL_UNIT -> cell.mineralBearingStructuralHost();
        };
    }

    private static SkyIslandLithologicAssemblageKind classify(
            SkyIslandMaterialFamilyCell cell) {
        SkyIslandLithologicAssemblageKind host =
                cell.layeredFabricRichHost() >= FABRIC_THRESHOLD
                                && cell.layeredFabricRichHost()
                                        >= 0.62 * cell.coherentMassiveHost()
                        ? SkyIslandLithologicAssemblageKind.FABRIC_RICH_HOST_UNIT
                        : SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT;

        SkyIslandLithologicAssemblageKind best = host;
        double bestScore = semanticSupport(cell, host) * 0.82;

        if (cell.stronglyAlteredHost() >= ALTERED_THRESHOLD) {
            double score = cell.stronglyAlteredHost() * 1.02;
            if (score > bestScore) {
                best = SkyIslandLithologicAssemblageKind.ALTERED_HOST_UNIT;
                bestScore = score;
            }
        }
        if (cell.waterConditionedHost() >= WATER_THRESHOLD) {
            double score = cell.waterConditionedHost();
            if (score > bestScore) {
                best = SkyIslandLithologicAssemblageKind.WATER_CONDITIONED_HOST_UNIT;
                bestScore = score;
            }
        }
        if (cell.mineralBearingStructuralHost() >= MINERAL_THRESHOLD) {
            double score = cell.mineralBearingStructuralHost() * 1.08;
            if (score > bestScore) {
                best = SkyIslandLithologicAssemblageKind.MINERAL_BEARING_STRUCTURAL_UNIT;
            }
        }
        return best;
    }

    /**
     * Removes threshold flecks without inventing conditioned material.
     *
     * <p>Small non-massive components revert to the ordinary massive-host interpretation. The
     * operation can therefore remove a specialized interpretation but never promote unsupported
     * cells into one.
     */
    private static void removeSmallSpecializedComponents(
            SkyIslandLithologicAssemblageKind[] labels,
            SkyIslandMaterialFamilyCell[] familyByIndex,
            int gridSize,
            int depthSamples) {
        for (int pass = 0; pass < 2; pass++) {
            boolean changed = false;
            for (Component component :
                    components(labels, familyByIndex, gridSize, depthSamples)) {
                if (component.kind()
                                != SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT
                        && component.indices().size() < MIN_SPECIALIZED_ASSEMBLAGE_CELLS) {
                    for (int index : component.indices()) {
                        labels[index] =
                                SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT;
                    }
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private static List<Component> components(
            SkyIslandLithologicAssemblageKind[] labels,
            SkyIslandMaterialFamilyCell[] familyByIndex,
            int gridSize,
            int depthSamples) {
        boolean[] visited = new boolean[labels.length];
        List<Component> result = new ArrayList<>();

        for (int seed = 0; seed < labels.length; seed++) {
            if (visited[seed] || familyByIndex[seed] == null) {
                continue;
            }
            SkyIslandLithologicAssemblageKind kind = labels[seed];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            List<Integer> indices = new ArrayList<>();
            queue.add(seed);
            visited[seed] = true;

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                indices.add(current);
                int ix = xIndex(current, gridSize);
                int id = depthIndex(current, gridSize, depthSamples);
                int iz = zIndex(current, gridSize, depthSamples);

                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix - 1,
                        id,
                        iz,
                        gridSize,
                        depthSamples);
                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix + 1,
                        id,
                        iz,
                        gridSize,
                        depthSamples);
                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix,
                        id - 1,
                        iz,
                        gridSize,
                        depthSamples);
                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix,
                        id + 1,
                        iz,
                        gridSize,
                        depthSamples);
                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix,
                        id,
                        iz - 1,
                        gridSize,
                        depthSamples);
                addNeighbor(
                        queue,
                        visited,
                        labels,
                        familyByIndex,
                        kind,
                        ix,
                        id,
                        iz + 1,
                        gridSize,
                        depthSamples);
            }
            result.add(new Component(kind, indices));
        }
        return result;
    }

    private static void addNeighbor(
            ArrayDeque<Integer> queue,
            boolean[] visited,
            SkyIslandLithologicAssemblageKind[] labels,
            SkyIslandMaterialFamilyCell[] familyByIndex,
            SkyIslandLithologicAssemblageKind kind,
            int ix,
            int id,
            int iz,
            int gridSize,
            int depthSamples) {
        if (ix < 0
                || id < 0
                || iz < 0
                || ix >= gridSize
                || id >= depthSamples
                || iz >= gridSize) {
            return;
        }
        int neighbor = index(ix, id, iz, gridSize, depthSamples);
        if (visited[neighbor]
                || familyByIndex[neighbor] == null
                || labels[neighbor] != kind) {
            return;
        }
        visited[neighbor] = true;
        queue.addLast(neighbor);
    }

    private static List<SkyIslandLithologicContact> buildContacts(
            List<SkyIslandLithologicAssemblage> assemblages,
            SkyIslandMaterialFamilyCell[] familyByIndex,
            int[] assemblageByIndex,
            int gridSize,
            int depthSamples) {
        Map<Long, ContactAccumulator> accumulators = new HashMap<>();

        for (int index = 0; index < familyByIndex.length; index++) {
            if (familyByIndex[index] == null) {
                continue;
            }
            int ix = xIndex(index, gridSize);
            int id = depthIndex(index, gridSize, depthSamples);
            int iz = zIndex(index, gridSize, depthSamples);

            addContactFace(
                    accumulators,
                    familyByIndex,
                    assemblageByIndex,
                    index,
                    ix + 1,
                    id,
                    iz,
                    gridSize,
                    depthSamples);
            addContactFace(
                    accumulators,
                    familyByIndex,
                    assemblageByIndex,
                    index,
                    ix,
                    id + 1,
                    iz,
                    gridSize,
                    depthSamples);
            addContactFace(
                    accumulators,
                    familyByIndex,
                    assemblageByIndex,
                    index,
                    ix,
                    id,
                    iz + 1,
                    gridSize,
                    depthSamples);
        }

        List<Map.Entry<Long, ContactAccumulator>> ordered =
                new ArrayList<>(accumulators.entrySet());
        ordered.sort(Map.Entry.comparingByKey());

        List<SkyIslandLithologicContact> contacts = new ArrayList<>(ordered.size());
        int nextContactId = 0;
        for (Map.Entry<Long, ContactAccumulator> entry : ordered) {
            ContactAccumulator accumulator = entry.getValue();
            double host = accumulator.hostFabricContrast / accumulator.faces;
            double alteration = accumulator.alterationContrast / accumulator.faces;
            double hydrologic = accumulator.hydrologicContrast / accumulator.faces;
            double mineralization = accumulator.mineralizationContrast / accumulator.faces;
            contacts.add(new SkyIslandLithologicContact(
                    nextContactId++,
                    accumulator.firstAssemblageId,
                    accumulator.secondAssemblageId,
                    contactKind(host, alteration, hydrologic, mineralization),
                    accumulator.faces,
                    clamp01(host),
                    clamp01(alteration),
                    clamp01(hydrologic),
                    clamp01(mineralization)));
        }
        return contacts;
    }

    private static void addContactFace(
            Map<Long, ContactAccumulator> accumulators,
            SkyIslandMaterialFamilyCell[] familyByIndex,
            int[] assemblageByIndex,
            int index,
            int neighborX,
            int neighborDepth,
            int neighborZ,
            int gridSize,
            int depthSamples) {
        if (neighborX < 0
                || neighborDepth < 0
                || neighborZ < 0
                || neighborX >= gridSize
                || neighborDepth >= depthSamples
                || neighborZ >= gridSize) {
            return;
        }
        int neighbor = index(
                neighborX, neighborDepth, neighborZ, gridSize, depthSamples);
        if (familyByIndex[neighbor] == null) {
            return;
        }

        int firstId = assemblageByIndex[index];
        int secondId = assemblageByIndex[neighbor];
        if (firstId == secondId) {
            return;
        }
        int low = Math.min(firstId, secondId);
        int high = Math.max(firstId, secondId);
        long key = ((long) low << 32) | (high & 0xFFFFFFFFL);
        ContactAccumulator accumulator = accumulators.computeIfAbsent(
                key, ignored -> new ContactAccumulator(low, high));

        SkyIslandMaterialFamilyCell first = familyByIndex[index];
        SkyIslandMaterialFamilyCell second = familyByIndex[neighbor];
        accumulator.faces++;
        accumulator.hostFabricContrast += 0.5
                * (Math.abs(first.coherentMassiveHost() - second.coherentMassiveHost())
                        + Math.abs(
                                first.layeredFabricRichHost()
                                        - second.layeredFabricRichHost()));
        accumulator.alterationContrast += Math.abs(
                first.stronglyAlteredHost() - second.stronglyAlteredHost());
        accumulator.hydrologicContrast += Math.abs(
                first.waterConditionedHost() - second.waterConditionedHost());
        accumulator.mineralizationContrast += Math.abs(
                first.mineralBearingStructuralHost()
                        - second.mineralBearingStructuralHost());
    }

    private static SkyIslandLithologicContactKind contactKind(
            double host,
            double alteration,
            double hydrologic,
            double mineralization) {
        double structuralScore = host * 1.05;
        double alterationScore = alteration * 1.10;
        double hydrologicScore = hydrologic * 1.05;
        double mineralizationScore = mineralization * 1.12;
        double maximum = Math.max(
                Math.max(structuralScore, alterationScore),
                Math.max(hydrologicScore, mineralizationScore));
        if (maximum < 0.08) {
            return SkyIslandLithologicContactKind.GRADATIONAL_CONTACT;
        }
        if (mineralizationScore == maximum) {
            return SkyIslandLithologicContactKind.MINERALIZATION_FRONT;
        }
        if (alterationScore == maximum) {
            return SkyIslandLithologicContactKind.ALTERATION_FRONT;
        }
        if (hydrologicScore == maximum) {
            return SkyIslandLithologicContactKind.HYDROLOGIC_FRONT;
        }
        return SkyIslandLithologicContactKind.HOST_FABRIC_CONTACT;
    }

    private static int index(
            int ix, int depth, int iz, int gridSize, int depthSamples) {
        return (iz * depthSamples + depth) * gridSize + ix;
    }

    private static int xIndex(int index, int gridSize) {
        return index % gridSize;
    }

    private static int depthIndex(int index, int gridSize, int depthSamples) {
        return (index / gridSize) % depthSamples;
    }

    private static int zIndex(int index, int gridSize, int depthSamples) {
        return index / (gridSize * depthSamples);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Component(
            SkyIslandLithologicAssemblageKind kind,
            List<Integer> indices) {}

    private static final class ContactAccumulator {
        private final int firstAssemblageId;
        private final int secondAssemblageId;
        private int faces;
        private double hostFabricContrast;
        private double alterationContrast;
        private double hydrologicContrast;
        private double mineralizationContrast;

        private ContactAccumulator(int firstAssemblageId, int secondAssemblageId) {
            this.firstAssemblageId = firstAssemblageId;
            this.secondAssemblageId = secondAssemblageId;
        }
    }
}
