package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeAtmosphereViewTest {
    @Test
    void preservesAuthoritativeQuantitiesWithoutSkyforgeRemapping() {
        SkyforgeAtmosphereView.Sample sample = new SkyforgeAtmosphereView.Sample(
                true,
                3.0,
                -1.0,
                4.0,
                0.5,
                -0.25,
                1.0,
                -42.0,
                1.6,
                -2.0,
                0.04,
                0.8,
                "L1",
                "SERVER_AUTHORITATIVE",
                10L,
                11L,
                -1L);

        assertTrue(sample.trustedForGameplay());
        assertEquals(3.5, sample.effectiveX());
        assertEquals(-1.25, sample.effectiveY());
        assertEquals(5.0, sample.effectiveZ());
        assertEquals(-42.0, sample.pressure());
        assertEquals(1.6, sample.turbulenceIntensity());
        assertEquals(-2.0, sample.updraftMetersPerSecond());
        assertEquals(0.04, sample.windShearMagnitudePerBlock());
        assertEquals(0.8, sample.confidence());
        assertEquals("L1", sample.sourceLevel());
        assertEquals("SERVER_AUTHORITATIVE", sample.authority());
        assertEquals(10L, sample.l1Epoch());
        assertEquals(11L, sample.worldDeltaEpoch());
        assertEquals(-1L, sample.l2Epoch());
    }

    @Test
    void unavailableSampleIsInertAndExplicitlyUntrusted() {
        SkyforgeAtmosphereView.Sample sample = SkyforgeAtmosphereView.Sample.unavailable();

        assertFalse(sample.trustedForGameplay());
        assertEquals(0.0, sample.effectiveX());
        assertEquals(0.0, sample.effectiveY());
        assertEquals(0.0, sample.effectiveZ());
        assertEquals(0.0, sample.updraftMetersPerSecond());
        assertEquals(0.0, sample.confidence());
        assertEquals("NONE", sample.sourceLevel());
        assertEquals("NONE", sample.authority());
        assertEquals(-1L, sample.l1Epoch());
        assertEquals(-1L, sample.worldDeltaEpoch());
        assertEquals(-1L, sample.l2Epoch());
    }
}
