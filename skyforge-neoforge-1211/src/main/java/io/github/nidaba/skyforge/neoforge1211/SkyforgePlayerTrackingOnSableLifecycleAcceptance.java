package io.github.nidaba.skyforge.neoforge1211;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** PLATFORM-012: natural actual-client Sable player tracking and inherited parent translation. */
final class SkyforgePlayerTrackingOnSableLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformPlayerTrackingOnSableLifecycle";
    static final String CAPABILITY = "PLAYER_TRACKING_ON_SABLE_LIFECYCLE";
    static final String CREATE_SOURCE_COMMIT = "ac0c444d9828da3453ae8cc65338e8de063286fb";
    static final String SABLE_SOURCE_COMMIT = "6966d2928340de7631abcecf8549904b877df0a8";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgePlayerTrackingOnSableLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_PLAYER_TRACKING_ON_SABLE_LIFECYCLE";

    private static final ResourceLocation SEAT_ID = id("create:brown_seat");
    // Keep the body plus Sable's one-block physics envelope inside explicitly loaded chunk (0,0).
    private static final BlockPos BODY_MIN = new BlockPos(5, 200, 5);
    private static final BlockPos BODY_MAX = new BlockPos(6, 201, 6);
    private static final BlockPos SEAT_SOURCE = new BlockPos(5, 202, 5);
    private static final BlockPos FIXTURE_MAX = new BlockPos(6, 202, 6);
    private static final int EXPLICIT_FIXTURE_CELLS = 9;

    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long SEAT_MOUNT_DEADLINE_TICKS = 240L;
    private static final long SEAT_DISMOUNT_DEADLINE_TICKS = 160L;
    private static final long SEAT_CLEANUP_DEADLINE_TICKS = 80L;
    private static final long TRACKING_ACQUISITION_DEADLINE_TICKS = 80L;
    private static final long PARENT_TRANSLATION_DEADLINE_TICKS = 80L;
    private static final double TRANSLATION_VELOCITY_METERS_PER_SECOND = 2.0;
    private static final double MINIMUM_PARENT_TRANSLATION_BLOCKS = 0.15;
    private static final double PLAYER_DELTA_TOLERANCE_BLOCKS = 0.08;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object assembledBody;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static Set<UUID> beforeIds = Set.of();
    private static UUID bodyId;
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
    private static volatile UUID clientTrackingId;
    private static volatile boolean serverTrackingAcquired;
    private static volatile boolean translationBaselineRequested;
    private static volatile boolean clientTranslationBaselineReady;
    private static volatile double clientStartX;
    private static volatile boolean translationStarted;
    private static volatile boolean translationServerQualified;
    private static volatile boolean clientTranslationReported;
    private static volatile double clientEndX;
    private static volatile boolean trackingLifecycleQualified;
    private static double startParentX;
    private static double startServerPlayerX;
    private static double measuredParentDeltaX;
    private static double measuredServerPlayerDeltaX;
    private static double measuredClientPlayerDeltaX;
    private static int translationTicks;
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
        CLIENT_CLEANUP,
        TRACKING_ACQUISITION,
        PARENT_TRANSLATION
    }

    private SkyforgePlayerTrackingOnSableLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgePlayerTrackingOnSableLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgePlayerTrackingOnSableLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now
                        + " createSourceCommit=" + CREATE_SOURCE_COMMIT + " sableSourceCommit=" + SABLE_SOURCE_COMMIT
                        + " clientState=actual-client-pending");
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            beforeIds = currentSubLevelIds();
            prepareFixture();
            assembleExplicitFixture(now);
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
                case ASSEMBLY -> fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        finalDiagnostic("qualificationSetup=sable:SubLevelAssemblyHelper.assembleBlocks stageStillPending=true"),
                        "synchronous Sable qualification assembly unexpectedly remained pending");
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case CLIENT_MOUNT, CLIENT_DISMOUNT, CLIENT_CLEANUP -> pollClientLifecycle(event, now);
                case TRACKING_ACQUISITION, PARENT_TRANSLATION -> pollTrackingLifecycle(now);
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
                case CLIENT_DISMOUNT, CLIENT_CLEANUP, TRACKING_ACQUISITION -> SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING;
                case PARENT_TRANSLATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
            };
            fail(code, finalDiagnostic(exception.toString()), "Create seat client lifecycle failed: " + exception);
        }
    }

    private static void assembleExplicitFixture(long now) throws ReflectiveOperationException {
        stage = Stage.ASSEMBLY;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                "exactly nine explicit fixture cells synchronously transfer into one new canonical Sable body",
                now, now, sourceIds(), safeServerState(),
                "qualificationSetup=sable:SubLevelAssemblyHelper.assembleBlocks explicitFixtureCells="
                        + EXPLICIT_FIXTURE_CELLS + " beforeSubLevelIds=" + beforeIds);

        List<BlockPos> blocks = sourceFixtureBlocks();
        if (blocks.size() != EXPLICIT_FIXTURE_CELLS) {
            throw new IllegalStateException("explicit fixture cell count mismatch: " + blocks.size());
        }
        Class<?> boundsClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i");
        Class<?> boundsInterface = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
        Object bounds = boundsClass
                .getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
                .newInstance(
                        BODY_MIN.getX(), BODY_MIN.getY(), BODY_MIN.getZ(),
                        FIXTURE_MAX.getX(), FIXTURE_MAX.getY(), FIXTURE_MAX.getZ());
        Class<?> helperClass = Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
        Method assembleBlocks = helperClass.getMethod(
                "assembleBlocks", ServerLevel.class, BlockPos.class, Iterable.class, boundsInterface);
        Object returnedBody = assembleBlocks.invoke(null, level, BODY_MIN, blocks, bounds);
        if (returnedBody == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    finalDiagnostic("returnedBody=null"),
                    "Sable explicit qualification assembly returned no body");
        }

        Set<UUID> currentIds = currentSubLevelIds();
        Set<UUID> created = new LinkedHashSet<>(currentIds);
        created.removeAll(beforeIds);
        UUID returnedId = subLevelUniqueId(returnedBody);
        if (created.size() != 1 || !created.contains(returnedId)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    finalDiagnostic("returnedId=" + returnedId + " createdIds=" + created),
                    "explicit qualification assembly did not create exactly one canonical Sable UUID");
        }
        bodyId = returnedId;
        Object listedBody = findListedBody(bodyId);
        if (listedBody == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    finalDiagnostic("bodyId=" + bodyId + " listedBody=null"),
                    "explicitly assembled Sable UUID could not be reselected");
        }
        int sourceNonAir = countSourceFixtureNonAir();
        Object massTracker = publicMethod(listedBody, "getMassTracker").invoke(listedBody);
        double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
        Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
        boolean removed = Boolean.TRUE.equals(publicMethod(listedBody, "isRemoved").invoke(listedBody));
        if (sourceNonAir != 0 || removed || !(mass > 0.0) || centerOfMass == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    finalDiagnostic("bodyId=" + bodyId + " sourceNonAirAfterAssembly=" + sourceNonAir
                            + " mass=" + mass + " centerOfMass=" + centerOfMass + " removed=" + removed),
                    "explicit Sable qualification assembly did not synchronously transfer all fixture cells");
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
                "qualificationSetup=sable:SubLevelAssemblyHelper.assembleBlocks explicitFixtureCells="
                        + EXPLICIT_FIXTURE_CELLS + "; fixtureLivenessTicket=sable:command_forced");
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " ASSEMBLED bodyId=" + bodyId + " movedOffset=" + movedOffset
                        + " movedSeat=" + movedSeat + " mass=" + mass
                        + " qualificationSetup=sable:SubLevelAssemblyHelper.assembleBlocks"
                        + " explicitFixtureCells=" + EXPLICIT_FIXTURE_CELLS);
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
                seatEntityCleanupObserved = true;
                stage = Stage.TRACKING_ACQUISITION;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.CLIENT_INTERACTION,
                        "actual client naturally acquires the same persistent Sable UUID and the server adopts it through ordinary movement packets",
                        now, now + TRACKING_ACQUISITION_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "seatEntityCleanupObserved=true; harnessTrackingSetterInvoked=false; harnessPlayerMutationDuringMeasurement=false");
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " TRACKING_READY bodyId=" + bodyId
                                + " seatEntityCleanupObserved=true playerPassenger=false"
                                + " harnessTrackingSetterInvoked=false");
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

    private static void pollTrackingLifecycle(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    finalDiagnostic("currentPhysicsHandle=null-or-invalid"),
                    "current Sable physics authority disappeared during tracking qualification");
        }
        if (positionedPlayer == null || positionedPlayer.isPassenger()) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING,
                    finalDiagnostic("player=" + positionedPlayer + " passenger="
                            + (positionedPlayer != null && positionedPlayer.isPassenger())),
                    "tracking measurement requires the same dismounted integrated ServerPlayer");
        }

        UUID serverTrackingId = trackingSubLevelId(positionedPlayer);
        if (stage == Stage.TRACKING_ACQUISITION) {
            if (bodyId.equals(clientTrackingId) && bodyId.equals(serverTrackingId)) {
                serverTrackingAcquired = true;
                translationBaselineRequested = true;
                stage = Stage.PARENT_TRANSLATION;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.PHYSICS_PROGRESSION,
                        "client baseline handshake completes, then one bounded parent translation is inherited by client/server player state",
                        now, now + PARENT_TRANSLATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "clientTrackingId=" + clientTrackingId + " serverTrackingId=" + serverTrackingId
                                + "; persistentBodyId=" + bodyId
                                + "; harnessPlayerMutationDuringMeasurement=false");
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " TRACKING_ACQUIRED bodyId=" + bodyId
                                + " clientTrackingId=" + clientTrackingId
                                + " serverTrackingId=" + serverTrackingId
                                + " acquisitionPath=client_sublevel_collision_then_movement_packet"
                                + " harnessTrackingSetterInvoked=false");
                return;
            }
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_TRACKING,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "clientTrackingId=" + clientTrackingId + " serverTrackingId=" + serverTrackingId),
                        "natural Sable player tracking did not converge client/server to the persistent parent UUID");
            }
            return;
        }

        if (!bodyId.equals(clientTrackingId) || !bodyId.equals(serverTrackingId)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING,
                    finalDiagnostic("clientTrackingId=" + clientTrackingId + " serverTrackingId=" + serverTrackingId),
                    "Sable tracking identity left the persistent parent during translation measurement");
        }
        if (!clientTranslationBaselineReady) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PARENT_TRANSLATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "translationBaselineRequested=true clientTranslationBaselineReady=false"),
                        "actual client did not acknowledge the no-mutation translation baseline");
            }
            return;
        }

        if (!translationStarted) {
            startParentX = parentPositionX(canonical);
            startServerPlayerX = positionedPlayer.getX();
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
            physicsPauseChanged = true;
            setLinearVelocityX(handle, TRANSLATION_VELOCITY_METERS_PER_SECOND);
            translationStarted = true;
            translationTicks = 0;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " TRANSLATION_STARTED bodyId=" + bodyId
                            + " startParentX=" + startParentX
                            + " startServerPlayerX=" + startServerPlayerX
                            + " startClientPlayerX=" + clientStartX
                            + " commandedParentVelocityX=" + TRANSLATION_VELOCITY_METERS_PER_SECOND
                            + " harnessPlayerMutationDuringMeasurement=false");
            return;
        }

        if (!translationServerQualified) {
            translationTicks++;
            measuredParentDeltaX = parentPositionX(canonical) - startParentX;
            measuredServerPlayerDeltaX = positionedPlayer.getX() - startServerPlayerX;
            if (Math.abs(measuredParentDeltaX) >= MINIMUM_PARENT_TRANSLATION_BLOCKS) {
                setLinearVelocityX(handle, 0.0);
                double serverError = Math.abs(measuredServerPlayerDeltaX - measuredParentDeltaX);
                if (Math.signum(measuredServerPlayerDeltaX) != Math.signum(measuredParentDeltaX)
                        || serverError > PLAYER_DELTA_TOLERANCE_BLOCKS) {
                    fail(
                            SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING,
                            finalDiagnostic("parentDeltaX=" + measuredParentDeltaX
                                    + " serverPlayerDeltaX=" + measuredServerPlayerDeltaX
                                    + " serverError=" + serverError),
                            "server player did not inherit the bounded parent translation within tolerance");
                }
                translationServerQualified = true;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " SERVER_TRANSLATION_QUALIFIED bodyId=" + bodyId
                                + " parentDeltaX=" + measuredParentDeltaX
                                + " serverPlayerDeltaX=" + measuredServerPlayerDeltaX
                                + " tolerance=" + PLAYER_DELTA_TOLERANCE_BLOCKS);
                return;
            }
            if (translationTicks > PARENT_TRANSLATION_DEADLINE_TICKS || waitDiagnostic.expired(now)) {
                setLinearVelocityX(handle, 0.0);
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PARENT_TRANSLATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "parentDeltaX=" + measuredParentDeltaX
                                        + " serverPlayerDeltaX=" + measuredServerPlayerDeltaX),
                        "parent body did not reach the bounded translation threshold");
            }
            return;
        }

        if (!clientTranslationReported) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PARENT_TRANSLATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "actual-client",
                                "translationServerQualified=true clientTranslationReported=false"),
                        "actual client did not report its inherited translation after the server probe completed");
            }
            return;
        }

        measuredClientPlayerDeltaX = clientEndX - clientStartX;
        double clientError = Math.abs(measuredClientPlayerDeltaX - measuredParentDeltaX);
        if (Math.signum(measuredClientPlayerDeltaX) != Math.signum(measuredParentDeltaX)
                || clientError > PLAYER_DELTA_TOLERANCE_BLOCKS) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PASSENGER_TRACKING,
                    finalDiagnostic("parentDeltaX=" + measuredParentDeltaX
                            + " serverPlayerDeltaX=" + measuredServerPlayerDeltaX
                            + " clientPlayerDeltaX=" + measuredClientPlayerDeltaX
                            + " clientError=" + clientError),
                    "actual client did not inherit the bounded parent translation within tolerance");
        }

        restorePlayerInvulnerability();
        restorePhysicsPause();
        removeFixtureForceLoadTicket();
        trackingLifecycleQualified = true;
        complete = true;
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " PASS capability=" + CAPABILITY
                        + " bodyId=" + bodyId
                        + " clientTrackingId=" + clientTrackingId
                        + " serverTrackingId=" + serverTrackingId
                        + " naturalTrackingAcquired=true"
                        + " acquisitionPath=client_sublevel_collision_then_movement_packet"
                        + " parentTranslationApplied=true"
                        + " parentDeltaX=" + measuredParentDeltaX
                        + " serverPlayerDeltaX=" + measuredServerPlayerDeltaX
                        + " clientPlayerDeltaX=" + measuredClientPlayerDeltaX
                        + " tolerance=" + PLAYER_DELTA_TOLERANCE_BLOCKS
                        + " samePersistentSableUuid=true currentPhysicsHandleValid=true"
                        + " harnessTrackingSetterInvoked=false harnessPlayerMutationDuringMeasurement=false"
                        + " fixtureLivenessTicket=sable:command_forced(released)"
                        + " playerSableTrackingQualified=true inheritedParentTranslationQualified=true"
                        + " flightQualified=false");
    }

    static void observeClientTracking(UUID trackingId) {
        clientTrackingId = trackingId;
    }

    static boolean serverTrackingAcquired() {
        return serverTrackingAcquired;
    }

    static boolean translationBaselineRequested() {
        return translationBaselineRequested;
    }

    static void submitClientTranslationBaseline(double startX) {
        if (!Double.isFinite(startX)) {
            throw new IllegalArgumentException("client translation baseline must be finite");
        }
        clientStartX = startX;
        clientTranslationBaselineReady = true;
    }

    static boolean translationServerQualified() {
        return translationServerQualified;
    }

    static void submitClientTranslationResult(double endX) {
        if (!Double.isFinite(endX)) {
            throw new IllegalArgumentException("client translation endpoint must be finite");
        }
        clientEndX = endX;
        clientTranslationReported = true;
    }

    static boolean trackingLifecycleQualified() {
        return trackingLifecycleQualified;
    }

    static double measuredParentDeltaX() {
        return measuredParentDeltaX;
    }

    static double measuredServerPlayerDeltaX() {
        return measuredServerPlayerDeltaX;
    }

    static double measuredClientPlayerDeltaX() {
        return measuredClientPlayerDeltaX;
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
        return trackingLifecycleQualified && !forceLoadTicketAdded;
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
            Block seat = requireBlock(SEAT_ID);
            if (!seat.getClass().getName().endsWith("SeatBlock")) {
                throw new IllegalStateException("create:brown_seat runtime type=" + seat.getClass().getName());
            }
            if (!withProperty(seat.defaultBlockState(), "waterlogged", "false")
                    .getBlock().getClass().getName().endsWith("SeatBlock")) {
                throw new IllegalStateException("Create seat waterlogged=false state unavailable");
            }
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
            Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i");
            Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
            Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatBlock");
            Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "exact C11 Sable explicit-assembly/Create SeatBlock/SeatEntity resources resolve",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(),
                            "createSourceCommit=" + CREATE_SOURCE_COMMIT + " sableSourceCommit=" + SABLE_SOURCE_COMMIT
                                    + " " + exception),
                    "required exact-stack seat/tracking runtime resource unavailable: " + exception);
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
        if (!level.setBlock(SEAT_SOURCE, seatState, 3)) {
            throw new IllegalStateException("failed to place Create seat qualification fixture");
        }
        if (countSourceFixtureNonAir() != EXPLICIT_FIXTURE_CELLS) {
            throw new IllegalStateException("explicit seat-platform fixture did not place exactly "
                    + EXPLICIT_FIXTURE_CELLS + " source cells");
        }
    }

    private static List<BlockPos> sourceFixtureBlocks() {
        java.util.ArrayList<BlockPos> blocks = new java.util.ArrayList<>(EXPLICIT_FIXTURE_CELLS);
        for (BlockPos pos : BlockPos.betweenClosed(BODY_MIN, BODY_MAX)) {
            blocks.add(pos.immutable());
        }
        blocks.add(SEAT_SOURCE);
        return List.copyOf(blocks);
    }

    private static List<Entity> seatEntitiesAtMovedSeat() {
        return level.getEntitiesOfClass(Entity.class, new AABB(movedSeat),
                entity -> entity.getClass().getName().endsWith("SeatEntity"));
    }

    private static String seatEntitySummary(List<Entity> entities) {
        return entities.stream().map(SkyforgePlayerTrackingOnSableLifecycleAcceptance::entitySummary).toList().toString();
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

    private static UUID trackingSubLevelId(ServerPlayer player) throws ReflectiveOperationException {
        Class.forName("dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension");
        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
        Object helper = sableClass.getField("HELPER").get(null);
        Method method = helper.getClass().getMethod("getTrackingSubLevel", Entity.class);
        Object subLevel = method.invoke(helper, player);
        return subLevel == null ? null : subLevelUniqueId(subLevel);
    }

    private static double parentPositionX(Object parentSubLevel) throws ReflectiveOperationException {
        Object pose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Object position = publicMethod(pose, "position").invoke(pose);
        return component(position, "x");
    }

    private static void setLinearVelocityX(Object handle, double targetX) throws ReflectiveOperationException {
        Vector3d currentLinear = new Vector3d();
        Vector3d currentAngular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", currentLinear).invoke(handle, currentLinear);
        oneArgMethod(handle, "getAngularVelocity", currentAngular).invoke(handle, currentAngular);
        Vector3d linearDelta = new Vector3d(
                targetX - currentLinear.x(),
                -currentLinear.y(),
                -currentLinear.z());
        Vector3d angularDelta = new Vector3d(currentAngular).negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linearDelta, angularDelta)
                .invoke(handle, linearDelta, angularDelta);
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
        int count = 0;
        for (BlockPos pos : sourceFixtureBlocks()) {
            if (!level.getBlockState(pos).isAir()) count++;
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

    private static Method twoArgMethod(Object target, String name, Object first, Object second)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
            Class<?>[] types = method.getParameterTypes();
            if ((first == null || types[0].isAssignableFrom(first.getClass()))
                    && (second == null || types[1].isAssignableFrom(second.getClass()))) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(2 args)");
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
        return "fixtureBounds=" + BODY_MIN + ".." + FIXTURE_MAX + " seat=" + SEAT_SOURCE
                + " explicitFixtureCells=" + EXPLICIT_FIXTURE_CELLS;
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
