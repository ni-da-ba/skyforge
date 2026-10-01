package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One exact AUTH-0033 host-material cell enriched with AUTH-0098 petroleum-system opportunity.
 *
 * <p>All values are normalized geological planning evidence. They are not oil saturation, reserve,
 * pressure, grade, thickness, physical volume, or backend deposit policy.
 */
public record SkyIslandPetroleumSystemOpportunityCell(
        SkyIslandMaterialFamilyCell sourceCell,
        double sourcePotential,
        double reservoirPotential,
        double sealPotential,
        double deeperSourceSupport,
        double shallowerSealSupport,
        double systemOpportunity) {

    public SkyIslandPetroleumSystemOpportunityCell {
        sourceCell = Objects.requireNonNull(sourceCell, "sourceCell");
        requireNormalized("sourcePotential", sourcePotential);
        requireNormalized("reservoirPotential", reservoirPotential);
        requireNormalized("sealPotential", sealPotential);
        requireNormalized("deeperSourceSupport", deeperSourceSupport);
        requireNormalized("shallowerSealSupport", shallowerSealSupport);
        requireNormalized("systemOpportunity", systemOpportunity);

        if (sourcePotential > sourceCell.layeredFabricRichHost()) {
            throw new IllegalArgumentException(
                    "petroleum source potential cannot exceed accepted layered-host support");
        }
        if (systemOpportunity > reservoirPotential
                || systemOpportunity > deeperSourceSupport
                || systemOpportunity > shallowerSealSupport) {
            throw new IllegalArgumentException(
                    "petroleum-system opportunity must remain subordinate to reservoir/source/seal support");
        }
        if ((reservoirPotential == 0.0
                        || deeperSourceSupport == 0.0
                        || shallowerSealSupport == 0.0)
                && systemOpportunity != 0.0) {
            throw new IllegalArgumentException(
                    "petroleum-system opportunity requires reservoir, deeper-source, and shallower-seal support");
        }
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
