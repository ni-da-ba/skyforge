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
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** PLATFORM-001: minimal exact-stack Sable primary assembly and physics lifecycle fixture. */
final class SkyforgeSableAssemblyLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformSableAssemblyLifecycle";
    static final String CAPABILITY = "SABLE_PRIMARY_ASSEMBLY_LIFECYCLE";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeSableAssemblyLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_SABLE_ASSEMBLY_LIFECYCLE";
    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final BlockPos BODY_MIN = new BlockPos(0, 200, 0);
    private static final BlockPos BODY_MAX = new BlockPos(1, 201, 1);
    private static final BlockPos ASSEMBLER_POS = new BlockPos(0, 202, 0);
    private static final BlockPos GLUE_MIN = BODY_MIN;
    private static final BlockPos GLUE_MAX = new BlockPos(1, 202, 1);
    private static final long ASSEMBLY_DEADLINE_TICKS = 80L;
    private static final long PHYSICS_INITIALIZATION_DEADLINE_TICKS = 40L;
    private static final long PHYSICS_DEADLINE_TICKS = 80L;
    private static final double TARGET_VELOCITY_X = 1.0;
    private static final double MIN_TRANSLATION_X = 0.05;

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
    private static boolean physicsWasPaused;
    private static double startPoseX;
    private static boolean velocityApplied;
    private static boolean complete;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;

    private SkyforgeSableAssemblyLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSableAssemblyLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeSableAssemblyLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(
                System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            beforeIds = currentSubLevelIds(container);
            prepareFixture(level);
            glueId = addFixtureGlue(level);
            BlockEntity assembler = level.getBlockEntity(ASSEMBLER_POS);
            if (assembler == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "Physics Assembler block entity exists after placement",
                                now,
                                now,
                                "assembler=" + ASSEMBLER_POS,
                                "block=" + level.getBlockState(ASSEMBLER_POS),
                                "missing block entity"),
                        "Physics Assembler block entity missing after placement");
            }
            if (!assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "Physics Assembler block entity has expected runtime type",
                                now,
                                now,
                                "assembler=" + ASSEMBLER_POS,
                                "blockEntity=" + assembler.getClass().getName(),
                                "unexpected block entity"),
                        "unexpected Physics Assembler block entity " + assembler.getClass().getName());
            }

            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                    "exactly one new canonical Sable ServerSubLevel UUID is registered",
                    now,
                    now + ASSEMBLY_DEADLINE_TICKS,
                    "assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                    "beforeSubLevelIds=" + beforeIds,
                    "assembly invoked; awaiting registration");
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
            // Assembly is synchronous. Observe registration before the first physics tick so a
            // short-lived body is classified as a lifecycle failure rather than a false timeout.
            pollAssembly(now, true);
        } catch (ReflectiveOperationException exception) {
            failReflection(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                                "minimal Sable fixture is placed and assembly is invoked",
                                now,
                                now,
                                "assembler=" + ASSEMBLER_POS,
                                safeServerState(),
                                exception.toString()),
                        "fixture preparation failed: " + exception);
            }
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null) {
            return;
        }
        long now = level.getGameTime();
        try {
            switch (waitDiagnostic.phase()) {
                case ASSEMBLY -> pollAssembly(now, false);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case PHYSICS_PROGRESSION -> pollPhysics(now);
                default -> fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        finalDiagnostic("unexpected lifecycle phase " + waitDiagnostic.phase()),
                        "fixture entered unexpected wait phase");
            }
        } catch (ReflectiveOperationException exception) {
            SkyforgeCompilerIntegrationFailure code =
                    waitDiagnostic.phase() == SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION
                                    || waitDiagnostic.phase() == SkyforgeCompilerIntegrationPhase.PHYSICS_PROGRESSION
                            ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                            : SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
            failReflection(code, exception);
        }
    }

    private static void pollAssembly(long now, boolean synchronousObservation) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds(container);
        Set<UUID> created = new LinkedHashSet<>(currentIds);
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(
                            "assembler=" + ASSEMBLER_POS + " glueId=" + glueId + " createdIds=" + created,
                            safeServerState(),
                            "headless",
                            "more than one new Sable body registered"),
                    "minimal fixture created multiple Sable bodies");
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            // Do not canonically resolve the just-created body inside the assembler callback.
            // The pinned Sable/Simulated stack can expose client-only signatures while that
            // transient object is still inside synchronous assembly. Persistent UUID authority
            // begins here; canonical live-body authority is required on the next bounded phase.
            int sourceNonAir = countSourceFixtureNonAir();
            Object listedBody = findListedBody(bodyId);
            if (listedBody == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(
                                "bodyId=" + bodyId + " assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                                safeServerState(),
                                "headless",
                                "UUID was observed in the collection but the listed object could not be reselected"),
                        "new Sable UUID did not retain an assembly-time collection object");
            }
            assembledBody = listedBody;
            Object massTracker = publicMethod(listedBody, "getMassTracker").invoke(listedBody);
            Object massValue = massTracker == null ? null : publicMethod(massTracker, "getMass").invoke(massTracker);
            Object centerOfMass = massTracker == null ? null : publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            double mass = massValue instanceof Number number ? number.doubleValue() : Double.NaN;
            Object removedValue = publicMethod(listedBody, "isRemoved").invoke(listedBody);
            boolean removed = removedValue instanceof Boolean booleanValue && booleanValue;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    PREFIX + " ASSEMBLED bodyId=" + bodyId
                            + " sourceNonAirAfterAssembly=" + sourceNonAir
                            + " mass=" + mass
                            + " centerOfMass=" + centerOfMass
                            + " removed=" + removed
                            + " synchronous=" + synchronousObservation);
            if (sourceNonAir != 0) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(
                                "bodyId=" + bodyId + " assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                                safeServerState(),
                                "headless",
                                "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                                        + " sourceNonAirAfterAssembly=" + sourceNonAir),
                        "minimal fixture did not transfer every source cell into the Sable body");
            }
            if (removed) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                                "transferred Sable body remains live after synchronous assembly",
                                now,
                                now,
                                "bodyId=" + bodyId + " assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                                safeServerState(),
                                "sourceNonAirAfterAssembly=" + sourceNonAir
                                        + " mass=" + mass
                                        + " centerOfMass=" + centerOfMass),
                        "new Sable body is already marked removed when the assembler returns");
            }
            if (!(mass > 0.0) || centerOfMass == null) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                                "transferred Sable body has positive merged mass and non-null center of mass",
                                now,
                                now,
                                "bodyId=" + bodyId + " assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                                safeServerState(),
                                "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                                        + " sourceNonAirAfterAssembly=" + sourceNonAir
                                        + " mass=" + mass
                                        + " centerOfMass=" + centerOfMass),
                        "new Sable body is mass-invalid immediately after complete source transfer");
            }
            addFixtureForceLoadTicket(listedBody);
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "registered Sable UUID survives initialization and resolves to a valid current physics handle",
                    now,
                    now + PHYSICS_INITIALIZATION_DEADLINE_TICKS,
                    "bodyId=" + bodyId + " assembler=" + ASSEMBLER_POS + " glueId=" + glueId,
                    safeServerState(),
                    "assemblyRegistrationObservedSynchronously=" + synchronousObservation
                            + " sourceNonAirAfterAssembly=" + sourceNonAir
                            + " fixtureLivenessTicket=sable:command_forced");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(
                            "assembler=" + ASSEMBLER_POS + " glueId=" + glueId + " createdIds=" + created,
                            safeServerState(),
                            "headless",
                            "currentSubLevelIds=" + currentIds),
                    "Sable body registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds(container);
        if (!currentIds.contains(bodyId)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    waitDiagnostic.withFinalState(
                            "bodyId=" + bodyId + " currentSubLevelIds=" + currentIds,
                            safeServerState(),
                            "headless",
                            "registered body disappeared despite the bounded Sable liveness ticket"),
                    "registered Sable body disappeared before physics initialization");
        }

        Object canonicalBody = findCanonicalBody(bodyId);
        if (canonicalBody == null) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(
                                "bodyId=" + bodyId,
                                safeServerState(),
                                "headless",
                                "UUID remained registered but canonical body lookup returned null"),
                        "canonical Sable body did not become available before physics initialization deadline");
            }
            return;
        }

        if (physicsSystem == null) {
            physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        }
        if (physicsSystem == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    waitDiagnostic.withFinalState(
                            "bodyId=" + bodyId,
                            safeServerState(),
                            "headless",
                            "Sable physicsSystem=null"),
                    "Sable physics system unavailable after body registration");
        }

        Object handle = findCurrentPhysicsHandle(canonicalBody);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(
                                "bodyId=" + bodyId,
                                safeServerState(),
                                "headless",
                                "canonical body exists but no valid current physics handle was observed"),
                        "Sable physics handle did not become valid before initialization deadline");
            }
            return;
        }

        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
        }
        startPoseX = poseX(canonicalBody);
        setLinearVelocityX(handle, TARGET_VELOCITY_X);
        velocityApplied = true;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.PHYSICS_PROGRESSION,
                "canonical Sable body translates after bounded velocity nudge",
                now,
                now + PHYSICS_DEADLINE_TICKS,
                "bodyId=" + bodyId,
                "startPoseX=" + startPoseX + " handleValid=true physicsPaused=false",
                "velocityX=" + TARGET_VELOCITY_X);
    }

    private static void pollPhysics(long now) throws ReflectiveOperationException {
        Set<UUID> currentIds = currentSubLevelIds(container);
        Object canonicalBody = findCanonicalBody(bodyId);
        if (!currentIds.contains(bodyId) || canonicalBody == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    waitDiagnostic.withFinalState(
                            "bodyId=" + bodyId + " currentSubLevelIds=" + currentIds,
                            safeServerState(),
                            "headless",
                            "body disappeared after a valid physics handle had already been observed"),
                    "canonical Sable body disappeared during physics progression");
        }
        Object handle = findCurrentPhysicsHandle(canonicalBody);
        if (handle == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    waitDiagnostic.withFinalState(
                            "bodyId=" + bodyId,
                            safeServerState(),
                            "headless",
                            "physics handle became null or invalid after initialization"),
                    "Sable physics handle was removed during physics progression");
        }
        double currentPoseX = poseX(canonicalBody);
        double deltaX = currentPoseX - startPoseX;
        if (Math.abs(deltaX) >= MIN_TRANSLATION_X) {
            setLinearVelocityX(handle, 0.0);
            velocityApplied = false;
            restorePhysicsPause();
            removeFixtureForceLoadTicket();
            complete = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " bodyId=" + bodyId
                            + " startPoseX=" + startPoseX
                            + " finalPoseX=" + currentPoseX
                            + " deltaX=" + deltaX
                            + " canonicalResolutionPerTick=true"
                            + " staleHandleRetained=false"
                            + " fixtureLivenessTicket=sable:command_forced(released)"
                            + " clientState=headless");
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_PROGRESSION,
                    waitDiagnostic.withFinalState(
                            "bodyId=" + bodyId,
                            safeServerState(),
                            "headless",
                            "currentPoseX=" + currentPoseX + " deltaX=" + deltaX + " handleValid=true"),
                    "canonical Sable body did not translate before physics deadline");
        }
    }

    private static void requireRuntimePreconditions() {
        if (!ModList.get().isLoaded("create")) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_TARGET_LOWERING,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "Create is loaded from the exact C11 resolved runtime",
                            level.getGameTime(),
                            level.getGameTime(),
                            "modId=create",
                            safeServerState(),
                            "missing Create mod"),
                    "required exact-stack Create mod not loaded");
        }
        requireBlock(PHYSICS_ASSEMBLER);
        try {
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper");
        } catch (ClassNotFoundException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_TARGET_LOWERING,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "required Sable/Simulated runtime classes resolve",
                            level.getGameTime(),
                            level.getGameTime(),
                            "class=" + exception.getMessage(),
                            safeServerState(),
                            exception.toString()),
                    "required exact-stack runtime class unavailable");
        }
    }

    private static void prepareFixture(ServerLevel level) {
        level.getChunk(0, 0);
        for (int x = -2; x <= 3; x++) {
            for (int y = 198; y <= 205; y++) {
                for (int z = -2; z <= 3; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = BODY_MIN.getX(); x <= BODY_MAX.getX(); x++) {
            for (int y = BODY_MIN.getY(); y <= BODY_MAX.getY(); y++) {
                for (int z = BODY_MIN.getZ(); z <= BODY_MAX.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3)) {
                        throw new IllegalStateException("failed to place body fixture block at " + pos);
                    }
                }
            }
        }
        BlockState assemblerState = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(ASSEMBLER_POS, assemblerState, 3)) {
            throw new IllegalStateException("failed to place Physics Assembler at " + ASSEMBLER_POS);
        }
    }


    private static UUID addFixtureGlue(ServerLevel level) throws ReflectiveOperationException {
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

    private static Set<UUID> currentSubLevelIds(Object container) throws ReflectiveOperationException {
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


    private static int countSourceFixtureNonAir() {
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

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        // ServerSubLevelContainer overrides getAllSubLevels(), so selecting the current live
        // object by persistent UUID avoids reflecting across SubLevelContainer's client-side
        // overload signatures while preserving canonical live-body authority.
        return findListedBody(uuid);
    }

    private static Object requireCanonicalBody(UUID uuid) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(uuid);
        if (canonical == null) {
            throw new IllegalStateException("Sable canonical live body unavailable for UUID " + uuid);
        }
        return canonical;
    }

    private static Object findCurrentPhysicsHandle(Object canonicalBody) throws ReflectiveOperationException {
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", canonicalBody)
                .invoke(physicsSystem, canonicalBody);
        if (handle == null) {
            return null;
        }
        Object valid = publicMethod(handle, "isValid").invoke(handle);
        return valid instanceof Boolean booleanValid && booleanValid ? handle : null;
    }

    private static Object requireCurrentPhysicsHandle(Object canonicalBody) throws ReflectiveOperationException {
        Object handle = findCurrentPhysicsHandle(canonicalBody);
        if (handle == null) {
            throw new IllegalStateException("Sable physics handle unavailable or removed for canonical body " + bodyId);
        }
        return handle;
    }

    private static double poseX(Object canonicalBody) throws ReflectiveOperationException {
        Object pose = publicMethod(canonicalBody, "logicalPose").invoke(canonicalBody);
        Object position = publicMethod(pose, "position").invoke(pose);
        if (!(position instanceof Vector3dc vector)) {
            throw new IllegalStateException("Sable logical pose position is not Vector3dc: " + position);
        }
        return vector.x();
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

    private static void addFixtureForceLoadTicket(Object body) throws ReflectiveOperationException {
        Class<?> ticketTypeClass = Class.forName(
                "dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        Class<?> unitClass = Class.forName("net.minecraft.util.Unit");
        forceLoadTicketKey = unitClass.getField("INSTANCE").get(null);
        Method addTicket = container.getClass().getMethod(
                "addForceLoadTicket", body.getClass(), ticketTypeClass, Object.class);
        Object added = addTicket.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(added instanceof Boolean addedBoolean) || !addedBoolean) {
            throw new IllegalStateException(
                    "Sable command-forced liveness ticket was not added for body " + bodyId);
        }
        forceLoadTicketAdded = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                PREFIX + " LIVENESS_TICKET_ADDED bodyId=" + bodyId + " ticket=sable:command_forced");
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || assembledBody == null || forceLoadTicketType == null || forceLoadTicketKey == null) {
            return;
        }
        Method removeTicket = container.getClass().getMethod(
                "removeForceLoadTicket", assembledBody.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object removed = removeTicket.invoke(container, assembledBody, forceLoadTicketType, forceLoadTicketKey);
        if (!(removed instanceof Boolean removedBoolean) || !removedBoolean) {
            throw new IllegalStateException(
                    "Sable command-forced liveness ticket was not removed for body " + bodyId);
        }
        forceLoadTicketAdded = false;
        LOGGER.log(
                System.Logger.Level.INFO,
                PREFIX + " LIVENESS_TICKET_REMOVED bodyId=" + bodyId + " ticket=sable:command_forced");
    }

    private static void removeFixtureForceLoadTicketQuietly() {
        try {
            removeFixtureForceLoadTicket();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Preserve the original classified fixture result as authoritative.
        }
    }

    private static void restorePhysicsPause() throws ReflectiveOperationException {
        if (physicsSystem != null && physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, true);
        }
    }

    private static void cleanupPhysicsQuietly() {
        try {
            if (velocityApplied && bodyId != null && physicsSystem != null) {
                Object canonical = requireCanonicalBody(bodyId);
                setLinearVelocityX(requireCurrentPhysicsHandle(canonical), 0.0);
                velocityApplied = false;
            }
            restorePhysicsPause();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Preserve the original classified fixture failure as authoritative.
        } finally {
            removeFixtureForceLoadTicketQuietly();
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

    private static Method twoArgMethod(Object target, String name, Object first, Object second)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) {
                continue;
            }
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
                CAPABILITY,
                phase,
                expected,
                startTick,
                deadlineTick,
                ids,
                serverState,
                "headless",
                dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        SkyforgeCompilerIntegrationDiagnostic current = waitDiagnostic;
        if (current == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic(
                    SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                    "fixture reaches a classified terminal state",
                    now,
                    now,
                    "bodyId=" + bodyId + " glueId=" + glueId + " assembler=" + ASSEMBLER_POS,
                    safeServerState(),
                    dump);
        }
        return current.withFinalState(
                "bodyId=" + bodyId + " glueId=" + glueId + " assembler=" + ASSEMBLER_POS,
                safeServerState(),
                "headless",
                dump);
    }

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        String assembler = String.valueOf(level.getBlockState(ASSEMBLER_POS));
        String ids;
        try {
            ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds(container));
        } catch (ReflectiveOperationException exception) {
            ids = "subLevelIds=<reflection-failed:" + exception.getClass().getSimpleName() + ">";
        }
        return "gameTime=" + level.getGameTime() + " assemblerState=" + assembler + " subLevelIds=" + ids;
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
            cleanupPhysicsQuietly();
            complete = true;
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(CAPABILITY + " failed: " + code + ": " + reason);
    }
}
