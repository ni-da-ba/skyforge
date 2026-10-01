package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * WBY-INT-0002 fresh-process persistence fixture for the distant Sable visibility baseline.
 *
 * <p>Prepare assembles and saves one bounded Sable body. Reload consumes Sable's accepted
 * persistent UUID + GlobalSavedSubLevelPointer locator contract, reacquires current physics
 * authority, and exposes the recovered body to the actual-client renderer proof.
 */
final class SkyforgeWbyWave1PersistenceAcceptance {
    static final String PHASE_PROPERTY = "skyforge.dev.wbyWave1Persistence";
    private static final Path IDENTITY_FILE = Path.of("wby-wave1-persistence.identity");
    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final TicketType<ChunkPos> FIXTURE_CHUNK_TICKET = TicketType.create(
            "skyforge_wby_wave1_persistence", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;

    private static final BlockPos BODY_MIN = new BlockPos(256, 200, 0);
    private static final BlockPos BODY_MAX = new BlockPos(259, 202, 3);
    private static final BlockPos ASSEMBLER_POS = new BlockPos(257, 203, 1);

    private static final long ASSEMBLY_DEADLINE_TICKS = 120L;
    private static final long PHYSICS_DEADLINE_TICKS = 100L;
    private static final long HOLDING_DEADLINE_TICKS = 160L;
    private static final long CANONICAL_DEADLINE_TICKS = 120L;
    private static final long REHYDRATION_DEADLINE_TICKS = 120L;

    private static String phase;
    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object body;
    private static UUID bodyId;
    private static Set<UUID> beforeIds = Set.of();
    private static final Set<ChunkPos> activeChunkTickets = new LinkedHashSet<>();
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static boolean previousPhysicsPaused;
    private static boolean physicsPauseChanged;
    private static boolean fixtureReady;
    private static boolean clientComplete;
    private static boolean loadRequested;
    private static boolean pointerVerified;
    private static boolean currentPhysicsHandleValid;
    private static boolean viewerStateCaptured;
    private static ServerPlayer viewer;
    private static boolean viewerOriginalInvulnerable;
    private static long stageStartTick;
    private static Stage stage;
    private static PersistenceIdentity persisted;
    private static Vec3 expectedBodyCenter;
    private static double serverReloadTransformErrorBlocks;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        HOLDING_DISCOVERY,
        CANONICALIZATION,
        PHYSICS_REHYDRATION,
        READY
    }

    record ReloadSnapshot(
            UUID bodyId,
            Vec3 expectedBodyCenter,
            boolean samePersistentUuid,
            boolean pointerVerified,
            boolean currentPhysicsHandleValid,
            double serverReloadTransformErrorBlocks) {}

    private record PointerState(int chunkX, int chunkZ, short storageIndex, short subLevelIndex) {}

    private record PersistenceIdentity(UUID bodyId, PointerState pointer, Vec3 expectedBodyCenter) {}

    private SkyforgeWbyWave1PersistenceAcceptance() {}

    static void installFromSystemProperty() {
        phase = System.getProperty(PHASE_PROPERTY, "").trim();
        if (phase.isEmpty()) {
            return;
        }
        if (!phase.equals("prepare") && !phase.equals("reload")) {
            throw new IllegalArgumentException(PHASE_PROPERTY + " must be prepare or reload, got " + phase);
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1PersistenceAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1PersistenceAcceptance::onServerTickPost);
    }

    static ReloadSnapshot reloadSnapshot() {
        if (!fixtureReady || persisted == null || expectedBodyCenter == null) {
            return null;
        }
        return new ReloadSnapshot(
                bodyId,
                expectedBodyCenter,
                persisted.bodyId().equals(bodyId),
                pointerVerified,
                currentPhysicsHandleValid,
                serverReloadTransformErrorBlocks);
    }

