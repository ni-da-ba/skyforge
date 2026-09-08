package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * C12 B0-A1/A2: build the exact historical B0-A MAIN_BODY, assemble it through Simulated's actual
 * Physics Assembler, prove exact membership, then read authoritative live Sable mass/COM evidence.
 *
 * <p>The optional flight stack is accessed by registry ID/reflection so normal packaged Skyforge
 * stays inert when those mods are absent.
 */
final class SkyforgeWaveC12BellancaB0AssemblyAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC12BellancaB0Assembly";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC12BellancaB0AssemblyAcceptance.class.getName());

    private static final BlockPos BASE = new BlockPos(0, 220, 0);
    private static final BlockPos ASSEMBLER_REL = new BlockPos(0, 2, 2);
    private static final int EXPECTED_UNIQUE_MAIN_BODY_BLOCKS = 105;
    private static final double MASS_REVIEW_THRESHOLD_KPG = 60.0;

    private static final ResourceLocation SAIL = id("create:white_sail");
    private static final ResourceLocation PLANKS = id("minecraft:spruce_planks");
    private static final ResourceLocation ENGINE = id("simulated:red_portable_engine");
    private static final ResourceLocation SPEED_CONTROLLER = id("create:rotation_speed_controller");
    private static final ResourceLocation LARGE_COG = id("create:large_cogwheel");
    private static final ResourceLocation SHAFT = id("create:shaft");
    private static final ResourceLocation PROPELLER_BEARING = id("aeronautics:propeller_bearing");
    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");

    private SkyforgeWaveC12BellancaB0AssemblyAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(
                SkyforgeWaveC12BellancaB0AssemblyAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();

        try {
            prepareFixtureAirspace(level);

            LinkedHashMap<BlockPos, ExpectedBlock> expected = exactMainBody();
            assertInt(
                    "unique MAIN_BODY position count",
                    EXPECTED_UNIQUE_MAIN_BODY_BLOCKS,
                    expected.size());

            for (Map.Entry<BlockPos, ExpectedBlock> entry : expected.entrySet()) {
                BlockPos worldPos = BASE.offset(entry.getKey());
                level.setBlock(worldPos, materialize(entry.getValue()), 3);
            }

            addGlueDomain(level, new BlockPos(-13, 4, -1), new BlockPos(2, 4, 1));
            addGlueDomain(level, new BlockPos(-2, 4, -1), new BlockPos(13, 4, 1));
            addGlueDomain(level, new BlockPos(-1, 1, -8), new BlockPos(1, 4, 5));
            addGlueDomain(level, new BlockPos(0, 1, 4), new BlockPos(0, 3, 6));

            Object container = requireServerSubLevelContainer(level);
            List<?> before = new ArrayList<>(getAllSubLevels(container));

            BlockPos assemblerWorldPos = BASE.offset(ASSEMBLER_REL);
            BlockEntity assembler = level.getBlockEntity(assemblerWorldPos);
            if (assembler == null) {
                fail("Physics Assembler block entity missing before assembly");
            }
            if (!assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                fail("unexpected assembler BE " + assembler.getClass().getName());
            }

            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

            List<?> after = new ArrayList<>(getAllSubLevels(container));
            List<Object> created = new ArrayList<>();
            for (Object candidate : after) {
                if (!containsIdentity(before, candidate)) {
                    created.add(candidate);
                }
            }
            assertInt("exactly one Sable body created", 1, created.size());

            Object subLevel = created.getFirst();
            if (!subLevel.getClass().getName().endsWith("ServerSubLevel")) {
                fail("new assembly is not a ServerSubLevel: " + subLevel.getClass().getName());
            }

            Object massTracker = publicMethod(subLevel, "getMassTracker").invoke(subLevel);
            if (massTracker == null) {
                fail("assembled Sable body has no mass tracker");
            }
            double mass = asDouble(publicMethod(massTracker, "getMass").invoke(massTracker));
            Object com = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
            if (com == null) {
                fail("assembled Sable body has null center of mass");
            }
            double comX = component(com, "x");
            double comY = component(com, "y");
            double comZ = component(com, "z");

            Object pose = publicMethod(subLevel, "logicalPose").invoke(subLevel);
            Object posePosition = publicMethod(pose, "position").invoke(pose);
            double poseX = component(posePosition, "x");
            double poseY = component(posePosition, "y");
            double poseZ = component(posePosition, "z");

            int offsetX = exactIntegerOffset("X", comX - poseX);
            int offsetY = exactIntegerOffset("Y", comY - poseY);
            int offsetZ = exactIntegerOffset("Z", comZ - poseZ);
            BlockPos offset = new BlockPos(offsetX, offsetY, offsetZ);

            for (Map.Entry<BlockPos, ExpectedBlock> entry : expected.entrySet()) {
                BlockPos source = BASE.offset(entry.getKey());
                BlockPos moved = source.offset(offset);
                assertTrue("source position vacated " + source, level.getBlockState(source).isAir());
                assertExpectedState("moved " + entry.getKey(), level.getBlockState(moved), entry.getValue());
            }

            int movedNonAir = 0;
            for (int x = -13; x <= 13; x++) {
                for (int y = 1; y <= 4; y++) {
                    for (int z = -8; z <= 6; z++) {
                        BlockPos moved = BASE.offset(x, y, z).offset(offset);
                        if (!level.getBlockState(moved).isAir()) {
                            movedNonAir++;
                        }
                    }
                }
            }
            assertInt("translated MAIN_BODY occupied block count", EXPECTED_UNIQUE_MAIN_BODY_BLOCKS, movedNonAir);

            assertTrue("live Sable mass is finite", Double.isFinite(mass));
            assertTrue("live Sable mass is positive", mass > 0.0);
            if (mass > MASS_REVIEW_THRESHOLD_KPG) {
                fail("live MAIN_BODY mass "
                        + mass
                        + " kpg exceeds explicit "
                        + MASS_REVIEW_THRESHOLD_KPG
                        + " kpg review threshold");
            }

            double localComX = comX - offsetX - BASE.getX();
            double localComY = comY - offsetY - BASE.getY();
            double localComZ = comZ - offsetZ - BASE.getZ();
            assertTrue("local COM X finite/in bounds", Double.isFinite(localComX) && localComX >= -13.0 && localComX <= 14.0);
            assertTrue("local COM Y finite/in bounds", Double.isFinite(localComY) && localComY >= 1.0 && localComY <= 5.0);
            assertTrue("local COM Z finite/in bounds", Double.isFinite(localComZ) && localComZ >= -8.0 && localComZ <= 7.0);

            // The exact MAIN_BODY is laterally symmetric; a live offset from X=0.5 would be a
            // material realization discrepancy worth stopping on before propulsion/control work.
            assertNear("lateral COM symmetry", 0.5, localComX, 0.000001);

            double forwardOfWingCenter = 0.5 - localComZ;

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WAVE_C12_BELLANCA_B0_ASSEMBLY PASS"
                            + " moved="
                            + movedNonAir
                            + " massKpg="
                            + mass
                            + " localComX="
                            + localComX
                            + " localComY="
                            + localComY
                            + " localComZ="
                            + localComZ
                            + " forwardOfWingCenter="
                            + forwardOfWingCenter
                            + " offset="
                            + offset
                            + " assembler=real"
                            + " glueDomains=4");
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException("C12 B0-A assembly acceptance reflection failure", cause);
        }
    }

    private static LinkedHashMap<BlockPos, ExpectedBlock> exactMainBody() {
        LinkedHashMap<BlockPos, ExpectedBlock> blocks = new LinkedHashMap<>();

        for (int x = -13; x <= -3; x++) {
            for (int z = -1; z <= 1; z++) {
                put(blocks, new BlockPos(x, 4, z), SAIL, Map.of("facing", "up"));
            }
        }
        for (int x = 3; x <= 13; x++) {
            for (int z = -1; z <= 1; z++) {
                put(blocks, new BlockPos(x, 4, z), SAIL, Map.of("facing", "up"));
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                put(blocks, new BlockPos(x, 4, z), PLANKS, Map.of());
            }
        }
        for (int z = -5; z <= 6; z++) {
            put(blocks, new BlockPos(0, 1, z), PLANKS, Map.of());
        }

        put(blocks, new BlockPos(0, 2, 0), PLANKS, Map.of());
        put(blocks, new BlockPos(0, 3, 0), PLANKS, Map.of());
        put(blocks, new BlockPos(-1, 1, -5), PLANKS, Map.of());
        put(blocks, new BlockPos(1, 1, -5), PLANKS, Map.of());

        put(blocks, new BlockPos(-1, 2, -5), ENGINE, Map.of("horizontal_facing", "east"));
        put(blocks, new BlockPos(1, 2, -5), ENGINE, Map.of("horizontal_facing", "west"));
        put(blocks, new BlockPos(0, 2, -5), SPEED_CONTROLLER, Map.of("horizontal_axis", "x"));
        put(blocks, new BlockPos(0, 3, -5), LARGE_COG, Map.of("axis", "z"));
        put(blocks, new BlockPos(0, 3, -6), SHAFT, Map.of("axis", "z"));
        put(blocks, new BlockPos(0, 3, -7), SHAFT, Map.of("axis", "z"));
        put(blocks, new BlockPos(0, 3, -8), PROPELLER_BEARING, Map.of("facing", "north"));

        // physics_assembler_support is intentionally the already-present keel position (0,1,+2).
        put(blocks, new BlockPos(0, 1, 2), PLANKS, Map.of());
        put(blocks, ASSEMBLER_REL, PHYSICS_ASSEMBLER, Map.of("face", "floor"));

        return blocks;
    }

    private static void prepareFixtureAirspace(ServerLevel level) {
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        for (int x = -15; x <= 15; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -10; z <= 8; z++) {
                    level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void addGlueDomain(ServerLevel level, BlockPos relativeFrom, BlockPos relativeTo)
            throws ReflectiveOperationException {
        Class<?> glueClass =
                Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        AABB box = (AABB) span.invoke(null, BASE.offset(relativeFrom), BASE.offset(relativeTo));
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        assertTrue("Create Super Glue domain inserted", level.addFreshEntity(glue));
    }

    private static Object requireServerSubLevelContainer(ServerLevel level)
            throws ReflectiveOperationException {
        Class<?> containerClass =
                Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Method getContainer = containerClass.getMethod("getContainer", ServerLevel.class);
        Object container = getContainer.invoke(null, level);
        if (container == null) {
            fail("Sable ServerSubLevelContainer unavailable");
        }
        return container;
    }

    @SuppressWarnings("unchecked")
    private static List<?> getAllSubLevels(Object container) throws ReflectiveOperationException {
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> list)) {
            fail("Sable getAllSubLevels did not return a List");
        }
        return (List<?>) value;
    }

    private static boolean containsIdentity(List<?> values, Object candidate) {
        for (Object value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    private static BlockState materialize(ExpectedBlock expected) {
        if (!BuiltInRegistries.BLOCK.containsKey(expected.id())) {
            fail("required B0-A block is not registered: " + expected.id());
        }
        Block block = BuiltInRegistries.BLOCK.get(expected.id());
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> property : expected.properties().entrySet()) {
            state = withManifestProperty(state, property.getKey(), property.getValue());
        }
        return state;
    }

    private static void assertExpectedState(String label, BlockState state, ExpectedBlock expected) {
        ResourceLocation actualId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!expected.id().equals(actualId)) {
            fail(label + " block mismatch expected=" + expected.id() + " actual=" + actualId);
        }
        for (Map.Entry<String, String> property : expected.properties().entrySet()) {
            String actual = readManifestProperty(state, property.getKey());
            if (!property.getValue().equals(actual)) {
                fail(label
                        + " property "
                        + property.getKey()
                        + " expected="
                        + property.getValue()
                        + " actual="
                        + actual);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withManifestProperty(BlockState state, String semanticName, String value) {
        Property property = findManifestProperty(state, semanticName);
        Object parsed = property.getValue(value).orElse(null);
        if (!(parsed instanceof Comparable)) {
            fail("cannot parse block property " + semanticName + "=" + value + " on " + state);
        }
        return state.setValue(property, (Comparable) parsed);
    }

    @SuppressWarnings("rawtypes")
    private static String readManifestProperty(BlockState state, String semanticName) {
        Property property = findManifestProperty(state, semanticName);
        Object value = state.getValue(property);
        return property.getName((Comparable) value);
    }

    private static Property<?> findManifestProperty(BlockState state, String semanticName) {
        List<String> candidates = switch (semanticName) {
            case "horizontal_facing" -> List.of("horizontal_facing", "facing");
            case "horizontal_axis" -> List.of("horizontal_axis", "axis");
            default -> List.of(semanticName);
        };
        for (String candidate : candidates) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(candidate)) {
                    return property;
                }
            }
        }
        fail("missing property " + semanticName + " on " + state);
        throw new AssertionError();
    }

    private static void put(
            LinkedHashMap<BlockPos, ExpectedBlock> blocks,
            BlockPos pos,
            ResourceLocation id,
            Map<String, String> properties) {
        ExpectedBlock expected = new ExpectedBlock(id, Map.copyOf(properties));
        ExpectedBlock existing = blocks.putIfAbsent(pos, expected);
        if (existing != null && !existing.equals(expected)) {
            fail("manifest overlap conflict at " + pos + ": " + existing + " vs " + expected);
        }
    }

    private static int exactIntegerOffset(String axis, double value) {
        int rounded = (int) Math.round(value);
        assertNear("assembly " + axis + " offset is integral", rounded, value, 0.000001);
        return rounded;
    }

    private static double component(Object vector, String methodName)
            throws ReflectiveOperationException {
        return asDouble(publicMethod(vector, methodName).invoke(vector));
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("expected numeric value, got " + value);
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            fail(label
                    + ": expected="
                    + expected
                    + " actual="
                    + actual
                    + " tolerance="
                    + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return resourceLocation;
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C12_BELLANCA_B0_ASSEMBLY FAIL " + reason);
        throw new IllegalStateException("C12 B0-A assembly acceptance failed: " + reason);
    }

    private record ExpectedBlock(ResourceLocation id, Map<String, String> properties) {}
}
