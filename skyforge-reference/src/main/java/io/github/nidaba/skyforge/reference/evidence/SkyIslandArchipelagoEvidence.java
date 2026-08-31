package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import java.util.List;
import java.util.Objects;

/** Complete regional-scale evidence for one hierarchical archipelago realization. */
public record SkyIslandArchipelagoEvidence(
        SkyIslandArchipelagoPlan plan,
        List<CompiledSkyIslandVolume> compiledMembers,
        List<Integer> groupByCompiledMember,
        OccupancyVolumeGrid occupancy,
        int[] islandOwnerBySample,
        int[] groupOwnerBySample,
        double[] upperEnvelope,
        double[] undersideEnvelope,
        int[] horizontalGroupOwner,
        SkyIslandArchipelagoMetrics metrics) {

    public SkyIslandArchipelagoEvidence {
        Objects.requireNonNull(plan, "plan");
        compiledMembers = List.copyOf(compiledMembers);
        groupByCompiledMember = List.copyOf(groupByCompiledMember);
        Objects.requireNonNull(occupancy, "occupancy");
        Objects.requireNonNull(islandOwnerBySample, "islandOwnerBySample");
        Objects.requireNonNull(groupOwnerBySample, "groupOwnerBySample");
        Objects.requireNonNull(upperEnvelope, "upperEnvelope");
        Objects.requireNonNull(undersideEnvelope, "undersideEnvelope");
        Objects.requireNonNull(horizontalGroupOwner, "horizontalGroupOwner");
        Objects.requireNonNull(metrics, "metrics");
        if (compiledMembers.size() != plan.totalMemberCount()
                || groupByCompiledMember.size() != compiledMembers.size()) {
            throw new IllegalArgumentException("compiled archipelago member mapping is inconsistent");
        }
        if (islandOwnerBySample.length != occupancy.specification().sampleCount()
                || groupOwnerBySample.length != occupancy.specification().sampleCount()) {
            throw new IllegalArgumentException("voxel ownership arrays differ from occupancy domain");
        }
        int horizontal = occupancy.specification().xSamples() * occupancy.specification().zSamples();
        if (upperEnvelope.length != horizontal
                || undersideEnvelope.length != horizontal
                || horizontalGroupOwner.length != horizontal) {
            throw new IllegalArgumentException("horizontal evidence arrays differ from occupancy domain");
        }
        islandOwnerBySample = islandOwnerBySample.clone();
        groupOwnerBySample = groupOwnerBySample.clone();
        upperEnvelope = upperEnvelope.clone();
        undersideEnvelope = undersideEnvelope.clone();
        horizontalGroupOwner = horizontalGroupOwner.clone();
    }

    @Override
    public int[] islandOwnerBySample() {
        return islandOwnerBySample.clone();
    }

    @Override
    public int[] groupOwnerBySample() {
        return groupOwnerBySample.clone();
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
    public int[] horizontalGroupOwner() {
        return horizontalGroupOwner.clone();
    }
}
