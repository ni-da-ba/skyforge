package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import java.util.Objects;
import java.util.Optional;

/**
 * One provider-compiled signal-free primary morphology plus explicit structural field handles.
 *
 * <p>The handles allow composition to consume provider semantics without depending on provider-local
 * node naming conventions.
 */
public record PrimaryMorphologyContribution(
        CompiledSkyIslandVolume volume,
        NodeId footprintResidual,
        NodeId alongNormalized,
        NodeId acrossNormalized,
        Optional<NodeId> lobeDirectional,
        NodeId upperFactor,
        NodeId undersideDepthFactor) {

    /** Validates that every declared structural handle exists in the supplied primary graphs. */
    public PrimaryMorphologyContribution {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(footprintResidual, "footprintResidual");
        Objects.requireNonNull(alongNormalized, "alongNormalized");
        Objects.requireNonNull(acrossNormalized, "acrossNormalized");
        Objects.requireNonNull(lobeDirectional, "lobeDirectional");
        Objects.requireNonNull(upperFactor, "upperFactor");
        Objects.requireNonNull(undersideDepthFactor, "undersideDepthFactor");

        requireAll(volume.upperSurfaceGraph(), footprintResidual, alongNormalized, acrossNormalized, upperFactor);
        requireAll(
                volume.undersideSurfaceGraph(),
                footprintResidual,
                alongNormalized,
                acrossNormalized,
                undersideDepthFactor);
        requireAll(
                volume.densityGraph(),
                footprintResidual,
                alongNormalized,
                acrossNormalized,
                upperFactor,
                undersideDepthFactor);
        lobeDirectional.ifPresent(id -> {
            requireAll(volume.upperSurfaceGraph(), id);
            requireAll(volume.undersideSurfaceGraph(), id);
            requireAll(volume.densityGraph(), id);
        });
    }

    private static void requireAll(ProceduralGraph graph, NodeId... ids) {
        for (NodeId id : ids) {
            try {
                graph.requireNode(id);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "primary morphology structural node is missing from graph: " + id,
                        exception);
            }
        }
    }
}
