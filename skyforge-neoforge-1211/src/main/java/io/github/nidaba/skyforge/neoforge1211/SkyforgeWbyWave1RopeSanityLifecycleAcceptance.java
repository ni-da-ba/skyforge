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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * WBY-INT-0002 rope/attached-element qualification.
 *
 * <p>Sable 2.0.5 exposes a server-side rope physics object rather than a client renderer. This
 * fixture therefore proves the relevant contract directly: a real rope stays active and finite
 * while attached between the world and an assembled Sable body that crosses a vanilla chunk
 * boundary. The proof also records chunk counts and rejects forced-chunk growth.
 */
final class SkyforgeWbyWave1RopeSanityLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1RopeSanity";

    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final int SETUP_TIMEOUT_TICKS = 360;
    private static final int PRE_MOVE_SETTLE_TICKS = 30;
    private static final int POST_MOVE_SETTLE_TICKS = 20;
    private static final int TICKET_SETTLE_TICKS = 40;
    private static final int ROPE_POINTS = 32;
    private static final double ROPE_RADIUS = 0.125;
    private static final double WORLD_ANCHOR_OFFSET = 12.0;
    private static final double ENDPOINT_TOLERANCE = 2.0;
    private static final int MAX_LOADED_CHUNK_DELTA = 64;
    private static final TicketType<ChunkPos> ROPE_SANITY_TICKET = TicketType.create(
            "skyforge_wby_wave1_rope_sanity",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int ROPE_SANITY_TICKET_DISTANCE = 1;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object pipeline;
    private static Object body;
    private static Object rope;
    private static UUID bodyId;
    private static Vector3d worldAnchor;
    private static Vector3d initialBodyPosition;
    private static Vector3d targetBodyPosition;
    private static ChunkPos initialBodyChunk;
    private static ChunkPos targetBodyChunk;
    private static final Set<ChunkPos> fixtureTicketChunks = new LinkedHashSet<>();
    private static int loadedChunksBefore = -1;
    private static int forcedChunksBefore = -1;
    private static long firstTick = Long.MIN_VALUE;
    private static long ticketPlanReadyTick = Long.MIN_VALUE;
    private static long ropeCreatedTick = Long.MIN_VALUE;
    private static long boundaryCrossedTick = Long.MIN_VALUE;
    private static double maxStartEndpointError;
    private static double maxEndEndpointError;
    private static boolean assembled;
    private static boolean moved;
    private static boolean complete;

    private SkyforgeWbyWave1RopeSanityLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1RopeSanityLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || complete) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
            firstTick = level.getGameTime();
        }

        long now = level.getGameTime();
        if (now - firstTick > SETUP_TIMEOUT_TICKS) {
            fail(event, "WBY rope sanity timed out"
                    + "; assembled=" + assembled
                    + "; ropeActive=" + safeRopeActive()
                    + "; moved=" + moved
                    + "; bodyId=" + bodyId);
            return;
        }

        try {
            requireRuntimePreconditions();

            if (!assembled) {
                assembleBody();
                return;
            }

            if (loadedChunksBefore < 0 || forcedChunksBefore < 0) {
                if (ticketPlanReadyTick == Long.MIN_VALUE) {
                    throw new IllegalStateException("rope fixture ticket plan was not initialized");
                }
                if (now - ticketPlanReadyTick < TICKET_SETTLE_TICKS) {
                    return;
                }
                if (!fixtureTicketChunksLoadedEnough()) {
                    return;
                }
                loadedChunksBefore = level.getChunkSource().getLoadedChunksCount();
                forcedChunksBefore = level.getForcedChunks().size();
                return;
            }

            if (rope == null) {
                createAttachedRope();
                ropeCreatedTick = now;
                return;
            }

            updateAndValidateRope();

            if (!moved && now - ropeCreatedTick >= PRE_MOVE_SETTLE_TICKS) {
                relocateBodyAcrossChunkBoundary();
                moved = true;
                return;
            }

            if (moved) {
                ChunkPos currentChunk = chunkAt(bodyWorldPosition(body));
                boolean acrossBoundary =
                        currentChunk.x != initialBodyChunk.x || currentChunk.z != initialBodyChunk.z;
                if (acrossBoundary) {
                    if (boundaryCrossedTick == Long.MIN_VALUE) {
                        boundaryCrossedTick = now;
                    }
                    if (now - boundaryCrossedTick >= POST_MOVE_SETTLE_TICKS) {
                        complete(event);
                    }
                } else {
                    boundaryCrossedTick = Long.MIN_VALUE;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 rope sanity failed: " + failure);
        }
    }

    private static void assembleBody() throws ReflectiveOperationException {
        container = requireServerSubLevelContainer(level);
        Set<UUID> before = currentSubLevelIds(container);

        BlockPos bodyMin = new BlockPos(7, 100, 7);
        BlockPos bodyMax = new BlockPos(10, 102, 10);
        BlockPos assemblerPos = new BlockPos(8, 103, 8);
        BlockPos glueMax = new BlockPos(bodyMax.getX(), assemblerPos.getY(), bodyMax.getZ());

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
                    level.setBlock(new BlockPos(x, y, z), state, 3);
                }
            }
        }

        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        level.setBlock(assemblerPos, assemblerState, 3);
        addFixtureGlue(bodyMin, glueMax);

        BlockEntity assembler = level.getBlockEntity(assemblerPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY rope Physics Assembler unavailable at " + assemblerPos);
        }
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

        Set<UUID> after = currentSubLevelIds(container);
        LinkedHashSet<UUID> created = new LinkedHashSet<>(after);
        created.removeAll(before);
        if (created.size() != 1) {
            throw new IllegalStateException("expected exactly one Sable body for rope sanity, created=" + created);
        }

        bodyId = created.iterator().next();
        body = findSubLevel(container, bodyId);
        if (body == null) {
            throw new IllegalStateException("created Sable body was not retrievable: " + bodyId);
        }

        physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        pipeline = publicMethod(physicsSystem, "getPipeline").invoke(physicsSystem);
        initialBodyPosition = bodyWorldPosition(body);
        initialBodyChunk = chunkAt(initialBodyPosition);
        prepareTravelPlanAndTickets();
        ticketPlanReadyTick = level.getGameTime();
        loadedChunksBefore = -1;
        forcedChunksBefore = -1;
        assembled = true;
    }

    private static void createAttachedRope() throws ReflectiveOperationException {
        Vector3dc localCenterOfMass = bodyLocalCenterOfMass(body);
        Vector3d bodyWorld = bodyWorldPosition(body);

        List<Vector3d> points = new ArrayList<>(ROPE_POINTS);
        for (int i = 0; i < ROPE_POINTS; i++) {
            double t = (double) i / (ROPE_POINTS - 1);
            points.add(new Vector3d(
                    worldAnchor.x + (bodyWorld.x - worldAnchor.x) * t,
                    worldAnchor.y + (bodyWorld.y - worldAnchor.y) * t,
                    worldAnchor.z + (bodyWorld.z - worldAnchor.z) * t));
        }

        Class<?> ropeClass =
                Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject");
        Constructor<?> constructor = ropeClass.getConstructor(java.util.Collection.class, double.class);
        rope = constructor.newInstance(points, ROPE_RADIUS);
        publicMethod(physicsSystem, "addObject", Class.forName(
                        "dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject"))
                .invoke(physicsSystem, rope);

        Class<?> pointClass =
                Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopeHandle$AttachmentPoint");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object start = Enum.valueOf((Class<? extends Enum>) pointClass.asSubclass(Enum.class), "START");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object end = Enum.valueOf((Class<? extends Enum>) pointClass.asSubclass(Enum.class), "END");

        Method setAttachment = methodByNameAndArity(rope, "setAttachment", 3);
        setAttachment.invoke(rope, start, worldAnchor, null);
        setAttachment.invoke(rope, end, localCenterOfMass, body);

        if (!(Boolean) publicMethod(rope, "isActive").invoke(rope)) {
            throw new IllegalStateException("Sable rope did not become active after physics-system addition");
        }
    }

    private static void prepareTravelPlanAndTickets() {
        worldAnchor = new Vector3d(
                initialBodyPosition.x - WORLD_ANCHOR_OFFSET,
                initialBodyPosition.y,
                initialBodyPosition.z);

        int nextBoundaryX = (initialBodyChunk.x + 1) << 4;
        double targetX = nextBoundaryX + 3.0;
        if (targetX - initialBodyPosition.x < 4.0) {
            targetX += 16.0;
        }
        targetBodyPosition = new Vector3d(
                targetX,
                initialBodyPosition.y,
                initialBodyPosition.z);
        targetBodyChunk = chunkAt(targetBodyPosition);
        if (targetBodyChunk.x == initialBodyChunk.x && targetBodyChunk.z == initialBodyChunk.z) {
            throw new IllegalStateException(
                    "rope sanity target did not cross a chunk boundary: "
                            + initialBodyChunk + " -> " + targetBodyChunk);
        }

        ChunkPos anchorChunk = chunkAt(worldAnchor);
        int minX = Math.min(anchorChunk.x, Math.min(initialBodyChunk.x, targetBodyChunk.x));
        int maxX = Math.max(anchorChunk.x, Math.max(initialBodyChunk.x, targetBodyChunk.x));
        int minZ = Math.min(anchorChunk.z, Math.min(initialBodyChunk.z, targetBodyChunk.z));
        int maxZ = Math.max(anchorChunk.z, Math.max(initialBodyChunk.z, targetBodyChunk.z));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                fixtureTicketChunks.add(chunk);
                level.getChunkSource().addRegionTicket(
                        ROPE_SANITY_TICKET,
                        chunk,
                        ROPE_SANITY_TICKET_DISTANCE,
                        chunk);
                level.getChunk(x, z);
            }
        }
    }

    private static boolean fixtureTicketChunksLoadedEnough() throws ReflectiveOperationException {
        Class<?> ticketManagerClass =
                Class.forName("dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager");
        Method isLoadedEnough =
                ticketManagerClass.getMethod("isChunkLoadedEnough", ServerLevel.class, int.class, int.class);
        for (ChunkPos chunk : fixtureTicketChunks) {
            if (!(Boolean) isLoadedEnough.invoke(null, level, chunk.x, chunk.z)) {
                return false;
            }
        }
        return true;
    }

    private static void relocateBodyAcrossChunkBoundary() throws ReflectiveOperationException {
        Object pose = publicMethod(body, "logicalPose").invoke(body);
        Object orientationValue = publicMethod(pose, "orientation").invoke(pose);
        if (!(orientationValue instanceof Quaterniondc orientation)) {
            throw new IllegalStateException(
                    "Sable body logical orientation was not Quaterniondc: " + orientationValue);
        }

        Method teleport = methodByNameAndArity(pipeline, "teleport", 3);
        teleport.invoke(pipeline, body, targetBodyPosition, new Quaterniond(orientation));
    }

    private static void updateAndValidateRope() throws ReflectiveOperationException {
        if (!(Boolean) publicMethod(rope, "isActive").invoke(rope)) {
            throw new IllegalStateException("Sable rope became inactive during chunk-transition proof");
        }

        publicMethod(rope, "updatePose").invoke(rope);
        Object pointsValue = publicMethod(rope, "getPoints").invoke(rope);
        if (!(pointsValue instanceof List<?> points) || points.size() != ROPE_POINTS) {
            throw new IllegalStateException("unexpected Sable rope point set: " + pointsValue);
        }

        for (Object value : points) {
            if (!(value instanceof Vector3dc point)
                    || !Double.isFinite(point.x())
                    || !Double.isFinite(point.y())
                    || !Double.isFinite(point.z())) {
                throw new IllegalStateException("rope produced non-finite point: " + value);
            }
        }

        Vector3dc start = (Vector3dc) points.getFirst();
        Vector3dc end = (Vector3dc) points.getLast();
        double startError = distance(start, worldAnchor);
        double endError = distance(end, bodyWorldPosition(body));
        maxStartEndpointError = Math.max(maxStartEndpointError, startError);
        maxEndEndpointError = Math.max(maxEndEndpointError, endError);

        if (startError > ENDPOINT_TOLERANCE) {
            throw new IllegalStateException("world-attached rope endpoint drifted " + startError + " blocks");
        }
        if (endError > ENDPOINT_TOLERANCE) {
            throw new IllegalStateException("Sable-attached rope endpoint drifted " + endError + " blocks");
        }
    }

    private static void complete(ServerTickEvent.Post event) throws ReflectiveOperationException {
        updateAndValidateRope();

        Vector3d finalBodyPosition = bodyWorldPosition(body);
        ChunkPos finalBodyChunk = chunkAt(finalBodyPosition);
        if (finalBodyChunk.x == initialBodyChunk.x && finalBodyChunk.z == initialBodyChunk.z) {
            throw new IllegalStateException(
                    "Sable body did not remain across the intended chunk boundary: "
                            + initialBodyChunk + " -> " + finalBodyChunk);
        }

        int loadedChunksAfter = level.getChunkSource().getLoadedChunksCount();
        int forcedChunksAfter = level.getForcedChunks().size();
        int loadedDelta = loadedChunksAfter - loadedChunksBefore;
        if (forcedChunksAfter != forcedChunksBefore) {
            throw new IllegalStateException(
                    "rope sanity changed forced-chunk count: "
                            + forcedChunksBefore + " -> " + forcedChunksAfter);
        }
        if (loadedDelta > MAX_LOADED_CHUNK_DELTA) {
            throw new IllegalStateException(
                    "rope sanity loaded-chunk growth exceeded bounded allowance: delta=" + loadedDelta);
        }

        if (!containsIdentity(
                (Iterable<?>) publicMethod(physicsSystem, "getArbitraryObjects").invoke(physicsSystem),
                rope)) {
            throw new IllegalStateException("rope vanished from Sable arbitrary-object registry");
        }
        if (findSubLevel(container, bodyId) == null) {
            throw new IllegalStateException("attached Sable body vanished during rope chunk transition");
        }

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ropeApiAvailable", true);
        evidence.put("ropeActive", true);
        evidence.put("ropePointCount", ROPE_POINTS);
        evidence.put("worldAttachmentStable", maxStartEndpointError <= ENDPOINT_TOLERANCE);
        evidence.put("sableAttachmentStable", maxEndEndpointError <= ENDPOINT_TOLERANCE);
        evidence.put("maxWorldAttachmentErrorBlocks", maxStartEndpointError);
        evidence.put("maxSableAttachmentErrorBlocks", maxEndEndpointError);
        evidence.put("bodyId", bodyId);
        evidence.put("initialBodyChunk", initialBodyChunk.x + "," + initialBodyChunk.z);
        evidence.put("targetBodyChunk", targetBodyChunk.x + "," + targetBodyChunk.z);
        evidence.put("finalBodyChunk", finalBodyChunk.x + "," + finalBodyChunk.z);
        evidence.put("chunkBoundaryCrossed", true);
        evidence.put("boundaryStableTicks", POST_MOVE_SETTLE_TICKS);
        evidence.put("bodyRelocationMethod", "SablePhysicsPipeline.teleport");
        evidence.put("targetBodyPosition", targetBodyPosition.x + "," + targetBodyPosition.y + "," + targetBodyPosition.z);
        evidence.put("fixtureTicketChunks", fixtureTicketChunks.size());
        evidence.put("ticketSettleTicks", TICKET_SETTLE_TICKS);
        evidence.put("fixtureTicketChunksLoadedEnough", true);
        evidence.put("loadedChunksBefore", loadedChunksBefore);
        evidence.put("loadedChunksAfter", loadedChunksAfter);
        evidence.put("loadedChunkDelta", loadedDelta);
        evidence.put("forcedChunksBefore", forcedChunksBefore);
        evidence.put("forcedChunksAfter", forcedChunksAfter);
        evidence.put("forcedChunkGrowth", forcedChunksAfter - forcedChunksBefore);
        evidence.put("ropeChunkTransitionQualified", true);

        releaseFixtureTickets();
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(event.getServer(), evidence);
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

    private static Object findSubLevel(Object value, UUID expected) throws ReflectiveOperationException {
        Object subLevelsValue = publicMethod(value, "getAllSubLevels").invoke(value);
        if (!(subLevelsValue instanceof List<?> subLevels)) {
            return null;
        }
        for (Object subLevel : subLevels) {
            if (expected.equals(publicMethod(subLevel, "getUniqueId").invoke(subLevel))) {
                return subLevel;
            }
        }
        return null;
    }

    private static Vector3d bodyWorldPosition(Object subLevel) throws ReflectiveOperationException {
        Object pose = publicMethod(subLevel, "logicalPose").invoke(subLevel);
        Vector3dc position = (Vector3dc) publicMethod(pose, "position").invoke(pose);
        return new Vector3d(position);
    }

    private static Vector3dc bodyLocalCenterOfMass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = publicMethod(subLevel, "getMassTracker").invoke(subLevel);
        Object center = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
        if (!(center instanceof Vector3dc vector)) {
            throw new IllegalStateException("Sable body center of mass unavailable: " + center);
        }
        return new Vector3d(vector);
    }

    private static ChunkPos chunkAt(Vector3dc position) {
        return new ChunkPos(
                ((int) Math.floor(position.x())) >> 4,
                ((int) Math.floor(position.z())) >> 4);
    }

    private static double distance(Vector3dc a, Vector3dc b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean containsIdentity(Iterable<?> values, Object target) {
        for (Object value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add WBY rope Super Glue fixture");
        }
    }

    private static void requireRuntimePreconditions() {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY rope-sanity server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required WBY rope block is not registered: " + id);
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

    private static Method methodByNameAndArity(Object target, String name, int arity)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "/" + arity);
    }

    private static Object safeRopeActive() {
        try {
            return rope != null ? publicMethod(rope, "isActive").invoke(rope) : null;
        } catch (ReflectiveOperationException failure) {
            return "<unavailable:" + failure + ">";
        }
    }

    private static void releaseFixtureTickets() {
        if (level == null) {
            return;
        }
        for (ChunkPos chunk : fixtureTicketChunks) {
            level.getChunkSource().removeRegionTicket(
                    ROPE_SANITY_TICKET,
                    chunk,
                    ROPE_SANITY_TICKET_DISTANCE,
                    chunk);
        }
        fixtureTicketChunks.clear();
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        releaseFixtureTickets();
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
