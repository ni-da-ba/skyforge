package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0038 continuously sampleable semantic palette-binding coherence field.
 *
 * <p>Planning-cell domains provide stable assemblage/conditioned-region keys. Continuous contact
 * samples that create a role/source state absent from the nearest planning cell receive a stable
 * contact-scoped fallback key rather than a per-sample binding.
 */
public final class SkyIslandSemanticPaletteBindingField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticMaterialPaletteField palette;
    private final SkyIslandLithologicRealizationField realization;
    private final SkyIslandSemanticPaletteBindingPlan plan;
    private final List<SkyIslandLithologicAssemblageCell> cells;
    private final Map<Long, SkyIslandSemanticPaletteBindingKey> plannedKeys;
    private final double radius;

    private SkyIslandSemanticPaletteBindingField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.palette = SkyIslandSemanticMaterialPaletteField.create(descriptor);
        this.realization = SkyIslandLithologicRealizationField.create(descriptor);
        this.plan = SkyIslandSemanticPaletteBindingPlanner.plan(descriptor);
        this.cells = realization.assemblagePlan().cells();
        this.radius = descriptor.nominalRadius();
        this.plannedKeys = new HashMap<>();

        for (SkyIslandSemanticPaletteBindingDomain domain : plan.domains()) {
            for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
                long nodeKey = nodeKey(cell.index(), domain.key().role());
                SkyIslandSemanticPaletteBindingKey previous =
                        plannedKeys.put(nodeKey, domain.key());
                if (previous != null) {
                    throw new IllegalStateException(
                            "planning-cell role assigned to multiple binding keys");
                }
            }
        }
    }

    public static SkyIslandSemanticPaletteBindingField create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandSemanticPaletteBindingField(descriptor);
    }

    public SkyIslandSemanticPaletteBindingPlan plan() {
        return plan;
    }

    public SkyIslandSemanticPaletteBindingSelection sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandSemanticMaterialPaletteSelection selection = palette.sample(position);
        if (!selection.owned()) {
            return SkyIslandSemanticPaletteBindingSelection.outside();
        }
        if (!selection.materialPresent()) {
            return SkyIslandSemanticPaletteBindingSelection.authoredVoid();
        }

        SkyIslandLithologicAssemblageCell nearest = nearestCell(position);
        List<SkyIslandSemanticPaletteBindingCandidate> bindings =
                new ArrayList<>(selection.candidates().size());

        for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                selection.candidates()) {
            SkyIslandSemanticPaletteBindingKey key =
                    plannedKeys.get(nodeKey(nearest.index(), candidate.role()));
            if (key == null || key.sourceChannel() != candidate.sourceChannel()) {
                if (selection.contactId() < 0) {
                    throw new IllegalStateException(
                            "off-contact AUTH-0037 candidate lacks an AUTH-0038 planning domain");
                }
                key = SkyIslandSemanticPaletteBindingKey.of(
                        descriptor.identity(),
                        candidate.role(),
                        candidate.sourceChannel(),
                        SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION,
                        selection.contactId());
            }
            bindings.add(new SkyIslandSemanticPaletteBindingCandidate(candidate, key));
        }

        bindings.sort(Comparator.comparingInt(
                binding -> binding.candidate().role().ordinal()));

        return new SkyIslandSemanticPaletteBindingSelection(
                true,
                true,
                selection.localAssemblageId(),
                selection.localAssemblageKind(),
                selection.contactId(),
                selection.contactKind(),
                bindings);
    }

    private SkyIslandLithologicAssemblageCell nearestCell(
            SkyIslandSubsurfacePosition position) {
        SkyIslandLithologicAssemblageCell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (SkyIslandLithologicAssemblageCell cell : cells) {
            double dx = (position.x() - cell.position().x()) / radius;
            double dz = (position.z() - cell.position().z()) / radius;
            double dd = position.depthFraction() - cell.position().depthFraction();
            double distance = dx * dx + dz * dz + dd * dd;
            if (distance < bestDistance
                    || (distance == bestDistance
                            && (best == null || cell.index() < best.index()))) {
                best = cell;
                bestDistance = distance;
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                    "material-present AUTH-0038 sample requires an AUTH-0034 host cell");
        }
        return best;
    }

    private static long nodeKey(
            int cellIndex, SkyIslandSemanticMaterialPaletteRole role) {
        return (((long) cellIndex) << 8) | (role.ordinal() & 0xFFL);
    }
}
