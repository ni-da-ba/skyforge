package io.github.nidaba.skyforge.neoforge1211;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** MECH-001 L3 proof consuming the accepted fixed-world Create kinetic lifecycle capability. */
final class SkyforgeMech001FunctionalMechanismAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.mech001FunctionalMechanism";
    private static final String PREFIX = "MECH_001_FIXED_WORLD_UTILITY";
    private static final String ASSET_ID = "guild.utility.airflow_bench.mech_001";
    private static final String REQUIRED_CAPABILITY = "CREATE_KINETIC_NETWORK_LIFECYCLE";
    private static final String TARGET_STACK = "C11_FLIGHT_EXACT_2026-09-05";
    private static final String EXPECTED_DIGEST =
            "da905478f5f3e42e197bd0ef1a38baba848c8ce65c2a911d7153743597098ce1";
    private static final String PLAN_RESOURCE =
            "/data/skyforge/mechanisms/mech_001_airflow_bench.json";
    private static final String STRUCTURE_ID = "skyforge:mech_001_airflow_bench";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeMech001FunctionalMechanismAcceptance.class.getName());

    private static final BlockPos BASE = new BlockPos(20, 200, 0);
    private static final long INIT_DEADLINE_TICKS = 60L;
    private static final long DISCONNECT_DEADLINE_TICKS = 60L;
    private static final long REBUILD_DEADLINE_TICKS = 60L;

    private static ServerLevel level;
    private static JsonObject plan;
    private static BlockPos sourcePos;
    private static BlockPos relayPos;
    private static BlockPos endpointPos;
    private static BlockState relayState;
    private static List<BlockPos> clearancePositions = List.of();
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static Stage stage;
    private static boolean complete;
    private static float initialSpeed;

    private enum Stage {
        INITIAL_BUILD,
        DISCONNECTED,
        REBUILT
    }

    private SkyforgeMech001FunctionalMechanismAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeMech001FunctionalMechanismAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeMech001FunctionalMechanismAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START assetId=" + ASSET_ID
                        + " requiredCapability=" + REQUIRED_CAPABILITY
                        + " startTick=" + now
                        + " clientState=headless");
        try {
            plan = loadPlan();
            validatePlanContract(plan);
            requireRuntimePreconditions(plan);
            prepareFixture(plan);
            stage = Stage.INITIAL_BUILD;
            waitDiagnostic = diagnostic(
                    "compiled airflow endpoint receives nonzero propagated Create speed",
                    now,
                    now + INIT_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "compiled plan placed; awaiting kinetic attachment");
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic(
                            "compiled MECH-001 plan is loaded and placed",
                            now,
                            now,
                            fixtureIds(),
                            safeServerState(),
                            exception.toString()),
                    "MECH-001 preparation failed: " + exception);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null || stage == null) {
            return;
        }
        long now = level.getGameTime();
        try {
            switch (stage) {
                case INITIAL_BUILD -> pollInitialBuild(now);
                case DISCONNECTED -> pollDisconnected(now);
                case REBUILT -> pollRebuilt(now);
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : exception;
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic("reflectionFailure=" + cause),
                    "MECH-001 Create kinetic reflection failed: " + cause);
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic(exception.toString()),
                    "MECH-001 runtime failed: " + exception);
        }
    }

    private static void pollInitialBuild(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) > 0.0f
                && endpointState.hasSource()
                && endpointState.hasNetwork()) {
            requireClearance();
            initialSpeed = endpointState.speed();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " ACTIVE endpointSpeed=" + endpointState.speed()
                            + " theoreticalSpeed=" + endpointState.theoreticalSpeed()
                            + " sourceSpeed=" + sourceState.speed()
                            + " endpointHasSource=true endpointHasNetwork=true tick=" + now);
            if (!level.setBlock(relayPos, Blocks.AIR.defaultBlockState(), 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to remove compiled sever node at " + relayPos),
                        "could not sever compiled mechanism");
            }
            stage = Stage.DISCONNECTED;
            waitDiagnostic = diagnostic(
                    "removing compiled required relay stops the airflow endpoint",
                    now,
                    now + DISCONNECT_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " relayRemoved=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(),
                            safeServerState(),
                            "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "compiled airflow mechanism did not become active before deadline");
        }
    }

    private static void pollDisconnected(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) == 0.0f && !endpointState.hasSource()) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " SEVERED endpointSpeed=0.0 endpointHasSource=false"
                            + " endpointHasNetwork=" + endpointState.hasNetwork()
                            + " sourceSpeed=" + sourceState.speed()
                            + " tick=" + now);
            if (!level.setBlock(relayPos, relayState, 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to restore compiled relay at " + relayPos),
                        "could not restore compiled mechanism");
            }
            stage = Stage.REBUILT;
            waitDiagnostic = diagnostic(
                    "restoring compiled relay rebuilds the airflow endpoint network",
                    now,
                    now + REBUILD_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " relayRestored=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(),
                            safeServerState(),
                            "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "compiled mechanism did not become inactive after sever before deadline");
        }
    }

    private static void pollRebuilt(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) > 0.0f
                && endpointState.hasSource()
                && endpointState.hasNetwork()) {
            requireClearance();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS assetId=" + ASSET_ID
                            + " digest=" + EXPECTED_DIGEST
                            + " requiredCapability=" + REQUIRED_CAPABILITY
                            + " initialSpeed=" + initialSpeed
                            + " rebuiltSpeed=" + endpointState.speed()
                            + " sourceSpeed=" + sourceState.speed()
                            + " severedObserved=true rebuiltObserved=true"
                            + " endpointHasSource=true endpointHasNetwork=true"
                            + " clearanceCells=" + clearancePositions.size()
                            + " clientState=headless");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(),
                            safeServerState(),
                            "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "compiled mechanism did not rebuild before deadline");
        }
    }

    private static JsonObject loadPlan() {
        try (InputStream stream =
                        SkyforgeMech001FunctionalMechanismAcceptance.class.getResourceAsStream(PLAN_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("compiled mechanism plan resource is missing: " + PLAN_RESOURCE);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new IllegalStateException("compiled mechanism plan root is not an object");
                }
                return element.getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load compiled mechanism plan", exception);
        }
    }

    private static void validatePlanContract(JsonObject loaded) {
        requireString(loaded, "schema", "skyforge.functional-mechanism-plan.v1");
        requireString(loaded, "assetId", ASSET_ID);
        requireString(loaded, "compilerVersion", "mech-0.1-fixed-world");
        requireString(loaded, "targetStackAuthority", TARGET_STACK);
        requireString(loaded, "requiredPlatformCapability", REQUIRED_CAPABILITY);
        requireString(loaded, "sourcePolicy", "qualified_test_source_not_gameplay_canon");
        requireString(loaded, "digestSha256", EXPECTED_DIGEST);

        JsonObject evidence = loaded.getAsJsonObject("platformEvidence");
        if (evidence == null
                || !"accepted".equals(evidence.get("status").getAsString())
                || !"L2".equals(evidence.get("verificationLevel").getAsString())
                || !"PASS".equals(evidence.get("result").getAsString())
                || !evidence.get("agentCAuthorized").getAsBoolean()) {
            throw new IllegalStateException("compiled mechanism does not carry accepted Agent C platform evidence");
        }
        JsonObject connectivity = loaded.getAsJsonObject("connectivity");
        requireString(connectivity, "severNode", "relay_0");
    }

    private static void requireRuntimePreconditions(JsonObject loaded) {
        if (!ModList.get().isLoaded("create")) {
            throw new IllegalStateException("required exact-stack Create mod not loaded");
        }
        for (JsonElement element : loaded.getAsJsonArray("placements")) {
            JsonObject state = element.getAsJsonObject().getAsJsonObject("blockState");
            requireBlock(ResourceLocation.parse(state.get("name").getAsString()));
        }
        try {
            Class.forName("com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("required Create runtime class unavailable: " + exception.getMessage(), exception);
        }
    }

    private static void prepareFixture(JsonObject loaded) {
        JsonObject envelope = loaded.getAsJsonObject("envelope");
        int[] min = intTriple(envelope.getAsJsonArray("min"));
        int[] max = intTriple(envelope.getAsJsonArray("max"));

        level.getChunk(BASE.getX() >> 4, BASE.getZ() >> 4);
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
            BlockState state = blockState(placement.getAsJsonObject("blockState"));
            String role = placement.get("mechanicalRole").getAsString();
            if ("kinetic_source".equals(role)) {
                sourcePos = worldPos;
            } else if ("required_relay".equals(role)) {
                relayPos = worldPos;
                relayState = state;
            } else if ("airflow_endpoint".equals(role)) {
                endpointPos = worldPos;
            }
        }
        if (sourcePos == null || relayPos == null || endpointPos == null || relayState == null) {
            throw new IllegalStateException("compiled mechanism is missing source/relay/endpoint roles");
        }

        String placeCommand = "place template " + STRUCTURE_ID
                + " " + BASE.getX() + " " + BASE.getY() + " " + BASE.getZ();
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack(), placeCommand);

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
            JsonObject requirement = element.getAsJsonObject();
            BlockPos supportPos = BASE.offset(toBlockPos(requirement.getAsJsonArray("supportBelow")));
            if (level.getBlockState(supportPos).isAir()) {
                throw new IllegalStateException("compiled support requirement is empty at " + supportPos);
            }
        }

        List<BlockPos> resolvedClearance = new ArrayList<>();
        for (JsonElement element : loaded.getAsJsonArray("clearanceCells")) {
            resolvedClearance.add(BASE.offset(toBlockPos(element.getAsJsonArray())));
        }
        clearancePositions = List.copyOf(resolvedClearance);
        requireClearance();
    }

    private static void requireClearance() {
        for (BlockPos pos : clearancePositions) {
            if (!level.getBlockState(pos).isAir()) {
                throw new IllegalStateException("compiled airflow clearance is obstructed at " + pos
                        + " state=" + level.getBlockState(pos));
            }
        }
    }

    private static Object requireExpectedBlockEntity(BlockPos pos, String suffix, long now) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("missing block entity at " + pos + " tick=" + now),
                    "required MECH-001 block entity missing at " + pos);
            throw new IllegalStateException("unreachable");
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("unexpected block entity=" + blockEntity.getClass().getName() + " at " + pos),
                    "unexpected MECH-001 block entity at " + pos);
            throw new IllegalStateException("unreachable");
        }
        return blockEntity;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kineticClass.isInstance(blockEntity)) {
            throw new IllegalStateException("not a Create KineticBlockEntity: " + blockEntity.getClass().getName());
        }
        float speed = ((Number) kineticClass.getDeclaredMethod("getSpeed").invoke(blockEntity)).floatValue();
        float theoretical =
                ((Number) kineticClass.getDeclaredMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue();
        boolean hasSource = (Boolean) kineticClass.getDeclaredMethod("hasSource").invoke(blockEntity);
        boolean hasNetwork = (Boolean) kineticClass.getDeclaredMethod("hasNetwork").invoke(blockEntity);
        return new KineticState(speed, theoretical, hasSource, hasNetwork);
    }

    private static BlockState blockState(JsonObject stateObject) {
        BlockState state = requireBlock(ResourceLocation.parse(stateObject.get("name").getAsString())).defaultBlockState();
        JsonObject properties = stateObject.getAsJsonObject("properties");
        if (properties == null) {
            return state;
        }
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            state = withProperty(state, entry.getKey(), entry.getValue().getAsString());
        }
        return state;
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required exact-stack block is not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return withParsedProperty(state, property, name, value);
            }
        }
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static BlockPos localPos(JsonObject placement) {
        return toBlockPos(placement.getAsJsonArray("pos"));
    }

    private static BlockPos toBlockPos(JsonArray array) {
        int[] values = intTriple(array);
        return new BlockPos(values[0], values[1], values[2]);
    }

    private static int[] intTriple(JsonArray array) {
        if (array == null || array.size() != 3) {
            throw new IllegalStateException("expected three-integer coordinate");
        }
        return new int[] {
            array.get(0).getAsInt(),
            array.get(1).getAsInt(),
            array.get(2).getAsInt()
        };
    }

    private static void requireString(JsonObject object, String key, String expected) {
        if (object == null || !object.has(key) || !expected.equals(object.get(key).getAsString())) {
            throw new IllegalStateException("compiled mechanism " + key + " must equal " + expected);
        }
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            String expected,
            long startTick,
            long deadlineTick,
            String ids,
            String serverState,
            String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                ASSET_ID,
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                expected,
                startTick,
                deadlineTick,
                ids,
                serverState,
                "headless",
                dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        if (waitDiagnostic == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic("MECH-001 reaches terminal state", now, now, fixtureIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }

    private static String fixtureIds() {
        return "assetId=" + ASSET_ID
                + " source=" + sourcePos
                + " relay=" + relayPos
                + " endpoint=" + endpointPos
                + " digest=" + EXPECTED_DIGEST;
    }

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        return "gameTime=" + level.getGameTime()
                + " source=" + stateAt(sourcePos)
                + " relay=" + stateAt(relayPos)
                + " endpoint=" + stateAt(endpointPos)
                + " stage=" + stage;
    }

    private static String stateAt(BlockPos pos) {
        return pos == null ? "null" : level.getBlockState(pos).toString();
    }

    private static void fail(
            SkyforgeCompilerIntegrationFailure code,
            SkyforgeCompilerIntegrationDiagnostic diagnostic,
            String reason) {
        if (!complete) {
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(ASSET_ID + " failed: " + code + ": " + reason);
    }

    private record KineticState(float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork) {}
}
