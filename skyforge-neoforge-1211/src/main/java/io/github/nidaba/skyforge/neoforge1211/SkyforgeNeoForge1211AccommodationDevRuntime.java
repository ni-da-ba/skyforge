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
 * <p>The forced origin desert pyramid samples a high point near the start-chunk center while the
 * surrounding footprint falls smoothly toward a seven-block-lower asymptote. The neutral fixture
 * check proves the representative 21x21 footprint fails natural relief yet passes bounded
 * foundation feasibility before Minecraft is launched. Runtime admission then requires the real
 * native start to take the accommodation path; otherwise the development run fails loudly instead
 * of producing a false-positive visual proof.
 */
final class SkyforgeNeoForge1211AccommodationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.accommodation";
    static final long ROOT_SEED = 0x53464f554e443436L;
    static final int INSPECTION_X = 8;
    static final int INSPECTION_Y = 235;
    static final int INSPECTION_Z = 8;

    private static final double SURFACE_PEAK_Y = 224.0;
    private static final double SURFACE_RELIEF = 7.0;
    private static final double RELIEF_FALLOFF_RADIUS_SQUARED = 64.0;
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
                        + "Skyforge Development world type. Development data forces a minecraft:desert_pyramid start "
                        + "at the origin. Inspect near x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + ". The run is self-checking: it will throw if the real native start bypasses accommodation. "
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
                "sf-imp-0046-foundation-bowl",
                0,
                0,
                ROOT_SEED);
        SkyIslandWorldVolume volume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(-64.0, 96.0, 188.0, 225.0, -64.0, 96.0),
                compiledBowl());
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(volume));
    }

    private static void validateNeutralFixture() {
        SkyIslandWorldVolume volume = catalog().volumes().getFirst();
        SurfaceSupportRequirements natural = new SurfaceSupportRequirements(
                0.0, 20.0, 0.0, 20.0, 4.0, 2.0, 0.90, 0.50, 4.0);
        var naturalAssessment = new SkyIslandSurfaceSupportEvaluator().assess(volume, natural);
        if (naturalAssessment.accepted() || naturalAssessment.heightSpan() <= 4.0) {
            throw new IllegalStateException(
                    "SF-IMP-0046 neutral fixture must fail natural relief: " + naturalAssessment);
        }

        SurfaceFoundationRequirements foundation = new SurfaceFoundationRequirements(
                new SurfaceSupportRequirements(
                        0.0, 20.0, 0.0, 20.0, 1.0, 2.0, 1.0, 0.50, 12.0),
                SURFACE_PEAK_Y,
                8.0);
        var foundationAssessment = new SkyIslandSurfaceFoundationEvaluator().assess(volume, foundation);
        if (!foundationAssessment.accepted()
                || foundationAssessment.maximumRequiredFillDepth() > 8.0
                || !foundationAssessment.requiresFill()) {
            throw new IllegalStateException(
                    "SF-IMP-0046 neutral fixture must pass bounded fill accommodation: " + foundationAssessment);
        }
    }

    private static CompiledSkyIslandVolume compiledBowl() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                8.0,
                8.0,
                SURFACE_PEAK_Y,
                80.0,
                36.0,
                80.0,
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
                bowlSurfaceGraph(GraphValueType.SCALAR_FIELD_2, "upper"),
                constantGraph(GraphValueType.SCALAR_FIELD_2, 190.0, "underside"),
                densityGraph(),
                Map.of());
    }

    /** Peak at (8,8), smoothly approaching but never exceeding seven blocks of relief. */
    private static ProceduralGraph bowlSurfaceGraph(GraphValueType type, String prefix) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, prefix + "-x", type, CoordinateAxis.X);
        NodeId z = coordinate(nodes, prefix + "-z", type, CoordinateAxis.Z);
        NodeId center = constant(nodes, prefix + "-center", type, 8.0);
        NodeId dx = arithmetic(nodes, prefix + "-dx", type, ArithmeticOperator.SUBTRACT, x, center);
        NodeId dz = arithmetic(nodes, prefix + "-dz", type, ArithmeticOperator.SUBTRACT, z, center);
        NodeId dx2 = arithmetic(nodes, prefix + "-dx2", type, ArithmeticOperator.MULTIPLY, dx, dx);
        NodeId dz2 = arithmetic(nodes, prefix + "-dz2", type, ArithmeticOperator.MULTIPLY, dz, dz);
        NodeId radius2 = arithmetic(nodes, prefix + "-radius2", type, ArithmeticOperator.ADD, dx2, dz2);
        NodeId reliefCap = constant(nodes, prefix + "-relief", type, SURFACE_RELIEF);
        NodeId reliefNumerator = arithmetic(
                nodes,
                prefix + "-relief-numerator",
                type,
                ArithmeticOperator.MULTIPLY,
                radius2,
                reliefCap);
        NodeId falloff = constant(nodes, prefix + "-falloff", type, RELIEF_FALLOFF_RADIUS_SQUARED);
        NodeId reliefDenominator = arithmetic(
                nodes,
                prefix + "-relief-denominator",
                type,
                ArithmeticOperator.ADD,
                radius2,
                falloff);
        NodeId relief = arithmetic(
                nodes,
                prefix + "-bounded-relief",
                type,
                ArithmeticOperator.DIVIDE,
                reliefNumerator,
                reliefDenominator);
        NodeId peak = constant(nodes, prefix + "-peak", type, SURFACE_PEAK_Y);
        NodeId output = arithmetic(nodes, prefix + "-surface", type, ArithmeticOperator.SUBTRACT, peak, relief);
        return new ProceduralGraph(nodes, output);
    }

    private static ProceduralGraph densityGraph() {
        GraphValueType type = GraphValueType.SCALAR_FIELD_3;
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, "density-x", type, CoordinateAxis.X);
        NodeId y = coordinate(nodes, "density-y", type, CoordinateAxis.Y);
        NodeId z = coordinate(nodes, "density-z", type, CoordinateAxis.Z);

        NodeId center = constant(nodes, "density-center", type, 8.0);
        NodeId dx = arithmetic(nodes, "density-dx", type, ArithmeticOperator.SUBTRACT, x, center);
        NodeId dz = arithmetic(nodes, "density-dz", type, ArithmeticOperator.SUBTRACT, z, center);
        NodeId dx2 = arithmetic(nodes, "density-dx2", type, ArithmeticOperator.MULTIPLY, dx, dx);
        NodeId dz2 = arithmetic(nodes, "density-dz2", type, ArithmeticOperator.MULTIPLY, dz, dz);
        NodeId radius2 = arithmetic(nodes, "density-radius2", type, ArithmeticOperator.ADD, dx2, dz2);
        NodeId reliefCap = constant(nodes, "density-relief", type, SURFACE_RELIEF);
        NodeId reliefNumerator = arithmetic(
                nodes,
                "density-relief-numerator",
                type,
                ArithmeticOperator.MULTIPLY,
                radius2,
                reliefCap);
        NodeId falloff = constant(nodes, "density-falloff", type, RELIEF_FALLOFF_RADIUS_SQUARED);
        NodeId reliefDenominator = arithmetic(
                nodes,
                "density-relief-denominator",
                type,
                ArithmeticOperator.ADD,
                radius2,
                falloff);
        NodeId relief = arithmetic(
                nodes,
                "density-bounded-relief",
                type,
                ArithmeticOperator.DIVIDE,
                reliefNumerator,
                reliefDenominator);
        NodeId peak = constant(nodes, "density-peak", type, SURFACE_PEAK_Y);
        NodeId upper = arithmetic(nodes, "density-upper", type, ArithmeticOperator.SUBTRACT, peak, relief);
        NodeId upperGap = arithmetic(nodes, "density-upper-gap", type, ArithmeticOperator.SUBTRACT, upper, y);
        NodeId underside = constant(nodes, "density-underside", type, 190.0);
        NodeId lowerGap = arithmetic(nodes, "density-lower-gap", type, ArithmeticOperator.SUBTRACT, y, underside);
        NodeId support = intersect(nodes, "density-vertical", upperGap, lowerGap);

        NodeId minimumX = constant(nodes, "density-min-x", type, -64.0);
        NodeId maximumX = constant(nodes, "density-max-x", type, 96.0);
        NodeId minimumZ = constant(nodes, "density-min-z", type, -64.0);
        NodeId maximumZ = constant(nodes, "density-max-z", type, 96.0);
        support = intersect(nodes, "density-left", support,
                arithmetic(nodes, "density-left-gap", type, ArithmeticOperator.SUBTRACT, x, minimumX));
        support = intersect(nodes, "density-right", support,
                arithmetic(nodes, "density-right-gap", type, ArithmeticOperator.SUBTRACT, maximumX, x));
        support = intersect(nodes, "density-front", support,
                arithmetic(nodes, "density-front-gap", type, ArithmeticOperator.SUBTRACT, z, minimumZ));
        support = intersect(nodes, "density-back", support,
                arithmetic(nodes, "density-back-gap", type, ArithmeticOperator.SUBTRACT, maximumZ, z));
        return new ProceduralGraph(nodes, support);
    }

    private static ProceduralGraph constantGraph(GraphValueType type, double value, String name) {
        NodeId output = new NodeId(name);
        return new ProceduralGraph(List.of(new ConstantNode(output, type, value)), output);
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
