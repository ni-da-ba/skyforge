package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks the Python asset compiler's bounded target registry against the actual Minecraft
 * 1.21.1 block/state implementation on the NeoForge test classpath.
 *
 * <p>The Python unit suite separately requires the same manifest to equal minecraft_adapter.py's
 * capability registry. Together the two tests make the JSON manifest a checked bridge rather than a
 * second untrusted hand-written source of truth.</p>
 */
final class MinecraftAssetCompilerCapabilityManifestTest {
    private static final Path MANIFEST_FROM_REPO_ROOT =
            Path.of("tools", "asset_compiler", "minecraft_data", "java_1_21_1_guild_capabilities.json");
    private static final Path MANIFEST_FROM_SUBPROJECT =
            Path.of("..", "tools", "asset_compiler", "minecraft_data", "java_1_21_1_guild_capabilities.json");

    @Test
    void boundedGuildCapabilityManifestMatchesLiveMinecraftStatesAndDefaults() throws IOException {
        // Several existing Minecraft-facing tests use this factory to ensure built-in registries are
        // fully bootstrapped under the ordinary JUnit test runtime.
        MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));

        Path manifest = locateManifest();
        JsonObject root = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        assertEquals("minecraft-java-1.21.1", root.get("target").getAsString());
        assertEquals(3955, root.get("dataVersion").getAsInt());

        for (JsonElement element : root.getAsJsonArray("blocks")) {
            JsonObject entry = element.getAsJsonObject();
            String name = entry.get("name").getAsString();
            ResourceLocation id = resourceLocation(name);
            assertTrue(BuiltInRegistries.BLOCK.containsKey(id), () -> "missing live block " + name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            assertNotNull(block, () -> "registry returned null for " + name);

            Map<String, Property<?>> liveProperties = new LinkedHashMap<>();
            block.getStateDefinition().getProperties().stream()
                    .sorted(Comparator.comparing(Property::getName))
                    .forEach(property -> liveProperties.put(property.getName(), property));

            JsonObject declaredProperties = entry.getAsJsonObject("properties");
            assertEquals(
                    new TreeSet<>(liveProperties.keySet()),
                    new TreeSet<>(declaredProperties.keySet()),
                    () -> name + " property-name set diverges from live Minecraft");

            for (Map.Entry<String, Property<?>> propertyEntry : liveProperties.entrySet()) {
                String propertyName = propertyEntry.getKey();
                Property<?> property = propertyEntry.getValue();
                List<String> liveValues = possibleValueNames(property);
                List<String> declaredValues = new ArrayList<>();
                for (JsonElement value : declaredProperties.getAsJsonArray(propertyName)) {
                    declaredValues.add(value.getAsString());
                }
                declaredValues.sort(String::compareTo);
                assertEquals(
                        liveValues,
                        declaredValues,
                        () -> name + "[" + propertyName + "] possible values diverge from live Minecraft");
            }

            JsonObject declaredDefaults = entry.getAsJsonObject("defaults");
            assertEquals(
                    new TreeSet<>(liveProperties.keySet()),
                    new TreeSet<>(declaredDefaults.keySet()),
                    () -> name + " must declare the live default for every state property");
            BlockState defaultState = block.defaultBlockState();
            for (Map.Entry<String, Property<?>> propertyEntry : liveProperties.entrySet()) {
                String propertyName = propertyEntry.getKey();
                String liveDefault = stateValueName(defaultState, propertyEntry.getValue());
                assertEquals(
                        liveDefault,
                        declaredDefaults.get(propertyName).getAsString(),
                        () -> name + "[" + propertyName + "] default diverges from live Minecraft");
            }
        }
    }

    private static Path locateManifest() {
        for (Path candidate : List.of(MANIFEST_FROM_REPO_ROOT, MANIFEST_FROM_SUBPROJECT)) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new AssertionError(
                "could not locate asset-compiler Minecraft capability manifest from "
                        + Path.of("").toAbsolutePath());
    }

    private static ResourceLocation resourceLocation(String name) {
        int separator = name.indexOf(':');
        if (separator <= 0 || separator == name.length() - 1) {
            throw new AssertionError("invalid namespaced block id in manifest: " + name);
        }
        return ResourceLocation.fromNamespaceAndPath(
                name.substring(0, separator), name.substring(separator + 1));
    }

    private static List<String> possibleValueNames(Property<?> property) {
        List<String> result = new ArrayList<>();
        for (Comparable<?> value : property.getPossibleValues()) {
            result.add(propertyValueName(property, value));
        }
        result.sort(String::compareTo);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(Property property, Comparable value) {
        return property.getName(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String stateValueName(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }
}
