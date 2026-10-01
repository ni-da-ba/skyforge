package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Aggregated face-adjacent semantic contact between two coherent AUTH-0034 assemblages.
 *
 * <p>Contrasts are measured from the complete AUTH-0033 family state on either side of the contact.
 */
public record SkyIslandLithologicContact(
        int contactId,
        int firstAssemblageId,
        int secondAssemblageId,
        SkyIslandLithologicContactKind kind,
        int faceCount,
        double hostFabricContrast,
        double alterationContrast,
        double hydrologicContrast,
        double mineralizationContrast) {

    public SkyIslandLithologicContact {
        if (contactId < 0) {
            throw new IllegalArgumentException("contactId must be non-negative");
        }
        if (firstAssemblageId < 0
                || secondAssemblageId < 0
                || firstAssemblageId >= secondAssemblageId) {
            throw new IllegalArgumentException(
                    "contact assemblage ids must be distinct, non-negative, and ordered");
        }
        kind = Objects.requireNonNull(kind, "kind");
        if (faceCount < 1) {
            throw new IllegalArgumentException("contact must contain at least one adjacent face");
        }
        requireNormalized("hostFabricContrast", hostFabricContrast);
        requireNormalized("alterationContrast", alterationContrast);
        requireNormalized("hydrologicContrast", hydrologicContrast);
        requireNormalized("mineralizationContrast", mineralizationContrast);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
