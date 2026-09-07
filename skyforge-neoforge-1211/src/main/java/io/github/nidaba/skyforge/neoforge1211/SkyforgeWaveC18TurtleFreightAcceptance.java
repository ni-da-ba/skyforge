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
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * C18 black-box turtle freight acceptance for the retained CC:Tweaked stack.
 *
 * <p>Movement, refuelling, and delivery are performed by a real CraftOS turtle through the stock
 * turtle Lua API. Skyforge fixture code only prepares Minecraft blocks/inventory/chunk state and
 * observes results.
 */
final class SkyforgeWaveC18TurtleFreightAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC18TurtleFreight";
    private static final String RESULT_FILE = "c18-result.properties";
    private static final String START_FILE = "c18-start.properties";
    private static final String BOUNDARY_PROGRESS_FILE = "c18-boundary-progress.properties";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC18TurtleFreightAcceptance.class.getName());

    private static final int FREIGHT_DISTANCE = 64;
    private static final int RETURN_DISTANCE = 64;
    private static final int TOTAL_MOVES = FREIGHT_DISTANCE + RETURN_DISTANCE;
    private static final int CARGO_STACKS = 15;
    private static final int CARGO_PER_STACK = 64;
    private static final int EXPECTED_DELIVERED = CARGO_STACKS * CARGO_PER_STACK;
    private static final long TIMEOUT_TICKS = 1800L;
    private static final int BOUNDARY_ATTEMPT_DISTANCE = 48;
    private static final long BOUNDARY_STALL_TICKS = 240L;

    private static final BlockPos FREIGHT_START = new BlockPos(0, 250, 0);
    private static final BlockPos BARREL_POS = new BlockPos(FREIGHT_DISTANCE + 1, 250, 0);

    // Deliberately far from spawn and the freight corridor. The turtle starts at the eastern edge
    // of one explicitly forced chunk, then attempts a 48-block route through otherwise unforced sky.
    private static final BlockPos BOUNDARY_START = new BlockPos(2047, 250, 0);

    private record Fixture(String name, int computerId, Path resultFile, Path startFile, Object computer) {}

    private static ServerLevel overworld;
    private static Fixture freight;
    private static Fixture boundary;
    private static long startTick;
    private static Long freightScriptStartTick;
    private static int boundaryLastMoves = -1;
    private static long boundaryLastProgressTick;
    private static boolean boundaryStalled;
    private static boolean complete;

    private SkyforgeWaveC18TurtleFreightAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC18TurtleFreightAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC18TurtleFreightAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            requireLoaded("computercraft");
            MinecraftServer server = event.getServer();
            overworld = server.overworld();

            prepareVoidCorridor(overworld);
            Container barrel = prepareBarrel(overworld);

            freight = createTurtle(
                    server,
                    overworld,
                    "freight",
                    FREIGHT_START,
                    freightScript(),
                    true);
            seedFreightInventory(freight, overworld);

            // Re-turn-on after inventory seeding. turnOn() is idempotent and ensures the startup
            // script sees the seeded fuel/cargo on its first execution window.
            invokeNoArgs(freight.computer(), "turnOn");

            // The boundary specimen keeps only the turtle's starting chunk explicitly forced.
            // Minecraft may transiently/ticket-load neighbours around that chunk; the measurement is
            // whether the real turtle can sustain a 48-block route once it leaves the externally
            // ticking envelope, not whether an artificial adjacent-unloaded state can be constructed.
            boundary = createTurtle(
                    server,
                    overworld,
                    "boundary",
                    BOUNDARY_START,
                    boundaryScript(),
                    false);
            seedBoundaryFuel(boundary, overworld);

            int boundaryChunkX = BOUNDARY_START.getX() >> 4;
            int boundaryChunkZ = BOUNDARY_START.getZ() >> 4;
            overworld.setChunkForced(boundaryChunkX, boundaryChunkZ, true);
            overworld.getChunk(boundaryChunkX, boundaryChunkZ);

            startTick = overworld.getGameTime();
            boundaryLastProgressTick = startTick;
            invokeNoArgs(boundary.computer(), "turnOn");
            if (barrel.getContainerSize() < CARGO_STACKS) {
                throw new IllegalStateException("vanilla destination container is too small for C18 cargo");
            }

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Wave C18 turtles prepared freightId="
                            + freight.computerId()
                            + " boundaryId="
                            + boundary.computerId());
        } catch (Exception exception) {
            fail("fixture bootstrap failed", exception);
        }
    }

    private static void prepareVoidCorridor(ServerLevel level) {
        int startChunk = FREIGHT_START.getX() >> 4;
        int endChunk = BARREL_POS.getX() >> 4;
        for (int chunkX = startChunk; chunkX <= endChunk; chunkX++) {
            level.setChunkForced(chunkX, 0, true);
            level.getChunk(chunkX, 0);
        }

        for (int x = FREIGHT_START.getX(); x <= BARREL_POS.getX(); x++) {
            BlockPos path = new BlockPos(x, FREIGHT_START.getY(), FREIGHT_START.getZ());
            level.setBlockAndUpdate(path, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(path.below(), Blocks.AIR.defaultBlockState());
        }
    }

    private static Container prepareBarrel(ServerLevel level) {
        level.setBlockAndUpdate(BARREL_POS, Blocks.BARREL.defaultBlockState());
        Object blockEntity = level.getBlockEntity(BARREL_POS);
        if (!(blockEntity instanceof Container container)) {
            throw new IllegalStateException("real vanilla barrel container missing");
        }
        container.clearContent();
        container.setChanged();
        return container;
    }

    private static Fixture createTurtle(
            MinecraftServer server,
            ServerLevel level,
            String name,
            BlockPos position,
            String startupScript,
            boolean turnOn)
            throws IOException, ReflectiveOperationException {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        level.getChunk(chunkX, chunkZ);

        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(position.below(), Blocks.AIR.defaultBlockState());

        Block turtleBlock = requireBlock("computercraft", "turtle_normal");
        BlockState turtleState = turtleBlock.defaultBlockState();
        if (turtleState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            turtleState = turtleState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        }
        level.setBlockAndUpdate(position, turtleState);

        Object blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof Container)) {
            throw new IllegalStateException("real ComputerCraft turtle container missing for " + name);
        }
        if (blockEntity == null || !blockEntity.getClass().getName().contains("TurtleBlockEntity")) {
            throw new IllegalStateException("real ComputerCraft turtle block entity missing for " + name);
        }

        Object serverComputer = invokeNoArgs(blockEntity, "createServerComputer");
        int computerId = ((Number) invokeNoArgs(serverComputer, "getID")).intValue();

        Path computerDirectory = server.getWorldPath(new LevelResource("computercraft"))
                .resolve("computer")
                .resolve(Integer.toString(computerId));
        Files.createDirectories(computerDirectory);
        Path resultFile = computerDirectory.resolve(RESULT_FILE);
        Path startFile = computerDirectory.resolve(START_FILE);
        Files.deleteIfExists(resultFile);
        Files.deleteIfExists(startFile);
        Files.deleteIfExists(computerDirectory.resolve(BOUNDARY_PROGRESS_FILE));
        Files.writeString(computerDirectory.resolve("startup.lua"), startupScript);

        if (turnOn) {
            invokeNoArgs(serverComputer, "turnOn");
        }
        return new Fixture(name, computerId, resultFile, startFile, serverComputer);
    }

    private static void seedFreightInventory(Fixture fixture, ServerLevel level) {
        Object blockEntity = level.getBlockEntity(FREIGHT_START);
        if (!(blockEntity instanceof Container container)) {
            throw new IllegalStateException("freight turtle inventory unavailable");
        }

        container.clearContent();
        container.setItem(0, new ItemStack(Items.COAL, 2));
        for (int slot = 1; slot < 16; slot++) {
            container.setItem(slot, new ItemStack(Items.COBBLESTONE, CARGO_PER_STACK));
        }
        container.setChanged();
    }

    private static void seedBoundaryFuel(Fixture fixture, ServerLevel level) {
        Object blockEntity = level.getBlockEntity(BOUNDARY_START);
        if (!(blockEntity instanceof Container container)) {
            throw new IllegalStateException("boundary turtle inventory unavailable");
        }
        container.clearContent();
        container.setItem(0, new ItemStack(Items.COAL, 1));
        container.setChanged();
    }

    private static String freightScript() {
        return """
                local start = assert(fs.open("%s", "w"))
                start.writeLine("started=true")
                start.close()

                turtle.select(1)
                assert(turtle.refuel(2), "could not refuel freight turtle")
                local fuelStart = turtle.getFuelLevel()

                local outbound = 0
                for i = 1, %d do
                  local ok, err = turtle.forward()
                  assert(ok, "outbound move " .. i .. " failed: " .. tostring(err))
                  outbound = outbound + 1
                end

                local delivered = 0
                for slot = 2, 16 do
                  turtle.select(slot)
                  local count = turtle.getItemCount(slot)
                  assert(count == %d, "cargo stack changed before delivery in slot " .. slot)
                  assert(turtle.drop(), "cargo drop failed in slot " .. slot)
                  delivered = delivered + count
                end

                local returned = 0
                for i = 1, %d do
                  local ok, err = turtle.back()
                  assert(ok, "return move " .. i .. " failed: " .. tostring(err))
                  returned = returned + 1
                end

                local fuelEnd = turtle.getFuelLevel()
                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=PASS")
                f.writeLine("outboundMoves=" .. outbound)
                f.writeLine("returnMoves=" .. returned)
                f.writeLine("fuelStart=" .. tostring(fuelStart))
                f.writeLine("fuelEnd=" .. tostring(fuelEnd))
                f.writeLine("delivered=" .. delivered)
                f.close()
                """.formatted(
                START_FILE,
                FREIGHT_DISTANCE,
                CARGO_PER_STACK,
                RETURN_DISTANCE,
                RESULT_FILE);
    }

    private static String boundaryScript() {
        return """
                turtle.select(1)
                assert(turtle.refuel(1), "could not refuel boundary turtle")
                local fuelStart = turtle.getFuelLevel()
                local moved = 0

                local function progress()
                  local p = assert(fs.open("%s", "w"))
                  p.writeLine("status=PROGRESS")
                  p.writeLine("moved=" .. moved)
                  p.writeLine("fuelStart=" .. tostring(fuelStart))
                  p.writeLine("fuelCurrent=" .. tostring(turtle.getFuelLevel()))
                  p.close()
                end

                for i = 1, %d do
                  local ok, err = turtle.forward()
                  if not ok then
                    local f = assert(fs.open("%s", "w"))
                    f.writeLine("status=STOPPED")
                    f.writeLine("moved=" .. moved)
                    f.writeLine("error=" .. tostring(err))
                    f.writeLine("fuelStart=" .. tostring(fuelStart))
                    f.writeLine("fuelEnd=" .. tostring(turtle.getFuelLevel()))
                    f.close()
                    return
                  end
                  moved = moved + 1
                  progress()
                end

                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=COMPLETED")
                f.writeLine("moved=" .. moved)
                f.writeLine("error=NONE")
                f.writeLine("fuelStart=" .. tostring(fuelStart))
                f.writeLine("fuelEnd=" .. tostring(turtle.getFuelLevel()))
                f.close()
                """.formatted(
                BOUNDARY_PROGRESS_FILE,
                BOUNDARY_ATTEMPT_DISTANCE,
                RESULT_FILE,
                RESULT_FILE);
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
            throw new IllegalStateException("required C18 mod not loaded: " + modId);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || overworld == null || freight == null || boundary == null) {
            return;
        }

        try {
            long now = overworld.getGameTime();

            if (freightScriptStartTick == null && Files.isRegularFile(freight.startFile())) {
                freightScriptStartTick = now;
            }

            Properties boundaryProgress = observeBoundaryProgress(now);
            Properties freightResult = loadComplete(
                    freight,
                    new String[] {"status", "outboundMoves", "returnMoves", "fuelStart", "fuelEnd", "delivered"});
            Properties boundaryResult = loadComplete(
                    boundary,
                    new String[] {"status", "moved", "error", "fuelStart", "fuelEnd"});

            if (freightResult != null
                    && freightScriptStartTick != null
                    && (boundaryResult != null || boundaryStalled)) {
                evaluate(
                        freightResult,
                        boundaryResult,
                        boundaryProgress,
                        now - freightScriptStartTick);
                return;
            }

            if (now - startTick > TIMEOUT_TICKS) {
                fail("real CC turtle scripts did not complete before timeout", null);
            }
        } catch (Exception exception) {
            fail("result evaluation failed", exception);
        }
    }

    private static Properties observeBoundaryProgress(long now) throws IOException {
        Path progressFile = boundary.resultFile().resolveSibling(BOUNDARY_PROGRESS_FILE);
        Properties progress = loadComplete(
                progressFile,
                new String[] {"status", "moved", "fuelStart", "fuelCurrent"});
        if (progress == null) {
            return null;
        }

        int moved = Integer.parseInt(progress.getProperty("moved"));
        if (moved != boundaryLastMoves) {
            boundaryLastMoves = moved;
            boundaryLastProgressTick = now;
        } else if (moved > 0
                && moved < BOUNDARY_ATTEMPT_DISTANCE
                && now - boundaryLastProgressTick > BOUNDARY_STALL_TICKS) {
            boundaryStalled = true;
        }
        return progress;
    }

    private static Properties loadComplete(Path file, String[] required) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        for (String key : required) {
            if (properties.getProperty(key) == null) {
                return null;
            }
        }
        return properties;
    }

    private static Properties loadComplete(Fixture fixture, String[] required) throws IOException {
        return loadComplete(fixture.resultFile(), required);
    }

    private static void evaluate(
            Properties freightResult,
            Properties boundaryResult,
            Properties boundaryProgress,
            long elapsedTicks) {
        requireProperty(freightResult, "status", "PASS", "freight turtle script failed");
        requireProperty(
                freightResult,
                "outboundMoves",
                Integer.toString(FREIGHT_DISTANCE),
                "freight outbound distance mismatch");
        requireProperty(
                freightResult,
                "returnMoves",
                Integer.toString(RETURN_DISTANCE),
                "freight return distance mismatch");
        requireProperty(
                freightResult,
                "delivered",
                Integer.toString(EXPECTED_DELIVERED),
                "freight Lua delivery count mismatch");

        int fuelStart = Integer.parseInt(freightResult.getProperty("fuelStart"));
        int fuelEnd = Integer.parseInt(freightResult.getProperty("fuelEnd"));
        if (fuelStart - fuelEnd != TOTAL_MOVES) {
            throw new IllegalStateException(
                    "freight fuel delta did not equal physical moves: start="
                            + fuelStart
                            + " end="
                            + fuelEnd
                            + " moves="
                            + TOTAL_MOVES);
        }

        Object barrelBlockEntity = overworld.getBlockEntity(BARREL_POS);
        if (!(barrelBlockEntity instanceof Container barrel)) {
            throw new IllegalStateException("destination barrel disappeared before evaluation");
        }
        int barrelCargo = countItem(barrel, Items.COBBLESTONE);
        if (barrelCargo != EXPECTED_DELIVERED) {
            throw new IllegalStateException(
                    "vanilla barrel cargo mismatch: expected="
                            + EXPECTED_DELIVERED
                            + " actual="
                            + barrelCargo);
        }

        Object freightBlockEntity = overworld.getBlockEntity(FREIGHT_START);
        if (freightBlockEntity == null
                || !freightBlockEntity.getClass().getName().contains("TurtleBlockEntity")) {
            throw new IllegalStateException("freight turtle did not physically return to its start position");
        }

        String boundaryOutcome;
        int boundaryMoves;
        int boundaryFuelStart;
        int boundaryFuelEnd;
        String boundaryError;

        if (boundaryResult != null) {
            boundaryOutcome = boundaryResult.getProperty("status");
            boundaryMoves = Integer.parseInt(boundaryResult.getProperty("moved"));
            boundaryFuelStart = Integer.parseInt(boundaryResult.getProperty("fuelStart"));
            boundaryFuelEnd = Integer.parseInt(boundaryResult.getProperty("fuelEnd"));
            boundaryError = boundaryResult.getProperty("error");

            if ("COMPLETED".equals(boundaryOutcome)
                    || boundaryMoves >= BOUNDARY_ATTEMPT_DISTANCE) {
                throw new IllegalStateException(
                        "boundary turtle autonomously completed the unforced route: moved="
                                + boundaryMoves);
            }
            if (!"STOPPED".equals(boundaryOutcome)) {
                throw new IllegalStateException(
                        "unexpected boundary turtle result status=" + boundaryOutcome);
            }
            if (boundaryError == null || !boundaryError.contains("Cannot leave loaded world")) {
                throw new IllegalStateException(
                        "boundary turtle stopped for a non-loading reason: error=" + boundaryError);
            }
        } else {
            if (!boundaryStalled || boundaryProgress == null) {
                throw new IllegalStateException(
                        "boundary turtle produced neither a bounded stop nor a measured stall");
            }
            boundaryOutcome = "STALLED";
            boundaryMoves = Integer.parseInt(boundaryProgress.getProperty("moved"));
            boundaryFuelStart = Integer.parseInt(boundaryProgress.getProperty("fuelStart"));
            boundaryFuelEnd = Integer.parseInt(boundaryProgress.getProperty("fuelCurrent"));
            boundaryError = "NO_TICK_PROGRESS";
        }

        if (boundaryMoves <= 0 || boundaryMoves >= BOUNDARY_ATTEMPT_DISTANCE) {
            throw new IllegalStateException(
                    "boundary turtle progress was outside the expected bounded envelope: moved="
                            + boundaryMoves);
        }
        if (boundaryFuelStart - boundaryFuelEnd != boundaryMoves) {
            throw new IllegalStateException(
                    "boundary fuel delta did not equal successful moves: start="
                            + boundaryFuelStart
                            + " end="
                            + boundaryFuelEnd
                            + " moved="
                            + boundaryMoves);
        }

        complete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C18 PASS outboundMoves="
                        + FREIGHT_DISTANCE
                        + " returnMoves="
                        + RETURN_DISTANCE
                        + " fuelStart="
                        + fuelStart
                        + " fuelEnd="
                        + fuelEnd
                        + " delivered="
                        + barrelCargo
                        + " elapsedTicks="
                        + elapsedTicks
                        + " boundaryOutcome="
                        + boundaryOutcome
                        + " boundaryMoves="
                        + boundaryMoves
                        + " boundaryFuelStart="
                        + boundaryFuelStart
                        + " boundaryFuelEnd="
                        + boundaryFuelEnd
                        + " boundaryError="
                        + boundaryError);
    }

    private static int countItem(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void requireProperty(
            Properties properties, String key, String expected, String reason) {
        String actual = properties.getProperty(key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    reason + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void fail(String reason, Throwable cause) {
        complete = true;
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C18 FAIL " + reason);
        if (cause == null) {
            throw new IllegalStateException("Wave C18 turtle-freight acceptance failed: " + reason);
        }
        throw new IllegalStateException("Wave C18 turtle-freight acceptance failed: " + reason, cause);
    }
}
