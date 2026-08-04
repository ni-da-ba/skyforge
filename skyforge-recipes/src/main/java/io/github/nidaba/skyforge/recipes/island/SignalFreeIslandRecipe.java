package io.github.nidaba.skyforge.recipes.island;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateAxis;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles the deterministic base morphology for the first island, without seeded variation. */
public final class SignalFreeIslandRecipe {
    /** Version of the signal-free polynomial morphology defined by this class. */
    public static final int RECIPE_VERSION = 1;

    /** Maximum fractional extension of the principal radius at full ridge strength. */
    public static final double MAXIMUM_RIDGE_STRETCH = 0.40;

    /**
     * Compiles a semantic descriptor into height and derived density graphs.
     *
     * @throws NullPointerException if {@code descriptor} is {@code null}
     * @throws IllegalArgumentException if signal displacement is requested or derived constants
     *     cannot be represented as finite nonzero binary64 values
     */
    public CompiledIsland compile(IslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException("signalAmplitude must be zero for the signal-free recipe");
        }

        double stretch = 1.0 + MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();
        double majorRadius = descriptor.nominalRadius() * stretch;
        double minorRadius = descriptor.nominalRadius() / stretch;
        requireFinitePositive("derived major radius", majorRadius);
        requireFinitePositive("derived minor radius", minorRadius);

        ProceduralGraph height = morphologyGraph(descriptor, GraphValueType.SCALAR_FIELD_2, false);
        ProceduralGraph density = morphologyGraph(descriptor, GraphValueType.SCALAR_FIELD_3, true);
        return new CompiledIsland(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.SCHEMA_VERSION,
                height,
                density);
    }

    private static ProceduralGraph morphologyGraph(
            IslandDescriptor descriptor, GraphValueType type, boolean density) {
        GraphBuilder graph = new GraphBuilder(type);
        NodeId x = graph.coordinate("position.x", CoordinateAxis.X);
        NodeId z = graph.coordinate("position.z", CoordinateAxis.Z);
        NodeId centerX = graph.constant("descriptor.center-x", descriptor.centerX());
        NodeId centerZ = graph.constant("descriptor.center-z", descriptor.centerZ());
        NodeId dx = graph.arithmetic("position.relative-x", ArithmeticOperator.SUBTRACT, x, centerX);
        NodeId dz = graph.arithmetic("position.relative-z", ArithmeticOperator.SUBTRACT, z, centerZ);

        NodeId cosine = graph.constant("ridge.cos-azimuth", Math.cos(descriptor.ridgeAzimuth()));
        NodeId sine = graph.constant("ridge.sin-azimuth", Math.sin(descriptor.ridgeAzimuth()));
        NodeId dxCosine = graph.arithmetic("ridge.dx-cos", ArithmeticOperator.MULTIPLY, dx, cosine);
        NodeId dzSine = graph.arithmetic("ridge.dz-sin", ArithmeticOperator.MULTIPLY, dz, sine);
        NodeId alongRidge = graph.arithmetic(
                "ridge.along-axis", ArithmeticOperator.ADD, dxCosine, dzSine);
        NodeId dzCosine = graph.arithmetic("ridge.dz-cos", ArithmeticOperator.MULTIPLY, dz, cosine);
        NodeId dxSine = graph.arithmetic("ridge.dx-sin", ArithmeticOperator.MULTIPLY, dx, sine);
        NodeId acrossRidge = graph.arithmetic(
                "ridge.across-axis", ArithmeticOperator.SUBTRACT, dzCosine, dxSine);

        double stretch = 1.0 + MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();
        NodeId majorRadius = graph.constant(
                "ridge.major-radius", descriptor.nominalRadius() * stretch);
        NodeId minorRadius = graph.constant(
                "ridge.minor-radius", descriptor.nominalRadius() / stretch);
        NodeId alongNormalized = graph.arithmetic(
                "profile.along-normalized", ArithmeticOperator.DIVIDE, alongRidge, majorRadius);
        NodeId acrossNormalized = graph.arithmetic(
                "profile.across-normalized", ArithmeticOperator.DIVIDE, acrossRidge, minorRadius);
        NodeId alongSquared = graph.arithmetic(
                "profile.along-squared", ArithmeticOperator.MULTIPLY, alongNormalized, alongNormalized);
        NodeId acrossSquared = graph.arithmetic(
                "profile.across-squared", ArithmeticOperator.MULTIPLY, acrossNormalized, acrossNormalized);
        NodeId radiusSquared = graph.arithmetic(
                "profile.radius-squared", ArithmeticOperator.ADD, alongSquared, acrossSquared);
        NodeId radiusFourth = graph.arithmetic(
                "profile.radius-fourth", ArithmeticOperator.MULTIPLY, radiusSquared, radiusSquared);

        double quadraticWeight = descriptor.coastalFalloff() / descriptor.nominalRadius();
        double quarticWeight = 1.0 - quadraticWeight;
        NodeId quadraticWeightNode = graph.constant("coast.quadratic-weight", quadraticWeight);
        NodeId quarticWeightNode = graph.constant("coast.quartic-weight", quarticWeight);
        NodeId quadraticTerm = graph.arithmetic(
                "coast.quadratic-term", ArithmeticOperator.MULTIPLY, quadraticWeightNode, radiusSquared);
        NodeId quarticTerm = graph.arithmetic(
                "coast.quartic-term", ArithmeticOperator.MULTIPLY, quarticWeightNode, radiusFourth);
        NodeId profile = graph.arithmetic(
                "coast.normalized-profile", ArithmeticOperator.ADD, quadraticTerm, quarticTerm);
        NodeId one = graph.constant("height.one", 1.0);
        NodeId remainingHeight = graph.arithmetic(
                "height.remaining-fraction", ArithmeticOperator.SUBTRACT, one, profile);
        NodeId maximumElevation = graph.constant(
                "descriptor.maximum-elevation", descriptor.maximumElevation());
        NodeId height = graph.arithmetic(
                "height", ArithmeticOperator.MULTIPLY, maximumElevation, remainingHeight);

        if (!density) {
            return graph.build(height);
        }
        NodeId y = graph.coordinate("position.y", CoordinateAxis.Y);
        NodeId densityOutput = graph.arithmetic("density", ArithmeticOperator.SUBTRACT, height, y);
        return graph.build(densityOutput);
    }

    private static void requireFinitePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " is outside the representable range");
        }
    }

    private static final class GraphBuilder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private GraphBuilder(GraphValueType type) {
            this.type = type;
        }

        private NodeId coordinate(String id, CoordinateAxis axis) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new CoordinateNode(nodeId, type, axis));
            return nodeId;
        }

        private NodeId constant(String id, double value) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ConstantNode(nodeId, type, value));
            return nodeId;
        }

        private NodeId arithmetic(String id, ArithmeticOperator operator, NodeId left, NodeId right) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ArithmeticNode(nodeId, type, operator, left, right));
            return nodeId;
        }

        private ProceduralGraph build(NodeId output) {
            return new ProceduralGraph(nodes, output);
        }
    }
}
