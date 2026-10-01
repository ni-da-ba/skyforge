package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Deterministic proof for the SF-IMP-0046 fill-only accommodation contract. */
final class SkyIslandSurfaceFoundationEvaluatorTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private final SkyIslandSurfaceFoundationEvaluator evaluator = new SkyIslandSurfaceFoundationEvaluator();

    @Test
    void acceptsBoundedFillBelowFoundationWithoutChangingSurface() {
        SkyIslandWorldVolume volume = rectangularVolume("slope", 31L, 0.0, 10.0, 0.0, 10.0, 100.0, 1.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(2.0, 8.0, 2.0, 8.0, 110.0, 8.0));

        assertEquals(1.0, assessment.supportAssessment().coverageFraction());
        assertEquals(6.0, assessment.supportAssessment().heightSpan());
        assertEquals(16, assessment.fillSampleCount());
        assertEquals(0, assessment.surfaceAboveFoundationSampleCount());
        assertEquals(8.0, assessment.maximumRequiredFillDepth());
        assertTrue(assessment.accepted());
    }

    @Test
    void acceptsAuthorizedNativeSurfaceOverlapWithoutInflatingFillDepth() {
        SkyIslandWorldVolume volume = rectangularVolume("native-overlap", 36L, 0.0, 10.0, 0.0, 10.0, 100.0, 1.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(2.0, 8.0, 2.0, 8.0, 107.0, 108.0, 8.0));

        assertEquals(0, assessment.surfaceAboveFoundationSampleCount());
        assertEquals(12, assessment.fillSampleCount());
        assertEquals(5.0, assessment.maximumRequiredFillDepth());
        assertTrue(assessment.accepted());
    }

    @Test
    void rejectsAccommodationThatWouldRequireExcavation() {
        SkyIslandWorldVolume volume = rectangularVolume("slope", 32L, 0.0, 10.0, 0.0, 10.0, 100.0, 1.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(2.0, 8.0, 2.0, 8.0, 105.0, 8.0));

        assertTrue(assessment.surfaceAboveFoundationSampleCount() > 0);
        assertFalse(assessment.accepted());
    }

    @Test
    void rejectsAccommodationDeeperThanExplicitFillBound() {
        SkyIslandWorldVolume volume = rectangularVolume("slope", 33L, 0.0, 10.0, 0.0, 10.0, 100.0, 1.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(2.0, 8.0, 2.0, 8.0, 110.0, 7.0));

        assertEquals(8.0, assessment.maximumRequiredFillDepth());
        assertFalse(assessment.accepted());
    }

    @Test
    void rejectsIslandEdgeInsteadOfBridgingUnsupportedSpace() {
        SkyIslandWorldVolume volume = rectangularVolume("edge", 34L, 0.0, 10.0, 0.0, 10.0, 100.0, 0.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(8.0, 12.0, 2.0, 8.0, 104.0, 8.0));

        assertTrue(assessment.supportAssessment().coverageFraction() < 1.0);
        assertEquals(0, assessment.fillSampleCount());
        assertFalse(assessment.accepted());
    }

    @Test
    void rejectsNoOpFoundationWhenNaturalSurfaceAlreadyMeetsTop() {
        SkyIslandWorldVolume volume = rectangularVolume("flat", 35L, 0.0, 10.0, 0.0, 10.0, 100.0, 0.0);
        SurfaceFoundationAssessment assessment = evaluator.assess(
                volume,
                foundationRequirements(2.0, 8.0, 2.0, 8.0, 100.0, 8.0));

        assertEquals(0, assessment.fillSampleCount());
        assertFalse(assessment.requiresFill());
        assertFalse(assessment.accepted());
    }

    private static SurfaceFoundationRequirements foundationRequirements(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double foundationTopY,
            double maximumFillDepth) {
        return foundationRequirements(
                minimumX,
                maximumX,
                minimumZ,
                maximumZ,
                foundationTopY,
                foundationTopY,
                maximumFillDepth);
    }

    private static SurfaceFoundationRequirements foundationRequirements(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double foundationTopY,
            double maximumSurfaceY,
            double maximumFillDepth) {
        return new SurfaceFoundationRequirements(
                new SurfaceSupportRequirements(
                        minimumX,
                        maximumX,
                        minimumZ,
                        maximumZ,
                        2.0,
                        0.0,
                        1.0,
                        0.0,
                        12.0),
                foundationTopY,
                maximumSurfaceY,
                maximumFillDepth);
    }

    private static SkyIslandWorldVolume rectangularVolume(
            String name,
            long seed,
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double baseUpper,
            double slopeX) {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                baseUpper,
                64.0,
                20.0,
                20.0,
                8.0,
                0.0,
                0.5,
                0.5,
                0.0,
                0.0,
                16.0);
        CompiledSkyIslandVolume compiled = new CompiledSkyIslandVolume(
                descriptor,
                1,
                1,
                planarGraph(baseUpper, slopeX, "upper"),
                planarGraph(baseUpper - 20.0, slopeX, "underside"),
                densityGraph(minimumX, maximumX, minimumZ, maximumZ, baseUpper, slopeX),
                Map.of());
        double surfaceAtMinimumX = baseUpper + slopeX * minimumX;
        double surfaceAtMaximumX = baseUpper + slopeX * maximumX;
        double minimumSurface = Math.min(surfaceAtMinimumX, surfaceAtMaximumX);
        double maximumSurface = Math.max(surfaceAtMinimumX, surfaceAtMaximumX);
        SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(ROOT_SEED, name, 0, 0, seed);
        return new SkyIslandWorldVolume(
                id,
                new WorldBounds(
                        minimumX,
                        maximumX,
                        minimumSurface - 21.0,
                        maximumSurface + 1.0,
                        minimumZ,
                        maximumZ),
                compiled);
    }

    private static ProceduralGraph planarGraph(double base, double slopeX, String prefix) {
        NodeId x = new NodeId(prefix + "-x");
        NodeId slope = new NodeId(prefix + "-slope");
        NodeId scaledX = new NodeId(prefix + "-scaled-x");
        NodeId offset = new NodeId(prefix + "-offset");
        NodeId output = new NodeId(prefix + "-output");
        return new ProceduralGraph(
                List.of(
                        new CoordinateNode(x, GraphValueType.SCALAR_FIELD_2, CoordinateAxis.X),
                        new ConstantNode(slope, GraphValueType.SCALAR_FIELD_2, slopeX),
                        new ArithmeticNode(
                                scaledX,
                                GraphValueType.SCALAR_FIELD_2,
                                ArithmeticOperator.MULTIPLY,
                                x,
                                slope),
                        new ConstantNode(offset, GraphValueType.SCALAR_FIELD_2, base),
                        new ArithmeticNode(
                                output,
                                GraphValueType.SCALAR_FIELD_2,
                                ArithmeticOperator.ADD,
                                offset,
                                scaledX)),
                output);
    }

    private static ProceduralGraph densityGraph(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double baseUpper,
            double slopeX) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = new NodeId("density-x");
        NodeId y = new NodeId("density-y");
        NodeId z = new NodeId("density-z");
        nodes.add(new CoordinateNode(x, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.X));
        nodes.add(new CoordinateNode(y, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Y));
        nodes.add(new CoordinateNode(z, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Z));

        NodeId slope = constant(nodes, "density-slope", slopeX);
        NodeId scaledX = arithmetic(nodes, "density-scaled-x", ArithmeticOperator.MULTIPLY, x, slope);
        NodeId upperBase = constant(nodes, "density-upper-base", baseUpper);
        NodeId upper = arithmetic(nodes, "density-upper", ArithmeticOperator.ADD, upperBase, scaledX);
        NodeId upperGap = arithmetic(nodes, "density-upper-gap", ArithmeticOperator.SUBTRACT, upper, y);
        NodeId undersideBase = constant(nodes, "density-underside-base", baseUpper - 20.0);
        NodeId underside = arithmetic(nodes, "density-underside", ArithmeticOperator.ADD, undersideBase, scaledX);
        NodeId lowerGap = arithmetic(nodes, "density-lower-gap", ArithmeticOperator.SUBTRACT, y, underside);
        NodeId support = intersect(nodes, "density-vertical", upperGap, lowerGap);

        NodeId minimumXNode = constant(nodes, "density-minimum-x", minimumX);
        NodeId maximumXNode = constant(nodes, "density-maximum-x", maximumX);
        NodeId minimumZNode = constant(nodes, "density-minimum-z", minimumZ);
        NodeId maximumZNode = constant(nodes, "density-maximum-z", maximumZ);
        NodeId left = arithmetic(nodes, "density-left", ArithmeticOperator.SUBTRACT, x, minimumXNode);
        NodeId right = arithmetic(nodes, "density-right", ArithmeticOperator.SUBTRACT, maximumXNode, x);
        NodeId front = arithmetic(nodes, "density-front", ArithmeticOperator.SUBTRACT, z, minimumZNode);
        NodeId back = arithmetic(nodes, "density-back", ArithmeticOperator.SUBTRACT, maximumZNode, z);
        support = intersect(nodes, "density-with-left", support, left);
        support = intersect(nodes, "density-with-right", support, right);
        support = intersect(nodes, "density-with-front", support, front);
        support = intersect(nodes, "density-with-back", support, back);
        return new ProceduralGraph(nodes, support);
    }

    private static NodeId constant(List<GraphNode> nodes, String name, double value) {
        NodeId id = new NodeId(name);
        nodes.add(new ConstantNode(id, GraphValueType.SCALAR_FIELD_3, value));
        return id;
    }

    private static NodeId arithmetic(
            List<GraphNode> nodes,
            String name,
            ArithmeticOperator operator,
            NodeId left,
            NodeId right) {
        NodeId id = new NodeId(name);
        nodes.add(new ArithmeticNode(id, GraphValueType.SCALAR_FIELD_3, operator, left, right));
        return id;
    }

    private static NodeId intersect(List<GraphNode> nodes, String name, NodeId left, NodeId right) {
        NodeId id = new NodeId(name);
        nodes.add(new IntersectionNode(id, left, right));
        return id;
    }
}
