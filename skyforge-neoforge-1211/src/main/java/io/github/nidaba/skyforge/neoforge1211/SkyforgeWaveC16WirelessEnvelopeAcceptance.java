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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * C16 black-box wireless-network acceptance for the exact retained CC:Tweaked stack.
 *
 * <p>Real CraftOS computers discover real modem blocks and communicate through normal Lua modem
 * calls. Skyforge deliberately has no compile-time CC dependency and does not invoke CC network
 * internals. The fixture measures whether stock normal and Ender modems preserve or bypass
 * Skyforge-scale infrastructure.
 */
final class SkyforgeWaveC16WirelessEnvelopeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC16WirelessEnvelope";
    private static final String RESULT_FILE = "c16-result.properties";
    private static final int NORMAL_CHANNEL = 43160;
    private static final int ENDER_CHANNEL = 43161;
    private static final long TIMEOUT_TICKS = 260L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC16WirelessEnvelopeAcceptance.class.getName());

    private static final BlockPos NORMAL_SENDER = new BlockPos(0, 64, 0);
    private static final BlockPos NORMAL_NEAR = new BlockPos(48, 64, 0);
    private static final BlockPos NORMAL_FAR = new BlockPos(96, 64, 0);
    private static final BlockPos ENDER_SENDER = new BlockPos(0, 64, 64);
    private static final BlockPos ENDER_REMOTE = new BlockPos(512, 64, 64);
    private static final BlockPos ENDER_CROSS_DIMENSION = new BlockPos(0, 64, 64);

    private record Fixture(String name, int computerId, Path resultFile) {}

    private static ServerLevel overworld;
    private static ServerLevel nether;
    private static Fixture normalSender;
    private static Fixture normalNear;
    private static Fixture normalFar;
    private static Fixture enderSender;
    private static Fixture enderRemote;
    private static Fixture enderCross;
    private static long startTick;
    private static boolean complete;

    private SkyforgeWaveC16WirelessEnvelopeAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC16WirelessEnvelopeAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC16WirelessEnvelopeAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            requireLoaded("computercraft");

            MinecraftServer server = event.getServer();
            overworld = server.overworld();
            nether = server.getLevel(Level.NETHER);
            if (nether == null) {
                throw new IllegalStateException("Nether level is required for interdimensional modem proof");
            }

            // Receivers boot first. Senders sleep before transmission, so every receiver has time
            // to open its real modem channel.
            normalNear = createComputer(
                    server,
                    overworld,
                    "normal-near",
                    NORMAL_NEAR,
                    "wireless_modem_normal",
                    receiverScript(NORMAL_CHANNEL, "normal"));
            normalFar = createComputer(
                    server,
                    overworld,
                    "normal-far",
                    NORMAL_FAR,
                    "wireless_modem_normal",
                    receiverScript(NORMAL_CHANNEL, "normal"));
            enderRemote = createComputer(
                    server,
                    overworld,
                    "ender-remote",
                    ENDER_REMOTE,
                    "wireless_modem_advanced",
                    receiverScript(ENDER_CHANNEL, "ender"));
            enderCross = createComputer(
                    server,
                    nether,
                    "ender-cross",
                    ENDER_CROSS_DIMENSION,
                    "wireless_modem_advanced",
                    receiverScript(ENDER_CHANNEL, "ender"));

            normalSender = createComputer(
                    server,
                    overworld,
                    "normal-sender",
                    NORMAL_SENDER,
                    "wireless_modem_normal",
                    senderScript(NORMAL_CHANNEL, "normal"));
            enderSender = createComputer(
                    server,
                    overworld,
                    "ender-sender",
                    ENDER_SENDER,
                    "wireless_modem_advanced",
                    senderScript(ENDER_CHANNEL, "ender"));

            startTick = overworld.getGameTime();
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Wave C16 real CC computers booted normalNear="
                            + normalNear.computerId()
                            + " normalFar="
                            + normalFar.computerId()
                            + " enderRemote="
                            + enderRemote.computerId()
                            + " enderCross="
                            + enderCross.computerId());
        } catch (Exception exception) {
            fail("fixture bootstrap failed", exception);
        }
    }

    private static Fixture createComputer(
            MinecraftServer server,
            ServerLevel level,
            String name,
            BlockPos computerPos,
            String modemPath,
            String startupScript)
            throws IOException, ReflectiveOperationException {
        int chunkX = computerPos.getX() >> 4;
        int chunkZ = computerPos.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunkAt(computerPos);

        BlockPos modemPos = computerPos.east();
        level.setBlockAndUpdate(computerPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(modemPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(computerPos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(modemPos.below(), Blocks.STONE.defaultBlockState());

        Block computerBlock = requireBlock("computercraft", "computer_normal");
        Block modemBlock = requireBlock("computercraft", modemPath);
        level.setBlockAndUpdate(computerPos, computerBlock.defaultBlockState());

        BlockState modemState = modemBlock.defaultBlockState();
        if (modemState.hasProperty(BlockStateProperties.FACING)) {
            // A modem east of the computer must expose its peripheral on its west/support face.
            modemState = modemState.setValue(BlockStateProperties.FACING, Direction.WEST);
        }
        level.setBlockAndUpdate(modemPos, modemState);

        Object blockEntity = level.getBlockEntity(computerPos);
        if (blockEntity == null || !blockEntity.getClass().getName().contains("ComputerBlockEntity")) {
            throw new IllegalStateException("real ComputerCraft block entity missing for " + name);
        }

        Object serverComputer = invokeNoArgs(blockEntity, "createServerComputer");
        int computerId = ((Number) invokeNoArgs(serverComputer, "getID")).intValue();

        Path computerDirectory = server.getWorldPath(new LevelResource("computercraft"))
                .resolve("computer")
                .resolve(Integer.toString(computerId));
        Files.createDirectories(computerDirectory);
        Path resultFile = computerDirectory.resolve(RESULT_FILE);
        Files.deleteIfExists(resultFile);
        Files.writeString(computerDirectory.resolve("startup.lua"), startupScript);

        invokeNoArgs(serverComputer, "turnOn");
        return new Fixture(name, computerId, resultFile);
    }

    private static String receiverScript(int channel, String expectedMessage) {
        return """
                local modem = assert(peripheral.find("modem"), "wireless modem peripheral missing")
                assert(modem.isWireless(), "discovered modem is not wireless")
                modem.open(%d)

                local timer = os.startTimer(4)
                local received = false
                local message = "NONE"
                local distance = "NONE"

                while true do
                  local event = {os.pullEvent()}
                  if event[1] == "modem_message"
                      and event[3] == %d
                      and tostring(event[5]) == "%s" then
                    received = true
                    message = tostring(event[5])
                    distance = tostring(event[6])
                    break
                  elseif event[1] == "timer" and event[2] == timer then
                    break
                  end
                end

                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=PASS")
                f.writeLine("modemFound=true")
                f.writeLine("wireless=true")
                f.writeLine("received=" .. tostring(received))
                f.writeLine("message=" .. message)
                f.writeLine("distance=" .. distance)
                f.close()
                """.formatted(channel, channel, expectedMessage, RESULT_FILE);
    }

    private static String senderScript(int channel, String message) {
        return """
                local modem = assert(peripheral.find("modem"), "wireless modem peripheral missing")
                assert(modem.isWireless(), "discovered modem is not wireless")
                sleep(1.5)
                modem.transmit(%d, 0, "%s")

                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=PASS")
                f.writeLine("modemFound=true")
                f.writeLine("wireless=true")
                f.writeLine("sent=true")
                f.close()
                """.formatted(channel, message, RESULT_FILE);
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
            throw new IllegalStateException("required C16 mod not loaded: " + modId);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || overworld == null || normalSender == null || enderSender == null) {
            return;
        }

        try {
            Properties normalSenderResult = loadComplete(normalSender, true);
            Properties enderSenderResult = loadComplete(enderSender, true);
            Properties normalNearResult = loadComplete(normalNear, false);
            Properties normalFarResult = loadComplete(normalFar, false);
            Properties enderRemoteResult = loadComplete(enderRemote, false);
            Properties enderCrossResult = loadComplete(enderCross, false);

            if (normalSenderResult != null
                    && enderSenderResult != null
                    && normalNearResult != null
                    && normalFarResult != null
                    && enderRemoteResult != null
                    && enderCrossResult != null) {
                evaluate(
                        normalSenderResult,
                        enderSenderResult,
                        normalNearResult,
                        normalFarResult,
                        enderRemoteResult,
                        enderCrossResult);
                return;
            }

            if (overworld.getGameTime() - startTick > TIMEOUT_TICKS) {
                fail("real CC wireless scripts did not complete before timeout", null);
            }
        } catch (Exception exception) {
            fail("result evaluation failed", exception);
        }
    }

    private static Properties loadComplete(Fixture fixture, boolean sender) throws IOException {
        if (fixture == null || !Files.isRegularFile(fixture.resultFile())) {
            return null;
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(fixture.resultFile())) {
            properties.load(input);
        }

        String[] required = sender
                ? new String[] {"status", "modemFound", "wireless", "sent"}
                : new String[] {"status", "modemFound", "wireless", "received", "message", "distance"};
        for (String key : required) {
            if (properties.getProperty(key) == null) {
                return null;
            }
        }
        return properties;
    }

    private static void evaluate(
            Properties normalSenderResult,
            Properties enderSenderResult,
            Properties normalNearResult,
            Properties normalFarResult,
            Properties enderRemoteResult,
            Properties enderCrossResult) {
        assertSender(normalSenderResult, "normal sender");
        assertSender(enderSenderResult, "Ender sender");
        assertReceiver(normalNearResult, true, "normal", "normal receiver at 48 blocks");
        assertReceiver(normalFarResult, false, "NONE", "normal receiver at 96 blocks");
        assertReceiver(enderRemoteResult, true, "ender", "Ender receiver at 512 blocks");
        assertReceiver(enderCrossResult, true, "ender", "Ender receiver in Nether");

        complete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C16 PASS normalNearReceived="
                        + normalNearResult.getProperty("received")
                        + " normalNearDistance="
                        + normalNearResult.getProperty("distance")
                        + " normalFarReceived="
                        + normalFarResult.getProperty("received")
                        + " enderRemoteReceived="
                        + enderRemoteResult.getProperty("received")
                        + " enderRemoteDistance="
                        + enderRemoteResult.getProperty("distance")
                        + " enderCrossReceived="
                        + enderCrossResult.getProperty("received")
                        + " enderCrossDistance="
                        + enderCrossResult.getProperty("distance"));
    }

    private static void assertSender(Properties result, String label) {
        requireProperty(result, "status", "PASS", label + " script failed");
        requireProperty(result, "modemFound", "true", label + " did not discover modem");
        requireProperty(result, "wireless", "true", label + " modem was not wireless");
        requireProperty(result, "sent", "true", label + " did not transmit");
    }

    private static void assertReceiver(
            Properties result, boolean expectedReceived, String expectedMessage, String label) {
        requireProperty(result, "status", "PASS", label + " script failed");
        requireProperty(result, "modemFound", "true", label + " did not discover modem");
        requireProperty(result, "wireless", "true", label + " modem was not wireless");
        requireProperty(
                result,
                "received",
                Boolean.toString(expectedReceived),
                label + " reception boundary was wrong");
        requireProperty(result, "message", expectedMessage, label + " message mismatch");
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
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C16 FAIL " + reason);
        if (cause == null) {
            throw new IllegalStateException("Wave C16 wireless-envelope acceptance failed: " + reason);
        }
        throw new IllegalStateException("Wave C16 wireless-envelope acceptance failed: " + reason, cause);
    }
}
