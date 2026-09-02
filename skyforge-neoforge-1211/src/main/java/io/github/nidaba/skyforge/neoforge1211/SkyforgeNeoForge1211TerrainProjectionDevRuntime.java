package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateAxis;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Development-only SF-IMP-0051 specimen for cross-volume terrain-matching projection. */
final class SkyforgeNeoForge1211TerrainProjectionDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.terrainProjection";
    static final long ROOT_SEED = 0x534650524f4a3531L;
    static final int VILLAGE_CHUNK_X = 32;
    static final int VILLAGE_CHUNK_Z = 0;
    static final int INSPECTION_X = 570;
    static final int INSPECTION_Y = 238;
    static final int INSPECTION_Z = 8;

    private static final double CENTER_X = 640.0;
    private static final double CENTER_Z = 8.0;
    private static final double UPPER_Y = 224.0;
    private static final double CENTER_UNDERSIDE_Y = 184.0;
    private static final double ISLAND_RADIUS = 96.0;
    private static final double ISLAND_RADIUS_SQUARED = ISLAND_RADIUS * ISLAND_RADIUS;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211TerrainProjectionDevRuntime.class.getName());
    private static final AtomicInteger CORRECTION_COUNT = new AtomicInteger();
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211TerrainProjectionDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "cannot install the SF-IMP-0051 terrain-projection specimen over an existing Skyforge binding");
        }
        validateFixture();
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0051 terrain-projection specimen enabled. Create a NEW disposable world using the "
                        + "Skyforge Development world type. A development structure set forces a lower vanilla plains "
                        + "village candidate at chunk (" + VILLAGE_CHUNK_X + "," + VILLAGE_CHUNK_Z + ") while a clean "
                        + "floating Skyforge island overlaps only the village outskirts in X/Z. Inspect near x="
                        + INSPECTION_X + ", y=" + INSPECTION_Y + ", z=" + INSPECTION_Z + ". The lower village should "
                        + "remain present, while terrain-matching paths or other projected village blocks must not "
                        + "appear on the upper island. Every proven correction emits 'SF-IMP-0051 TERRAIN PROJECTION SCOPED'.");
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static void recordCorrection(
            int worldX,
            int worldZ,
            int placementAnchorY,
            int vanillaTopY,
            int scopedTopY) {
        if (!enabled()) {
            return;
        }
        int count = CORRECTION_COUNT.incrementAndGet();
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0051 TERRAIN PROJECTION SCOPED: x=" + worldX
                        + ", z=" + worldZ
                        + ", placementAnchorY=" + placementAnchorY
                        + ", vanillaTopY=" + vanillaTopY
                        + ", scopedTopY=" + scopedTopY
                        + ", correctionCount=" + count);
    }

    static int correctionCount() {
        return CORRECTION_COUNT.get();
    }

    static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    static SkyIslandWorldCatalog catalog() {
        SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "sf-imp-0051-projection-island",
                0,
                0,
                ROOT_SEED);
        SkyIslandWorldVolume volume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(
                        CENTER_X - ISLAND_RADIUS,
                        CENTER_X + ISLAND_RADIUS,
                        CENTER_UNDERSIDE_Y - 1.0,
                        UPPER_Y + 1.0,
                        CENTER_Z - ISLAND_RADIUS,
                        CENTER_Z + ISLAND_RADIUS),
                compiledIsland());
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(volume));
    }

    private static void validateFixture() {
        SkyforgeNeoForge1211ChunkAdapter adapter = adapter();
        SkyIslandWorldVolumeId id = catalog().volumes().getFirst().id();
        if (!adapter.isSolidOwnedBy(id, (int) CENTER_X, 200, (int) CENTER_Z)) {
            throw new IllegalStateException("SF-IMP-0051 fixture center must contain Skyforge terrain");
        }
        int villageRootX = VILLAGE_CHUNK_X * 16 + 8;
        int villageRootZ = VILLAGE_CHUNK_Z * 16 + 8;
        if (adapter.isSolidOwnedBy(id, villageRootX, 200, villageRootZ)) {
            throw new IllegalStateException("SF-IMP-0051 forced village root must remain outside the upper island");
        }
        if (!adapter.isSolidOwnedBy(id, 560, 218, 8)) {
            throw new IllegalStateException("SF-IMP-0051 island must overlap the village-outskirts inspection corridor");
        }
    }

    private static CompiledSkyIslandVolume compiledIsland() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                CENTER_X,
                CENTER_Z,
                UPPER_Y,
                ISLAND_RADIUS,
                36.0,
                96.0,
                24.0,
                0.0,
                0.0,
                0.5,
                0.0,
                0.0,
                16.0);
        return new CompiledSkyIslandVolume(
                descriptor,
                1,
                1,
                constantGraph(GraphValueType.SCALAR_FIELD_2, "upper", UPPER_Y),
                undersideGraph(),
                densityGraph(),
                Map.of());
    }

    private static ProceduralGraph constantGraph(GraphValueType type, String name, double value) {
        NodeId output = new NodeId(name);
        return new ProceduralGraph(List.of(new ConstantNode(output, type, value)), output);
    }

    private static ProceduralGraph undersideGraph() {
        GraphValueType type = GraphValueType.SCALAR_FIELD_2;
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, "underside-x", type, CoordinateAxis.X);
        NodeId z = coordinate(nodes, "underside-z", type, CoordinateAxis.Z);
        NodeId underside = undersideBoundary(nodes, "underside", type, x, z);
        return new ProceduralGraph(nodes, underside);
    }

    private static ProceduralGraph densityGraph() {
        GraphValueType type = GraphValueType.SCALAR_FIELD_3;
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, "density-x", type, CoordinateAxis.X);
        NodeId y = coordinate(nodes, "density-y", type, CoordinateAxis.Y);
        NodeId z = coordinate(nodes, "density-z", type, CoordinateAxis.Z);
        NodeId upper = constant(nodes, "density-upper", type, UPPER_Y);
        NodeId underside = undersideBoundary(nodes, "density-underside", type, x, z);
        NodeId upperGap = arithmetic(nodes, "density-upper-gap", type, ArithmeticOperator.SUBTRACT, upper, y);
        NodeId lowerGap = arithmetic(nodes, "density-lower-gap", type, ArithmeticOperator.SUBTRACT, y, underside);
        NodeId vertical = intersect(nodes, "density-vertical", upperGap, lowerGap);
        NodeId radiusSquared = squaredRadius(nodes, "density-radius", type, x, z);
        NodeId radiusLimit = constant(nodes, "density-radius-limit", type, ISLAND_RADIUS_SQUARED);
        NodeId radial = arithmetic(nodes, "density-radial-gap", type, ArithmeticOperator.SUBTRACT, radiusLimit, radiusSquared);
        NodeId bounded = intersect(nodes, "density-bounded", vertical, radial);
        return new ProceduralGraph(nodes, bounded);
    }

    private static NodeId undersideBoundary(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z) {
        NodeId radiusSquared = squaredRadius(nodes, prefix + "-radius", type, x, z);
        NodeId radiusLimit = constant(nodes, prefix + "-radius-limit", type, ISLAND_RADIUS_SQUARED);
        NodeId normalized = arithmetic(nodes, prefix + "-normalized", type, ArithmeticOperator.DIVIDE, radiusSquared, radiusLimit);
        NodeId verticalRange = constant(nodes, prefix + "-vertical-range", type, UPPER_Y - CENTER_UNDERSIDE_Y);
        NodeId rise = arithmetic(nodes, prefix + "-rise", type, ArithmeticOperator.MULTIPLY, normalized, verticalRange);
        NodeId center = constant(nodes, prefix + "-center", type, CENTER_UNDERSIDE_Y);
        return arithmetic(nodes, prefix + "-surface", type, ArithmeticOperator.ADD, center, rise);
    }

    private static NodeId squaredRadius(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z) {
        NodeId centerX = constant(nodes, prefix + "-center-x", type, CENTER_X);
        NodeId centerZ = constant(nodes, prefix + "-center-z", type, CENTER_Z);
        NodeId dx = arithmetic(nodes, prefix + "-dx", type, ArithmeticOperator.SUBTRACT, x, centerX);
        NodeId dz = arithmetic(nodes, prefix + "-dz", type, ArithmeticOperator.SUBTRACT, z, centerZ);
        NodeId dx2 = arithmetic(nodes, prefix + "-dx2", type, ArithmeticOperator.MULTIPLY, dx, dx);
        NodeId dz2 = arithmetic(nodes, prefix + "-dz2", type, ArithmeticOperator.MULTIPLY, dz, dz);
        return arithmetic(nodes, prefix + "-r2", type, ArithmeticOperator.ADD, dx2, dz2);
    }

    private static NodeId coordinate(
            List<GraphNode> nodes,
            String name,
            GraphValueType type,
            CoordinateAxis axis) {
        NodeId id = new NodeId(name);
        nodes.add(new CoordinateNode(id, type, axis));
        return id;
    }

    private static NodeId constant(
            List<GraphNode> nodes,
            String name,
            GraphValueType type,
            double value) {
        NodeId id = new NodeId(name);
        nodes.add(new ConstantNode(id, type, value));
        return id;
    }

    private static NodeId arithmetic(
            List<GraphNode> nodes,
            String name,
            GraphValueType type,
            ArithmeticOperator operator,
            NodeId left,
            NodeId right) {
        NodeId id = new NodeId(name);
        nodes.add(new ArithmeticNode(id, type, operator, left, right));
        return id;
    }

    private static NodeId intersect(List<GraphNode> nodes, String name, NodeId left, NodeId right) {
        NodeId id = new NodeId(name);
        nodes.add(new IntersectionNode(id, left, right));
        return id;
    }
}
