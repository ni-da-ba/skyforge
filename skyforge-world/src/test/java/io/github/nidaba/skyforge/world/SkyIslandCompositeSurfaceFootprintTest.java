package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Regression proof that piece-like rectangle unions do not require terrain in their empty gaps. */
final class SkyIslandCompositeSurfaceFootprintTest {
    private static final long ROOT_SEED = 0x53464f4f54505249L;

    @Test
    void separatedFootprintComponentsRemainCoherentWithoutSamplingEnvelopeGap() {
        SkyIslandWorldVolume volume = flatVolume();
        SurfaceFootprint footprint = separatedFootprint();
        SurfaceSupportRequirements requirements = new SurfaceSupportRequirements(
                footprint,
                1.0,
                0.0,
                1.0,
                0.0,
                1.0);

        SurfaceSupportAssessment assessment = new SkyIslandSurfaceSupportEvaluator().assess(volume, requirements);

        assertEquals(18, assessment.sampleCount());
        assertEquals(18, assessment.supportedSampleCount());
        assertEquals(2, assessment.surfaceComponentCount());
        assertTrue(assessment.coherentSurface());
        assertTrue(assessment.accepted());
    }

    @Test
    void foundationCountsOnlyCompositeFootprintColumns() {
        SkyIslandWorldVolume volume = flatVolume();
        SurfaceSupportRequirements support = new SurfaceSupportRequirements(
                separatedFootprint(),
                1.0,
                0.0,
                1.0,
                0.0,
                1.0);
        SurfaceFoundationRequirements foundation = new SurfaceFoundationRequirements(
                support,
                104.0,
                104.0,
                8.0);

        SurfaceFoundationAssessment assessment = new SkyIslandSurfaceFoundationEvaluator().assess(volume, foundation);

        assertEquals(18, assessment.supportAssessment().sampleCount());
        assertEquals(18, assessment.fillSampleCount());
        assertEquals(4.0, assessment.maximumRequiredFillDepth());
        assertTrue(assessment.accepted());
    }

    private static SurfaceFootprint separatedFootprint() {
        return new SurfaceFootprint(List.of(
                new SurfaceFootprintRectangle(0.0, 2.0, 0.0, 2.0),
                new SurfaceFootprintRectangle(8.0, 10.0, 0.0, 2.0)));
    }

    private static SkyIslandWorldVolume flatVolume() {
        double upperY = 100.0;
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                5.0,
                1.0,
                upperY,
                16.0,
                20.0,
                8.0,
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
                constantGraph(GraphValueType.SCALAR_FIELD_2, upperY, "upper"),
                constantGraph(GraphValueType.SCALAR_FIELD_2, upperY - 20.0, "underside"),
                densityGraph(-1.0, 11.0, -1.0, 3.0, upperY),
                Map.of());
        SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(ROOT_SEED, "composite", 0, 0, ROOT_SEED);
        return new SkyIslandWorldVolume(
                id,
                new WorldBounds(-1.0, 11.0, upperY - 21.0, upperY + 1.0, -1.0, 3.0),
                compiled);
    }

    private static ProceduralGraph constantGraph(GraphValueType type, double value, String name) {
        NodeId output = new NodeId(name);
        return new ProceduralGraph(List.of(new ConstantNode(output, type, value)), output);
    }

    private static ProceduralGraph densityGraph(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double upperY) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, "density-x", CoordinateAxis.X);
        NodeId y = coordinate(nodes, "density-y", CoordinateAxis.Y);
        NodeId z = coordinate(nodes, "density-z", CoordinateAxis.Z);
        NodeId upper = constant(nodes, "density-upper", upperY);
        NodeId lower = constant(nodes, "density-lower", upperY - 20.0);
        NodeId support = intersect(
                nodes,
                "vertical",
                arithmetic(nodes, "upper-gap", ArithmeticOperator.SUBTRACT, upper, y),
                arithmetic(nodes, "lower-gap", ArithmeticOperator.SUBTRACT, y, lower));
        support = intersect(nodes, "left", support,
                arithmetic(nodes, "left-gap", ArithmeticOperator.SUBTRACT, x, constant(nodes, "min-x", minimumX)));
        support = intersect(nodes, "right", support,
                arithmetic(nodes, "right-gap", ArithmeticOperator.SUBTRACT, constant(nodes, "max-x", maximumX), x));
        support = intersect(nodes, "front", support,
                arithmetic(nodes, "front-gap", ArithmeticOperator.SUBTRACT, z, constant(nodes, "min-z", minimumZ)));
        support = intersect(nodes, "back", support,
                arithmetic(nodes, "back-gap", ArithmeticOperator.SUBTRACT, constant(nodes, "max-z", maximumZ), z));
        return new ProceduralGraph(nodes, support);
    }

    private static NodeId coordinate(List<GraphNode> nodes, String name, CoordinateAxis axis) {
        NodeId id = new NodeId(name);
        nodes.add(new CoordinateNode(id, GraphValueType.SCALAR_FIELD_3, axis));
        return id;
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
