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
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** PLATFORM-008: exact-stack Simulated Swivel child and real Create kinetic actuation on one live Sable parent. */
final class SkyforgeSwivelControlChildLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformSwivelControlChildLifecycle";
    static final String CAPABILITY = "SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeSwivelControlChildLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE";

    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");
    private static final ResourceLocation MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");
    private static final ResourceLocation ENDPOINT_ID = id("create:gearbox");
    private static final ResourceLocation COG_ID = id("create:cogwheel");
    private static final ResourceLocation SWIVEL_ID = id("simulated:swivel_bearing");
    private static final ResourceLocation SAIL_ID = id("simulated:white_symmetric_sail");

    private static final BlockPos BODY_MIN = new BlockPos(0, 200, 0);
    private static final BlockPos BODY_MAX = new BlockPos(3, 201, 1);
    private static final BlockPos MOTOR_SOURCE = new BlockPos(0, 201, 0);
    private static final BlockPos SHAFT_SOURCE = new BlockPos(1, 201, 0);
    private static final BlockPos ENDPOINT_SOURCE = new BlockPos(2, 201, 0);
    private static final BlockPos SWIVEL_SOURCE = new BlockPos(3, 201, 0);
    private static final BlockPos DRIVE_COG_SOURCE = new BlockPos(3, 201, 1);
    private static final BlockPos COMMAND_MOTOR_SOURCE = new BlockPos(3, 200, 1);
    private static final BlockPos ASSEMBLER_SOURCE = new BlockPos(3, 202, 1);
    private static final List<BlockPos> CHILD_SOURCE = List.of(
            new BlockPos(3, 202, 0), new BlockPos(3, 203, 0),
            new BlockPos(3, 204, 0), new BlockPos(3, 205, 0));
    private static final BlockPos GLUE_MIN = BODY_MIN;
    private static final BlockPos GLUE_MAX = ASSEMBLER_SOURCE;

    private static final long ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long CARRIER_NETWORK_DEADLINE_TICKS = 80L;
    private static final long CHILD_ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long COMMAND_BUILD_DEADLINE_TICKS = 80L;
    private static final long COMMAND_PHASE_DEADLINE_TICKS = 80L;
    private static final int COMMAND_RPM = 16;
    private static final int COMMAND_TICKS = 10;
    private static final int HOLD_TICKS = 10;
    private static final double TARGET_TOLERANCE_DEGREES = 0.25;
    private static final double PHYSICAL_NEUTRAL_TOLERANCE_DEGREES = 3.0;
    private static final double PHYSICAL_RESPONSE_MIN_DEGREES = 5.0;

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
    private static UUID childGlueId;
    private static UUID childId;
    private static BlockPos movedOffset;
    private static BlockPos movedMotor;
    private static BlockPos movedShaft;
    private static BlockPos movedEndpoint;
    private static BlockPos movedSwivel;
    private static BlockPos movedDriveCog;
    private static BlockPos movedCommandMotor;
    private static List<BlockPos> movedChild = List.of();
    private static BlockState shaftState;
    private static boolean physicsWasPaused;
    private static boolean physicsPauseChanged;
    private static boolean complete;
    private static float carrierSpeed;
    private static float commandExtraCogSpeed;
    private static double neutralTarget;
    private static double neutralPhysicalYaw;
    private static double deflectedTarget;
    private static double deflectedPhysicalYaw;
    private static double heldTarget;
    private static int commandTicksObserved;
    private static int holdTicksObserved;
    private static int returnTicksObserved;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;

    private enum Stage {
        ASSEMBLY,
        PHYSICS_INITIALIZATION,
        CARRIER_NETWORK,
        CHILD_ASSEMBLY,
        COMMAND_BUILD,
        COMMAND_DEFLECT,
        COMMAND_HOLD,
        COMMAND_RETURN,
        NEUTRAL_SETTLE
    }

    private SkyforgeSwivelControlChildLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSwivelControlChildLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeSwivelControlChildLifecycleAcceptance::onServerTickPost);
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
                fail(SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        diagnostic(SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "real Physics Assembler exists before primary assembly",
                                now, now, sourceIds(), safeServerState(),
                                "assemblerBlockEntity=" + (assembler == null ? "null" : assembler.getClass().getName())),
                        "Physics Assembler block entity missing or wrong type");
            }
            stage = Stage.ASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                    "accepted PLATFORM-003 carrier assembles into exactly one live Sable primary body",
                    now, now + ASSEMBLY_DEADLINE_TICKS, sourceIds(), safeServerState(),
                    "glueId=" + glueId + " beforeSubLevelIds=" + beforeIds);
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
            pollAssembly(now, true);
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(stage == Stage.ASSEMBLY ? SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY
                                : SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                        diagnostic(stage == Stage.ASSEMBLY ? SkyforgeCompilerIntegrationPhase.ASSEMBLY
                                        : SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "bounded Swivel fixture reaches primary assembly", now, now,
                                sourceIds(), safeServerState(), exception.toString()),
                        "Swivel fixture startup failed: " + exception);
            }
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null || stage == null) return;
        long now = level.getGameTime();
        try {
            switch (stage) {
                case ASSEMBLY -> pollAssembly(now, false);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case CARRIER_NETWORK -> pollCarrierNetwork(now);
                case CHILD_ASSEMBLY -> pollChildAssembly(now);
                case COMMAND_BUILD -> pollCommandBuild(now);
                case COMMAND_DEFLECT -> pollCommandDeflect(now);
                case COMMAND_HOLD -> pollCommandHold(now);
                case COMMAND_RETURN -> pollCommandReturn(now);
                case NEUTRAL_SETTLE -> pollNeutralSettle(now);
            }
        } catch (ReflectiveOperationException exception) {
            failReflection(stage == Stage.CHILD_ASSEMBLY
                    ? SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY
                    : stage == Stage.PHYSICS_INITIALIZATION
                            ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                            : SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD, exception);
        } catch (RuntimeException exception) {
            fail(stage == Stage.CHILD_ASSEMBLY
                            ? SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY
                            : stage == Stage.PHYSICS_INITIALIZATION
                                    ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                                    : SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic(exception.toString()), "Swivel control-child lifecycle failed: " + exception);
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
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "canonicalBody=null"),
                        "canonical live Sable body did not remain available");
            }
            return;
        }
        if (physicsSystem == null) physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "currentPhysicsHandle=null"),
                        "current Sable physics handle did not become valid");
            }
            return;
        }
        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
            physicsPauseChanged = true;
        }
        for (BlockPos pos : List.of(movedMotor, movedShaft, movedEndpoint)) requireCanonicalPlotOwnership(canonical, pos);
        requireExpectedMovedBlockEntity(canonical, movedMotor, "CreativeMotorBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        stage = Stage.CARRIER_NETWORK;
        waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "accepted moving Create carrier network initializes before Swivel realization",
                now, now + CARRIER_NETWORK_DEADLINE_TICKS, movedIds(), safeServerState(),
                "currentPhysicsHandleValid=true");
    }

    private static void pollCarrierNetwork(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object endpoint = requireExpectedMovedBlockEntity(canonical, movedEndpoint, "GearboxBlockEntity", now);
        KineticState carrier = kineticState(endpoint);
        if (Math.abs(carrier.speed()) > 0.0f && carrier.hasSource() && carrier.hasNetwork()) {
            carrierSpeed = carrier.speed();
            removePrimaryAssemblyGlue();
            realizeSwivelAndChild(canonical, now);
            stage = Stage.CHILD_ASSEMBLY;
            waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "real moved Swivel Bearing creates exactly one bounded Sable control child",
                    now, now + CHILD_ASSEMBLY_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "carrierSpeed=" + carrierSpeed + " childGlueId=" + childGlueId);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "carrier=" + carrier),
                    "accepted moving Create carrier network did not initialize");
        }
    }

    private static void pollChildAssembly(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        if (bearing == null) return;
        if (!Boolean.TRUE.equals(publicMethod(bearing, "isAssembled").invoke(bearing))) {
            Object last = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_CHILD_ASSEMBLY,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "lastAssemblyException=" + last),
                        "Swivel child did not assemble before deadline");
            }
            return;
        }
        Object child = requireSingleChild(bearing);
        childId = subLevelUniqueId(child);
        if (childId.equals(bodyId)) throw new IllegalStateException("Swivel child reused primary UUID");
        for (BlockPos pos : movedChild) {
            if (!level.getBlockState(pos).isAir()) {
                throw new IllegalStateException("Swivel child source cell remained in parent plot at " + pos);
            }
        }
        Object massTracker = publicMethod(child, "getMassTracker").invoke(child);
        double childMass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
        if (!(childMass > 0.0)) throw new IllegalStateException("Swivel child mass is not positive: " + childMass);
        neutralTarget = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        neutralPhysicalYaw = physicalYawDegrees(canonical, child);
        if (Math.abs(neutralTarget) > TARGET_TOLERANCE_DEGREES
                || Math.abs(neutralPhysicalYaw) > PHYSICAL_NEUTRAL_TOLERANCE_DEGREES) {
            throw new IllegalStateException("Swivel child did not begin neutral target=" + neutralTarget
                    + " physicalYaw=" + neutralPhysicalYaw);
        }
        realizeCommandNetwork(canonical, now);
        stage = Stage.COMMAND_BUILD;
        waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "real Create command network reaches Swivel hidden extra cog at 16 RPM",
                now, now + COMMAND_BUILD_DEADLINE_TICKS, movedIds(), safeServerState(),
                "childId=" + childId + " childMass=" + childMass);
    }

    private static void pollCommandBuild(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        Object cog = requireExpectedMovedBlockEntity(canonical, movedDriveCog, "KineticBlockEntity", now);
        Object motor = requireExpectedMovedBlockEntity(canonical, movedCommandMotor, "CreativeMotorBlockEntity", now);
        Object extra = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        KineticState cogState = kineticState(cog);
        KineticState extraState = kineticState(extra);
        double motorRpm = number(publicMethod(motor, "getGeneratedSpeed").invoke(motor));
        if (Math.abs(motorRpm) >= COMMAND_RPM - 0.01 && Math.abs(cogState.speed()) > 0.0f
                && Math.abs(extraState.speed()) > 0.0f && extraState.hasNetwork()) {
            if (Math.signum(cogState.speed()) == Math.signum(extraState.speed())) {
                throw new IllegalStateException("Swivel small-cog mesh did not reverse sign: drive=" + cogState
                        + " extra=" + extraState);
            }
            commandExtraCogSpeed = extraState.speed();
            neutralTarget = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
            commandTicksObserved = 0;
            stage = Stage.COMMAND_DEFLECT;
            waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "positive real Create command drives signed Swivel target and physical child response",
                    now, now + COMMAND_PHASE_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "motorRpm=" + motorRpm + " driveCog=" + cogState + " extraCog=" + extraState);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "motorRpm=" + motorRpm + " driveCog=" + cogState + " extraCog=" + extraState),
                    "Swivel command network did not initialize");
        }
    }

    private static void pollCommandDeflect(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        Object child = requireCurrentChild(bearing);
        Object extra = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        KineticState extraState = kineticState(extra);
        if (Math.abs(extraState.speed()) > 0.0f) commandTicksObserved++;
        double target = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double physical = physicalYawDegrees(canonical, child);
        if (commandTicksObserved >= COMMAND_TICKS) {
            double delta = signedDeltaDegrees(neutralTarget, target);
            if (Math.abs(delta) < 10.0 || Math.signum(delta) != Math.signum(commandExtraCogSpeed)) {
                throw new IllegalStateException("Swivel target did not follow signed extra-cog command: delta=" + delta
                        + " extraCogSpeed=" + commandExtraCogSpeed);
            }
            if (Math.abs(physical) < PHYSICAL_RESPONSE_MIN_DEGREES || Math.signum(physical) != Math.signum(delta)) {
                throw new IllegalStateException("Swivel child physical yaw did not follow target: targetDelta=" + delta
                        + " physicalYaw=" + physical);
            }
            deflectedTarget = target;
            deflectedPhysicalYaw = physical;
            if (Boolean.getBoolean("skyforge.dev.compilerPlatformAerodynamicForceObservation")) {
                SkyforgeAerodynamicForceObservationAcceptance.verify(level, canonical, child, 10.0);
            }
            setMotorSpeed(movedCommandMotor, 0);
            holdTicksObserved = 0;
            stage = Stage.COMMAND_HOLD;
            waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "zero source RPM stops hidden cog and locked Swivel holds commanded target",
                    now, now + COMMAND_PHASE_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "deflectedTarget=" + deflectedTarget + " deflectedPhysicalYaw=" + deflectedPhysicalYaw);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "ticks=" + commandTicksObserved + " target=" + target + " physicalYaw=" + physical),
                    "Swivel signed deflection did not complete");
        }
    }

    private static void pollCommandHold(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        Object child = requireCurrentChild(bearing);
        KineticState extraState = kineticState(publicMethod(bearing, "getExtraKinetics").invoke(bearing));
        double target = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double physical = physicalYawDegrees(canonical, child);
        if (Math.abs(extraState.speed()) <= 0.01f) {
            if (holdTicksObserved == 0) heldTarget = target;
            holdTicksObserved++;
            if (holdTicksObserved >= HOLD_TICKS) {
                if (Math.abs(signedDeltaDegrees(heldTarget, target)) > TARGET_TOLERANCE_DEGREES) {
                    throw new IllegalStateException("locked Swivel target drifted during hold: start=" + heldTarget
                            + " end=" + target);
                }
                if (Math.abs(signedDeltaDegrees(target, physical)) > 6.0) {
                    throw new IllegalStateException("physical Swivel child did not settle near held target: target="
                            + target + " physicalYaw=" + physical);
                }
                setMotorSpeed(movedCommandMotor, -COMMAND_RPM);
                returnTicksObserved = 0;
                stage = Stage.COMMAND_RETURN;
                waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                        "inverse real Create command returns Swivel target toward neutral",
                        now, now + COMMAND_PHASE_DEADLINE_TICKS, movedIds(), safeServerState(),
                        "heldTarget=" + heldTarget + " heldPhysicalYaw=" + physical);
                return;
            }
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "extraCog=" + extraState + " target=" + target + " physicalYaw=" + physical),
                    "Swivel command source did not stop and hold");
        }
    }

    private static void pollCommandReturn(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        Object extra = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        KineticState extraState = kineticState(extra);
        if (Math.abs(extraState.speed()) > 0.0f && Math.signum(extraState.speed()) == -Math.signum(commandExtraCogSpeed)) {
            returnTicksObserved++;
        }
        if (returnTicksObserved >= COMMAND_TICKS) {
            setMotorSpeed(movedCommandMotor, 0);
            stage = Stage.NEUTRAL_SETTLE;
            waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "returned Swivel target and physical child settle near initial neutral",
                    now, now + COMMAND_PHASE_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "returnTicks=" + returnTicksObserved + " inverseExtraCog=" + extraState);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "returnTicks=" + returnTicksObserved + " extraCog=" + extraState),
                    "inverse Swivel command did not complete");
        }
    }

    private static void pollNeutralSettle(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object handle = findCurrentPhysicsHandle(canonical);
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        Object child = requireCurrentChild(bearing);
        KineticState extraState = kineticState(publicMethod(bearing, "getExtraKinetics").invoke(bearing));
        double target = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double physical = physicalYawDegrees(canonical, child);
        double targetFromNeutral = signedDeltaDegrees(neutralTarget, target);
        double physicalFromNeutral = signedDeltaDegrees(neutralPhysicalYaw, physical);
        if (handle != null && Math.abs(extraState.speed()) <= 0.01f
                && Math.abs(targetFromNeutral) <= TARGET_TOLERANCE_DEGREES
                && Math.abs(physicalFromNeutral) <= PHYSICAL_NEUTRAL_TOLERANCE_DEGREES) {
            restorePhysicsPause();
            removeFixtureForceLoadTicket();
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId + " childId=" + childId
                            + " movedOffset=" + movedOffset + " carrierSpeed=" + carrierSpeed
                            + " commandExtraCogSpeed=" + commandExtraCogSpeed
                            + " commandTicks=" + commandTicksObserved
                            + " neutralTarget=" + neutralTarget + " deflectedTarget=" + deflectedTarget
                            + " deflectedPhysicalYaw=" + deflectedPhysicalYaw
                            + " heldTarget=" + heldTarget + " returnTicks=" + returnTicksObserved
                            + " finalTarget=" + target + " finalPhysicalYaw=" + physical
                            + " signedDeflectionObserved=true lockedHoldObserved=true inverseNeutralReturnObserved=true"
                            + " currentPhysicsHandleValid=true canonicalBodyResolutionPerPhase=true"
                            + " childResolutionPerPhase=true directTargetMutation=false"
                            + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "extraCog=" + extraState + " targetFromNeutral=" + targetFromNeutral
                                    + " physicalFromNeutral=" + physicalFromNeutral),
                    "Swivel did not settle back near neutral");
        }
    }

    private static void removePrimaryAssemblyGlue() throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB sourceBox = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class)
                .invoke(null, GLUE_MIN, GLUE_MAX);
        AABB expectedMovedBox = sourceBox.move(movedOffset.getX(), movedOffset.getY(), movedOffset.getZ());
        Entity matched = null;
        int matches = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, expectedMovedBox.inflate(1.0))) {
            if (glueClass.isInstance(entity) && sameBox(entity.getBoundingBox(), expectedMovedBox)) {
                matched = entity;
                matches++;
            }
        }
        if (matches > 1) {
            throw new IllegalStateException("duplicate moved primary assembly glue domains before Swivel realization: "
                    + matches + " expected=" + expectedMovedBox);
        }
        if (matched != null) {
            UUID movedGlueId = matched.getUUID();
            matched.discard();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PRIMARY_ASSEMBLY_GLUE_RELEASED bodyId=" + bodyId
                            + " sourceGlueId=" + glueId + " movedGlueId=" + movedGlueId
                            + " resolution=current-moved-box");
        } else {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PRIMARY_ASSEMBLY_GLUE_RELEASED bodyId=" + bodyId
                            + " sourceGlueId=" + glueId + " movedGlueId=absent-already-consumed"
                            + " resolution=current-moved-box");
        }
        for (Entity entity : level.getEntitiesOfClass(Entity.class, expectedMovedBox.inflate(1.0))) {
            if (glueClass.isInstance(entity) && sameBox(entity.getBoundingBox(), expectedMovedBox)) {
                throw new IllegalStateException("fixture-only moved primary assembly glue remained after discard: "
                        + entity.getUUID());
            }
        }
    }

    private static boolean sameBox(AABB first, AABB second) {
        double epsilon = 1.0e-6;
        return Math.abs(first.minX - second.minX) < epsilon
                && Math.abs(first.minY - second.minY) < epsilon
                && Math.abs(first.minZ - second.minZ) < epsilon
                && Math.abs(first.maxX - second.maxX) < epsilon
                && Math.abs(first.maxY - second.maxY) < epsilon
                && Math.abs(first.maxZ - second.maxZ) < epsilon;
    }

    private static void realizeSwivelAndChild(Object canonical, long now) throws ReflectiveOperationException {
        movedSwivel = SWIVEL_SOURCE.offset(movedOffset);
        movedDriveCog = DRIVE_COG_SOURCE.offset(movedOffset);
        movedCommandMotor = COMMAND_MOTOR_SOURCE.offset(movedOffset);
        movedChild = CHILD_SOURCE.stream().map(p -> p.offset(movedOffset)).toList();
        for (BlockPos pos : movedChild) requireCanonicalPlotOwnership(canonical, pos);
        for (BlockPos pos : List.of(movedSwivel, movedDriveCog, movedCommandMotor)) requireCanonicalPlotOwnership(canonical, pos);
        BlockState swivel = withProperty(requireBlock(SWIVEL_ID).defaultBlockState(), "facing", "up");
        if (!level.setBlock(movedSwivel, swivel, 3)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT, finalDiagnostic("movedSwivel=" + movedSwivel),
                    "failed to realize Swivel Bearing on canonical parent");
        }
        BlockState sail = withProperty(requireBlock(SAIL_ID).defaultBlockState(), "axis", "z");
        for (BlockPos pos : movedChild) {
            if (!level.setBlock(pos, sail, 3)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT, finalDiagnostic("childPos=" + pos),
                        "failed to realize bounded Swivel child payload");
            }
        }
        childGlueId = addGlueAt(movedChild.getFirst(), movedChild.getLast(), "Swivel child");
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedSwivel, "SwivelBearingBlockEntity", now);
        publicMethod(bearing, "assemble").invoke(bearing);
        LOGGER.log(System.Logger.Level.INFO, PREFIX + " CHILD_ASSEMBLY_REQUEST bodyId=" + bodyId
                + " movedSwivel=" + movedSwivel + " childGlueId=" + childGlueId + " tick=" + now);
    }

    private static void realizeCommandNetwork(Object canonical, long now) throws ReflectiveOperationException {
        BlockState cog = withProperty(requireBlock(COG_ID).defaultBlockState(), "axis", "y");
        BlockState motor = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "up");
        if (!level.setBlock(movedDriveCog, cog, 3) || !level.setBlock(movedCommandMotor, motor, 3)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    finalDiagnostic("driveCog=" + movedDriveCog + " commandMotor=" + movedCommandMotor),
                    "failed to realize real Create Swivel command network");
        }
        requireExpectedMovedBlockEntity(canonical, movedDriveCog, "KineticBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedCommandMotor, "CreativeMotorBlockEntity", now);
        setMotorSpeed(movedCommandMotor, COMMAND_RPM);
    }

    private static UUID addGlueAt(BlockPos min, BlockPos max, String label) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, min, max);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) throw new IllegalStateException("failed to add " + label + " glue domain");
        return glue.getUUID();
    }

    private static Object requireSingleChild(Object bearing) throws ReflectiveOperationException {
        Object raw = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(raw instanceof Iterable<?> iterable)) throw new IllegalStateException("Swivel has no child dependencies");
        Object child = null;
        int count = 0;
        for (Object value : iterable) {
            if (value != null && value.getClass().getName().endsWith("ServerSubLevel")) {
                child = value;
                count++;
            }
        }
        if (count != 1 || child == null) throw new IllegalStateException("expected exactly one Swivel child, got " + count);
        return child;
    }

    private static Object requireCurrentChild(Object bearing) throws ReflectiveOperationException {
        Object child = requireSingleChild(bearing);
        UUID current = subLevelUniqueId(child);
        if (childId != null && !childId.equals(current)) {
            throw new IllegalStateException("Swivel child identity changed: expected=" + childId + " actual=" + current);
        }
        return child;
    }

    private static double physicalYawDegrees(Object parent, Object child) throws ReflectiveOperationException {
        Object parentPose = publicMethod(parent, "logicalPose").invoke(parent);
        Object childPose = publicMethod(child, "logicalPose").invoke(child);
        Object parentQ = publicMethod(parentPose, "orientation").invoke(parentPose);
        Object childQ = publicMethod(childPose, "orientation").invoke(childPose);
        if (!(parentQ instanceof Quaterniondc pq) || !(childQ instanceof Quaterniondc cq)) {
            throw new IllegalStateException("Sable poses did not expose Quaterniondc orientations");
        }
        Quaterniond relative = new Quaterniond(pq).conjugate().mul(new Quaterniond(cq));
        Vector3d forward = relative.transform(new Vector3d(1.0, 0.0, 0.0));
        double yaw = Math.toDegrees(Math.atan2(-forward.z, forward.x));
        if (!Double.isFinite(yaw)) throw new IllegalStateException("non-finite Swivel child physical yaw");
        return yaw;
    }

    private static void setMotorSpeed(BlockPos pos, int rpm) throws ReflectiveOperationException {
        BlockEntity motor = level.getBlockEntity(pos);
        if (motor == null || !motor.getClass().getName().endsWith("CreativeMotorBlockEntity")) {
            throw new IllegalStateException("command motor missing at " + pos);
        }
        Object behaviour = motor.getClass().getField("generatedSpeed").get(motor);
        publicMethod(behaviour, "setValue", int.class).invoke(behaviour, rpm);
    }

    private static double signedDeltaDegrees(double from, double to) {
        double delta = (to - from) % 360.0;
        if (delta > 180.0) delta -= 360.0;
        else if (delta <= -180.0) delta += 360.0;
        return delta;
    }

    private static void requireRuntimePreconditions() {
        long now = level.getGameTime();
        try {
            if (!ModList.get().isLoaded("create")) throw new IllegalStateException("Create mod not loaded");
            for (ResourceLocation resource : List.of(PHYSICS_ASSEMBLER, MOTOR_ID, SHAFT_ID, ENDPOINT_ID,
                    COG_ID, SWIVEL_ID, SAIL_ID)) requireBlock(resource);
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "exact pinned Create/Sable/Simulated Swivel runtime resolves",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(), exception.toString()),
                    "required exact-stack Swivel resource unavailable: " + exception);
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = -2; x <= 5; x++) {
            for (int y = 198; y <= 206; y++) {
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
            if (movedCommandMotor != null && level != null && level.getBlockEntity(movedCommandMotor) != null) {
                setMotorSpeed(movedCommandMotor, 0);
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
                + " endpoint=" + ENDPOINT_SOURCE + " swivelRealization=" + SWIVEL_SOURCE
                + " driveCogRealization=" + DRIVE_COG_SOURCE + " commandMotorRealization=" + COMMAND_MOTOR_SOURCE
                + " glueId=" + glueId;
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " childId=" + childId + " movedOffset=" + movedOffset
                + " carrierMotor=" + movedMotor + " carrierShaft=" + movedShaft + " carrierEndpoint=" + movedEndpoint
                + " swivel=" + movedSwivel + " driveCog=" + movedDriveCog + " commandMotor=" + movedCommandMotor
                + " childPayload=" + movedChild + " childGlueId=" + childGlueId;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        String ids;
        try { ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds()); }
        catch (ReflectiveOperationException exception) { ids = "subLevelIds=<reflection-failed:" + exception.getClass().getSimpleName() + ">"; }
        return "gameTime=" + level.getGameTime() + " stage=" + stage + " subLevelIds=" + ids
                + " movedSwivelState=" + (movedSwivel == null ? "n/a" : level.getBlockState(movedSwivel))
                + " commandMotorState=" + (movedCommandMotor == null ? "n/a" : level.getBlockState(movedCommandMotor));
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