    static void markClientComplete() {
        clientComplete = true;
        restoreFixtureState();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            if (phase.equals("prepare")) {
                beginPrepare();
            } else {
                beginReload();
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event.getServer(), "WBY Wave 1 persistence startup failed: " + failure);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (clientComplete || level == null || stage == null) {
            return;
        }
        try {
            long now = level.getGameTime();
            switch (stage) {
                case ASSEMBLY -> pollAssembly(event.getServer(), now);
                case PHYSICS_INITIALIZATION -> pollPreparePhysics(event.getServer(), now);
                case HOLDING_DISCOVERY -> pollHoldingDiscovery(event.getServer(), now);
                case CANONICALIZATION -> pollCanonicalization(event.getServer(), now);
                case PHYSICS_REHYDRATION -> pollReloadPhysics(event.getServer(), now);
                case READY -> positionViewer();
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event.getServer(), "WBY Wave 1 persistence lifecycle failed: " + failure);
        }
    }

    private static void beginPrepare() throws ReflectiveOperationException {
        addChunkTicket(new ChunkPos(BODY_MIN));
        beforeIds = currentSubLevelIds();
        prepareFixture();

        BlockEntity assembler = level.getBlockEntity(ASSEMBLER_POS);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            throw new IllegalStateException("WBY persistence Physics Assembler unavailable at " + ASSEMBLER_POS);
        }

