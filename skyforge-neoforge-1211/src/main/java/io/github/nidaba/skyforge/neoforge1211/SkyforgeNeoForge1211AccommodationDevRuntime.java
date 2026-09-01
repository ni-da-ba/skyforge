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
import io.github.nidaba.skyforge.world.SkyIslandSurfaceFoundationEvaluator;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSupportEvaluator;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Development-only SF-IMP-0046 specimen designed to exercise fill-only structure accommodation.
 *
 * <p>The specimen is one bounded floating mesa containing four shallow pockets away from the
 * origin chunk's terrain-query neighborhood. Development data forces a woodland mansion at chunk
 * (0,0). Unlike scattered-feature pieces that defer their final Y until placement, the mansion
 * resolves its generated pieces from its terrain-derived start position during STRUCTURE_STARTS.
 * The broad top therefore gives us a truthful start-time floor while the pockets force the actual
 * mansion footprint to fail natural relief and pass bounded fill-only accommodation. The lower
 * surface rises toward a circular edge so this proof remains visibly island-like rather than a
 * giant rectangular slab.
 */
final class SkyforgeNeoForge1211AccommodationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.accommodation";
    static final long ROOT_SEED = 0x53464f554e443436L;
    static final int INSPECTION_X = 8;
    static final int INSPECTION_Y = 242;
    static final int INSPECTION_Z = 8;

    private static final double SURFACE_PLATEAU_Y = 224.0;
    private static final double SURFACE_RELIEF = 6.5;
    private static final double POCKET_FALLOFF_RADIUS_SQUARED = 16.0;
    private static final double[] POCKET_COORDINATES = {-16.0, 32.0};
    private static final double CENTER_UNDERSIDE_Y = 184.0;
    private static final double ISLAND_RADIUS = 120.0;
    private static final double ISLAND_RADIUS_SQUARED = ISLAND_RADIUS * ISLAND_RADIUS;
    private static final double WORLD_MINIMUM_XZ = -ISLAND_RADIUS;
    private static final double WORLD_MAXIMUM_XZ = ISLAND_RADIUS;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211AccommodationDevRuntime.class.getName());
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211AccommodationDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "cannot install the SF-IMP-0046 accommodation specimen over an existing Skyforge binding");
        }

        validateNeutralFixture();
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0046 accommodation specimen enabled. Create a NEW disposable world using the "
                        + "Skyforge Development world type. Development data forces one nearby minecraft:mansion "
                        + "candidate at chunk (0,0); the next forced candidate is over 8,000 blocks away. Inspect "
                        + "near x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + ". The run is self-checking: it will throw unless the real mansion start is resolved at "
                        + "the Skyforge surface, fails natural relief, and accepts bounded fill accommodation. "
                        + "A successful candidate emits the marker 'SF-IMP-0046 FOUNDATION ATTACHED'.");
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static void requireNaturalRejection(BoundingBox actualStartBounds) {
        if (enabled()) {
            throw new IllegalStateException(
                    "SF-IMP-0046 fixture invalid: forced native start was naturally accepted instead of requiring "
                            + "foundation accommodation; bounds=" + actualStartBounds);
        }
    }

    static void requireFoundationAcceptance(BoundingBox actualStartBounds) {
        if (enabled()) {
            throw new IllegalStateException(
                    "SF-IMP-0046 fixture invalid: forced native start failed bounded foundation accommodation; bounds="
                            + actualStartBounds);
        }
    }

    static void recordFoundationAttached(
            BoundingBox actualStartBounds,
            SkyIslandWorldVolumeId volumeId,
            double requiredFillDepth) {
        if (!enabled()) {
            return;
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0046 FOUNDATION ATTACHED: structureBounds=" + actualStartBounds
                        + ", volume=" + volumeId.path()
                        + ", maximumRequiredFillDepth=" + requiredFillDepth);
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
                "sf-imp-0046-foundation-island",
                0,
                0,
                ROOT_SEED);
        SkyIslandWorldVolume volume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(
                        WORLD_MINIMUM_XZ,
                        WORLD_MAXIMUM_XZ,
                        CENTER_UNDERSIDE_Y - 2.0,
                        SURFACE_PLATEAU_Y + 1.0,
                        WORLD_MINIMUM_XZ,
                        WORLD_MAXIMUM_XZ),
                compiledIsland());
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(volume));
    }

    private static void validateNeutralFixture() {
        SkyIslandWorldVolume volume = catalog().volumes().getFirst();
        SurfaceSupportRequirements natural = new SurfaceSupportRequirements(
                -24.0, 40.0, -24.0, 40.0, 4.0, 2.0, 0.90, 0.50, 4.0);
        var naturalAssessment = new SkyIslandSurfaceSupportEvaluator().assess(volume, natural);
        if (naturalAssessment.accepted() || naturalAssessment.heightSpan() <= 4.0) {
            throw new IllegalStateException(
                    "SF-IMP-0046 neutral fixture must fail natural relief: " + naturalAssessment);
        }

        SurfaceFoundationRequirements foundation = new SurfaceFoundationRequirements(
                new SurfaceSupportRequirements(
                        -24.0, 40.0, -24.0, 40.0, 1.0, 2.0, 1.0, 0.50, 12.0),
                SURFACE_PLATEAU_Y - 1.0,
                SURFACE_PLATEAU_Y,
                8.0);
        var foundationAssessment = new SkyIslandSurfaceFoundationEvaluator().assess(volume, foundation);
        if (!foundationAssessment.accepted()
                || foundationAssessment.maximumRequiredFillDepth() > 8.0
                || !foundationAssessment.requiresFill()) {
            throw new IllegalStateException(
                    "SF-IMP-0046 neutral fixture must pass bounded fill accommodation: " + foundationAssessment);
        }
    }

    private static CompiledSkyIslandVolume compiledIsland() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                8.0,
                8.0,
                SURFACE_PLATEAU_Y,
                160.0,
                36.0,
                160.0,
                36.0,
                0.0,
                0.5,
                0.5,
                0.0,
                0.0,
                16.0);
        return new CompiledSkyIslandVolume(
                descriptor,
                1,
                1,
                plateauSurfaceGraph(GraphValueType.SCALAR_FIELD_2, "upper"),
                taperedUndersideGraph(GraphValueType.SCALAR_FIELD_2, "underside"),
                densityGraph(),
                Map.of());
    }

    /** Broad top with four shallow localized pockets centered away from the origin chunk. */
    private static ProceduralGraph plateauSurfaceGraph(GraphValueType type, String prefix) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, prefix + "-x", type, CoordinateAxis.X);
        NodeId z = coordinate(nodes, prefix + "-z", type, CoordinateAxis.Z);
        NodeId upper = plateauUpperBoundary(nodes, prefix, type, x, z);
        return new ProceduralGraph(nodes, upper);
    }

    /** Lower surface rises quadratically toward the circular edge, producing a tapered island body. */
    private static ProceduralGraph taperedUndersideGraph(GraphValueType type, String prefix) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, prefix + "-x", type, CoordinateAxis.X);
        NodeId z = coordinate(nodes, prefix + "-z", type, CoordinateAxis.Z);
        NodeId underside = taperedUndersideBoundary(nodes, prefix, type, x, z);
        return new ProceduralGraph(nodes, underside);
    }

    private static ProceduralGraph densityGraph() {
        GraphValueType type = GraphValueType.SCALAR_FIELD_3;
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, "density-x", type, CoordinateAxis.X);
        NodeId y = coordinate(nodes, "density-y", type, CoordinateAxis.Y);
        NodeId z = coordinate(nodes, "density-z", type, CoordinateAxis.Z);

        NodeId upper = plateauUpperBoundary(nodes, "density", type, x, z);
        NodeId underside = taperedUndersideBoundary(nodes, "density-underside", type, x, z);
        NodeId upperGap = arithmetic(nodes, "density-upper-gap", type, ArithmeticOperator.SUBTRACT, upper, y);
        NodeId lowerGap = arithmetic(nodes, "density-lower-gap", type, ArithmeticOperator.SUBTRACT, y, underside);
        NodeId support = intersect(nodes, "density-vertical", upperGap, lowerGap);

        NodeId radiusSquared = squaredHorizontalRadius(nodes, "density-edge", type, x, z);
        NodeId radiusLimit = constant(nodes, "density-edge-radius", type, ISLAND_RADIUS_SQUARED);
        NodeId radialGap = arithmetic(
                nodes,
                "density-edge-gap",
                type,
                ArithmeticOperator.SUBTRACT,
                radiusLimit,
                radiusSquared);
        support = intersect(nodes, "density-bounded-island", support, radialGap);
        return new ProceduralGraph(nodes, support);
    }

    private static NodeId plateauUpperBoundary(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z) {
        NodeId totalRelief = null;
        int index = 0;
        for (double centerX : POCKET_COORDINATES) {
            for (double centerZ : POCKET_COORDINATES) {
                NodeId relief = depression(nodes, prefix + "-pocket-" + index, type, x, z, centerX, centerZ);
                totalRelief = totalRelief == null
                        ? relief
                        : arithmetic(
                                nodes,
                                prefix + "-relief-sum-" + index,
                                type,
                                ArithmeticOperator.ADD,
                                totalRelief,
                                relief);
                index++;
            }
        }
        NodeId plateau = constant(nodes, prefix + "-plateau", type, SURFACE_PLATEAU_Y);
        return arithmetic(nodes, prefix + "-surface", type, ArithmeticOperator.SUBTRACT, plateau, totalRelief);
    }

    private static NodeId taperedUndersideBoundary(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z) {
        NodeId radiusSquared = squaredHorizontalRadius(nodes, prefix + "-radius", type, x, z);
        NodeId radiusLimit = constant(nodes, prefix + "-radius-limit", type, ISLAND_RADIUS_SQUARED);
        NodeId normalizedRadius = arithmetic(
                nodes,
                prefix + "-normalized-radius",
                type,
                ArithmeticOperator.DIVIDE,
                radiusSquared,
                radiusLimit);
        NodeId verticalRange = constant(
                nodes,
                prefix + "-vertical-range",
                type,
                SURFACE_PLATEAU_Y - CENTER_UNDERSIDE_Y);
        NodeId rise = arithmetic(
                nodes,
                prefix + "-rise",
                type,
                ArithmeticOperator.MULTIPLY,
                normalizedRadius,
                verticalRange);
        NodeId center = constant(nodes, prefix + "-center", type, CENTER_UNDERSIDE_Y);
        return arithmetic(nodes, prefix + "-surface", type, ArithmeticOperator.ADD, center, rise);
    }

    private static NodeId squaredHorizontalRadius(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z) {
        NodeId x2 = arithmetic(nodes, prefix + "-x2", type, ArithmeticOperator.MULTIPLY, x, x);
        NodeId z2 = arithmetic(nodes, prefix + "-z2", type, ArithmeticOperator.MULTIPLY, z, z);
        return arithmetic(nodes, prefix + "-r2", type, ArithmeticOperator.ADD, x2, z2);
    }

    /** Rational compact-looking pocket: depth approaches zero rapidly without a type-specific clamp node. */
    private static NodeId depression(
            List<GraphNode> nodes,
            String prefix,
            GraphValueType type,
            NodeId x,
            NodeId z,
            double centerX,
            double centerZ) {
        NodeId cx = constant(nodes, prefix + "-cx", type, centerX);
        NodeId cz = constant(nodes, prefix + "-cz", type, centerZ);
        NodeId dx = arithmetic(nodes, prefix + "-dx", type, ArithmeticOperator.SUBTRACT, x, cx);
        NodeId dz = arithmetic(nodes, prefix + "-dz", type, ArithmeticOperator.SUBTRACT, z, cz);
        NodeId dx2 = arithmetic(nodes, prefix + "-dx2", type, ArithmeticOperator.MULTIPLY, dx, dx);
        NodeId dz2 = arithmetic(nodes, prefix + "-dz2", type, ArithmeticOperator.MULTIPLY, dz, dz);
        NodeId radius2 = arithmetic(nodes, prefix + "-radius2", type, ArithmeticOperator.ADD, dx2, dz2);
        NodeId falloff = constant(nodes, prefix + "-falloff", type, POCKET_FALLOFF_RADIUS_SQUARED);
        NodeId denominator = arithmetic(
                nodes,
                prefix + "-denominator",
                type,
                ArithmeticOperator.ADD,
                radius2,
                falloff);
        NodeId relief = constant(nodes, prefix + "-relief", type, SURFACE_RELIEF);
        NodeId numerator = arithmetic(
                nodes,
                prefix + "-numerator",
                type,
                ArithmeticOperator.MULTIPLY,
                relief,
                falloff);
        return arithmetic(nodes, prefix + "-depth", type, ArithmeticOperator.DIVIDE, numerator, denominator);
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
