package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import java.util.List;
import java.util.Objects;

/** Complete group-scale evidence for one planned and compiled suspended-island group. */
public record SkyIslandGroupEvidence(
        SkyIslandGroupPlan plan,
        List<CompiledSkyIslandVolume> compiledMembers,
        OccupancyVolumeGrid occupancy,
        int[] ownerBySample,
        double[] upperEnvelope,
        double[] undersideEnvelope,
        int[] ownerByHorizontalSample,
        SkyIslandGroupMetrics metrics) {

    public SkyIslandGroupEvidence {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(compiledMembers, "compiledMembers");
        Objects.requireNonNull(occupancy, "occupancy");
        Objects.requireNonNull(ownerBySample, "ownerBySample");
        Objects.requireNonNull(upperEnvelope, "upperEnvelope");
        Objects.requireNonNull(undersideEnvelope, "undersideEnvelope");
        Objects.requireNonNull(ownerByHorizontalSample, "ownerByHorizontalSample");
        Objects.requireNonNull(metrics, "metrics");
        compiledMembers = List.copyOf(compiledMembers);
        if (compiledMembers.size() != plan.memberCount()) {
            throw new IllegalArgumentException("compiled member count differs from group plan");
        }
        int volumeSamples = occupancy.specification().sampleCount();
        int horizontalSamples = occupancy.specification().xSamples()
                * occupancy.specification().zSamples();
        if (ownerBySample.length != volumeSamples
                || upperEnvelope.length != horizontalSamples
                || undersideEnvelope.length != horizontalSamples
                || ownerByHorizontalSample.length != horizontalSamples) {
            throw new IllegalArgumentException("group evidence array dimensions do not match grid");
        }
        ownerBySample = ownerBySample.clone();
        upperEnvelope = upperEnvelope.clone();
        undersideEnvelope = undersideEnvelope.clone();
        ownerByHorizontalSample = ownerByHorizontalSample.clone();
    }

    @Override
    public int[] ownerBySample() {
        return ownerBySample.clone();
    }

    @Override
    public double[] upperEnvelope() {
        return upperEnvelope.clone();
    }

    @Override
    public double[] undersideEnvelope() {
        return undersideEnvelope.clone();
    }

    @Override
    public int[] ownerByHorizontalSample() {
        return ownerByHorizontalSample.clone();
    }
}
