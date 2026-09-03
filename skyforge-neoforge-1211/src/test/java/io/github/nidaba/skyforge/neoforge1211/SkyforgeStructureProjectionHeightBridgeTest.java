package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SkyforgeStructureProjectionHeightBridgeTest {
    @Test
    void preservesLiveHeightWhenNoPhysicalSkyforgeSurfaceExists() {
        assertEquals(
                91,
                SkyforgeStructureProjectionHeightBridge.selectBaseWorldHeight(
                        91,
                        76,
                        OptionalInt.empty()));
    }

    @Test
    void preservesLiveHeightWhenSkyforgeSurfaceDoesNotRiseAboveNativeDomain() {
        assertEquals(
                91,
                SkyforgeStructureProjectionHeightBridge.selectBaseWorldHeight(
                        91,
                        96,
                        OptionalInt.of(88)));
    }

    @Test
    void preservesLiveHeightWhenHeightmapDoesNotObserveUpperSkyforgeSurface() {
        assertEquals(
                78,
                SkyforgeStructureProjectionHeightBridge.selectBaseWorldHeight(
                        78,
                        76,
                        OptionalInt.of(248)));
    }

    @Test
    void constrainsBaseWorldProjectionWhenUpperSkyforgeSurfaceLiftedLiveHeight() {
        assertEquals(
                76,
                SkyforgeStructureProjectionHeightBridge.selectBaseWorldHeight(
                        249,
                        76,
                        OptionalInt.of(249)));
    }

    @Test
    void alsoConstrainsFeatureRaisedLiveHeightAboveForeignSkyforgeTerrain() {
        assertEquals(
                76,
                SkyforgeStructureProjectionHeightBridge.selectBaseWorldHeight(
                        263,
                        76,
                        OptionalInt.of(249)));
    }
}
