package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * F3E source-to-terminal network completeness evidence.
 *
 * <p>This is an admission plan only. QUALIFIED means the continuous component has no known
 * ordinary/transition/terminal blocker; it does not itself mutate terrain or authorize Minecraft.
 */
public record SkyIslandHydraulicNetworkAssemblyPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandOrdinarySpanPlan ordinarySpanPlan,
        List<SkyIslandHydraulicReachAssembly> reachAssemblies,
        List<SkyIslandHydraulicTerminalComponent> terminalComponents) {

    public SkyIslandHydraulicNetworkAssemblyPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        ordinarySpanPlan = Objects.requireNonNull(ordinarySpanPlan, "ordinarySpanPlan");
        reachAssemblies = List.copyOf(reachAssemblies);
        terminalComponents = List.copyOf(terminalComponents);
        if (!descriptor.equals(ordinarySpanPlan.descriptor())) {
            throw new IllegalArgumentException("network assembly descriptor must match F3D plan");
        }
        reachAssemblies.forEach(value -> Objects.requireNonNull(value, "reach assembly"));
        terminalComponents.forEach(value -> Objects.requireNonNull(value, "terminal component"));
    }
}
