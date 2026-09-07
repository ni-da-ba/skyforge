package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeThermalSoaringDecisionTest {
    @Test
    void requiresTrustedUsefulLiftToEnter() {
        var inactive = SkyforgeThermalSoaringDecision.State.inactive();

        assertFalse(SkyforgeThermalSoaringDecision.update(inactive, false, 8.0, 100L).soaring());
        assertFalse(SkyforgeThermalSoaringDecision.update(inactive, true, 1.49, 100L).soaring());
        assertTrue(SkyforgeThermalSoaringDecision.update(inactive, true, 1.50, 100L).soaring());
    }

    @Test
    void holdsThenUsesLowerExitThreshold() {
        var entered = SkyforgeThermalSoaringDecision.update(
                SkyforgeThermalSoaringDecision.State.inactive(), true, 2.0, 100L);

        assertTrue(SkyforgeThermalSoaringDecision.update(entered, true, 0.0, 199L).soaring());
        assertFalse(SkyforgeThermalSoaringDecision.update(entered, true, 0.75, 200L).soaring());
        assertTrue(SkyforgeThermalSoaringDecision.update(entered, true, 0.76, 200L).soaring());
    }

    @Test
    void losesSoaringImmediatelyWhenAuthorityIsLost() {
        var entered = SkyforgeThermalSoaringDecision.update(
                SkyforgeThermalSoaringDecision.State.inactive(), true, 2.0, 100L);

        assertFalse(SkyforgeThermalSoaringDecision.update(entered, false, 2.0, 101L).soaring());
    }

    @Test
    void matchesOnlyStockRaptorHuntWindows() {
        assertFalse(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(999L));
        assertTrue(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(1_000L));
        assertTrue(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(5_999L));
        assertFalse(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(6_000L));
        assertTrue(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(8_000L));
        assertFalse(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(11_000L));
        assertTrue(SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(25_000L));
    }
}
