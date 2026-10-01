package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Immutable adapter-level plan for native surface population of one exact Skyforge volume. */
record SkyforgeNativeSurfacePopulationPlan(
        SkyIslandWorldVolumeId volumeId,
        SkyforgeExactVolumeBiomeResolver biomeResolver,
        List<GenerationStep.Decoration> phases,
        int maximumAttachmentDepth) {

    SkyforgeNativeSurfacePopulationPlan {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(phases, "phases");
        phases = List.copyOf(phases);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("surface population plan must contain at least one admitted phase");
        }
        if (maximumAttachmentDepth < 0) {
            throw new IllegalArgumentException("maximumAttachmentDepth must be non-negative");
        }
        if (new HashSet<>(phases).size() != phases.size()) {
            throw new IllegalArgumentException("surface population plan contains duplicate phases");
        }
        int previousOrdinal = -1;
        for (GenerationStep.Decoration phase : phases) {
            SkyforgeSurfacePopulationPhasePolicy.requireAdmitted(phase);
            if (phase.ordinal() <= previousOrdinal) {
                throw new IllegalArgumentException("surface population phases must preserve native generation order");
            }
            previousOrdinal = phase.ordinal();
        }
    }

    static SkyforgeNativeSurfacePopulationPlan surfaceEcology(
            SkyIslandWorldVolumeId volumeId,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            int maximumAttachmentDepth) {
        return new SkyforgeNativeSurfacePopulationPlan(
                volumeId,
                biomeResolver,
                SkyforgeSurfacePopulationPhasePolicy.admittedPhases(),
                maximumAttachmentDepth);
    }
}
