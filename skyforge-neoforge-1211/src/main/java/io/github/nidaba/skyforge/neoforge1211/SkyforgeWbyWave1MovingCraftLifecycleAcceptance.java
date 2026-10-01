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
import net.minecraft.util.Unit;
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
import org.joml.Vector3d;

/**
 * Server fixture for WBY-INT-0002 actual moving-craft visibility qualification.
 *
 * <p>The viewer remains fixed while one real Sable physics body moves laterally at long range,
 * recedes farther from the viewer, then approaches back to its initial X range. Motion uses the
 * same current-physics-handle velocity seam accepted by PLATFORM-012.
 */
final class SkyforgeWbyWave1MovingCraftLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1MovingCraft";
    static final int HORIZONTAL_OFFSET_BLOCKS = 240;

    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> MOTION_TICKET = TicketType.create(
            "skyforge_wby_wave1_moving_craft",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 4;

    private static final double MOTION_SPEED_BLOCKS_PER_SECOND = 8.0;
    private static final double LATERAL_TRAVEL_BLOCKS = 24.0;
    private static final double RECEDE_TRAVEL_BLOCKS = 32.0;
    private static final double ENDPOINT_TOLERANCE_BLOCKS = 1.5;
    private static final int HOLD_TICKS = 24;
    private static final long FIXTURE_DEADLINE_TICKS = 160L;
    private static final long MOTION_DEADLINE_TICKS = 900L;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object body;
    private static UUID bodyId;
    private static ChunkPos targetChunk;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static boolean fixtureReady;
    private static boolean clientComplete;
    private static long firstPlayerTick = Long.MIN_VALUE;
    private static long motionStartTick = Long.MIN_VALUE;
    private static int phaseTick;
    private static Vec3 originPose;
    private static volatile Snapshot snapshot;

    private enum Phase {
        WAIT_PHYSICS,
        BASELINE_HOLD,
        LATERAL,
        LATERAL_HOLD,
        RECEDE,
        FAR_HOLD,
        APPROACH,
        FINAL_HOLD,
        COMPLETE
    }

    record Snapshot(
            UUID bodyId,
            Vec3 originPose,
            Vec3 logicalPose,
            String phase,
            double lateralDisplacementBlocks,
            double recedeDisplacementBlocks) {}

    private SkyforgeWbyWave1MovingCraftLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1MovingCraftLifecycleAcceptance::onServerTickPost);
    }

    static Snapshot snapshot() {
        return snapshot;
    }

    static boolean fixtureReady() {
        return fixtureReady;
    }

    static void markClientComplete() {
        clientComplete = true;
        restoreFixtureState();
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (clientComplete) {
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

        try {
            if (bodyId == null) {
                if (now - firstPlayerTick > FIXTURE_DEADLINE_TICKS) {
                    fail(event, "moving-craft Sable fixture was not assembled before deadline");
                    return;
                }
                assembleFixture(event);
                return;
            }

            Object canonical = findCanonicalBody(bodyId);
            if (canonical == null) {
                fail(event, "moving-craft canonical Sable body disappeared: bodyId=" + bodyId);
                return;
            }
            body = canonical;
            Object handle = findCurrentPhysicsHandle(canonical);
            if (handle == null) {
                if (now - firstPlayerTick > FIXTURE_DEADLINE_TICKS) {
                    fail(event, "moving-craft body did not acquire a valid current physics handle");
                }
                snapshot = new Snapshot(bodyId, originPose, bodyPosePosition(canonical), Phase.WAIT_PHYSICS.name(), 0.0, 0.0);
                return;
            }

            if (!fixtureReady) {
                originPose = bodyPosePosition(canonical);
                addFixtureForceLoadTicket(canonical);
                setLinearVelocity(handle, 0.0, 0.0, 0.0);
                fixtureReady = true;
                motionStartTick = now;
                transition(Phase.BASELINE_HOLD);
            }

            if (now - motionStartTick > MOTION_DEADLINE_TICKS) {
                fail(event, "moving-craft sequence exceeded bounded motion deadline");
                return;
            }

            advanceMotion(event, canonical, handle);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 moving-craft lifecycle failed: " + failure);
        }
    }

    private static void assembleFixture(ServerTickEvent.Post event) throws ReflectiveOperationException {
        requireRuntimePreconditions();
        var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
        BlockPos viewerBlock = viewer.blockPosition();
        int baseX = viewerBlock.getX() + HORIZONTAL_OFFSET_BLOCKS;
        int baseY = 200;
        int baseZ = viewerBlock.getZ();

        BlockPos bodyMin = new BlockPos(baseX, baseY, baseZ);
        BlockPos bodyMax = new BlockPos(baseX + 3, baseY + 2, baseZ + 3);
        BlockPos assemblerPos = new BlockPos(baseX + 1, baseY + 3, baseZ + 1);
        BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

        targetChunk = new ChunkPos(bodyMin);
        level.getChunkSource().addRegionTicket(MOTION_TICKET, targetChunk, TICKET_DISTANCE, targetChunk);
        level.getChunk(targetChunk.x, targetChunk.z);

        container = requireServerSubLevelContainer(level);
        physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Set<UUID> before = currentSubLevelIds();

        prepareFixture(bodyMin, bodyMax, assemblerPos);
        addFixtureGlue(bodyMin, glueMax);

        BlockEntity assembler = level.getBlockEntity(assemblerPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY moving-craft Physics Assembler unavailable at " + assemblerPos);
        }
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

        Set<UUID> after = currentSubLevelIds();
        LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
        created.removeAll(before);
        if (created.size() != 1) {
            throw new IllegalStateException("expected exactly one moving Sable body, created=" + created);
        }

        bodyId = created.iterator().next();
        body = findCanonicalBody(bodyId);
        if (body == null) {
            throw new IllegalStateException("new moving Sable UUID was not canonical: " + bodyId);
        }
        snapshot = new Snapshot(bodyId, null, bodyPosePosition(body), Phase.WAIT_PHYSICS.name(), 0.0, 0.0);
    }

    private static void advanceMotion(
            ServerTickEvent.Post event,
            Object canonical,
            Object handle) throws ReflectiveOperationException {
        Phase phase = Phase.valueOf(snapshot.phase());
        Vec3 pose = bodyPosePosition(canonical);
        double lateral = pose.z - originPose.z;
        double recede = pose.x - originPose.x;

        switch (phase) {
            case WAIT_PHYSICS -> throw new IllegalStateException("fixtureReady while still WAIT_PHYSICS");
            case BASELINE_HOLD -> {
                setLinearVelocity(handle, 0.0, 0.0, 0.0);
                if (++phaseTick >= HOLD_TICKS) transition(Phase.LATERAL);
            }
            case LATERAL -> {
                setLinearVelocity(handle, 0.0, 0.0, MOTION_SPEED_BLOCKS_PER_SECOND);
                if (lateral >= LATERAL_TRAVEL_BLOCKS) {
                    setLinearVelocity(handle, 0.0, 0.0, 0.0);
                    transition(Phase.LATERAL_HOLD);
                }
            }
            case LATERAL_HOLD -> {
                setLinearVelocity(handle, 0.0, 0.0, 0.0);
                if (++phaseTick >= HOLD_TICKS) transition(Phase.RECEDE);
            }
            case RECEDE -> {
                setLinearVelocity(handle, MOTION_SPEED_BLOCKS_PER_SECOND, 0.0, 0.0);
                if (recede >= RECEDE_TRAVEL_BLOCKS) {
                    setLinearVelocity(handle, 0.0, 0.0, 0.0);
                    transition(Phase.FAR_HOLD);
                }
            }
            case FAR_HOLD -> {
                setLinearVelocity(handle, 0.0, 0.0, 0.0);
                if (++phaseTick >= HOLD_TICKS) transition(Phase.APPROACH);
            }
            case APPROACH -> {
                setLinearVelocity(handle, -MOTION_SPEED_BLOCKS_PER_SECOND, 0.0, 0.0);
                if (recede <= ENDPOINT_TOLERANCE_BLOCKS) {
                    setLinearVelocity(handle, 0.0, 0.0, 0.0);
                    transition(Phase.FINAL_HOLD);
                }
            }
            case FINAL_HOLD -> {
                setLinearVelocity(handle, 0.0, 0.0, 0.0);
                if (++phaseTick >= HOLD_TICKS) transition(Phase.COMPLETE);
            }
            case COMPLETE -> setLinearVelocity(handle, 0.0, 0.0, 0.0);
        }

        pose = bodyPosePosition(canonical);
        lateral = pose.z - originPose.z;
        recede = pose.x - originPose.x;
        Phase published = Phase.valueOf(snapshot.phase());
        snapshot = new Snapshot(bodyId, originPose, pose, published.name(), lateral, recede);
    }

    private static void transition(Phase next) {
        phaseTick = 0;
        Vec3 logical = body == null || originPose == null ? originPose : snapshot.logicalPose();
        snapshot = new Snapshot(
                bodyId,
                originPose,
                logical,
                next.name(),
                logical == null || originPose == null ? 0.0 : logical.z - originPose.z,
                logical == null || originPose == null ? 0.0 : logical.x - originPose.x);
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY moving-craft server mod not loaded: " + modId);
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
                        throw new IllegalStateException("failed to place WBY moving-craft body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(assemblerPos, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY moving-craft Physics Assembler");
        }
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY moving-craft Super Glue fixture");
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
            if (id instanceof UUID uuid) ids.add(uuid);
        }
        return ids;
    }

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        }
        for (Object subLevel : subLevels) {
            if (uuid.equals(publicMethod(subLevel, "getUniqueId").invoke(subLevel))) return subLevel;
        }
        return null;
    }

    private static Object findCurrentPhysicsHandle(Object canonical) throws ReflectiveOperationException {
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonical).invoke(physicsSystem, canonical);
        if (handle == null) return null;
        return Boolean.TRUE.equals(publicMethod(handle, "isValid").invoke(handle)) ? handle : null;
    }

    private static Vec3 bodyPosePosition(Object canonical) throws ReflectiveOperationException {
        Object pose = publicMethod(canonical, "logicalPose").invoke(canonical);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                number(publicMethod(position, "x").invoke(position)),
                number(publicMethod(position, "y").invoke(position)),
                number(publicMethod(position, "z").invoke(position)));
    }

    private static void setLinearVelocity(Object handle, double targetX, double targetY, double targetZ)
            throws ReflectiveOperationException {
        Vector3d currentLinear = new Vector3d();
        Vector3d currentAngular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", currentLinear).invoke(handle, currentLinear);
        oneArgMethod(handle, "getAngularVelocity", currentAngular).invoke(handle, currentAngular);
        Vector3d linearDelta = new Vector3d(
                targetX - currentLinear.x(),
                targetY - currentLinear.y(),
                targetZ - currentLinear.z());
        Vector3d angularDelta = new Vector3d(currentAngular).negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linearDelta, angularDelta)
                .invoke(handle, linearDelta, angularDelta);
    }

    private static void addFixtureForceLoadTicket(Object canonical) throws ReflectiveOperationException {
        if (forceLoadTicketAdded) return;
        Class<?> ticketTypeClass =
                Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        forceLoadTicketKey = Unit.INSTANCE;
        Method add = container.getClass().getMethod(
                "addForceLoadTicket", canonical.getClass(), ticketTypeClass, Object.class);
        Object result = add.invoke(container, canonical, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("could not add bounded moving-craft Sable liveness ticket");
        }
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || body == null || forceLoadTicketType == null || forceLoadTicketKey == null) return;
        Method remove = container.getClass().getMethod(
                "removeForceLoadTicket", body.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object result = remove.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("could not remove bounded moving-craft Sable liveness ticket");
        }
        forceLoadTicketAdded = false;
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required exact-stack block is not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return withParsedProperty(state, property, name, value);
        }
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Method oneArgMethod(Object target, String name, Object argument)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(1 arg)");
    }

    private static Method twoArgMethod(Object target, String name, Object first, Object second)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types[0].isAssignableFrom(first.getClass())
                    && types[1].isAssignableFrom(second.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(2 args)");
    }

    private static double number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("expected Number, got " + value);
        }
        return number.doubleValue();
    }

    private static void restoreFixtureState() {
        if (body != null && physicsSystem != null) {
            try {
                Object handle = findCurrentPhysicsHandle(body);
                if (handle != null) setLinearVelocity(handle, 0.0, 0.0, 0.0);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Disposable integrated server is stopping.
            }
        }
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Disposable integrated server is stopping.
        }
        if (level != null && targetChunk != null) {
            level.getChunkSource().removeRegionTicket(MOTION_TICKET, targetChunk, TICKET_DISTANCE, targetChunk);
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
