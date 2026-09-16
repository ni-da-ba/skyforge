package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

/** PLATFORM-007: fresh-process persistence of one composed Sable/Create/Aeronautics mechanism. */
final class SkyforgeComposedMechanismPersistenceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformComposedMechanismPersistence";
    static final String CAPABILITY = "SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeComposedMechanismPersistenceAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_COMPOSED_MECHANISM_PERSISTENCE";
    private static final Path IDENTITY_FILE = Path.of("platform-007-composed-persistence.identity");
    private static final String GLUE_CLASS_NAME = "com.simibubi.create.content.contraptions.glue.SuperGlueEntity";

    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");
    private static final ResourceLocation MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");
    private static final ResourceLocation ENDPOINT_ID = id("create:gearbox");
    private static final ResourceLocation BEARING_ID = id("aeronautics:propeller_bearing");
    private static final ResourceLocation SAIL_ID = id("simulated:white_symmetric_sail");

    private static final BlockPos BODY_MIN = new BlockPos(0, 200, 0);
    private static final BlockPos BODY_MAX = new BlockPos(3, 201, 1);
    private static final BlockPos MOTOR_SOURCE = new BlockPos(0, 201, 0);
    private static final BlockPos SHAFT_SOURCE = new BlockPos(1, 201, 0);
    private static final BlockPos ENDPOINT_SOURCE = new BlockPos(2, 201, 0);
    private static final BlockPos BEARING_SOURCE = new BlockPos(3, 201, 0);
    private static final BlockPos ASSEMBLER_SOURCE = new BlockPos(3, 202, 1);
    private static final BlockPos CHILD_HUB_SOURCE = new BlockPos(4, 201, 0);
    private static final BlockPos CHILD_SAIL_UP_SOURCE = new BlockPos(4, 202, 0);
    private static final BlockPos CHILD_SAIL_DOWN_SOURCE = new BlockPos(4, 200, 0);
    private static final BlockPos MAIN_GLUE_MIN = BODY_MIN;
    private static final BlockPos MAIN_GLUE_MAX = ASSEMBLER_SOURCE;
    private static final BlockPos CHILD_GLUE_MIN = CHILD_SAIL_DOWN_SOURCE;
    private static final BlockPos CHILD_GLUE_MAX = CHILD_SAIL_UP_SOURCE;

    private static final long ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long KINETIC_BUILD_DEADLINE_TICKS = 80L;
    private static final long CHILD_ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long RELOAD_RECOVERY_DEADLINE_TICKS = 120L;
    private static final long ENTITY_REHYDRATION_GRACE_TICKS = 10L;
    private static final long KINETIC_DISCONNECT_DEADLINE_TICKS = 80L;
    private static final long KINETIC_REBUILD_DEADLINE_TICKS = 80L;

    private static String persistencePhase;
    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object assembledBody;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static Set<UUID> beforeIds = Set.of();
    private static Set<UUID> beforeHoldingIds = Set.of();
    private static UUID bodyId;
    private static UUID mainGlueId;
    private static UUID childGlueId;
    private static UUID groundChildId;
    private static UUID nestedChildId;
    private static BlockPos movedOffset;
    private static BlockPos movedMotor;
    private static BlockPos movedShaft;
    private static BlockPos movedEndpoint;
    private static BlockPos movedBearing;
    private static BlockPos movedHub;
    private static BlockPos movedSailUp;
    private static BlockPos movedSailDown;
    private static BlockState shaftState;
    private static boolean physicsWasPaused;
    private static boolean physicsPauseChanged;
    private static boolean complete;
    private static float bearingNetworkSpeed;
    private static UUID preReloadNestedChildId;
    private static UUID preReloadMovedGlueId;
    private static UUID postReloadMovedGlueId;
    private static UUID postReloadChildGlueId;
    private static String childRecoveryMode = "unresolved";
    private static String glueRecoveryMode = "unresolved";
    private static boolean reloadChildAssemblyRequested;
    private static long reloadCanonicalResolvedTick = -1L;
    private static boolean primaryAssemblyTriggered;
    private static boolean primaryRecoveredFromHolding;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        KINETIC_BUILD,
        CHILD_REASSEMBLY,
        RELOAD_RECOVERY,
        KINETIC_DISCONNECT,
        KINETIC_REBUILD
    }

    private SkyforgeComposedMechanismPersistenceAcceptance() {}

    static void installFromSystemProperty() {
        persistencePhase = System.getProperty(ENABLE_PROPERTY, "").trim();
        if (persistencePhase.isEmpty()) {
            return;
        }
        if (!persistencePhase.equals("prepare") && !persistencePhase.equals("verify")) {
            throw new IllegalArgumentException(ENABLE_PROPERTY + " must be prepare or verify, got " + persistencePhase);
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeComposedMechanismPersistenceAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeComposedMechanismPersistenceAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " phase=" + persistencePhase
                        + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            if (persistencePhase.equals("verify")) {
                beginReloadVerification(now);
                return;
            }

            beforeIds = currentSubLevelIds();
            beforeHoldingIds = currentHoldingSubLevelIds();
            prepareFixture();
            GlueFixture mainGlue = addGlue(MAIN_GLUE_MIN, MAIN_GLUE_MAX, "main");
            mainGlueId = mainGlue.id();

            BlockEntity assembler = requireExpectedBlockEntity(ASSEMBLER_SOURCE, "PhysicsAssemblerBlockEntity");
            stage = Stage.ASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                    "real Physics Assembler captures the bounded primary Sable/Create mechanism",
                    now,
                    now + ASSEMBLY_DEADLINE_TICKS,
                    sourceIds(),
                    safeServerState(),
                    "beforeSubLevelIds=" + beforeIds + " beforeHoldingIds=" + beforeHoldingIds
                            + "; nested child realization deferred until live primary body");
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
            primaryAssemblyTriggered = true;
            pollAssembly(now, true);
        } catch (ReflectiveOperationException exception) {
            failReflection(stageFailureCode(), exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(stageFailureCode(),
                        diagnostic(stagePhase(),
                                "bounded composed persistence fixture reaches the current phase",
                                now, now, sourceIds(), safeServerState(), exception.toString()),
                        "composed persistence fixture failed: " + exception);
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
                case KINETIC_BUILD -> pollKineticBuild(now);
                case CHILD_REASSEMBLY -> pollChildReassembly(now);
                case RELOAD_RECOVERY -> pollReloadRecovery(now);
                case KINETIC_DISCONNECT -> pollReloadKineticDisconnect(now);
                case KINETIC_REBUILD -> pollReloadKineticRebuild(now);
            }
        } catch (ReflectiveOperationException exception) {
            failReflection(stageFailureCode(), exception);
        } catch (RuntimeException exception) {
            fail(stageFailureCode(), finalDiagnostic(exception.toString()),
                    "composed persistence lifecycle failed: " + exception);
        }
    }

    private static SkyforgeCompilerIntegrationFailure stageFailureCode() {
        if (stage == null) return SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE;
        return switch (stage) {
            case ASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
            case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
            case KINETIC_BUILD, KINETIC_DISCONNECT, KINETIC_REBUILD ->
                    SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD;
            case CHILD_REASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY;
            case RELOAD_RECOVERY -> SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE;
        };
    }

    private static SkyforgeCompilerIntegrationPhase stagePhase() {
        if (stage == null) return SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD;
        return switch (stage) {
            case ASSEMBLY -> SkyforgeCompilerIntegrationPhase.ASSEMBLY;
            case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION;
            case KINETIC_BUILD, CHILD_REASSEMBLY, KINETIC_DISCONNECT, KINETIC_REBUILD ->
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION;
            case RELOAD_RECOVERY -> SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD;
        };
    }

    private static void pollAssembly(long now, boolean synchronousObservation) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds();
        Set<UUID> created = new LinkedHashSet<>(currentIds);
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "minimal fixture created multiple live Sable bodies");
        }

        UUID candidateId = created.size() == 1 ? created.iterator().next() : bodyId;
        if (candidateId == null && primaryAssemblyTriggered && level.getBlockEntity(ASSEMBLER_SOURCE) == null) {
            Set<UUID> holdingIds = currentHoldingSubLevelIds();
            Set<UUID> createdHolding = new LinkedHashSet<>(holdingIds);
            createdHolding.removeAll(beforeHoldingIds);
            if (createdHolding.size() > 1) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "createdIds=" + created + " createdHoldingIds=" + createdHolding),
                        "consumed primary assembly produced multiple new holding Sable UUIDs");
            }
            if (createdHolding.size() == 1) {
                candidateId = createdHolding.iterator().next();
                bodyId = candidateId;
                primaryRecoveredFromHolding = true;
                requestHoldingLoadIfAvailable();
                currentIds = currentSubLevelIds();
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " PRIMARY_HOLDING_RECOVERY bodyId=" + bodyId
                                + " holdingIds=" + holdingIds + " liveIdsAfterSnatch=" + currentIds
                                + " tick=" + now);
            }
        }

        if (candidateId != null) {
            bodyId = candidateId;
            Object listedBody = findListedBody(bodyId);
            if (listedBody == null) {
                Set<UUID> holdingIds = currentHoldingSubLevelIds();
                if (primaryRecoveredFromHolding || holdingIds.contains(bodyId)) {
                    primaryRecoveredFromHolding = true;
                    requestHoldingLoadIfAvailable();
                    listedBody = findListedBody(bodyId);
                }
                if (listedBody == null) {
                    if (waitDiagnostic.expired(now)) {
                        fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                                waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                        "bodyId=" + bodyId + " primaryRecoveredFromHolding=" + primaryRecoveredFromHolding
                                                + " liveIds=" + currentSubLevelIds()
                                                + " holdingIds=" + currentHoldingSubLevelIds()),
                                "created Sable UUID could not be restored to the live collection");
                    }
                    return;
                }
            }
            assembledBody = listedBody;
            Object massTracker = publicMethod(listedBody, "getMassTracker").invoke(listedBody);
            double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            boolean removed = Boolean.TRUE.equals(publicMethod(listedBody, "isRemoved").invoke(listedBody));
            int sourceNonAir = countSourceFixtureNonAir();
            Entity staleGroundChild = findEntity(groundChildId);
            if (removed || !(mass > 0.0) || centerOfMass == null) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "sourceNonAir=" + sourceNonAir + " mass=" + mass + " removed=" + removed
                                        + " staleGroundChild=" + staleGroundChild),
                        "primary assembly registered an invalid Sable body");
            }
            // A normally live body must be allowed to finish Simulated parent-world cleanup before force-loading.
            // If the known UUID already fell into Sable holding during that window, recover it and ticket that
            // recovered live authority immediately so it cannot cycle back into holding.
            if (primaryRecoveredFromHolding && !forceLoadTicketAdded) {
                addFixtureForceLoadTicket(listedBody);
            }
            boolean staleChildAlive = staleGroundChild != null && staleGroundChild.isAlive();
            if (sourceNonAir != 0 || staleChildAlive) {
                if (waitDiagnostic.expired(now)) {
                    fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                            waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                    "sourceNonAir=" + sourceNonAir + " mass=" + mass + " removed=" + removed
                                            + " staleGroundChild=" + staleGroundChild),
                            "primary assembly source cleanup did not settle before deadline");
                }
                return;
            }
            if (!forceLoadTicketAdded) {
                addFixtureForceLoadTicket(listedBody);
            }
            movedOffset = movedOffset(listedBody, centerOfMass);
            movedMotor = MOTOR_SOURCE.offset(movedOffset);
            movedShaft = SHAFT_SOURCE.offset(movedOffset);
            movedEndpoint = ENDPOINT_SOURCE.offset(movedOffset);
            movedBearing = BEARING_SOURCE.offset(movedOffset);
            movedHub = CHILD_HUB_SOURCE.offset(movedOffset);
            movedSailUp = CHILD_SAIL_UP_SOURCE.offset(movedOffset);
            movedSailDown = CHILD_SAIL_DOWN_SOURCE.offset(movedOffset);
            requireMovedBlockId(movedMotor, MOTOR_ID, "motor");
            requireMovedBlockId(movedShaft, SHAFT_ID, "shaft");
            requireMovedBlockId(movedEndpoint, ENDPOINT_ID, "accepted PLATFORM-003 gearbox endpoint");
            requireMovedBlockId(movedBearing, id("minecraft:oak_planks"), "bearing realization plate cell");
            stage = Stage.PHYSICS_INITIALIZATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "canonical Sable body and current physics handle remain live after primary assembly",
                    now, now + PHYSICS_INITIALIZATION_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                            + " primaryRecoveredFromHolding=" + primaryRecoveredFromHolding
                            + " primaryAssemblyTriggered=" + primaryAssemblyTriggered
                            + " movedOffset=" + movedOffset + " groundChildId=" + groundChildId);
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " FLATTENED bodyId=" + bodyId + " groundChildId=" + groundChildId
                            + " movedOffset=" + movedOffset + " mass=" + mass
                            + " primaryRecoveredFromHolding=" + primaryRecoveredFromHolding);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            BlockEntity assembler = level.getBlockEntity(ASSEMBLER_SOURCE);
            Object lastAssemblyException = assembler == null
                    ? "assembler=null"
                    : publicMethod(assembler, "getLastAssemblyException").invoke(assembler);
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                            "createdIds=" + created + " holdingIds=" + currentHoldingSubLevelIds()
                                    + " lastAssemblyException=" + lastAssemblyException),
                    "Sable primary-body registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "canonicalBody=null"),
                        "canonical primary body did not remain available");
            }
            return;
        }
        if (physicsSystem == null) {
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        }
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "physicsHandle=null"),
                        "current physics handle did not become valid");
            }
            return;
        }
        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
            physicsPauseChanged = true;
        }
        for (BlockPos pos : List.of(movedMotor, movedShaft, movedEndpoint, movedBearing, movedHub, movedSailUp, movedSailDown)) {
            requireCanonicalPlotOwnership(canonical, pos);
        }
        requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        realizeMovedBearing(canonical, now);
        stage = Stage.KINETIC_BUILD;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "moved Create network propagates nonzero speed to the canonical Propeller Bearing",
                now, now + KINETIC_BUILD_DEADLINE_TICKS, movedIds(), safeServerState(),
                "currentPhysicsHandleValid=true; nested child realization deferred=true");
    }

    private static void pollKineticBuild(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        if (bearing == null) return;
        KineticState state = kineticState(bearing);
        if (Math.abs(state.speed()) > 0.0f && state.hasSource() && state.hasNetwork()) {
            bearingNetworkSpeed = state.speed();
            realizeMovedChildTopology(canonical, bearing, now);
            stage = Stage.CHILD_REASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "canonical moved Propeller Bearing creates one fresh controlled child with exact hub/sail membership",
                    now, now + CHILD_ASSEMBLY_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "bearingNetwork=" + state + " groundChildId=" + groundChildId);
            pollChildReassembly(now);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "bearing=" + state),
                    "moved Propeller Bearing kinetic network did not initialize");
        }
    }

    private static void realizeMovedBearing(Object canonical, long now) throws ReflectiveOperationException {
        requireCanonicalPlotOwnership(canonical, movedBearing);
        BlockState bearing = withProperty(requireBlock(BEARING_ID).defaultBlockState(), "facing", "east");
        if (!level.setBlock(movedBearing, bearing, 3)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    finalDiagnostic("movedBearing=" + movedBearing),
                    "failed to realize Propeller Bearing into accepted PLATFORM-003 primary plot");
        }
        requireMovedBlockId(movedBearing, BEARING_ID, "post-primary Propeller Bearing");
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " BEARING_REALIZED bodyId=" + bodyId + " movedBearing=" + movedBearing
                        + " upstreamEndpoint=" + movedEndpoint + " tick=" + now);
    }

    private static void realizeMovedChildTopology(Object canonical, Object bearing, long now)
            throws ReflectiveOperationException {
        for (BlockPos pos : List.of(movedHub, movedSailUp, movedSailDown)) {
            requireCanonicalPlotOwnership(canonical, pos);
        }
        BlockState sail = withProperty(requireBlock(SAIL_ID).defaultBlockState(), "axis", "x");
        if (!level.setBlock(movedHub, shaftState, 3)
                || !level.setBlock(movedSailUp, sail, 3)
                || !level.setBlock(movedSailDown, sail, 3)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    finalDiagnostic("movedHub=" + movedHub + " movedSailUp=" + movedSailUp
                            + " movedSailDown=" + movedSailDown),
                    "failed to realize moved nested-child topology inside canonical Sable plot");
        }
        GlueFixture childGlue = addGlue(
                CHILD_GLUE_MIN.offset(movedOffset), CHILD_GLUE_MAX.offset(movedOffset), "moved-child");
        childGlueId = childGlue.id();
        GlueState mainGlue = requireMovedMainGlue(now);
        GlueState childGlueState = new GlueState(childGlue.id(), childGlue.box(), 1);
        requireMovedGlueBoundary(mainGlue, childGlueState);
        publicMethod(bearing, "assemble").invoke(bearing);
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " CHILD_REALIZATION_REQUEST bodyId=" + bodyId + " childGlueId=" + childGlueId
                        + " movedHub=" + movedHub + " tick=" + now);
    }

    private static void pollChildReassembly(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        ChildState child = childState(bearing);
        if (child.present()) {
            requireExactChild(child, "nested", now);
            nestedChildId = child.id();
            requireMovedChildRemoved();
            GlueState movedGlue = requireMovedMainGlue(now);
            preReloadMovedGlueId = movedGlue.id();
            preReloadNestedChildId = nestedChildId;
            publicMethod(bearing, "disassemble").invoke(bearing);
            ChildState normalized = childState(bearing);
            if (normalized.present() || Boolean.TRUE.equals(publicMethod(bearing, "isRunning").invoke(bearing))) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("nestedBefore=" + child + " nestedAfterNormalize=" + normalized),
                        "nested child did not normalize back into the Sable plot before save");
            }
            requireMovedBlockId(movedHub, SHAFT_ID, "normalized child hub");
            requireMovedBlockId(movedSailUp, SAIL_ID, "normalized child sail up");
            requireMovedBlockId(movedSailDown, SAIL_ID, "normalized child sail down");
            Entity staleNested = findEntity(preReloadNestedChildId);
            if (staleNested != null && staleNested.isAlive()) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("preReloadNestedChildId=" + preReloadNestedChildId),
                        "normalized save boundary retained the nested child entity");
            }
            restorePhysicsPause();
            removeFixtureForceLoadTicket();
            if (!level.getServer().saveEverything(false, true, true)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("saveEverything=false"),
                        "server did not report a successful PLATFORM-007 prepare save");
            }
            writeIdentityFile(new PersistenceIdentity(bodyId, nestedChildId, movedGlue.id(), movedOffset));
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PREPARE PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId + " movedOffset=" + movedOffset
                            + " groundChildId=" + groundChildId + " nestedChildId=" + nestedChildId
                            + " movedGlueId=" + movedGlue.id() + " movedGlueCount=" + movedGlue.count()
                            + " bearingSpeed=" + bearingNetworkSpeed
                            + " childBlocks=3 sailBlocks=2 runningObserved=true childRunningAtSave=false"
                            + " normalizedChildBlocksInPlot=true entityBackedRuntimeNormalized=true saveSuccess=true"
                            + " canonicalBodyResolutionPerPhase=true"
                            + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            Object lastException = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_CHILD_ASSEMBLY,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "bearing=" + kineticState(bearing) + " lastAssemblyException=" + lastException),
                    "moved Propeller Bearing did not create nested child before deadline");
        }
    }

    private static void beginReloadVerification(long now) throws ReflectiveOperationException {
        PersistenceIdentity identity = readIdentityFile();
        bodyId = identity.bodyId();
        preReloadNestedChildId = identity.nestedChildId();
        preReloadMovedGlueId = identity.movedGlueId();
        movedOffset = identity.movedOffset();
        shaftState = withProperty(requireBlock(SHAFT_ID).defaultBlockState(), "axis", "x");
        level.getChunk(0, 0);
        requestHoldingLoadIfAvailable();
        stage = Stage.RELOAD_RECOVERY;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                "fresh server process re-resolves persisted Sable UUID and current composed mechanism",
                now, now + RELOAD_RECOVERY_DEADLINE_TICKS, movedIds(), safeServerState(),
                "bodyId=" + bodyId + " preReloadNestedChildId=" + preReloadNestedChildId
                        + " preReloadMovedGlueId=" + preReloadMovedGlueId);
        pollReloadRecovery(now);
    }

    private static void pollReloadRecovery(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            requestHoldingLoadIfAvailable();
            canonical = findCanonicalBody(bodyId);
            if (canonical == null) {
                if (waitDiagnostic.expired(now)) {
                    fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PERSISTENCE_RELOAD,
                            waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                    "bodyId=" + bodyId + " canonicalBody=null"),
                            "persisted Sable UUID did not reload from holding storage");
                }
                return;
            }
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " RELOAD_CANONICAL_RESOLVED bodyId=" + bodyId + " tick=" + now);
        }
        assembledBody = canonical;
        if (reloadCanonicalResolvedTick < 0L) {
            reloadCanonicalResolvedTick = now;
        }
        if (!forceLoadTicketAdded) addFixtureForceLoadTicket(canonical);
        if (physicsSystem == null) physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PERSISTENCE_RELOAD,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "physicsHandle=null"),
                        "reloaded Sable body did not acquire a current physics handle");
            }
            return;
        }
        Object massTracker = publicMethod(canonical, "getMassTracker").invoke(canonical);
        Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
        Object pose = publicMethod(canonical, "logicalPose").invoke(canonical);
        Object position = publicMethod(pose, "position").invoke(pose);
        requireFiniteVector(centerOfMass, "reloaded centerOfMass");
        requireFiniteVector(position, "reloaded logicalPose.position");
        movedMotor = MOTOR_SOURCE.offset(movedOffset);
        movedShaft = SHAFT_SOURCE.offset(movedOffset);
        movedEndpoint = ENDPOINT_SOURCE.offset(movedOffset);
        movedBearing = BEARING_SOURCE.offset(movedOffset);
        movedHub = CHILD_HUB_SOURCE.offset(movedOffset);
        movedSailUp = CHILD_SAIL_UP_SOURCE.offset(movedOffset);
        movedSailDown = CHILD_SAIL_DOWN_SOURCE.offset(movedOffset);
        requireMovedBlockId(movedMotor, MOTOR_ID, "reloaded motor");
        requireMovedBlockId(movedShaft, SHAFT_ID, "reloaded shaft");
        requireMovedBlockId(movedEndpoint, ENDPOINT_ID, "reloaded gearbox endpoint");
        requireMovedBlockId(movedBearing, BEARING_ID, "reloaded bearing");
        GlueState mainGlue;
        if ("unresolved".equals(glueRecoveryMode)) {
            requireMovedBlockId(movedHub, SHAFT_ID, "reloaded normalized child hub");
            requireMovedBlockId(movedSailUp, SAIL_ID, "reloaded normalized child sail up");
            requireMovedBlockId(movedSailDown, SAIL_ID, "reloaded normalized child sail down");
            GlueState observedMain = observeMovedGlueDomain(MAIN_GLUE_MIN, MAIN_GLUE_MAX, "main", now);
            GlueState observedChild = observeMovedGlueDomain(CHILD_GLUE_MIN, CHILD_GLUE_MAX, "child", now);
            boolean graceExpired = now >= reloadCanonicalResolvedTick + ENTITY_REHYDRATION_GRACE_TICKS;
            if (!graceExpired && (observedMain == null || observedChild == null)) {
                return;
            }
            boolean mainPersisted = observedMain != null;
            boolean childPersisted = observedChild != null;
            mainGlue = mainPersisted
                    ? observedMain
                    : createMissingMovedGlueDomain(MAIN_GLUE_MIN, MAIN_GLUE_MAX, "main", now);
            GlueState recoveredChildGlue = childPersisted
                    ? observedChild
                    : createMissingMovedGlueDomain(CHILD_GLUE_MIN, CHILD_GLUE_MAX, "child", now);
            postReloadMovedGlueId = mainGlue.id();
            postReloadChildGlueId = recoveredChildGlue.id();
            requireMovedGlueBoundary(mainGlue, recoveredChildGlue);
            glueRecoveryMode = mainPersisted && childPersisted
                    ? "persisted-both"
                    : mainPersisted || childPersisted ? "mixed-persisted-reactivated" : "recreated-both";
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " RELOAD_GLUE_READY bodyId=" + bodyId + " mode=" + glueRecoveryMode
                            + " mainGlueId=" + mainGlue.id() + " childGlueId=" + recoveredChildGlue.id()
                            + " tick=" + now);
        } else {
            mainGlue = requireMovedMainGlue(now);
            if (!reloadChildAssemblyRequested) {
                requireMovedGlueDomain(CHILD_GLUE_MIN, CHILD_GLUE_MAX, "child", now);
            }
        }
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        KineticState kinetic = kineticState(bearing);
        ChildState child = childState(bearing);
        if (!child.present() && allMovedChildBlocksPresent()) {
            if (!reloadChildAssemblyRequested) {
                publicMethod(bearing, "assemble").invoke(bearing);
                reloadChildAssemblyRequested = true;
                LOGGER.log(System.Logger.Level.INFO,
                        PREFIX + " RELOAD_CHILD_REASSEMBLY_REQUEST bodyId=" + bodyId);
            }
            return;
        }
        if (!child.present() || Math.abs(kinetic.speed()) == 0.0f || !kinetic.hasSource() || !kinetic.hasNetwork()) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PERSISTENCE_RELOAD,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                "mainGlue=" + mainGlue + " recoveredChildGlueId=" + postReloadChildGlueId
                                        + " child=" + child + " bearing=" + kinetic),
                        "composed mechanism did not recover before reload deadline");
            }
            return;
        }
        requireExactChild(child, "nested", now);
        Entity stale = findEntity(preReloadNestedChildId);
        if (!child.id().equals(preReloadNestedChildId) && stale != null && stale.isAlive()) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("preReloadChild=" + preReloadNestedChildId + " currentChild=" + child.id()),
                    "reload retained a stale duplicate Propeller Bearing child");
        }
        childRecoveryMode = child.id().equals(preReloadNestedChildId)
                ? "persisted-current"
                : "reassembled-from-normalized-plot";
        nestedChildId = child.id();
        bearingNetworkSpeed = kinetic.speed();
        if (!level.setBlock(movedShaft, Blocks.AIR.defaultBlockState(), 3)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic("failedToRemove=" + movedShaft),
                    "could not sever reloaded kinetic connection");
        }
        stage = Stage.KINETIC_DISCONNECT;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "post-reload shaft sever proves recovered Create network is live",
                now, now + KINETIC_DISCONNECT_DEADLINE_TICKS, movedIds(), safeServerState(),
                "reloadedSpeed=" + bearingNetworkSpeed + " childRecoveryMode=" + childRecoveryMode
                        + " postReloadMovedGlueId=" + postReloadMovedGlueId);
    }

    private static void pollReloadKineticDisconnect(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        KineticState state = kineticState(bearing);
        if (Math.abs(state.speed()) == 0.0f && !state.hasSource()) {
            if (!level.setBlock(movedShaft, shaftState, 3)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failedToRestore=" + movedShaft),
                        "could not restore reloaded kinetic connection");
            }
            stage = Stage.KINETIC_REBUILD;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "restored post-reload shaft rebuilds the recovered Create network",
                    now, now + KINETIC_REBUILD_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "preDisconnectSpeed=" + bearingNetworkSpeed + " movedShaftRestored=true");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "bearing=" + state),
                    "reloaded Create network did not disconnect before deadline");
        }
    }

    private static void pollReloadKineticRebuild(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        KineticState state = kineticState(bearing);
        ChildState child = childState(bearing);
        GlueState glue = requireMovedMainGlue(now);
        if (handle != null && Math.abs(state.speed()) > 0.0f && state.hasSource() && state.hasNetwork()
                && child.present()) {
            requireExactChild(child, "nested", now);
            restorePhysicsPause();
            removeFixtureForceLoadTicket();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " VERIFY PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId + " movedOffset=" + movedOffset
                            + " preReloadNestedChildId=" + preReloadNestedChildId
                            + " postReloadNestedChildId=" + child.id()
                            + " childRecoveryMode=" + childRecoveryMode
                            + " glueRecoveryMode=" + glueRecoveryMode
                            + " preReloadMovedGlueId=" + preReloadMovedGlueId
                            + " postReloadMovedGlueId=" + glue.id() + " movedGlueCount=" + glue.count()
                            + " postReloadChildGlueId=" + postReloadChildGlueId + " childGlueRecoveryValidated=true"
                            + " reloadedSpeed=" + bearingNetworkSpeed + " rebuiltSpeed=" + state.speed()
                            + " samePersistentUuid=true currentPhysicsHandleValid=true"
                            + " staleChildDuplicate=false glueDuplicate=false"
                            + " severedObserved=true rebuiltObserved=true"
                            + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "handleValid=" + (handle != null) + " bearing=" + state + " child=" + child
                                    + " glue=" + glue + " postReloadChildGlueId=" + postReloadChildGlueId),
                    "reloaded Create network did not rebuild before deadline");
        }
    }

    private static boolean requestHoldingLoadIfAvailable() throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        Object holding = publicMethod(holdingMap, "getHoldingSubLevel", UUID.class).invoke(holdingMap, bodyId);
        if (holding == null) return false;
        Object pointer = publicMethod(holding, "pointer").invoke(holding);
        if (pointer == null) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " HOLDING_POINTER_PENDING bodyId=" + bodyId + " holding=" + holding);
            return false;
        }
        Method snatch = holdingMap.getClass().getMethod("snatchAndLoad", pointer.getClass(), UUID.class);
        snatch.invoke(holdingMap, pointer, bodyId);
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " HOLDING_SNATCH_REQUEST bodyId=" + bodyId + " pointer=" + pointer);
        return true;
    }

    private static boolean allMovedChildBlocksPresent() {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedHub).getBlock()).equals(SHAFT_ID)
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedSailUp).getBlock()).equals(SAIL_ID)
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedSailDown).getBlock()).equals(SAIL_ID);
    }

    private static void requireRuntimePreconditions() {
        long now = level.getGameTime();
        try {
            if (!ModList.get().isLoaded("create")) {
                throw new IllegalStateException("required exact-stack Create mod not loaded");
            }
            for (ResourceLocation resource : List.of(PHYSICS_ASSEMBLER, MOTOR_ID, SHAFT_ID, BEARING_ID, SAIL_ID)) {
                requireBlock(resource);
            }
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity");
            Class.forName("dev.eriksonn.aeronautics.content.blocks.propeller.bearing.contraption.PropellerBearingContraptionEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "exact pinned Create/Sable/Aeronautics/Simulated bearing runtime resolves",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(), exception.toString()),
                    "required exact-stack bearing resource unavailable: " + exception);
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = -2; x <= 5; x++) {
            for (int y = 198; y <= 204; y++) {
                for (int z = -2; z <= 3; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    if (!level.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)) {
                        throw new IllegalStateException("failed to place rigid primary body cell");
                    }
                }
            }
        }
        BlockState motor = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "east");
        shaftState = withProperty(requireBlock(SHAFT_ID).defaultBlockState(), "axis", "x");
        BlockState shaft = shaftState;
        BlockState endpoint = withProperty(requireBlock(ENDPOINT_ID).defaultBlockState(), "axis", "y");
        BlockState assembler = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(MOTOR_SOURCE, motor, 3)
                || !level.setBlock(SHAFT_SOURCE, shaft, 3)
                || !level.setBlock(ENDPOINT_SOURCE, endpoint, 3)
                || !level.setBlock(ASSEMBLER_SOURCE, assembler, 3)) {
            throw new IllegalStateException("failed to place accepted PLATFORM-003 primary persistence fixture");
        }
    }

    private static GlueFixture addGlue(BlockPos min, BlockPos max, String label) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, min, max);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to register " + label + " Super Glue domain");
        }
        return new GlueFixture(glue.getUUID(), box, glue);
    }

    private static void requireGlueBoundary(GlueFixture main, GlueFixture child) throws ReflectiveOperationException {
        boolean mainContainsHub = Boolean.TRUE.equals(publicMethod(main.entity(), "contains", BlockPos.class)
                .invoke(main.entity(), CHILD_HUB_SOURCE));
        boolean childContainsBearing = Boolean.TRUE.equals(publicMethod(child.entity(), "contains", BlockPos.class)
                .invoke(child.entity(), BEARING_SOURCE));
        if (mainContainsHub || childContainsBearing) {
            throw new IllegalStateException("fixture glue crosses Propeller Bearing controller boundary");
        }
    }

    private static BlockEntity requireExpectedBlockEntity(BlockPos pos, String suffix) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().endsWith(suffix)) {
            throw new IllegalStateException("required block entity missing/wrong at " + pos + ": "
                    + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
        }
        return blockEntity;
    }

    private static ChildState childState(Object bearing) throws ReflectiveOperationException {
        Object moved = publicMethod(bearing, "getMovedContraption").invoke(bearing);
        boolean running = Boolean.TRUE.equals(publicMethod(bearing, "isRunning").invoke(bearing));
        if (!(moved instanceof Entity child) || !child.isAlive()) {
            return new ChildState(null, false, running, 0, 0, 0, "missing");
        }
        Object contraption = publicMethod(child, "getContraption").invoke(child);
        Object blocksValue = publicMethod(contraption, "getBlocks").invoke(contraption);
        if (!(blocksValue instanceof Map<?, ?> blocks)) {
            throw new IllegalStateException("bearing child block map unavailable");
        }
        int hubs = 0;
        int sails = 0;
        for (Object value : blocks.values()) {
            if (!(value instanceof StructureTemplate.StructureBlockInfo info)) {
                throw new IllegalStateException("unexpected child block-info type " + value);
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
            if (SHAFT_ID.equals(id)) hubs++;
            if (SAIL_ID.equals(id)) sails++;
        }
        int sailPower = ((Number) publicMethod(contraption, "getSailBlocks").invoke(contraption)).intValue();
        return new ChildState(child.getUUID(), true, running, blocks.size(), hubs, sails,
                child.getClass().getName() + ";sailPower=" + sailPower);
    }

    private static void requireExactChild(ChildState child, String label, long now) throws ReflectiveOperationException {
        if (!child.present() || !child.running() || child.blockCount() != 3 || child.hubCount() != 1 || child.sailCount() != 2) {
            Object bearing = level.getBlockEntity("ground".equals(label) ? BEARING_SOURCE : movedBearing);
            Object last = bearing == null ? "bearing=null" : publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
            fail(SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY,
                    diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                            label + " Propeller Bearing child contains one hub plus exactly two symmetric sails",
                            now, now, label + "Child=" + child.id(), safeServerState(),
                            "child=" + child + " lastAssemblyException=" + last),
                    label + " Propeller Bearing child membership invalid");
        }
    }

    private static void requireSourceChildRemoved(String label) {
        for (BlockPos pos : List.of(CHILD_HUB_SOURCE, CHILD_SAIL_UP_SOURCE, CHILD_SAIL_DOWN_SOURCE)) {
            if (!level.getBlockState(pos).isAir()) {
                throw new IllegalStateException(label + " did not remove child source block at " + pos);
            }
        }
    }

    private static void requireMovedChildRemoved() {
        for (BlockPos pos : List.of(movedHub, movedSailUp, movedSailDown)) {
            if (!level.getBlockState(pos).isAir()) {
                throw new IllegalStateException("nested child assembly did not remove moved child block at " + pos);
            }
        }
    }

    private static Entity findEntity(UUID id) throws ReflectiveOperationException {
        if (id == null) return null;
        Object value = publicMethod(level, "getEntity", UUID.class).invoke(level, id);
        return value instanceof Entity entity ? entity : null;
    }
    private static Object requireServerSubLevelContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(level)) {
            throw new IllegalStateException("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (value == null || !containerClass.isInstance(value)) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        }
        return value;
    }

    private static Set<UUID> currentHoldingSubLevelIds() throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        Field field = holdingMap.getClass().getDeclaredField("allHoldingSubLevels");
        field.setAccessible(true);
        Object value = field.get(holdingMap);
        if (!(value instanceof Map<?, ?> holdings)) {
            throw new IllegalStateException("Sable allHoldingSubLevels is not Map-like: " + value);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object key : holdings.keySet()) {
            if (!(key instanceof UUID uuid)) {
                throw new IllegalStateException("Sable holding sub-level key is not UUID: " + key);
            }
            ids.add(uuid);
        }
        return ids;
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
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonicalBody).invoke(physicsSystem, canonicalBody);
        if (handle == null) {
            return null;
        }
        Object valid = publicMethod(handle, "isValid").invoke(handle);
        return Boolean.TRUE.equals(valid) ? handle : null;
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

    private static void requireFiniteVector(Object vector, String label) throws ReflectiveOperationException {
        for (String axis : List.of("x", "y", "z")) {
            double value = component(vector, axis);
            if (!Double.isFinite(value)) {
                throw new IllegalStateException(label + " has non-finite " + axis + "=" + value);
            }
        }
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

    private static int countSourceFixtureNonAir() {
        int count = level.getBlockState(ASSEMBLER_SOURCE).isAir() ? 0 : 1;
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

    private static void requireMovedBlockId(BlockPos pos, ResourceLocation expected, String label) {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!expected.equals(actual)) {
            throw new IllegalStateException("moved " + label + " block mismatch at " + pos
                    + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void requireCanonicalPlotOwnership(Object canonical, BlockPos pos) throws ReflectiveOperationException {
        Object plot = publicMethod(canonical, "getPlot").invoke(canonical);
        Object contained = publicMethod(plot, "contains", double.class, double.class)
                .invoke(plot, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (!Boolean.TRUE.equals(contained)) {
            throw new IllegalStateException("moved position is not owned by canonical Sable plot: bodyId="
                    + bodyId + " pos=" + pos);
        }
    }

    private static Object requireExpectedMovedBlockEntity(Object canonical, BlockPos pos, String suffix, long now)
            throws ReflectiveOperationException {
        requireCanonicalPlotOwnership(canonical, pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            if (waitDiagnostic != null && waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId + " missingBlockEntityAt=" + pos),
                        "required moved Create block entity missing after deadline");
            }
            return null;
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("unexpectedBlockEntity=" + blockEntity.getClass().getName() + " at=" + pos),
                    "unexpected moved Create block entity type");
        }
        return blockEntity;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        if (blockEntity == null) {
            return KineticState.MISSING;
        }
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kineticClass.isInstance(blockEntity)) {
            throw new IllegalStateException("not a Create KineticBlockEntity: " + blockEntity.getClass().getName());
        }
        float speed = ((Number) kineticClass.getDeclaredMethod("getSpeed").invoke(blockEntity)).floatValue();
        float theoretical = ((Number) kineticClass.getDeclaredMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue();
        boolean hasSource = (Boolean) kineticClass.getDeclaredMethod("hasSource").invoke(blockEntity);
        boolean hasNetwork = (Boolean) kineticClass.getDeclaredMethod("hasNetwork").invoke(blockEntity);
        return new KineticState(speed, theoretical, hasSource, hasNetwork, blockEntity.getClass().getName());
    }

    private static void addFixtureForceLoadTicket(Object body) throws ReflectiveOperationException {
        Class<?> ticketTypeClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        Class<?> unitClass = Class.forName("net.minecraft.util.Unit");
        forceLoadTicketKey = unitClass.getField("INSTANCE").get(null);
        Method addTicket = container.getClass().getMethod(
                "addForceLoadTicket", body.getClass(), ticketTypeClass, Object.class);
        Object added = addTicket.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(added instanceof Boolean addedBoolean) || !addedBoolean) {
            throw new IllegalStateException("Sable command-forced liveness ticket was not added for body " + bodyId);
        }
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || assembledBody == null || forceLoadTicketType == null || forceLoadTicketKey == null) {
            return;
        }
        Method removeTicket = container.getClass().getMethod(
                "removeForceLoadTicket", assembledBody.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object removed = removeTicket.invoke(container, assembledBody, forceLoadTicketType, forceLoadTicketKey);
        if (!(removed instanceof Boolean removedBoolean) || !removedBoolean) {
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

    private static void cleanupQuietly() {
        try {
            BlockEntity bearing = movedBearing == null ? level == null ? null : level.getBlockEntity(BEARING_SOURCE)
                    : level.getBlockEntity(movedBearing);
            if (bearing != null && bearing.getClass().getName().endsWith("PropellerBearingBlockEntity")) {
                Object child = publicMethod(bearing, "getMovedContraption").invoke(bearing);
                if (child != null) publicMethod(bearing, "disassemble").invoke(bearing);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            restorePhysicsPause();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }


    private static GlueState observeMovedGlueDomain(
            BlockPos sourceMin, BlockPos sourceMax, String label, long now) throws ReflectiveOperationException {
        AABB expected = movedGlueBox(sourceMin, sourceMax);
        List<Entity> matches = matchingGlueEntities(expected);
        if (matches.size() > 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    diagnostic(SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                            "at most one persisted Super Glue domain rehydrates at the canonical moved box",
                            now, now, "bodyId=" + bodyId + " label=" + label, safeServerState(),
                            "expectedGlueBox=" + expected + " exactMatches=" + matches.size()),
                    "reload rehydrated duplicate " + label + " glue authority");
        }
        return matches.isEmpty() ? null : new GlueState(matches.getFirst().getUUID(), expected, 1);
    }

    private static GlueState createMissingMovedGlueDomain(
            BlockPos sourceMin, BlockPos sourceMax, String label, long now) throws ReflectiveOperationException {
        GlueState existing = observeMovedGlueDomain(sourceMin, sourceMax, label, now);
        if (existing != null) return existing;
        AABB expected = movedGlueBox(sourceMin, sourceMax);
        Class<?> glueClass = Class.forName(GLUE_CLASS_NAME);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, expected);
        if (!level.addFreshEntity(glue)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                    finalDiagnostic("label=" + label + " expectedGlueBox=" + expected),
                    "could not re-realize missing " + label + " glue topology after reload");
        }
        return requireMovedGlueDomain(sourceMin, sourceMax, label, now);
    }

    private static GlueState requireMovedGlueDomain(
            BlockPos sourceMin, BlockPos sourceMax, String label, long now) throws ReflectiveOperationException {
        AABB expected = movedGlueBox(sourceMin, sourceMax);
        List<Entity> matches = matchingGlueEntities(expected);
        if (matches.size() != 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    diagnostic(SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                            "exactly one recovered Super Glue domain remains at the canonical moved box",
                            now, now, "bodyId=" + bodyId + " label=" + label, safeServerState(),
                            "expectedGlueBox=" + expected + " exactMatches=" + matches.size()),
                    "recovered " + label + " glue domain was missing or duplicated");
        }
        return new GlueState(matches.getFirst().getUUID(), expected, matches.size());
    }

    private static GlueState requireMovedMainGlue(long now) throws ReflectiveOperationException {
        AABB expected = movedGlueBox(MAIN_GLUE_MIN, MAIN_GLUE_MAX);
        List<Entity> matches = matchingGlueEntities(expected);
        if (matches.size() != 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    diagnostic(SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                            "exactly one bounded main-body Super Glue domain exists at the canonical moved box",
                            now, now, "bodyId=" + bodyId, safeServerState(),
                            "expectedGlueBox=" + expected + " exactMatches=" + matches.size()),
                    "moved main-body glue domain was missing or duplicated");
        }
        return new GlueState(matches.getFirst().getUUID(), expected, matches.size());
    }

    private static AABB movedGlueBox(BlockPos sourceMin, BlockPos sourceMax) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName(GLUE_CLASS_NAME);
        AABB sourceBox = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class)
                .invoke(null, sourceMin, sourceMax);
        return sourceBox.move(movedOffset.getX(), movedOffset.getY(), movedOffset.getZ());
    }

    private static List<Entity> matchingGlueEntities(AABB expected) {
        List<Entity> matches = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, expected.inflate(1.0))) {
            if (GLUE_CLASS_NAME.equals(entity.getClass().getName()) && sameBox(entity.getBoundingBox(), expected)) {
                matches.add(entity);
            }
        }
        return matches;
    }

    private static void requireMovedGlueBoundary(GlueState main, GlueState child) {
        BlockPos movedHubPos = CHILD_HUB_SOURCE.offset(movedOffset);
        BlockPos movedBearingPos = BEARING_SOURCE.offset(movedOffset);
        if (main.box().contains(movedHubPos.getX() + 0.5, movedHubPos.getY() + 0.5, movedHubPos.getZ() + 0.5)
                || child.box().contains(movedBearingPos.getX() + 0.5, movedBearingPos.getY() + 0.5, movedBearingPos.getZ() + 0.5)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("main=" + main + " child=" + child),
                    "recovered glue topology crosses the Propeller Bearing controller boundary");
        }
    }

    private static boolean sameBox(AABB first, AABB second) {
        double epsilon = 1.0e-6;
        return Math.abs(first.minX - second.minX) <= epsilon
                && Math.abs(first.minY - second.minY) <= epsilon
                && Math.abs(first.minZ - second.minZ) <= epsilon
                && Math.abs(first.maxX - second.maxX) <= epsilon
                && Math.abs(first.maxY - second.maxY) <= epsilon
                && Math.abs(first.maxZ - second.maxZ) <= epsilon;
    }

    private static void writeIdentityFile(PersistenceIdentity identity) {
        String value = identity.bodyId() + "\n"
                + identity.nestedChildId() + "\n"
                + identity.movedGlueId() + "\n"
                + identity.movedOffset().getX() + "," + identity.movedOffset().getY() + ","
                + identity.movedOffset().getZ() + "\n";
        try {
            Files.writeString(IDENTITY_FILE, value);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist PLATFORM-007 identity sidecar", exception);
        }
    }

    private static PersistenceIdentity readIdentityFile() {
        try {
            List<String> lines = Files.readAllLines(IDENTITY_FILE);
            if (lines.size() != 4) {
                throw new IllegalStateException("PLATFORM-007 identity sidecar expected 4 lines, got " + lines.size());
            }
            String[] offset = lines.get(3).split(",", -1);
            if (offset.length != 3) {
                throw new IllegalStateException("invalid PLATFORM-007 moved offset line: " + lines.get(3));
            }
            return new PersistenceIdentity(
                    UUID.fromString(lines.get(0).trim()),
                    UUID.fromString(lines.get(1).trim()),
                    UUID.fromString(lines.get(2).trim()),
                    new BlockPos(Integer.parseInt(offset[0]), Integer.parseInt(offset[1]), Integer.parseInt(offset[2])));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to read PLATFORM-007 identity sidecar", exception);
        }
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
                CAPABILITY, phase, expected, startTick, deadlineTick, ids, serverState, "headless", dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        if (waitDiagnostic == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic(
                    SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                    "fixture reaches a classified terminal state",
                    now,
                    now,
                    movedIds(),
                    safeServerState(),
                    dump);
        }
        return waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", dump);
    }

    private static String sourceIds() {
        return "assembler=" + ASSEMBLER_SOURCE + " motor=" + MOTOR_SOURCE + " shaft=" + SHAFT_SOURCE
                + " endpoint=" + ENDPOINT_SOURCE + " bearingRealization=" + BEARING_SOURCE + " hub=" + CHILD_HUB_SOURCE
                + " sailUp=" + CHILD_SAIL_UP_SOURCE + " sailDown=" + CHILD_SAIL_DOWN_SOURCE
                + " mainGlueId=" + mainGlueId + " childGlueId=" + childGlueId + " groundChildId=" + groundChildId;
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " movedOffset=" + movedOffset + " motor=" + movedMotor + " shaft=" + movedShaft
                + " endpoint=" + movedEndpoint + " bearing=" + movedBearing + " hub=" + movedHub + " sailUp=" + movedSailUp
                + " sailDown=" + movedSailDown + " groundChildId=" + groundChildId + " nestedChildId=" + nestedChildId;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        String ids;
        try {
            ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds());
        } catch (ReflectiveOperationException exception) {
            ids = "subLevelIds=<reflection-failed:" + exception.getClass().getSimpleName() + ">";
        }
        return "gameTime=" + level.getGameTime() + " stage=" + stage + " subLevelIds=" + ids
                + " sourceBearing=" + level.getBlockState(BEARING_SOURCE)
                + " movedBearing=" + (movedBearing == null ? "n/a" : level.getBlockState(movedBearing))
                + " movedHub=" + (movedHub == null ? "n/a" : level.getBlockState(movedHub));
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
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("expected Number, got " + value);
        }
        return number.doubleValue();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record KineticState(
            float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork, String runtimeType) {
        private static final KineticState MISSING = new KineticState(0.0f, 0.0f, false, false, "missing");
    }
    private record GlueFixture(UUID id, AABB box, Entity entity) {}
    private record GlueState(UUID id, AABB box, int count) {}

    private record PersistenceIdentity(UUID bodyId, UUID nestedChildId, UUID movedGlueId, BlockPos movedOffset) {}

    private record ChildState(
            UUID id, boolean present, boolean running, int blockCount, int hubCount, int sailCount, String runtimeType) {}

}
