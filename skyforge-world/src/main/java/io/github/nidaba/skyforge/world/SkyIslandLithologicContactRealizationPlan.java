package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic AUTH-0035 continuous contact-realization plan for one authored island. */
public record SkyIslandLithologicContactRealizationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandLithologicAssemblagePlan assemblagePlan,
        List<SkyIslandLithologicContactRealization> realizations) {

    public SkyIslandLithologicContactRealizationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        assemblagePlan = Objects.requireNonNull(assemblagePlan, "assemblagePlan");
        if (!assemblagePlan.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "contact-realization descriptor must match assemblage plan");
        }
        realizations = List.copyOf(realizations);
        realizations.forEach(realization -> Objects.requireNonNull(realization, "realization"));
        if (realizations.size() != assemblagePlan.contacts().size()) {
            throw new IllegalArgumentException(
                    "every AUTH-0034 contact must receive one AUTH-0035 realization");
        }
    }

    public int patchCount() {
        return realizations.stream()
                .mapToInt(realization -> realization.patches().size())
                .sum();
    }

    public double meanHalfWidth() {
        return realizations.stream()
                .flatMap(realization -> realization.patches().stream())
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .average()
                .orElse(0.0);
    }

    public double minimumHalfWidth() {
        return realizations.stream()
                .flatMap(realization -> realization.patches().stream())
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .min()
                .orElse(0.0);
    }

    public double maximumHalfWidth() {
        return realizations.stream()
                .flatMap(realization -> realization.patches().stream())
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .max()
                .orElse(0.0);
    }
}
