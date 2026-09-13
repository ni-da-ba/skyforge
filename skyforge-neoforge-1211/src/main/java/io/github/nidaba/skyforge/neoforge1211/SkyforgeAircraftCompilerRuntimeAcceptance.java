package io.github.nidaba.skyforge.neoforge1211;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * AIRCRAFT-001 v0.12: consume the compiler's v0.9/v0.10/v0.11 outputs and execute the first
 * genuine exact-stack Sable assembly probe. This fixture intentionally remains opt-in and
 * development-only; ordinary packaged Skyforge never reads compiler build artifacts.
 */
final class SkyforgeAircraftCompilerRuntimeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftCompilerRuntime";
    static final String MANIFEST_PROPERTY = "skyforge.dev.aircraftCompilerManifest";
    static final String FIXTURE_PROPERTY = "skyforge.dev.aircraftCompilerAssemblyFixture";
    static final String GLUE_PROPERTY = "skyforge.dev.aircraftCompilerGlueEncoding";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerRuntimeAcceptance.class.getName());

    // Deliberately separated from the historical C12 specimen, which may run in the same server.
    private static final BlockPos BASE = new BlockPos(128, 220, 0);
    private static final int EXPECTED_MANIFEST_PLACEMENTS = 122;
    private static final int EXPECTED_MOVING_MAIN_BODY = 114;
    private static final int EXPECTED_NESTED_CHILD = 9;
    private static final int EXPECTED_GLUE_EDGES = 113;

    private SkyforgeAircraftCompilerRuntimeAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftCompilerRuntimeAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            JsonObject manifest = readJson(requirePathProperty(MANIFEST_PROPERTY));
            JsonObject fixture = readJson(requirePathProperty(FIXTURE_PROPERTY));
            JsonObject glueEncoding = readJson(requirePathProperty(GLUE_PROPERTY));
            runProbe(event.getServer(), event.getServer().overworld(), manifest, fixture, glueEncoding);
        } catch (IOException | ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            fail("runtime acceptance exception: " + cause);
        }
    }

    private static void runProbe(
            MinecraftServer server,
            ServerLevel level,
            JsonObject manifest,
            JsonObject fixture,
            JsonObject glueEncoding) throws ReflectiveOperationException {
        assertEquals("manifest schema", "aircraft-probe-placement-manifest-ir-0.9", string(manifest, "schemaVersion"));
        assertEquals("fixture schema", "aircraft-assembly-fixture-ir-0.10", string(fixture, "schemaVersion"));
        assertEquals("glue schema", "aircraft-glue-encoding-ir-0.11", string(glueEncoding, "schemaVersion"));

        LinkedHashMap<BlockPos, ExpectedBlock> manifestBlocks = readManifestBlocks(manifest);
        assertInt("manifest placement count", EXPECTED_MANIFEST_PLACEMENTS, manifestBlocks.size());

        Set<BlockPos> mainCoordinates = readCoordinateSet(fixture.getAsJsonObject("mainBody"), "coordinates");
        Set<BlockPos> childCoordinates = readCoordinateSet(fixture.getAsJsonObject("nestedPropellerChild"), "coordinates");
        assertInt("moving main-body coordinate count", EXPECTED_MOVING_MAIN_BODY, mainCoordinates.size());
        assertInt("nested child coordinate count", EXPECTED_NESTED_CHILD, childCoordinates.size());

        JsonObject assemblerJson = fixture.getAsJsonObject("physicsAssemblerPlacement");
        BlockPos assemblerRelative = blockPos(assemblerJson.getAsJsonArray("lattice"));
        ExpectedBlock assemblerExpected = expectedBlock(assemblerJson);
        assertTrue("assembler is declared moving main-body member", mainCoordinates.contains(assemblerRelative));
        assertTrue("assembler is not in nested child", !childCoordinates.contains(assemblerRelative));

        LinkedHashMap<BlockPos, ExpectedBlock> movingExpected = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, ExpectedBlock> childExpected = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, ExpectedBlock> entry : manifestBlocks.entrySet()) {
            if (mainCoordinates.contains(entry.getKey())) {
                movingExpected.put(entry.getKey(), entry.getValue());
            } else if (childCoordinates.contains(entry.getKey())) {
                childExpected.put(entry.getKey(), entry.getValue());
            } else {
                fail("manifest coordinate not classified by v0.10 fixture: " + entry.getKey());
            }
        }
        ExpectedBlock collision = movingExpected.putIfAbsent(assemblerRelative, assemblerExpected);
        if (collision != null && !collision.equals(assemblerExpected)) {
            fail("assembler fixture collides with manifest placement at " + assemblerRelative);
        }
        assertInt("moving main-body expected block count", EXPECTED_MOVING_MAIN_BODY, movingExpected.size());
        assertInt("nested child expected block count", EXPECTED_NESTED_CHILD, childExpected.size());

        prepareAirspace(level, movingExpected.keySet(), childExpected.keySet());
        for (Map.Entry<BlockPos, ExpectedBlock> entry : manifestBlocks.entrySet()) {
            level.setBlock(BASE.offset(entry.getKey()), materialize(entry.getValue()), 3);
        }
        level.setBlock(BASE.offset(assemblerRelative), materialize(assemblerExpected), 3);

        // Runtime registry check for the exact privileged command v0.11 encodes.
        var createNode = server.getCommands().getDispatcher().getRoot().getChild("create");
        assertTrue("/create command root registered", createNode != null);
        assertTrue("/create glue command registered", createNode.getChild("glue") != null);

        JsonArray glueEntitiesJson = glueEncoding.getAsJsonArray("glueEntities");
        assertInt("encoded glue edge count", EXPECTED_GLUE_EDGES, glueEntitiesJson.size());
        List<Entity> insertedGlue = new ArrayList<>();
        for (JsonElement raw : glueEntitiesJson) {
            JsonObject edge = raw.getAsJsonObject();
            BlockPos a = blockPos(edge.getAsJsonArray("a"));
            BlockPos b = blockPos(edge.getAsJsonArray("b"));
            assertTrue("glue endpoint a belongs to moving body " + a, mainCoordinates.contains(a));
            assertTrue("glue endpoint b belongs to moving body " + b, mainCoordinates.contains(b));
            assertInt("glue edge Manhattan distance " + a + " -> " + b, 1, manhattan(a, b));
            insertedGlue.add(addGlueEdge(level, a, b));
        }
        assertInt("runtime SuperGlueEntity insertion count", EXPECTED_GLUE_EDGES, insertedGlue.size());
        for (BlockPos child : childCoordinates) {
            Vec3 center = Vec3.atCenterOf(BASE.offset(child));
            for (Entity glue : insertedGlue) {
                assertTrue("no main-body glue volume contains nested child center " + child,
                        !glue.getBoundingBox().contains(center));
            }
        }

        Object container = requireServerSubLevelContainer(level);
        List<?> before = new ArrayList<>(getAllSubLevels(container));

        BlockPos assemblerWorldPos = BASE.offset(assemblerRelative);
        BlockEntity assembler = level.getBlockEntity(assemblerWorldPos);
        if (assembler == null || !assembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity")) {
            fail("Physics Assembler block entity missing or wrong type before assembly at " + assemblerWorldPos);
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

        int movedCount = 0;
        for (Map.Entry<BlockPos, ExpectedBlock> entry : movingExpected.entrySet()) {
            BlockPos source = BASE.offset(entry.getKey());
            BlockPos moved = source.offset(offset);
            assertTrue("moving-body source position vacated " + source, level.getBlockState(source).isAir());
            assertExpectedState("moved " + entry.getKey(), level.getBlockState(moved), entry.getValue());
            movedCount++;
        }
        assertInt("exact moving main-body block count", EXPECTED_MOVING_MAIN_BODY, movedCount);

        int childCount = 0;
        for (Map.Entry<BlockPos, ExpectedBlock> entry : childExpected.entrySet()) {
            BlockPos source = BASE.offset(entry.getKey());
            assertExpectedState("nested child remains outside main Sable body " + entry.getKey(),
                    level.getBlockState(source), entry.getValue());
            childCount++;
        }
        assertInt("exact nested child block count left in world", EXPECTED_NESTED_CHILD, childCount);

        assertTrue("live Sable mass finite", Double.isFinite(mass));
        assertTrue("live Sable mass positive", mass > 0.0);
        double localComX = comX - offsetX - BASE.getX();
        double localComY = comY - offsetY - BASE.getY();
        double localComZ = comZ - offsetZ - BASE.getZ();
        assertWithinCoordinateBounds("local COM X", localComX, movingExpected.keySet(), 0);
        assertWithinCoordinateBounds("local COM Y", localComY, movingExpected.keySet(), 1);
        assertWithinCoordinateBounds("local COM Z", localComZ, movingExpected.keySet(), 2);

        // The assembler must have moved with the body. This is the source-driven correction that
        // turns the fixture into a reversible assembly lifecycle rather than an external one-shot.
        assertTrue("assembler source vacated with moving body", level.getBlockState(assemblerWorldPos).isAir());
        BlockEntity movedAssembler = level.getBlockEntity(assemblerWorldPos.offset(offset));
        assertTrue("assembler block entity exists on moved Sable body", movedAssembler != null
                && movedAssembler.getClass().getName().endsWith("PhysicsAssemblerBlockEntity"));

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_ASSEMBLY PASS"
                        + " movedMainBody=" + movedCount
                        + " nestedChildStatic=" + childCount
                        + " glueEdges=" + insertedGlue.size()
                        + " massKpg=" + mass
                        + " localComX=" + localComX
                        + " localComY=" + localComY
                        + " localComZ=" + localComZ
                        + " offset=" + offset
                        + " assemblerMoved=true"
                        + " createGlueRegistered=true");
    }

    private static Path requirePathProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            fail("missing required system property " + name);
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            fail("required compiler artifact does not exist: " + path);
        }
        return path;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static LinkedHashMap<BlockPos, ExpectedBlock> readManifestBlocks(JsonObject manifest) {
        LinkedHashMap<BlockPos, ExpectedBlock> result = new LinkedHashMap<>();
        for (JsonElement raw : manifest.getAsJsonArray("placements")) {
            JsonObject placement = raw.getAsJsonObject();
            BlockPos pos = blockPos(placement.getAsJsonArray("lattice"));
            ExpectedBlock expected = expectedBlock(placement);
            ExpectedBlock existing = result.putIfAbsent(pos, expected);
            if (existing != null) {
                fail("duplicate manifest coordinate " + pos);
            }
        }
        return result;
    }

    private static ExpectedBlock expectedBlock(JsonObject placement) {
        ResourceLocation id = id(string(placement, "resourceId"));
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        JsonObject rawState = placement.getAsJsonObject("blockState");
        if (rawState != null) {
            for (Map.Entry<String, JsonElement> property : rawState.entrySet()) {
                properties.put(property.getKey(), property.getValue().getAsString());
            }
        }
        return new ExpectedBlock(id, Map.copyOf(properties));
    }

    private static Set<BlockPos> readCoordinateSet(JsonObject owner, String field) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (JsonElement raw : owner.getAsJsonArray(field)) {
            BlockPos pos = blockPos(raw.getAsJsonArray());
            if (!result.add(pos)) {
                fail("duplicate coordinate in " + field + ": " + pos);
            }
        }
        return result;
    }

    private static BlockPos blockPos(JsonArray values) {
        if (values == null || values.size() != 3) {
            fail("expected three-element lattice coordinate, got " + values);
        }
        return new BlockPos(values.get(0).getAsInt(), values.get(1).getAsInt(), values.get(2).getAsInt());
    }

    private static void prepareAirspace(ServerLevel level, Set<BlockPos> main, Set<BlockPos> child) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : concat(main, child)) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int worldMinX = BASE.getX() + minX - 3;
        int worldMaxX = BASE.getX() + maxX + 3;
        int worldMinZ = BASE.getZ() + minZ - 3;
        int worldMaxZ = BASE.getZ() + maxZ + 3;
        for (int chunkX = Math.floorDiv(worldMinX, 16); chunkX <= Math.floorDiv(worldMaxX, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(worldMinZ, 16); chunkZ <= Math.floorDiv(worldMaxZ, 16); chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        for (int x = minX - 3; x <= maxX + 3; x++) {
            for (int y = minY - 3; y <= maxY + 3; y++) {
                for (int z = minZ - 3; z <= maxZ + 3; z++) {
                    level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static List<BlockPos> concat(Set<BlockPos> a, Set<BlockPos> b) {
        List<BlockPos> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }

    private static Entity addGlueEdge(ServerLevel level, BlockPos relativeA, BlockPos relativeB)
            throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        Method span = glueClass.getMethod("span", BlockPos.class, BlockPos.class);
        BlockPos worldA = BASE.offset(relativeA);
        BlockPos worldB = BASE.offset(relativeB);
        AABB box = (AABB) span.invoke(null, worldA, worldB);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        assertTrue("Create Super Glue edge inserted " + relativeA + " -> " + relativeB, level.addFreshEntity(glue));
        assertTrue("glue contains endpoint A", glue.getBoundingBox().contains(Vec3.atCenterOf(worldA)));
        assertTrue("glue contains endpoint B", glue.getBoundingBox().contains(Vec3.atCenterOf(worldB)));
        return glue;
    }

    private static Object requireServerSubLevelContainer(ServerLevel level)
            throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(level)) {
            fail("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Method getPlotContainer = holderClass.getDeclaredMethod("sable$getPlotContainer");
        Object container = getPlotContainer.invoke(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (container == null || !serverContainerClass.isInstance(container)) {
            fail("Sable ServerSubLevelContainer unavailable");
        }
        return container;
    }

    private static List<?> getAllSubLevels(Object container) throws ReflectiveOperationException {
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Method getAll = serverContainerClass.getDeclaredMethod("getAllSubLevels");
        Object value = getAll.invoke(container);
        if (!(value instanceof List<?> list)) {
            fail("Sable getAllSubLevels did not return a List");
            throw new AssertionError();
        }
        return list;
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
            fail("required compiler block is not registered: " + expected.id());
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
                fail(label + " property " + property.getKey() + " expected=" + property.getValue() + " actual=" + actual);
            }
        }
    }

    private static BlockState withManifestProperty(BlockState state, String name, String value) {
        Property<?> property = findProperty(state, name);
        return withParsedProperty(state, property, name, value);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElse(null);
        if (parsed == null) {
            fail("cannot parse block property " + name + "=" + value + " on " + state);
        }
        return state.setValue(property, parsed);
    }

    private static String readManifestProperty(BlockState state, String name) {
        Property<?> property = findProperty(state, name);
        return readProperty(state, property);
    }

    private static <T extends Comparable<T>> String readProperty(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static Property<?> findProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        fail("missing property " + name + " on " + state);
        throw new AssertionError();
    }

    private static void assertWithinCoordinateBounds(
            String label, double value, Set<BlockPos> positions, int axis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            int coordinate = axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }
        assertTrue(label + " finite/in moving-body bounds",
                Double.isFinite(value) && value >= min && value <= max + 1.0);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static int exactIntegerOffset(String axis, double value) {
        int rounded = (int) Math.round(value);
        assertNear("assembly " + axis + " offset is integral", rounded, value, 0.000001);
        return rounded;
    }

    private static double component(Object vector, String methodName) throws ReflectiveOperationException {
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

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            fail("missing string field " + field);
        }
        return value.getAsString();
    }

    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) {
            fail("invalid resource location " + value);
        }
        return result;
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            fail(label + ": expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_ASSEMBLY FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 runtime assembly acceptance failed: " + reason);
    }

    private record ExpectedBlock(ResourceLocation id, Map<String, String> properties) {}
}
