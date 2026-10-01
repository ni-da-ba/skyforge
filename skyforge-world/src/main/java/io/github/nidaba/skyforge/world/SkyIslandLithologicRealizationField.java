package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0036 stable semantic lithologic realization field.
 *
 * <p>The field composes AUTH-0033 local family character, AUTH-0034 coherent assemblage identity,
 * and AUTH-0035 continuous contact blending into one backend-neutral sampling contract.
 */
public final class SkyIslandLithologicRealizationField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSubsurfaceMaterialFieldSet material;
    private final SkyIslandLithologicContactRealizationField contacts;
    private final SkyIslandLithologicAssemblagePlan assemblagePlan;
    private final List<SkyIslandLithologicAssemblageCell> cells;
    private final SkyIslandLithologicAssemblage[] assemblagesById;
    private final double radius;

    private SkyIslandLithologicRealizationField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.material = SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        this.contacts = SkyIslandLithologicContactRealizationField.create(descriptor);
        this.assemblagePlan = contacts.plan().assemblagePlan();
        this.cells = assemblagePlan.cells();
        this.radius = descriptor.nominalRadius();

        int maximumId = assemblagePlan.assemblages().stream()
                .mapToInt(SkyIslandLithologicAssemblage::assemblageId)
                .max()
                .orElseThrow();
        this.assemblagesById = new SkyIslandLithologicAssemblage[maximumId + 1];
        for (SkyIslandLithologicAssemblage assemblage : assemblagePlan.assemblages()) {
            if (assemblagesById[assemblage.assemblageId()] != null) {
                throw new IllegalStateException("duplicate AUTH-0034 assemblage id");
            }
            assemblagesById[assemblage.assemblageId()] = assemblage;
        }
    }

    public static SkyIslandLithologicRealizationField create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandLithologicRealizationField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandLithologicAssemblagePlan assemblagePlan() {
        return assemblagePlan;
    }

    public SkyIslandLithologicContactRealizationPlan contactPlan() {
        return contacts.plan();
    }

    public SkyIslandLithologicRealizationSample sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandSubsurfaceMaterialSample materialSample = material.sample(position);
        if (!materialSample.owned()) {
            return SkyIslandLithologicRealizationSample.outside();
        }
        if (!materialSample.materialPresent()) {
            return SkyIslandLithologicRealizationSample.authoredVoid();
        }

        SkyIslandLithologicAssemblageCell local = nearestCell(position);
        SkyIslandMaterialFamilyCell localFamily = local.familyCharacter();
        SkyIslandLithologicContactRealizationSample contactSample =
                contacts.sample(position);

        if (contactSample.contactId() < 0) {
            return new SkyIslandLithologicRealizationSample(
                    true,
                    true,
                    local.assemblageId(),
                    local.assemblageKind(),
                    -1,
                    null,
                    local.assemblageId(),
                    local.assemblageKind(),
                    1.0,
                    -1,
                    null,
                    0.0,
                    localFamily.coherentMassiveHost(),
                    localFamily.layeredFabricRichHost(),
                    localFamily.stronglyAlteredHost(),
                    localFamily.waterConditionedHost(),
                    localFamily.mineralBearingStructuralHost());
        }

        SkyIslandLithologicAssemblage first =
                assemblage(contactSample.firstAssemblageId());
        SkyIslandLithologicAssemblage second =
                assemblage(contactSample.secondAssemblageId());
        double firstWeight = contactSample.firstAssemblageWeight();
        double secondWeight = contactSample.secondAssemblageWeight();
        double contactInfluence = contactSample.contactInfluence();

        double massiveTarget = blendedMean(
                first,
                second,
                firstWeight,
                secondWeight,
                SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST);
        double fabricTarget = blendedMean(
                first,
                second,
                firstWeight,
                secondWeight,
                SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST);
        double alterationTarget = blendedMean(
                first,
                second,
                firstWeight,
                secondWeight,
                SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST);
        double waterTarget = blendedMean(
                first,
                second,
                firstWeight,
                secondWeight,
                SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST);
        double mineralTarget = blendedMean(
                first,
                second,
                firstWeight,
                secondWeight,
                SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST);

        return new SkyIslandLithologicRealizationSample(
                true,
                true,
                local.assemblageId(),
                local.assemblageKind(),
                contactSample.contactId(),
                contactSample.contactKind(),
                first.assemblageId(),
                first.kind(),
                firstWeight,
                second.assemblageId(),
                second.kind(),
                secondWeight,
                lerp(localFamily.coherentMassiveHost(), massiveTarget, contactInfluence),
                lerp(localFamily.layeredFabricRichHost(), fabricTarget, contactInfluence),
                lerp(localFamily.stronglyAlteredHost(), alterationTarget, contactInfluence),
                lerp(localFamily.waterConditionedHost(), waterTarget, contactInfluence),
                lerp(localFamily.mineralBearingStructuralHost(), mineralTarget, contactInfluence));
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
                    "material-present AUTH-0036 sample requires at least one AUTH-0034 host cell");
        }
        return best;
    }

    private SkyIslandLithologicAssemblage assemblage(int id) {
        if (id < 0 || id >= assemblagesById.length || assemblagesById[id] == null) {
            throw new IllegalStateException("unknown AUTH-0034 assemblage id " + id);
        }
        return assemblagesById[id];
    }

    private static double blendedMean(
            SkyIslandLithologicAssemblage first,
            SkyIslandLithologicAssemblage second,
            double firstWeight,
            double secondWeight,
            SkyIslandMaterialFamilyKind family) {
        return clamp01(
                firstWeight * first.meanFamilyMembership(family)
                        + secondWeight * second.meanFamilyMembership(family));
    }

    private static double lerp(double first, double second, double t) {
        return clamp01(first + (second - first) * t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
