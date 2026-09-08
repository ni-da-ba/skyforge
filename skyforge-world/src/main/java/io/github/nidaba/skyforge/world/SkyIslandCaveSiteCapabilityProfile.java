package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** AUTH-0099 deterministic cave-system site capability evidence for one authored island. */
public record SkyIslandCaveSiteCapabilityProfile(
        SkyIslandDescriptor descriptor,
        SkyIslandCaveSystemPlan topology,
        SkyIslandCaveGeometryPlan geometry,
        SkyIslandCaveExposurePlan exposure,
        SkyIslandMaterialFamilyPlan materialPlan,
        List<SkyIslandCaveSiteCapabilitySystem> systems) {

    public SkyIslandCaveSiteCapabilityProfile {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        topology = Objects.requireNonNull(topology, "topology");
        geometry = Objects.requireNonNull(geometry, "geometry");
        exposure = Objects.requireNonNull(exposure, "exposure");
        materialPlan = Objects.requireNonNull(materialPlan, "materialPlan");
        systems = List.copyOf(systems);
        systems.forEach(system -> Objects.requireNonNull(system, "cave site system"));

        if (!topology.descriptor().equals(descriptor)
                || !geometry.descriptor().equals(descriptor)
                || !exposure.descriptor().equals(descriptor)
                || !materialPlan.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "cave site capability sources must belong to one exact authored descriptor");
        }
        if (!geometry.topology().equals(topology)
                || !exposure.geometry().equals(geometry)) {
            throw new IllegalArgumentException(
                    "cave site capability must retain exact accepted topology/geometry/exposure chain");
        }
        if (systems.size() != topology.systems().size()) {
            throw new IllegalArgumentException(
                    "cave site capability must cover every exact cave system");
        }
        for (int ordinal = 0; ordinal < systems.size(); ordinal++) {
            if (!systems.get(ordinal).sourceSystem().equals(topology.systems().get(ordinal))) {
                throw new IllegalArgumentException(
                        "cave site capability must preserve exact cave-system order/identity");
            }
        }
    }

    public int systemCount() {
        return systems.size();
    }

    public long exposedSystemCount() {
        return systems.stream()
                .filter(SkyIslandCaveSiteCapabilitySystem::hasAcceptedExteriorExposure)
                .count();
    }
}
