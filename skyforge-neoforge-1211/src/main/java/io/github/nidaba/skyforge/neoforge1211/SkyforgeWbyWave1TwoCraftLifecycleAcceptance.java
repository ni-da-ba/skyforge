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

/** Server fixture for the WBY-INT-0002 two-distant-craft visibility case. */
final class SkyforgeWbyWave1TwoCraftLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1TwoCraft";
    static final int HORIZONTAL_OFFSET_BLOCKS = 240;
    private static final int LATERAL_HALF_SEPARATION_BLOCKS = 24;
    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> VISIBILITY_TICKET = TicketType.create(
            "skyforge_wby_wave1_two_craft",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Craft craftA;
    private static Craft craftB;
    private static final Set<ChunkPos> targetChunks = new LinkedHashSet<>();
    private static boolean previousPhysicsPaused;
    private static boolean physicsPauseChanged;
    private static boolean fixtureReady;
    private static boolean clientComplete;
    private static long firstPlayerTick = Long.MIN_VALUE;

    record Snapshot(UUID bodyAId, Vec3 expectedBodyACenter, UUID bodyBId, Vec3 expectedBodyBCenter) {}

    private record Craft(UUID bodyId, Vec3 expectedCenter) {}

    private SkyforgeWbyWave1TwoCraftLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1TwoCraftLifecycleAcceptance::onServerTickPost);
    }

    static Snapshot snapshot() {
        return fixtureReady && craftA != null && craftB != null
                ? new Snapshot(craftA.bodyId(), craftA.expectedCenter(), craftB.bodyId(), craftB.expectedCenter())
                : null;
    }

    static boolean fixtureReady() {
        return fixtureReady;
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
            fail(event, "integrated-server player existed but two distant Sable fixtures were not assembled within 120 ticks");
            return;
        }

        try {
            requireRuntimePreconditions();
            var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
            BlockPos viewerBlock = viewer.blockPosition();
            int baseX = viewerBlock.getX() + HORIZONTAL_OFFSET_BLOCKS;
            int baseY = 200;
            int centerZ = viewerBlock.getZ();

            container = requireServerSubLevelContainer(level);
            craftA = assembleCraft(
                    new BlockPos(baseX, baseY, centerZ - LATERAL_HALF_SEPARATION_BLOCKS),
                    Blocks.RED_WOOL,
                    Blocks.GOLD_BLOCK);
            craftB = assembleCraft(
                    new BlockPos(baseX, baseY, centerZ + LATERAL_HALF_SEPARATION_BLOCKS),
                    Blocks.BLUE_WOOL,
                    Blocks.IRON_BLOCK);

            if (craftA.bodyId().equals(craftB.bodyId())) {
                throw new IllegalStateException("two-craft fixture resolved the same Sable UUID twice");
            }

            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            previousPhysicsPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
            if (!previousPhysicsPaused) {
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
                physicsPauseChanged = true;
            }
            fixtureReady = true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 two-craft fixture failed: " + failure);
        }
    }

    private static Craft assembleCraft(BlockPos bodyMin, Block bodyBlock, Block capBlock)
            throws ReflectiveOperationException {
        BlockPos bodyMax = bodyMin.offset(3, 2, 3);
        BlockPos assemblerPos = bodyMin.offset(1, 3, 1);
        BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

        addTargetChunk(new ChunkPos(bodyMin));
        Set<UUID> before = currentSubLevelIds();
        prepareFixture(bodyMin, bodyMax, assemblerPos, bodyBlock, capBlock);
        addFixtureGlue(bodyMin, glueMax);

        BlockEntity assembler = level.getBlockEntity(assemblerPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY Wave 1 Physics Assembler unavailable at " + assemblerPos);
        }
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

        Set<UUID> after = currentSubLevelIds();
        LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
        created.removeAll(before);
        if (created.size() != 1) {
            throw new IllegalStateException("expected exactly one new Sable body at " + bodyMin + ", created=" + created);
        }

        UUID id = created.iterator().next();
        Vec3 expectedCenter = new Vec3(
                (bodyMin.getX() + bodyMax.getX() + 1.0) * 0.5,
                (bodyMin.getY() + bodyMax.getY() + 1.0) * 0.5,
                (bodyMin.getZ() + bodyMax.getZ() + 1.0) * 0.5);
        return new Craft(id, expectedCenter);
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY Wave 1 server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
    }

    private static void addTargetChunk(ChunkPos chunk) {
        if (!targetChunks.add(chunk)) {
            return;
        }
        level.getChunkSource().addRegionTicket(VISIBILITY_TICKET, chunk, TICKET_DISTANCE, chunk);
        level.getChunk(chunk.x, chunk.z);
    }

    private static void prepareFixture(
            BlockPos bodyMin,
            BlockPos bodyMax,
            BlockPos assemblerPos,
            Block bodyBlock,
            Block capBlock) {
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
                            ? capBlock.defaultBlockState()
                            : bodyBlock.defaultBlockState();
                    if (!level.setBlock(new BlockPos(x, y, z), state, 3)) {
                        throw new IllegalStateException("failed to place WBY Wave 1 two-craft body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(assemblerPos, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY Wave 1 two-craft Physics Assembler");
        }
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY Wave 1 two-craft Super Glue fixture");
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

    private static Set<UUID> currentSubLevelIds() throws ReflectiveOperationException {
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
                () -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
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
                // Disposable integrated server is stopping.
            }
            physicsPauseChanged = false;
        }
        if (level != null) {
            for (ChunkPos chunk : List.copyOf(targetChunks)) {
                level.getChunkSource().removeRegionTicket(VISIBILITY_TICKET, chunk, TICKET_DISTANCE, chunk);
                targetChunks.remove(chunk);
            }
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
