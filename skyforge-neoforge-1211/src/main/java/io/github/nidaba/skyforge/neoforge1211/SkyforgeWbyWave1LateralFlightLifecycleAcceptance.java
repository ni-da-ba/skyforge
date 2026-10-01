package io.github.nidaba.skyforge.neoforge1211;

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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

/**
 * Server fixture for WBY-INT-0002 true lateral Sable motion against the DH/SSRD long-range path.
 *
 * <p>The body is held stationary until the actual client qualifies its distant render path. The
 * client then arms motion; the server releases physics and adds a bounded horizontal velocity
 * through Sable's current RigidBodyHandle. Only the source chunk is region-ticketed for assembly.
 * During motion one COMMAND_FORCED Sable ticket keeps the exact UUID live, so the test does not
 * grow a trail of forced world chunks.
 */
final class SkyforgeWbyWave1LateralFlightLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1LateralFlight";
    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> SOURCE_CHUNK_TICKET = TicketType.create(
            "skyforge_wby_wave1_lateral_flight",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;
    private static final int HORIZONTAL_OFFSET_BLOCKS = 240;
    private static final int BODY_Y = 240;
    private static final double TARGET_LATERAL_VELOCITY = 16.0;
    private static final double REQUIRED_LATERAL_DISPLACEMENT = 48.0;
    private static final long ASSEMBLY_DEADLINE_TICKS = 120L;
    private static final long PHYSICS_DEADLINE_TICKS = 100L;
    private static final long MOTION_DEADLINE_TICKS = 180L;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object body;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static UUID bodyId;
    private static Set<UUID> beforeIds = Set.of();
    private static ChunkPos sourceChunk;
    private static BlockPos fixtureBodyMin;
    private static BlockPos fixtureBodyMax;
    private static BlockPos fixtureAssemblerPos;
    private static boolean sourceChunkTicketAdded;
    private static boolean forceLoadTicketAdded;
    private static boolean physicsStateCaptured;
    private static boolean previousPhysicsPaused;
    private static volatile boolean fixtureReady;
    private static volatile boolean clientComplete;
    private static volatile boolean clientArmRequested;
    private static volatile boolean motionStarted;
    private static volatile boolean motionComplete;
    private static volatile Vec3 initialServerPose;
    private static volatile Vec3 currentServerPose;
    private static volatile Vec3 commandedVelocity = Vec3.ZERO;
    private static volatile double lateralDisplacementBlocks;
    private static long stageStartTick;
    private static Stage stage;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        WAITING_FOR_CLIENT,
        MOVING,
        COMPLETE
    }

    record Snapshot(
            UUID bodyId,
            Vec3 initialServerPose,
            Vec3 serverPose,
            boolean motionStarted,
            boolean motionComplete,
            double lateralDisplacementBlocks,
            Vec3 commandedVelocity,
            boolean sourceChunkTicketReleased,
            boolean boundedSableLivenessActive) {}

    private SkyforgeWbyWave1LateralFlightLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1LateralFlightLifecycleAcceptance::onServerTickPost);
    }

    static boolean fixtureReady() {
        return fixtureReady;
    }

    static Snapshot snapshot() {
        if (!fixtureReady || bodyId == null || initialServerPose == null || currentServerPose == null) {
            return null;
        }
        return new Snapshot(
                bodyId,
                initialServerPose,
                currentServerPose,
                motionStarted,
                motionComplete,
                lateralDisplacementBlocks,
                commandedVelocity,
                !sourceChunkTicketAdded,
                forceLoadTicketAdded);
    }

    static void armMotionFromClient() {
        clientArmRequested = true;
    }

    static boolean fixtureLivenessReleased() {
        return !forceLoadTicketAdded && !sourceChunkTicketAdded;
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

        try {
            long now = level.getGameTime();
            if (stage == null) {
                beginAssembly(event, now);
                return;
            }
            switch (stage) {
                case ASSEMBLY -> pollAssembly(event, now);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(event, now);
                case WAITING_FOR_CLIENT -> {
                    refreshServerPose();
                    if (clientArmRequested) {
                        beginMotion(event, now);
                    }
                }
                case MOVING -> pollMotion(event, now);
                case COMPLETE -> refreshServerPose();
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 lateral-flight lifecycle failed: " + failure);
        }
    }

    private static void beginAssembly(ServerTickEvent.Post event, long now)
            throws ReflectiveOperationException {
        requireRuntimePreconditions();
        var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
        BlockPos bodyMin = new BlockPos(
                viewer.blockPosition().getX() + HORIZONTAL_OFFSET_BLOCKS,
                BODY_Y,
                viewer.blockPosition().getZ());
        BlockPos bodyMax = bodyMin.offset(3, 2, 3);
        BlockPos assemblerPos = bodyMin.offset(1, 3, 1);
        fixtureBodyMin = bodyMin;
        fixtureBodyMax = bodyMax;
        fixtureAssemblerPos = assemblerPos;

        sourceChunk = new ChunkPos(bodyMin);
        level.getChunkSource().addRegionTicket(
                SOURCE_CHUNK_TICKET, sourceChunk, TICKET_DISTANCE, sourceChunk);
        sourceChunkTicketAdded = true;
        level.getChunk(sourceChunk.x, sourceChunk.z);

        container = requireServerSubLevelContainer(level);
        beforeIds = currentSubLevelIds();
        prepareFixture(bodyMin, bodyMax, assemblerPos);

        BlockEntity assembler = level.getBlockEntity(assemblerPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY lateral-flight Physics Assembler unavailable at " + assemblerPos);
        }

        stage = Stage.ASSEMBLY;
        stageStartTick = now;
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
        pollAssembly(event, now);
    }

    private static void pollAssembly(ServerTickEvent.Post event, long now)
            throws ReflectiveOperationException {
        Set<UUID> created = new LinkedHashSet<>(currentSubLevelIds());
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(event, "WBY lateral-flight fixture created multiple Sable bodies: " + created);
            return;
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            Object canonical = findCanonicalBody(bodyId);
            if (canonical != null && sourceNonAirBlocks() == 0) {
                body = canonical;
                requireViableBody(canonical);
                physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
                addFixtureForceLoadTicket(canonical);
                removeSourceChunkTicket();
                stage = Stage.PHYSICS_INITIALIZATION;
                stageStartTick = now;
                pollPhysicsInitialization(event, now);
                return;
            }
        }
        if (now - stageStartTick > ASSEMBLY_DEADLINE_TICKS) {
            fail(event, "WBY lateral-flight body/source transfer did not settle: created="
                    + created + ", sourceNonAir=" + sourceNonAirBlocks());
        }
    }

    private static void pollPhysicsInitialization(ServerTickEvent.Post event, long now)
            throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        Object handle = canonical == null ? null : findCurrentPhysicsHandle(canonical);
        if (canonical != null && handle != null) {
            body = canonical;
            captureAndPausePhysics();
            initialServerPose = bodyPosePosition(canonical);
            currentServerPose = initialServerPose;
            lateralDisplacementBlocks = 0.0;
            fixtureReady = true;
            stage = Stage.WAITING_FOR_CLIENT;
            stageStartTick = now;
            return;
        }
        if (now - stageStartTick > PHYSICS_DEADLINE_TICKS) {
            fail(event, "WBY lateral-flight body never acquired a valid current physics handle");
        }
    }

    private static void beginMotion(ServerTickEvent.Post event, long now)
            throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            fail(event, "WBY lateral-flight body lost current physics authority before motion");
            return;
        }

        resetVelocity(handle);
        publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
        Vector3d linear = new Vector3d(0.0, 0.0, TARGET_LATERAL_VELOCITY);
        Vector3d angular = new Vector3d();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linear, angular).invoke(handle, linear, angular);

        initialServerPose = bodyPosePosition(canonical);
        currentServerPose = initialServerPose;
        commandedVelocity = new Vec3(linear.x, linear.y, linear.z);
        lateralDisplacementBlocks = 0.0;
        motionStarted = true;
        stage = Stage.MOVING;
        stageStartTick = now;
    }

    private static void pollMotion(ServerTickEvent.Post event, long now)
            throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            fail(event, "WBY lateral-flight body lost valid current physics authority during motion");
            return;
        }

        body = canonical;
        currentServerPose = bodyPosePosition(canonical);
        lateralDisplacementBlocks = Math.abs(currentServerPose.z - initialServerPose.z);

        if (lateralDisplacementBlocks >= REQUIRED_LATERAL_DISPLACEMENT) {
            resetVelocity(handle);
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
            currentServerPose = bodyPosePosition(canonical);
            lateralDisplacementBlocks = Math.abs(currentServerPose.z - initialServerPose.z);
            motionComplete = true;
            stage = Stage.COMPLETE;
            return;
        }

        if (now - stageStartTick > MOTION_DEADLINE_TICKS) {
            fail(event, "WBY lateral-flight body did not reach required lateral displacement: "
                    + lateralDisplacementBlocks);
        }
    }

    private static Object requireCanonicalBody() throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            throw new IllegalStateException("WBY lateral-flight body UUID is no longer canonical: " + bodyId);
        }
        requireViableBody(canonical);
        return canonical;
    }

    private static void refreshServerPose() throws ReflectiveOperationException {
        if (bodyId == null) {
            return;
        }
        Object canonical = findCanonicalBody(bodyId);
        if (canonical != null) {
            body = canonical;
            currentServerPose = bodyPosePosition(canonical);
            if (motionStarted && initialServerPose != null) {
                lateralDisplacementBlocks = Math.abs(currentServerPose.z - initialServerPose.z);
            }
        }
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
                            : Blocks.SLIME_BLOCK.defaultBlockState();
                    if (!level.setBlock(new BlockPos(x, y, z), state, 3)) {
                        throw new IllegalStateException("failed to place WBY lateral-flight body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(assemblerPos, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY lateral-flight Physics Assembler");
        }
    }

    private static int sourceNonAirBlocks() {
        if (fixtureBodyMin == null || fixtureBodyMax == null || fixtureAssemblerPos == null) {
            return -1;
        }
        int count = level.getBlockState(fixtureAssemblerPos).isAir() ? 0 : 1;
        for (int x = fixtureBodyMin.getX(); x <= fixtureBodyMax.getX(); x++) {
            for (int y = fixtureBodyMin.getY(); y <= fixtureBodyMax.getY(); y++) {
                for (int z = fixtureBodyMin.getZ(); z <= fixtureBodyMax.getZ(); z++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY lateral-flight server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
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

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        }
        for (Object subLevel : subLevels) {
            if (uuid.equals(publicMethod(subLevel, "getUniqueId").invoke(subLevel))) {
                return subLevel;
            }
        }
        return null;
    }

    private static Object findCurrentPhysicsHandle(Object canonical) throws ReflectiveOperationException {
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonical).invoke(physicsSystem, canonical);
        if (handle == null) {
            return null;
        }
        return Boolean.TRUE.equals(publicMethod(handle, "isValid").invoke(handle)) ? handle : null;
    }

    private static void resetVelocity(Object handle) throws ReflectiveOperationException {
        Vector3d linear = (Vector3d) publicMethod(handle, "getLinearVelocity", Vector3d.class)
                .invoke(handle, new Vector3d());
        Vector3d angular = (Vector3d) publicMethod(handle, "getAngularVelocity", Vector3d.class)
                .invoke(handle, new Vector3d());
        linear.negate();
        angular.negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linear, angular).invoke(handle, linear, angular);
    }

    private static Vec3 bodyPosePosition(Object canonical) throws ReflectiveOperationException {
        Object pose = publicMethod(canonical, "logicalPose").invoke(canonical);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                number(publicMethod(position, "x").invoke(position)),
                number(publicMethod(position, "y").invoke(position)),
                number(publicMethod(position, "z").invoke(position)));
    }

    private static void requireViableBody(Object canonical) throws ReflectiveOperationException {
        boolean removed = Boolean.TRUE.equals(publicMethod(canonical, "isRemoved").invoke(canonical));
        Object massTracker = publicMethod(canonical, "getMassTracker").invoke(canonical);
        double mass = ((Number) publicMethod(massTracker, "getMass").invoke(massTracker)).doubleValue();
        if (removed || !(mass > 0.0)) {
            throw new IllegalStateException("WBY lateral-flight body invalid: removed=" + removed + ", mass=" + mass);
        }
    }

    private static void captureAndPausePhysics() throws ReflectiveOperationException {
        if (physicsStateCaptured) {
            return;
        }
        previousPhysicsPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        physicsStateCaptured = true;
        if (!previousPhysicsPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
        }
    }

    private static void addFixtureForceLoadTicket(Object canonical) throws ReflectiveOperationException {
        if (forceLoadTicketAdded) {
            return;
        }
        Class<?> ticketTypeClass =
                Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        forceLoadTicketKey = Unit.INSTANCE;
        Method add = container.getClass().getMethod(
                "addForceLoadTicket", canonical.getClass(), ticketTypeClass, Object.class);
        Object result = add.invoke(container, canonical, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("could not add bounded Sable lateral-flight liveness ticket");
        }
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || body == null || forceLoadTicketType == null || forceLoadTicketKey == null) {
            return;
        }
        Method remove = container.getClass().getMethod(
                "removeForceLoadTicket", body.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object result = remove.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("could not remove bounded Sable lateral-flight liveness ticket");
        }
        forceLoadTicketAdded = false;
    }

    private static void removeSourceChunkTicket() {
        if (!sourceChunkTicketAdded || level == null || sourceChunk == null) {
            return;
        }
        level.getChunkSource().removeRegionTicket(
                SOURCE_CHUNK_TICKET, sourceChunk, TICKET_DISTANCE, sourceChunk);
        sourceChunkTicketAdded = false;
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required WBY lateral-flight block not registered: " + id);
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
            if (method.getName().equals(name)
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(first.getClass())
                    && method.getParameterTypes()[1].isAssignableFrom(second.getClass())) {
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
        try {
            if (body != null && physicsSystem != null) {
                Object handle = findCurrentPhysicsHandle(body);
                if (handle != null) {
                    resetVelocity(handle);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Disposable acceptance process is stopping.
        }
        if (physicsSystem != null && physicsStateCaptured) {
            try {
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, previousPhysicsPaused);
            } catch (ReflectiveOperationException ignored) {
                // Disposable acceptance process is stopping.
            }
        }
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException ignored) {
            // Disposable acceptance process is stopping.
        }
        removeSourceChunkTicket();
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        restoreFixtureState();
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
