package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import java.util.Objects;

/** Provider-supplied positive two-dimensional secondary-morphology multiplier. */
public record SecondaryMorphologyContribution(
        ProceduralGraph factorGraph,
        double minimumFactor,
        double maximumFactor) {

    /** Validates factor dimensionality and the declared finite positive analytical envelope. */
    public SecondaryMorphologyContribution {
        Objects.requireNonNull(factorGraph, "factorGraph");
        if (factorGraph.outputType() != GraphValueType.SCALAR_FIELD_2) {
            throw new IllegalArgumentException("secondary morphology factor must be a scalar field 2");
        }
        if (!Double.isFinite(minimumFactor)
                || !Double.isFinite(maximumFactor)
                || minimumFactor <= 0.0
                || maximumFactor < minimumFactor) {
            throw new IllegalArgumentException(
                    "secondary morphology factor envelope must be finite, positive, and ordered");
        }
    }
}
