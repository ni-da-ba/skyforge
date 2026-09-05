package io.github.nidaba.skyforge.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0039 material sample carrying stable resolver requests plus local expression state. */
public record SkyIslandMaterialBindingRequestSelection(
        boolean owned,
        boolean materialPresent,
        int localAssemblageId,
        SkyIslandLithologicAssemblageKind localAssemblageKind,
        int contactId,
        SkyIslandLithologicContactKind contactKind,
        List<SkyIslandMaterialBindingRequestUse> uses) {

    public SkyIslandMaterialBindingRequestSelection {
        uses = List.copyOf(uses);
        for (SkyIslandMaterialBindingRequestUse use : uses) {
            Objects.requireNonNull(use, "request use");
        }

        if (!owned && materialPresent) {
            throw new IllegalArgumentException(
                    "unowned request selection cannot contain material");
        }

        if (!materialPresent) {
            if (localAssemblageId != -1
                    || localAssemblageKind != null
                    || contactId != -1
                    || contactKind != null
                    || !uses.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-material request selection must contain empty provenance and requests");
            }
        } else {
            if (localAssemblageId < 0 || localAssemblageKind == null) {
                throw new IllegalArgumentException(
                        "material request selection requires local assemblage provenance");
            }
            if ((contactId < 0) != (contactKind == null)) {
                throw new IllegalArgumentException(
                        "contact id and kind must either both be present or both be absent");
            }
            EnumSet<SkyIslandSemanticMaterialPaletteRole> roles =
                    EnumSet.noneOf(SkyIslandSemanticMaterialPaletteRole.class);
            int required = 0;
            for (SkyIslandMaterialBindingRequestUse use : uses) {
                if (!roles.add(use.request().role())) {
                    throw new IllegalArgumentException(
                            "material-binding request roles must be unique per sample");
                }
                if (use.request().required()) {
                    required++;
                }
            }
            if (required != 1
                    || !roles.contains(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)) {
                throw new IllegalArgumentException(
                        "material request selection requires exactly one required PRIMARY_MATRIX");
            }
        }
    }

    public Optional<SkyIslandMaterialBindingRequestUse> use(
            SkyIslandSemanticMaterialPaletteRole role) {
        Objects.requireNonNull(role, "role");
        return uses.stream().filter(use -> use.request().role() == role).findFirst();
    }

    public static SkyIslandMaterialBindingRequestSelection outside() {
        return empty(false, false);
    }

    public static SkyIslandMaterialBindingRequestSelection authoredVoid() {
        return empty(true, false);
    }

    private static SkyIslandMaterialBindingRequestSelection empty(
            boolean owned, boolean materialPresent) {
        return new SkyIslandMaterialBindingRequestSelection(
                owned,
                materialPresent,
                -1,
                null,
                -1,
                null,
                List.of());
    }
}
