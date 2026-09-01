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

final class SkyIslandTerrainBoxObserverTest {
    private static final long ROOT_SEED = 0x53464f4253455256L;
    private final SkyIslandTerrainBoxObserver observer = new SkyIslandTerrainBoxObserver();

    @Test
    void reportsWhollySolidInteriorWithoutInventingPolicy() {
        TerrainBoxObservation observation = observe(volume("solid", 100.0, 80.0, false),
                new WorldBounds(2.0, 4.0, 88.0, 92.0, 2.0, 4.0), 2.0);

        assertEquals(12, observation.sampleCount());
        assertEquals(12, observation.solidSampleCount());
        assertTrue(observation.allSamplesSolid());
        assertFalse(observation.mixed());
    }

    @Test
    void reportsWhollyAtOrAboveCrown() {
        TerrainBoxObservation observation = observe(volume("above", 100.0, 80.0, false),
                new WorldBounds(2.0, 4.0, 100.0, 104.0, 2.0, 4.0), 2.0);

        assertEquals(observation.sampleCount(), observation.atOrAboveUpperSurfaceSampleCount());
        assertTrue(observation.allSamplesAtOrAboveUpperSurface());
    }

    @Test
    void reportsWhollyAtOrBelowUnderside() {
        TerrainBoxObservation observation = observe(volume("below", 100.0, 80.0, false),
                new WorldBounds(2.0, 4.0, 76.0, 80.0, 2.0, 4.0), 2.0);

        assertEquals(observation.sampleCount(), observation.atOrBelowUndersideSurfaceSampleCount());
        assertTrue(observation.allSamplesAtOrBelowUndersideSurface());
    }

    @Test
    void reportsOpenSpaceBetweenSurfacesWithoutCallingItExterior() {
        TerrainBoxObservation observation = observe(volume("open", 100.0, 80.0, true),
                new WorldBounds(-0.5, 0.5, 88.0, 92.0, -0.5, 0.5), 1.0);

        assertEquals(observation.sampleCount(), observation.openBetweenSurfacesSampleCount());
        assertTrue(observation.hasOpenBetweenSurfacesSamples());
        assertFalse(observation.allSamplesSolid());
    }

    @Test
    void reportsMixedBoundaryEvidenceRatherThanCollapsingIt() {
        TerrainBoxObservation observation = observe(volume("mixed", 100.0, 80.0, false),
                new WorldBounds(2.0, 4.0, 98.0, 102.0, 2.0, 4.0), 2.0);

        assertTrue(observation.solidSampleCount() > 0);
        assertTrue(observation.atOrAboveUpperSurfaceSampleCount() > 0);
        assertTrue(observation.mixed());
        assertFalse(observation.allSamplesSolid());
        assertFalse(observation.allSamplesAtOrAboveUpperSurface());
    }

    @Test
    void independentStackedVolumesProduceIndependentObservations() {
        SkyIslandWorldVolume lower = volume("lower", 100.0, 80.0, false);
        SkyIslandWorldVolume upper = volume("upper", 200.0, 180.0, false);
        TerrainBoxObservationRequirements requirements = new TerrainBoxObservationRequirements(
                new WorldBounds(2.0, 4.0, 88.0, 92.0, 2.0, 4.0), 2.0);

        TerrainBoxObservation lowerObservation = observer.observe(lower, SkyIslandTerrainProfile.reference(), requirements);
        TerrainBoxObservation upperObservation = observer.observe(upper, SkyIslandTerrainProfile.reference(), requirements);

        assertTrue(lowerObservation.allSamplesSolid());
        assertTrue(upperObservation.allSamplesAtOrBelowUndersideSurface());
        assertEquals(lower.id(), lowerObservation.observedVolumeId());
        assertEquals(upper.id(), upperObservation.observedVolumeId());
    }

    private TerrainBoxObservation observe(SkyIslandWorldVolume volume, WorldBounds bounds, double spacing) {
        return observer.observe(
                volume,
                SkyIslandTerrainProfile.reference(),
                new TerrainBoxObservationRequirements(bounds, spacing));
    }

    private static SkyIslandWorldVolume volume(String name, double upperY, double undersideY, boolean centerHole) {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                0.0,
                0.0,
                upperY,
                32.0,
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
                constantGraph(GraphValueType.SCALAR_FIELD_2, upperY, name + "-upper"),
                constantGraph(GraphValueType.SCALAR_FIELD_2, undersideY, name + "-underside"),
                densityGraph(-10.0, 10.0, -10.0, 10.0, upperY, undersideY, centerHole, name),
                Map.of());
        SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(ROOT_SEED, name, 0, 0, name.hashCode());
        return new SkyIslandWorldVolume(
                id,
                new WorldBounds(-10.0, 10.0, undersideY - 1.0, upperY + 1.0, -10.0, 10.0),
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
            double upperY,
            double undersideY,
            boolean centerHole,
            String prefix) {
        List<GraphNode> nodes = new ArrayList<>();
        NodeId x = coordinate(nodes, prefix + "-x", CoordinateAxis.X);
        NodeId y = coordinate(nodes, prefix + "-y", CoordinateAxis.Y);
        NodeId z = coordinate(nodes, prefix + "-z", CoordinateAxis.Z);
        NodeId upper = constant(nodes, prefix + "-upper3", upperY);
        NodeId lower = constant(nodes, prefix + "-lower3", undersideY);
        NodeId support = intersect(nodes, prefix + "-vertical",
                arithmetic(nodes, prefix + "-upper-gap", ArithmeticOperator.SUBTRACT, upper, y),
                arithmetic(nodes, prefix + "-lower-gap", ArithmeticOperator.SUBTRACT, y, lower));
        support = intersect(nodes, prefix + "-left", support,
                arithmetic(nodes, prefix + "-left-gap", ArithmeticOperator.SUBTRACT, x,
                        constant(nodes, prefix + "-min-x", minimumX)));
        support = intersect(nodes, prefix + "-right", support,
                arithmetic(nodes, prefix + "-right-gap", ArithmeticOperator.SUBTRACT,
                        constant(nodes, prefix + "-max-x", maximumX), x));
        support = intersect(nodes, prefix + "-front", support,
                arithmetic(nodes, prefix + "-front-gap", ArithmeticOperator.SUBTRACT, z,
                        constant(nodes, prefix + "-min-z", minimumZ)));
        support = intersect(nodes, prefix + "-back", support,
                arithmetic(nodes, prefix + "-back-gap", ArithmeticOperator.SUBTRACT,
                        constant(nodes, prefix + "-max-z", maximumZ), z));
        if (centerHole) {
            NodeId squaredX = arithmetic(nodes, prefix + "-x2", ArithmeticOperator.MULTIPLY, x, x);
            NodeId squaredZ = arithmetic(nodes, prefix + "-z2", ArithmeticOperator.MULTIPLY, z, z);
            NodeId radiusSquared = arithmetic(nodes, prefix + "-r2", ArithmeticOperator.ADD, squaredX, squaredZ);
            NodeId outsideHole = arithmetic(nodes, prefix + "-outside-hole", ArithmeticOperator.SUBTRACT,
                    radiusSquared, constant(nodes, prefix + "-hole-r2", 1.0));
            support = intersect(nodes, prefix + "-with-hole", support, outsideHole);
        }
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
            List<GraphNode> nodes, String name, ArithmeticOperator operator, NodeId left, NodeId right) {
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
