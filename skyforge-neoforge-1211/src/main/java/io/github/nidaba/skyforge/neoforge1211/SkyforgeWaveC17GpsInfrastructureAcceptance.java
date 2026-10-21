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
 * C17 black-box GPS infrastructure acceptance for the exact retained CC:Tweaked stack.
 *
 * <p>The specimen boots real CraftOS computers and runs the stock {@code gps} API/program through
 * ordinary wireless and Ender modem blocks. Skyforge deliberately has no compile-time CC dependency
 * and never calls ComputerCraft GPS/network internals.
 */
final class SkyforgeWaveC17GpsInfrastructureAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC17GpsInfrastructure";
    private static final String RESULT_FILE = "c17-result.properties";
    private static final long HOST_WARMUP_TICKS = 60L;
    private static final long TIMEOUT_TICKS = 260L;
    private static final double POSITION_TOLERANCE = 0.15;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC17GpsInfrastructureAcceptance.class.getName());

    private static final BlockPos NO_HOST_LOCATOR = new BlockPos(0, 64, 0);

    private static final BlockPos NORMAL_HOST_A = new BlockPos(0, 64, 0);
    private static final BlockPos NORMAL_HOST_B = new BlockPos(0, 64, 32);
    private static final BlockPos NORMAL_HOST_C = new BlockPos(32, 64, 0);
    private static final BlockPos NORMAL_NEAR_LOCATOR = new BlockPos(16, 64, 16);
    private static final BlockPos NORMAL_FAR_LOCATOR = new BlockPos(160, 64, 16);

    private static final BlockPos ENDER_HOST_A = new BlockPos(0, 80, 256);
    private static final BlockPos ENDER_HOST_B = new BlockPos(0, 80, 288);
    private static final BlockPos ENDER_HOST_C = new BlockPos(32, 80, 256);
    private static final BlockPos ENDER_REMOTE_LOCATOR = new BlockPos(512, 80, 272);

    private record Fixture(String name, int computerId, Path resultFile) {}

    private static MinecraftServer server;
    private static ServerLevel overworld;
    private static ServerLevel nether;
    private static Fixture noHost;
    private static Fixture normalNear;
    private static Fixture normalFar;
    private static Fixture enderRemote;
    private static long hostsStartedTick;
    private static long startTick;
    private static boolean locatorsBooted;
    private static boolean complete;

    private SkyforgeWaveC17GpsInfrastructureAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC17GpsInfrastructureAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC17GpsInfrastructureAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            requireLoaded("computercraft");

            server = event.getServer();
            overworld = server.overworld();
            nether = server.getLevel(Level.NETHER);
            if (nether == null) {
                throw new IllegalStateException("Nether level is required for the no-host isolation proof");
            }

            // Boot every GPS host first. Locators are deliberately withheld for a fixed number of
            // server ticks so CraftOS and the stock gps host program can establish their modem
            // listeners before any locate request is allowed to begin. This is a black-box
            // readiness barrier: Skyforge still never calls ComputerCraft GPS/network internals.
            createComputer(server, overworld, "normal-host-a", NORMAL_HOST_A,
                    "wireless_modem_normal", hostScript(NORMAL_HOST_A));
            createComputer(server, overworld, "normal-host-b", NORMAL_HOST_B,
                    "wireless_modem_normal", hostScript(NORMAL_HOST_B));
            createComputer(server, overworld, "normal-host-c", NORMAL_HOST_C,
                    "wireless_modem_normal", hostScript(NORMAL_HOST_C));

            createComputer(server, overworld, "ender-host-a", ENDER_HOST_A,
                    "wireless_modem_advanced", hostScript(ENDER_HOST_A));
            createComputer(server, overworld, "ender-host-b", ENDER_HOST_B,
                    "wireless_modem_advanced", hostScript(ENDER_HOST_B));
            createComputer(server, overworld, "ender-host-c", ENDER_HOST_C,
                    "wireless_modem_advanced", hostScript(ENDER_HOST_C));

            hostsStartedTick = overworld.getGameTime();
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Wave C17 GPS hosts booted; delaying locators by "
                            + HOST_WARMUP_TICKS
                            + " server ticks");
        } catch (Exception exception) {
            fail("fixture bootstrap failed", exception);
        }
    }

    private static void bootLocators() throws IOException, ReflectiveOperationException {
        // The no-host locator is isolated in the Nether. Every GPS host lives in the Overworld,
        // and ordinary Wireless Modems cannot cross dimensions.
        noHost = createComputer(
                server,
                nether,
                "no-host",
                NO_HOST_LOCATOR,
                "wireless_modem_normal",
                locatorScript());

        // The near locator is inside C16's accepted local envelope; the far locator is outside it.
        normalNear = createComputer(
                server,
                overworld,
                "normal-near",
                NORMAL_NEAR_LOCATOR,
                "wireless_modem_normal",
                locatorScript());
        normalFar = createComputer(
                server,
                overworld,
                "normal-far",
                NORMAL_FAR_LOCATOR,
                "wireless_modem_normal",
                locatorScript());

        // The Ender locator demonstrates C16's mature range bypass through stock gps.locate.
        enderRemote = createComputer(
                server,
                overworld,
                "ender-remote",
                ENDER_REMOTE_LOCATOR,
                "wireless_modem_advanced",
                locatorScript());

        startTick = overworld.getGameTime();
        locatorsBooted = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "Wave C17 real GPS locators booted after host warm-up noHost="
                        + noHost.computerId()
                        + " normalNear="
                        + normalNear.computerId()
                        + " normalFar="
                        + normalFar.computerId()
                        + " enderRemote="
                        + enderRemote.computerId());
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

    private static String hostScript(BlockPos position) {
        return """
                local modem = assert(peripheral.find("modem"), "wireless modem peripheral missing")
                assert(modem.isWireless(), "discovered modem is not wireless")
                shell.run("gps", "host", "%d", "%d", "%d")
                """.formatted(position.getX(), position.getY(), position.getZ());
    }

    private static String locatorScript() {
        return """
                local modem = assert(peripheral.find("modem"), "wireless modem peripheral missing")
                assert(modem.isWireless(), "discovered modem is not wireless")
                sleep(1.5)
                local x, y, z
                for attempt = 1, 3 do
                  x, y, z = gps.locate(2, false)
                  if x ~= nil then
                    break
                  end
                  if attempt < 3 then
                    sleep(0.5)
                  end
                end
                local found = x ~= nil

                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=PASS")
                f.writeLine("modemFound=true")
                f.writeLine("wireless=true")
                f.writeLine("found=" .. tostring(found))
                f.writeLine("x=" .. (found and tostring(x) or "NONE"))
                f.writeLine("y=" .. (found and tostring(y) or "NONE"))
                f.writeLine("z=" .. (found and tostring(z) or "NONE"))
                f.close()
                """.formatted(RESULT_FILE);
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
            throw new IllegalStateException("required C17 mod not loaded: " + modId);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || server == null || overworld == null) {
            return;
        }

        try {
            if (!locatorsBooted) {
                if (overworld.getGameTime() - hostsStartedTick < HOST_WARMUP_TICKS) {
                    return;
                }
                bootLocators();
                return;
            }

            Properties noHostResult = loadComplete(noHost);
            Properties normalNearResult = loadComplete(normalNear);
            Properties normalFarResult = loadComplete(normalFar);
            Properties enderRemoteResult = loadComplete(enderRemote);

            if (noHostResult != null
                    && normalNearResult != null
                    && normalFarResult != null
                    && enderRemoteResult != null) {
                evaluate(noHostResult, normalNearResult, normalFarResult, enderRemoteResult);
                return;
            }

            if (overworld.getGameTime() - startTick > TIMEOUT_TICKS) {
                fail("real CC GPS scripts did not complete before timeout", null);
            }
        } catch (Exception exception) {
            fail("result evaluation failed", exception);
        }
    }

    private static Properties loadComplete(Fixture fixture) throws IOException {
        if (!Files.isRegularFile(fixture.resultFile())) {
            return null;
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(fixture.resultFile())) {
            properties.load(input);
        }
        for (String key : new String[] {"status", "modemFound", "wireless", "found", "x", "y", "z"}) {
            if (properties.getProperty(key) == null) {
                return null;
            }
        }
        return properties;
    }

    private static void evaluate(
            Properties noHostResult,
            Properties normalNearResult,
            Properties normalFarResult,
            Properties enderRemoteResult) {
        assertBase(noHostResult, "no-host locator");
        assertBase(normalNearResult, "normal near locator");
        assertBase(normalFarResult, "normal far locator");
        assertBase(enderRemoteResult, "Ender remote locator");

        requireProperty(noHostResult, "found", "false", "GPS unexpectedly worked without hosts");
        requireProperty(normalFarResult, "found", "false", "ordinary GPS escaped the normal wireless envelope");

        assertLocated(normalNearResult, NORMAL_NEAR_LOCATOR, "normal near locator");
        assertLocated(enderRemoteResult, ENDER_REMOTE_LOCATOR, "Ender remote locator");

        complete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C17 PASS noHostFound="
                        + noHostResult.getProperty("found")
                        + " normalNearFound="
                        + normalNearResult.getProperty("found")
                        + " normalNear="
                        + coordinates(normalNearResult)
                        + " normalFarFound="
                        + normalFarResult.getProperty("found")
                        + " enderRemoteFound="
                        + enderRemoteResult.getProperty("found")
                        + " enderRemote="
                        + coordinates(enderRemoteResult));
    }

    private static void assertBase(Properties result, String label) {
        requireProperty(result, "status", "PASS", label + " script failed");
        requireProperty(result, "modemFound", "true", label + " did not discover modem");
        requireProperty(result, "wireless", "true", label + " modem was not wireless");
    }

    private static void assertLocated(Properties result, BlockPos expected, String label) {
        requireProperty(result, "found", "true", label + " could not establish GPS position");
        double x = Double.parseDouble(result.getProperty("x"));
        double y = Double.parseDouble(result.getProperty("y"));
        double z = Double.parseDouble(result.getProperty("z"));
        if (Math.abs(x - expected.getX()) > POSITION_TOLERANCE
                || Math.abs(y - expected.getY()) > POSITION_TOLERANCE
                || Math.abs(z - expected.getZ()) > POSITION_TOLERANCE) {
            throw new IllegalStateException(
                    label + " position mismatch expected=" + expected + " actual=" + x + "," + y + "," + z);
        }
    }

    private static String coordinates(Properties result) {
        return result.getProperty("x") + "," + result.getProperty("y") + "," + result.getProperty("z");
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
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C17 FAIL " + reason);
        if (cause == null) {
            throw new IllegalStateException("Wave C17 GPS-infrastructure acceptance failed: " + reason);
        }
        throw new IllegalStateException("Wave C17 GPS-infrastructure acceptance failed: " + reason, cause);
    }
}
