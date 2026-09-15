package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;

/**
 * Opt-in live-registry proof for the WBY C1 structure compiler's Create vocabulary.
 *
 * <p>The probe is deliberately piggy-backed on the already isolated C21 baseline server run. That
 * run resolves Create through {@code waveC1Pin("create", "coordinate")}; the companion Gradle
 * resolver gate verifies the exact immutable artifact before this process starts. Nothing here
 * grants runtime gameplay authority or changes an ordinary Skyforge launch.
 */
final class SkyforgeWbyC1StructureRegistryProbe {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyC1StructureRegistryProbe";
    static final String RESULT_FILE_PROPERTY = "skyforge.dev.wbyC1StructureRegistryResultFile";

    private static final Set<String> BOOLEAN_DOMAIN = Set.of("false", "true");
    private static final Map<String, Set<String>> PANE_PROPERTIES = Map.of(
            "east", BOOLEAN_DOMAIN,
            "north", BOOLEAN_DOMAIN,
            "south", BOOLEAN_DOMAIN,
            "waterlogged", BOOLEAN_DOMAIN,
            "west", BOOLEAN_DOMAIN);
    private static final Map<String, String> PANE_DEFAULTS = Map.of(
            "east", "false",
            "north", "false",
            "south", "false",
            "waterlogged", "false",
            "west", "false");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWbyC1StructureRegistryProbe.class.getName());

    private SkyforgeWbyC1StructureRegistryProbe() {}

    static void runIfEnabled(MinecraftServer server) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        try {
            String createVersion = ModList.get()
                    .getModContainerById("create")
                    .orElseThrow(() -> new IllegalStateException("Create is not loaded"))
                    .getModInfo()
                    .getVersion()
                    .toString();
            if (!createVersion.contains("6.0.10")) {
                throw new IllegalStateException(
                        "expected Create 6.0.10 runtime but observed version " + createVersion);
            }
            evidence.put("createVersion", createVersion);

            requireStateContract("create:andesite_casing", Map.of(), Map.of(), evidence);
            requireStateContract("create:brass_casing", Map.of(), Map.of(), evidence);
            requireStateContract("create:copper_casing", Map.of(), Map.of(), evidence);
            requireStateContract("create:industrial_iron_block", Map.of(), Map.of(), evidence);
            requireStateContract("create:weathered_iron_block", Map.of(), Map.of(), evidence);
            requireStateContract("create:framed_glass_pane", PANE_PROPERTIES, PANE_DEFAULTS, evidence);
            requireStateContract("create:industrial_iron_window_pane", PANE_PROPERTIES, PANE_DEFAULTS, evidence);
            requireStateContract("create:ornate_iron_window_pane", PANE_PROPERTIES, PANE_DEFAULTS, evidence);

            evidence.put("catalogBlocksValidated", "8");
            evidence.put("paneContractsValidated", "3");
            evidence.put("status", "PASS");
            writeResult(evidence);
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WBY_C1_STRUCTURE_REGISTRY PASS evidence=" + evidence);
            server.halt(false);
        } catch (RuntimeException exception) {
            evidence.put("status", "FAIL");
            evidence.put("failure", exception.getMessage() == null
                    ? exception.getClass().getName()
                    : exception.getMessage());
            writeResult(evidence);
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "WBY_C1_STRUCTURE_REGISTRY FAIL evidence=" + evidence);
            server.halt(false);
            throw exception;
        }
    }

    private static void requireStateContract(
            String resourceName,
            Map<String, Set<String>> expectedDomains,
            Map<String, String> expectedDefaults,
            Map<String, String> evidence) {
        ResourceLocation key = ResourceLocation.parse(resourceName);
        Block block = BuiltInRegistries.BLOCK.get(key);
        if (block == Blocks.AIR || !BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalStateException("missing Create structure block " + resourceName);
        }

        Map<String, Set<String>> actualDomains = new TreeMap<>();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            Set<String> values = new TreeSet<>();
            for (Comparable<?> value : property.getPossibleValues()) {
                values.add(String.valueOf(value));
            }
            actualDomains.put(property.getName(), Set.copyOf(values));
        }
        if (!actualDomains.equals(expectedDomains)) {
            throw new IllegalStateException(
                    resourceName + " property domains differ: expected=" + expectedDomains
                            + " actual=" + actualDomains);
        }

        BlockState defaultState = block.defaultBlockState();
        Map<String, String> actualDefaults = new TreeMap<>();
        for (var entry : defaultState.getValues().entrySet()) {
            actualDefaults.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
        }
        if (!actualDefaults.equals(expectedDefaults)) {
            throw new IllegalStateException(
                    resourceName + " defaults differ: expected=" + expectedDefaults
                            + " actual=" + actualDefaults);
        }

        evidence.put(resourceName + ".class", block.getClass().getName());
        evidence.put(resourceName + ".properties", actualDomains.keySet().toString());
        evidence.put(resourceName + ".defaults", actualDefaults.toString());
    }

    private static void writeResult(Map<String, String> evidence) {
        String configured = System.getProperty(RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "WBY structure registry probe requires system property " + RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalStateException("WBY structure registry result path has no parent: " + path);
        }
        try {
            Files.createDirectories(parent);
            Properties properties = new Properties();
            properties.putAll(evidence);
            try (OutputStream output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                properties.store(output, "Skyforge WBY C1 exact Create structure registry probe");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write WBY structure registry result " + path, exception);
        }
    }
}
