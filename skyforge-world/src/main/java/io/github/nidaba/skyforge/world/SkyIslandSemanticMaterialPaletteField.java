package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0037 backend-neutral semantic material palette-role selector.
 *
 * <p>This layer decides which semantic roles a backend is allowed to bind at a position and places
 * bounded expression ceilings on optional roles. It never chooses a concrete material.
 */
public final class SkyIslandSemanticMaterialPaletteField {
    public static final double SECONDARY_MATRIX_MIN_SUPPORT = 0.18;
    public static final double SECONDARY_MATRIX_MIN_RATIO = 0.28;
    public static final double ALTERATION_MIN_SUPPORT = 0.22;
    public static final double HYDROLOGIC_MIN_SUPPORT = 0.24;
    public static final double MINERAL_MIN_SUPPORT = 0.20;

    private final SkyIslandLithologicRealizationField realization;

    private SkyIslandSemanticMaterialPaletteField(SkyIslandDescriptor descriptor) {
        this.realization =
                SkyIslandLithologicRealizationField.create(
                        Objects.requireNonNull(descriptor, "descriptor"));
    }

    public static SkyIslandSemanticMaterialPaletteField create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandSemanticMaterialPaletteField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return realization.descriptor();
    }

    public SkyIslandSemanticMaterialPaletteSelection sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandLithologicRealizationSample sample = realization.sample(position);
        if (!sample.owned()) {
            return SkyIslandSemanticMaterialPaletteSelection.outside();
        }
        if (!sample.materialPresent()) {
            return SkyIslandSemanticMaterialPaletteSelection.authoredVoid();
        }

        List<SkyIslandSemanticMaterialPaletteCandidate> candidates = new ArrayList<>(5);
        double massive = sample.massiveMatrix();
        double fabric = sample.fabricRichMatrix();
        SkyIslandLithologicRealizationChannel primaryChannel =
                massive >= fabric
                        ? SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX
                        : SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX;
        SkyIslandLithologicRealizationChannel secondaryChannel =
                primaryChannel == SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX
                        ? SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX
                        : SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX;
        double primarySupport = Math.max(massive, fabric);
        double secondarySupport = Math.min(massive, fabric);

        candidates.add(new SkyIslandSemanticMaterialPaletteCandidate(
                SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX,
                primaryChannel,
                primarySupport,
                1.0,
                true));

        if (secondarySupport >= SECONDARY_MATRIX_MIN_SUPPORT
                && secondarySupport / primarySupport >= SECONDARY_MATRIX_MIN_RATIO) {
            candidates.add(new SkyIslandSemanticMaterialPaletteCandidate(
                    SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX,
                    secondaryChannel,
                    secondarySupport,
                    clamp(0.18 + 0.34 * secondarySupport, 0.18, 0.48),
                    false));
        }

        addOptional(
                candidates,
                SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT,
                sample.alterationOverprint(),
                ALTERATION_MIN_SUPPORT,
                0.16,
                0.42,
                0.56);
        addOptional(
                candidates,
                SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                SkyIslandLithologicRealizationChannel.WATER_CONDITIONING,
                sample.waterConditioning(),
                HYDROLOGIC_MIN_SUPPORT,
                0.14,
                0.34,
                0.48);
        addOptional(
                candidates,
                SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE,
                sample.mineralBearingStructure(),
                MINERAL_MIN_SUPPORT,
                0.10,
                0.26,
                0.34);

        candidates.sort(Comparator.comparingInt(candidate -> candidate.role().ordinal()));

        return new SkyIslandSemanticMaterialPaletteSelection(
                true,
                true,
                sample.localAssemblageId(),
                sample.localAssemblageKind(),
                sample.contactId(),
                sample.contactKind(),
                candidates);
    }

    private static void addOptional(
            List<SkyIslandSemanticMaterialPaletteCandidate> candidates,
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel channel,
            double support,
            double threshold,
            double baseCeiling,
            double supportScale,
            double maximumCeiling) {
        if (support < threshold) {
            return;
        }
        candidates.add(new SkyIslandSemanticMaterialPaletteCandidate(
                role,
                channel,
                support,
                clamp(baseCeiling + supportScale * support, baseCeiling, maximumCeiling),
                false));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
