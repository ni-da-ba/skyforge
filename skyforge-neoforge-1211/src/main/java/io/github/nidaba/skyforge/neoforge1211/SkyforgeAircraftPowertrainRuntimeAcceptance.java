package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** AIRCRAFT-RUNTIME-001: exact-stack v0.12 Guild utility powertrain gate at the first 128-RPM point. */
final class SkyforgeAircraftPowertrainRuntimeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftPowertrainRuntime128";
    static final String PERSISTENCE_PROPERTY = "skyforge.dev.aircraftPowertrainPersistence";
    static final String CAPABILITY = "AIRCRAFT_V012_POWERTRAIN_128_RUNTIME";
    static final String PERSISTENCE_CAPABILITY = "AIRCRAFT_V012_PERSISTENCE_RUNTIME";
    private static final System.Logger LOGGER = System.getLogger(SkyforgeAircraftPowertrainRuntimeAcceptance.class.getName());
    private static final String PREFIX = "AIRCRAFT_V012_POWERTRAIN_128_RUNTIME";
    private static final String PERSISTENCE_PREFIX = "AIRCRAFT_V012_PERSISTENCE_RUNTIME";
    private static final Path IDENTITY_FILE = Path.of("aircraft-runtime-002.identity");
    private static final long RELOAD_DEADLINE_TICKS = 180L;
    private static final long ENTITY_REHYDRATION_GRACE_TICKS = 10L;
    private static final BlockPos BASE = new BlockPos(128, 220, 0);
    private static final int ENGINE_BURN_TICKS = 1200;
    private static final int TARGET_RPM = 128;
    private static final int THRUST_SETTLE_TICKS = 48;
    private static final float RPM_TOLERANCE = 0.01f;
    private static final float STRESS_TOLERANCE = 0.01f;
    private static final long ASSEMBLY_DEADLINE_TICKS = 100L;
    private static final long PHYSICS_DEADLINE_TICKS = 60L;
    private static final long KINETIC_DEADLINE_TICKS = 120L;
    private static final long CHILD_DEADLINE_TICKS = 100L;
    private static final long THRUST_DEADLINE_TICKS = 100L;

    private static ServerLevel level;
    private static Object container;
    private static Object physicsSystem;
    private static Object assembledBody;
    private static Object forceLoadTicketType;
    private static Object forceLoadTicketKey;
    private static boolean forceLoadTicketAdded;
    private static boolean physicsWasPaused;
    private static boolean physicsPauseChanged;
    private static boolean complete;
    private static boolean powertrainActivated;
    private static Set<UUID> beforeIds = Set.of();
    private static UUID bodyId;
    private static UUID nestedChildId;
    private static BlockPos movedOffset;
    private static BlockPos movedBearing;
    private static List<BlockPos> movedChild = List.of();
    private static final Map<String, BlockPos> movedRoles = new LinkedHashMap<>();
    private static final Map<BlockPos, ExpectedBlock> mainExpected = new LinkedHashMap<>();
    private static final Map<BlockPos, ExpectedBlock> childExpected = new LinkedHashMap<>();
    private static final List<UUID> glueIds = new ArrayList<>();
    private static SkyforgeAircraftRetainedGuildUtilityFixture.Fixture compilerFixture;
    private static Stage stage;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static long thrustSettleNotBefore;
    private static float observedBearingRpm;
    private static float observedCapacity;
    private static float observedStress;
    private static float observedStressMargin;
    private static RunMode runMode = RunMode.LEGACY;
    private static String activeCapability = CAPABILITY;
    private static String activePrefix = PREFIX;
    private static UUID preReloadNestedChildId;
    private static List<UUID> preReloadGlueIds = List.of();
    private static List<UUID> postReloadGlueIds = List.of();
    private static long reloadCanonicalResolvedTick = -1L;
    private static String glueRecoveryMode = "not-applicable";
    private static String childRecoveryMode = "not-applicable";
    private static boolean reloadChildAssemblyRequested;

    private enum RunMode { LEGACY, PREPARE, VERIFY }
    private enum Stage { ASSEMBLY, PHYSICS_INITIALIZATION, KINETIC_128, CHILD_REASSEMBLY, THRUST_SETTLE, RELOAD_RECOVERY }

    private SkyforgeAircraftPowertrainRuntimeAcceptance() {}

    static void installFromSystemProperty() {
        String persistence = System.getProperty(PERSISTENCE_PROPERTY, "").trim();
        if (!persistence.isEmpty()) {
            runMode = switch (persistence) {
                case "prepare" -> RunMode.PREPARE;
                case "verify" -> RunMode.VERIFY;
                default -> throw new IllegalArgumentException("unsupported aircraft persistence mode: " + persistence);
            };
            activeCapability = PERSISTENCE_CAPABILITY;
            activePrefix = PERSISTENCE_PREFIX;
        } else if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftPowertrainRuntimeAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftPowertrainRuntimeAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO, activePrefix + " START capability=" + activeCapability + " mode=" + runMode + " startTick=" + now + " clientState=headless");
        try {
            compilerFixture = SkyforgeAircraftRetainedGuildUtilityFixture.compileV012();
            requireCompilerBoundary();
            buildExpectedMaps();
            requireRuntimePreconditions();
            container = requireServerSubLevelContainer(level);
            if (runMode == RunMode.VERIFY) {
                beginReloadVerification(now);
            } else {
                beforeIds = currentSubLevelIds();
                prepareFixture();
                addCompilerGlue();
                BlockEntity assembler = requireExpectedBlockEntity(source(compilerFixture.assemblyFixture().physicsAssemblerPlacement().point()), "PhysicsAssemblerBlockEntity");
                stage = Stage.ASSEMBLY;
                waitDiagnostic = diagnostic(
                        SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                        "compiler-emitted v0.12 specimen transfers into exactly one canonical Sable body",
                        now, now + ASSEMBLY_DEADLINE_TICKS, sourceIds(), safeServerState(),
                        "manifest=" + compilerFixture.manifest().sha256() + " powertrain=" + compilerFixture.powertrain().sha256());
                publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);
                pollAssembly(now, true);
            }
        } catch (ReflectiveOperationException exception) {
            failReflection(stage == null ? SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT : SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                SkyforgeCompilerIntegrationFailure code = stage == null
                        ? SkyforgeCompilerIntegrationFailure.FAIL_TARGET_LOWERING
                        : stage == Stage.ASSEMBLY ? SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY
                        : stage == Stage.RELOAD_RECOVERY ? SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE
                        : SkyforgeCompilerIntegrationFailure.FAIL_NETWORK;
                fail(code, diagnostic(
                        stage == null ? SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT
                                : stage == Stage.RELOAD_RECOVERY ? SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD
                                : SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                        "bounded v0.12 aircraft runtime fixture reaches its first lifecycle gate",
                        now, now, sourceIds(), safeServerState(), exception.toString()),
                        "fixture startup failed: " + exception);
            }
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || stage == null || waitDiagnostic == null) return;
        long now = level.getGameTime();
        try {
            switch (stage) {
                case ASSEMBLY -> pollAssembly(now, false);
                case PHYSICS_INITIALIZATION -> pollPhysicsInitialization(now);
                case KINETIC_128 -> pollKinetic128(now);
                case CHILD_REASSEMBLY -> pollChildReassembly(now);
                case THRUST_SETTLE -> pollThrust(now);
                case RELOAD_RECOVERY -> pollReloadRecovery(now);
            }
        } catch (ReflectiveOperationException exception) {
            SkyforgeCompilerIntegrationFailure code = switch (stage) {
                case ASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
                case PHYSICS_INITIALIZATION -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
                case KINETIC_128 -> SkyforgeCompilerIntegrationFailure.FAIL_NETWORK;
                case CHILD_REASSEMBLY -> SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY;
                case THRUST_SETTLE -> SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS;
                case RELOAD_RECOVERY -> SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE;
            };
            failReflection(code, exception);
        } catch (RuntimeException exception) {
            SkyforgeCompilerIntegrationFailure code = stage == Stage.CHILD_REASSEMBLY
                    ? SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY
                    : stage == Stage.RELOAD_RECOVERY
                            ? SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE
                            : stage == Stage.PHYSICS_INITIALIZATION || stage == Stage.THRUST_SETTLE
                                    ? SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS
                                    : SkyforgeCompilerIntegrationFailure.FAIL_NETWORK;
            fail(code, finalDiagnostic(exception.toString()), "aircraft runtime phase failed: " + exception);
        }
    }

    private static void requireCompilerBoundary() {
        var f = compilerFixture;
        if (!f.design().validation().passed()
                || !f.blockspace().validation().passed()
                || !f.assembly().validation().passed()
                || !f.target().validation().passed()
                || !f.propulsion().validation().passed()
                || !f.tail().validation().passed()
                || !f.pilot().validation().passed()
                || !f.manifest().validation().passed()
                || !f.assemblyFixture().validation().passed()
                || !f.glue().validation().passed()
                || !f.powertrain().validation().passed()) {
            throw new IllegalStateException("production v0.12 compiler chain is not statically valid");
        }
        if (!f.powertrain().readiness().governor128RuntimeProbeReady()
                || f.powertrain().readiness().runtimeQualificationReady()
                || f.powertrain().governor().firstAcceptedTargetRpm() != TARGET_RPM
                || f.powertrain().governor().sourceRpmPerPortableEngine() != 32) {
            throw new IllegalStateException("v0.12 runtime boundary does not match the bounded 32->128 RPM contract");
        }
        if (f.manifest().placements().size() != 122
                || f.assemblyFixture().metrics().mainBodyPlacementCount() != 114
                || f.assemblyFixture().metrics().nestedChildPlacementCount() != 9
                || f.glue().metrics().glueDomainCount() != 4
                || f.powertrain().metrics().resultingMovingMainBodyPlacementCount() != 118
                || f.powertrain().metrics().nestedPropellerChildPlacementCount() != 9
                || f.powertrain().metrics().expectedPrimarySableTransferCount() != 127) {
            throw new IllegalStateException("retained v0.12 placement-count authority drifted");
        }
    }

    private static void buildExpectedMaps() {
        mainExpected.clear();
        childExpected.clear();
        movedRoles.clear();
        SkyforgeAircraftProbeManifestIR manifest = compilerFixture.manifest();
        SkyforgeAircraftAssemblyFixtureIR assemblyFixture = compilerFixture.assemblyFixture();
        SkyforgeAircraftPowertrainIR powertrain = compilerFixture.powertrain();

        Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective = new LinkedHashMap<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : manifest.placements()) {
            EffectivePlacement prior = effective.put(placement.point(), new EffectivePlacement(
                    placement.kind(), placement.resourceId(), placement.blockState()));
            if (prior != null) throw new IllegalStateException("duplicate production manifest coordinate " + placement.point());
        }
        Set<AircraftBlockspaceIR.LatticePoint> mainCoordinates = new LinkedHashSet<>(assemblyFixture.mainBody().coordinates());
        Set<AircraftBlockspaceIR.LatticePoint> childCoordinates = new LinkedHashSet<>(assemblyFixture.nestedPropellerChild().coordinates());
        for (SkyforgeAircraftPowertrainIR.Placement placement : powertrain.placements()) {
            EffectivePlacement replacement = new EffectivePlacement("powertrain_main_body", placement.resourceId(), placement.blockState());
            if (placement.mode() == SkyforgeAircraftPowertrainProfile.Mode.REPLACE) {
                if (!effective.containsKey(placement.point())) throw new IllegalStateException("powertrain replacement source missing " + placement.point());
                effective.put(placement.point(), replacement);
            } else {
                if (effective.putIfAbsent(placement.point(), replacement) != null) {
                    throw new IllegalStateException("powertrain addition collision " + placement.point());
                }
                mainCoordinates.add(placement.point());
            }
        }
        for (Map.Entry<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> entry : effective.entrySet()) {
            BlockPos point = blockPos(entry.getKey());
            ExpectedBlock expected = new ExpectedBlock(id(entry.getValue().resourceId()), entry.getValue().blockState());
            if (mainCoordinates.contains(entry.getKey())) {
                mainExpected.put(point, expected);
            } else if (childCoordinates.contains(entry.getKey())) {
                childExpected.put(point, expected);
            } else {
                throw new IllegalStateException("effective compiler placement is unclassified: " + entry.getKey());
            }
        }
        var assembler = assemblyFixture.physicsAssemblerPlacement();
        BlockPos assemblerPoint = blockPos(assembler.point());
        ExpectedBlock assemblerExpected = new ExpectedBlock(id(assembler.resourceId()), assembler.blockState());
        ExpectedBlock collision = mainExpected.putIfAbsent(assemblerPoint, assemblerExpected);
        if (collision != null && !collision.equals(assemblerExpected)) {
            throw new IllegalStateException("Physics Assembler collides with compiled main body");
        }
        if (mainExpected.size() != powertrain.metrics().resultingMovingMainBodyPlacementCount()
                || childExpected.size() != powertrain.metrics().nestedPropellerChildPlacementCount()
                || mainExpected.size() + childExpected.size() != powertrain.metrics().expectedPrimarySableTransferCount()) {
            throw new IllegalStateException("runtime effective-map counts disagree with v0.12 metrics");
        }
    }

    private static void requireRuntimePreconditions() {
        long now = level.getGameTime();
        try {
            if (!ModList.get().isLoaded("create")) throw new IllegalStateException("required exact-stack Create mod not loaded");
            for (ExpectedBlock expected : mainExpected.values()) requireBlock(expected.id());
            for (ExpectedBlock expected : childExpected.values()) requireBlock(expected.id());
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity");
            Class.forName("dev.eriksonn.aeronautics.content.blocks.propeller.bearing.contraption.PropellerBearingContraptionEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        } catch (ClassNotFoundException | IllegalStateException exception) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "accepted C11 Sable/Create/SuperGlue/NestedBearing seams resolve",
                            now, now, "targetStack=C11_FLIGHT_EXACT_2026-09-05", safeServerState(), exception.toString()),
                    "required exact-stack aircraft runtime resource unavailable: " + exception);
        }
    }

    private static void prepareFixture() {
        Set<BlockPos> all = new LinkedHashSet<>();
        mainExpected.keySet().forEach(p -> all.add(source(p)));
        childExpected.keySet().forEach(p -> all.add(source(p)));
        int minX = all.stream().mapToInt(BlockPos::getX).min().orElseThrow() - 2;
        int maxX = all.stream().mapToInt(BlockPos::getX).max().orElseThrow() + 2;
        int minY = all.stream().mapToInt(BlockPos::getY).min().orElseThrow() - 2;
        int maxY = all.stream().mapToInt(BlockPos::getY).max().orElseThrow() + 2;
        int minZ = all.stream().mapToInt(BlockPos::getZ).min().orElseThrow() - 2;
        int maxZ = all.stream().mapToInt(BlockPos::getZ).max().orElseThrow() + 2;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) level.getChunk(chunkX, chunkZ);
        }
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : mainExpected.entrySet()) {
            if (!level.setBlock(source(entry.getKey()), materialize(entry.getValue()), 3)) {
                throw new IllegalStateException("failed to place compiled main-body block " + entry.getKey());
            }
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) {
            if (!level.setBlock(source(entry.getKey()), materialize(entry.getValue()), 3)) {
                throw new IllegalStateException("failed to place compiled propeller child block " + entry.getKey());
            }
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : mainExpected.entrySet()) assertExpectedState("source main", source(entry.getKey()), entry.getValue());
        for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) assertExpectedState("source child", source(entry.getKey()), entry.getValue());
    }

    private static void addCompilerGlue() throws ReflectiveOperationException {
        glueIds.clear();
        List<Entity> insertedGlue = new ArrayList<>();
        for (SkyforgeAircraftGlueEncodingIR.GlueDomain domain : compilerFixture.glue().glueDomains()) {
            insertedGlue.add(addGlue(source(domain.from()), source(domain.to()), domain.name()));
        }
        SkyforgeAircraftPowertrainIR.GlueDomain powerplant = compilerFixture.powertrain().powerplantGlueDomain();
        insertedGlue.add(addGlue(source(powerplant.from()), source(powerplant.to()), powerplant.name()));
        insertedGlue.forEach(glue -> glueIds.add(glue.getUUID()));
        if (insertedGlue.size() != 5) throw new IllegalStateException("v0.12 requires four accepted airframe glue domains plus one powerplant domain");
        BlockPos bearing = source(findManifestKind("propeller_bearing"));
        BlockPos hub = source(findManifestKind("propeller_hub"));
        for (Entity glue : insertedGlue) {
            boolean containsBearing = Boolean.TRUE.equals(publicMethod(glue, "contains", BlockPos.class).invoke(glue, bearing));
            boolean containsHub = Boolean.TRUE.equals(publicMethod(glue, "contains", BlockPos.class).invoke(glue, hub));
            if (containsBearing && containsHub) throw new IllegalStateException("compiler glue crosses Propeller Bearing controller boundary");
            for (BlockPos child : childExpected.keySet()) {
                if (Boolean.TRUE.equals(publicMethod(glue, "contains", BlockPos.class).invoke(glue, source(child)))) {
                    throw new IllegalStateException("compiler main-body glue unexpectedly contains propeller child cell " + child);
                }
            }
        }
    }

    private static Entity addGlue(BlockPos from, BlockPos to, String label) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) throw new IllegalStateException("failed to register compiler glue domain " + label);
        return glue;
    }

    private static void pollAssembly(long now, boolean synchronousObservation) throws ReflectiveOperationException {
        Set<UUID> created = new LinkedHashSet<>(currentSubLevelIds());
        created.removeAll(beforeIds);
        if (created.size() > 1) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "aircraft fixture created multiple Sable bodies");
        }
        if (created.size() == 1) {
            bodyId = created.iterator().next();
            Object body = findListedBody(bodyId);
            if (body == null) throw new IllegalStateException("created Sable UUID cannot be reselected");
            assembledBody = body;
            Object massTracker = publicMethod(body, "getMassTracker").invoke(body);
            double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            boolean removed = Boolean.TRUE.equals(publicMethod(body, "isRemoved").invoke(body));
            int sourceNonAir = countSourceNonAir();
            if (sourceNonAir != 0 || removed || !(mass > 0.0) || centerOfMass == null) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless",
                                "sourceNonAir=" + sourceNonAir + " mass=" + mass + " removed=" + removed),
                        "v0.12 primary assembly did not transfer the exact compiled payload cleanly");
            }
            movedOffset = movedOffset(body, centerOfMass);
            movedBearing = moved(findManifestKind("propeller_bearing"));
            movedChild = childExpected.keySet().stream().map(SkyforgeAircraftPowertrainRuntimeAcceptance::moved).toList();
            for (SkyforgeAircraftPowertrainIR.Placement placement : compilerFixture.powertrain().placements()) {
                movedRoles.put(placement.role(), moved(blockPos(placement.point())));
            }
            for (Map.Entry<BlockPos, ExpectedBlock> entry : mainExpected.entrySet()) assertExpectedState("moved main", moved(entry.getKey()), entry.getValue());
            for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) assertExpectedState("moved child payload", moved(entry.getKey()), entry.getValue());
            addFixtureForceLoadTicket(body);
            stage = Stage.PHYSICS_INITIALIZATION;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                    "canonical UUID re-resolves to a live Sable body with current physics and exact moved aircraft blocks",
                    now, now + PHYSICS_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "assemblyRegistrationObservedSynchronously=" + synchronousObservation + " mass=" + mass + " movedOffset=" + movedOffset);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_ASSEMBLY_REGISTRATION,
                    waitDiagnostic.withFinalState(sourceIds(), safeServerState(), "headless", "createdIds=" + created),
                    "v0.12 Sable primary-body registration deadline expired");
        }
    }

    private static void pollPhysicsInitialization(long now) throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "canonicalBody=null"),
                        "canonical aircraft body did not remain available");
            }
            return;
        }
        if (physicsSystem == null) physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PHYSICS_INITIALIZATION,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "physicsHandle=null"),
                        "current aircraft physics handle did not become valid");
            }
            return;
        }
        physicsWasPaused = (Boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        if (physicsWasPaused) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, false);
            physicsPauseChanged = true;
        }
        for (BlockPos pos : movedRoles.values()) requireCanonicalPlotOwnership(canonical, pos);
        requireCanonicalPlotOwnership(canonical, movedBearing);
        for (BlockPos pos : movedChild) requireCanonicalPlotOwnership(canonical, pos);
        requireExpectedMovedBlockEntity(canonical, movedRoles.get("engine_port"), "PortableEngineBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedRoles.get("engine_starboard"), "PortableEngineBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedRoles.get("governor"), "SpeedControllerBlockEntity", now);
        requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        stage = Stage.KINETIC_128;
        waitDiagnostic = diagnostic(
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "both moved 32-RPM Portable Engines share one network and the moved governor delivers 128 RPM to the bearing",
                now, now + KINETIC_DEADLINE_TICKS, movedIds(), safeServerState(),
                "currentPhysicsHandleValid=true platformSeams=SABLE_PRIMARY+SUPER_GLUE+CREATE_KINETIC_ON_SABLE+NESTED_BEARING");
    }

    private static void pollKinetic128(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object enginePort = requireExpectedMovedBlockEntity(canonical, movedRoles.get("engine_port"), "PortableEngineBlockEntity", now);
        Object engineStarboard = requireExpectedMovedBlockEntity(canonical, movedRoles.get("engine_starboard"), "PortableEngineBlockEntity", now);
        Object governor = requireExpectedMovedBlockEntity(canonical, movedRoles.get("governor"), "SpeedControllerBlockEntity", now);
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        if (enginePort == null || engineStarboard == null || governor == null || bearing == null) return;

        if (!powertrainActivated) {
            publicMethod(enginePort, "setCurrentBurnTime", int.class).invoke(enginePort, ENGINE_BURN_TICKS);
            publicMethod(engineStarboard, "setCurrentBurnTime", int.class).invoke(engineStarboard, ENGINE_BURN_TICKS);
            Object targetSpeed = publicField(governor, "targetSpeed").get(governor);
            publicMethod(targetSpeed, "setValue", int.class).invoke(targetSpeed, TARGET_RPM);
            int realizedTarget = ((Number) publicMethod(targetSpeed, "getValue").invoke(targetSpeed)).intValue();
            if (realizedTarget != TARGET_RPM) throw new IllegalStateException("Rotation Speed Controller rejected 128-RPM target: " + realizedTarget);
            powertrainActivated = true;
        }

        float portRpm = ((Number) publicMethod(enginePort, "getGeneratedSpeed").invoke(enginePort)).floatValue();
        float starboardRpm = ((Number) publicMethod(engineStarboard, "getGeneratedSpeed").invoke(engineStarboard)).floatValue();
        Object portNetwork = publicField(enginePort, "network").get(enginePort);
        Object starboardNetwork = publicField(engineStarboard, "network").get(engineStarboard);
        KineticState bearingState = kineticState(bearing);
        boolean ready = absNear(32.0f, portRpm, 0.001f)
                && absNear(32.0f, starboardRpm, 0.001f)
                && portNetwork != null
                && portNetwork.equals(starboardNetwork)
                && bearingState.hasNetwork()
                && bearingState.hasSource()
                && absNear(TARGET_RPM, bearingState.speed(), RPM_TOLERANCE)
                && absNear(TARGET_RPM, bearingState.theoreticalSpeed(), RPM_TOLERANCE);
        if (ready) {
            Object bearingNetwork = publicMethod(bearing, "getOrCreateNetwork").invoke(bearing);
            float capacity = ((Number) publicMethod(bearingNetwork, "calculateCapacity").invoke(bearingNetwork)).floatValue();
            float stress = ((Number) publicMethod(bearingNetwork, "calculateStress").invoke(bearingNetwork)).floatValue();
            if (!Float.isFinite(capacity) || !Float.isFinite(stress) || capacity <= 0.0f || stress < 0.0f || capacity - stress <= STRESS_TOLERANCE) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                        finalDiagnostic("capacity=" + capacity + " stress=" + stress + " bearing=" + bearingState),
                        "128-RPM kinetic network lacks finite positive stress margin");
            }
            observedBearingRpm = bearingState.speed();
            observedCapacity = capacity;
            observedStress = stress;
            observedStressMargin = capacity - stress;
            if (runMode == RunMode.VERIFY) {
                ChildState recoveredChild = childState(bearing);
                if (!recoveredChild.present()) {
                    for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) {
                        assertExpectedState("reloaded normalized child", moved(entry.getKey()), entry.getValue());
                    }
                    publicMethod(bearing, "assemble").invoke(bearing);
                    reloadChildAssemblyRequested = true;
                }
            }
            stage = Stage.CHILD_REASSEMBLY;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                    "moved WEST-facing Propeller Bearing re-forms the compiler-emitted nine-block child with sail power 8",
                    now, now + CHILD_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "portRpm=" + portRpm + " starboardRpm=" + starboardRpm + " bearing=" + bearingState
                            + " capacity=" + capacity + " stress=" + stress);
            pollChildReassembly(now);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "portRpm=" + portRpm + " starboardRpm=" + starboardRpm + " bearing=" + bearingState
                                    + " sharedNetwork=" + (portNetwork != null && portNetwork.equals(starboardNetwork))),
                    "v0.12 moved powertrain did not converge to the 128-RPM gate");
        }
    }

    private static void pollChildReassembly(long now) throws ReflectiveOperationException {
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        if (bearing == null) return;
        ChildState child = childState(bearing);
        if (child.present()) {
            requireExactChild(child, now);
            nestedChildId = child.id();
            if (runMode == RunMode.VERIFY && preReloadNestedChildId != null) {
                Entity stale = findEntity(preReloadNestedChildId);
                if (!child.id().equals(preReloadNestedChildId) && stale != null && stale.isAlive()) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                            finalDiagnostic("preReloadChild=" + preReloadNestedChildId + " currentChild=" + child.id()),
                            "reload retained a stale duplicate Propeller Bearing child");
                }
                if (child.id().equals(preReloadNestedChildId)) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                            finalDiagnostic("preReloadChild=" + preReloadNestedChildId + " currentChild=" + child.id()),
                            "normalized production child reused the pre-reload entity UUID instead of fresh reassembly");
                }
                childRecoveryMode = "reassembled-from-normalized-plot";
            }
            for (BlockPos pos : movedChild) {
                if (!level.getBlockState(pos).isAir()) {
                    fail(SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY,
                            finalDiagnostic("child=" + child + " retainedPayload=" + pos),
                            "nested child assembly left compiler payload in primary Sable lattice");
                }
            }
            thrustSettleNotBefore = now + THRUST_SETTLE_TICKS;
            stage = Stage.THRUST_SETTLE;
            waitDiagnostic = diagnostic(
                    SkyforgeCompilerIntegrationPhase.PHYSICS_PROGRESSION,
                    "128-RPM nested propeller reaches finite nonzero thrust with WEST-facing local -X point-force sign",
                    now, now + THRUST_DEADLINE_TICKS, movedIds(), safeServerState(),
                    "nestedChild=" + child + " settleNotBefore=" + thrustSettleNotBefore);
            return;
        }
        if (waitDiagnostic.expired(now)) {
            Object lastException = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
            fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_CHILD_ASSEMBLY,
                    waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless",
                            "bearing=" + kineticState(bearing) + " lastAssemblyException=" + lastException),
                    "compiler-emitted propeller child did not re-form without hidden child glue");
        }
    }

    private static void pollThrust(long now) throws ReflectiveOperationException {
        if (now < thrustSettleNotBefore) return;
        Object canonical = requireCanonicalBody();
        Object bearing = requireExpectedMovedBlockEntity(canonical, movedBearing, "PropellerBearingBlockEntity", now);
        if (bearing == null) return;
        ChildState child = childState(bearing);
        requireExactChild(child, now);
        if (!nestedChildId.equals(child.id())) throw new IllegalStateException("nested propeller child identity changed during thrust settle");
        KineticState state = kineticState(bearing);
        if (!absNear(TARGET_RPM, state.speed(), RPM_TOLERANCE) || !state.hasNetwork()) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_NETWORK, finalDiagnostic("bearing=" + state),
                        "bearing lost the 128-RPM network before thrust qualification");
            }
            return;
        }
        Direction bearingDirection = (Direction) publicMethod(bearing, "getBlockDirection").invoke(bearing);
        if (bearingDirection != Direction.WEST) throw new IllegalStateException("compiled Propeller Bearing is not WEST-facing: " + bearingDirection);
        double directionIndependentSpeed = number(publicMethod(bearing, "getDirectionIndependentSpeed").invoke(bearing));
        double rawThrust = number(publicMethod(bearing, "getThrust").invoke(bearing));
        double scaledThrust = number(publicMethod(bearing, "getScaledThrust").invoke(bearing));
        double airflowScaling = number(publicMethod(bearing, "getAirflowScaling").invoke(bearing));
        double airPressure = number(publicMethod(bearing, "getCurrentAirPressure").invoke(bearing));
        double appliedForceX = bearingDirection.getStepX() * scaledThrust;
        if (!Double.isFinite(directionIndependentSpeed) || Math.abs(directionIndependentSpeed) <= 0.01
                || !Double.isFinite(rawThrust) || Math.abs(rawThrust) <= 1.0e-9 || rawThrust >= 0.0
                || !Double.isFinite(scaledThrust) || scaledThrust <= 0.0
                || !Double.isFinite(airflowScaling) || airflowScaling < 0.0
                || !Double.isFinite(airPressure) || airPressure <= 0.0
                || !Double.isFinite(appliedForceX) || appliedForceX >= 0.0) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    finalDiagnostic("bearing=" + state + " rawThrust=" + rawThrust + " scaledThrust=" + scaledThrust
                            + " airflowScaling=" + airflowScaling + " airPressure=" + airPressure + " forceX=" + appliedForceX),
                    "128-RPM propeller thrust sign/magnitude gate failed");
        }
        if (runMode == RunMode.PREPARE) {
            normalizeAndSaveAircraft(bearing, child, rawThrust, scaledThrust, appliedForceX);
            return;
        }
        if (runMode == RunMode.VERIFY) {
            completePersistenceVerification(child, rawThrust, scaledThrust, appliedForceX);
            return;
        }
        restorePhysicsPause();
        removeFixtureForceLoadTicket();
        complete = true;
        LOGGER.log(System.Logger.Level.INFO,
                activePrefix + " PASS capability=" + activeCapability
                        + " bodyId=" + bodyId + " movedOffset=" + movedOffset + " nestedChildId=" + nestedChildId
                        + " mainBodyBlocks=" + mainExpected.size() + " nestedPayloadBlocks=" + childExpected.size()
                        + " glueDomains=" + glueIds.size() + " enginePortRpm=32 engineStarboardRpm=32"
                        + " governorTargetRpm=128 bearingRpm=" + observedBearingRpm
                        + " kineticCapacity=" + observedCapacity + " kineticStress=" + observedStress
                        + " kineticStressMargin=" + observedStressMargin
                        + " childBlocks=9 sailBlocks=8 sailPower=8"
                        + " rawThrust=" + rawThrust + " scaledThrust=" + scaledThrust + " appliedForceX=" + appliedForceX
                        + " analyticalAuthorityIndependent=true higherGovernorPointsQualified=false"
                        + " persistenceQualified=false controlAxisQualified=false flightQualified=false"
                        + " consumedPlatformAuthorities=SABLE_PRIMARY_ASSEMBLY_LIFECYCLE,SUPER_GLUE_ASSEMBLY_DOMAIN_LIFECYCLE,CREATE_KINETIC_ON_SABLE_LIFECYCLE,NESTED_PROPELLER_BEARING_LIFECYCLE"
                        + " canonicalBodyResolutionPerPhase=true blockEntityResolutionPerPoll=true"
                        + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
    }

    private static void normalizeAndSaveAircraft(
            Object bearing, ChildState child, double rawThrust, double scaledThrust, double appliedForceX)
            throws ReflectiveOperationException {
        preReloadNestedChildId = child.id();
        preReloadGlueIds = requireAllMovedCompilerGlueDomains(level.getGameTime(), false);
        publicMethod(bearing, "disassemble").invoke(bearing);
        ChildState normalized = childState(bearing);
        if (normalized.present() || Boolean.TRUE.equals(publicMethod(bearing, "isRunning").invoke(bearing))) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("nestedBefore=" + child + " nestedAfterNormalize=" + normalized),
                    "production propeller child did not normalize into the Sable plot before save");
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) {
            assertExpectedState("normalized child", moved(entry.getKey()), entry.getValue());
        }
        Entity stale = findEntity(preReloadNestedChildId);
        if (stale != null && stale.isAlive()) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("preReloadNestedChildId=" + preReloadNestedChildId),
                    "normalized production save boundary retained the nested child entity");
        }
        restorePhysicsPause();
        removeFixtureForceLoadTicket();
        if (!level.getServer().saveEverything(false, true, true)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("saveEverything=false"),
                    "server did not report a successful aircraft persistence prepare save");
        }
        writeIdentityFile(new PersistenceIdentity(bodyId, preReloadNestedChildId, movedOffset,
                compilerFixture.manifest().sha256(), compilerFixture.powertrain().sha256(), preReloadGlueIds));
        complete = true;
        LOGGER.log(System.Logger.Level.INFO,
                activePrefix + " PREPARE PASS capability=" + activeCapability
                        + " bodyId=" + bodyId + " movedOffset=" + movedOffset
                        + " manifestSha256=" + compilerFixture.manifest().sha256()
                        + " powertrainSha256=" + compilerFixture.powertrain().sha256()
                        + " preReloadNestedChildId=" + preReloadNestedChildId
                        + " preReloadGlueIds=" + preReloadGlueIds
                        + " bearingRpm=" + observedBearingRpm
                        + " kineticCapacity=" + observedCapacity + " kineticStress=" + observedStress
                        + " kineticStressMargin=" + observedStressMargin
                        + " rawThrust=" + rawThrust + " scaledThrust=" + scaledThrust
                        + " appliedForceX=" + appliedForceX
                        + " normalizedChildBlocksInPlot=true saveSuccess=true"
                        + " lifecycleAuthority=SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE"
                        + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
    }

    private static void beginReloadVerification(long now) throws ReflectiveOperationException {
        stage = Stage.RELOAD_RECOVERY;
        PersistenceIdentity identity = readIdentityFile();
        if (!identity.manifestSha256().equals(compilerFixture.manifest().sha256())
                || !identity.powertrainSha256().equals(compilerFixture.powertrain().sha256())) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    diagnostic(SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                            "fresh process recompiles exactly the persisted production manifest/powertrain identity",
                            now, now, "persistedBodyId=" + identity.bodyId(), safeServerState(),
                            "persistedManifest=" + identity.manifestSha256()
                                    + " currentManifest=" + compilerFixture.manifest().sha256()
                                    + " persistedPowertrain=" + identity.powertrainSha256()
                                    + " currentPowertrain=" + compilerFixture.powertrain().sha256()),
                    "production compiler identity changed across save/reload boundary");
        }
        bodyId = identity.bodyId();
        preReloadNestedChildId = identity.nestedChildId();
        preReloadGlueIds = identity.glueIds();
        movedOffset = identity.movedOffset();
        initializeMovedCoordinatesFromOffset();
        level.getChunk(0, 0);
        requestHoldingLoadIfAvailable();
        stage = Stage.RELOAD_RECOVERY;
        waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.PERSISTENCE_RELOAD,
                "fresh server process re-resolves persisted production Sable UUID, exact blocks, glue topology, and current physics",
                now, now + RELOAD_DEADLINE_TICKS, movedIds(), safeServerState(),
                "manifest=" + identity.manifestSha256() + " powertrain=" + identity.powertrainSha256()
                        + " preReloadNestedChildId=" + preReloadNestedChildId + " preReloadGlueIds=" + preReloadGlueIds);
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
                            waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "canonicalBody=null"),
                            "persisted aircraft Sable UUID did not reload from holding storage");
                }
                return;
            }
        }
        assembledBody = canonical;
        if (reloadCanonicalResolvedTick < 0L) reloadCanonicalResolvedTick = now;
        if (!forceLoadTicketAdded) addFixtureForceLoadTicket(canonical);
        if (physicsSystem == null) physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Object handle = findCurrentPhysicsHandle(canonical);
        if (handle == null) {
            if (waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.TIMEOUT_PERSISTENCE_RELOAD,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "physicsHandle=null"),
                        "reloaded aircraft body did not acquire a current physics handle");
            }
            return;
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : mainExpected.entrySet()) {
            requireCanonicalPlotOwnership(canonical, moved(entry.getKey()));
            assertReloadedMainState(moved(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) {
            requireCanonicalPlotOwnership(canonical, moved(entry.getKey()));
            assertExpectedState("reloaded normalized child", moved(entry.getKey()), entry.getValue());
        }
        Entity stale = findEntity(preReloadNestedChildId);
        if (stale != null && stale.isAlive()) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                    finalDiagnostic("preReloadNestedChildId=" + preReloadNestedChildId),
                    "fresh-process reload resurrected the normalized pre-save propeller child entity");
        }
        if (!recoverAllMovedCompilerGlueDomains(now)) return;
        powertrainActivated = false;
        stage = Stage.KINETIC_128;
        waitDiagnostic = diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                "supported lifecycle reactivation restores the accepted dual-engine 128-RPM production operating point",
                now, now + KINETIC_DEADLINE_TICKS, movedIds(), safeServerState(),
                "samePersistentUuid=true currentPhysicsHandleValid=true glueRecoveryMode=" + glueRecoveryMode
                        + " lifecycleReactivation=portable_engine_burn_plus_governor_target");
        pollKinetic128(now);
    }

    private static void initializeMovedCoordinatesFromOffset() {
        movedBearing = moved(findManifestKind("propeller_bearing"));
        movedChild = childExpected.keySet().stream().map(SkyforgeAircraftPowertrainRuntimeAcceptance::moved).toList();
        movedRoles.clear();
        for (SkyforgeAircraftPowertrainIR.Placement placement : compilerFixture.powertrain().placements()) {
            movedRoles.put(placement.role(), moved(blockPos(placement.point())));
        }
    }

    private static boolean requestHoldingLoadIfAvailable() throws ReflectiveOperationException {
        Object holdingMap = publicMethod(container, "getHoldingChunkMap").invoke(container);
        Object holding = publicMethod(holdingMap, "getHoldingSubLevel", UUID.class).invoke(holdingMap, bodyId);
        if (holding == null) return false;
        Object pointer = publicMethod(holding, "pointer").invoke(holding);
        if (pointer == null) return false;
        Method snatch = holdingMap.getClass().getMethod("snatchAndLoad", pointer.getClass(), UUID.class);
        snatch.invoke(holdingMap, pointer, bodyId);
        return true;
    }

    private static List<GlueDomainSpec> compilerGlueDomains() {
        List<GlueDomainSpec> domains = new ArrayList<>();
        for (SkyforgeAircraftGlueEncodingIR.GlueDomain domain : compilerFixture.glue().glueDomains()) {
            domains.add(new GlueDomainSpec(domain.name(), blockPos(domain.from()), blockPos(domain.to())));
        }
        SkyforgeAircraftPowertrainIR.GlueDomain powerplant = compilerFixture.powertrain().powerplantGlueDomain();
        domains.add(new GlueDomainSpec(powerplant.name(), blockPos(powerplant.from()), blockPos(powerplant.to())));
        return List.copyOf(domains);
    }

    private static List<UUID> requireAllMovedCompilerGlueDomains(long now, boolean persistencePhase)
            throws ReflectiveOperationException {
        List<UUID> ids = new ArrayList<>();
        for (GlueDomainSpec domain : compilerGlueDomains()) {
            List<Entity> matches = matchingGlueEntities(movedGlueBox(domain));
            if (matches.size() != 1) {
                fail(persistencePhase ? SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE
                                : SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                        finalDiagnostic("glueDomain=" + domain.name() + " matches=" + matches.size()),
                        "compiler glue domain missing or duplicated: " + domain.name());
            }
            ids.add(matches.getFirst().getUUID());
        }
        return List.copyOf(ids);
    }

    private static boolean recoverAllMovedCompilerGlueDomains(long now) throws ReflectiveOperationException {
        List<UUID> ids = new ArrayList<>();
        int persisted = 0;
        int recreated = 0;
        List<GlueDomainSpec> missing = new ArrayList<>();
        for (GlueDomainSpec domain : compilerGlueDomains()) {
            List<Entity> matches = matchingGlueEntities(movedGlueBox(domain));
            if (matches.size() > 1) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_PERSISTENCE,
                        finalDiagnostic("glueDomain=" + domain.name() + " matches=" + matches.size()),
                        "reload rehydrated duplicate compiler glue authority");
            }
            if (matches.isEmpty()) missing.add(domain);
            else { persisted++; ids.add(matches.getFirst().getUUID()); }
        }
        if (!missing.isEmpty() && now < reloadCanonicalResolvedTick + ENTITY_REHYDRATION_GRACE_TICKS) return false;
        for (GlueDomainSpec domain : missing) {
            AABB expected = movedGlueBox(domain);
            Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
            Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
            Entity glue = (Entity) constructor.newInstance(level, expected);
            if (!level.addFreshEntity(glue)) throw new IllegalStateException("failed to re-realize glue domain " + domain.name());
            List<Entity> matches = matchingGlueEntities(expected);
            if (matches.size() != 1) throw new IllegalStateException("re-realized glue domain not unique " + domain.name());
            recreated++; ids.add(matches.getFirst().getUUID());
        }
        postReloadGlueIds = List.copyOf(ids);
        glueRecoveryMode = recreated == 0 ? "persisted-all" : persisted == 0 ? "recreated-all" : "mixed-persisted-reactivated";
        if (postReloadGlueIds.size() != 5) throw new IllegalStateException("expected five recovered compiler glue domains");
        return true;
    }

    private static AABB movedGlueBox(GlueDomainSpec domain) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB sourceBox = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class)
                .invoke(null, source(domain.from()), source(domain.to()));
        return sourceBox.move(movedOffset.getX(), movedOffset.getY(), movedOffset.getZ());
    }

    private static List<Entity> matchingGlueEntities(AABB expected) {
        List<Entity> matches = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, expected.inflate(1.0))) {
            if ("com.simibubi.create.content.contraptions.glue.SuperGlueEntity".equals(entity.getClass().getName())
                    && sameBox(entity.getBoundingBox(), expected)) matches.add(entity);
        }
        return matches;
    }

    private static boolean sameBox(AABB a, AABB b) {
        double e = 1.0e-6;
        return Math.abs(a.minX-b.minX)<=e && Math.abs(a.minY-b.minY)<=e && Math.abs(a.minZ-b.minZ)<=e
                && Math.abs(a.maxX-b.maxX)<=e && Math.abs(a.maxY-b.maxY)<=e && Math.abs(a.maxZ-b.maxZ)<=e;
    }

    private static void completePersistenceVerification(
            ChildState child, double rawThrust, double scaledThrust, double appliedForceX)
            throws ReflectiveOperationException {
        restorePhysicsPause();
        removeFixtureForceLoadTicket();
        complete = true;
        LOGGER.log(System.Logger.Level.INFO,
                activePrefix + " VERIFY PASS capability=" + activeCapability
                        + " bodyId=" + bodyId + " movedOffset=" + movedOffset
                        + " manifestSha256=" + compilerFixture.manifest().sha256()
                        + " powertrainSha256=" + compilerFixture.powertrain().sha256()
                        + " preReloadNestedChildId=" + preReloadNestedChildId
                        + " postReloadNestedChildId=" + child.id() + " childRecoveryMode=" + childRecoveryMode
                        + " preReloadGlueIds=" + preReloadGlueIds + " postReloadGlueIds=" + postReloadGlueIds
                        + " glueRecoveryMode=" + glueRecoveryMode + " glueDuplicate=false"
                        + " samePersistentUuid=true currentPhysicsHandleValid=true staleChildDuplicate=false"
                        + " lifecycleReactivation=portable_engine_burn_plus_governor_target"
                        + " enginePortRpm=32 engineStarboardRpm=32 governorTargetRpm=128 bearingRpm=" + observedBearingRpm
                        + " kineticCapacity=" + observedCapacity + " kineticStress=" + observedStress
                        + " kineticStressMargin=" + observedStressMargin
                        + " childBlocks=9 sailBlocks=8 sailPower=8"
                        + " rawThrust=" + rawThrust + " scaledThrust=" + scaledThrust + " appliedForceX=" + appliedForceX
                        + " persistenceQualified=true controlAxisQualified=false higherGovernorPointsQualified=false flightQualified=false"
                        + " consumedPlatformAuthority=SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE"
                        + " fixtureLivenessTicket=sable:command_forced(released) clientState=headless");
    }

    private static void writeIdentityFile(PersistenceIdentity identity) {
        String glue = identity.glueIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        String value = identity.bodyId() + "\n" + identity.nestedChildId() + "\n"
                + identity.movedOffset().getX() + "," + identity.movedOffset().getY() + "," + identity.movedOffset().getZ() + "\n"
                + identity.manifestSha256() + "\n" + identity.powertrainSha256() + "\n" + glue + "\n";
        try { Files.writeString(IDENTITY_FILE, value); }
        catch (IOException exception) { throw new IllegalStateException("failed to persist aircraft runtime identity sidecar", exception); }
    }

    private static PersistenceIdentity readIdentityFile() {
        try {
            List<String> lines = Files.readAllLines(IDENTITY_FILE);
            if (lines.size() != 6) throw new IllegalStateException("aircraft identity sidecar expected 6 lines, got " + lines.size());
            String[] offset = lines.get(2).split(",", -1);
            List<UUID> glueIds = java.util.Arrays.stream(lines.get(5).split(",")).map(String::trim).map(UUID::fromString).toList();
            return new PersistenceIdentity(UUID.fromString(lines.get(0).trim()), UUID.fromString(lines.get(1).trim()),
                    new BlockPos(Integer.parseInt(offset[0]), Integer.parseInt(offset[1]), Integer.parseInt(offset[2])),
                    lines.get(3).trim(), lines.get(4).trim(), glueIds);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("failed to read aircraft runtime identity sidecar", exception);
        }
    }

    private static ChildState childState(Object bearing) throws ReflectiveOperationException {
        Object moved = publicMethod(bearing, "getMovedContraption").invoke(bearing);
        boolean running = Boolean.TRUE.equals(publicMethod(bearing, "isRunning").invoke(bearing));
        if (!(moved instanceof Entity child) || !child.isAlive()) return ChildState.MISSING;
        Object contraption = publicMethod(child, "getContraption").invoke(child);
        Object rawBlocks = publicMethod(contraption, "getBlocks").invoke(contraption);
        if (!(rawBlocks instanceof Map<?, ?> blocks)) throw new IllegalStateException("propeller child block map unavailable");
        int hubs = 0;
        int sails = 0;
        for (Object value : blocks.values()) {
            if (!(value instanceof StructureTemplate.StructureBlockInfo info)) throw new IllegalStateException("unexpected child block-info type " + value);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
            if (id("minecraft:spruce_planks").equals(blockId)) hubs++;
            if (id("simulated:white_symmetric_sail").equals(blockId)) sails++;
        }
        int sailPower = ((Number) publicMethod(contraption, "getSailBlocks").invoke(contraption)).intValue();
        return new ChildState(child.getUUID(), true, running, blocks.size(), hubs, sails, sailPower, child.getClass().getName());
    }

    private static void requireExactChild(ChildState child, long now) throws ReflectiveOperationException {
        if (!child.present() || !child.running() || child.blockCount() != 9 || child.hubCount() != 1
                || child.sailCount() != 8 || child.sailPower() != 8) {
            Object bearing = level.getBlockEntity(movedBearing);
            Object last = bearing == null ? "bearing=null" : publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
            fail(SkyforgeCompilerIntegrationFailure.FAIL_CHILD_ASSEMBLY,
                    diagnostic(SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                            "compiler propeller child contains one hub plus eight symmetric sails with sail power 8",
                            now, now, "nestedChild=" + child.id(), safeServerState(), "child=" + child + " lastAssemblyException=" + last),
                    "nested Propeller Bearing child membership invalid");
        }
        Object bearing = level.getBlockEntity(movedBearing);
        Field totalSailPower = bearing.getClass().getField("totalSailPower");
        float bearingSailPower = totalSailPower.getFloat(bearing);
        if (Math.abs(bearingSailPower - 8.0f) > 0.0001f) {
            throw new IllegalStateException("bearing realized sail power mismatch: " + bearingSailPower);
        }
    }

    private static BlockPos findManifestKind(String kind) {
        List<SkyforgeAircraftProbeManifestIR.Placement> matches = compilerFixture.manifest().placements().stream()
                .filter(placement -> kind.equals(placement.kind())).toList();
        if (matches.size() != 1) throw new IllegalStateException("expected exactly one manifest placement kind " + kind + ", got " + matches.size());
        return blockPos(matches.getFirst().point());
    }

    private static BlockEntity requireExpectedBlockEntity(BlockPos pos, String suffix) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().endsWith(suffix)) {
            throw new IllegalStateException("required block entity missing/wrong at " + pos + ": "
                    + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
        }
        return blockEntity;
    }

    private static Object requireExpectedMovedBlockEntity(Object canonical, BlockPos pos, String suffix, long now)
            throws ReflectiveOperationException {
        requireCanonicalPlotOwnership(canonical, pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            if (waitDiagnostic != null && waitDiagnostic.expired(now)) {
                fail(SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                        waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", "missingBlockEntityAt=" + pos),
                        "required moved aircraft block entity missing after deadline");
            }
            return null;
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("unexpectedBlockEntity=" + blockEntity.getClass().getName() + " at=" + pos),
                    "unexpected moved aircraft block entity type");
        }
        return blockEntity;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        if (blockEntity == null) return KineticState.MISSING;
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kineticClass.isInstance(blockEntity)) throw new IllegalStateException("not a Create KineticBlockEntity: " + blockEntity.getClass().getName());
        float speed = ((Number) kineticClass.getDeclaredMethod("getSpeed").invoke(blockEntity)).floatValue();
        float theoretical = ((Number) kineticClass.getDeclaredMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue();
        boolean hasSource = (Boolean) kineticClass.getDeclaredMethod("hasSource").invoke(blockEntity);
        boolean hasNetwork = (Boolean) kineticClass.getDeclaredMethod("hasNetwork").invoke(blockEntity);
        return new KineticState(speed, theoretical, hasSource, hasNetwork, blockEntity.getClass().getName());
    }

    private static Object requireServerSubLevelContainer(ServerLevel serverLevel) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(serverLevel)) throw new IllegalStateException("ServerLevel does not expose Sable SubLevelContainerHolder");
        Object value = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(serverLevel);
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (value == null || !containerClass.isInstance(value)) throw new IllegalStateException("Sable ServerSubLevelContainer unavailable");
        return value;
    }

    private static Set<UUID> currentSubLevelIds() throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object subLevel : subLevels) ids.add(subLevelUniqueId(subLevel));
        return ids;
    }

    private static UUID subLevelUniqueId(Object subLevel) throws ReflectiveOperationException {
        Object value = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
        if (!(value instanceof UUID uuid)) throw new IllegalStateException("Sable sub-level unique ID is not UUID: " + value);
        return uuid;
    }

    private static Object findListedBody(UUID uuid) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) throw new IllegalStateException("Sable getAllSubLevels did not return a List");
        for (Object subLevel : subLevels) if (uuid.equals(subLevelUniqueId(subLevel))) return subLevel;
        return null;
    }

    private static Object findCanonicalBody(UUID uuid) throws ReflectiveOperationException { return findListedBody(uuid); }
    private static Object requireCanonicalBody() throws ReflectiveOperationException {
        Object canonical = findCanonicalBody(bodyId);
        if (canonical == null) throw new IllegalStateException("canonical live Sable body unavailable for UUID " + bodyId);
        return canonical;
    }

    private static Object findCurrentPhysicsHandle(Object canonicalBody) throws ReflectiveOperationException {
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
        if (vector instanceof Vector3dc joml) return switch (name) { case "x" -> joml.x(); case "y" -> joml.y(); case "z" -> joml.z(); default -> throw new IllegalArgumentException("unknown vector component " + name); };
        return number(publicMethod(vector, name).invoke(vector));
    }

    private static void requireCanonicalPlotOwnership(Object canonical, BlockPos pos) throws ReflectiveOperationException {
        Object plot = publicMethod(canonical, "getPlot").invoke(canonical);
        Object contained = publicMethod(plot, "contains", double.class, double.class).invoke(plot, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (!Boolean.TRUE.equals(contained)) throw new IllegalStateException("moved position is not owned by canonical Sable plot: bodyId=" + bodyId + " pos=" + pos);
    }

    private static void addFixtureForceLoadTicket(Object body) throws ReflectiveOperationException {
        Class<?> ticketTypeClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
        forceLoadTicketType = ticketTypeClass.getField("COMMAND_FORCED").get(null);
        Class<?> unitClass = Class.forName("net.minecraft.util.Unit");
        forceLoadTicketKey = unitClass.getField("INSTANCE").get(null);
        Method addTicket = container.getClass().getMethod("addForceLoadTicket", body.getClass(), ticketTypeClass, Object.class);
        Object added = addTicket.invoke(container, body, forceLoadTicketType, forceLoadTicketKey);
        if (!(added instanceof Boolean ok) || !ok) throw new IllegalStateException("Sable command-forced liveness ticket was not added for body " + bodyId);
        forceLoadTicketAdded = true;
    }

    private static void removeFixtureForceLoadTicket() throws ReflectiveOperationException {
        if (!forceLoadTicketAdded || assembledBody == null || forceLoadTicketType == null || forceLoadTicketKey == null) return;
        Method removeTicket = container.getClass().getMethod("removeForceLoadTicket", assembledBody.getClass(), forceLoadTicketType.getClass(), Object.class);
        Object removed = removeTicket.invoke(container, assembledBody, forceLoadTicketType, forceLoadTicketKey);
        if (!(removed instanceof Boolean ok) || !ok) throw new IllegalStateException("Sable command-forced liveness ticket was not removed for body " + bodyId);
        forceLoadTicketAdded = false;
    }

    private static void restorePhysicsPause() throws ReflectiveOperationException {
        if (physicsPauseChanged && physicsSystem != null) {
            publicMethod(physicsSystem, "setPaused", boolean.class).invoke(physicsSystem, physicsWasPaused);
            physicsPauseChanged = false;
        }
    }

    private static int countSourceNonAir() {
        int count = 0;
        for (BlockPos relative : mainExpected.keySet()) if (!level.getBlockState(source(relative)).isAir()) count++;
        for (BlockPos relative : childExpected.keySet()) if (!level.getBlockState(source(relative)).isAir()) count++;
        return count;
    }

    private static Entity findEntity(UUID uuid) throws ReflectiveOperationException {
        if (uuid == null) return null;
        Object value = publicMethod(level, "getEntity", UUID.class).invoke(level, uuid);
        return value instanceof Entity entity ? entity : null;
    }

    private static BlockState materialize(ExpectedBlock expected) {
        BlockState state = requireBlock(expected.id()).defaultBlockState();
        for (Map.Entry<String, String> entry : expected.properties().entrySet()) state = withProperty(state, entry.getKey(), entry.getValue());
        return state;
    }

    private static void assertReloadedMainState(BlockPos pos, ExpectedBlock expected) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation actualId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!expected.id().equals(actualId)) {
            throw new IllegalStateException("reloaded main resource mismatch at " + pos + ": expected=" + expected.id() + " actual=" + actualId);
        }
        for (Map.Entry<String, String> entry : expected.properties().entrySet()) {
            if (entry.getKey().equals("lit")) continue; // Portable Engine running state is lifecycle state, not immutable lowering state.
            String actual = propertyValue(state, entry.getKey());
            if (!entry.getValue().equals(actual)) {
                throw new IllegalStateException("reloaded main state mismatch at " + pos + " property=" + entry.getKey()
                        + " expected=" + entry.getValue() + " actual=" + actual);
            }
        }
    }

    private static void assertExpectedState(String label, BlockPos pos, ExpectedBlock expected) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation actualId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!expected.id().equals(actualId)) throw new IllegalStateException(label + " resource mismatch at " + pos + ": expected=" + expected.id() + " actual=" + actualId);
        for (Map.Entry<String, String> entry : expected.properties().entrySet()) {
            String actual = propertyValue(state, entry.getKey());
            if (!entry.getValue().equals(actual)) throw new IllegalStateException(label + " state mismatch at " + pos + " property=" + entry.getKey() + " expected=" + entry.getValue() + " actual=" + actual);
        }
    }

    private static String propertyValue(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) if (property.getName().equals(name)) return propertyValue(state, property);
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static Block requireBlock(ResourceLocation resource) {
        if (!BuiltInRegistries.BLOCK.containsKey(resource)) throw new IllegalStateException("required exact-stack block is not registered: " + resource);
        return BuiltInRegistries.BLOCK.get(resource);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) if (property.getName().equals(name)) return withParsedProperty(state, property, name, value);
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static Field publicField(Object target, String name) throws NoSuchFieldException { return target.getClass().getField(name); }
    private static Method publicMethod(Object target, String name, Class<?>... types) throws NoSuchMethodException { return target.getClass().getMethod(name, types); }
    private static Method oneArgMethod(Object target, String name, Object argument) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass()))) return method;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(1 arg)");
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            SkyforgeCompilerIntegrationPhase phase, String expected, long startTick, long deadlineTick,
            String ids, String serverState, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(activeCapability, phase, expected, startTick, deadlineTick, ids, serverState, "headless", dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        if (waitDiagnostic == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic(SkyforgeCompilerIntegrationPhase.SERVER_BOOT, "fixture reaches classified terminal state", now, now, movedIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(movedIds(), safeServerState(), "headless", dump);
    }

    private static String sourceIds() {
        return "base=" + BASE + " main=" + mainExpected.size() + " child=" + childExpected.size() + " glueIds=" + glueIds;
    }

    private static String movedIds() {
        return "bodyId=" + bodyId + " movedOffset=" + movedOffset + " movedBearing=" + movedBearing
                + " movedRoles=" + movedRoles + " nestedChildId=" + nestedChildId;
    }

    private static String safeServerState() {
        if (level == null) return "overworld=null";
        String ids;
        try { ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds()); }
        catch (ReflectiveOperationException exception) { ids = "<reflection-failed:" + exception.getClass().getSimpleName() + ">"; }
        return "gameTime=" + level.getGameTime() + " stage=" + stage + " subLevelIds=" + ids
                + " sourceBearing=" + (compilerFixture == null ? "n/a" : level.getBlockState(source(findManifestKind("propeller_bearing"))))
                + " movedBearing=" + (movedBearing == null ? "n/a" : level.getBlockState(movedBearing));
    }

    private static void cleanupQuietly() {
        try { restorePhysicsPause(); } catch (ReflectiveOperationException | RuntimeException ignored) {}
        try { removeFixtureForceLoadTicket(); } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static void failReflection(SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getTargetException() : exception;
        fail(code, finalDiagnostic("reflectionFailure=" + cause), "runtime reflection failed: " + cause);
    }

    private static void fail(SkyforgeCompilerIntegrationFailure code, SkyforgeCompilerIntegrationDiagnostic diagnostic, String reason) {
        if (!complete) {
            cleanupQuietly();
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR, activePrefix + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(activeCapability + " failed: " + code + ": " + reason);
    }

    private static boolean absNear(float expectedMagnitude, float actual, float tolerance) {
        return Float.isFinite(actual) && Math.abs(Math.abs(actual) - expectedMagnitude) <= tolerance;
    }
    private static double number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("expected Number, got " + value);
        return number.doubleValue();
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static BlockPos source(BlockPos relative) { return BASE.offset(relative); }
    private static BlockPos source(AircraftBlockspaceIR.LatticePoint relative) { return source(blockPos(relative)); }
    private static BlockPos moved(BlockPos relative) { return source(relative).offset(movedOffset); }
    private static BlockPos blockPos(AircraftBlockspaceIR.LatticePoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

    private record GlueDomainSpec(String name, BlockPos from, BlockPos to) {}
    private record PersistenceIdentity(UUID bodyId, UUID nestedChildId, BlockPos movedOffset, String manifestSha256, String powertrainSha256, List<UUID> glueIds) { PersistenceIdentity { glueIds = List.copyOf(glueIds); } }
    private record EffectivePlacement(String kind, String resourceId, Map<String, String> blockState) {}
    private record ExpectedBlock(ResourceLocation id, Map<String, String> properties) { ExpectedBlock { properties = Map.copyOf(properties); } }
    private record KineticState(float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork, String runtimeType) {
        private static final KineticState MISSING = new KineticState(0.0f, 0.0f, false, false, "missing");
    }
    private record ChildState(UUID id, boolean present, boolean running, int blockCount, int hubCount, int sailCount, int sailPower, String runtimeType) {
        private static final ChildState MISSING = new ChildState(null, false, false, 0, 0, 0, 0, "missing");
    }
}
