package io.github.nidaba.skyforge.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0038 semantic palette selection with one stable binding key per eligible role. */
public record SkyIslandSemanticPaletteBindingSelection(
        boolean owned,
        boolean materialPresent,
        int localAssemblageId,
        SkyIslandLithologicAssemblageKind localAssemblageKind,
        int contactId,
        SkyIslandLithologicContactKind contactKind,
        List<SkyIslandSemanticPaletteBindingCandidate> bindings) {

    public SkyIslandSemanticPaletteBindingSelection {
        bindings = List.copyOf(bindings);
        for (SkyIslandSemanticPaletteBindingCandidate binding : bindings) {
            Objects.requireNonNull(binding, "binding");
        }

        if (!owned && materialPresent) {
            throw new IllegalArgumentException("unowned binding selection cannot contain material");
        }

        if (!materialPresent) {
            if (localAssemblageId != -1
                    || localAssemblageKind != null
                    || contactId != -1
                    || contactKind != null
                    || !bindings.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-material binding selection must contain empty provenance and bindings");
            }
        } else {
            if (localAssemblageId < 0 || localAssemblageKind == null) {
                throw new IllegalArgumentException(
                        "material binding selection requires local assemblage provenance");
            }
            if ((contactId < 0) != (contactKind == null)) {
                throw new IllegalArgumentException(
                        "contact id and kind must either both be present or both be absent");
            }

            EnumSet<SkyIslandSemanticMaterialPaletteRole> roles =
                    EnumSet.noneOf(SkyIslandSemanticMaterialPaletteRole.class);
            int required = 0;
            for (SkyIslandSemanticPaletteBindingCandidate binding : bindings) {
                if (!roles.add(binding.candidate().role())) {
                    throw new IllegalArgumentException(
                            "binding roles must be unique per selection");
                }
                if (binding.candidate().required()) {
                    required++;
                }
            }
            if (required != 1
                    || !roles.contains(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)) {
                throw new IllegalArgumentException(
                        "material binding selection requires exactly one required PRIMARY_MATRIX");
            }
        }
    }

    public Optional<SkyIslandSemanticPaletteBindingCandidate> binding(
            SkyIslandSemanticMaterialPaletteRole role) {
        Objects.requireNonNull(role, "role");
        return bindings.stream()
                .filter(binding -> binding.candidate().role() == role)
                .findFirst();
    }

    public static SkyIslandSemanticPaletteBindingSelection outside() {
        return empty(false, false);
    }

    public static SkyIslandSemanticPaletteBindingSelection authoredVoid() {
        return empty(true, false);
    }

    private static SkyIslandSemanticPaletteBindingSelection empty(
            boolean owned, boolean materialPresent) {
        return new SkyIslandSemanticPaletteBindingSelection(
                owned,
                materialPresent,
                -1,
                null,
                -1,
                null,
                List.of());
    }
}
