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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** MECH-002 L3 proof consuming PLATFORM-009 environmental water-wheel source authority. */
final class SkyforgeMech002NaturalPowerAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.mech002NaturalPower";
    private static final String PREFIX = "MECH_002_NATURAL_WORKSHOP_POWER";
    private static final String ASSET_ID = "guild.utility.waterwheel_airflow_bench.mech_002";
    private static final String REQUIRED_CAPABILITY = "CREATE_WATER_WHEEL_SOURCE_LIFECYCLE";
    private static final String TARGET_STACK = "C11_FLIGHT_EXACT_2026-09-05";
    private static final String EXPECTED_DIGEST =
            "6a8834d2c2d18cd0914fc488807d8e4042eb4a12f8fe11258a94d931c400f293";
    private static final String PLAN_RESOURCE =
            "/data/skyforge/mechanisms/mech_002_waterwheel_airflow_bench.json";
    private static final String STRUCTURE_ID = "skyforge:mech_002_waterwheel_airflow_bench";
    private static final float EXPECTED_SPEED = -8.0f;
    private static final float SPEED_EPSILON = 0.001f;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeMech002NaturalPowerAcceptance.class.getName());

    private static final BlockPos BASE = new BlockPos(40, 200, 0);
    private static final long ACTIVE_DEADLINE_TICKS = 180L;
    private static final long STABILITY_TICKS = 100L;
    private static final long DISABLED_DEADLINE_TICKS = 140L;
    private static final long RECOVERY_DEADLINE_TICKS = 140L;

    private static ServerLevel level;
    private static JsonObject plan;
    private static BlockPos sourcePos;
    private static BlockPos relayPos;
    private static BlockPos endpointPos;
    private static BlockPos flowPos;
    private static BlockPos feederPos;
    private static BlockState feederRestoreState;
    private static List<BlockPos> clearancePositions = List.of();
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static Stage stage;
    private static boolean complete;
    private static float initialSourceSpeed;
    private static float initialEndpointSpeed;
    private static long stableStartTick = -1L;

    private enum Stage {
        ACTIVE,
        DISABLED,
        RECOVERED
    }

    private SkyforgeMech002NaturalPowerAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeMech002NaturalPowerAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeMech002NaturalPowerAcceptance::onServerTickPost);
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
            prepareFixture(plan);
            stage = Stage.ACTIVE;
            waitDiagnostic = diagnostic(
                    "compiled falling-water precondition drives water wheel, relay, and airflow endpoint",
                    now,
                    now + ACTIVE_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "structure template placed; awaiting environmental source generation");
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic(
                            "compiled MECH-002 plan is loaded and placed",
                            now,
                            now,
                            fixtureIds(),
                            safeServerState(),
                            exception.toString()),
                    "MECH-002 preparation failed: " + exception);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null || stage == null) {
            return;
        }
        long now = level.getGameTime();
        try {
            switch (stage) {
                case ACTIVE -> pollActive(now);
                case DISABLED -> pollDisabled(now);
                case RECOVERED -> pollRecovered(now);
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : exception;
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic("reflectionFailure=" + cause),
                    "MECH-002 Create runtime reflection failed: " + cause);
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic(exception.toString()),
                    "MECH-002 runtime failed: " + exception);
        }
    }

    private static void pollActive(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "WaterWheelBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);
        Vec3 flow = flowVector(flowPos);
        boolean feederPresent = level.getFluidState(feederPos).isSource();
        boolean active = feederPresent
                && hasActiveWaterFlow(flow)
                && speedMatches(sourceState.speed(), EXPECTED_SPEED)
                && speedMatches(endpointState.speed(), EXPECTED_SPEED)
                && endpointState.hasSource()
                && endpointState.hasNetwork();

        if (active) {
            requireClearance();
            if (stableStartTick < 0L) {
                stableStartTick = now;
                initialSourceSpeed = sourceState.speed();
                initialEndpointSpeed = endpointState.speed();
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " ACTIVE sourceSpeed=" + sourceState.speed()
                                + " endpointSpeed=" + endpointState.speed()
                                + " feederPresent=true flowCell=" + level.getFluidState(flowPos)
                                + " flowVector=" + flow
                                + " endpointHasSource=true endpointHasNetwork=true tick=" + now);
            }
            if (now - stableStartTick >= STABILITY_TICKS) {
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " STABLE stableTicks=" + (now - stableStartTick)
                                + " feederPresent=true sourceSpeed=" + sourceState.speed()
                                + " endpointSpeed=" + endpointState.speed() + " tick=" + now);
                if (!level.setBlock(feederPos, Blocks.AIR.defaultBlockState(), 3)) {
                    throw new IllegalStateException("failed to remove compiled environmental feeder at " + feederPos);
                }
                stage = Stage.DISABLED;
                waitDiagnostic = diagnostic(
                        "removing only the compiled feeder naturally drains the drive cell and stops source and endpoint",
                        now,
                        now + DISABLED_DEADLINE_TICKS,
                        fixtureIds(),
                        safeServerState(),
                        "environmentalControlRemoved=true stableTicks=" + (now - stableStartTick)
                                + " initialSourceSpeed=" + initialSourceSpeed
                                + " initialEndpointSpeed=" + initialEndpointSpeed);
            }
            return;
        }

        if (stableStartTick >= 0L) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic("lostPersistentFeed=true feederPresent=" + feederPresent
                            + " source=" + sourceState + " endpoint=" + endpointState + " flowVector=" + flow),
                    "compiled source-fed mechanism lost power during the required stability interval");
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "feederPresent=" + feederPresent + " source=" + sourceState
                                    + " endpoint=" + endpointState + " flowVector=" + flow),
                    "compiled natural-power mechanism did not become active before deadline");
        }
    }

    private static void pollDisabled(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "WaterWheelBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);
        FluidState feederState = level.getFluidState(feederPos);
        FluidState flowState = level.getFluidState(flowPos);

        if (feederState.isEmpty()
                && flowState.isEmpty()
                && speedMatches(sourceState.speed(), 0.0f)
                && speedMatches(endpointState.speed(), 0.0f)
                && !endpointState.hasSource()) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " DISABLED sourceSpeed=0.0 endpointSpeed=0.0"
                            + " feederPresent=false flowCellEmpty=true endpointHasSource=false endpointHasNetwork="
                            + endpointState.hasNetwork() + " tick=" + now);
            if (!level.setBlock(feederPos, feederRestoreState, 3)) {
                throw new IllegalStateException("failed to restore compiled water feeder at " + feederPos);
            }
            stage = Stage.RECOVERED;
            waitDiagnostic = diagnostic(
                    "restoring only the compiled feeder naturally reforms the falling drive and recovers source and endpoint",
                    now,
                    now + RECOVERY_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "environmentalControlRestored=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "feeder=" + feederState + " flow=" + flowState
                                    + " source=" + sourceState + " endpoint=" + endpointState),
                    "compiled mechanism did not naturally drain and stop after feeder removal");
        }
    }

    private static void pollRecovered(long now) throws ReflectiveOperationException {
        Object source = requireExpectedBlockEntity(sourcePos, "WaterWheelBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(endpointPos, "EncasedFanBlockEntity", now);
        KineticState sourceState = kineticState(source);
        KineticState endpointState = kineticState(endpoint);
        Vec3 flow = flowVector(flowPos);
        boolean feederPresent = level.getFluidState(feederPos).isSource();

        if (feederPresent
                && hasActiveWaterFlow(flow)
                && speedMatches(sourceState.speed(), EXPECTED_SPEED)
                && speedMatches(endpointState.speed(), EXPECTED_SPEED)
                && endpointState.hasSource()
                && endpointState.hasNetwork()) {
            requireClearance();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS assetId=" + ASSET_ID
                            + " digest=" + EXPECTED_DIGEST
                            + " requiredCapability=" + REQUIRED_CAPABILITY
                            + " initialSourceSpeed=" + initialSourceSpeed
                            + " initialEndpointSpeed=" + initialEndpointSpeed
                            + " recoveredSourceSpeed=" + sourceState.speed()
                            + " recoveredEndpointSpeed=" + endpointState.speed()
                            + " flowVector=" + flow
                            + " feederPresent=true stableTicks=" + STABILITY_TICKS
                            + " environmentalDisableObserved=true environmentalRecoveryObserved=true"
                            + " endpointHasSource=true endpointHasNetwork=true"
                            + " clearanceCells=" + clearancePositions.size()
                            + " clientState=headless");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "source=" + sourceState + " endpoint=" + endpointState + " flowVector=" + flow),
                    "compiled natural-power mechanism did not recover before deadline");
        }
    }

    private static JsonObject loadPlan() {
        try (InputStream stream =
                        SkyforgeMech002NaturalPowerAcceptance.class.getResourceAsStream(PLAN_RESOURCE)) {
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
        requireString(loaded, "compilerVersion", "mech-0.2-fixed-world");
        requireString(loaded, "targetStackAuthority", TARGET_STACK);
        requireString(loaded, "requiredPlatformCapability", REQUIRED_CAPABILITY);
        requireString(loaded, "sourcePolicy", "qualified_environmental_source_candidate_not_geography_canon");
        requireString(loaded, "digestSha256", EXPECTED_DIGEST);

        JsonObject evidence = loaded.getAsJsonObject("platformEvidence");
        if (evidence == null
                || !"accepted".equals(evidence.get("status").getAsString())
                || !"L2".equals(evidence.get("verificationLevel").getAsString())
                || !"PASS".equals(evidence.get("result").getAsString())
                || !evidence.get("agentCAuthorized").getAsBoolean()) {
            throw new IllegalStateException("compiled mechanism does not carry accepted Agent C platform evidence");
        }

        JsonObject environment = loaded.getAsJsonObject("environmentalEnvelope");
        requireString(environment, "sourcePlacementId", "source");
        requireString(environment, "disableCell", "source_feeder");
        JsonArray requiredCells = environment.getAsJsonArray("requiredCells");
        if (requiredCells == null || requiredCells.size() != 2) {
            throw new IllegalStateException("MECH-002 requires feeder and falling-drive environmental cells");
        }
        JsonObject feeder = requiredCells.get(0).getAsJsonObject();
        requireString(feeder, "placementId", "source_feeder");
        requireString(feeder, "role", "persistent_water_feeder");
        requireTriple(feeder.getAsJsonArray("offsetFromSource"), 0, 1, -1, "feeder offset");
        JsonObject flow = requiredCells.get(1).getAsJsonObject();
        requireString(flow, "placementId", "source_flow_0");
        requireString(flow, "role", "falling_water_drive");
        requireTriple(flow.getAsJsonArray("offsetFromSource"), 0, 0, -1, "flow offset");
        requireDoubleTriple(flow.getAsJsonArray("expectedFlowVector"), 0.0, -1.0, 0.0, "expected flow vector");

        JsonObject active = loaded.getAsJsonObject("runtimeExpectations").getAsJsonObject("active");
        requireNumber(active, "sourceSpeed", EXPECTED_SPEED);
        requireNumber(active, "endpointSpeed", EXPECTED_SPEED);
        requireNumber(active, "stableTicks", STABILITY_TICKS);
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
            Class.forName("com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity");
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

        String disableCell = loaded.getAsJsonObject("environmentalEnvelope").get("disableCell").getAsString();
        for (JsonElement element : loaded.getAsJsonArray("placements")) {
            JsonObject placement = element.getAsJsonObject();
            BlockPos worldPos = BASE.offset(localPos(placement));
            BlockState state = blockState(placement.getAsJsonObject("blockState"));
            String id = placement.get("id").getAsString();
            String role = placement.get("mechanicalRole").getAsString();
            if ("kinetic_source".equals(role)) {
                sourcePos = worldPos;
            } else if ("required_relay".equals(role)) {
                relayPos = worldPos;
            } else if ("airflow_endpoint".equals(role)) {
                endpointPos = worldPos;
            }
            if ("source_flow_0".equals(id)) {
                flowPos = worldPos;
            }
            if (disableCell.equals(id)) {
                feederPos = worldPos;
                feederRestoreState = state;
            }
        }
        if (sourcePos == null || relayPos == null || endpointPos == null
                || flowPos == null || feederPos == null || feederRestoreState == null) {
            throw new IllegalStateException("compiled mechanism is missing source/relay/endpoint/environment roles");
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
                throw new IllegalStateException("compiled moving/airflow clearance is obstructed at " + pos
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
                    "required MECH-002 block entity missing at " + pos);
            throw new IllegalStateException("unreachable");
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("unexpected block entity=" + blockEntity.getClass().getName() + " at " + pos),
                    "unexpected MECH-002 block entity at " + pos);
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

    private static Vec3 flowVector(BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.isEmpty() ? Vec3.ZERO : fluid.getFlow(level, pos);
    }

    private static boolean hasActiveWaterFlow(Vec3 flow) {
        return flowPos != null && !level.getFluidState(flowPos).isEmpty() && Math.abs(flow.y) > 0.5;
    }

    private static boolean speedMatches(float actual, float expected) {
        return Math.abs(actual - expected) <= SPEED_EPSILON;
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
        return new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()};
    }

    private static void requireTriple(JsonArray array, int x, int y, int z, String label) {
        int[] values = intTriple(array);
        if (values[0] != x || values[1] != y || values[2] != z) {
            throw new IllegalStateException(label + " must equal [" + x + "," + y + "," + z + "]");
        }
    }

    private static void requireDoubleTriple(JsonArray array, double x, double y, double z, String label) {
        if (array == null || array.size() != 3
                || Double.compare(array.get(0).getAsDouble(), x) != 0
                || Double.compare(array.get(1).getAsDouble(), y) != 0
                || Double.compare(array.get(2).getAsDouble(), z) != 0) {
            throw new IllegalStateException(label + " must match accepted PLATFORM-009 vector");
        }
    }

    private static void requireNumber(JsonObject object, String key, double expected) {
        if (object == null || !object.has(key) || Double.compare(object.get(key).getAsDouble(), expected) != 0) {
            throw new IllegalStateException("compiled mechanism " + key + " must equal " + expected);
        }
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
            return diagnostic("MECH-002 reaches terminal state", now, now, fixtureIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }

    private static String fixtureIds() {
        return "assetId=" + ASSET_ID
                + " source=" + sourcePos
                + " relay=" + relayPos
                + " endpoint=" + endpointPos
                + " flow=" + flowPos
                + " feeder=" + feederPos
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
                + " flow=" + stateAt(flowPos)
                + " feeder=" + stateAt(feederPos)
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
