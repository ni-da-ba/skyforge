package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0039 backend-neutral material-binding request field.
 *
 * <p>Stable resolver requests are paired with local AUTH-0037 expression state without allowing
 * local support variation to alter binding identity.
 */
public final class SkyIslandMaterialBindingRequestField {
    private final SkyIslandSemanticPaletteBindingField bindings;
    private final SkyIslandMaterialBindingRequestCatalog catalog;

    private SkyIslandMaterialBindingRequestField(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        this.bindings = SkyIslandSemanticPaletteBindingField.create(descriptor);
        this.catalog =
                SkyIslandMaterialBindingRequestCatalog.create(
                        descriptor, bindings.plan());
    }

    public static SkyIslandMaterialBindingRequestField create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandMaterialBindingRequestField(descriptor);
    }

    public SkyIslandMaterialBindingRequestCatalog catalog() {
        return catalog;
    }

    public SkyIslandMaterialBindingRequestSelection sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandSemanticPaletteBindingSelection selection =
                bindings.sample(position);
        if (!selection.owned()) {
            return SkyIslandMaterialBindingRequestSelection.outside();
        }
        if (!selection.materialPresent()) {
            return SkyIslandMaterialBindingRequestSelection.authoredVoid();
        }

        List<SkyIslandMaterialBindingRequestUse> uses =
                new ArrayList<>(selection.bindings().size());
        for (SkyIslandSemanticPaletteBindingCandidate binding :
                selection.bindings()) {
            uses.add(new SkyIslandMaterialBindingRequestUse(
                    binding, catalog.request(binding.bindingKey())));
        }
        uses.sort(Comparator.comparingInt(
                use -> use.request().role().ordinal()));

        return new SkyIslandMaterialBindingRequestSelection(
                true,
                true,
                selection.localAssemblageId(),
                selection.localAssemblageKind(),
                selection.contactId(),
                selection.contactKind(),
                uses);
    }
}
