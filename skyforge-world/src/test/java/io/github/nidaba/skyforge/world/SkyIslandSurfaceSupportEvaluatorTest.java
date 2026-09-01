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

/** Deterministic proof for the SF-IMP-0044 backend-neutral surface-support contract. */
final class SkyIslandSurfaceSupportEvaluatorTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private final SkyIslandSurfaceSupportEvaluator evaluator = new SkyIslandSurfaceSupportEvaluator();

    @Test
    void acceptsBroadFlatInteriorWithSupportedClearanceRing() {
        SkyIslandWorldVolume volume = rectangularVolume("flat", 11L, 0.0, 10.0, 0.0, 10.0, 100.0, 0.0);
        SurfaceSupportAssessment assessment = evaluator.assess(volume, requirements(
                2.0, 8.0, 2.0, 8.0, 2.0, 1.0, 0.90, 0.90, 1.0));

        assertEquals(16, assessment.sampleCount());
        assertEquals(16, assessment.supportedSampleCount());
        assertEquals(1.0, assessment.coverageFraction());
        assertEquals(16, assessment.clearanceSampleCount());
        assertEquals(16, assessment.supportedClearanceSampleCount());
        assertEquals(0.0, assessment.heightSpan());
        assertEquals(1, assessment.surfaceComponentCount());
        assertTrue(assessment.coherentSurface());
        assertTrue(assessment.accepted());
    }

    @Test
    void rejectsFootprintThatCrossesIslandEdge() {
        SkyIslandWorldVolume volume = rectangularVolume("edge", 12L, 0.0, 10.0, 0.0, 10.0, 100.0, 0.0);
        SurfaceSupportAssessment assessment = evaluator.assess(volume, requirements(
                8.0, 12.0, 2.0, 8.0, 2.0, 0.0, 0.90, 0.0, 1.0));

        assertEquals(12, assessment.sampleCount());
        assertEquals(4, assessment.supportedSampleCount());
        assertTrue(assessment.crossesSurfaceBoundary());
        assertFalse(assessment.accepted());
    }

    @Test
    void rejectsExcessiveSurfaceHeightSpanWithoutFlatteningGeometry() {
        SkyIslandWorldVolume volume = rectangularVolume("slope", 13L, 0.0, 10.0, 0.0, 10.0, 100.0, 1.0);
        SurfaceSupportAssessment assessment = evaluator.assess(volume, requirements(
                2.0, 8.0, 2.0, 8.0, 2.0, 0.0, 1.0, 0.0, 4.0));

        assertEquals(1.0, assessment.coverageFraction());
        assertEquals(102.0, assessment.minimumSurfaceY());
        assertEquals(108.0, assessment.maximumSurfaceY());
        assertEquals(6.0, assessment.heightSpan());
        assertFalse(assessment.accepted());
    }

    @Test
    void disconnectedSupportIsNotAcoherentFoundation() {
        SkyIslandWorldVolume volume = splitVolume("split", 14L, -5.0, 5.0, -3.0, 3.0, 100.0);
        SurfaceSupportAssessment assessment = evaluator.assess(volume, requirements(
                -4.0, 4.0, -2.0, 2.0, 2.0, 0.0, 0.50, 0.0, 1.0));

        assertEquals(2, assessment.surfaceComponentCount());
        assertFalse(assessment.coherentSurface());
        assertFalse(assessment.accepted());
    }

    @Test
    void verticallyStackedIslandsRemainIndependentAssessments() {
        SkyIslandWorldVolume lower = rectangularVolume("lower", 21L, 0.0, 10.0, 0.0, 10.0, 100.0, 0.0);
        SkyIslandWorldVolume upper = rectangularVolume("upper", 22L, 0.0, 10.0, 0.0, 10.0, 200.0, 0.0);
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
        List<SurfaceSupportAssessment> assessments = evaluator.assess(catalog, requirements(
                2.0, 8.0, 2.0, 8.0, 2.0, 0.0, 1.0, 0.0, 1.0));

        assertEquals(2, assessments.size());
        assertEquals(lower.id(), assessments.get(0).supportingVolumeId());
        assertEquals(100.0, assessments.get(0).minimumSurfaceY());
        assertEquals(upper.id(), assessments.get(1).supportingVolumeId());
        assertEquals(200.0, assessments.get(1).minimumSurfaceY());
        assertEquals(0.0, assessments.get(0).heightSpan());
        assertEquals(0.0, assessments.get(1).heightSpan());
        assertTrue(assessments.get(0).accepted());
        assertTrue(assessments.get(1).accepted());
    }

    private static SurfaceSupportRequirements requirements(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double spacing,
            double clearance,
            double minimumCoverage,
            double minimumClearanceCoverage,
            double maximumHeightSpan) {
        return new SurfaceSupportRequirements(
                minimumX,
                maximumX,
                minimumZ,
                maximumZ,
                spacing,
                clearance,
                minimumCoverage,
                minimumClearanceCoverage,
                maximumHeightSpan);
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
        return volume(name, seed, minimumX, maximumX, minimumZ, maximumZ, baseUpper, slopeX, false);
    }

    private static SkyIslandWorldVolume splitVolume(
            String name,
            long seed,
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double baseUpper) {
        return volume(name, seed, minimumX, maximumX, minimumZ, maximumZ, baseUpper, 0.0, true);
    }

    private static SkyIslandWorldVolume volume(
            String name,
            long seed,
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double baseUpper,
            double slopeX,
            boolean splitSupport) {
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
                densityGraph(minimumX, maximumX, minimumZ, maximumZ, baseUpper, slopeX, splitSupport),
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
            double slopeX,
            boolean splitSupport) {
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

        if (splitSupport) {
            NodeId squaredX = arithmetic(nodes, "density-x-squared", ArithmeticOperator.MULTIPLY, x, x);
            NodeId one = constant(nodes, "density-one", 1.0);
            NodeId outsideCenter = arithmetic(
                    nodes, "density-outside-center", ArithmeticOperator.SUBTRACT, squaredX, one);
            support = intersect(nodes, "density-split-support", support, outsideCenter);
        }
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
