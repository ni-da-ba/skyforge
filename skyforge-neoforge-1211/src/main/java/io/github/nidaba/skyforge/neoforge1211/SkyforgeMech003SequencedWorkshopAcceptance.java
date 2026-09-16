package io.github.nidaba.skyforge.neoforge1211;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** MECH-003: compiled beltless eight-loop Portable Engine CUT/PRESS workshop proof. */
final class SkyforgeMech003SequencedWorkshopAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.mech003SequencedWorkshop";
    private static final String PREFIX = "MECH_003_SEQUENCED_WORKSHOP";
    private static final String ASSET_ID = "guild.utility.portable_engine_workshop.mech_003";
    private static final String REQUIRED_CAPABILITY = "CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE";
    private static final String TARGET_STACK = "C11_FLIGHT_EXACT_2026-09-05";
    private static final String EXPECTED_DIGEST =
            "a34c3135f77a4d5ac1f84751b4bd625747599c96c4b2f8e390e0498a54e962c0";
    private static final String PLAN_RESOURCE =
            "/data/skyforge/mechanisms/mech_003_portable_engine_workshop.json";
    private static final String STRUCTURE_ID = "skyforge:mech_003_portable_engine_workshop";
    private static final String CREATE_SOURCE_AUTHORITY = "ac0c444d9828da3453ae8cc65338e8de063286fb";

    private static final ResourceLocation RECIPE_ID = id("simulated:sequenced_assembly/engine_assembly");
    private static final ResourceLocation INPUT_ID = id("create:iron_sheet");
    private static final ResourceLocation TRANSITION_ID = id("simulated:incomplete_engine_assembly");
    private static final ResourceLocation ENGINE_ASSEMBLY_ID = id("simulated:engine_assembly");
    private static final int EXPECTED_LOOPS = 8;
    private static final int EXPECTED_SEQUENCE_SIZE = 2;
    private static final int EXPECTED_TOTAL_STEPS = EXPECTED_LOOPS * EXPECTED_SEQUENCE_SIZE;
    private static final float EXPECTED_ENGINE_CHANCE = 0.5f;
    private static final float EPSILON = 1.0e-5f;

    private static final Set<ResourceLocation> FORBIDDEN_TRANSPORT = Set.of(
            id("create:belt"), id("create:depot"), id("create:andesite_funnel"), id("create:brass_funnel"),
            id("create:chute"), id("create:smart_chute"), id("create:mechanical_arm"));

    private static final BlockPos BASE = new BlockPos(0, 200, 0);
    private static final ChunkPos FIXTURE_CHUNK = new ChunkPos(BASE);
    private static final int FIXTURE_CHUNK_TICKET_DISTANCE = 3;
    private static final TicketType<ChunkPos> FIXTURE_CHUNK_TICKET = TicketType.create(
            "skyforge_mech_003", Comparator.comparingLong(ChunkPos::toLong));
    private static final long CHUNK_READINESS_DEADLINE_TICKS = 80L;
    private static final long KINETIC_DEADLINE_TICKS = 100L;
    private static final long SAW_DEADLINE_TICKS = 180L;
    // Create 6.0.10 WORLD-mode Press uses a 240-unit cycle; at the accepted 16 RPM fixture
    // its running tick speed is 2, so a restaged item may wait for the previous cycle to retract
    // before the next scan/start plus ~60 ticks to the processing midpoint. Keep this bounded but
    // large enough to cover that real residual-cycle timing without resetting Create internals.
    private static final long PRESS_DEADLINE_TICKS = 260L;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeMech003SequencedWorkshopAcceptance.class.getName());

    private static ServerLevel level;
    private static JsonObject plan;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static boolean complete;
    private static boolean fixtureChunkTicketAdded;

    private static BlockPos cutSourcePos;
    private static BlockPos cutRelayPos;
    private static BlockPos cutStationPos;
    private static BlockPos pressSourcePos;
    private static BlockPos pressRelayPos;
    private static BlockPos pressStationPos;
    private static BlockPos pressWorldSurfacePos;
    private static List<BlockPos> clearancePositions = List.of();

    private static float cutSpeed;
    private static float pressSpeed;
    private static int completedSteps;
    private static int expectedStep;
    private static int handoffCount;
    private static UUID currentItemId;
    private static boolean sawInventoryObserved;
    private static boolean pressGroundedObserved;
    private static Set<ResourceLocation> terminalResultIds = Set.of();
    private static String terminalPoolSummary = "<unresolved>";
    private static float liveEngineChance;

    private enum Stage { CHUNK_READY, KINETIC_READY, SAW_PROCESS, PRESS_PROCESS }

    private SkyforgeMech003SequencedWorkshopAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        NeoForge.EVENT_BUS.addListener(SkyforgeMech003SequencedWorkshopAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeMech003SequencedWorkshopAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START assetId=" + ASSET_ID
                        + " requiredCapability=" + REQUIRED_CAPABILITY
                        + " startTick=" + now + " clientState=headless");
        try {
            plan = loadPlan();
            validatePlanContract(plan);
            requireRuntimePreconditions(plan);
            addFixtureChunkTicket();
            stage = Stage.CHUNK_READY;
            waitDiagnostic = diagnostic(
                    "compiled workshop chunk reaches entity/block-entity ticking readiness",
                    now, now + CHUNK_READINESS_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                    "fixtureChunk=" + FIXTURE_CHUNK + " forceTicks=true");
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION, exception);
        } catch (RuntimeException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic("compiled MECH-003 plan loads before placement", now, now,
                            fixtureIds(), safeServerState(), exception.toString()),
                    "MECH-003 preparation failed: " + exception);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || stage == null || waitDiagnostic == null) return;
        long now = level.getGameTime();
        try {
            switch (stage) {
                case CHUNK_READY -> pollChunkReady(now);
                case KINETIC_READY -> pollKineticReady(now);
                case SAW_PROCESS -> pollSawProcess(now);
                case PRESS_PROCESS -> pollPressProcess(now);
            }
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_RUNTIME_EXIT,
                        finalDiagnostic(exception.toString()),
                        "MECH-003 sequenced workshop failed: " + exception);
            }
        }
    }

    private static void pollChunkReady(long now) {
        if (!level.isPositionEntityTicking(BASE)) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                        waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                                "fixtureChunk=" + FIXTURE_CHUNK + " entitySectionTicking=false"),
                        "MECH-003 fixture chunk did not become ticking before deadline");
            }
            return;
        }
        prepareFixture(plan);
        stage = Stage.KINETIC_READY;
        waitDiagnostic = diagnostic(
                "compiler-emitted Saw and Press stations acquire accepted stationary kinetic power",
                now, now + KINETIC_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                "structureTemplatePlaced=true forbiddenTransportCount=0");
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " FIXTURE_READY structure=" + STRUCTURE_ID + " chunk=" + FIXTURE_CHUNK + " tick=" + now);
    }

    private static void pollKineticReady(long now) throws ReflectiveOperationException {
        Object cut = requireExpectedBlockEntity(cutStationPos, "SawBlockEntity", now);
        Object press = requireExpectedBlockEntity(pressStationPos, "MechanicalPressBlockEntity", now);
        KineticState cutState = kineticState(cut);
        KineticState pressState = kineticState(press);
        if (Math.abs(cutState.speed()) > 0.0f && Math.abs(pressState.speed()) > 0.0f
                && cutState.hasSource() && pressState.hasSource()) {
            cutSpeed = cutState.speed();
            pressSpeed = pressState.speed();
            requireNoForbiddenTransport();
            requireClearance();
            requireLiveProductRecipe();
            ItemEntity input = new ItemEntity(level,
                    cutStationPos.getX() + 0.5,
                    cutStationPos.getY() + 0.90,
                    cutStationPos.getZ() + 0.5,
                    new ItemStack(requireItem(INPUT_ID)));
            input.setDeltaMovement(0.0, -0.08, 0.0);
            input.setDefaultPickUpDelay();
            if (!level.addFreshEntity(input)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK, finalDiagnostic("addFreshEntity=false"),
                        "could not spawn the accepted iron-sheet world input above the compiled Saw");
            }
            completedSteps = 0;
            handoffCount = 0;
            beginSawStep(input, now, false);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                            "cut=" + cutState + " press=" + pressState),
                    "compiled Saw/Press stations did not acquire kinetic power before deadline");
        }
    }

    private static void beginSawStep(ItemEntity input, long now, boolean isHandoff) {
        currentItemId = input.getUUID();
        expectedStep = completedSteps + 1;
        if (expectedStep > EXPECTED_TOTAL_STEPS || expectedStep % 2 != 1) {
            throw new IllegalStateException("invalid CUT step transition: completed=" + completedSteps + " expected=" + expectedStep);
        }
        input.setPos(cutStationPos.getX() + 0.5, cutStationPos.getY() + 0.90, cutStationPos.getZ() + 0.5);
        input.setDeltaMovement(0.0, -0.08, 0.0);
        input.setDefaultPickUpDelay();
        sawInventoryObserved = false;
        if (isHandoff) handoffCount++;
        stage = Stage.SAW_PROCESS;
        waitDiagnostic = diagnostic(
                "world item completes live Portable Engine CUT step " + expectedStep + "/" + EXPECTED_TOTAL_STEPS,
                now, now + SAW_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                "inputUuid=" + currentItemId + " completedSteps=" + completedSteps + " expectedStep=" + expectedStep);
    }

    private static void pollSawProcess(long now) throws ReflectiveOperationException {
        Object saw = requireExpectedBlockEntity(cutStationPos, "SawBlockEntity", now);
        requirePowered(cutStationPos, "SawBlockEntity", now);
        String inventoryDump = sawInventoryDump(saw);
        if (!sawInventoryObserved && !inventoryDump.contains("items=[]")) {
            sawInventoryObserved = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " CUT_ACQUIRED step=" + expectedStep + " inputUuid=" + currentItemId
                            + " " + inventoryDump + " tick=" + now);
        }

        for (ItemEntity entity : itemEntities(sawSearch())) {
            SequenceState sequence = sequenceState(entity.getItem());
            if (!TRANSITION_ID.equals(itemId(entity.getItem())) || sequence == null) continue;
            if (!RECIPE_ID.equals(sequence.id()) || sequence.step() != expectedStep
                    || !progressMatches(sequence.progress(), expectedStep)) continue;

            UUID consumedId = currentItemId;
            Entity original = level.getEntity(consumedId);
            if (original != null && original.isAlive() && !original.getUUID().equals(entity.getUUID())) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                        finalDiagnostic("consumedSawInputStillAlive=" + entityDiagnostic(original)),
                        "Saw emitted the next transition while the prior world input remained alive");
            }
            completedSteps = expectedStep;
            currentItemId = entity.getUUID();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " CUT_PASS step=" + completedSteps + "/" + EXPECTED_TOTAL_STEPS
                            + " inputUuid=" + consumedId + " outputUuid=" + currentItemId
                            + " item=" + itemId(entity.getItem()) + " recipe=" + sequence.id()
                            + " progress=" + sequence.progress() + " tick=" + now);
            beginPressStep(entity, now);
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_FIXTURE_TERMINAL_STATE,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                            "sawInventoryObserved=" + sawInventoryObserved
                                    + " sawInventory=" + inventoryDump
                                    + " current=" + entityDiagnostic(level.getEntity(currentItemId))
                                    + " worldItems=" + itemDump(sawSearch())),
                    "compiled Saw did not produce the expected live Portable Engine transition before deadline");
        }
    }

    private static void beginPressStep(ItemEntity input, long now) {
        expectedStep = completedSteps + 1;
        if (expectedStep > EXPECTED_TOTAL_STEPS || expectedStep % 2 != 0) {
            throw new IllegalStateException("invalid PRESS step transition: completed=" + completedSteps + " expected=" + expectedStep);
        }
        currentItemId = input.getUUID();
        input.setPos(pressWorldSurfacePos.getX() + 0.5,
                pressWorldSurfacePos.getY() + 1.15,
                pressWorldSurfacePos.getZ() + 0.5);
        input.setDeltaMovement(Vec3.ZERO);
        input.setDefaultPickUpDelay();
        pressGroundedObserved = false;
        handoffCount++;
        stage = Stage.PRESS_PROCESS;
        waitDiagnostic = diagnostic(
                "same logical world item completes live Portable Engine PRESS step " + expectedStep + "/" + EXPECTED_TOTAL_STEPS,
                now, now + PRESS_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                "inputUuid=" + currentItemId + " completedSteps=" + completedSteps + " expectedStep=" + expectedStep);
    }

    private static void pollPressProcess(long now) throws ReflectiveOperationException {
        requirePowered(pressStationPos, "MechanicalPressBlockEntity", now);
        Entity resolved = level.getEntity(currentItemId);
        if (resolved instanceof ItemEntity itemEntity && itemEntity.isAlive()) {
            SequenceState sequence = sequenceState(itemEntity.getItem());
            ResourceLocation item = itemId(itemEntity.getItem());
            if (TRANSITION_ID.equals(item) && sequence != null && RECIPE_ID.equals(sequence.id())
                    && sequence.step() == completedSteps && itemEntity.onGround()) {
                pressGroundedObserved = true;
            }

            if (expectedStep < EXPECTED_TOTAL_STEPS
                    && TRANSITION_ID.equals(item) && sequence != null
                    && RECIPE_ID.equals(sequence.id()) && sequence.step() == expectedStep
                    && progressMatches(sequence.progress(), expectedStep)) {
                if (!pressGroundedObserved) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                            finalDiagnostic("pressOutputBeforeGroundedObservation=true step=" + expectedStep),
                            "Press advanced the world item without observing its grounded WORLD-mode acquisition precondition");
                }
                completedSteps = expectedStep;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " PRESS_PASS step=" + completedSteps + "/" + EXPECTED_TOTAL_STEPS
                                + " uuid=" + currentItemId + " item=" + item
                                + " recipe=" + sequence.id() + " progress=" + sequence.progress()
                                + " groundedObserved=true tick=" + now);
                beginSawStep(itemEntity, now, true);
                return;
            }

            if (expectedStep == EXPECTED_TOTAL_STEPS && sequence == null && terminalResultIds.contains(item)) {
                if (!pressGroundedObserved) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                            finalDiagnostic("terminalOutputBeforeGroundedObservation=true output=" + item),
                            "terminal weighted result appeared without observing grounded Press acquisition");
                }
                completedSteps = expectedStep;
                requireNoForbiddenTransport();
                requireClearance();
                removeFixtureChunkTicket();
                complete = true;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " PASS assetId=" + ASSET_ID
                                + " digest=" + EXPECTED_DIGEST
                                + " requiredCapability=" + REQUIRED_CAPABILITY
                                + " recipe=" + RECIPE_ID
                                + " createSourceAuthority=" + CREATE_SOURCE_AUTHORITY
                                + " loops=" + EXPECTED_LOOPS + " sequenceSize=" + EXPECTED_SEQUENCE_SIZE
                                + " completedSteps=" + completedSteps
                                + " cutSpeed=" + cutSpeed + " pressSpeed=" + pressSpeed
                                + " handoffCount=" + handoffCount
                                + " terminalOutput=" + item
                                + " terminalResultPool=" + terminalPoolSummary
                                + " engineAssemblyChance=" + liveEngineChance
                                + " singleAttemptEngineSuccessRequired=false"
                                + " terminalSequencedComponentCleared=true resultBelongsToLivePool=true"
                                + " belts=false depots=false funnels=false chutes=false arms=false"
                                + " clientState=headless");
                return;
            }
        }

        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_FIXTURE_TERMINAL_STATE,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                            "pressGroundedObserved=" + pressGroundedObserved
                                    + " current=" + entityDiagnostic(resolved)
                                    + " pressBehaviour=" + pressBehaviourDump(requireExpectedBlockEntity(pressStationPos, "MechanicalPressBlockEntity", now))
                                    + " worldItems=" + itemDump(pressSearch())),
                    "compiled Press did not produce the expected live Portable Engine transition/result before deadline");
        }
    }

    private static void requireLiveProductRecipe() throws ReflectiveOperationException {
        Optional<? extends RecipeHolder<?>> holder = level.getRecipeManager().byKey(RECIPE_ID);
        if (holder.isEmpty()) throw new IllegalStateException("missing retained Portable Engine recipe " + RECIPE_ID);
        Object recipe = holder.get().value();
        String expectedRecipeClass = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe";
        if (!expectedRecipeClass.equals(recipe.getClass().getName())) {
            throw new IllegalStateException("wrong retained recipe class: " + recipe.getClass().getName());
        }

        int loops = ((Number) recipe.getClass().getMethod("getLoops").invoke(recipe)).intValue();
        Object rawSequence = recipe.getClass().getMethod("getSequence").invoke(recipe);
        if (loops != EXPECTED_LOOPS || !(rawSequence instanceof List<?> sequence)
                || sequence.size() != EXPECTED_SEQUENCE_SIZE) {
            throw new IllegalStateException("retained Portable Engine sequence drift: loops=" + loops + " sequence=" + rawSequence);
        }
        Object cutRecipe = sequence.get(0).getClass().getMethod("getRecipe").invoke(sequence.get(0));
        Object pressRecipe = sequence.get(1).getClass().getMethod("getRecipe").invoke(sequence.get(1));
        if (!cutRecipe.getClass().getName().endsWith("CuttingRecipe")
                || !pressRecipe.getClass().getName().endsWith("PressingRecipe")) {
            throw new IllegalStateException("retained Portable Engine sequence is no longer CUT -> PRESS: "
                    + cutRecipe.getClass().getName() + " -> " + pressRecipe.getClass().getName());
        }

        Object rawIngredient = recipe.getClass().getMethod("getIngredient").invoke(recipe);
        if (!(rawIngredient instanceof Ingredient ingredient)
                || !ingredient.test(new ItemStack(requireItem(INPUT_ID)))) {
            throw new IllegalStateException("retained Portable Engine recipe no longer accepts " + INPUT_ID);
        }
        Object rawTransition = recipe.getClass().getMethod("getTransitionalItem").invoke(recipe);
        if (!(rawTransition instanceof ItemStack transitional) || !TRANSITION_ID.equals(itemId(transitional))) {
            throw new IllegalStateException("retained transitional item drift: " + rawTransition);
        }

        Field resultPoolField = recipe.getClass().getField("resultPool");
        Object rawPool = resultPoolField.get(recipe);
        if (!(rawPool instanceof List<?> pool) || pool.isEmpty()) {
            throw new IllegalStateException("retained Portable Engine result pool is empty/unavailable");
        }
        Set<ResourceLocation> resultIds = new HashSet<>();
        List<String> summary = new ArrayList<>();
        float totalWeight = 0.0f;
        float engineWeight = 0.0f;
        for (Object output : pool) {
            Method getStack = output.getClass().getMethod("getStack");
            Method getChance = output.getClass().getMethod("getChance");
            Object rawStack = getStack.invoke(output);
            if (!(rawStack instanceof ItemStack stack) || stack.isEmpty()) {
                throw new IllegalStateException("retained result pool contains empty/non-item output");
            }
            float weight = ((Number) getChance.invoke(output)).floatValue();
            if (!(weight > 0.0f)) throw new IllegalStateException("retained result pool contains nonpositive weight");
            ResourceLocation id = itemId(stack);
            resultIds.add(id);
            totalWeight += weight;
            if (ENGINE_ASSEMBLY_ID.equals(id)) engineWeight += weight;
            summary.add(id + "@" + weight);
        }
        float chance = ((Number) recipe.getClass().getMethod("getOutputChance").invoke(recipe)).floatValue();
        float derivedChance = engineWeight / totalWeight;
        if (!resultIds.contains(ENGINE_ASSEMBLY_ID)
                || Math.abs(chance - derivedChance) > EPSILON
                || Math.abs(chance - EXPECTED_ENGINE_CHANCE) > EPSILON) {
            throw new IllegalStateException("retained terminal weighting drift: outputChance=" + chance
                    + " derivedEngineChance=" + derivedChance + " pool=" + summary);
        }
        terminalResultIds = Set.copyOf(resultIds);
        terminalPoolSummary = summary.toString();
        liveEngineChance = chance;
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " LIVE_RECIPE recipe=" + RECIPE_ID + " loops=" + loops
                        + " sequence=CUT->PRESS totalSteps=" + EXPECTED_TOTAL_STEPS
                        + " transition=" + TRANSITION_ID + " resultPool=" + terminalPoolSummary
                        + " engineAssemblyChance=" + liveEngineChance);
    }

    private static JsonObject loadPlan() {
        try (InputStream stream = SkyforgeMech003SequencedWorkshopAcceptance.class.getResourceAsStream(PLAN_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("compiled mechanism plan is missing: " + PLAN_RESOURCE);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) throw new IllegalStateException("compiled MECH-003 plan root is not an object");
                return element.getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load compiled MECH-003 plan", exception);
        }
    }

    private static void validatePlanContract(JsonObject loaded) {
        requireString(loaded, "schema", "skyforge.functional-mechanism-plan.v1");
        requireString(loaded, "assetId", ASSET_ID);
        requireString(loaded, "compilerVersion", "mech-0.3-fixed-world");
        requireString(loaded, "targetStackAuthority", TARGET_STACK);
        requireString(loaded, "requiredPlatformCapability", REQUIRED_CAPABILITY);
        requireString(loaded, "sourcePolicy", "qualification_power_not_gameplay_canon");
        requireString(loaded, "digestSha256", EXPECTED_DIGEST);

        JsonObject evidence = loaded.getAsJsonObject("platformEvidence");
        if (evidence == null
                || !"accepted".equals(evidence.get("status").getAsString())
                || !"L2".equals(evidence.get("verificationLevel").getAsString())
                || !"PASS".equals(evidence.get("result").getAsString())
                || !evidence.get("agentCAuthorized").getAsBoolean()) {
            throw new IllegalStateException("compiled MECH-003 plan lacks accepted Agent C processing authority");
        }
        JsonObject processing = loaded.getAsJsonObject("processingEnvelope");
        JsonArray stations = processing == null ? null : processing.getAsJsonArray("stations");
        if (stations == null || stations.size() != 2) throw new IllegalStateException("MECH-003 requires CUT and PRESS stations");
        requireString(stations.get(0).getAsJsonObject(), "role", "cut");
        requireString(stations.get(1).getAsJsonObject(), "role", "press");
        requireString(processing.getAsJsonObject("manualHandoff"), "mode", "world_item_restage");
        JsonObject product = loaded.getAsJsonObject("productProcessingContract");
        requireString(product, "recipeAuthority", "live_exact_stack_recipe_manager");
        requireString(product, "loopCountAuthority", "live_recipe");
        requireString(product, "terminalResultPolicy", "live_weighted_result_pool");
        if (product.get("singleAttemptSuccessRequired").getAsBoolean()) {
            throw new IllegalStateException("MECH-003 must not require one random Engine Assembly success");
        }
    }

    private static void requireRuntimePreconditions(JsonObject loaded) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("create") || !ModList.get().isLoaded("simulated")) {
            throw new IllegalStateException("required exact-stack Create/Simulated mods are not loaded");
        }
        String createVersion = ModList.get().getModContainerById("create")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("missing");
        if (!createVersion.startsWith("6.0.10")) {
            throw new IllegalStateException("expected Create 6.0.10 exact stack, got " + createVersion);
        }
        for (JsonElement element : loaded.getAsJsonArray("placements")) {
            requireBlock(ResourceLocation.parse(element.getAsJsonObject().getAsJsonObject("blockState").get("name").getAsString()));
        }
        requireItem(INPUT_ID); requireItem(TRANSITION_ID); requireItem(ENGINE_ASSEMBLY_ID);
        Class.forName("com.simibubi.create.content.kinetics.saw.SawBlockEntity");
        Class.forName("com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity");
        Class.forName("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe");
        Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        Class.forName("com.simibubi.create.AllDataComponents");
    }

    private static void prepareFixture(JsonObject loaded) {
        JsonObject envelope = loaded.getAsJsonObject("envelope");
        int[] min = intTriple(envelope.getAsJsonArray("min"));
        int[] max = intTriple(envelope.getAsJsonArray("max"));
        level.getChunk(FIXTURE_CHUNK.x, FIXTURE_CHUNK.z);
        for (int x = min[0] - 1; x <= max[0] + 1; x++) {
            for (int y = min[1] - 1; y <= max[1] + 1; y++) {
                for (int z = min[2] - 1; z <= max[2] + 1; z++) {
                    level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        for (JsonElement element : loaded.getAsJsonArray("placements")) {
            JsonObject placement = element.getAsJsonObject();
            BlockPos worldPos = BASE.offset(localPos(placement));
            String placementId = placement.get("id").getAsString();
            if ("cut_power_source".equals(placementId)) cutSourcePos = worldPos;
            else if ("cut_power_relay".equals(placementId)) cutRelayPos = worldPos;
            else if ("cut_station".equals(placementId)) cutStationPos = worldPos;
            else if ("press_power_source".equals(placementId)) pressSourcePos = worldPos;
            else if ("press_power_relay".equals(placementId)) pressRelayPos = worldPos;
            else if ("press_station".equals(placementId)) pressStationPos = worldPos;
            else if ("press_world_surface".equals(placementId)) pressWorldSurfacePos = worldPos;
        }
        if (cutSourcePos == null || cutRelayPos == null || cutStationPos == null
                || pressSourcePos == null || pressRelayPos == null || pressStationPos == null
                || pressWorldSurfacePos == null) {
            throw new IllegalStateException("compiled MECH-003 plan is missing required station/power placements");
        }

        String placeCommand = "place template " + STRUCTURE_ID
                + " " + BASE.getX() + " " + BASE.getY() + " " + BASE.getZ();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), placeCommand);

        for (JsonElement element : loaded.getAsJsonArray("placements")) {
            JsonObject placement = element.getAsJsonObject();
            BlockPos worldPos = BASE.offset(localPos(placement));
            BlockState expected = blockState(placement.getAsJsonObject("blockState"));
            BlockState actual = level.getBlockState(worldPos);
            if (!actual.equals(expected)) {
                throw new IllegalStateException("structure-template placement mismatch for "
                        + placement.get("id").getAsString() + " at " + worldPos
                        + " expected=" + expected + " actual=" + actual);
            }
        }
        for (JsonElement element : loaded.getAsJsonArray("supportRequirements")) {
            BlockPos support = BASE.offset(toBlockPos(element.getAsJsonObject().getAsJsonArray("supportBelow")));
            if (level.getBlockState(support).isAir()) throw new IllegalStateException("compiled support requirement is empty at " + support);
        }
        List<BlockPos> resolved = new ArrayList<>();
        for (JsonElement element : loaded.getAsJsonArray("clearanceCells")) {
            resolved.add(BASE.offset(toBlockPos(element.getAsJsonArray())));
        }
        clearancePositions = List.copyOf(resolved);
        requireClearance();
        requireNoForbiddenTransport();
    }

    private static void addFixtureChunkTicket() {
        if (fixtureChunkTicketAdded) return;
        level.getChunkSource().addRegionTicket(
                FIXTURE_CHUNK_TICKET, FIXTURE_CHUNK, FIXTURE_CHUNK_TICKET_DISTANCE, FIXTURE_CHUNK, true);
        level.getChunk(FIXTURE_CHUNK.x, FIXTURE_CHUNK.z);
        fixtureChunkTicketAdded = true;
    }

    private static void removeFixtureChunkTicket() {
        if (!fixtureChunkTicketAdded || level == null) return;
        level.getChunkSource().removeRegionTicket(
                FIXTURE_CHUNK_TICKET, FIXTURE_CHUNK, FIXTURE_CHUNK_TICKET_DISTANCE, FIXTURE_CHUNK, true);
        fixtureChunkTicketAdded = false;
    }

    private static void requireNoForbiddenTransport() {
        JsonObject envelope = plan.getAsJsonObject("envelope");
        int[] min = intTriple(envelope.getAsJsonArray("min"));
        int[] max = intTriple(envelope.getAsJsonArray("max"));
        for (int x = min[0]; x <= max[0]; x++) {
            for (int y = min[1]; y <= max[1]; y++) {
                for (int z = min[2]; z <= max[2]; z++) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(BASE.offset(x, y, z)).getBlock());
                    if (FORBIDDEN_TRANSPORT.contains(blockId)) {
                        throw new IllegalStateException("forbidden automated transport present in compiled workshop: " + blockId);
                    }
                }
            }
        }
    }

    private static void requireClearance() {
        for (BlockPos pos : clearancePositions) {
            if (!level.getBlockState(pos).isAir()) {
                throw new IllegalStateException("compiled operator/staging clearance is obstructed at " + pos
                        + " state=" + level.getBlockState(pos));
            }
        }
    }

    private static Object requireExpectedBlockEntity(BlockPos pos, String suffix, long now) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().endsWith(suffix)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("pos=" + pos + " blockEntity="
                            + (blockEntity == null ? "null" : blockEntity.getClass().getName()) + " tick=" + now),
                    "required MECH-003 block entity missing/wrong at " + pos);
        }
        return blockEntity;
    }

    private static void requirePowered(BlockPos pos, String suffix, long now) throws ReflectiveOperationException {
        KineticState state = kineticState(requireExpectedBlockEntity(pos, suffix, now));
        if (Math.abs(state.speed()) <= 0.0f || !state.hasSource()) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic("pos=" + pos + " kinetic=" + state),
                    "processing station lost required kinetic authority");
        }
    }

    private static String pressBehaviourDump(Object press) throws ReflectiveOperationException {
        Object behaviour = press.getClass().getField("pressingBehaviour").get(press);
        if (behaviour == null) return "null";
        Class<?> type = behaviour.getClass();
        boolean running = type.getField("running").getBoolean(behaviour);
        int runningTicks = type.getField("runningTicks").getInt(behaviour);
        boolean finished = type.getField("finished").getBoolean(behaviour);
        Object mode = type.getField("mode").get(behaviour);
        return "running=" + running + " runningTicks=" + runningTicks
                + " finished=" + finished + " mode=" + mode;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        Class<?> kinetic = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kinetic.isInstance(blockEntity)) throw new IllegalStateException("not KineticBlockEntity: " + blockEntity.getClass());
        return new KineticState(
                ((Number) kinetic.getMethod("getSpeed").invoke(blockEntity)).floatValue(),
                ((Number) kinetic.getMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue(),
                (Boolean) kinetic.getMethod("hasSource").invoke(blockEntity),
                (Boolean) kinetic.getMethod("hasNetwork").invoke(blockEntity));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SequenceState sequenceState(ItemStack stack) throws ReflectiveOperationException {
        Object rawType = Class.forName("com.simibubi.create.AllDataComponents").getField("SEQUENCED_ASSEMBLY").get(null);
        if (!(rawType instanceof DataComponentType type)) throw new IllegalStateException("Create SEQUENCED_ASSEMBLY component unavailable");
        Object value = stack.get(type);
        if (value == null) return null;
        ResourceLocation id = (ResourceLocation) value.getClass().getMethod("id").invoke(value);
        int step = ((Number) value.getClass().getMethod("step").invoke(value)).intValue();
        float progress = ((Number) value.getClass().getMethod("progress").invoke(value)).floatValue();
        return new SequenceState(id, step, progress);
    }

    private static boolean progressMatches(float actual, int step) {
        return Math.abs(actual - ((float) step / EXPECTED_TOTAL_STEPS)) <= EPSILON;
    }

    private static String sawInventoryDump(Object saw) throws ReflectiveOperationException {
        Object inventory = saw.getClass().getField("inventory").get(saw);
        Method getSlots = inventory.getClass().getMethod("getSlots");
        Method getStack = inventory.getClass().getMethod("getStackInSlot", int.class);
        int slots = ((Number) getSlots.invoke(inventory)).intValue();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            Object raw = getStack.invoke(inventory, i);
            if (raw instanceof ItemStack stack && !stack.isEmpty()) {
                items.add(i + ":" + itemId(stack) + "x" + stack.getCount() + ":seq=" + sequenceState(stack));
            }
        }
        float remaining = ((Number) inventory.getClass().getField("remainingTime").get(inventory)).floatValue();
        float duration = ((Number) inventory.getClass().getField("recipeDuration").get(inventory)).floatValue();
        boolean applied = inventory.getClass().getField("appliedRecipe").getBoolean(inventory);
        return "items=" + items + " remainingTime=" + remaining + " recipeDuration=" + duration + " appliedRecipe=" + applied;
    }

    private static AABB sawSearch() { return new AABB(cutStationPos).inflate(3.0, 4.0, 3.0); }
    private static AABB pressSearch() { return new AABB(pressWorldSurfacePos).inflate(3.0, 3.0, 3.0); }
    private static List<ItemEntity> itemEntities(AABB box) { return level.getEntitiesOfClass(ItemEntity.class, box); }

    private static String itemDump(AABB box) {
        try {
            List<String> items = new ArrayList<>();
            for (ItemEntity entity : itemEntities(box)) {
                items.add(entity.getUUID() + ":" + itemId(entity.getItem()) + ":ground=" + entity.onGround()
                        + ":seq=" + sequenceState(entity.getItem()));
            }
            return items.toString();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "<item-dump-failed:" + exception + ">";
        }
    }

    private static String entityDiagnostic(Entity entity) {
        if (entity == null) return "null";
        return "uuid=" + entity.getUUID() + " alive=" + entity.isAlive() + " pos=" + entity.position();
    }

    private static ResourceLocation itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()); }

    private static Item requireItem(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("required exact-stack item missing: " + id);
        return BuiltInRegistries.ITEM.get(id);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("required exact-stack block missing: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState blockState(JsonObject stateObject) {
        BlockState state = requireBlock(ResourceLocation.parse(stateObject.get("name").getAsString())).defaultBlockState();
        JsonObject properties = stateObject.getAsJsonObject("properties");
        if (properties == null) return state;
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            state = withProperty(state, entry.getKey(), entry.getValue().getAsString());
        }
        return state;
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return withParsedProperty(state, property, name, value);
        }
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static BlockPos localPos(JsonObject placement) { return toBlockPos(placement.getAsJsonArray("pos")); }
    private static BlockPos toBlockPos(JsonArray array) {
        int[] values = intTriple(array);
        return new BlockPos(values[0], values[1], values[2]);
    }
    private static int[] intTriple(JsonArray array) {
        if (array == null || array.size() != 3) throw new IllegalStateException("expected three-integer coordinate");
        return new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
    }

    private static void requireString(JsonObject object, String key, String expected) {
        if (object == null || !object.has(key) || !expected.equals(object.get(key).getAsString())) {
            throw new IllegalStateException("compiled MECH-003 " + key + " must equal " + expected);
        }
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            String expected, long start, long deadline, String ids, String server, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                ASSET_ID, SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                expected, start, deadline, ids, server, "headless", dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        long now = level == null ? 0L : level.getGameTime();
        return waitDiagnostic == null
                ? diagnostic("MECH-003 reaches terminal state", now, now, fixtureIds(), safeServerState(), dump)
                : waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }

    private static String fixtureIds() {
        return "assetId=" + ASSET_ID + " cutSource=" + cutSourcePos + " cutRelay=" + cutRelayPos
                + " cut=" + cutStationPos + " pressSource=" + pressSourcePos + " pressRelay=" + pressRelayPos
                + " press=" + pressStationPos + " pressSurface=" + pressWorldSurfacePos
                + " itemUuid=" + currentItemId + " digest=" + EXPECTED_DIGEST;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        return "gameTime=" + level.getGameTime() + " stage=" + stage
                + " completedSteps=" + completedSteps + " expectedStep=" + expectedStep
                + " cut=" + stateAt(cutStationPos) + " press=" + stateAt(pressStationPos)
                + " current=" + entityDiagnostic(currentItemId == null ? null : level.getEntity(currentItemId));
    }

    private static String stateAt(BlockPos pos) { return pos == null ? "null" : level.getBlockState(pos).toString(); }

    private static void failReflection(SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getTargetException() : exception;
        fail(code, finalDiagnostic("reflectionFailure=" + cause), "runtime reflection failed: " + cause);
    }

    private static void fail(SkyforgeCompilerIntegrationFailure code, SkyforgeCompilerIntegrationDiagnostic diagnostic, String reason) {
        if (!complete) {
            removeFixtureChunkTicket();
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(ASSET_ID + " failed: " + code + ": " + reason);
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private record KineticState(float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork) {}
    private record SequenceState(ResourceLocation id, int step, float progress) {}
}