        stage = Stage.ASSEMBLY;
        stageStartTick = level.getGameTime();
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
        pollAssembly(level.getServer(), stageStartTick);
    }

    private static void pollAssembly(MinecraftServer server, long now) throws ReflectiveOperationException {
        Set<UUID> created = new LinkedHashSet<>(currentSubLevelIds());
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(server, "WBY persistence prepare created multiple Sable bodies: " + created);
            return;
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            Object canonical = findCanonicalBody(bodyId);
            if (canonical == null || sourceNonAirBlocks() != 0) {
                if (now - stageStartTick > ASSEMBLY_DEADLINE_TICKS) {
                    fail(server, "WBY persistence body/source transfer did not settle: bodyId=" + bodyId
                            + ", sourceNonAir=" + sourceNonAirBlocks());
                }
                return;
            }
            body = canonical;
            requireViableBody(canonical);
            addFixtureForceLoadTicket(canonical);
            stage = Stage.PHYSICS_INITIALIZATION;
            stageStartTick = now;
            pollPreparePhysics(server, now);
            return;
        }
        if (now - stageStartTick > ASSEMBLY_DEADLINE_TICKS) {
            fail(server, "WBY persistence prepare did not register exactly one body");
        }
    }

    private static void pollPreparePhysics(MinecraftServer server, long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        Object handle = canonical == null ? null : findCurrentPhysicsHandle(canonical);
        if (canonical != null && handle != null) {
            body = canonical;
            pausePhysics();
            expectedBodyCenter = bodyPosePosition(canonical);
            Object pointerBeforeSave = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);

            removeFixtureForceLoadTicket();
            if (!server.saveEverything(false, true, true)) {
                fail(server, "WBY persistence prepare saveEverything returned false");
                return;
            }

            Object pointer = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);
            if (pointer == null) {
                fail(server, "WBY persistence prepare saved without a Sable serialization pointer; before="
                        + pointerBeforeSave);
                return;
            }

            PointerState pointerState = pointerState(pointer);
            persisted = new PersistenceIdentity(bodyId, pointerState, expectedBodyCenter);
            writeIdentityFile(persisted);
            removeAllChunkTickets();

            LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("persistencePrepared", true);
            evidence.put("bodyId", bodyId);
            evidence.put("savedPointer", pointerState);
            evidence.put("expectedBodyCenter", expectedBodyCenter);
            evidence.put("saveSuccess", true);
            evidence.put("fixtureLivenessReleased", true);
            SkyforgeAutomatedAcceptanceHarness.completeServerCase(server, evidence);
            stage = Stage.READY;
            return;
        }
        if (now - stageStartTick > PHYSICS_DEADLINE_TICKS) {
            fail(server, "WBY persistence prepare body never acquired a valid current physics handle");
        }
    }

    private static void beginReload() {
        persisted = readIdentityFile();
        bodyId = persisted.bodyId();
        expectedBodyCenter = persisted.expectedBodyCenter();
        ChunkPos locator = new ChunkPos(persisted.pointer().chunkX(), persisted.pointer().chunkZ());
        addChunkTicket(locator);
        stage = Stage.HOLDING_DISCOVERY;
        stageStartTick = level.getGameTime();
    }

    private static void pollHoldingDiscovery(MinecraftServer server, long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical != null) {
            verifyCanonicalPointer(canonical);
            body = canonical;
            addFixtureForceLoadTicket(canonical);
            stage = Stage.PHYSICS_REHYDRATION;
            stageStartTick = now;
            pollReloadPhysics(server, now);
            return;
        }

        Object holding = holdingSubLevel(bodyId);
        if (holding != null) {
            Object pointer = publicMethod(holding, "pointer").invoke(holding);
            if (pointer == null) {
                if (now - stageStartTick > HOLDING_DEADLINE_TICKS) {
                    fail(server, "persisted WBY Sable UUID has no holding pointer after reload");
                }
                return;
            }
            PointerState actual = pointerState(pointer);
            if (!persisted.pointer().equals(actual)) {
                fail(server, "persisted WBY Sable pointer changed across restart: expected="
                        + persisted.pointer() + ", actual=" + actual);
                return;
            }
            pointerVerified = true;
            Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
            Method snatch = publicMethod(holdingMap, "snatchAndLoad", pointer.getClass(), UUID.class);
            snatch.invoke(holdingMap, pointer, bodyId);
            loadRequested = true;
            stage = Stage.CANONICALIZATION;
            stageStartTick = now;
            pollCanonicalization(server, now);
            return;
        }

        if (now - stageStartTick > HOLDING_DEADLINE_TICKS) {
            fail(server, "persisted WBY Sable UUID was not discoverable after loading its saved locator chunk");
        }
    }

    private static void pollCanonicalization(MinecraftServer server, long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical != null) {
            verifyCanonicalPointer(canonical);
            body = canonical;
            addFixtureForceLoadTicket(canonical);
            stage = Stage.PHYSICS_REHYDRATION;
            stageStartTick = now;
            pollReloadPhysics(server, now);
            return;
        }
        if (now - stageStartTick > CANONICAL_DEADLINE_TICKS) {
            fail(server, "persisted WBY Sable UUID never became canonical after load request=" + loadRequested);
        }
    }

    private static void pollReloadPhysics(MinecraftServer server, long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        Object handle = canonical == null ? null : findCurrentPhysicsHandle(canonical);
        if (canonical != null && handle != null) {
            body = canonical;
            requireViableBody(canonical);
            verifyCanonicalPointer(canonical);
            currentPhysicsHandleValid = true;
            pausePhysics();

            Vec3 actual = bodyPosePosition(canonical);
            serverReloadTransformErrorBlocks = actual.distanceTo(expectedBodyCenter);
            if (serverReloadTransformErrorBlocks > 8.0) {
                fail(server, "reloaded WBY body transform drifted before client proof: errorBlocks="
                        + serverReloadTransformErrorBlocks);
                return;
            }

            Set<UUID> live = currentSubLevelIds();
            if (!live.contains(bodyId)) {
                fail(server, "reloaded WBY body UUID is not canonical: live=" + live);
                return;
            }

            fixtureReady = true;
            stage = Stage.READY;
            positionViewer();
            return;
        }
        if (now - stageStartTick > REHYDRATION_DEADLINE_TICKS) {
            fail(server, "persisted WBY Sable UUID did not regain valid current physics authority");
        }
    }

    private static void positionViewer() {
        if (!fixtureReady || clientComplete || level.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
        if (!viewerStateCaptured) {
            viewer = player;
            viewerOriginalInvulnerable = player.isInvulnerable();
            viewerStateCaptured = true;
            player.setInvulnerable(true);
        }
        player.teleportTo(
                expectedBodyCenter.x - SkyforgeWbyWave1VisibilityLifecycleAcceptance.HORIZONTAL_OFFSET_BLOCKS,
                player.getY(),
                expectedBodyCenter.z);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void verifyCanonicalPointer(Object canonical) throws ReflectiveOperationException {
        Object pointer = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);
        if (pointer == null) {
            throw new IllegalStateException("canonical reloaded WBY body has no serialization pointer");
        }
        PointerState actual = pointerState(pointer);
        if (!persisted.pointer().equals(actual)) {
            throw new IllegalStateException("canonical WBY serialization pointer changed: expected="
                    + persisted.pointer() + ", actual=" + actual);
        }
        pointerVerified = true;
    }

    private static void requireRuntimePreconditions() throws ClassNotFoundException {
        for (String modId : List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY persistence server mod not loaded: " + modId);
            }
        }
        requireBlock(PHYSICS_ASSEMBLER);
        Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Class.forName("dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer");
    }

    private static void prepareFixture() {
        for (int x = BODY_MIN.getX() - 2; x <= BODY_MAX.getX() + 2; x++) {
            for (int y = BODY_MIN.getY() - 2; y <= ASSEMBLER_POS.getY() + 2; y++) {
                for (int z = BODY_MIN.getZ() - 2; z <= BODY_MAX.getZ() + 2; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    // Use Sable-native sticky connectivity for the persistence fixture.
                    // PLATFORM-010 established this as the accepted transfer authority;
                    // Create Super Glue is not a substitute for Sable source transfer.
                    BlockState state = Blocks.SLIME_BLOCK.defaultBlockState();
                    if (!level.setBlock(new BlockPos(x, y, z), state, 3)) {
                        throw new IllegalStateException("failed to place WBY persistence body block");
                    }
                }
            }
        }
        BlockState assemblerState =
                withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(ASSEMBLER_POS, assemblerState, 3)) {
            throw new IllegalStateException("failed to place WBY persistence Physics Assembler");
        }
    }

    private static int sourceNonAirBlocks() {
        int count = level.getBlockState(ASSEMBLER_POS).isAir() ? 0 : 1;
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void requireViableBody(Object canonical) throws ReflectiveOperationException {
        boolean removed = Boolean.TRUE.equals(publicMethod(canonical, "isRemoved").invoke(canonical));
        Object massTracker = publicMethod(canonical, "getMassTracker").invoke(canonical);
        double mass = ((Number) publicMethod(massTracker, "getMass").invoke(massTracker)).doubleValue();
        if (removed || !(mass > 0.0)) {
            throw new IllegalStateException("WBY persistence body invalid: removed=" + removed + ", mass=" + mass);
        }
    }

    private static Vec3 bodyPosePosition(Object canonical) throws ReflectiveOperationException {
        Object pose = publicMethod(canonical, "logicalPose").invoke(canonical);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                number(publicMethod(position, "x").invoke(position)),
                number(publicMethod(position, "y").invoke(position)),
                number(publicMethod(position, "z").invoke(position)));
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

    private static Object holdingSubLevel(UUID uuid) throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        return publicMethod(holdingMap, "getHoldingSubLevel", UUID.class).invoke(holdingMap, uuid);
    }

    private static Object findCurrentPhysicsHandle(Object canonical) throws ReflectiveOperationException {
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonical).invoke(physicsSystem, canonical);
        if (handle == null) {
            return null;
        }
        return Boolean.TRUE.equals(publicMethod(handle, "isValid").invoke(handle)) ? handle : null;
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
            throw new IllegalStateException("could not add bounded Sable persistence liveness ticket");
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
            throw new IllegalStateException("could not remove bounded Sable persistence liveness ticket");
        }
        forceLoadTicketAdded = false;
    }

    private static void pausePhysics() throws ReflectiveOperationException {
        if (physicsSystem == null || physicsPauseChanged) {
            return;
        }
        previousPhysicsPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (!previousPhysicsPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
            physicsPauseChanged = true;
        }
    }

    private static void addChunkTicket(ChunkPos chunk) {
        if (!activeChunkTickets.add(chunk)) {
            return;
        }
        level.getChunkSource().addRegionTicket(FIXTURE_CHUNK_TICKET, chunk, TICKET_DISTANCE, chunk);
        level.getChunk(chunk.x, chunk.z);
    }

    private static void removeAllChunkTickets() {
        if (level == null) {
            return;
        }
        for (ChunkPos chunk : List.copyOf(activeChunkTickets)) {
            level.getChunkSource().removeRegionTicket(FIXTURE_CHUNK_TICKET, chunk, TICKET_DISTANCE, chunk);
            activeChunkTickets.remove(chunk);
        }
    }

    private static PointerState pointerState(Object pointer) throws ReflectiveOperationException {
        Object chunkValue = publicMethod(pointer, "chunkPos").invoke(pointer);
        if (!(chunkValue instanceof ChunkPos chunk)) {
            throw new IllegalStateException("Sable pointer chunkPos is not ChunkPos");
        }
        short storage = ((Number) publicMethod(pointer, "storageIndex").invoke(pointer)).shortValue();
        short subLevel = ((Number) publicMethod(pointer, "subLevelIndex").invoke(pointer)).shortValue();
        return new PointerState(chunk.x, chunk.z, storage, subLevel);
    }

    private static void writeIdentityFile(PersistenceIdentity identity) {
        String value = identity.bodyId() + "\n"
                + identity.pointer().chunkX() + "," + identity.pointer().chunkZ() + ","
                + identity.pointer().storageIndex() + "," + identity.pointer().subLevelIndex() + "\n"
                + identity.expectedBodyCenter().x + "," + identity.expectedBodyCenter().y + ","
                + identity.expectedBodyCenter().z + "\n";
        try {
            Files.writeString(IDENTITY_FILE, value);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write WBY persistence identity sidecar", exception);
        }
    }

    private static PersistenceIdentity readIdentityFile() {
        try {
            List<String> lines = Files.readAllLines(IDENTITY_FILE);
            if (lines.size() != 3) {
                throw new IllegalStateException("WBY persistence identity expected 3 lines, got " + lines.size());
            }
            String[] pointer = lines.get(1).split(",", -1);
            String[] center = lines.get(2).split(",", -1);
            if (pointer.length != 4 || center.length != 3) {
                throw new IllegalStateException("invalid WBY persistence identity sidecar");
            }
            return new PersistenceIdentity(
                    UUID.fromString(lines.get(0).trim()),
                    new PointerState(
                            Integer.parseInt(pointer[0]),
                            Integer.parseInt(pointer[1]),
                            Short.parseShort(pointer[2]),
                            Short.parseShort(pointer[3])),
                    new Vec3(
                            Double.parseDouble(center[0]),
                            Double.parseDouble(center[1]),
                            Double.parseDouble(center[2])));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to read WBY persistence identity sidecar", exception);
        }
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required WBY persistence block not registered: " + id);
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

    private static double number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("expected Number, got " + value);
        }
        return number.doubleValue();
    }

    private static void restoreFixtureState() {
        if (viewerStateCaptured && viewer != null) {
            viewer.setInvulnerable(viewerOriginalInvulnerable);
            viewerStateCaptured = false;
        }
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException ignored) {
            // Disposable acceptance process is stopping.
        }
        removeAllChunkTickets();
        if (physicsSystem != null && physicsPauseChanged) {
            try {
                publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, previousPhysicsPaused);
            } catch (ReflectiveOperationException ignored) {
                // Disposable acceptance process is stopping.
            }
            physicsPauseChanged = false;
        }
    }

    private static void fail(MinecraftServer server, String reason) {
        restoreFixtureState();
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }
}
