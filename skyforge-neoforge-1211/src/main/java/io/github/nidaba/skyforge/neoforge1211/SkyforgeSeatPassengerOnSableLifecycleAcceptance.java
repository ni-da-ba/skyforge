package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

/** PLATFORM-011: actual-client Create seat mount/dismount lifecycle on one live Sable body. */
final class SkyforgeSeatPassengerOnSableLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformSeatPassengerOnSableLifecycle";
    static final String CAPABILITY = "CREATE_SEAT_PASSENGER_ON_SABLE_LIFECYCLE";
    static final String CREATE_SOURCE_COMMIT = "ac0c444d9828da3453ae8cc65338e8de063286fb";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeSeatPassengerOnSableLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_SEAT_PASSENGER_ON_SABLE_LIFECYCLE";

    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");
    private static final ResourceLocation SEAT_ID = id("create:brown_seat");
    // Keep the body plus Sable's one-block physics envelope inside explicitly loaded chunk (0,0).
    private static final BlockPos BODY_MIN = new BlockPos(5, 200, 5);
    private static final BlockPos BODY_MAX = new BlockPos(6, 201, 6);
    private static final BlockPos SEAT_SOURCE = new BlockPos(5, 202, 5);
    private static final BlockPos ASSEMBLER_SOURCE = new BlockPos(6, 202, 6);
    private static final BlockPos GLUE_MIN = BODY_MIN;
    private static final BlockPos GLUE_MAX = ASSEMBLER_SOURCE;

    private static final long ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long SEAT_MOUNT_DEADLINE_TICKS = 240L;
    private static final long SEAT_DISMOUNT_DEADLINE_TICKS = 160L;
    private static final long SEAT_CLEANUP_DEADLINE_TICKS = 80L;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object assembledBody;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static Set<UUID> beforeIds = Set.of();
    private static UUID bodyId;
    private static UUID glueId;
    private static BlockPos movedOffset;
    private static BlockPos movedSeat;
    private static boolean physicsWasPaused;
    private static boolean physicsPauseChanged;
    private static boolean complete;
    private static volatile boolean playerPositioned;
    private static volatile boolean seatMountObserved;
    private static volatile boolean seatDismountObserved;
    private static volatile boolean seatEntityCleanupObserved;
    private static volatile UUID seatEntityId;
    private static boolean playerInvulnerabilityCaptured;
    private static boolean originalPlayerInvulnerable;
    private static boolean playerInvulnerabilityRestored;
    private static ServerPlayer positionedPlayer;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        CLIENT_MOUNT,
        CLIENT_DISMOUNT,
        CLIENT_CLEANUP
    }

    private SkyforgeSeatPassengerOnSableLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSeatPassengerOnSableLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeSeatPassengerOnSableLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now
                        + " createSourceCommit=" + CREATE_SOURCE_COMMIT + " clientState=actual-client-pending");
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            beforeIds = currentSubLevelIds();
            prepareFixture();
            glueId = addFixtureGlue();
            BlockEntity assembler = level.getBlockEntity(ASSEMBLER_SOURCE);
            if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "real Physics Assembler block entity exists before primary assembly",
                                now, now, sourceIds(), safeServerState(),
                                "assemblerBlockEntity=" + (assembler == null ? "null" : assembler.getClass().getName())),
                        "Physics Assembler block entity missing or wrong type");
            }
            stage = Stage.ASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                    "exactly one new Sable UUID is synchronously registered and all source cells transfer",
                    now, now + ASSEMBLY_DEADLINE_TICKS, sourceIds(), safeServerState(),
                    "glueId=" + glueId + " beforeSubLevelIds=" + beforeIds);
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
            pollAssembly(now, true);
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(
                        stage == Stage.ASSEMBLY
                                ? SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY
                                : SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                        diagnostic(
                                stage == Stage.ASSEMBLY
                                        ? SkyforgeCompilerIntegrationPhase.ASSEMBLY
                                        : SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "minimal Create-seat/Sable fixture is placed and assembled",
                                now, now, sourceIds(), safeServerState(), exception.toString()),
                        "fixture preparation failed: " + exception);
            }
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null || stage == null) {
            return;
        }
        long now = level.getGameTime();
        try {
            switch (stage) {
                case ASSEMBLY -> pollAssembly(now, false);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case CLIENT_MOUNT, CLIENT_DISMOUNT, CLIENT_CLEANUP -> pollClientLifecycle(event, now);
            }
        } catch (ReflectiveOperationException exception) {
            SkyforgeCompilerIntegrationFailure code = switch (stage) {
                case ASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
                case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
                default -> SkyforgeCompilerIntegrationFailure.FAIL_CLIENT_INTERACTION;
            };
            failReflection(code, exception);
        } catch (RuntimeException exception) {
            SkyforgeCompilerIntegrationFailure code = switch (stage) {
                case ASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
                case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
                case CLIENT_MOUNT -> SkyforgeCompilerIntegrationFailure.FAIL_CLIENT_INTERACTION;
                case CLIENT_DISMOUNT, CLIENT_CLEANUP -> SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING;
            };
            fail(code, finalDiagnostic(exception.toString()), "Create seat client lifecycle failed: " + exception);
        }
    }

    private static void pollAssembly(long now, boolean synchronousObservation) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds();
        Set<UUID> created = new LinkedHashSet<>(currentIds);
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "actual-client", "createdIds=" + created),
                    "minimal seat fixture created multiple Sable bodies");
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            Object listedBody = findListedBody(bodyId);
            if (listedBody == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "actual-client",
                                "bodyId=" + bodyId + " listedBody=null"),
                        "created Sable UUID could not be reselected");
            }
            int sourceNonAir = countSourceFixtureNonAir();
            Object massTracker = publicMethod(listedBody, "getMassTracker").invoke(listedBody);
            double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            boolean removed = Boolean.TRUE.equals(publicMethod(listedBody, "isRemoved").invoke(listedBody));
            if (sourceNonAir != 0 || removed || !(mass > 0.0) || centerOfMass == null) {
                fail(
                        removed || !(mass > 0.0) || centerOfMass == null
                                ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                                : SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "actual-client",
                                "bodyId=" + bodyId + " sourceNonAirAfterAssembly=" + sourceNonAir
                                        + " mass=" + mass + " centerOfMass=" + centerOfMass + " removed=" + removed),
                        "primary seat body did not reach a valid synchronous post-assembly state");
            }
            assembledBody = listedBody;
            movedOffset = movedOffset(listedBody, centerOfMass);
            movedSeat = SEAT_SOURCE.offset(movedOffset);
            requireMovedSeatBlock();
            addFixtureForceLoadTicket(listedBody);
            stage = Stage.PHYSICS_INITIALIZATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "persistent Sable UUID resolves to current body and valid current physics handle",
                    now, now + PHYSICS_INITIALIZATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                            + " fixtureLivenessTicket=sable:command_forced");
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " ASSEMBLED bodyId=" + bodyId + " movedOffset=" + movedOffset
                            + " movedSeat=" + movedSeat + " mass=" + mass);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "actual-client", "createdIds=" + created),
                    "Sable body registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client", "canonicalBody=null"),
                        "canonical live Sable body did not remain available");
            }
            return;
        }
        if (physicsSystem == null) {
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        }
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "currentPhysicsHandle=null-or-invalid"),
                        "current Sable physics handle did not become valid");
            }
            return;
        }
        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (!physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
            physicsPauseChanged = true;
        }
        requireCanonicalPlotOwnership(canonical, movedSeat);
        requireMovedSeatBlock();
        Vec3 standPlot = new Vec3(movedSeat.getX() + 0.5, movedSeat.getY() + 1.0, movedSeat.getZ() + 0.5);
        Vec3 expectedGlobalSeatCenter = projectThroughCanonicalBody(canonical, Vec3.atCenterOf(movedSeat));
        SkyforgeSeatPassengerOnSableBridge.publish(bodyId, movedSeat, standPlot, expectedGlobalSeatCenter);
        stage = Stage.CLIENT_MOUNT;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.CLIENT_INTERACTION,
                "actual client uses real Create SeatBlock and client/server observe the same SeatEntity mount",
                now, now + SEAT_MOUNT_DEADLINE_TICKS, movedIds(), safeServerState(),
                "currentPhysicsHandleValid=true; actualClientBridgePublished=true; testSetupPhysicsPinned=true"
                        + "; expectedGlobalSeatCenter=" + expectedGlobalSeatCenter);
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " CLIENT_READY bodyId=" + bodyId + " movedSeat=" + movedSeat
                        + " standPlot=" + standPlot + " expectedGlobalSeatCenter=" + expectedGlobalSeatCenter
                        + " currentPhysicsHandleValid=true testSetupPhysicsPinned=true"
                        + " originalPhysicsPaused=" + physicsWasPaused);
    }

    private static void pollClientLifecycle(ServerTickEvent.Post event, long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    finalDiagnostic("currentPhysicsHandle=null-or-invalid"),
                    "current Sable physics authority disappeared during seat interaction");
        }
        requireCanonicalPlotOwnership(canonical, movedSeat);
        requireMovedSeatBlock();

        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        if (!playerPositioned) {
            if (players.size() != 1) {
                if (waitDiagnostic.expired(now)) {
                    fail(
                            SkyforgeCompilerIntegrationFailure.FAIL_CLIENT_INTERACTION,
                            finalDiagnostic("connectedServerPlayers=" + players.size()),
                            "actual-client seat fixture did not acquire exactly one integrated ServerPlayer");
                }
                return;
            }
            positionedPlayer = players.getFirst();
            originalPlayerInvulnerable = positionedPlayer.isInvulnerable();
            playerInvulnerabilityCaptured = true;
            positionedPlayer.setInvulnerable(true);
            positionedPlayer.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            Vec3 globalStand = projectThroughCanonicalBody(
                    canonical, SkyforgeSeatPassengerOnSableBridge.snapshot().standPlotPosition());
            positionedPlayer.teleportTo(globalStand.x, globalStand.y, globalStand.z);
            positionedPlayer.setDeltaMovement(Vec3.ZERO);
            playerPositioned = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PLAYER_POSITIONED globalStand=" + globalStand
                            + " testSetupOnly=true playerSableTrackingQualified=false");
            return;
        }

        if (players.size() != 1 || positionedPlayer != players.getFirst()) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_CLIENT_INTERACTION,
                    finalDiagnostic("connectedServerPlayers=" + players.size()),
                    "integrated ServerPlayer identity changed during seat lifecycle");
        }

        Entity vehicle = positionedPlayer.getVehicle();
        if (stage == Stage.CLIENT_MOUNT) {
            if (positionedPlayer.isPassenger()
                    && vehicle != null
                    && vehicle.getClass().getName().endsWith("SeatEntity")) {
                List<Entity> seats = seatEntitiesAtMovedSeat();
                if (seats.size() != 1 || seats.getFirst() != vehicle || !vehicle.hasPassenger(positionedPlayer)) {
                    fail(
                            SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING,
                            finalDiagnostic("seatEntities=" + seatEntitySummary(seats)
                                    + " vehicle=" + entitySummary(vehicle)),
                            "server did not observe exactly one real Create SeatEntity carrying the player");
                }
                seatEntityId = vehicle.getUUID();
                seatMountObserved = true;
                stage = Stage.CLIENT_DISMOUNT;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.CLIENT_INTERACTION,
                        "ordinary client crouch input dismounts the same server SeatEntity passenger",
                        now, now + SEAT_DISMOUNT_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "seatEntityId=" + seatEntityId + " serverPassenger=true");
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " SEAT_MOUNT OBSERVED seatEntityId=" + seatEntityId
                                + " player=" + positionedPlayer.getUUID()
                                + " clientServerAgreementPending=true");
                return;
            }
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_SEAT_MOUNT,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "playerPassenger=" + positionedPlayer.isPassenger()
                                        + " vehicle=" + entitySummary(vehicle)
                                        + " seatEntities=" + seatEntitySummary(seatEntitiesAtMovedSeat())),
                        "ordinary actual-client Create seat use did not produce a bounded real mount");
            }
            return;
        }

        if (stage == Stage.CLIENT_DISMOUNT) {
            if (!positionedPlayer.isPassenger()) {
                seatDismountObserved = true;
                stage = Stage.CLIENT_CLEANUP;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.CLIENT_INTERACTION,
                        "dismounted Create SeatEntity is discarded and leaves no stale passenger authority",
                        now, now + SEAT_CLEANUP_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "seatEntityId=" + seatEntityId + " serverPassenger=false");
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " SEAT_DISMOUNT OBSERVED seatEntityId=" + seatEntityId
                                + " playerPassenger=false");
                return;
            }
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_SEAT_DISMOUNT,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "playerPassenger=true vehicle=" + entitySummary(vehicle)),
                        "ordinary actual-client crouch input did not dismount the server player");
            }
            return;
        }

        if (stage == Stage.CLIENT_CLEANUP) {
            Entity stale = seatEntityId == null ? null : level.getEntity(seatEntityId);
            List<Entity> seats = seatEntitiesAtMovedSeat();
            if ((stale == null || !stale.isAlive()) && seats.isEmpty()) {
                restorePlayerInvulnerability();
                restorePhysicsPause();
                removeFixtureForceLoadTicket();
                seatEntityCleanupObserved = true;
                complete = true;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " PASS capability=" + CAPABILITY
                                + " bodyId=" + bodyId + " movedSeat=" + movedSeat
                                + " seatEntityId=" + seatEntityId
                                + " actualClient=true seatMountObserved=true seatDismountObserved=true"
                                + " seatEntityCleanupObserved=true samePersistentSableUuid=true"
                                + " currentPhysicsHandleValid=true canonicalBodyResolutionPerPhase=true"
                                + " fixtureLivenessTicket=sable:command_forced(released)"
                                + " testSetupPhysicsPinned=true playerSableTrackingQualified=false");
                return;
            }
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_SEAT_ENTITY_CLEANUP,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "staleSeat=" + entitySummary(stale)
                                        + " seatEntities=" + seatEntitySummary(seats)),
                        "dismounted Create SeatEntity retained stale server passenger/entity authority");
            }
        }
    }

    static boolean playerPositioned() {
        return playerPositioned;
    }

    static boolean seatMountObserved() {
        return seatMountObserved;
    }

    static boolean seatDismountObserved() {
        return seatDismountObserved;
    }

    static boolean seatEntityCleanupObserved() {
        return seatEntityCleanupObserved;
    }

    static UUID seatEntityId() {
        return seatEntityId;
    }

    static boolean fixtureLivenessTicketReleased() {
        return seatEntityCleanupObserved && !forceLoadTicketAdded;
    }

    private static void requireRuntimePreconditions() {
        long now = level.getGameTime();
        if (!ModList.get().isLoaded("create")) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "Create is loaded from the exact retained runtime",
                            now, now, "modId=create", safeServerState(), "missing Create mod"),
                    "required exact-stack Create mod not loaded");
        }
        try {
            Block assembler = requireBlock(PHYSICS_ASSEMBLER);
            Block seat = requireBlock(SEAT_ID);
            if (!seat.getClass().getName().endsWith("SeatBlock")) {
                throw new IllegalStateException("create:brown_seat runtime type=" + seat.getClass().getName());
            }
            if (!withProperty(seat.defaultBlockState(), "waterlogged", "false")
                    .getBlock().getClass().getName().endsWith("SeatBlock")) {
                throw new IllegalStateException("Create seat waterlogged=false state unavailable");
            }
            if (assembler == null) {
                throw new IllegalStateException("Physics Assembler resource unavailable");
            }
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper");
            Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatBlock");
            Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "exact C11 Sable/Simulated/Create SeatBlock/SeatEntity resources resolve",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(),
                            "createSourceCommit=" + CREATE_SOURCE_COMMIT + " " + exception),
                    "required exact-stack seat runtime resource unavailable: " + exception);
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = 3; x <= 8; x++) {
            for (int y = 198; y <= 204; y++) {
                for (int z = 3; z <= 8; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    if (!level.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)) {
                        throw new IllegalStateException("failed to place rigid seat-platform cell");
                    }
                }
            }
        }
        BlockState seatState = withProperty(requireBlock(SEAT_ID).defaultBlockState(), "waterlogged", "false");
        BlockState assemblerState = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(SEAT_SOURCE, seatState, 3)
                || !level.setBlock(ASSEMBLER_SOURCE, assemblerState, 3)) {
            throw new IllegalStateException("failed to place complete moving Create seat fixture");
        }
    }

    private static UUID addFixtureGlue() throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class)
                .invoke(null, GLUE_MIN, GLUE_MAX);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to register bounded seat-platform Super Glue fixture");
        }
        return glue.getUUID();
    }

    private static List<Entity> seatEntitiesAtMovedSeat() {
        return level.getEntitiesOfClass(Entity.class, new AABB(movedSeat),
                entity -> entity.getClass().getName().endsWith("SeatEntity"));
    }

    private static String seatEntitySummary(List<Entity> entities) {
        return entities.stream().map(SkyforgeSeatPassengerOnSableLifecycleAcceptance::entitySummary).toList().toString();
    }

    private static String entitySummary(Entity entity) {
        if (entity == null) return "null";
        return entity.getClass().getName() + "#" + entity.getUUID()
                + " alive=" + entity.isAlive() + " passengers=" + entity.getPassengers().size();
    }

    private static Object requireServerSubLevelContainer(ServerLevel serverLevel) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(serverLevel)) {
            throw new IllegalStateException("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(serverLevel);
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (value == null || !containerClass.isInstance(value)) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        }
        return value;
    }

    private static Set<UUID> currentSubLevelIds() throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object subLevel : subLevels) {
            ids.add(subLevelUniqueId(subLevel));
        }
        return ids;
    }

    private static UUID subLevelUniqueId(Object subLevel) throws ReflectiveOperationException {
        Object value = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
        if (!(value instanceof UUID uuid)) {
            throw new IllegalStateException("Sable sub-level unique ID is not UUID: " + value);
        }
        return uuid;
    }

    private static Object findListedBody(UUID uuid) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        }
        for (Object subLevel : subLevels) {
            if (uuid.equals(subLevelUniqueId(subLevel))) {
                return subLevel;
            }
        }
        return null;
    }

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        return findListedBody(uuid);
    }

    private static Object requireCanonicalBody() throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            throw new IllegalStateException("canonical live Sable body unavailable for UUID " + bodyId);
        }
        return canonical;
    }

    private static Object findCurrentPhysicsHandle(Object canonicalBody) throws ReflectiveOperationException {
        if (physicsSystem == null) return null;
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonicalBody).invoke(physicsSystem, canonicalBody);
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
        if (vector instanceof Vector3dc joml) {
            return switch (name) {
                case "x" -> joml.x();
                case "y" -> joml.y();
                case "z" -> joml.z();
                default -> throw new IllegalArgumentException("unknown vector component " + name);
            };
        }
        return number(publicMethod(vector, name).invoke(vector));
    }

    private static Vec3 projectThroughCanonicalBody(Object canonical, Vec3 plotPosition)
            throws ReflectiveOperationException {
        Object pose = publicMethod(canonical, "logicalPose").invoke(canonical);
        Object projected = publicMethod(pose, "transformPosition", Vec3.class).invoke(pose, plotPosition);
        if (!(projected instanceof Vec3 globalPosition)) {
            throw new IllegalStateException("Sable logicalPose transformPosition returned " + projected);
        }
        return globalPosition;
    }

    private static int countSourceFixtureNonAir() {
        int count = level.getBlockState(ASSEMBLER_SOURCE).isAir() ? 0 : 1;
        count += level.getBlockState(SEAT_SOURCE).isAir() ? 0 : 1;
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) count++;
                }
            }
        }
        return count;
    }

    private static void requireMovedSeatBlock() {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedSeat).getBlock());
        if (!SEAT_ID.equals(actual)) {
            throw new IllegalStateException("moved Create seat mismatch at " + movedSeat
                    + ": expected=" + SEAT_ID + " actual=" + actual);
        }
        if (!level.getBlockState(movedSeat).getBlock().getClass().getName().endsWith("SeatBlock")) {
            throw new IllegalStateException("moved create:brown_seat is not SeatBlock at " + movedSeat);
        }
    }

    private static void requireCanonicalPlotOwnership(Object canonical, BlockPos pos) throws ReflectiveOperationException {
        Object plot = publicMethod(canonical, "getPlot").invoke(canonical);
        Object contained = publicMethod(plot, "contains", double.class, double.class)
                .invoke(plot, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (!Boolean.TRUE.equals(contained)) {
            throw new IllegalStateException("moved seat is not owned by canonical Sable plot: bodyId="
                    + bodyId + " pos=" + pos);
        }
    }

    private static void addFixtureForceLoadTicket(Object body) throws ReflectiveOperationException {
        Class<?> ticketTypeClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        Class<?> unitClass = Class.forName("net.minecraft.util.Unit");
        forceLoadTicketKey = unitClass.getField("INSTANCE").get(null);
        Method addTicket = container.getClass().getMethod(
                "addForceLoadTicket", body.getClass(), ticketTypeClass, Object.class);
        Object added = addTicket.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(added instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("Sable command-forced liveness ticket was not added for body " + bodyId);
        }
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || assembledBody == null || forceLoadTicketType == null || forceLoadTicketKey == null) return;
        Method removeTicket = container.getClass().getMethod(
                "removeForceLoadTicket", assembledBody.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object removed = removeTicket.invoke(container, assembledBody, forceLoadTicketType, forceLoadTicketKey);
        if (!(removed instanceof Boolean ok) || !ok) {
            throw new IllegalStateException("Sable command-forced liveness ticket was not removed for body " + bodyId);
        }
        forceLoadTicketAdded = false;
    }

    private static void restorePhysicsPause() throws ReflectiveOperationException {
        if (physicsPauseChanged && physicsSystem != null) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, physicsWasPaused);
            physicsPauseChanged = false;
        }
    }

    private static void restorePlayerInvulnerability() {
        if (playerInvulnerabilityCaptured && !playerInvulnerabilityRestored && positionedPlayer != null) {
            positionedPlayer.setInvulnerable(originalPlayerInvulnerable);
            playerInvulnerabilityRestored = true;
        }
    }

    private static void cleanupQuietly() {
        try { restorePlayerInvulnerability(); } catch (RuntimeException ignored) {}
        try { restorePhysicsPause(); } catch (ReflectiveOperationException | RuntimeException ignored) {}
        try { removeFixtureForceLoadTicket(); } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static Block requireBlock(ResourceLocation blockId) {
        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            throw new IllegalStateException("required exact-stack block is not registered: " + blockId);
        }
        return BuiltInRegistries.BLOCK.get(blockId);
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

    private static Method oneArgMethod(Object target, String name, Object argument) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass()))) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(1 arg)");
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            SkyforgeCompilerIntegrationPhase phase,
            String expected,
            long startTick,
            long deadlineTick,
            String ids,
            String serverState,
            String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                CAPABILITY, phase, expected, startTick, deadlineTick, ids, serverState, "actual-client", dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        if (waitDiagnostic == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic(
                    SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                    "fixture reaches a classified terminal state",
                    now, now, movedIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client", dump);
    }

    private static String sourceIds() {
        return "assembler=" + ASSEMBLER_SOURCE + " seat=" + SEAT_SOURCE + " glueId=" + glueId;
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " movedOffset=" + movedOffset + " seat=" + movedSeat
                + " seatEntityId=" + seatEntityId;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        String ids;
        try { ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds()); }
        catch (ReflectiveOperationException exception) { ids = "<reflection-failed:" + exception.getClass().getSimpleName() + ">"; }
        String player = positionedPlayer == null
                ? "player=null"
                : "player=" + positionedPlayer.getUUID() + " passenger=" + positionedPlayer.isPassenger()
                        + " vehicle=" + entitySummary(positionedPlayer.getVehicle());
        return "gameTime=" + level.getGameTime() + " stage=" + stage + " subLevelIds=" + ids
                + " movedSeat=" + (movedSeat == null ? "n/a" : level.getBlockState(movedSeat)) + " " + player;
    }

    private static void failReflection(
            SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                ? invocation.getTargetException()
                : exception;
        fail(code, finalDiagnostic("reflectionFailure=" + cause), "runtime reflection failed: " + cause);
    }

    private static void fail(
            SkyforgeCompilerIntegrationFailure code,
            SkyforgeCompilerIntegrationDiagnostic diagnostic,
            String reason) {
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

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
