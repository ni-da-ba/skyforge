package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dedicated-server half of the WBY-INT-0002 baseline telemetry capture.
 *
 * <p>The fixture reuses the exact W1-A server stack, assembles one stationary Sable body under a
 * finite acceptance ticket, warms the server, then records a bounded distribution of real server
 * tick durations plus memory and chunk/sublevel counts. It does not mutate production behavior.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeWbyWave1TelemetryServerAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1TelemetryServer";

    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> TELEMETRY_TICKET = TicketType.create(
            "skyforge_wby_wave1_telemetry",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;
    private static final int WARMUP_TICKS = 100;
    private static final int SAMPLE_TICKS = 300;
    private static final int TIMEOUT_TICKS = 800;

    private static final List<Long> tickNanos = new ArrayList<>();
    private static final List<Long> rollingAverageTickNanos = new ArrayList<>();
    private static final List<Long> heapUsedBytes = new ArrayList<>();
    private static final List<Long> loadedChunks = new ArrayList<>();
    private static final List<Long> forcedChunks = new ArrayList<>();
    private static final List<Long> sableSublevels = new ArrayList<>();

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static ChunkPos targetChunk;
    private static UUID bodyId;
    private static boolean previousPhysicsPaused;
    private static boolean physicsPauseChanged;
    private static boolean fixtureReady;
    private static boolean complete;
    private static long firstTick = Long.MIN_VALUE;
    private static long tickStartNanos;
    private static int warmupTicks;

    private SkyforgeWbyWave1TelemetryServerAcceptance() {}

    @SubscribeEvent
    static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!enabled() || complete || !fixtureReady || warmupTicks < WARMUP_TICKS) {
            return;
        }
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled() || complete) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
            firstTick = level.getGameTime();
        }
        if (level.getGameTime() - firstTick > TIMEOUT_TICKS) {
            fail(event, "WBY Wave 1 server telemetry timed out; fixtureReady="
                    + fixtureReady + ", samples=" + tickNanos.size());
            return;
        }

        try {
            if (!fixtureReady) {
                assembleFixture();
                return;
            }
            if (warmupTicks < WARMUP_TICKS) {
                warmupTicks++;
                return;
            }

            long now = System.nanoTime();
            if (tickStartNanos <= 0L || now < tickStartNanos) {
                return;
            }
            long elapsed = now - tickStartNanos;
            tickStartNanos = 0L;

            long rolling = Math.max(0L, event.getServer().getAverageTickTimeNanos());
            long heap = Math.max(0L, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            long loaded = Math.max(0, level.getChunkSource().getLoadedChunksCount());
            long forced = Math.max(0, level.getForcedChunks().size());
            long sublevels = currentSubLevelCount(container);

            tickNanos.add(elapsed);
            rollingAverageTickNanos.add(rolling);
            heapUsedBytes.add(heap);
            loadedChunks.add(loaded);
            forcedChunks.add(forced);
            sableSublevels.add(sublevels);

            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "wbyWave1.serverTickNanos", elapsed);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "wbyWave1.serverRollingAverageTickNanos", rolling);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "wbyWave1.serverHeapUsedBytes", heap);
            SkyforgeRuntimePerformanceMetrics.recordSample("wbyWave1.serverLoadedChunks", loaded);
            SkyforgeRuntimePerformanceMetrics.recordSample("wbyWave1.serverForcedChunks", forced);
            SkyforgeRuntimePerformanceMetrics.recordSample("wbyWave1.serverSableSublevels", sublevels);

            if (tickNanos.size() >= SAMPLE_TICKS) {
                complete(event);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 server telemetry failed: " + failure);
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static void assembleFixture() throws ReflectiveOperationException {
        requireServerMods();
        BlockPos bodyMin = new BlockPos(8, 120, 8);
        BlockPos bodyMax = new BlockPos(11, 122, 11);
        BlockPos assemblerPos = new BlockPos(9, 123, 9);
        BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

        targetChunk = new ChunkPos(bodyMin);
        level.getChunkSource().addRegionTicket(
                TELEMETRY_TICKET,
                targetChunk,
                TICKET_DISTANCE,
                targetChunk);
        level.getChunk(targetChunk.x, targetChunk.z);

        container = requireServerSubLevelContainer(level);
        Set<UUID> before = currentSubLevelIds(container);

        for (int x = bodyMin.getX() - 2; x <= bodyMax.getX() + 2; x++) {
            for (int y = bodyMin.getY() - 2; y <= assemblerPos.getY() + 2; y++) {
                for (int z = bodyMin.getZ() - 2; z <= bodyMax.getZ() + 2; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = bodyMin.getX(); x <= bodyMax.getX(); x++) {
            for (int y = bodyMin.getY(); y <= bodyMax.getY(); y++) {
                for (int z = bodyMin.getZ(); z <= bodyMax.getZ(); z++) {
                    level.setBlock(
                            new BlockPos(x, y, z),
                            y == bodyMax.getY()
                                    ? Blocks.GOLD_BLOCK.defaultBlockState()
                                    : Blocks.RED_WOOL.defaultBlockState(),
                            3);
                }
            }
        }

        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        level.setBlock(assemblerPos, assemblerState, 3);
        addFixtureGlue(bodyMin, glueMax);

        BlockEntity assembler = level.getBlockEntity(assemblerPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY telemetry Physics Assembler unavailable at " + assemblerPos);
        }
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

        Set<UUID> after = currentSubLevelIds(container);
        LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
        created.removeAll(before);
        if (created.size() != 1) {
            throw new IllegalStateException("expected exactly one telemetry Sable body, created=" + created);
        }
        bodyId = created.iterator().next();

        physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        previousPhysicsPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (!previousPhysicsPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
            physicsPauseChanged = true;
        }
        fixtureReady = true;
    }

    private static void complete(ServerTickEvent.Post event) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("telemetryProfile", "W1-A");
        evidence.put("dedicatedServer", !event.getServer().isSingleplayer());
        evidence.put("bodyId", bodyId);
        evidence.put("sampleTicks", tickNanos.size());
        evidence.put("warmupTicks", warmupTicks);
        evidence.put("serverMsptMedian", nanosToMillis(percentile(tickNanos, 50)));
        evidence.put("serverMsptP95", nanosToMillis(percentile(tickNanos, 95)));
        evidence.put("serverRollingMsptMedian", nanosToMillis(percentile(rollingAverageTickNanos, 50)));
        evidence.put("serverRollingMsptP95", nanosToMillis(percentile(rollingAverageTickNanos, 95)));
        evidence.put("serverHeapUsedMedianBytes", percentile(heapUsedBytes, 50));
        evidence.put("serverHeapUsedP95Bytes", percentile(heapUsedBytes, 95));
        evidence.put("loadedChunksMedian", percentile(loadedChunks, 50));
        evidence.put("loadedChunksMax", maximum(loadedChunks));
        evidence.put("forcedChunksMedian", percentile(forcedChunks, 50));
        evidence.put("forcedChunksMax", maximum(forcedChunks));
        evidence.put("sableSublevelsMedian", percentile(sableSublevels, 50));
        evidence.put("sableSublevelsMax", maximum(sableSublevels));
        evidence.put("serverTelemetryQualified", true);

        restoreFixtureState();
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(event.getServer(), evidence);
    }

    private static long percentile(List<Long> values, int p) {
        ArrayList<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(sorted, p);
    }

    private static long maximum(List<Long> values) {
        long max = 0L;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static Object requireServerSubLevelContainer(ServerLevel serverLevel)
            throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(serverLevel);
        if (value == null) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        }
        return value;
    }

    private static Set<UUID> currentSubLevelIds(Object value) throws ReflectiveOperationException {
        Object subLevelsValue = publicMethod(value, "getAllSubLevels").invoke(value);
        if (!(subLevelsValue instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (Object subLevel : subLevels) {
            Object id = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
            if (id instanceof UUID uuid) {
                ids.add(uuid);
            }
        }
        return ids;
    }

    private static long currentSubLevelCount(Object value) throws ReflectiveOperationException {
        Object subLevelsValue = publicMethod(value, "getAllSubLevels").invoke(value);
        if (subLevelsValue instanceof List<?> subLevels) {
            return subLevels.size();
        }
        throw new IllegalStateException("Sable getAllSubLevels did not return a List");
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY telemetry Super Glue fixture");
        }
    }

    private static void requireServerMods() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY telemetry server mod not loaded: " + modId);
            }
        }
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required WBY telemetry block is not registered: " + id);
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
            BlockState state,
            Property<T> property,
            String name,
            String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static void restoreFixtureState() {
        if (physicsSystem != null && physicsPauseChanged) {
            try {
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(
                        physicsSystem, previousPhysicsPaused);
            } catch (ReflectiveOperationException ignored) {
                // Bounded server process is terminating after evidence capture.
            }
        }
        if (level != null && targetChunk != null) {
            level.getChunkSource().removeRegionTicket(
                    TELEMETRY_TICKET,
                    targetChunk,
                    TICKET_DISTANCE,
                    targetChunk);
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
