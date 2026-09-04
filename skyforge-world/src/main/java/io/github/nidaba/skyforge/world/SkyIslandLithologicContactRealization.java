package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Continuous finite-width realization metadata for one AUTH-0034 lithologic contact. */
public record SkyIslandLithologicContactRealization(
        SkyIslandLithologicContact contact,
        List<SkyIslandLithologicContactPatch> patches) {

    public SkyIslandLithologicContactRealization {
        contact = Objects.requireNonNull(contact, "contact");
        patches = List.copyOf(patches);
        if (patches.isEmpty()) {
            throw new IllegalArgumentException("contact realization requires at least one patch");
        }
        for (SkyIslandLithologicContactPatch patch : patches) {
            Objects.requireNonNull(patch, "contact patch");
            if (patch.contactId() != contact.contactId()
                    || patch.firstAssemblageId() != contact.firstAssemblageId()
                    || patch.secondAssemblageId() != contact.secondAssemblageId()) {
                throw new IllegalArgumentException(
                        "contact patches must retain parent contact provenance");
            }
        }
    }

    public double meanHalfWidth() {
        return patches.stream()
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .average()
                .orElse(0.0);
    }

    public double minimumHalfWidth() {
        return patches.stream()
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .min()
                .orElse(0.0);
    }

    public double maximumHalfWidth() {
        return patches.stream()
                .mapToDouble(SkyIslandLithologicContactPatch::normalizedHalfWidth)
                .max()
                .orElse(0.0);
    }

    public double meanSharpness() {
        return patches.stream()
                .mapToDouble(SkyIslandLithologicContactPatch::transitionSharpness)
                .average()
                .orElse(0.0);
    }
}
