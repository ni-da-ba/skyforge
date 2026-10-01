package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A semantic sky-island descriptor compiled into inspectable surface and density graphs. */
public record CompiledSkyIslandVolume(
        SkyIslandVolumeDescriptor descriptor,
        int recipeVersion,
        int graphSchemaVersion,
        ProceduralGraph upperSurfaceGraph,
        ProceduralGraph undersideSurfaceGraph,
        ProceduralGraph densityGraph,
        Map<String, List<NodeId>> provenance) {
    /** Validates graph domains, version metadata, and semantic provenance. */
    public CompiledSkyIslandVolume {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(upperSurfaceGraph, "upperSurfaceGraph");
        Objects.requireNonNull(undersideSurfaceGraph, "undersideSurfaceGraph");
        Objects.requireNonNull(densityGraph, "densityGraph");
        Objects.requireNonNull(provenance, "provenance");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        if (graphSchemaVersion <= 0) {
            throw new IllegalArgumentException("graphSchemaVersion must be positive");
        }
        requireType("upperSurfaceGraph", upperSurfaceGraph, GraphValueType.SCALAR_FIELD_2);
        requireType("undersideSurfaceGraph", undersideSurfaceGraph, GraphValueType.SCALAR_FIELD_2);
        requireType("densityGraph", densityGraph, GraphValueType.SCALAR_FIELD_3);
        provenance = immutableProvenance(
                provenance, upperSurfaceGraph, undersideSurfaceGraph, densityGraph);
    }

    private static void requireType(
            String name, ProceduralGraph graph, GraphValueType expected) {
        if (graph.outputType() != expected) {
            throw new IllegalArgumentException(name + " must produce " + expected);
        }
    }

    private static Map<String, List<NodeId>> immutableProvenance(
            Map<String, List<NodeId>> source,
            ProceduralGraph upper,
            ProceduralGraph underside,
            ProceduralGraph density) {
        LinkedHashMap<String, List<NodeId>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<NodeId>> entry : source.entrySet()) {
            String control = Objects.requireNonNull(entry.getKey(), "provenance control");
            if (control.isBlank() || !control.equals(control.strip())) {
                throw new IllegalArgumentException(
                        "provenance controls must be nonblank and have no surrounding whitespace");
            }
            List<NodeId> nodes = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "provenance nodes"));
            for (NodeId node : nodes) {
                if (!contains(upper, node) && !contains(underside, node) && !contains(density, node)) {
                    throw new IllegalArgumentException(
                            "provenance references an unknown graph node: " + node);
                }
            }
            if (copy.put(control, nodes) != null) {
                throw new IllegalArgumentException("duplicate provenance control: " + control);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean contains(ProceduralGraph graph, NodeId id) {
        List<NodeId> ids = new ArrayList<>(graph.nodes().size());
        graph.nodes().forEach(node -> ids.add(node.id()));
        return ids.contains(id);
    }
}
