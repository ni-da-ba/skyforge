package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Explicit F4A deferral for one otherwise F3E-qualified terminal component. */
public record SkyIslandComponentFluvialTerrainDeferral(
        SkyIslandHydraulicTerminalComponent component,
        List<SkyIslandComponentFluvialTerrainDeferralReason> reasons,
        String diagnostic) {

    public SkyIslandComponentFluvialTerrainDeferral(
            SkyIslandHydraulicTerminalComponent component,
            List<SkyIslandComponentFluvialTerrainDeferralReason> reasons) {
        this(component, reasons, "");
    }

    public SkyIslandComponentFluvialTerrainDeferral {
        component = Objects.requireNonNull(component, "component");
        reasons = List.copyOf(reasons);
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        reasons.forEach(value -> Objects.requireNonNull(value, "deferral reason"));
        if (component.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
            throw new IllegalArgumentException(
                    "F4A deferral is only meaningful for an F3E-qualified component");
        }
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "deferred qualified component requires at least one terrain reason");
        }
        if (diagnostic.length() > 512) {
            diagnostic = diagnostic.substring(0, 512);
        }
    }
}
