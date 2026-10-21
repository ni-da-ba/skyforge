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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;

/** PLATFORM-003: exact-stack Create kinetic lifecycle inside one live Sable primary body. */
final class SkyforgeCreateKineticOnSableLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformCreateKineticOnSableLifecycle";
    static final String CAPABILITY = "CREATE_KINETIC_ON_SABLE_LIFECYCLE";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeCreateKineticOnSableLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_CREATE_KINETIC_ON_SABLE_LIFECYCLE";

    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");
    private static final ResourceLocation MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");
    private static final ResourceLocation ENDPOINT_ID = id("create:gearbox");

    private static final BlockPos BODY_MIN = new BlockPos(0, 200, 0);
    private static final BlockPos BODY_MAX = new BlockPos(3, 201, 1);
    private static final BlockPos MOTOR_SOURCE = new BlockPos(0, 201, 0);
    private static final BlockPos SHAFT_SOURCE = new BlockPos(1, 201, 0);
    private static final BlockPos ENDPOINT_SOURCE = new BlockPos(2, 201, 0);
    private static final BlockPos ASSEMBLER_SOURCE = new BlockPos(3, 202, 1);
    private static final BlockPos GLUE_MIN = BODY_MIN;
    private static final BlockPos GLUE_MAX = ASSEMBLER_SOURCE;

    private static final long ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long KINETIC_BUILD_DEADLINE_TICKS = 80L;
    private static final long KINETIC_DISCONNECT_DEADLINE_TICKS = 60L;
    private static final long KINETIC_REBUILD_DEADLINE_TICKS = 60L;

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
    private static BlockPos movedMotor;
    private static BlockPos movedShaft;
    private static BlockPos movedEndpoint;
    private static BlockState shaftState;
    private static boolean physicsWasPaused;
    private static boolean physicsPauseChanged;
    private static boolean complete;
    private static float initialSpeed;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        KINETIC_BUILD,
        KINETIC_DISCONNECT,
        KINETIC_REBUILD
    }

    private SkyforgeCreateKineticOnSableLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeCreateKineticOnSableLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeCreateKineticOnSableLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now + " clientState=headless");
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
                                now,
                                now,
                                sourceIds(),
                                safeServerState(),
                                "assemblerBlockEntity=" + (assembler == null ? "null" : assembler.getClass().getName())),
                        "Physics Assembler block entity missing or wrong type");
            }
            stage = Stage.ASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                    "exactly one new Sable UUID is synchronously registered and all source cells transfer",
                    now,
                    now + ASSEMBLY_DEADLINE_TICKS,
                    sourceIds(),
                    safeServerState(),
                    "glueId=" + glueId + " beforeSubLevelIds=" + beforeIds);
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
            pollAssembly(now, true);
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                boolean assemblyStarted = stage == Stage.ASSEMBLY;
                fail(
                        assemblyStarted
                                ? SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY
                                : SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                        diagnostic(
                                assemblyStarted
                                        ? SkyforgeCompilerIntegrationPhase.ASSEMBLY
                                        : SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                assemblyStarted
                                        ? "real Physics Assembler completes synchronous primary-body transfer"
                                        : "minimal preassembled Sable/Create fixture is placed",
                                now,
                                now,
                                sourceIds(),
                                safeServerState(),
                                exception.toString()),
                        (assemblyStarted ? "assembly observation failed: " : "fixture preparation failed: ") + exception);
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
                case KINETIC_DISCONNECT -> pollKineticDisconnect(now);
                case KINETIC_REBUILD -> pollKineticRebuild(now);
            }
        } catch (ReflectiveOperationException exception) {
            SkyforgeCompilerIntegrationFailure code = stage == Stage.ASSEMBLY
                    ? SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY
                    : stage == Stage.PHYSICS_INITIALIZATION
                            ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                            : SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD;
            failReflection(code, exception);
        } catch (RuntimeException exception) {
            fail(
                    stage == Stage.PHYSICS_INITIALIZATION
                            ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                            : SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic(exception.toString()),
                    "moving-body kinetic lifecycle failed: " + exception);
        }
    }

    private static void pollAssembly(long now, boolean synchronousObservation) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds();
        Set<UUID> created = new LinkedHashSet<>(currentIds);
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "minimal fixture created multiple Sable bodies");
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            int sourceNonAir = countSourceFixtureNonAir();
            Object listedBody = findListedBody(bodyId);
            if (listedBody == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId + " listedBody=null"),
                        "created UUID could not be reselected from live Sable collection");
            }
            assembledBody = listedBody;
            Object massTracker = publicMethod(listedBody, "getMassTracker").invoke(listedBody);
            if (massTracker == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId + " massTracker=null"),
                        "assembled Sable body has no mass tracker");
            }
            double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            boolean removed = Boolean.TRUE.equals(publicMethod(listedBody, "isRemoved").invoke(listedBody));
            if (sourceNonAir != 0 || removed || !(mass > 0.0) || centerOfMass == null) {
                fail(
                        removed || !(mass > 0.0) || centerOfMass == null
                                ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                                : SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId
                                        + " sourceNonAirAfterAssembly=" + sourceNonAir
                                        + " mass=" + mass
                                        + " centerOfMass=" + centerOfMass
                                        + " removed=" + removed),
                        "primary body did not reach a valid synchronous post-assembly state");
            }
            movedOffset = movedOffset(listedBody, centerOfMass);
            movedMotor = MOTOR_SOURCE.offset(movedOffset);
            movedShaft = SHAFT_SOURCE.offset(movedOffset);
            movedEndpoint = ENDPOINT_SOURCE.offset(movedOffset);
            requireMovedBlockId(movedMotor, MOTOR_ID, "motor");
            requireMovedBlockId(movedShaft, SHAFT_ID, "shaft");
            requireMovedBlockId(movedEndpoint, ENDPOINT_ID, "endpoint");
            addFixtureForceLoadTicket(listedBody);
            stage = Stage.PHYSICS_INITIALIZATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "persistent Sable UUID resolves to a current live body with valid current physics handle",
                    now,
                    now + PHYSICS_INITIALIZATION_DEADLINE_TICKS,
                    movedIds(),
                    safeServerState(),
                    "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                            + " movedOffset=" + movedOffset
                            + " fixtureLivenessTicket=sable:command_forced");
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " ASSEMBLED bodyId=" + bodyId
                            + " movedOffset=" + movedOffset
                            + " mass=" + mass
                            + " movedMotor=" + movedMotor
                            + " movedShaft=" + movedShaft
                            + " movedEndpoint=" + movedEndpoint);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "Sable body registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId + " canonicalBody=null"),
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
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                                "bodyId=" + bodyId + " currentPhysicsHandle=null-or-invalid"),
                        "current Sable physics handle did not become valid");
            }
            return;
        }
        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
            physicsPauseChanged = true;
        }
        requireCanonicalPlotOwnership(canonical, movedMotor);
        requireCanonicalPlotOwnership(canonical, movedShaft);
        requireCanonicalPlotOwnership(canonical, movedEndpoint);
        requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        stage = Stage.KINETIC_BUILD;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "Create network on canonical live Sable body propagates nonzero speed to endpoint",
                now,
                now + KINETIC_BUILD_DEADLINE_TICKS,
                movedIds(),
                safeServerState(),
                "currentPhysicsHandleValid=true; awaiting moved kinetic network initialization");
    }

    private static void pollKineticBuild(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object motor = requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        KineticState source = kineticState(motor);
        KineticState downstream = kineticState(endpoint);
        if (Math.abs(downstream.speed()) > 0.0f && downstream.hasSource() && downstream.hasNetwork()) {
            initialSpeed = downstream.speed();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " INITIAL_NETWORK bodyId=" + bodyId
                            + " source=" + source
                            + " endpoint=" + downstream
                            + " tick=" + now);
            if (!level.setBlock(movedShaft, Blocks.AIR.defaultBlockState(), 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to remove moved shaft at " + movedShaft),
                        "could not sever moved kinetic connection");
            }
            stage = Stage.KINETIC_DISCONNECT;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "severed shaft inside canonical Sable plot removes downstream propagated speed",
                    now,
                    now + KINETIC_DISCONNECT_DEADLINE_TICKS,
                    movedIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " movedShaftRemoved=true");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "source=" + source + " endpoint=" + downstream),
                    "moved Create network did not initialize before deadline");
        }
    }

    private static void pollKineticDisconnect(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object motor = requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        KineticState source = kineticState(motor);
        KineticState downstream = kineticState(endpoint);
        if (Math.abs(downstream.speed()) == 0.0f && !downstream.hasSource()) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " DISCONNECTED bodyId=" + bodyId
                            + " source=" + source
                            + " endpoint=" + downstream
                            + " tick=" + now);
            if (!level.setBlock(movedShaft, shaftState, 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to restore moved shaft at " + movedShaft),
                        "could not restore moved kinetic connection");
            }
            stage = Stage.KINETIC_REBUILD;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "restored shaft inside canonical Sable plot rebuilds network and propagated speed",
                    now,
                    now + KINETIC_REBUILD_DEADLINE_TICKS,
                    movedIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " movedShaftRestored=true");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "source=" + source + " endpoint=" + downstream),
                    "moved Create network did not disconnect before deadline");
        }
    }

    private static void pollKineticRebuild(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        Object motor = requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        KineticState source = kineticState(motor);
        KineticState downstream = kineticState(endpoint);
        if (handle != null && Math.abs(downstream.speed()) > 0.0f && downstream.hasSource() && downstream.hasNetwork()) {
            restorePhysicsPause();
            removeFixtureForceLoadTicket();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId
                            + " movedOffset=" + movedOffset
                            + " initialSpeed=" + initialSpeed
                            + " rebuiltSpeed=" + downstream.speed()
                            + " sourceSpeed=" + source.speed()
                            + " endpointHasSource=true endpointHasNetwork=true"
                            + " canonicalBodyResolutionPerPhase=true"
                            + " blockEntityResolutionPerPoll=true"
                            + " currentPhysicsHandleValid=true"
                            + " severedObserved=true rebuiltObserved=true"
                            + " fixtureLivenessTicket=sable:command_forced(released)"
                            + " clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "handleValid=" + (handle != null) + " source=" + source + " endpoint=" + downstream),
                    "moved Create network did not rebuild before deadline");
        }
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
            requireBlock(PHYSICS_ASSEMBLER);
            requireBlock(MOTOR_ID);
            requireBlock(SHAFT_ID);
            requireBlock(ENDPOINT_ID);
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper");
            Class.forName("com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "required exact-stack blocks and runtime classes resolve",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(), exception.toString()),
                    "required exact-stack runtime resource unavailable: " + exception);
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
                        throw new IllegalStateException("failed to place rigid body cell");
                    }
                }
            }
        }
        BlockState motorState = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "east");
        shaftState = withProperty(requireBlock(SHAFT_ID).defaultBlockState(), "axis", "x");
        BlockState endpointState = withProperty(requireBlock(ENDPOINT_ID).defaultBlockState(), "axis", "y");
        BlockState assemblerState = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(MOTOR_SOURCE, motorState, 3)
                || !level.setBlock(SHAFT_SOURCE, shaftState, 3)
                || !level.setBlock(ENDPOINT_SOURCE, endpointState, 3)
                || !level.setBlock(ASSEMBLER_SOURCE, assemblerState, 3)) {
            throw new IllegalStateException("failed to place complete moving kinetic fixture");
        }
    }

    private static UUID addFixtureGlue() throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        AABB box = (AABB) span.invoke(null, GLUE_MIN, GLUE_MAX);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add bounded Create Super Glue fixture entity");
        }
        return glue.getUUID();
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
            restorePhysicsPause();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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
        return "assembler=" + ASSEMBLER_SOURCE + " motor=" + MOTOR_SOURCE
                + " shaft=" + SHAFT_SOURCE + " endpoint=" + ENDPOINT_SOURCE + " glueId=" + glueId;
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " movedOffset=" + movedOffset
                + " motor=" + movedMotor + " shaft=" + movedShaft + " endpoint=" + movedEndpoint;
    }

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        String ids;
        try {
            ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds());
        } catch (ReflectiveOperationException exception) {
            ids = "subLevelIds=<reflection-failed:" + exception.getClass().getSimpleName() + ">";
        }
        return "gameTime=" + level.getGameTime()
                + " stage=" + stage
                + " subLevelIds=" + ids
                + " movedMotorState=" + (movedMotor == null ? "n/a" : level.getBlockState(movedMotor))
                + " movedShaftState=" + (movedShaft == null ? "n/a" : level.getBlockState(movedShaft))
                + " movedEndpointState=" + (movedEndpoint == null ? "n/a" : level.getBlockState(movedEndpoint));
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
}
