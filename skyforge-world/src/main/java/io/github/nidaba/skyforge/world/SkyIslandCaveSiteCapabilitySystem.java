package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * AUTH-0099 threshold-free capability evidence for one exact authored cave system.
 *
 * <p>All summary methods are descriptive. No cave usefulness, mine eligibility, structure role,
 * progression tier, or backend compatibility is inferred.
 */
public record SkyIslandCaveSiteCapabilitySystem(
        SkyIslandCaveSystem sourceSystem,
        SkyIslandCaveSystemGeometry sourceGeometry,
        Optional<SkyIslandCaveExposureIntent> exteriorExposure,
        List<SkyIslandCaveSiteNodeEvidence> nodeEvidence,
        double nominalRadius) {

    public SkyIslandCaveSiteCapabilitySystem {
        sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem");
        sourceGeometry = Objects.requireNonNull(sourceGeometry, "sourceGeometry");
        exteriorExposure = Objects.requireNonNull(exteriorExposure, "exteriorExposure");
        nodeEvidence = List.copyOf(nodeEvidence);
        nodeEvidence.forEach(evidence -> Objects.requireNonNull(evidence, "cave-node evidence"));

        if (!Double.isFinite(nominalRadius) || nominalRadius <= 0.0) {
            throw new IllegalArgumentException("nominalRadius must be finite and positive");
        }
        if (sourceGeometry.systemId() != sourceSystem.systemId()) {
            throw new IllegalArgumentException(
                    "cave site capability geometry must retain exact source system identity");
        }
        if (nodeEvidence.size() != sourceSystem.nodes().size()) {
            throw new IllegalArgumentException(
                    "cave site capability must cover every exact source node");
        }
        for (int ordinal = 0; ordinal < nodeEvidence.size(); ordinal++) {
            if (!nodeEvidence.get(ordinal).sourceNode().equals(sourceSystem.nodes().get(ordinal))) {
                throw new IllegalArgumentException(
                        "cave site node evidence must preserve exact source node order/identity");
            }
        }
        if (exteriorExposure.isPresent()
                && exteriorExposure.orElseThrow().systemId() != sourceSystem.systemId()) {
            throw new IllegalArgumentException(
                    "cave exposure evidence must belong to the exact source system");
        }
    }

    public int systemId() {
        return sourceSystem.systemId();
    }

    public int nodeCount() {
        return sourceSystem.nodes().size();
    }

    public int linkCount() {
        return sourceSystem.links().size();
    }

    public int chamberCount() {
        return sourceGeometry.chambers().size();
    }

    public int passageCount() {
        return sourceGeometry.passages().size();
    }

    public double minimumNodeDepth() {
        return minimum(evidence -> evidence.sourceNode().position().depthFraction());
    }

    public double meanNodeDepth() {
        return mean(evidence -> evidence.sourceNode().position().depthFraction());
    }

    public double maximumNodeDepth() {
        return maximum(evidence -> evidence.sourceNode().position().depthFraction());
    }

    public double meanGroundwaterPotential() {
        return mean(evidence -> evidence.sourceNode().groundwaterPotential());
    }

    public double maximumChamberPotential() {
        return sourceSystem.nodes().stream()
                .mapToDouble(SkyIslandCaveNode::chamberPotential)
                .max()
                .orElse(0.0);
    }

    public double meanNormalizedChamberHorizontalRadius() {
        return sourceGeometry.chambers().stream()
                .mapToDouble(chamber -> chamber.horizontalRadius() / nominalRadius)
                .average()
                .orElse(0.0);
    }

    public double maximumNormalizedChamberHorizontalRadius() {
        return sourceGeometry.chambers().stream()
                .mapToDouble(chamber -> chamber.horizontalRadius() / nominalRadius)
                .max()
                .orElse(0.0);
    }

    public double maximumChamberDepthRadius() {
        return sourceGeometry.chambers().stream()
                .mapToDouble(SkyIslandCaveChamberGeometry::depthRadius)
                .max()
                .orElse(0.0);
    }

    public double meanFractureIntensity() {
        return mean(evidence -> evidence.geology().fractureIntensity());
    }

    public double peakFractureIntensity() {
        return maximum(evidence -> evidence.geology().fractureIntensity());
    }

    public double meanVoidFormationPotential() {
        return mean(evidence -> evidence.geology().voidFormationPotential());
    }

    public double peakVoidFormationPotential() {
        return maximum(evidence -> evidence.geology().voidFormationPotential());
    }

    public double meanConnectedPermeability() {
        return mean(evidence -> evidence.geology().connectedPermeability());
    }

    public double peakConnectedPermeability() {
        return maximum(evidence -> evidence.geology().connectedPermeability());
    }

    public double meanMineralBearingHostSupport() {
        return mean(evidence -> evidence.nearestHostCell().mineralBearingStructuralHost());
    }

    public double peakMineralBearingHostSupport() {
        return maximum(evidence -> evidence.nearestHostCell().mineralBearingStructuralHost());
    }

    public double meanNearestHostDistance() {
        return mean(SkyIslandCaveSiteNodeEvidence::normalizedHostDistance);
    }

    public double maximumNearestHostDistance() {
        return maximum(SkyIslandCaveSiteNodeEvidence::normalizedHostDistance);
    }

    public boolean hasAcceptedExteriorExposure() {
        return exteriorExposure.isPresent();
    }

    private double minimum(ToDoubleFunction<SkyIslandCaveSiteNodeEvidence> value) {
        return nodeEvidence.stream().mapToDouble(value).min().orElse(0.0);
    }

    private double mean(ToDoubleFunction<SkyIslandCaveSiteNodeEvidence> value) {
        return nodeEvidence.stream().mapToDouble(value).average().orElse(0.0);
    }

    private double maximum(ToDoubleFunction<SkyIslandCaveSiteNodeEvidence> value) {
        return nodeEvidence.stream().mapToDouble(value).max().orElse(0.0);
    }
}
