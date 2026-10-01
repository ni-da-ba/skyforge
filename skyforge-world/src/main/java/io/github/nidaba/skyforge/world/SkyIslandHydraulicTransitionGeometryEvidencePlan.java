package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * F3A finite transition geometry evidence.
 *
 * <p>No member of this plan may mutate terrain or select authoritative hydraulic head.
 */
public record SkyIslandHydraulicTransitionGeometryEvidencePlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicTransitionTopologyPlan topology,
        List<SkyIslandHydraulicConfluenceGeometryCandidate> confluences,
        List<SkyIslandHydraulicCascadeGeometryCandidate> cascades,
        List<SkyIslandHydraulicBasinInterfaceGeometry> openWaterInterfaces,
        List<SkyIslandHydraulicBasinTransitionSite> deferredWetlandInterfaces) {

    public SkyIslandHydraulicTransitionGeometryEvidencePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        topology = Objects.requireNonNull(topology, "topology");
        confluences = List.copyOf(confluences);
        cascades = List.copyOf(cascades);
        openWaterInterfaces = List.copyOf(openWaterInterfaces);
        deferredWetlandInterfaces = List.copyOf(deferredWetlandInterfaces);
        if (!descriptor.equals(topology.descriptor())) {
            throw new IllegalArgumentException("geometry evidence descriptor must match topology");
        }
        confluences.forEach(value -> Objects.requireNonNull(value, "confluence geometry"));
        cascades.forEach(value -> Objects.requireNonNull(value, "cascade geometry"));
        openWaterInterfaces.forEach(value -> Objects.requireNonNull(value, "open-water interface"));
        deferredWetlandInterfaces.forEach(value -> {
            Objects.requireNonNull(value, "wetland interface");
            if (value.terminalFate().kind()
                    != SkyIslandChannelTerminalFateKind.RETAINED_WETLAND) {
                throw new IllegalArgumentException(
                        "deferredWetlandInterfaces may contain only retained wetlands");
            }
        });
    }
}
