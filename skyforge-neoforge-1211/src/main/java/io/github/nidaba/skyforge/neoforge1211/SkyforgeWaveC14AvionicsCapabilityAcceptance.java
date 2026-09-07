package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * C14 black-box capability acceptance for the exact C9 CC:Tweaked + Create: Avionics stack.
 *
 * <p>Skyforge deliberately has no compile-time dependency on either optional mod here. The fixture
 * places their real registered blocks, boots real CC computers, and lets CraftOS Lua discover and
 * invoke the retained peripherals. Reflection is used only to start the already-registered CC
 * computer after its startup program has been written; no peripheral method is invoked reflectively.
 */
final class SkyforgeWaveC14AvionicsCapabilityAcceptance {
    static final String MODE_PROPERTY = "skyforge.dev.waveC14AvionicsCapability";
    private static final String RESULT_FILE = "c14-result.properties";
    private static final int LOW_Y = 80;
    private static final int HIGH_Y = 120;
    private static final long TIMEOUT_TICKS = 240L;
    private static final double ALTITUDE_DELTA_TOLERANCE = 0.05;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC14AvionicsCapabilityAcceptance.class.getName());

    private static final String STARTUP_SCRIPT = """
            local f = assert(fs.open("c14-result.properties", "w"))
            local function put(k, v)
              f.writeLine(k .. "=" .. tostring(v))
            end

            local ok, err = pcall(function()
              put("computerId", os.getComputerID())

              local altitude = peripheral.find("altitude_sensor")
              put("altitudePresent", altitude ~= nil)
              if altitude ~= nil then
                put("height", altitude.getHeight())
              else
                put("height", "NA")
              end

              local throttle = peripheral.find("throttle_lever")
              put("throttlePresent", throttle ~= nil)
              if throttle ~= nil then
                put("initial", throttle.getState())
                throttle.setSignal(9)
                put("afterNine", throttle.getState())
                throttle.setSignal(-4)
                put("afterLow", throttle.getState())
                throttle.setSignal(99)
                put("afterHigh", throttle.getState())
              else
                put("initial", "NA")
                put("afterNine", "NA")
                put("afterLow", "NA")
                put("afterHigh", "NA")
              end
            end)

            put("status", ok and "PASS" or "FAIL")
            if not ok then
              put("error", tostring(err))
            end
            f.close()
            """;

    private record Fixture(
            BlockPos computerPos,
            BlockPos altitudePos,
            BlockPos throttlePos,
            int computerId,
            Path resultFile) {}

    private static ServerLevel level;
    private static Fixture lowFixture;
    private static Fixture highFixture;
    private static long startTick;
    private static boolean complete;

    private SkyforgeWaveC14AvionicsCapabilityAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.parseBoolean(System.getProperty(MODE_PROPERTY, "false"))) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC14AvionicsCapabilityAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC14AvionicsCapabilityAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            requireLoaded("create");
            requireLoaded("sable");
            requireLoaded("aeronautics");
            requireLoaded("computercraft");
            requireLoaded("create_avionics");
            requireLoaded("simulated");

            MinecraftServer server = event.getServer();
            level = server.overworld();

            lowFixture = createFixture(server, new BlockPos(0, LOW_Y, 0));
            highFixture = createFixture(server, new BlockPos(16, HIGH_Y, 0));
            startTick = level.getGameTime();

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Wave C14 computers booted lowId="
                            + lowFixture.computerId()
                            + " highId="
                            + highFixture.computerId());
        } catch (Exception exception) {
            fail("fixture bootstrap failed", exception);
        }
    }

    private static Fixture createFixture(MinecraftServer server, BlockPos computerPos)
            throws IOException, ReflectiveOperationException {
        level.getChunkAt(computerPos);

        BlockPos altitudePos = computerPos.east();
        BlockPos throttlePos = computerPos.west();

        for (int dx = -2; dx <= 2; dx++) {
            level.setBlockAndUpdate(computerPos.offset(dx, -1, 0), Blocks.STONE.defaultBlockState());
        }

        Block computerBlock = requireBlock("computercraft", "computer_normal");
        Block altitudeBlock = requireBlock("simulated", "altitude_sensor");
        Block throttleBlock = requireBlock("simulated", "throttle_lever");

        level.setBlockAndUpdate(computerPos, computerBlock.defaultBlockState());
        level.setBlockAndUpdate(altitudePos, altitudeBlock.defaultBlockState());

        BlockState throttleState = throttleBlock.defaultBlockState();
        if (throttleState.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            throttleState = throttleState.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        }
        if (throttleState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            throttleState = throttleState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        level.setBlockAndUpdate(throttlePos, throttleState);

        Object blockEntity = level.getBlockEntity(computerPos);
        if (blockEntity == null || !blockEntity.getClass().getName().contains("ComputerBlockEntity")) {
            throw new IllegalStateException(
                    "registered ComputerCraft block did not create a computer block entity at " + computerPos);
        }

        Object serverComputer = invokeNoArgs(blockEntity, "createServerComputer");
        int computerId = ((Number) invokeNoArgs(serverComputer, "getID")).intValue();

        Path computerDirectory = server.getWorldPath(new LevelResource("computercraft"))
                .resolve("computer")
                .resolve(Integer.toString(computerId));
        Files.createDirectories(computerDirectory);

        Path resultFile = computerDirectory.resolve(RESULT_FILE);
        Files.deleteIfExists(resultFile);
        Files.writeString(computerDirectory.resolve("startup.lua"), STARTUP_SCRIPT);

        invokeNoArgs(serverComputer, "turnOn");
        return new Fixture(computerPos, altitudePos, throttlePos, computerId, resultFile);
    }

    private static Object invokeNoArgs(Object target, String method)
            throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static Block requireBlock(String namespace, String path) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Block block = BuiltInRegistries.BLOCK.get(key);
        if (block == Blocks.AIR) {
            throw new IllegalStateException("missing required registered block " + key);
        }
        return block;
    }

    private static void requireLoaded(String modId) {
        if (!ModList.get().isLoaded(modId)) {
            throw new IllegalStateException("required C14 mod not loaded: " + modId);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || lowFixture == null || highFixture == null) {
            return;
        }

        try {
            Properties low = loadIfComplete(lowFixture.resultFile());
            Properties high = loadIfComplete(highFixture.resultFile());
            if (low != null && high != null) {
                evaluate(low, high);
                return;
            }

            if (level.getGameTime() - startTick > TIMEOUT_TICKS) {
                fail(
                        "real CC computers did not produce complete peripheral results before timeout"
                                + " lowFile="
                                + lowFixture.resultFile()
                                + " highFile="
                                + highFixture.resultFile(),
                        null);
            }
        } catch (Exception exception) {
            fail("result evaluation failed", exception);
        }
    }

    private static Properties loadIfComplete(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        }

        for (String key : new String[] {
            "status",
            "computerId",
            "altitudePresent",
            "throttlePresent",
            "height",
            "initial",
            "afterNine",
            "afterLow",
            "afterHigh"
        }) {
            if (properties.getProperty(key) == null) {
                return null;
            }
        }
        return properties;
    }

    private static void evaluate(Properties low, Properties high) {
        requireProperty(low, "status", "PASS", "low computer Lua program failed: " + low.getProperty("error"));
        requireProperty(high, "status", "PASS", "high computer Lua program failed: " + high.getProperty("error"));
        requireProperty(low, "altitudePresent", "true", "low computer did not discover altitude_sensor");
        requireProperty(high, "altitudePresent", "true", "high computer did not discover altitude_sensor");
        requireProperty(low, "throttlePresent", "true", "low computer did not discover throttle_lever");
        requireProperty(high, "throttlePresent", "true", "high computer did not discover throttle_lever");

        if (Integer.parseInt(low.getProperty("computerId")) != lowFixture.computerId()
                || Integer.parseInt(high.getProperty("computerId")) != highFixture.computerId()) {
            throw new IllegalStateException("Lua result did not originate from the booted CC computers");
        }

        double lowHeight = Double.parseDouble(low.getProperty("height"));
        double highHeight = Double.parseDouble(high.getProperty("height"));
        double expectedDelta = HIGH_Y - LOW_Y;
        if (Math.abs((highHeight - lowHeight) - expectedDelta) > ALTITUDE_DELTA_TOLERANCE) {
            throw new IllegalStateException(
                    "altitude sensor did not track specimen world position: low="
                            + lowHeight
                            + " high="
                            + highHeight
                            + " expectedDelta="
                            + expectedDelta);
        }

        assertThrottleSequence(low, "low");
        assertThrottleSequence(high, "high");

        int lowPhysical = level.getSignal(lowFixture.throttlePos(), Direction.UP);
        int highPhysical = level.getSignal(highFixture.throttlePos(), Direction.UP);
        if (lowPhysical != 15 || highPhysical != 15) {
            throw new IllegalStateException(
                    "bounded Lua command did not reach retained physical throttle state: low="
                            + lowPhysical
                            + " high="
                            + highPhysical);
        }

        complete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C14 PASS lowHeight="
                        + lowHeight
                        + " highHeight="
                        + highHeight
                        + " altitudeDelta="
                        + (highHeight - lowHeight)
                        + " throttle=0->9->0->15"
                        + " physicalLow="
                        + lowPhysical
                        + " physicalHigh="
                        + highPhysical);
    }

    private static void assertThrottleSequence(Properties result, String label) {
        requireProperty(result, "initial", "0", label + " throttle did not start at zero");
        requireProperty(result, "afterNine", "9", label + " throttle did not accept bounded command 9");
        requireProperty(result, "afterLow", "0", label + " throttle did not clamp negative command");
        requireProperty(result, "afterHigh", "15", label + " throttle did not clamp oversized command");
    }

    private static void requireProperty(
            Properties properties, String key, String expected, String reason) {
        String actual = properties.getProperty(key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(reason + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void fail(String reason, Throwable cause) {
        complete = true;
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C14 FAIL " + reason);
        if (cause == null) {
            throw new IllegalStateException("Wave C14 avionics capability acceptance failed: " + reason);
        }
        throw new IllegalStateException("Wave C14 avionics capability acceptance failed: " + reason, cause);
    }
}
