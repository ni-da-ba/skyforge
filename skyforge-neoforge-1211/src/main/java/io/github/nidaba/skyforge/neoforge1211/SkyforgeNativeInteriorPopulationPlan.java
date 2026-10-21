package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Immutable production plan for already-accepted native interior generation phases in one exact
 * Skyforge volume.
 *
 * <p>The biome resolver is deliberately not stored here. Runtime execution reuses the resolver from
 * {@link SkyforgeNativeSurfacePopulationStage#planForVolume} so surface identity and interior
 * population cannot drift.
 */
record SkyforgeNativeInteriorPopulationPlan(
        SkyIslandWorldVolumeId volumeId,
        List<GenerationStep.Decoration> phases,
        int maximumAttachmentDepth) {

    SkyforgeNativeInteriorPopulationPlan {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(phases, "phases");
        phases = List.copyOf(phases);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("native interior population plan must contain at least one phase");
        }
        if (maximumAttachmentDepth < 0) {
            throw new IllegalArgumentException("maximumAttachmentDepth must be non-negative");
        }
        if (new HashSet<>(phases).size() != phases.size()) {
            throw new IllegalArgumentException("native interior population plan contains duplicate phases");
        }
        int previous = -1;
        for (GenerationStep.Decoration phase : phases) {
            SkyforgeNativeInteriorPopulationPhasePolicy.requireAdmitted(phase);
            if (phase.ordinal() <= previous) {
                throw new IllegalArgumentException(
                        "native interior population phases must preserve Minecraft generation-step order");
            }
            previous = phase.ordinal();
        }
    }

    static SkyforgeNativeInteriorPopulationPlan acceptedNativeInterior(
            SkyIslandWorldVolumeId volumeId,
            int maximumAttachmentDepth) {
        return new SkyforgeNativeInteriorPopulationPlan(
                volumeId,
                SkyforgeNativeInteriorPopulationPhasePolicy.admittedPhases(),
                maximumAttachmentDepth);
    }
}
