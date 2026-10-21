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
 * C19 black-box stock mining-turtle acceptance for the retained CC:Tweaked stack.
 *
 * <p>Tool equip, digging, movement, refuelling, and delivery are performed by a real CraftOS turtle
 * through the stock turtle Lua API. Skyforge fixture code only prepares deterministic vanilla
 * blocks/inventory/chunk state and observes results.
 */
final class SkyforgeWaveC19TurtleMiningAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC19TurtleMining";
    private static final String RESULT_FILE = "c19-result.properties";
    private static final String START_FILE = "c19-start.properties";
    private static final String BOUNDARY_PROGRESS_FILE = "c19-boundary-progress.properties";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC19TurtleMiningAcceptance.class.getName());

    private static final int MINE_DISTANCE = 96;
    private static final int RETURN_DISTANCE = MINE_DISTANCE;
    private static final int TOTAL_MOVES = MINE_DISTANCE + RETURN_DISTANCE;
    private static final int EXPECTED_DELIVERED = MINE_DISTANCE;
    private static final long TIMEOUT_TICKS = 4200L;
    private static final int BOUNDARY_ATTEMPT_DISTANCE = 48;
    private static final long BOUNDARY_STALL_TICKS = 240L;

    private static final BlockPos MINE_START = new BlockPos(0, 250, 0);
    private static final BlockPos HOME_BARREL_POS = new BlockPos(-1, 250, 0);
    private static final BlockPos BOUNDARY_START = new BlockPos(2047, 250, 0);

    private record Fixture(String name, int computerId, Path resultFile, Path startFile, Object computer) {}

    private static ServerLevel overworld;
    private static Fixture miner;
    private static Fixture boundary;
    private static long startTick;
    private static Long miningScriptStartTick;
    private static int boundaryLastMoves = -1;
    private static int boundaryLastDigs = -1;
    private static long boundaryLastProgressTick;
    private static boolean boundaryStalled;
    private static boolean complete;

    private SkyforgeWaveC19TurtleMiningAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC19TurtleMiningAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC19TurtleMiningAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            requireLoaded("computercraft");
            MinecraftServer server = event.getServer();
            overworld = server.overworld();

            prepareLoadedDeposit(overworld);
            Container barrel = prepareBarrel(overworld);

            miner = createTurtle(
                    server,
                    overworld,
                    "miner",
                    MINE_START,
                    miningScript());
            seedMiningInventory(miner, overworld, MINE_START, 3);
            invokeNoArgs(miner.computer(), "turnOn");

            prepareBoundaryDeposit(overworld);
            boundary = createTurtle(
                    server,
                    overworld,
                    "boundary-miner",
                    BOUNDARY_START,
                    boundaryScript());
            seedMiningInventory(boundary, overworld, BOUNDARY_START, 1);

            int boundaryChunkX = BOUNDARY_START.getX() >> 4;
            int boundaryChunkZ = BOUNDARY_START.getZ() >> 4;
            overworld.setChunkForced(boundaryChunkX, boundaryChunkZ, true);
            overworld.getChunk(boundaryChunkX, boundaryChunkZ);

            startTick = overworld.getGameTime();
            boundaryLastProgressTick = startTick;
            invokeNoArgs(boundary.computer(), "turnOn");

            if (barrel.getContainerSize() < 2) {
                throw new IllegalStateException("vanilla destination container is too small for C19 specimen");
            }

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Wave C19 mining turtles prepared minerId="
                            + miner.computerId()
                            + " boundaryId="
                            + boundary.computerId());
        } catch (Exception exception) {
            fail("fixture bootstrap failed", exception);
        }
    }

    private static void prepareLoadedDeposit(ServerLevel level) {
        int startChunk = HOME_BARREL_POS.getX() >> 4;
        int endChunk = (MINE_START.getX() + MINE_DISTANCE) >> 4;
        for (int chunkX = startChunk; chunkX <= endChunk; chunkX++) {
            level.setChunkForced(chunkX, 0, true);
            level.getChunk(chunkX, 0);
        }

        level.setBlockAndUpdate(MINE_START, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(MINE_START.below(), Blocks.AIR.defaultBlockState());
        for (int step = 1; step <= MINE_DISTANCE; step++) {
            BlockPos ore = MINE_START.offset(step, 0, 0);
            level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
            level.setBlockAndUpdate(ore.below(), Blocks.AIR.defaultBlockState());
        }
    }

    private static void prepareBoundaryDeposit(ServerLevel level) {
        level.setBlockAndUpdate(BOUNDARY_START, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(BOUNDARY_START.below(), Blocks.AIR.defaultBlockState());

        // These chunks are touched only to construct the deterministic deposit. They are not forced.
        // The only explicitly forced/ticking C19 boundary chunk is the turtle's starting chunk.
        for (int step = 1; step <= BOUNDARY_ATTEMPT_DISTANCE; step++) {
            BlockPos ore = BOUNDARY_START.offset(step, 0, 0);
            level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
            level.setBlockAndUpdate(ore.below(), Blocks.AIR.defaultBlockState());
        }
    }

    private static Container prepareBarrel(ServerLevel level) {
        level.setBlockAndUpdate(HOME_BARREL_POS, Blocks.BARREL.defaultBlockState());
        Object blockEntity = level.getBlockEntity(HOME_BARREL_POS);
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
            String startupScript)
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

        return new Fixture(name, computerId, resultFile, startFile, serverComputer);
    }

    private static void seedMiningInventory(
            Fixture fixture,
            ServerLevel level,
            BlockPos position,
            int coalCount) {
        Object blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof Container container)) {
            throw new IllegalStateException(fixture.name() + " turtle inventory unavailable");
        }

        container.clearContent();
        container.setItem(0, new ItemStack(Items.COAL, coalCount));
        container.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE, 1));
        container.setChanged();
    }

    private static String miningScript() {
        return """
                local start = assert(fs.open("%s", "w"))
                start.writeLine("started=true")
                start.close()

                turtle.select(1)
                assert(turtle.refuel(3), "could not refuel mining turtle")
                local fuelStart = turtle.getFuelLevel()

                turtle.select(2)
                assert(turtle.equipLeft(), "could not equip stock diamond pickaxe")

                local digs = 0
                local outbound = 0
                for i = 1, %d do
                  local dug, digErr = turtle.dig()
                  assert(dug, "dig " .. i .. " failed: " .. tostring(digErr))
                  digs = digs + 1

                  local moved, moveErr = turtle.forward()
                  assert(moved, "outbound move " .. i .. " failed: " .. tostring(moveErr))
                  outbound = outbound + 1
                end

                local returned = 0
                for i = 1, %d do
                  local moved, moveErr = turtle.back()
                  assert(moved, "return move " .. i .. " failed: " .. tostring(moveErr))
                  returned = returned + 1
                end

                turtle.turnLeft()
                turtle.turnLeft()

                local delivered = 0
                for slot = 1, 16 do
                  turtle.select(slot)
                  local count = turtle.getItemCount(slot)
                  if count > 0 then
                    local detail = turtle.getItemDetail(slot)
                    assert(detail ~= nil and detail.name == "minecraft:raw_iron",
                      "unexpected mined inventory item in slot " .. slot)
                    assert(turtle.drop(), "raw iron drop failed in slot " .. slot)
                    delivered = delivered + count
                  end
                end

                local fuelEnd = turtle.getFuelLevel()
                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=PASS")
                f.writeLine("digs=" .. digs)
                f.writeLine("outboundMoves=" .. outbound)
                f.writeLine("returnMoves=" .. returned)
                f.writeLine("fuelStart=" .. tostring(fuelStart))
                f.writeLine("fuelEnd=" .. tostring(fuelEnd))
                f.writeLine("delivered=" .. delivered)
                f.close()
                """.formatted(
                START_FILE,
                MINE_DISTANCE,
                RETURN_DISTANCE,
                RESULT_FILE);
    }

    private static String boundaryScript() {
        return """
                turtle.select(1)
                assert(turtle.refuel(1), "could not refuel boundary mining turtle")
                local fuelStart = turtle.getFuelLevel()

                turtle.select(2)
                assert(turtle.equipLeft(), "could not equip boundary stock diamond pickaxe")

                local digs = 0
                local moved = 0

                local function progress()
                  local p = assert(fs.open("%s", "w"))
                  p.writeLine("status=PROGRESS")
                  p.writeLine("digs=" .. digs)
                  p.writeLine("moved=" .. moved)
                  p.writeLine("fuelStart=" .. tostring(fuelStart))
                  p.writeLine("fuelCurrent=" .. tostring(turtle.getFuelLevel()))
                  p.close()
                end

                local function stopped(phase, err)
                  local f = assert(fs.open("%s", "w"))
                  f.writeLine("status=STOPPED")
                  f.writeLine("phase=" .. phase)
                  f.writeLine("digs=" .. digs)
                  f.writeLine("moved=" .. moved)
                  f.writeLine("error=" .. tostring(err))
                  f.writeLine("fuelStart=" .. tostring(fuelStart))
                  f.writeLine("fuelEnd=" .. tostring(turtle.getFuelLevel()))
                  f.close()
                end

                for i = 1, %d do
                  local dug, digErr = turtle.dig()
                  if not dug then
                    stopped("DIG", digErr)
                    return
                  end
                  digs = digs + 1

                  local ok, moveErr = turtle.forward()
                  if not ok then
                    stopped("MOVE", moveErr)
                    return
                  end
                  moved = moved + 1
                  progress()
                end

                local f = assert(fs.open("%s", "w"))
                f.writeLine("status=COMPLETED")
                f.writeLine("phase=NONE")
                f.writeLine("digs=" .. digs)
                f.writeLine("moved=" .. moved)
                f.writeLine("error=NONE")
                f.writeLine("fuelStart=" .. tostring(fuelStart))
                f.writeLine("fuelEnd=" .. tostring(turtle.getFuelLevel()))
                f.close()
                """.formatted(
                BOUNDARY_PROGRESS_FILE,
                RESULT_FILE,
                BOUNDARY_ATTEMPT_DISTANCE,
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
            throw new IllegalStateException("required C19 mod not loaded: " + modId);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || overworld == null || miner == null || boundary == null) {
            return;
        }

        try {
            long now = overworld.getGameTime();

            if (miningScriptStartTick == null && Files.isRegularFile(miner.startFile())) {
                miningScriptStartTick = now;
            }

            Properties boundaryProgress = observeBoundaryProgress(now);
            Properties miningResult = loadComplete(
                    miner,
                    new String[] {"status", "digs", "outboundMoves", "returnMoves", "fuelStart", "fuelEnd", "delivered"});
            Properties boundaryResult = loadComplete(
                    boundary,
                    new String[] {"status", "phase", "digs", "moved", "error", "fuelStart", "fuelEnd"});

            if (miningResult != null
                    && miningScriptStartTick != null
                    && (boundaryResult != null || boundaryStalled)) {
                evaluate(
                        miningResult,
                        boundaryResult,
                        boundaryProgress,
                        now - miningScriptStartTick);
                return;
            }

            if (now - startTick > TIMEOUT_TICKS) {
                fail("real CC mining-turtle scripts did not complete before timeout", null);
            }
        } catch (Exception exception) {
            fail("result evaluation failed", exception);
        }
    }

    private static Properties observeBoundaryProgress(long now) throws IOException {
        Path progressFile = boundary.resultFile().resolveSibling(BOUNDARY_PROGRESS_FILE);
        Properties progress = loadComplete(
                progressFile,
                new String[] {"status", "digs", "moved", "fuelStart", "fuelCurrent"});
        if (progress == null) {
            return null;
        }

        int moved = Integer.parseInt(progress.getProperty("moved"));
        int digs = Integer.parseInt(progress.getProperty("digs"));
        if (moved != boundaryLastMoves || digs != boundaryLastDigs) {
            boundaryLastMoves = moved;
            boundaryLastDigs = digs;
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
            Properties miningResult,
            Properties boundaryResult,
            Properties boundaryProgress,
            long elapsedTicks) {
        requireProperty(miningResult, "status", "PASS", "mining turtle script failed");
        requireProperty(
                miningResult,
                "digs",
                Integer.toString(MINE_DISTANCE),
                "mining dig count mismatch");
        requireProperty(
                miningResult,
                "outboundMoves",
                Integer.toString(MINE_DISTANCE),
                "mining outbound distance mismatch");
        requireProperty(
                miningResult,
                "returnMoves",
                Integer.toString(RETURN_DISTANCE),
                "mining return distance mismatch");
        requireProperty(
                miningResult,
                "delivered",
                Integer.toString(EXPECTED_DELIVERED),
                "mining Lua delivery count mismatch");

        int fuelStart = Integer.parseInt(miningResult.getProperty("fuelStart"));
        int fuelEnd = Integer.parseInt(miningResult.getProperty("fuelEnd"));
        if (fuelStart - fuelEnd != TOTAL_MOVES) {
            throw new IllegalStateException(
                    "mining fuel delta did not equal physical moves: start="
                            + fuelStart
                            + " end="
                            + fuelEnd
                            + " moves="
                            + TOTAL_MOVES);
        }

        Object barrelBlockEntity = overworld.getBlockEntity(HOME_BARREL_POS);
        if (!(barrelBlockEntity instanceof Container barrel)) {
            throw new IllegalStateException("home barrel disappeared before evaluation");
        }
        int barrelCargo = countItem(barrel, Items.RAW_IRON);
        if (barrelCargo != EXPECTED_DELIVERED) {
            throw new IllegalStateException(
                    "vanilla barrel raw-iron mismatch: expected="
                            + EXPECTED_DELIVERED
                            + " actual="
                            + barrelCargo);
        }

        for (int step = 1; step <= MINE_DISTANCE; step++) {
            BlockPos mined = MINE_START.offset(step, 0, 0);
            if (overworld.getBlockState(mined).is(Blocks.IRON_ORE)) {
                throw new IllegalStateException("accepted deposit cell remained unmined at " + mined);
            }
        }

        Object minerBlockEntity = overworld.getBlockEntity(MINE_START);
        if (minerBlockEntity == null
                || !minerBlockEntity.getClass().getName().contains("TurtleBlockEntity")) {
            throw new IllegalStateException("mining turtle did not physically return to its start position");
        }

        String boundaryOutcome;
        String boundaryPhase;
        int boundaryDigs;
        int boundaryMoves;
        int boundaryFuelStart;
        int boundaryFuelEnd;
        String boundaryError;

        if (boundaryResult != null) {
            boundaryOutcome = boundaryResult.getProperty("status");
            boundaryPhase = boundaryResult.getProperty("phase");
            boundaryDigs = Integer.parseInt(boundaryResult.getProperty("digs"));
            boundaryMoves = Integer.parseInt(boundaryResult.getProperty("moved"));
            boundaryFuelStart = Integer.parseInt(boundaryResult.getProperty("fuelStart"));
            boundaryFuelEnd = Integer.parseInt(boundaryResult.getProperty("fuelEnd"));
            boundaryError = boundaryResult.getProperty("error");

            if ("COMPLETED".equals(boundaryOutcome)
                    || boundaryMoves >= BOUNDARY_ATTEMPT_DISTANCE) {
                throw new IllegalStateException(
                        "boundary mining turtle autonomously completed the unforced route: moved="
                                + boundaryMoves
                                + " digs="
                                + boundaryDigs);
            }
            if (!"STOPPED".equals(boundaryOutcome)) {
                throw new IllegalStateException(
                        "unexpected boundary mining result status=" + boundaryOutcome);
            }
            if (!"MOVE".equals(boundaryPhase)
                    || boundaryError == null
                    || !boundaryError.contains("Cannot leave loaded world")) {
                throw new IllegalStateException(
                        "boundary mining turtle stopped for a non-loading reason: phase="
                                + boundaryPhase
                                + " error="
                                + boundaryError);
            }
        } else {
            if (!boundaryStalled || boundaryProgress == null) {
                throw new IllegalStateException(
                        "boundary mining turtle produced neither a bounded stop nor a measured stall");
            }
            boundaryOutcome = "STALLED";
            boundaryPhase = "NO_TICK_PROGRESS";
            boundaryDigs = Integer.parseInt(boundaryProgress.getProperty("digs"));
            boundaryMoves = Integer.parseInt(boundaryProgress.getProperty("moved"));
            boundaryFuelStart = Integer.parseInt(boundaryProgress.getProperty("fuelStart"));
            boundaryFuelEnd = Integer.parseInt(boundaryProgress.getProperty("fuelCurrent"));
            boundaryError = "NO_TICK_PROGRESS";
        }

        if (boundaryMoves <= 0 || boundaryMoves >= BOUNDARY_ATTEMPT_DISTANCE) {
            throw new IllegalStateException(
                    "boundary mining progress was outside expected bounded envelope: moved="
                            + boundaryMoves);
        }
        if (boundaryFuelStart - boundaryFuelEnd != boundaryMoves) {
            throw new IllegalStateException(
                    "boundary mining fuel delta did not equal successful moves: start="
                            + boundaryFuelStart
                            + " end="
                            + boundaryFuelEnd
                            + " moved="
                            + boundaryMoves);
        }
        if ("STALLED".equals(boundaryOutcome)) {
            if (boundaryDigs != boundaryMoves) {
                throw new IllegalStateException(
                        "stalled mining progress did not preserve one successful dig per move: digs="
                                + boundaryDigs
                                + " moved="
                                + boundaryMoves);
            }
        } else if (boundaryDigs < boundaryMoves || boundaryDigs > boundaryMoves + 1) {
            throw new IllegalStateException(
                    "stopped mining progress was inconsistent with dig-before-move sequencing: digs="
                            + boundaryDigs
                            + " moved="
                            + boundaryMoves);
        }

        complete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C19 PASS digs="
                        + MINE_DISTANCE
                        + " outboundMoves="
                        + MINE_DISTANCE
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
                        + " boundaryPhase="
                        + boundaryPhase
                        + " boundaryDigs="
                        + boundaryDigs
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
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C19 FAIL " + reason);
        if (cause == null) {
            throw new IllegalStateException("Wave C19 turtle-mining acceptance failed: " + reason);
        }
        throw new IllegalStateException("Wave C19 turtle-mining acceptance failed: " + reason, cause);
    }
}
