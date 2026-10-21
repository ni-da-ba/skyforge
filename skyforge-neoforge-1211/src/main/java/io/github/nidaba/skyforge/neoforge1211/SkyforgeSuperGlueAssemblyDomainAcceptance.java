package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
import org.joml.Vector3dc;

/** PLATFORM-004: exact-stack causal qualification of one bounded Create Super Glue assembly domain. */
final class SkyforgeSuperGlueAssemblyDomainAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformSuperGlueAssemblyDomain";
    static final String CAPABILITY = "SUPER_GLUE_ASSEMBLY_DOMAIN_LIFECYCLE";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeSuperGlueAssemblyDomainAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_SUPER_GLUE_ASSEMBLY_DOMAIN";
    private static final String GLUE_CLASS_NAME = "com.simibubi.create.content.contraptions.glue.SuperGlueEntity";
    private static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");
    private static final int CLUSTER_BLOCKS = 8;

    private static final CaseSpec GLUED = new CaseSpec("glued", new BlockPos(0, 200, 0), true);
    private static final CaseSpec UNGLUED = new CaseSpec("unglued-control", new BlockPos(24, 200, 0), false);

    private static ServerLevel level;
    private static Object container;
    private static boolean complete;
    private static String activeCase = "startup";
    private static SkyforgeCompilerIntegrationPhase activePhase = SkyforgeCompilerIntegrationPhase.SERVER_BOOT;
    private static SkyforgeCompilerIntegrationFailure reflectionFailure =
            SkyforgeCompilerIntegrationFailure.FAIL_RUNTIME_EXIT;

    private SkyforgeSuperGlueAssemblyDomainAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSuperGlueAssemblyDomainAcceptance::onServerStarted);
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
            CaseResult glued = runCase(GLUED);
            CaseResult unglued = runCase(UNGLUED);

            if (glued.secondaryTransferred() != CLUSTER_BLOCKS || unglued.secondaryTransferred() != 0) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.COMPLETE,
                                "only the one-domain case transfers the secondary honey cluster",
                                now,
                                now,
                                "gluedBody=" + glued.bodyId() + " ungluedBody=" + unglued.bodyId(),
                                safeServerState(),
                                "gluedSecondary=" + glued.secondaryTransferred()
                                        + " ungluedSecondary=" + unglued.secondaryTransferred()),
                        "positive/negative glue cases did not diverge causally");
            }

            complete = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " gluedBodyId=" + glued.bodyId()
                            + " gluedOffset=" + glued.movedOffset()
                            + " gluedSeedTransferred=" + glued.seedTransferred()
                            + " gluedSecondaryTransferred=" + glued.secondaryTransferred()
                            + " gluedMovedGlueDomains=" + glued.movedGlueDomains()
                            + " ungluedBodyId=" + unglued.bodyId()
                            + " ungluedOffset=" + unglued.movedOffset()
                            + " ungluedSeedTransferred=" + unglued.seedTransferred()
                            + " ungluedSecondaryTransferred=" + unglued.secondaryTransferred()
                            + " ungluedSecondaryParentRemaining=" + unglued.secondaryParentRemaining()
                            + " slimeHoneyOrdinaryAdhesion=false"
                            + " exactlyOneIntentionalGlueDomain=true"
                            + " clientState=headless");
        } catch (ReflectiveOperationException exception) {
            failReflection(reflectionFailure, exception);
        } catch (RuntimeException exception) {
            if (!complete) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_RUNTIME_EXIT,
                        diagnostic(
                                activePhase,
                                "bounded glue fixture reaches a classified terminal state",
                                now,
                                now,
                                "case=" + activeCase,
                                safeServerState(),
                                exception.toString()),
                        "unexpected runtime failure: " + exception);
            }
            throw exception;
        }
    }

    private static CaseResult runCase(CaseSpec spec) throws ReflectiveOperationException {
        activeCase = spec.name();
        activePhase = SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT;
        reflectionFailure = SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT;
        long now = level.getGameTime();
        prepareFixture(spec);
        requireNonStickyBoundary(spec, now);

        GlueExpectation glue = null;
        if (spec.glued()) {
            reflectionFailure = SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION;
            glue = addAndValidateGlue(spec, now);
        } else if (countGlueEntities(sourceSearchBox(spec)) != 0) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                            "negative control starts with zero Super Glue entities",
                            now,
                            now,
                            "case=" + spec.name(),
                            caseServerState(spec),
                            "unexpected source glue entity"),
                    "unglued control contains a Super Glue entity before assembly");
        }

        Set<UUID> beforeIds = currentSubLevelIds(container);
        BlockEntity assembler = level.getBlockEntity(spec.assembler());
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                            "real Physics Assembler block entity exists",
                            now,
                            now,
                            "case=" + spec.name() + " assembler=" + spec.assembler(),
                            caseServerState(spec),
                            "blockEntity=" + (assembler == null ? "null" : assembler.getClass().getName())),
                    "Physics Assembler block entity unavailable for " + spec.name());
        }

        activePhase = SkyforgeCompilerIntegrationPhase.ASSEMBLY;
        reflectionFailure = SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY;
        publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

        Set<UUID> afterIds = currentSubLevelIds(container);
        Set<UUID> created = new LinkedHashSet<>(afterIds);
        created.removeAll(beforeIds);
        if (created.size() != 1) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "exactly one new Sable body is registered synchronously",
                            now,
                            now,
                            "case=" + spec.name() + " assembler=" + spec.assembler(),
                            caseServerState(spec),
                            "beforeIds=" + beforeIds + " afterIds=" + afterIds + " created=" + created),
                    "case did not create exactly one Sable body");
        }

        UUID bodyId = created.iterator().next();
        Object body = findListedBody(bodyId);
        if (body == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "new Sable UUID resolves to its synchronous body",
                            now,
                            now,
                            "case=" + spec.name() + " bodyId=" + bodyId,
                            caseServerState(spec),
                            "listedBody=null"),
                    "new Sable UUID could not be reselected");
        }

        Object massTracker = publicMethod(body, "getMassTracker").invoke(body);
        double mass = number(publicMethod(massTracker, "getMass").invoke(massTracker));
        Object centerOfMass = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
        boolean removed = Boolean.TRUE.equals(publicMethod(body, "isRemoved").invoke(body));
        if (!(mass > 0.0) || centerOfMass == null || removed) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PHYSICS,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.PHYSICS_INITIALIZATION,
                            "assembled comparison body has positive mass, center of mass, and live state",
                            now,
                            now,
                            "case=" + spec.name() + " bodyId=" + bodyId,
                            caseServerState(spec),
                            "mass=" + mass + " centerOfMass=" + centerOfMass + " removed=" + removed),
                    "assembled comparison body is not a valid Sable body");
        }

        BlockPos offset = movedOffset(body, centerOfMass);
        int seedParentRemaining = countMatching(spec.seedPositions(), Blocks.SLIME_BLOCK, BlockPos.ZERO);
        int secondaryParentRemaining = countMatching(spec.secondaryPositions(), Blocks.HONEY_BLOCK, BlockPos.ZERO);
        int seedTransferred = countMatching(spec.seedPositions(), Blocks.SLIME_BLOCK, offset);
        int secondaryTransferred = countMatching(spec.secondaryPositions(), Blocks.HONEY_BLOCK, offset);

        requireTransferredSeed(spec, body, bodyId, offset, seedParentRemaining, seedTransferred, now);

        int movedGlueDomains;
        if (spec.glued()) {
            requireGluedSecondary(spec, body, bodyId, offset, secondaryParentRemaining, secondaryTransferred, now);
            movedGlueDomains = requireMovedGlue(spec, glue, offset, now);
        } else {
            requireUngluedSecondary(spec, bodyId, offset, secondaryParentRemaining, secondaryTransferred, now);
            movedGlueDomains = countGlueEntities(movedSearchBox(spec, offset));
            if (movedGlueDomains != 0) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                                "negative control produces no moved Super Glue domain",
                                now,
                                now,
                                "case=" + spec.name() + " bodyId=" + bodyId,
                                caseServerState(spec),
                                "movedGlueDomains=" + movedGlueDomains),
                        "unglued control unexpectedly produced a glue domain");
            }
        }

        boolean assemblerMoved = level.getBlockState(spec.assembler()).isAir()
                && PHYSICS_ASSEMBLER.equals(blockId(level.getBlockState(spec.assembler().offset(offset))));
        LOGGER.log(
                System.Logger.Level.INFO,
                PREFIX + " CASE_PASS case=" + spec.name()
                        + " bodyId=" + bodyId
                        + " offset=" + offset
                        + " seedTransferred=" + seedTransferred
                        + " secondaryTransferred=" + secondaryTransferred
                        + " secondaryParentRemaining=" + secondaryParentRemaining
                        + " movedGlueDomains=" + movedGlueDomains
                        + " assemblerMoved=" + assemblerMoved
                        + " mass=" + mass);
        return new CaseResult(
                spec.name(),
                bodyId,
                offset,
                seedTransferred,
                secondaryTransferred,
                secondaryParentRemaining,
                movedGlueDomains,
                assemblerMoved);
    }

    private static void prepareFixture(CaseSpec spec) {
        level.getChunk(spec.base().getX() >> 4, spec.base().getZ() >> 4);
        for (int x = spec.base().getX() - 2; x <= spec.base().getX() + 5; x++) {
            for (int y = spec.base().getY() - 2; y <= spec.base().getY() + 4; y++) {
                for (int z = spec.base().getZ() - 2; z <= spec.base().getZ() + 3; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (BlockPos pos : spec.seedPositions()) {
            if (!level.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), 3)) {
                failPlacement(spec, "failed to place slime seed block at " + pos);
            }
        }
        for (BlockPos pos : spec.secondaryPositions()) {
            if (!level.setBlock(pos, Blocks.HONEY_BLOCK.defaultBlockState(), 3)) {
                failPlacement(spec, "failed to place honey secondary block at " + pos);
            }
        }
        BlockState assemblerState = withProperty(requireBlock(PHYSICS_ASSEMBLER).defaultBlockState(), "face", "floor");
        if (!level.setBlock(spec.assembler(), assemblerState, 3)) {
            failPlacement(spec, "failed to place Physics Assembler at " + spec.assembler());
        }
    }

    private static void requireNonStickyBoundary(CaseSpec spec, long now) {
        BlockState slime = level.getBlockState(spec.bridgeSeed());
        BlockState honey = level.getBlockState(spec.bridgeSecondary());
        boolean slimeToHoney = slime.canStickTo(honey);
        boolean honeyToSlime = honey.canStickTo(slime);
        if (slimeToHoney || honeyToSlime) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                            "slime/honey boundary has no ordinary NeoForge adhesion in either direction",
                            now,
                            now,
                            "case=" + spec.name() + " bridge=" + spec.bridgeSeed() + "->" + spec.bridgeSecondary(),
                            caseServerState(spec),
                            "slimeToHoney=" + slimeToHoney + " honeyToSlime=" + honeyToSlime),
                    "negative-control material boundary is ordinarily adhesive");
        }
    }

    private static GlueExpectation addAndValidateGlue(CaseSpec spec, long now) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName(GLUE_CLASS_NAME);
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        AABB expectedBox = (AABB) span.invoke(null, spec.bridgeSeed(), spec.bridgeSecondary());
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, expectedBox);
        if (!level.addFreshEntity(glue)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                            "one bounded Super Glue entity registers in the source world",
                            now,
                            now,
                            "case=" + spec.name(),
                            caseServerState(spec),
                            "addFreshEntity=false box=" + expectedBox),
                    "failed to register bounded Super Glue entity");
        }

        List<Entity> sourceGlues = glueEntities(sourceSearchBox(spec));
        boolean seedContained = Boolean.TRUE.equals(publicMethod(glue, "contains", BlockPos.class).invoke(glue, spec.bridgeSeed()));
        boolean secondaryContained = Boolean.TRUE.equals(
                publicMethod(glue, "contains", BlockPos.class).invoke(glue, spec.bridgeSecondary()));
        int containedFixtureCells = 0;
        for (BlockPos pos : spec.allFixtureCells()) {
            if (Boolean.TRUE.equals(publicMethod(glue, "contains", BlockPos.class).invoke(glue, pos))) {
                containedFixtureCells++;
            }
        }
        if (sourceGlues.size() != 1
                || !glue.getUUID().equals(sourceGlues.getFirst().getUUID())
                || !seedContained
                || !secondaryContained
                || containedFixtureCells != 2) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                            "exactly one glue domain contains only the intended adjacent bridge cells",
                            now,
                            now,
                            "case=" + spec.name() + " glueId=" + glue.getUUID(),
                            caseServerState(spec),
                            "sourceGlueCount=" + sourceGlues.size()
                                    + " seedContained=" + seedContained
                                    + " secondaryContained=" + secondaryContained
                                    + " containedFixtureCells=" + containedFixtureCells
                                    + " box=" + expectedBox),
                    "bounded Super Glue domain registration did not match the intended bridge");
        }
        return new GlueExpectation(glue.getUUID(), expectedBox);
    }

    private static void requireTransferredSeed(
            CaseSpec spec,
            Object body,
            UUID bodyId,
            BlockPos offset,
            int parentRemaining,
            int transferred,
            long now) throws ReflectiveOperationException {
        if (parentRemaining != 0 || transferred != CLUSTER_BLOCKS) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "seed slime cluster transfers completely in both cases",
                            now,
                            now,
                            "case=" + spec.name() + " bodyId=" + bodyId,
                            caseServerState(spec),
                            "seedParentRemaining=" + parentRemaining + " seedTransferred=" + transferred),
                    "seed cluster transfer mismatch");
        }
        for (BlockPos source : spec.seedPositions()) {
            requireCanonicalPlotOwnership(body, source.offset(offset), bodyId);
        }
    }

    private static void requireGluedSecondary(
            CaseSpec spec,
            Object body,
            UUID bodyId,
            BlockPos offset,
            int parentRemaining,
            int transferred,
            long now) throws ReflectiveOperationException {
        if (parentRemaining != 0 || transferred != CLUSTER_BLOCKS) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "one bounded glue domain transfers the complete honey secondary cluster",
                            now,
                            now,
                            "case=" + spec.name() + " bodyId=" + bodyId,
                            caseServerState(spec),
                            "secondaryParentRemaining=" + parentRemaining + " secondaryTransferred=" + transferred),
                    "glued secondary cluster was not transferred completely");
        }
        for (BlockPos source : spec.secondaryPositions()) {
            requireCanonicalPlotOwnership(body, source.offset(offset), bodyId);
        }
    }

    private static void requireUngluedSecondary(
            CaseSpec spec,
            UUID bodyId,
            BlockPos offset,
            int parentRemaining,
            int transferred,
            long now) {
        if (parentRemaining != CLUSTER_BLOCKS || transferred != 0) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "unglued honey cluster remains in the parent world and outside the Sable body",
                            now,
                            now,
                            "case=" + spec.name() + " bodyId=" + bodyId,
                            caseServerState(spec),
                            "secondaryParentRemaining=" + parentRemaining + " secondaryTransferred=" + transferred),
                    "unglued negative control captured the secondary cluster");
        }
        for (BlockPos source : spec.secondaryPositions()) {
            BlockState moved = level.getBlockState(source.offset(offset));
            if (!moved.isAir()) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                        diagnostic(
                                SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                                "unglued honey would-be moved coordinates remain air",
                                now,
                                now,
                                "case=" + spec.name() + " bodyId=" + bodyId,
                                caseServerState(spec),
                                "unexpectedMovedState=" + moved + " pos=" + source.offset(offset)),
                        "unglued secondary cluster has unexpected moved-body state");
            }
        }
    }

    private static int requireMovedGlue(CaseSpec spec, GlueExpectation glue, BlockPos offset, long now) {
        if (glue == null) {
            throw new IllegalStateException("glued case missing glue expectation");
        }
        int sourceCount = countGlueEntities(sourceSearchBox(spec));
        AABB movedBox = glue.sourceBox().move(offset.getX(), offset.getY(), offset.getZ());
        List<Entity> movedCandidates = glueEntities(movedBox.inflate(1.0));
        int exactMoved = 0;
        for (Entity entity : movedCandidates) {
            if (sameBox(entity.getBoundingBox(), movedBox)) {
                exactMoved++;
            }
        }
        if (sourceCount != 0 || exactMoved != 1) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_GLUE_REGISTRATION,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "source glue is consumed and exactly one translated domain exists in the moved plot",
                            now,
                            now,
                            "case=" + spec.name() + " sourceGlueId=" + glue.sourceId(),
                            caseServerState(spec),
                            "sourceGlueCount=" + sourceCount
                                    + " movedCandidates=" + movedCandidates.size()
                                    + " exactMoved=" + exactMoved
                                    + " expectedMovedBox=" + movedBox),
                    "Super Glue domain was not translated exactly once with the assembled body");
        }
        return exactMoved;
    }

    private static int countMatching(List<BlockPos> sources, Block expected, BlockPos offset) {
        int count = 0;
        for (BlockPos source : sources) {
            if (level.getBlockState(source.offset(offset)).is(expected)) {
                count++;
            }
        }
        return count;
    }

    private static void requireCanonicalPlotOwnership(Object body, BlockPos moved, UUID bodyId)
            throws ReflectiveOperationException {
        Object plot = publicMethod(body, "getPlot").invoke(body);
        Object contained = publicMethod(plot, "contains", double.class, double.class)
                .invoke(plot, moved.getX() + 0.5, moved.getZ() + 0.5);
        if (!Boolean.TRUE.equals(contained)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_ASSEMBLY,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                            "transferred cluster cells are owned by the canonical Sable plot",
                            level.getGameTime(),
                            level.getGameTime(),
                            "case=" + activeCase + " bodyId=" + bodyId,
                            safeServerState(),
                            "movedPos=" + moved + " plotContains=false"),
                    "transferred cell is outside canonical Sable plot ownership");
        }
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

    private static double number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("expected numeric runtime value, got " + value);
        }
        return number.doubleValue();
    }

    private static List<Entity> glueEntities(AABB box) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            if (GLUE_CLASS_NAME.equals(entity.getClass().getName())) {
                result.add(entity);
            }
        }
        return result;
    }

    private static int countGlueEntities(AABB box) {
        return glueEntities(box).size();
    }

    private static AABB sourceSearchBox(CaseSpec spec) {
        return new AABB(
                spec.base().getX() - 1,
                spec.base().getY() - 1,
                spec.base().getZ() - 1,
                spec.base().getX() + 5,
                spec.base().getY() + 4,
                spec.base().getZ() + 3);
    }

    private static AABB movedSearchBox(CaseSpec spec, BlockPos offset) {
        return sourceSearchBox(spec).move(offset.getX(), offset.getY(), offset.getZ());
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
                    "required Create mod is not loaded");
        }
        requireBlock(PHYSICS_ASSEMBLER);
        try {
            Class.forName(GLUE_CLASS_NAME);
            Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper");
        } catch (ClassNotFoundException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_TARGET_LOWERING,
                    diagnostic(
                            SkyforgeCompilerIntegrationPhase.SERVER_BOOT,
                            "required Create/Sable/Simulated runtime classes resolve",
                            level.getGameTime(),
                            level.getGameTime(),
                            "class=" + exception.getMessage(),
                            safeServerState(),
                            exception.toString()),
                    "required exact-stack runtime class unavailable");
        }
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

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required exact-stack block is not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
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

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        String ids;
        try {
            ids = container == null ? "container=null" : String.valueOf(currentSubLevelIds(container));
        } catch (ReflectiveOperationException exception) {
            ids = "subLevelIds=<reflection-failed:" + exception.getClass().getSimpleName() + ">";
        }
        return "gameTime=" + level.getGameTime() + " activeCase=" + activeCase + " subLevelIds=" + ids;
    }

    private static String caseServerState(CaseSpec spec) {
        return safeServerState()
                + " assembler=" + level.getBlockState(spec.assembler())
                + " bridgeSeed=" + level.getBlockState(spec.bridgeSeed())
                + " bridgeSecondary=" + level.getBlockState(spec.bridgeSecondary());
    }

    private static void failPlacement(CaseSpec spec, String reason) {
        fail(
                SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                diagnostic(
                        SkyforgeCompilerIntegrationPhase.FIXTURE_PLACEMENT,
                        "two bounded comparison clusters and Physics Assembler are placed",
                        level.getGameTime(),
                        level.getGameTime(),
                        "case=" + spec.name(),
                        caseServerState(spec),
                        reason),
                reason);
    }

    private static void failReflection(
            SkyforgeCompilerIntegrationFailure code, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                ? invocation.getTargetException()
                : exception;
        fail(
                code,
                diagnostic(
                        activePhase,
                        "reflection-backed exact-stack operation succeeds",
                        level == null ? 0L : level.getGameTime(),
                        level == null ? 0L : level.getGameTime(),
                        "case=" + activeCase,
                        safeServerState(),
                        "reflectionFailure=" + cause),
                "runtime reflection failed: " + cause);
    }

    private static void fail(
            SkyforgeCompilerIntegrationFailure code,
            SkyforgeCompilerIntegrationDiagnostic diagnostic,
            String reason) {
        if (!complete) {
            complete = true;
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(CAPABILITY + " failed: " + code + ": " + reason);
    }

    private record GlueExpectation(UUID sourceId, AABB sourceBox) {}

    private record CaseResult(
            String name,
            UUID bodyId,
            BlockPos movedOffset,
            int seedTransferred,
            int secondaryTransferred,
            int secondaryParentRemaining,
            int movedGlueDomains,
            boolean assemblerMoved) {}

    private record CaseSpec(String name, BlockPos base, boolean glued) {
        BlockPos assembler() {
            return base.offset(0, 2, 0);
        }

        BlockPos bridgeSeed() {
            return base.offset(1, 0, 0);
        }

        BlockPos bridgeSecondary() {
            return base.offset(2, 0, 0);
        }

        List<BlockPos> seedPositions() {
            return cube(base, base.offset(1, 1, 1));
        }

        List<BlockPos> secondaryPositions() {
            return cube(base.offset(2, 0, 0), base.offset(3, 1, 1));
        }

        List<BlockPos> allFixtureCells() {
            List<BlockPos> result = new ArrayList<>(seedPositions());
            result.addAll(secondaryPositions());
            result.add(assembler());
            return result;
        }

        private static List<BlockPos> cube(BlockPos min, BlockPos max) {
            List<BlockPos> result = new ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
            return result;
        }
    }
}
