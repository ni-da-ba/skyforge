package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * C12 B0-A1/A2 development-only proof: materialize the historical B0-A MAIN_BODY hypothesis,
 * assemble it through the real Simulated Physics Assembler, then read authoritative Sable mass/CG.
 *
 * <p>Optional Create/Simulated/Sable types are accessed through registry ids/reflection so packaged
 * Skyforge remains loadable without the flight stack.
 */
final class SkyforgeWaveC12BellancaAssemblyAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC12BellancaAssemblyMass";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC12BellancaAssemblyAcceptance.class.getName());

    private static final BlockPos ORIGIN = new BlockPos(0, 160, 0);
    private static final int EXPECTED_MAIN_BODY_BLOCKS = 105;
    private static final double PAPER_MAIN_BODY_MASS = 38.5;
    private static final double PAPER_COM_X = 0.5;
    private static final double PAPER_COM_Y = 3.643;
    private static final double PAPER_COM_Z = -0.331;

    private static final ResourceLocation SAIL = id("create:white_sail");
    private static final ResourceLocation ENGINE = id("simulated:red_portable_engine");
    private static final ResourceLocation SPEED_CONTROLLER = id("create:rotation_speed_controller");
    private static final ResourceLocation LARGE_COG = id("create:large_cogwheel");
    private static final ResourceLocation SHAFT = id("create:shaft");
    private static final ResourceLocation PROP_BEARING = id("aeronautics:propeller_bearing");
    private static final ResourceLocation PHYSICS_ASSEMBLER = id("simulated:physics_assembler");

    private SkyforgeWaveC12BellancaAssemblyAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(
                SkyforgeWaveC12BellancaAssemblyAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        try {
            LinkedHashMap<BlockPos, BlockSpec> relative = mainBodySpecs();
            assertInt("distinct MAIN_BODY block count", EXPECTED_MAIN_BODY_BLOCKS, relative.size());

            LinkedHashMap<BlockPos, BlockState> placed = new LinkedHashMap<>();
            for (Map.Entry<BlockPos, BlockSpec> entry : relative.entrySet()) {
                BlockPos absolute = ORIGIN.offset(entry.getKey());
                level.getChunk(absolute.getX() >> 4, absolute.getZ() >> 4);
                BlockState state = materialize(entry.getValue());
                level.setBlock(absolute, state, 3);
                placed.put(absolute, state);
            }

            // Historical PR #242 glue domains, excluding G-PROP because B0-A1/A2 is MAIN_BODY only.
            addGlue(level, rel(-13, 4, -1), rel(2, 4, 1));
            addGlue(level, rel(-2, 4, -1), rel(13, 4, 1));
            addGlue(level, rel(-1, 1, -8), rel(1, 4, 5));
            addGlue(level, rel(0, 1, 4), rel(0, 3, 6));

            BlockPos assemblerSource = rel(0, 2, 2);
            BlockEntity assembler = level.getBlockEntity(assemblerSource);
            if (assembler == null
                    || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
                fail("Physics Assembler BE missing at source: "
                        + (assembler == null ? "<null>" : assembler.getClass().getName()));
            }

            Object container = sableContainer(level);
            List<?> before = new ArrayList<>(subLevels(container));
            publicMethod(assembler, "assembleOrDisassemble").invoke(assembler);

            List<?> after = subLevels(container);
            List<Object> created = new ArrayList<>();
            for (Object candidate : after) {
                if (!before.contains(candidate)) {
                    created.add(candidate);
                }
            }

            assertInt("exactly one new Sable sublevel", 1, created.size());
            Object subLevel = created.getFirst();
            if (!subLevel.getClass().getName().endsWith("ServerSubLevel")) {
                fail("new sublevel is not ServerSubLevel: " + subLevel.getClass().getName());
            }

            BlockPos movedAssembler = primaryAssembler(subLevel);
            if (movedAssembler == null) {
                fail("moved B0-A Physics Assembler was not recorded as primary assembler");
            }
            BlockPos offset = movedAssembler.subtract(assemblerSource);

            int movedCount = 0;
            for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
                BlockPos source = entry.getKey();
                BlockPos moved = source.offset(offset);

                if (!level.getBlockState(source).isAir()) {
                    fail("source MAIN_BODY position did not clear: " + source + " -> "
                            + BuiltInRegistries.BLOCK.getKey(level.getBlockState(source).getBlock()));
                }

                BlockState actual = level.getBlockState(moved);
                if (!actual.equals(entry.getValue())) {
                    fail("relocated state mismatch source="
                            + source
                            + " moved="
                            + moved
                            + " expected="
                            + entry.getValue()
                            + " actual="
                            + actual);
                }
                movedCount++;
            }
            assertInt("verified translated MAIN_BODY states", EXPECTED_MAIN_BODY_BLOCKS, movedCount);

            int nonAirInExpectedEnvelope = countTranslatedEnvelope(level, offset);
            assertInt(
                    "translated MAIN_BODY envelope non-air count",
                    EXPECTED_MAIN_BODY_BLOCKS,
                    nonAirInExpectedEnvelope);

            // Explicitly prove that the not-yet-tested nested propeller is absent from B0-A1/A2.
            for (int x = -2; x <= 2; x++) {
                BlockPos propPlane = rel(x, 3, -9).offset(offset);
                if (!level.getBlockState(propPlane).isAir()) {
                    fail("B0-A1/A2 unexpectedly captured PROP_CHILD position " + propPlane);
                }
            }

            Object massData = publicMethod(subLevel, "getMassTracker").invoke(subLevel);
            if (massData == null) {
                fail("Sable mass tracker is null after assembly");
            }

            double liveMass = number(publicMethod(massData, "getMass").invoke(massData));
            Object center = publicMethod(massData, "getCenterOfMass").invoke(massData);
            if (center == null) {
                fail("Sable center of mass is null after assembly");
            }

            double plotComX = number(publicMethod(center, "x").invoke(center));
            double plotComY = number(publicMethod(center, "y").invoke(center));
            double plotComZ = number(publicMethod(center, "z").invoke(center));

            double localComX = plotComX - offset.getX() - ORIGIN.getX();
            double localComY = plotComY - offset.getY() - ORIGIN.getY();
            double localComZ = plotComZ - offset.getZ() - ORIGIN.getZ();

            if (!Double.isFinite(liveMass) || liveMass <= 0.0) {
                fail("invalid live Sable mass " + liveMass);
            }
            for (double value : new double[] {localComX, localComY, localComZ}) {
                if (!Double.isFinite(value)) {
                    fail("invalid live Sable local COM component " + value);
                }
            }

            String massClass = liveMass <= 55.0
                    ? "ASPIRATIONAL_RANGE"
                    : liveMass <= 60.0 ? "REVIEW_RANGE" : "MASS_REDUCTION_REQUIRED";

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WAVE_C12_B0A_ACCEPTANCE PASS"
                            + " blocks="
                            + EXPECTED_MAIN_BODY_BLOCKS
                            + " subLevel="
                            + subLevel.getClass().getSimpleName()
                            + " offset="
                            + offset
                            + " liveMass="
                            + format(liveMass)
                            + " massClass="
                            + massClass
                            + " localCom=("
                            + format(localComX)
                            + ","
                            + format(localComY)
                            + ","
                            + format(localComZ)
                            + ")"
                            + " paperMass="
                            + PAPER_MAIN_BODY_MASS
                            + " massDelta="
                            + format(liveMass - PAPER_MAIN_BODY_MASS)
                            + " paperCom=("
                            + PAPER_COM_X
                            + ","
                            + PAPER_COM_Y
                            + ","
                            + PAPER_COM_Z
                            + ")"
                            + " comDelta=("
                            + format(localComX - PAPER_COM_X)
                            + ","
                            + format(localComY - PAPER_COM_Y)
                            + ","
                            + format(localComZ - PAPER_COM_Z)
                            + ")"
                            + " glueDomains=4"
                            + " propChildAbsent=true");
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException("C12 B0-A assembly/mass reflection failure", cause);
        }
    }

    private static LinkedHashMap<BlockPos, BlockSpec> mainBodySpecs() {
        LinkedHashMap<BlockPos, BlockSpec> specs = new LinkedHashMap<>();

        for (int x = -13; x <= -3; x++) {
            for (int z = -1; z <= 1; z++) {
                put(specs, new BlockPos(x, 4, z), spec(SAIL, "facing", "up"));
            }
        }
        for (int x = 3; x <= 13; x++) {
            for (int z = -1; z <= 1; z++) {
                put(specs, new BlockPos(x, 4, z), spec(SAIL, "facing", "up"));
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                put(specs, new BlockPos(x, 4, z), spec(id("minecraft:spruce_planks")));
            }
        }

        for (int z = -5; z <= 6; z++) {
            put(specs, new BlockPos(0, 1, z), spec(id("minecraft:spruce_planks")));
        }

        put(specs, new BlockPos(0, 2, 0), spec(id("minecraft:spruce_planks")));
        put(specs, new BlockPos(0, 3, 0), spec(id("minecraft:spruce_planks")));
        put(specs, new BlockPos(-1, 1, -5), spec(id("minecraft:spruce_planks")));
        put(specs, new BlockPos(1, 1, -5), spec(id("minecraft:spruce_planks")));

        put(specs, new BlockPos(-1, 2, -5), spec(ENGINE, "facing", "east"));
        put(specs, new BlockPos(1, 2, -5), spec(ENGINE, "facing", "west"));
        put(specs, new BlockPos(0, 2, -5), spec(SPEED_CONTROLLER, "axis", "x"));
        put(specs, new BlockPos(0, 3, -5), spec(LARGE_COG, "axis", "z"));
        put(specs, new BlockPos(0, 3, -6), spec(SHAFT, "axis", "z"));
        put(specs, new BlockPos(0, 3, -7), spec(SHAFT, "axis", "z"));
        put(specs, new BlockPos(0, 3, -8), spec(PROP_BEARING, "facing", "north"));

        // Historical manifest lists this support explicitly, but it is already the keel block.
        put(specs, new BlockPos(0, 1, 2), spec(id("minecraft:spruce_planks")));
        put(
                specs,
                new BlockPos(0, 2, 2),
                spec(PHYSICS_ASSEMBLER, "face", "floor", "facing", "north"));

        return specs;
    }

    private static void put(
            Map<BlockPos, BlockSpec> specs,
            BlockPos pos,
            BlockSpec spec) {
        BlockSpec previous = specs.putIfAbsent(pos, spec);
        if (previous != null && !previous.equals(spec)) {
            throw new IllegalStateException(
                    "conflicting B0-A manifest specs at " + pos + ": " + previous + " vs " + spec);
        }
    }

    private static BlockSpec spec(ResourceLocation id, String... propertyPairs) {
        if (propertyPairs.length % 2 != 0) {
            throw new IllegalArgumentException("propertyPairs must be name/value pairs");
        }
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < propertyPairs.length; i += 2) {
            properties.put(propertyPairs[i], propertyPairs[i + 1]);
        }
        return new BlockSpec(id, Map.copyOf(properties));
    }

    private static BlockState materialize(BlockSpec spec) {
        if (!BuiltInRegistries.BLOCK.containsKey(spec.id())) {
            fail("required B0-A block is not registered: " + spec.id());
        }
        Block block = BuiltInRegistries.BLOCK.get(spec.id());
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> property : spec.properties().entrySet()) {
            state = withProperty(state, property.getKey(), property.getValue());
        }
        return state;
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        Property<?> found = null;
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                found = property;
                break;
            }
        }
        if (found == null) {
            fail("block "
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " lacks property "
                    + name
                    + "; available="
                    + state.getProperties());
        }

        Optional<?> parsed = found.getValue(value);
        if (parsed.isEmpty()) {
            fail("property "
                    + name
                    + " on "
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " does not accept "
                    + value
                    + "; values="
                    + found.getPossibleValues());
        }
        return setUnchecked(state, found, parsed.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setUnchecked(BlockState state, Property property, Object value) {
        return state.setValue(property, (Comparable) value);
    }

    private static void addGlue(ServerLevel level, BlockPos from, BlockPos to)
            throws ReflectiveOperationException {
        Class<?> glueClass =
                Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        AABB bounds = (AABB) span.invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Object glue = constructor.newInstance(level, bounds);
        if (!(glue instanceof Entity entity)) {
            fail("Create SuperGlueEntity reflection did not produce Entity");
            return;
        }
        if (!level.addFreshEntity(entity)) {
            fail("failed to add Create SuperGlueEntity " + from + " -> " + to);
        }
    }

    private static Object sableContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = type.getMethod("getContainer", ServerLevel.class).invoke(null, level);
        if (container == null) {
            fail("Sable server sublevel container unavailable");
        }
        return container;
    }

    @SuppressWarnings("unchecked")
    private static List<?> subLevels(Object container) throws ReflectiveOperationException {
        return (List<?>) publicMethod(container, "getAllSubLevels").invoke(container);
    }

    private static BlockPos primaryAssembler(Object subLevel) throws ReflectiveOperationException {
        Class<?> extension = Class.forName(
                "dev.simulated_team.simulated.mixin_interface.assembly_preventer.PrimaryAssemblerExtension");
        if (!extension.isInstance(subLevel)) {
            fail("new ServerSubLevel lacks PrimaryAssemblerExtension");
        }
        return (BlockPos) extension.getMethod("simulated$getPrimaryAssembler").invoke(subLevel);
    }

    private static int countTranslatedEnvelope(ServerLevel level, BlockPos offset) {
        int count = 0;
        for (int x = -13; x <= 13; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = -8; z <= 6; z++) {
                    if (!level.getBlockState(rel(x, y, z).offset(offset)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BlockPos rel(int x, int y, int z) {
        return ORIGIN.offset(x, y, z);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("expected Number, got " + value);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
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
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C12_B0A_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException("Wave C12 B0-A assembly acceptance failed: " + reason);
    }

    private record BlockSpec(ResourceLocation id, Map<String, String> properties) {}
}
