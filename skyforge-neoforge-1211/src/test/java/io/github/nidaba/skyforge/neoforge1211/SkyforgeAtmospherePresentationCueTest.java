package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeAtmospherePresentationCueTest {
    @Test
    void untrustedAtmosphereCannotProducePlayerFacingCue() {
        SkyforgeAtmospherePresentationCue.Cue cue =
                SkyforgeAtmospherePresentationCue.from(sample(false, 8.0, 4.0, -8.0));

        assertFalse(cue.active());
        assertEquals(0.0, cue.intensity());
        assertEquals(0, cue.particleCount());
    }

    @Test
    void preservesDirectionalAndSignedVerticalAuthorityForPresentation() {
        SkyforgeAtmospherePresentationCue.Cue cue =
                SkyforgeAtmospherePresentationCue.from(sample(true, 1.5, -0.8, -2.0));

        assertTrue(cue.active());
        assertEquals(1.5, cue.horizontalX());
        assertEquals(-0.8, cue.vertical());
        assertEquals(-2.0, cue.horizontalZ());
        assertTrue(cue.audioVolume() > 0.08f);
        assertTrue(cue.audioPitch() > 0.78f);
    }

    @Test
    void weakStillAirDoesNotCreateNoiseOrParticleSpam() {
        SkyforgeAtmospherePresentationCue.Cue cue =
                SkyforgeAtmospherePresentationCue.from(sample(true, 0.1, 0.05, 0.1));

        assertFalse(cue.active());
        assertEquals(0, cue.particleCount());
        assertEquals(0, cue.audioCooldownTicks());
    }

    private static SkyforgeAtmosphereView.Sample sample(
            boolean trusted, double meanX, double updraft, double meanZ) {
        return new SkyforgeAtmosphereView.Sample(
                trusted,
                meanX,
                0.0,
                meanZ,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                updraft,
                0.0,
                trusted ? 1.0 : 0.0,
                trusted ? "L1" : "NONE",
                trusted ? "SERVER_AUTHORITATIVE" : "NONE",
                trusted ? 1L : -1L,
                trusted ? 1L : -1L,
                -1L);
    }
}
