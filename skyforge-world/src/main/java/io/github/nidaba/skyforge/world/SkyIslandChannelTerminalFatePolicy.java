package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** One fail-closed mapping from semantic channel-terminal fate to transition ownership. */
public final class SkyIslandChannelTerminalFatePolicy {
    private SkyIslandChannelTerminalFatePolicy() {}

    public static Optional<SkyIslandQualifiedFluvialDeferralReason> deferralReason(
            SkyIslandChannelTerminalFateKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case EDGE_OUTLET -> Optional.empty();
            case RETAINED_OPEN_WATER -> Optional.of(
                    SkyIslandQualifiedFluvialDeferralReason
                            .RETAINED_WATER_TRANSITION_REQUIRED);
            case RETAINED_WETLAND -> Optional.of(
                    SkyIslandQualifiedFluvialDeferralReason
                            .WETLAND_TRANSITION_REQUIRED);
            case UNRESOLVED -> Optional.of(
                    SkyIslandQualifiedFluvialDeferralReason
                            .UNRESOLVED_TERMINAL_FATE);
        };
    }
}
