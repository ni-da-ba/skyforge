package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** F4F centerline refinement evidence for one F4A-realized reach. */
public record SkyIslandWorldWaterHeadRefinementReach(
        SkyIslandHydraulicReachGeometry reach,
        SkyIslandWorldWaterHeadRefinementStatus status,
        List<SkyIslandWorldWaterHeadRefinementSample> samples,
        Optional<SkyIslandHydraulicQpResult> solve,
        Optional<String> diagnostic) {

    public SkyIslandWorldWaterHeadRefinementReach {
        reach = Objects.requireNonNull(reach, "reach");
        status = Objects.requireNonNull(status, "status");
        samples = List.copyOf(samples);
        solve = Objects.requireNonNull(solve, "solve");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        samples.forEach(value -> Objects.requireNonNull(value, "sample"));

        if (status == SkyIslandWorldWaterHeadRefinementStatus.SOLVED) {
            if (samples.size() != reach.centerline().points().size()
                    || solve.isEmpty()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                    || diagnostic.isPresent()) {
                throw new IllegalArgumentException("solved F4F reach requires complete QP/sample evidence");
            }
        } else if (!samples.isEmpty() || diagnostic.isEmpty()) {
            throw new IllegalArgumentException("unsolved F4F reach requires diagnostic and no samples");
        }
    }

    public double maximumTerrainRaiseWorld() {
        return samples.stream()
                .mapToDouble(SkyIslandWorldWaterHeadRefinementSample::terrainRaiseWorld)
                .max()
                .orElse(0.0);
    }

    public int uphillSegments(double tolerance) {
        int count = 0;
        for (int i = 0; i + 1 < samples.size(); i++) {
            if (samples.get(i + 1).refinedWaterHeadWorld()
                    > samples.get(i).refinedWaterHeadWorld() + tolerance) {
                count++;
            }
        }
        return count;
    }
}
