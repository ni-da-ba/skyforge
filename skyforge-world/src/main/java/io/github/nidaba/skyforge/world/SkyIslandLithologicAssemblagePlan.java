package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic AUTH-0034 coherent lithologic assemblage and contact plan. */
public record SkyIslandLithologicAssemblagePlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        int depthSamples,
        double horizontalSpacing,
        double depthSpacing,
        int activeHostCells,
        List<SkyIslandLithologicAssemblage> assemblages,
        List<SkyIslandLithologicContact> contacts) {

    public SkyIslandLithologicAssemblagePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 2 || depthSamples < 2) {
            throw new IllegalArgumentException("assemblage plan requires at least a 2 x 2 x 2 grid");
        }
        if (!Double.isFinite(horizontalSpacing)
                || horizontalSpacing <= 0.0
                || !Double.isFinite(depthSpacing)
                || depthSpacing <= 0.0) {
            throw new IllegalArgumentException("assemblage plan spacing must be positive and finite");
        }
        if (activeHostCells < 1 || activeHostCells > gridSize * depthSamples * gridSize) {
            throw new IllegalArgumentException("activeHostCells must fit inside planning lattice");
        }

        assemblages = List.copyOf(assemblages);
        contacts = List.copyOf(contacts);
        Set<Integer> seen = new HashSet<>();
        int cellCount = 0;
        for (SkyIslandLithologicAssemblage assemblage : assemblages) {
            Objects.requireNonNull(assemblage, "assemblage");
            for (SkyIslandLithologicAssemblageCell cell : assemblage.cells()) {
                if (!seen.add(cell.index())) {
                    throw new IllegalArgumentException(
                            "each active host cell must belong to exactly one assemblage");
                }
                cellCount++;
            }
        }
        if (cellCount != activeHostCells) {
            throw new IllegalArgumentException(
                    "assemblages must cover the complete active host-material planning volume");
        }
        contacts.forEach(contact -> Objects.requireNonNull(contact, "contact"));
    }

    public long assemblageCount(SkyIslandLithologicAssemblageKind kind) {
        return assemblages.stream().filter(unit -> unit.kind() == kind).count();
    }

    public long contactCount(SkyIslandLithologicContactKind kind) {
        return contacts.stream().filter(contact -> contact.kind() == kind).count();
    }

    public int largestAssemblageCellCount() {
        return assemblages.stream()
                .mapToInt(SkyIslandLithologicAssemblage::cellCount)
                .max()
                .orElse(0);
    }

    public int smallestAssemblageCellCount() {
        return assemblages.stream()
                .mapToInt(SkyIslandLithologicAssemblage::cellCount)
                .min()
                .orElse(0);
    }

    public List<SkyIslandLithologicAssemblageCell> cells() {
        List<SkyIslandLithologicAssemblageCell> cells = new ArrayList<>(activeHostCells);
        for (SkyIslandLithologicAssemblage assemblage : assemblages) {
            cells.addAll(assemblage.cells());
        }
        return List.copyOf(cells);
    }
}
