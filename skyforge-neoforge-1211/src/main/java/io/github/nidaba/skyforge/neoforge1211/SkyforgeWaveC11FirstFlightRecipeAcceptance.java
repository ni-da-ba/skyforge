package io.github.nidaba.skyforge.neoforge1211;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * C11 development-only proof that the exact retained flight stack exposes a live
 * pre-Brass/pre-petroleum recipe surface for the essential first-aircraft component family.
 *
 * <p>This is direct recipe-surface evidence only. It does not prove transitive raw-material closure,
 * a successful assembled aircraft, or acceptable aircraft handling.
 */
final class SkyforgeWaveC11FirstFlightRecipeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC11FirstFlightRecipeAcceptance";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC11FirstFlightRecipeAcceptance.class.getName());

    private static final List<ResourceLocation> REQUIRED_OUTPUTS = List.of(
            id("simulated:physics_assembler"),
            id("simulated:engine_assembly"),
            id("simulated:red_portable_engine"),
            id("aeronautics:andesite_propeller"),
            id("simulated:steering_wheel"),
            id("simulated:swivel_bearing"),
            id("simulated:white_symmetric_sail"),
            id("create:mechanical_press"),
            id("create:mechanical_saw"));

    // A direct ingredient is blocked only when every enumerable concrete alternative belongs to an
    // advanced family. Tags containing an early alternative remain admissible.
    private static final Set<String> FORBIDDEN_PATH_TOKENS = Set.of(
            "brass",
            "petroleum",
            "crude_oil",
            "gasoline",
            "diesel",
            "fuel_oil",
            "netherite",
            "levitite",
            "electric_motor",
            "capacitor");

    private SkyforgeWaveC11FirstFlightRecipeAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC11FirstFlightRecipeAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        HolderLookup.Provider registries = event.getServer().registryAccess();
        Map<ResourceLocation, List<RecipeHolder<?>>> byOutput = new LinkedHashMap<>();

        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(registries);
            } catch (RuntimeException failure) {
                continue;
            }
            if (result.isEmpty()) {
                continue;
            }
            ResourceLocation output = BuiltInRegistries.ITEM.getKey(result.getItem());
            byOutput.computeIfAbsent(output, ignored -> new ArrayList<>()).add(holder);
        }

        List<String> accepted = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (ResourceLocation output : REQUIRED_OUTPUTS) {
            if (!BuiltInRegistries.ITEM.containsKey(output)) {
                missing.add(output + " [item not registered]");
                continue;
            }

            List<RecipeHolder<?>> candidates =
                    byOutput.getOrDefault(output, List.of()).stream()
                            .sorted(Comparator.comparing(holder -> holder.id().toString()))
                            .toList();

            RecipeHolder<?> selected = candidates.stream()
                    .filter(SkyforgeWaveC11FirstFlightRecipeAcceptance::isDirectlyBootstrapSafe)
                    .findFirst()
                    .orElse(null);

            if (selected == null) {
                String candidateSummary = candidates.stream()
                        .map(SkyforgeWaveC11FirstFlightRecipeAcceptance::describe)
                        .collect(Collectors.joining(" | "));
                missing.add(output
                        + " [no directly bootstrap-safe recipe; candidates="
                        + candidateSummary
                        + "]");
                continue;
            }

            String evidence = output + " <- " + describe(selected);
            accepted.add(evidence);
            LOGGER.log(System.Logger.Level.INFO, "WAVE_C11_RECIPE " + evidence);
        }

        if (!missing.isEmpty()) {
            fail("missing/blocked essential first-flight recipe surface: "
                    + String.join("; ", missing));
            return;
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C11_ACCEPTANCE PASS essentialRecipes="
                        + accepted.size()
                        + " preBrass=true prePetroleum=true outputs="
                        + REQUIRED_OUTPUTS);
    }

    private static boolean isDirectlyBootstrapSafe(RecipeHolder<?> holder) {
        List<Ingredient> ingredients = effectiveIngredients(holder);
        if (ingredients.isEmpty()) {
            return false;
        }

        for (Ingredient ingredient : ingredients) {
            ItemStack[] variants = ingredient.getItems();
            if (variants.length == 0) {
                // Do not silently declare a non-enumerable custom ingredient bootstrap-safe.
                return false;
            }

            boolean hasEarlyAlternative = false;
            for (ItemStack stack : variants) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!isForbidden(itemId)) {
                    hasEarlyAlternative = true;
                    break;
                }
            }
            if (!hasEarlyAlternative) {
                return false;
            }
        }
        return true;
    }

    private static List<Ingredient> effectiveIngredients(RecipeHolder<?> holder) {
        List<Ingredient> ordinary = holder.value().getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .toList();
        if (!ordinary.isEmpty()) {
            return ordinary;
        }

        // Create's SequencedAssemblyRecipe does not expose its starting ingredient through
        // Recipe#getIngredients(), but its public getIngredient() is the live serialized/JEI input.
        try {
            var method = holder.value().getClass().getMethod("getIngredient");
            Object value = method.invoke(holder.value());
            if (value instanceof Ingredient ingredient && !ingredient.isEmpty()) {
                return List.of(ingredient);
            }
        } catch (ReflectiveOperationException ignored) {
            // Non-Create recipe without generic ingredients: conservatively return no proof.
        }

        return List.of();
    }

    private static boolean isForbidden(ResourceLocation itemId) {
        String path = itemId.getPath();
        return FORBIDDEN_PATH_TOKENS.stream().anyMatch(path::contains);
    }

    private static String describe(RecipeHolder<?> holder) {
        String ingredients = effectiveIngredients(holder).stream()
                .map(SkyforgeWaveC11FirstFlightRecipeAcceptance::describeIngredient)
                .collect(Collectors.joining(","));
        return holder.id() + "[" + ingredients + "]";
    }

    private static String describeIngredient(Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return "<empty>";
        }
        return java.util.Arrays.stream(ingredient.getItems())
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return resourceLocation;
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C11_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException("Wave C11 first-flight recipe acceptance failed: " + reason);
    }
}
