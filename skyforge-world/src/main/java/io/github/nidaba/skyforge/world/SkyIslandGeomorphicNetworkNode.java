package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One shared physical network anchor chosen near a semantic source, confluence, or terminal. */
public record SkyIslandGeomorphicNetworkNode(
        int cellIndex,
        SkyIslandGeomorphicNetworkNodeKind kind,
        SkyIslandLocalPosition semanticCenter,
        SkyIslandLocalPosition physicalPosition,
        double searchRadius,
        double terrainElevation,
        double valleyFloorAdvantage) {

    public SkyIslandGeomorphicNetworkNode {
        if (cellIndex < 0) {
            throw new IllegalArgumentException("cellIndex must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        semanticCenter = Objects.requireNonNull(semanticCenter, "semanticCenter");
        physicalPosition = Objects.requireNonNull(physicalPosition, "physicalPosition");
        if (!Double.isFinite(searchRadius) || searchRadius < 0.0) {
            throw new IllegalArgumentException("searchRadius must be finite and non-negative");
        }
        if (!Double.isFinite(terrainElevation) || terrainElevation < 0.0 || terrainElevation > 1.0) {
            throw new IllegalArgumentException("terrainElevation must be finite and in [0, 1]");
        }
        if (!Double.isFinite(valleyFloorAdvantage)) {
            throw new IllegalArgumentException("valleyFloorAdvantage must be finite");
        }
        double constructorDisplacement = Math.hypot(
                physicalPosition.x() - semanticCenter.x(),
                physicalPosition.z() - semanticCenter.z());
        if (constructorDisplacement > searchRadius + 1.0e-10) {
            throw new IllegalArgumentException("physical network node lies outside its semantic search region");
        }
    }

    public double displacement() {
        return Math.hypot(
                physicalPosition.x() - semanticCenter.x(),
                physicalPosition.z() - semanticCenter.z());
    }
}
