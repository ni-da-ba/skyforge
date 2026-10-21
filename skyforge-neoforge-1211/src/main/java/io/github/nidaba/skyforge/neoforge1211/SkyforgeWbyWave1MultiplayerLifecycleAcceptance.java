package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dedicated-server fixture for WBY-INT-0002 multiplayer remote-craft qualification.
 *
 * <p>Two real clients must be connected. The server assembles one stationary Sable body 128
 * blocks from the observer and continuously holds the second player beside that body. The observer
 * client independently proves that the same remote scene remains visible through SSRD/DH.
 */
final class SkyforgeWbyWave1MultiplayerLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1Multiplayer";
    static final String OBSERVER_NAME_PROPERTY = "skyforge.dev.wbyWave1MultiplayerObserverName";
    static final String NEAR_NAME_PROPERTY = "skyforge.dev.wbyWave1MultiplayerNearName";
    static final String FIXTURE_RESULT_FILE_PROPERTY = "skyforge.dev.wbyWave1MultiplayerFixtureResultFile";
    static final int HORIZONTAL_OFFSET_BLOCKS = 128;
    static final double NEAR_PLAYER_OFFSET_BLOCKS = 6.0;

    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> MULTIPLAYER_TICKET = TicketType.create(
            "skyforge_wby_wave1_multiplayer",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWbyWave1MultiplayerLifecycleAcceptance.class.getName());

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static UUID bodyId;
    private static Vec3 expectedBodyCenter;
    private static ChunkPos targetChunk;
    private static boolean previousPhysicsPaused;
    private static boolean physicsPauseChanged;
    private static boolean fixtureReady;
    private static long firstTwoPlayerTick = Long.MIN_VALUE;
    private static int diagnosticTicks;

    private SkyforgeWbyWave1MultiplayerLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1MultiplayerLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
        }

        String observerName = System.getProperty(OBSERVER_NAME_PROPERTY, "WbyObserver");
        String nearName = System.getProperty(NEAR_NAME_PROPERTY, "WbyNear");
        ServerPlayer observer = findPlayer(event, observerName);
        ServerPlayer near = findPlayer(event, nearName);
        if (observer == null || near == null) {
            return;
        }

        long now = level.getGameTime();
        if (firstTwoPlayerTick == Long.MIN_VALUE) {
            firstTwoPlayerTick = now;
        }
        if (now - firstTwoPlayerTick > 160L && !fixtureReady) {
            fail(event, "two multiplayer clients connected but remote Sable fixture was not ready within 160 ticks");
            return;
        }

        if (fixtureReady) {
            holdNearPlayer(near);
            diagnosticTicks++;
            if (diagnosticTicks == 1 || diagnosticTicks % 40 == 0) {
                logServerDiagnostics(observer, near);
            }
            return;
        }

        try {
            requireRuntimePreconditions();
            BlockPos observerBlock = observer.blockPosition();
            int baseX = observerBlock.getX() + HORIZONTAL_OFFSET_BLOCKS;
            int baseY = 200;
            int baseZ = observerBlock.getZ();

            BlockPos bodyMin = new BlockPos(baseX, baseY, baseZ);
            BlockPos bodyMax = new BlockPos(baseX + 3, baseY + 2, baseZ + 3);
            BlockPos assemblerPos = new BlockPos(baseX + 1, baseY + 3, baseZ + 1);
            BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

            targetChunk = new ChunkPos(bodyMin);
            level.getChunkSource().addRegionTicket(
                    MULTIPLAYER_TICKET,
                    targetChunk,
                    TICKET_DISTANCE,
                    targetChunk);
            level.getChunk(targetChunk.x, targetChunk.z);

            container = requireServerSubLevelContainer(level);
            Set<UUID> before = currentSubLevelIds(container);
            prepareFixture(bodyMin, bodyMax, assemblerPos);
            addFixtureGlue(bodyMin, glueMax);

            BlockEntity assembler = level.getBlockEntity(assemblerPos);
            if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                throw new IllegalStateException("WBY multiplayer Physics Assembler unavailable at " + assemblerPos);
            }
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

            Set<UUID> after = currentSubLevelIds(container);
            LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
            created.removeAll(before);
            if (created.size() != 1) {
                throw new IllegalStateException("expected exactly one multiplayer Sable body, created=" + created);
            }
            bodyId = created.iterator().next();

            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            previousPhysicsPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
            if (!previousPhysicsPaused) {
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
                physicsPauseChanged = true;
            }

            expectedBodyCenter = new Vec3(
                    (bodyMin.getX() + bodyMax.getX() + 1.0) * 0.5,
                    (bodyMin.getY() + bodyMax.getY() + 1.0) * 0.5,
                    (bodyMin.getZ() + bodyMax.getZ() + 1.0) * 0.5);
            observer.setInvulnerable(true);
            near.setInvulnerable(true);
            near.setNoGravity(true);
            fixtureReady = true;
            holdNearPlayer(near);
            writeFixtureEvidence(observer, near);

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WBY WAVE 1 MULTIPLAYER FIXTURE READY: bodyId=" + bodyId
                            + ", observer=" + observer.getGameProfile().getName()
                            + ", near=" + near.getGameProfile().getName()
                            + ", bodyCenter=" + expectedBodyCenter
                            + ", observerHorizontalDistance="
                            + horizontalDistance(observer.position(), expectedBodyCenter)
                            + ", nearPlayerDistance="
                            + near.position().distanceTo(expectedBodyCenter));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 multiplayer fixture failed: " + failure);
        }
    }

    private static ServerPlayer findPlayer(ServerTickEvent.Post event, String name) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equals(name)) {
                return player;
            }
        }
        return null;
    }

    private static void holdNearPlayer(ServerPlayer near) {
        if (expectedBodyCenter == null) {
            return;
        }
        near.teleportTo(
                expectedBodyCenter.x + NEAR_PLAYER_OFFSET_BLOCKS,
                expectedBodyCenter.y,
                expectedBodyCenter.z);
        near.setDeltaMovement(Vec3.ZERO);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static void logServerDiagnostics(ServerPlayer observer, ServerPlayer near) {
        try {
            Object body = findSubLevel(container, bodyId);
            Collection<?> tracking = body == null
                    ? List.of()
                    : (Collection<?>) publicMethod(body, "getTrackingPlayers").invoke(body);
            Integer observerRange = ssrdRequestedRange(observer);
            Integer nearRange = ssrdRequestedRange(near);
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WBY MULTIPLAYER SERVER DIAGNOSTIC: bodyId=" + bodyId
                            + ", bodyPresent=" + (body != null)
                            + ", trackingPlayers=" + tracking
                            + ", observerTracked=" + tracking.contains(observer.getUUID())
                            + ", nearTracked=" + tracking.contains(near.getUUID())
                            + ", observerSsrdRangeChunks=" + observerRange
                            + ", nearSsrdRangeChunks=" + nearRange
                            + ", observerDistanceBlocks="
                            + horizontalDistance(observer.position(), expectedBodyCenter)
                            + ", nearDistanceBlocks="
                            + near.position().distanceTo(expectedBodyCenter));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "WBY MULTIPLAYER SERVER DIAGNOSTIC FAILED: " + failure);
        }
    }

    private static Integer ssrdRequestedRange(ServerPlayer player)
            throws ReflectiveOperationException {
        Class<?> ssrdClass = Class.forName("net.ranold.ssrd.ssrd");
        Object value = ssrdClass.getField("playerRequestedRanges").get(null);
        if (!(value instanceof Map<?, ?> ranges)) {
            throw new IllegalStateException("SSRD playerRequestedRanges did not resolve to a Map");
        }
        Object range = ranges.get(player);
        return range instanceof Number number ? number.intValue() : null;
    }

    private static void writeFixtureEvidence(ServerPlayer observer, ServerPlayer near) {
        String configured = System.getProperty(FIXTURE_RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "WBY multiplayer fixture requires system property " + FIXTURE_RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalStateException("WBY multiplayer fixture result has no parent: " + path);
        }

        Properties properties = new Properties();
        properties.setProperty("status", "READY");
        properties.setProperty("dedicatedServer", String.valueOf(!level.getServer().isSingleplayer()));
        properties.setProperty("connectedPlayers", String.valueOf(level.getServer().getPlayerCount()));
        properties.setProperty("observerPlayerName", observer.getGameProfile().getName());
        properties.setProperty("nearPlayerName", near.getGameProfile().getName());
        properties.setProperty("bodyId", String.valueOf(bodyId));
        properties.setProperty(
                "observerBodyHorizontalDistanceBlocks",
                String.valueOf(horizontalDistance(observer.position(), expectedBodyCenter)));
        properties.setProperty(
                "nearPlayerBodyDistanceBlocks",
                String.valueOf(near.position().distanceTo(expectedBodyCenter)));
        properties.setProperty("nearPlayerPhysicallyPresent", "true");

        try {
            Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                properties.store(output, "Skyforge WBY Wave 1 multiplayer fixture evidence");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write WBY multiplayer fixture evidence " + path, exception);
        }
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY multiplayer server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
    }

    private static void prepareFixture(BlockPos bodyMin, BlockPos bodyMax, BlockPos assemblerPos) {
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
                    BlockState state = y == bodyMax.getY()
                            ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.RED_WOOL.defaultBlockState();
                    if (!level.setBlock(new BlockPos(x, y, z), state, 3)) {
                        throw new IllegalStateException("failed to place WBY multiplayer body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(assemblerPos, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY multiplayer Physics Assembler");
        }
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY multiplayer Super Glue fixture");
        }
    }

    private static Object requireServerSubLevelContainer(ServerLevel serverLevel)
            throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(serverLevel)) {
            throw new IllegalStateException("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(serverLevel);
        if (value == null) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        }
        return value;
    }

    private static Object findSubLevel(Object value, UUID expected)
            throws ReflectiveOperationException {
        if (value == null || expected == null) {
            return null;
        }
        Object subLevelsValue = publicMethod(value, "getAllSubLevels").invoke(value);
        if (!(subLevelsValue instanceof List<?> subLevels)) {
            return null;
        }
        for (Object subLevel : subLevels) {
            Object id = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
            if (expected.equals(id)) {
                return subLevel;
            }
        }
        return null;
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

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required WBY multiplayer block is not registered: " + id);
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
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, previousPhysicsPaused);
            } catch (ReflectiveOperationException ignored) {
                // Server is terminating after a failed bounded acceptance.
            }
        }
        if (level != null && targetChunk != null) {
            level.getChunkSource().removeRegionTicket(
                    MULTIPLAYER_TICKET,
                    targetChunk,
                    TICKET_DISTANCE,
                    targetChunk);
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
