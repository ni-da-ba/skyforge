package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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

/** PLATFORM-015: beltless world-item CUT -> PRESS sequenced-processing lifecycle. */
final class SkyforgeWorldItemCutPressLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformWorldItemCutPressLifecycle";
    static final String CAPABILITY = "CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE";
    private static final String PREFIX = "COMPILER_PLATFORM_WORLD_ITEM_CUT_PRESS_LIFECYCLE";
    private static final String CREATE_SOURCE_AUTHORITY = "ac0c444d9828da3453ae8cc65338e8de063286fb";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWorldItemCutPressLifecycleAcceptance.class.getName());

    private static final ResourceLocation MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation SAW_ID = id("create:mechanical_saw");
    private static final ResourceLocation PRESS_ID = id("create:mechanical_press");
    private static final ResourceLocation RECIPE_ID = id("skyforge:sequenced_assembly/platform_015_cut_press");
    private static final ResourceLocation INPUT_ID = id("minecraft:iron_ingot");
    private static final ResourceLocation TRANSITION_ID = id("minecraft:iron_nugget");
    private static final ResourceLocation OUTPUT_ID = id("minecraft:gold_nugget");

    private static final BlockPos SAW_MOTOR = new BlockPos(1, 200, 0);
    private static final BlockPos SAW = new BlockPos(2, 200, 0);
    private static final BlockPos PRESS_MOTOR = new BlockPos(5, 202, 0);
    private static final BlockPos PRESS = new BlockPos(6, 202, 0);
    private static final BlockPos PRESS_FLOOR = new BlockPos(6, 200, 0);
    private static final long KINETIC_DEADLINE_TICKS = 80L;
    private static final long SAW_DEADLINE_TICKS = 180L;
    private static final long PRESS_DEADLINE_TICKS = 120L;

    private static final Set<ResourceLocation> FORBIDDEN_TRANSPORT = Set.of(
            id("create:belt"), id("create:depot"), id("create:andesite_funnel"), id("create:brass_funnel"),
            id("create:chute"), id("create:smart_chute"), id("create:mechanical_arm"));

    private static ServerLevel level;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static boolean complete;
    private static UUID initialInputId;
    private static UUID transitionalId;
    private static boolean pressGroundedObserved;
    private static float sawSpeed;
    private static float pressSpeed;

    private enum Stage { KINETIC_READY, SAW_PROCESS, PRESS_PROCESS }

    private SkyforgeWorldItemCutPressLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        NeoForge.EVENT_BUS.addListener(SkyforgeWorldItemCutPressLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWorldItemCutPressLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            prepareFixture();
            stage = Stage.KINETIC_READY;
            waitDiagnostic = diagnostic(
                    "powered upward Saw and WORLD-mode Mechanical Press acquire nonzero Create kinetic speed",
                    now, now + KINETIC_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                    "recipe=" + RECIPE_ID + " forbiddenTransportCount=0");
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION, exception);
        } catch (RuntimeException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic("minimal beltless CUT/PRESS fixture is placed", now, now,
                            fixtureIds(), safeServerState(), exception.toString()),
                    "fixture preparation failed: " + exception);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || stage == null || waitDiagnostic == null) return;
        long now = level.getGameTime();
        try {
            switch (stage) {
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
                        "world-item CUT/PRESS lifecycle failed: " + exception);
            }
        }
    }

    private static void pollKineticReady(long now) throws ReflectiveOperationException {
        Object sawMotor = requireExpectedBlockEntity(SAW_MOTOR, "CreativeMotorBlockEntity", now);
        Object saw = requireExpectedBlockEntity(SAW, "SawBlockEntity", now);
        Object pressMotor = requireExpectedBlockEntity(PRESS_MOTOR, "CreativeMotorBlockEntity", now);
        Object press = requireExpectedBlockEntity(PRESS, "MechanicalPressBlockEntity", now);
        KineticState sawMotorState = kineticState(sawMotor);
        KineticState sawState = kineticState(saw);
        KineticState pressMotorState = kineticState(pressMotor);
        KineticState pressState = kineticState(press);
        if (Math.abs(sawState.speed()) > 0.0f && Math.abs(pressState.speed()) > 0.0f
                && sawState.hasSource() && pressState.hasSource()) {
            sawSpeed = sawState.speed();
            pressSpeed = pressState.speed();
            requireNoForbiddenTransport();
            requireGenericRecipe();
            ItemEntity input = new ItemEntity(level, SAW.getX() + 0.5, SAW.getY() + 2.25, SAW.getZ() + 0.5,
                    new ItemStack(requireItem(INPUT_ID)));
            input.setDefaultPickUpDelay();
            if (!level.addFreshEntity(input)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK, finalDiagnostic("addFreshEntity=false"),
                        "could not spawn ordinary world input ItemEntity above the Saw");
            }
            initialInputId = input.getUUID();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " SAW_WORLD_INPUT uuid=" + initialInputId + " item=" + INPUT_ID
                            + " sawSpeed=" + sawSpeed + " pressSpeed=" + pressSpeed + " tick=" + now);
            stage = Stage.SAW_PROCESS;
            waitDiagnostic = diagnostic(
                    "ordinary falling ItemEntity is accepted by the upward powered Saw and ejected as step-1 sequenced transitional world item",
                    now, now + SAW_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                    "inputUuid=" + initialInputId + " input=" + INPUT_ID + " expectedTransition=" + TRANSITION_ID);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                            "sawMotor=" + sawMotorState + " saw=" + sawState + " pressMotor=" + pressMotorState + " press=" + pressState),
                    "Saw/Press fixture did not reach powered kinetic state before deadline");
        }
    }

    private static void pollSawProcess(long now) throws ReflectiveOperationException {
        requirePowered(SAW, "SawBlockEntity", now);
        for (ItemEntity entity : itemEntities(SAW_SEARCH)) {
            SequenceState sequence = sequenceState(entity.getItem());
            ResourceLocation itemId = itemId(entity.getItem());
            if (!TRANSITION_ID.equals(itemId) || sequence == null) continue;
            if (!RECIPE_ID.equals(sequence.id()) || sequence.step() != 1 || Math.abs(sequence.progress() - 0.5f) > 1.0e-5f) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                        waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                                "transitionUuid=" + entity.getUUID() + " transition=" + itemId + " sequence=" + sequence),
                        "Saw produced a transitional item with wrong sequenced identity/progress");
            }
            transitionalId = entity.getUUID();
            Entity original = level.getEntity(initialInputId);
            if (original != null && original.isAlive()) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                        finalDiagnostic("initialInputStillAlive=true inputUuid=" + initialInputId),
                        "Saw world-item path did not consume the original input entity");
            }
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " SAW_CUT_PASS inputUuid=" + initialInputId + " transitionUuid=" + transitionalId
                            + " item=" + itemId + " recipe=" + sequence.id() + " step=" + sequence.step()
                            + " progress=" + sequence.progress() + " tick=" + now);

            // Explicit manual/world-item staging: preserve this exact transitional ItemEntity UUID and stack,
            // move it to the Press world-acquisition surface, and let normal gravity establish onGround.
            entity.setPos(PRESS.getX() + 0.5, PRESS_FLOOR.getY() + 1.15, PRESS.getZ() + 0.5);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setDefaultPickUpDelay();
            stage = Stage.PRESS_PROCESS;
            waitDiagnostic = diagnostic(
                    "same transitional world ItemEntity becomes grounded below the powered Press and advances through WORLD-mode PRESSING",
                    now, now + PRESS_DEADLINE_TICKS, fixtureIds(), safeServerState(),
                    "transitionUuid=" + transitionalId + " recipe=" + sequence.id() + " step=1 progress=0.5");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_FIXTURE_TERMINAL_STATE,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", itemDump(SAW_SEARCH)),
                    "Saw did not expose the expected sequenced transitional world item before deadline");
        }
    }

    private static void pollPressProcess(long now) throws ReflectiveOperationException {
        requirePowered(PRESS, "MechanicalPressBlockEntity", now);
        Entity resolved = level.getEntity(transitionalId);
        if (resolved instanceof ItemEntity itemEntity && itemEntity.isAlive()) {
            SequenceState sequence = sequenceState(itemEntity.getItem());
            ResourceLocation itemId = itemId(itemEntity.getItem());
            if (TRANSITION_ID.equals(itemId) && sequence != null && RECIPE_ID.equals(sequence.id())
                    && sequence.step() == 1 && itemEntity.onGround()) {
                pressGroundedObserved = true;
            }
            if (OUTPUT_ID.equals(itemId) && sequence == null) {
                if (!pressGroundedObserved) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                            finalDiagnostic("terminalOutputBeforeGroundedObservation=true transitionUuid=" + transitionalId),
                            "Press output appeared without observing the world-item grounded acquisition precondition");
                }
                requireNoForbiddenTransport();
                complete = true;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " PASS capability=" + CAPABILITY
                                + " recipe=" + RECIPE_ID
                                + " createSourceAuthority=" + CREATE_SOURCE_AUTHORITY
                                + " inputUuid=" + initialInputId
                                + " transitionAndOutputUuid=" + transitionalId
                                + " sawSpeed=" + sawSpeed + " pressSpeed=" + pressSpeed
                                + " cutStep=1 cutProgress=0.5"
                                + " pressWorldModeGroundedObserved=true"
                                + " terminalOutput=" + OUTPUT_ID + " terminalSequencedComponentCleared=true"
                                + " explicitWorldItemHandoff=true sameTransitionEntityUuid=true"
                                + " belts=false depots=false funnels=false chutes=false arms=false"
                                + " productRecipeQualified=false stochasticOutputQualified=false"
                                + " clientState=headless");
                return;
            }
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_FIXTURE_TERMINAL_STATE,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", itemDump(PRESS_SEARCH)),
                    "Press WORLD-mode processing did not produce the deterministic terminal output before deadline");
        }
    }

    private static final AABB SAW_SEARCH = new AABB(0, 199, -2, 5, 205, 3);
    private static final AABB PRESS_SEARCH = new AABB(4, 199, -2, 9, 205, 3);

    private static void requireRuntimePreconditions() throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("create")) {
            throw new IllegalStateException("Create mod not loaded");
        }
        String createVersion = ModList.get().getModContainerById("create")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("missing");
        if (!createVersion.startsWith("6.0.10")) {
            throw new IllegalStateException("expected Create 6.0.10 exact stack, got " + createVersion);
        }
        requireBlock(MOTOR_ID); requireBlock(SAW_ID); requireBlock(PRESS_ID);
        requireItem(INPUT_ID); requireItem(TRANSITION_ID); requireItem(OUTPUT_ID);
        Class.forName("com.simibubi.create.content.kinetics.saw.SawBlock");
        Class.forName("com.simibubi.create.content.kinetics.saw.SawBlockEntity");
        Class.forName("com.simibubi.create.content.kinetics.press.PressingBehaviour");
        Class.forName("com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity");
        Class.forName("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe");
        Class.forName("com.simibubi.create.AllDataComponents");
    }

    private static void requireGenericRecipe() throws ReflectiveOperationException {
        Optional<? extends RecipeHolder<?>> holder = level.getRecipeManager().byKey(RECIPE_ID);
        if (holder.isEmpty()) throw new IllegalStateException("missing development sequenced recipe " + RECIPE_ID);
        Object recipe = holder.get().value();
        if (!recipe.getClass().getName().equals("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe")) {
            throw new IllegalStateException("wrong recipe class for " + RECIPE_ID + ": " + recipe.getClass().getName());
        }
        int loops = ((Number) recipe.getClass().getMethod("getLoops").invoke(recipe)).intValue();
        Object sequence = recipe.getClass().getMethod("getSequence").invoke(recipe);
        if (loops != 1 || !(sequence instanceof List<?> steps) || steps.size() != 2) {
            throw new IllegalStateException("development recipe must be exactly one CUT/PRESS loop; loops=" + loops + " sequence=" + sequence);
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = 0; x <= 9; x++) for (int y = 198; y <= 205; y++) for (int z = 0; z <= 3; z++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);

        BlockState sawMotor = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "east");
        BlockState saw = withProperty(withProperty(withProperty(requireBlock(SAW_ID).defaultBlockState(),
                "facing", "up"), "axis_along_first", "true"), "flipped", "false");
        BlockState pressMotor = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "east");
        BlockState press = withProperty(requireBlock(PRESS_ID).defaultBlockState(), "facing", "east");
        if (!level.setBlock(SAW_MOTOR, sawMotor, 3)
                || !level.setBlock(SAW, saw, 3)
                || !level.setBlock(PRESS_MOTOR, pressMotor, 3)
                || !level.setBlock(PRESS, press, 3)
                || !level.setBlock(PRESS_FLOOR, Blocks.STONE.defaultBlockState(), 3)) {
            throw new IllegalStateException("failed to place complete beltless Saw/Press fixture");
        }
        requireNoForbiddenTransport();
    }

    private static void requireNoForbiddenTransport() {
        for (int x = 0; x <= 9; x++) for (int y = 198; y <= 205; y++) for (int z = 0; z <= 3; z++) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock());
            if (FORBIDDEN_TRANSPORT.contains(id)) throw new IllegalStateException("forbidden transport block present: " + id);
        }
    }

    private static Object requireExpectedBlockEntity(BlockPos pos, String suffix, long now) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().endsWith(suffix)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless",
                            "pos=" + pos + " blockEntity=" + (blockEntity == null ? "null" : blockEntity.getClass().getName()) + " tick=" + now),
                    "required Create block entity missing/wrong at " + pos);
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
        if (!(rawType instanceof DataComponentType type)) throw new IllegalStateException("Create SEQUENCED_ASSEMBLY component type unavailable");
        Object value = stack.get(type);
        if (value == null) return null;
        ResourceLocation id = (ResourceLocation) value.getClass().getMethod("id").invoke(value);
        int step = ((Number) value.getClass().getMethod("step").invoke(value)).intValue();
        float progress = ((Number) value.getClass().getMethod("progress").invoke(value)).floatValue();
        return new SequenceState(id, step, progress);
    }

    private static List<ItemEntity> itemEntities(AABB box) { return level.getEntitiesOfClass(ItemEntity.class, box); }
    private static String itemDump(AABB box) {
        try {
            return itemEntities(box).stream().map(e -> e.getUUID() + ":" + itemId(e.getItem()) + ":ground=" + e.onGround())
                    .toList().toString();
        } catch (RuntimeException exception) { return "<item-dump-failed:" + exception + ">"; }
    }
    private static ResourceLocation itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()); }
    private static Item requireItem(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("required item missing: " + id);
        return BuiltInRegistries.ITEM.get(id);
    }
    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("required block missing: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) if (property.getName().equals(name))
            return withParsedProperty(state, property, name, value);
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }
    private static <T extends Comparable<T>> BlockState withParsedProperty(BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalStateException("cannot parse " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(String expected, long start, long deadline,
            String ids, String server, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(CAPABILITY,
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                expected, start, deadline, ids, server, "headless", dump);
    }
    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        long now = level == null ? 0L : level.getGameTime();
        return waitDiagnostic == null
                ? diagnostic("world-item CUT/PRESS fixture reaches classified terminal state", now, now, fixtureIds(), safeServerState(), dump)
                : waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }
    private static String fixtureIds() {
        return "sawMotor=" + SAW_MOTOR + " saw=" + SAW + " pressMotor=" + PRESS_MOTOR + " press=" + PRESS
                + " pressFloor=" + PRESS_FLOOR + " inputUuid=" + initialInputId + " transitionUuid=" + transitionalId;
    }
    private static String safeServerState() {
        if (level == null) return "overworld=null";
        return "gameTime=" + level.getGameTime() + " stage=" + stage
                + " sawMotor=" + level.getBlockState(SAW_MOTOR) + " saw=" + level.getBlockState(SAW)
                + " pressMotor=" + level.getBlockState(PRESS_MOTOR) + " press=" + level.getBlockState(PRESS)
                + " sawItems=" + itemDump(SAW_SEARCH) + " pressItems=" + itemDump(PRESS_SEARCH);
    }
    private static void failReflection(SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getTargetException() : exception;
        fail(code, finalDiagnostic("reflectionFailure=" + cause), "runtime reflection failed: " + cause);
    }
    private static void fail(SkyforgeCompilerIntegrationFailure code, SkyforgeCompilerIntegrationDiagnostic diagnostic, String reason) {
        if (!complete) {
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR, PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(CAPABILITY + " failed: " + code + ": " + reason);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private record KineticState(float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork) {}
    private record SequenceState(ResourceLocation id, int step, float progress) {}
}
