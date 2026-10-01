package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Comparator;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server half of WBY-INT-0002 actual-client visibility qualification.
 *
 * <p>After the integrated-server player exists, this fixture assembles one stationary Sable body
 * 256 blocks east of that player, keeps only that finite target chunk live for the test, and
 * publishes the persistent body UUID for the client-side render-path proof.
 */
final class SkyforgeWbyWave1VisibilityLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1Visibility";
    static final int HORIZONTAL_OFFSET_BLOCKS = 256;

    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> VISIBILITY_TICKET = TicketType.create(
            "skyforge_wby_wave1_visibility",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static UUID bodyId;
    private static Vec3 expectedBodyCenter;
    private static ChunkPos targetChunk;
    private static boolean previousPhysicsPaused;
    private static boolean physicsPauseChanged;
    private static boolean fixtureReady;
    private static boolean clientComplete;
    private static long firstPlayerTick = Long.MIN_VALUE;

    private SkyforgeWbyWave1VisibilityLifecycleAcceptance() {}

    record Snapshot(UUID bodyId, Vec3 expectedBodyCenter) {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1VisibilityLifecycleAcceptance::onServerTickPost);
    }

    static boolean fixtureReady() {
        return fixtureReady;
    }

    static Snapshot snapshot() {
        return fixtureReady && bodyId != null && expectedBodyCenter != null
                ? new Snapshot(bodyId, expectedBodyCenter)
                : null;
    }

    static void markClientComplete() {
        clientComplete = true;
        restoreFixtureState();
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (clientComplete || fixtureReady) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
        }
        if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (firstPlayerTick == Long.MIN_VALUE) {
            firstPlayerTick = now;
        }
        if (now - firstPlayerTick > 120L) {
            fail(event, "integrated-server player existed but distant Sable fixture was not assembled within 120 ticks");
            return;
        }

        try {
            requireRuntimePreconditions();
            var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
            BlockPos viewerBlock = viewer.blockPosition();
            int baseX = viewerBlock.getX() + HORIZONTAL_OFFSET_BLOCKS;
            int baseY = 200;
            int baseZ = viewerBlock.getZ();

            BlockPos bodyMin = new BlockPos(baseX, baseY, baseZ);
            BlockPos bodyMax = new BlockPos(baseX + 3, baseY + 2, baseZ + 3);
            BlockPos assemblerPos = new BlockPos(baseX + 1, baseY + 3, baseZ + 1);
            BlockPos glueMin = bodyMin;
            BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

            targetChunk = new ChunkPos(bodyMin);
            level.getChunkSource().addRegionTicket(
                    VISIBILITY_TICKET,
                    targetChunk,
                    TICKET_DISTANCE,
                    targetChunk);
            level.getChunk(targetChunk.x, targetChunk.z);

            container = requireServerSubLevelContainer(level);
            Set<UUID> before = currentSubLevelIds(container);
            prepareFixture(level, bodyMin, bodyMax, assemblerPos);
            addFixtureGlue(level, glueMin, glueMax);

            BlockEntity assembler = level.getBlockEntity(assemblerPos);
            if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                throw new IllegalStateException("WBY Wave 1 Physics Assembler unavailable at " + assemblerPos);
            }
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

            Set<UUID> after = currentSubLevelIds(container);
            LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
            created.removeAll(before);
            if (created.size() != 1) {
                throw new IllegalStateException("expected exactly one new Sable body, created=" + created);
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
            fixtureReady = true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 distant Sable fixture failed: " + failure);
        }
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY Wave 1 server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
    }

    private static void prepareFixture(
            ServerLevel level,
            BlockPos bodyMin,
            BlockPos bodyMax,
            BlockPos assemblerPos) {
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
                    BlockState state = (y == bodyMax.getY())
                            ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.RED_WOOL.defaultBlockState();
                    if (!level.setBlock(new BlockPos(x, y, z), state, 3)) {
                        throw new IllegalStateException("failed to place WBY Wave 1 body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(assemblerPos, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY Wave 1 Physics Assembler");
        }
    }

    private static void addFixtureGlue(
            ServerLevel level,
            BlockPos glueMin,
            BlockPos glueMax) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        AABB box = (AABB) span.invoke(null, glueMin, glueMax);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY Wave 1 Super Glue fixture");
        }
    }

    private static Object requireServerSubLevelContainer(ServerLevel level)
            throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(level)) {
            throw new IllegalStateException("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        if (value == null) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        }
        return value;
    }

    private static Set<UUID> currentSubLevelIds(Object container)
            throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
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
            BlockState state,
            Property<T> property,
            String name,
            String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException(
                        "cannot parse block property " + name + "=" + value + " on " + state));
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
                // Integrated server is about to stop; evidence already records client completion.
            }
        }
        if (level != null && targetChunk != null) {
            level.getChunkSource().removeRegionTicket(
                    VISIBILITY_TICKET,
                    targetChunk,
                    TICKET_DISTANCE,
                    targetChunk);
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
