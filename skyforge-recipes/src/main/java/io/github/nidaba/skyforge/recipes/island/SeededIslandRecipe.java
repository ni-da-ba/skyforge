package io.github.nidaba.skyforge.recipes.island;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles the accepted base morphology with bounded deterministic seeded enrichment. */
public final class SeededIslandRecipe {
    /** Version of the seeded island recipe defined by this class. */
    public static final int RECIPE_VERSION = 2;

    /** Maximum relative height modulation at full descriptor amplitude. */
    public static final double MAXIMUM_RELATIVE_DISPLACEMENT = 0.15;

    /** Stable semantic namespace of the first island signal operation. */
    public static final String HEIGHT_SIGNAL_NAMESPACE = "island.height-detail";

    private static final NodeId BASE_HEIGHT = new NodeId("height");
    private static final NodeId BASE_DENSITY = new NodeId("density");
    private static final NodeId SEEDED_HEIGHT = new NodeId("height.seeded");
    private final SignalFreeIslandRecipe signalFreeRecipe = new SignalFreeIslandRecipe();
    private final CanonicalGraphJson graphCodec = new CanonicalGraphJson();

    /**
     * Compiles a descriptor, preserving the exact v1 artifact when signal amplitude is zero.
     *
     * @throws NullPointerException if {@code descriptor} is {@code null}
     */
    public CompiledIsland compile(IslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.signalAmplitude() == 0.0) {
            return signalFreeRecipe.compile(descriptor);
        }

        IslandDescriptor baseDescriptor = new IslandDescriptor(
                descriptor.schemaVersion(),
                descriptor.seed(),
                descriptor.centerX(),
                descriptor.centerZ(),
                descriptor.nominalRadius(),
                descriptor.maximumElevation(),
                descriptor.coastalFalloff(),
                descriptor.ridgeAzimuth(),
                descriptor.ridgeStrength(),
                0.0,
                descriptor.signalScale());
        CompiledIsland base = signalFreeRecipe.compile(baseDescriptor);
        ProceduralGraph height = enrich(base.heightGraph(), descriptor, false);
        ProceduralGraph density = enrich(base.densityGraph(), descriptor, true);
        int graphSchemaVersion = graphCodec.schemaVersion(height);
        if (graphCodec.schemaVersion(density) != graphSchemaVersion) {
            throw new IllegalStateException("height and density graphs require different schemas");
        }
        return new CompiledIsland(
                descriptor,
                RECIPE_VERSION,
                graphSchemaVersion,
                height,
                density);
    }

    private static ProceduralGraph enrich(
            ProceduralGraph base,
            IslandDescriptor descriptor,
            boolean density) {
        GraphValueType type = base.outputType();
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        if (density) {
            nodes.removeIf(node -> node.id().equals(BASE_DENSITY));
        }

        NodeId signal = new NodeId("signal.height-detail");
        nodes.add(new PlanarValueSignalNode(
                signal,
                type,
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                descriptor.seed(),
                HEIGHT_SIGNAL_NAMESPACE,
                descriptor.signalScale()));
        NodeId maximum = constant(
                nodes,
                type,
                "signal.maximum-relative-displacement",
                MAXIMUM_RELATIVE_DISPLACEMENT);
        NodeId amplitude = constant(
                nodes,
                type,
                "descriptor.signal-amplitude",
                descriptor.signalAmplitude());
        NodeId relativeAmplitude = arithmetic(
                nodes,
                type,
                "signal.relative-amplitude",
                ArithmeticOperator.MULTIPLY,
                maximum,
                amplitude);
        NodeId modulation = arithmetic(
                nodes,
                type,
                "signal.modulation",
                ArithmeticOperator.MULTIPLY,
                relativeAmplitude,
                signal);
        NodeId one = constant(nodes, type, "signal.one", 1.0);
        NodeId factor = arithmetic(
                nodes,
                type,
                "signal.factor",
                ArithmeticOperator.ADD,
                one,
                modulation);
        nodes.add(new ArithmeticNode(
                SEEDED_HEIGHT,
                type,
                ArithmeticOperator.MULTIPLY,
                BASE_HEIGHT,
                factor));

        if (!density) {
            return new ProceduralGraph(nodes, SEEDED_HEIGHT);
        }
        NodeId y = new NodeId("position.y");
        nodes.add(new ArithmeticNode(
                BASE_DENSITY,
                type,
                ArithmeticOperator.SUBTRACT,
                SEEDED_HEIGHT,
                y));
        return new ProceduralGraph(nodes, BASE_DENSITY);
    }

    private static NodeId constant(
            List<GraphNode> nodes,
            GraphValueType type,
            String identifier,
            double value) {
        NodeId id = new NodeId(identifier);
        nodes.add(new ConstantNode(id, type, value));
        return id;
    }

    private static NodeId arithmetic(
            List<GraphNode> nodes,
            GraphValueType type,
            String identifier,
            ArithmeticOperator operator,
            NodeId left,
            NodeId right) {
        NodeId id = new NodeId(identifier);
        nodes.add(new ArithmeticNode(id, type, operator, left, right));
        return id;
    }
}
