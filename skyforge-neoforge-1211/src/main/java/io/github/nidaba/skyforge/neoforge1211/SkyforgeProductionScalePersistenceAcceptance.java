package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

/** PLATFORM-010: off-origin production-footprint Sable primary-body persistence. */
final class SkyforgeProductionScalePersistenceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformProductionScalePersistence";
    static final String CAPABILITY = "SABLE_PRODUCTION_SCALE_PERSISTENCE_LIFECYCLE";
    private static final String PREFIX = "COMPILER_PLATFORM_PRODUCTION_SCALE_PERSISTENCE";
    private static final System.Logger LOGGER = System.getLogger(SkyforgeProductionScalePersistenceAcceptance.class.getName());
    private static final Path IDENTITY_FILE = Path.of("platform-010-production-scale-persistence.identity");

    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");
    private static final ResourceLocation PAYLOAD_BLOCK = id("minecraft:slime_block");
    private static final int EXPECTED_TRANSFER_COUNT = 118;

    // 11x11 minus four corners = 117 inert, sticky payload cells. The assembler is the 118th transferred cell.
    // The footprint intentionally crosses x chunks 7/8 and z chunks 0/1 at the production-aircraft world region.
    private static final int MIN_X = 124;
    private static final int MAX_X = 134;
    private static final int PAYLOAD_Y = 220;
    private static final int MIN_Z = 12;
    private static final int MAX_Z = 22;
    private static final BlockPos ASSEMBLER_POS = new BlockPos(129, 221, 17);
    private static final int TICKET_DISTANCE = 3;
    private static final TicketType<ChunkPos> FIXTURE_CHUNK_TICKET = TicketType.create(
            "skyforge_platform_010", Comparator.comparingLong(ChunkPos::toLong));

    private static final long ASSEMBLY_DEADLINE_TICKS = 100L;
    private static final long PHYSICS_DEADLINE_TICKS = 60L;
    private static final long HOLDING_DISCOVERY_DEADLINE_TICKS = 120L;
    private static final long CANONICALIZATION_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_REHYDRATION_DEADLINE_TICKS = 80L;

    private static String phase;
    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object assembledBody;
    private static UUID bodyId;
    private static BlockPos movedOffset;
    private static Set<UUID> beforeIds = Set.of();
    private static final Set<ChunkPos> activeChunkTickets = new LinkedHashSet<>();
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static boolean complete;
    private static boolean loadRequested;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static PersistenceIdentity persisted;
    private static Object observedHolding;
    private static Object observedPointer;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        HOLDING_DISCOVERY,
        CANONICALIZATION,
        PHYSICS_REHYDRATION
    }

    private SkyforgeProductionScalePersistenceAcceptance() {}

    static void installFromSystemProperty() {
        phase = System.getProperty(ENABLE_PROPERTY, "").trim();
        if (phase.isEmpty()) return;
        if (!phase.equals("prepare") && !phase.equals("verify")) {
            throw new IllegalArgumentException(ENABLE_PROPERTY + " must be prepare or verify, got " + phase);
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeProductionScalePersistenceAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeProductionScalePersistenceAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " phase=" + phase
                        + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            if (phase.equals("verify")) {
                beginVerify(now);
                return;
            }
            beginPrepare(now);
        } catch (ReflectiveOperationException exception) {
            failReflection(stageFailureCode(), exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(stageFailureCode(), finalDiagnostic(exception.toString()),
                        "production-scale persistence fixture failed: " + exception);
            }
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || stage == null || waitDiagnostic == null) return;
        long now = level.getGameTime();
        try {
            switch (stage) {
                case ASSEMBLY -> pollAssembly(now);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case HOLDING_DISCOVERY -> pollHoldingDiscovery(now);
                case CANONICALIZATION -> pollCanonicalization(now);
                case PHYSICS_REHYDRATION -> pollPhysicsRehydration(now);
            }
        } catch (ReflectiveOperationException exception) {
            failReflection(stageFailureCode(), exception);
        } catch (RuntimeException exception) {
            if (!complete) fail(stageFailureCode(), finalDiagnostic(exception.toString()),
                    "production-scale persistence lifecycle failed: " + exception);
        }
    }

    private static void beginPrepare(long now) throws ReflectiveOperationException {
        List<ChunkPos> sourceChunks = sourceChunks();
        for (ChunkPos chunk : sourceChunks) addChunkTicket(chunk);
        beforeIds = currentSubLevelIds();
        prepareFixture();
        if (payloadPositions().size() + 1 != EXPECTED_TRANSFER_COUNT) {
            throw new IllegalStateException("fixture transfer count drifted: payload=" + payloadPositions().size());
        }
        BlockEntity assembler = requireExpectedBlockEntity(ASSEMBLER_POS, "PhysicsAssemblerBlockEntity");
        stage = Stage.ASSEMBLY;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                "off-origin 118-cell Physics Assembler fixture creates one canonical Sable body",
                now, now + ASSEMBLY_DEADLINE_TICKS, sourceIds(), safeServerState(),
                "sourceChunks=" + sourceChunks + " fixtureChunkTickets=" + activeChunkTickets);
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
        pollAssembly(now);
    }

    private static void pollAssembly(long now) throws ReflectiveOperationException {
        Set<UUID> created = new LinkedHashSet<>(currentSubLevelIds());
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "reduced fixture created multiple Sable bodies");
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            Object body = findCanonicalBody(bodyId);
            if (body == null) return;
            assembledBody = body;
            Object massTracker = publicMethod(body, "getMassTracker").invoke(body);
            double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            boolean removed = Boolean.TRUE.equals(publicMethod(body, "isRemoved").invoke(body));
            int sourceNonAir = countSourceNonAir();
            if (removed || !(mass > 0.0) || centerOfMass == null) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "mass=" + mass + " centerOfMass=" + centerOfMass + " removed=" + removed
                                        + " sourceNonAir=" + sourceNonAir),
                        "assembled production-scale body is invalid");
            }
            if (sourceNonAir != 0) {
                if (waitDiagnostic.expired(now)) {
                    fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                            waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                    "bodyId=" + bodyId + " sourceNonAir=" + sourceNonAir),
                            "source transfer did not settle before deadline");
                }
                return;
            }
            movedOffset = movedOffset(body, centerOfMass);
            verifyPayload(body, false);
            if (!forceLoadTicketAdded) addFixtureForceLoadTicket(body);
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            stage = Stage.PHYSICS_INITIALIZATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "canonical production-scale Sable body acquires a valid current physics handle",
                    now, now + PHYSICS_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "mass=" + mass + " movedOffset=" + movedOffset + " fixtureChunkTickets=" + activeChunkTickets);
            pollPhysicsInitialization(now);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                            "createdIds=" + created + " holdingIds=" + currentHoldingSubLevelIds()),
                    "production-scale Sable registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        Object handle = canonical == null ? null : findCurrentPhysicsHandle(canonical);
        if (canonical != null && handle != null) {
            assembledBody = canonical;
            Object pointerBeforeSave = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);
            removeFixtureForceLoadTicket();
            if (!level.getServer().saveEverything(false, true, true)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("saveEverything=false"),
                        "server did not report a successful PLATFORM-010 prepare save");
            }
            Object pointer = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);
            if (pointer == null) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("pointerBeforeSave=" + pointerBeforeSave + " pointerAfterSave=null"),
                        "Sable save completed without assigning a serialization pointer");
            }
            PointerState pointerState = pointerState(pointer);
            persisted = new PersistenceIdentity(bodyId, movedOffset, pointerState);
            writeIdentityFile(persisted);
            removeAllChunkTickets();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PREPARE PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId + " transferCount=" + EXPECTED_TRANSFER_COUNT
                            + " movedOffset=" + movedOffset + " pointerBeforeSave=" + pointerBeforeSave
                            + " savedPointer=" + pointerState + " sourceChunks=" + sourceChunks()
                            + " fixtureLivenessTicket=sable:command_forced(released)"
                            + " fixtureChunkTickets=skyforge_platform_010(released) saveSuccess=true clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "canonical=" + (canonical != null) + " physicsHandleValid=" + (handle != null)),
                    "production-scale body did not acquire a current physics handle");
        }
    }

    private static void beginVerify(long now) throws ReflectiveOperationException {
        persisted = readIdentityFile();
        bodyId = persisted.bodyId();
        movedOffset = persisted.movedOffset();
        Set<UUID> liveBeforeLocator = currentSubLevelIds();
        observedHolding = holdingSubLevel(bodyId);
        String preLocatorHolding = holdingSnapshot(observedHolding);
        ChunkPos locatorChunk = new ChunkPos(persisted.pointer().chunkX(), persisted.pointer().chunkZ());
        addChunkTicket(locatorChunk);
        stage = Stage.HOLDING_DISCOVERY;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                "loading the persisted Sable pointer chunk exposes the held UUID and saved pointer",
                now, now + HOLDING_DISCOVERY_DEADLINE_TICKS, movedIds(), safeServerState(),
                "liveBeforeLocator=" + liveBeforeLocator + " preLocatorHolding=" + preLocatorHolding
                        + " persistedPointer=" + persisted.pointer() + " locatorChunk=" + locatorChunk);
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " RELOAD_LOCATOR bodyId=" + bodyId + " preLocatorHolding=" + preLocatorHolding
                        + " persistedPointer=" + persisted.pointer() + " locatorChunk=" + locatorChunk);
        pollHoldingDiscovery(now);
    }

    private static void pollHoldingDiscovery(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical != null) {
            observedPointer = publicMethod(canonical, "getLastSerializationPointer").invoke(canonical);
            stage = Stage.PHYSICS_REHYDRATION;
            assembledBody = canonical;
            addFixtureForceLoadTicket(canonical);
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                    "already-canonical persisted UUID acquires valid current physics authority",
                    now, now + PHYSICS_REHYDRATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "canonicalizedBeforeExplicitSnatch=true pointer=" + observedPointer);
            pollPhysicsRehydration(now);
            return;
        }
        observedHolding = holdingSubLevel(bodyId);
        if (observedHolding != null) {
            observedPointer = publicMethod(observedHolding, "pointer").invoke(observedHolding);
            if (observedPointer != null) {
                PointerState actual = pointerState(observedPointer);
                if (!persisted.pointer().equals(actual)) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_HOLDING_POINTER,
                            waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                    "persistedPointer=" + persisted.pointer() + " actualPointer=" + actual),
                            "holding pointer changed across the fresh-process boundary");
                }
                Method snatch = publicMethod(publicMethod(container, "getHoldingChunkMap").invoke(container),
                        "snatchAndLoad", observedPointer.getClass(), UUID.class);
                snatch.invoke(publicMethod(container, "getHoldingChunkMap").invoke(container), observedPointer, bodyId);
                loadRequested = true;
                stage = Stage.CANONICALIZATION;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                        "snatchAndLoad of the persisted locator registers the same UUID canonically",
                        now, now + CANONICALIZATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "holding=" + holdingSnapshot(observedHolding) + " pointer=" + actual
                                + " loadRequested=true liveIds=" + currentSubLevelIds());
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " HOLDING_LOAD_REQUEST bodyId=" + bodyId + " pointer=" + actual);
                pollCanonicalization(now);
                return;
            }
        }
        if (waitDiagnostic.expired(now)) {
            SkyforgeCompilerIntegrationFailure code = observedHolding == null
                    ? SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_NOT_PERSISTED
                    : SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_HOLDING_POINTER;
            fail(code,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "holding=" + holdingSnapshot(observedHolding) + " holdingIds=" + currentHoldingSubLevelIds()
                                    + " persistedPointer=" + persisted.pointer()),
                    observedHolding == null
                            ? "persisted UUID was not discoverable after loading its saved holding chunk"
                            : "persisted holding UUID never exposed a usable pointer");
        }
    }

    private static void pollCanonicalization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical != null) {
            assembledBody = canonical;
            addFixtureForceLoadTicket(canonical);
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
            stage = Stage.PHYSICS_REHYDRATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                    "canonicalized persisted UUID acquires a valid current physics handle",
                    now, now + PHYSICS_REHYDRATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "loadRequested=" + loadRequested + " liveIds=" + currentSubLevelIds());
            pollPhysicsRehydration(now);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_LOAD_CANONICALIZATION,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "loadRequested=" + loadRequested + " liveIds=" + currentSubLevelIds()
                                    + " holding=" + holdingSnapshot(holdingSubLevel(bodyId))),
                    "persisted UUID load was requested but never became canonical");
        }
    }

    private static void pollPhysicsRehydration(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        Object handle = canonical == null ? null : findCurrentPhysicsHandle(canonical);
        if (canonical != null && handle != null) {
            verifyPayload(canonical, true);
            removeFixtureForceLoadTicket();
            removeAllChunkTickets();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " VERIFY PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId + " samePersistentUuid=true"
                            + " persistedPointer=" + persisted.pointer() + " loadRequested=" + loadRequested
                            + " currentPhysicsHandleValid=true transferCount=" + EXPECTED_TRANSFER_COUNT
                            + " payloadRecovered=true fixtureLivenessTicket=sable:command_forced(released)"
                            + " fixtureChunkTickets=skyforge_platform_010(released) clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_PHYSICS_REHYDRATION,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "canonical=" + (canonical != null) + " currentPhysicsHandleValid=" + (handle != null)),
                    "canonical persisted UUID did not regain valid current physics authority");
        }
    }

    private static void requireRuntimePreconditions() {
        long now = level.getGameTime();
        try {
            if (!ModList.get().isLoaded("simulated")) throw new IllegalStateException("Simulated mod not loaded");
            requireBlock(PHYSICS_ASSEMBLER);
            requireBlock(PAYLOAD_BLOCK);
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "exact C11 Sable/Simulated persistence runtime resolves",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(), exception.toString()),
                    "required PLATFORM-010 runtime dependency unavailable: " + exception);
        }
    }

    private static void prepareFixture() {
        for (BlockPos pos : payloadPositions()) {
            if (!level.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), 3)) {
                throw new IllegalStateException("failed to place inert payload cell at " + pos);
            }
        }
        BlockState assembler = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(ASSEMBLER_POS, assembler, 3)) {
            throw new IllegalStateException("failed to place Physics Assembler at " + ASSEMBLER_POS);
        }
    }

    private static List<BlockPos> payloadPositions() {
        List<BlockPos> result = new ArrayList<>(117);
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                boolean corner = (x == MIN_X || x == MAX_X) && (z == MIN_Z || z == MAX_Z);
                if (!corner) result.add(new BlockPos(x, PAYLOAD_Y, z));
            }
        }
        return List.copyOf(result);
    }

    private static List<ChunkPos> sourceChunks() {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos pos : payloadPositions()) chunks.add(new ChunkPos(pos));
        chunks.add(new ChunkPos(ASSEMBLER_POS));
        return List.copyOf(chunks);
    }

    private static void addChunkTicket(ChunkPos chunk) {
        if (!activeChunkTickets.add(chunk)) return;
        level.getChunkSource().addRegionTicket(FIXTURE_CHUNK_TICKET, chunk, TICKET_DISTANCE, chunk);
        level.getChunk(chunk.x, chunk.z);
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " FIXTURE_CHUNK_TICKET_ADD chunk=" + chunk + " distance=" + TICKET_DISTANCE);
    }

    private static void removeAllChunkTickets() {
        if (level == null) return;
        for (ChunkPos chunk : List.copyOf(activeChunkTickets)) {
            level.getChunkSource().removeRegionTicket(FIXTURE_CHUNK_TICKET, chunk, TICKET_DISTANCE, chunk);
            activeChunkTickets.remove(chunk);
            LOGGER.log(System.Logger.Level.INFO, PREFIX + " FIXTURE_CHUNK_TICKET_REMOVE chunk=" + chunk);
        }
    }

    private static int countSourceNonAir() {
        int count = level.getBlockState(ASSEMBLER_POS).isAir() ? 0 : 1;
        for (BlockPos pos : payloadPositions()) if (!level.getBlockState(pos).isAir()) count++;
        return count;
    }

    private static void verifyPayload(Object canonical, boolean reloaded) throws ReflectiveOperationException {
        int count = 0;
        for (BlockPos source : payloadPositions()) {
            BlockPos moved = source.offset(movedOffset);
            requireCanonicalPlotOwnership(canonical, moved);
            ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(moved).getBlock());
            if (!PAYLOAD_BLOCK.equals(actual)) {
                throw new IllegalStateException((reloaded ? "reloaded" : "moved")
                        + " payload mismatch at " + moved + ": expected=" + PAYLOAD_BLOCK + " actual=" + actual);
            }
            count++;
        }
        BlockPos movedAssembler = ASSEMBLER_POS.offset(movedOffset);
        requireCanonicalPlotOwnership(canonical, movedAssembler);
        ResourceLocation assemblerId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedAssembler).getBlock());
        if (!PHYSICS_ASSEMBLER.equals(assemblerId)) {
            throw new IllegalStateException("moved Physics Assembler mismatch at " + movedAssembler + ": " + assemblerId);
        }
        count++;
        if (count != EXPECTED_TRANSFER_COUNT) throw new IllegalStateException("payload verification count=" + count);
    }

    private static Object requireServerSubLevelContainer(ServerLevel serverLevel) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(serverLevel)) throw new IllegalStateException("ServerLevel lacks Sable container holder");
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(serverLevel);
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (value == null || !containerClass.isInstance(value)) throw new IllegalStateException("Sable container unavailable");
        return value;
    }

    private static Set<UUID> currentSubLevelIds() throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) throw new IllegalStateException("Sable getAllSubLevels is not List-like");
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object subLevel : subLevels) ids.add(subLevelUniqueId(subLevel));
        return ids;
    }

    private static Set<UUID> currentHoldingSubLevelIds() throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        Field field = holdingMap.getClass().getDeclaredField("allHoldingSubLevels");
        field.setAccessible(true);
        Object value = field.get(holdingMap);
        if (!(value instanceof Map<?, ?> holdings)) throw new IllegalStateException("Sable holding map is not Map-like");
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object key : holdings.keySet()) if (key instanceof UUID uuid) ids.add(uuid);
        return ids;
    }

    private static UUID subLevelUniqueId(Object subLevel) throws ReflectiveOperationException {
        Object value = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
        if (!(value instanceof UUID uuid)) throw new IllegalStateException("Sable sub-level ID is not UUID: " + value);
        return uuid;
    }

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) throw new IllegalStateException("Sable getAllSubLevels is not List-like");
        for (Object subLevel : subLevels) if (uuid.equals(subLevelUniqueId(subLevel))) return subLevel;
        return null;
    }

    private static Object holdingSubLevel(UUID uuid) throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        return publicMethod(holdingMap, "getHoldingSubLevel", UUID.class).invoke(holdingMap, uuid);
    }

    private static String holdingSnapshot(Object holding) throws ReflectiveOperationException {
        if (holding == null) return "missing";
        Object pointer = publicMethod(holding, "pointer").invoke(holding);
        return "present:pointer=" + (pointer == null ? "null" : pointerState(pointer));
    }

    private static PointerState pointerState(Object pointer) throws ReflectiveOperationException {
        Object chunkValue = publicMethod(pointer, "chunkPos").invoke(pointer);
        if (!(chunkValue instanceof ChunkPos chunk)) throw new IllegalStateException("pointer chunkPos is not ChunkPos");
        short storage = ((Number) publicMethod(pointer, "storageIndex").invoke(pointer)).shortValue();
        short subLevel = ((Number) publicMethod(pointer, "subLevelIndex").invoke(pointer)).shortValue();
        return new PointerState(chunk.x, chunk.z, storage, subLevel);
    }

    private static Object findCurrentPhysicsHandle(Object canonical) throws ReflectiveOperationException {
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonical).invoke(physicsSystem, canonical);
        if (handle == null) return null;
        return Boolean.TRUE.equals(publicMethod(handle, "isValid").invoke(handle)) ? handle : null;
    }

    private static BlockPos movedOffset(Object body, Object centerOfMass) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "logicalPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new BlockPos(
                exactIntegerOffset("X", component(centerOfMass, "x") - component(position, "x")),
                exactIntegerOffset("Y", component(centerOfMass, "y") - component(position, "y")),
                exactIntegerOffset("Z", component(centerOfMass, "z") - component(position, "z")));
    }

    private static int exactIntegerOffset(String axis, double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > 1.0e-6 || rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalStateException("non-integral Sable plot offset " + axis + "=" + value);
        }
        return (int) rounded;
    }

    private static double component(Object vector, String name) throws ReflectiveOperationException {
        if (vector instanceof Vector3dc joml) return switch (name) {
            case "x" -> joml.x(); case "y" -> joml.y(); case "z" -> joml.z();
            default -> throw new IllegalArgumentException("unknown vector component " + name);
        };
        return number(publicMethod(vector, name).invoke(vector));
    }

    private static void requireCanonicalPlotOwnership(Object canonical, BlockPos pos) throws ReflectiveOperationException {
        Object plot = publicMethod(canonical, "getPlot").invoke(canonical);
        boolean contained = Boolean.TRUE.equals(publicMethod(plot, "contains", double.class, double.class)
                .invoke(plot, pos.getX() + 0.5, pos.getZ() + 0.5));
        if (!contained) throw new IllegalStateException("canonical plot does not own moved position " + pos);
    }

    private static void addFixtureForceLoadTicket(Object body) throws ReflectiveOperationException {
        Class<?> ticketTypeClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        forceLoadTicketKey = Unit.INSTANCE;
        Method add = container.getClass().getMethod("addForceLoadTicket", body.getClass(), ticketTypeClass, Object.class);
        Object result = add.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) throw new IllegalStateException("could not add Sable liveness ticket");
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || assembledBody == null || forceLoadTicketType == null || forceLoadTicketKey == null) return;
        Method remove = container.getClass().getMethod(
                "removeForceLoadTicket", assembledBody.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object result = remove.invoke(container, assembledBody, forceLoadTicketType, forceLoadTicketKey);
        if (!(result instanceof Boolean ok) || !ok) throw new IllegalStateException("could not remove Sable liveness ticket");
        forceLoadTicketAdded = false;
    }

    private static BlockEntity requireExpectedBlockEntity(BlockPos pos, String suffix) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().endsWith(suffix)) {
            throw new IllegalStateException("required block entity missing/wrong at " + pos + ": "
                    + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
        }
        return blockEntity;
    }

    private static void writeIdentityFile(PersistenceIdentity identity) {
        String value = identity.bodyId() + "\n"
                + identity.movedOffset().getX() + "," + identity.movedOffset().getY() + "," + identity.movedOffset().getZ() + "\n"
                + identity.pointer().chunkX() + "," + identity.pointer().chunkZ() + ","
                + identity.pointer().storageIndex() + "," + identity.pointer().subLevelIndex() + "\n";
        try {
            Files.writeString(IDENTITY_FILE, value);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write PLATFORM-010 identity sidecar", exception);
        }
    }

    private static PersistenceIdentity readIdentityFile() {
        try {
            List<String> lines = Files.readAllLines(IDENTITY_FILE);
            if (lines.size() != 3) throw new IllegalStateException("PLATFORM-010 identity expected 3 lines");
            String[] offset = lines.get(1).split(",", -1);
            String[] pointer = lines.get(2).split(",", -1);
            if (offset.length != 3 || pointer.length != 4) throw new IllegalStateException("invalid PLATFORM-010 identity sidecar");
            return new PersistenceIdentity(
                    UUID.fromString(lines.get(0).trim()),
                    new BlockPos(Integer.parseInt(offset[0]), Integer.parseInt(offset[1]), Integer.parseInt(offset[2])),
                    new PointerState(Integer.parseInt(pointer[0]), Integer.parseInt(pointer[1]),
                            Short.parseShort(pointer[2]), Short.parseShort(pointer[3])));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to read PLATFORM-010 identity sidecar", exception);
        }
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("required block missing: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) if (property.getName().equals(name)) {
            return withParsedProperty(state, property, name, value);
        }
        throw new IllegalStateException("missing property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static Method publicMethod(Object target, String name, Class<?>... types) throws NoSuchMethodException {
        return target.getClass().getMethod(name, types);
    }

    private static Method oneArgMethod(Object target, String name, Object argument) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass()))) return method;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(1 arg)");
    }

    private static SkyforgeCompilerIntegrationFailure stageFailureCode() {
        if (stage == null) return SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE;
        return switch (stage) {
            case ASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
            case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
            case HOLDING_DISCOVERY -> SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_NOT_PERSISTED;
            case CANONICALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_LOAD_CANONICALIZATION;
            case PHYSICS_REHYDRATION -> SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE_PHYSICS_REHYDRATION;
        };
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            SkyforgeCompilerIntegrationPhase diagnosticPhase, String expected, long start, long deadline,
            String ids, String server, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                CAPABILITY, diagnosticPhase, expected, start, deadline, ids, server, "headless", dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        long now = level == null ? 0L : level.getGameTime();
        return waitDiagnostic == null
                ? diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT, "fixture reaches a classified terminal state",
                        now, now, movedIds(), safeServerState(), dump)
                : waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", dump);
    }

    private static String sourceIds() {
        return "assembler=" + ASSEMBLER_POS + " transferCount=" + EXPECTED_TRANSFER_COUNT + " sourceChunks=" + sourceChunks();
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " movedOffset=" + movedOffset + " persistedPointer="
                + (persisted == null ? "n/a" : persisted.pointer()) + " loadRequested=" + loadRequested;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        String live = "n/a";
        String holding = "n/a";
        try {
            if (container != null) {
                live = String.valueOf(currentSubLevelIds());
                holding = String.valueOf(currentHoldingSubLevelIds());
            }
        } catch (ReflectiveOperationException exception) {
            live = "<reflection-failed:" + exception.getClass().getSimpleName() + ">";
        }
        return "gameTime=" + level.getGameTime() + " stage=" + stage + " liveIds=" + live
                + " holdingIds=" + holding + " activeChunkTickets=" + activeChunkTickets;
    }

    private static void cleanupQuietly() {
        try { removeFixtureForceLoadTicket(); } catch (ReflectiveOperationException | RuntimeException ignored) {}
        try { removeAllChunkTickets(); } catch (RuntimeException ignored) {}
    }

    private static void failReflection(SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getTargetException() : exception;
        fail(code, finalDiagnostic("reflectionFailure=" + cause), "runtime reflection failed: " + cause);
    }

    private static void fail(
            SkyforgeCompilerIntegrationFailure code, SkyforgeCompilerIntegrationDiagnostic diagnostic, String reason) {
        if (!complete) {
            cleanupQuietly();
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(CAPABILITY + " failed: " + code + ": " + reason);
    }

    private static double number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("expected Number, got " + value);
        return number.doubleValue();
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private record PointerState(int chunkX, int chunkZ, short storageIndex, short subLevelIndex) {}
    private record PersistenceIdentity(UUID bodyId, BlockPos movedOffset, PointerState pointer) {}
}
