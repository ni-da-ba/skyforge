package io.github.nidaba.skyforge.reference.backend;

import io.github.nidaba.skyforge.world.SkyIslandLithologicRealizationChannel;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reference-backend proof of a stable concrete binding table keyed only by AUTH-0038 identity.
 *
 * <p>The table is intentionally outside skyforge-world. Its material choices are diagnostic proof
 * values, not canonical Skyforge materials.
 */
public final class ReferenceAuthoredMaterialBindingTable {
    private ReferenceAuthoredMaterialBindingTable() {}

    public static Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> resolve(
            Collection<SkyIslandSemanticPaletteBindingKey> keys) {
        Objects.requireNonNull(keys, "keys");
        ArrayList<SkyIslandSemanticPaletteBindingKey> ordered = new ArrayList<>(keys);
        ordered.sort(
                java.util.Comparator.comparing(
                        SkyIslandSemanticPaletteBindingKey::canonicalToken));

        Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> result =
                new LinkedHashMap<>();
        for (SkyIslandSemanticPaletteBindingKey key : ordered) {
            Objects.requireNonNull(key, "binding key");
            result.put(key, resolveOne(key));
        }
        return Map.copyOf(result);
    }

    public static ReferenceAuthoredMaterial resolveOne(
            SkyIslandSemanticPaletteBindingKey key) {
        Objects.requireNonNull(key, "key");
        boolean alternate =
                Math.floorMod(key.canonicalToken().hashCode(), 2) == 1;

        return switch (key.role()) {
            case PRIMARY_MATRIX, SECONDARY_MATRIX ->
                    hostMaterial(key.sourceChannel(), alternate);
            case ALTERATION_OVERPRINT ->
                    alternate
                            ? ReferenceAuthoredMaterial.ALTERED_B
                            : ReferenceAuthoredMaterial.ALTERED_A;
            case HYDROLOGIC_CONDITIONING ->
                    alternate
                            ? ReferenceAuthoredMaterial.HYDRATED_B
                            : ReferenceAuthoredMaterial.HYDRATED_A;
            case MINERAL_BEARING_STRUCTURE ->
                    alternate
                            ? ReferenceAuthoredMaterial.MINERAL_B
                            : ReferenceAuthoredMaterial.MINERAL_A;
        };
    }

    private static ReferenceAuthoredMaterial hostMaterial(
            SkyIslandLithologicRealizationChannel channel,
            boolean alternate) {
        return switch (channel) {
            case MASSIVE_MATRIX ->
                    alternate
                            ? ReferenceAuthoredMaterial.MASSIVE_B
                            : ReferenceAuthoredMaterial.MASSIVE_A;
            case FABRIC_RICH_MATRIX ->
                    alternate
                            ? ReferenceAuthoredMaterial.FABRIC_B
                            : ReferenceAuthoredMaterial.FABRIC_A;
            default ->
                    throw new IllegalArgumentException(
                            "matrix binding uses non-host source channel: " + channel);
        };
    }
}
