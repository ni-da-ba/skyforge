package io.github.nidaba.skyforge.recipes.island;

import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import java.util.Objects;

/** An island descriptor compiled into inspectable height and solid-density graphs. */
public record CompiledIsland(
        IslandDescriptor descriptor,
        int recipeVersion,
        int graphSchemaVersion,
        ProceduralGraph heightGraph,
        ProceduralGraph densityGraph) {
    /** Validates the compiled artifact and its graph domains. */
    public CompiledIsland {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(heightGraph, "heightGraph");
        Objects.requireNonNull(densityGraph, "densityGraph");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        if (graphSchemaVersion <= 0) {
            throw new IllegalArgumentException("graphSchemaVersion must be positive");
        }
        if (heightGraph.outputType() != GraphValueType.SCALAR_FIELD_2) {
            throw new IllegalArgumentException("heightGraph must produce SCALAR_FIELD_2");
        }
        if (densityGraph.outputType() != GraphValueType.SCALAR_FIELD_3) {
            throw new IllegalArgumentException("densityGraph must produce SCALAR_FIELD_3");
        }
    }
}
