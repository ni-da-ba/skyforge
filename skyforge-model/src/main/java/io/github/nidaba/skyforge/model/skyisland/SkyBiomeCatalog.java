package io.github.nidaba.skyforge.model.skyisland;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Small, explicit backend-neutral SkyBiome vocabulary for the playable alpha. */
public final class SkyBiomeCatalog {
    public static final SkyBiomeId OPEN_SKY_ID = SkyBiomeId.parse("skyforge:open_sky");
    public static final SkyBiomeId CLOUD_SHELF_ID = SkyBiomeId.parse("skyforge:cloud_shelf");
    public static final SkyBiomeId ISLAND_LEE_ID = SkyBiomeId.parse("skyforge:island_lee");

    private static final Map<SkyBiomeId, SkyBiomeDefinition> DEFINITIONS = definitions();

    private SkyBiomeCatalog() {}

    /** Returns definitions in stable ID order. */
    public static List<SkyBiomeDefinition> definitions() {
        return DEFINITIONS.values().stream().toList();
    }

    /** Finds the built-in definition for an exact SkyBiome ID. */
    public static Optional<SkyBiomeDefinition> find(SkyBiomeId id) {
        return Optional.ofNullable(DEFINITIONS.get(Objects.requireNonNull(id, "id")));
    }

    private static Map<SkyBiomeId, SkyBiomeDefinition> definitions() {
        TreeMap<SkyBiomeId, SkyBiomeDefinition> definitions = new TreeMap<>();
        add(definitions, OPEN_SKY_ID, SkyBiomeTrait.OPEN_AIR);
        add(definitions, CLOUD_SHELF_ID, SkyBiomeTrait.CLOUD_ASSOCIATED);
        add(definitions, ISLAND_LEE_ID, SkyBiomeTrait.TERRAIN_SHELTERED);
        return Map.copyOf(definitions);
    }

    private static void add(
            Map<SkyBiomeId, SkyBiomeDefinition> definitions,
            SkyBiomeId id,
            SkyBiomeTrait trait) {
        definitions.put(id, new SkyBiomeDefinition(id, Set.of(trait)));
    }
}
