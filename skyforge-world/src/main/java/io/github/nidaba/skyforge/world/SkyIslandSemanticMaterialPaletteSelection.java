package io.github.nidaba.skyforge.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0037 semantic palette-role selection constraints at one material-present position.
 *
 * <p>The selection retains AUTH-0036 provenance so a backend can bind roles coherently by
 * assemblage/contact rather than selecting unrelated materials independently at every sample.
 */
public record SkyIslandSemanticMaterialPaletteSelection(
        boolean owned,
        boolean materialPresent,
        int localAssemblageId,
        SkyIslandLithologicAssemblageKind localAssemblageKind,
        int contactId,
        SkyIslandLithologicContactKind contactKind,
        List<SkyIslandSemanticMaterialPaletteCandidate> candidates) {

    public SkyIslandSemanticMaterialPaletteSelection {
        candidates = List.copyOf(candidates);
        for (SkyIslandSemanticMaterialPaletteCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "palette candidate");
        }

        if (!owned && materialPresent) {
            throw new IllegalArgumentException("unowned palette selection cannot contain material");
        }

        if (!materialPresent) {
            if (localAssemblageId != -1
                    || localAssemblageKind != null
                    || contactId != -1
                    || contactKind != null
                    || !candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-material palette selection must contain empty provenance and candidates");
            }
        } else {
            if (localAssemblageId < 0 || localAssemblageKind == null) {
                throw new IllegalArgumentException(
                        "material palette selection requires local assemblage provenance");
            }
            if ((contactId < 0) != (contactKind == null)) {
                throw new IllegalArgumentException(
                        "contact id and kind must either both be present or both be absent");
            }

            EnumSet<SkyIslandSemanticMaterialPaletteRole> roles =
                    EnumSet.noneOf(SkyIslandSemanticMaterialPaletteRole.class);
            int required = 0;
            for (SkyIslandSemanticMaterialPaletteCandidate candidate : candidates) {
                if (!roles.add(candidate.role())) {
                    throw new IllegalArgumentException("palette roles must be unique per selection");
                }
                if (candidate.required()) {
                    required++;
                }
            }
            if (required != 1
                    || !roles.contains(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)) {
                throw new IllegalArgumentException(
                        "material palette selection requires exactly one required PRIMARY_MATRIX");
            }
            if (candidates.size() > SkyIslandSemanticMaterialPaletteRole.values().length) {
                throw new IllegalArgumentException("too many semantic palette candidates");
            }
        }
    }

    public Optional<SkyIslandSemanticMaterialPaletteCandidate> candidate(
            SkyIslandSemanticMaterialPaletteRole role) {
        Objects.requireNonNull(role, "role");
        return candidates.stream().filter(candidate -> candidate.role() == role).findFirst();
    }

    public boolean roleEligible(SkyIslandSemanticMaterialPaletteRole role) {
        return candidate(role).isPresent();
    }

    public static SkyIslandSemanticMaterialPaletteSelection outside() {
        return empty(false, false);
    }

    public static SkyIslandSemanticMaterialPaletteSelection authoredVoid() {
        return empty(true, false);
    }

    private static SkyIslandSemanticMaterialPaletteSelection empty(
            boolean owned, boolean materialPresent) {
        return new SkyIslandSemanticMaterialPaletteSelection(
                owned,
                materialPresent,
                -1,
                null,
                -1,
                null,
                List.of());
    }
}
