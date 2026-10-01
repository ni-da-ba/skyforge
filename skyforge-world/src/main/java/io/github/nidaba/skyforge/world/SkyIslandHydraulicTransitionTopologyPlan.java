package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Exact F3 transition ownership topology.
 *
 * <p>This plan identifies boundaries only. It grants no hydraulic-head, transition-shape, terrain,
 * retained-basin, or backend authority.
 */
public record SkyIslandHydraulicTransitionTopologyPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan,
        List<SkyIslandHydraulicConfluenceTransitionSite> confluences,
        List<SkyIslandHydraulicCascadeTransitionSite> cascades,
        List<SkyIslandHydraulicBasinTransitionSite> basins,
        List<SkyIslandChannelTerminalFate> unresolvedTerminals) {

    public SkyIslandHydraulicTransitionTopologyPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        skeletonPlan = Objects.requireNonNull(skeletonPlan, "skeletonPlan");
        confluences = List.copyOf(confluences);
        cascades = List.copyOf(cascades);
        basins = List.copyOf(basins);
        unresolvedTerminals = List.copyOf(unresolvedTerminals);
        if (!descriptor.equals(skeletonPlan.descriptor())) {
            throw new IllegalArgumentException("transition topology descriptor must match F2B skeleton");
        }
        confluences.forEach(site -> Objects.requireNonNull(site, "confluence site"));
        cascades.forEach(site -> Objects.requireNonNull(site, "cascade site"));
        basins.forEach(site -> Objects.requireNonNull(site, "basin site"));
        unresolvedTerminals.forEach(fate -> {
            Objects.requireNonNull(fate, "unresolved terminal fate");
            if (fate.kind() != SkyIslandChannelTerminalFateKind.UNRESOLVED) {
                throw new IllegalArgumentException(
                        "unresolvedTerminals may contain only UNRESOLVED fates");
            }
        });
    }
}
