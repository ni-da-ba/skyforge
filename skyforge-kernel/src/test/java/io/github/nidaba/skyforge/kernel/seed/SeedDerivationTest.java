package io.github.nidaba.skyforge.kernel.seed;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SeedDerivationTest {
    @Test
    void matchesVersionOneGoldenVectorsForArbitraryRootBits() {
        assertAll(
                () -> assertEquals(
                        0x675b569513bb25f4L,
                        SeedDerivation.derive(0L, "island.height-detail")),
                () -> assertEquals(
                        0x7f3d233bd3573e9cL,
                        SeedDerivation.derive(1L, "world.primary")),
                () -> assertEquals(
                        0x336049bc234b81e7L,
                        SeedDerivation.derive(Long.MIN_VALUE, "island.height-detail")),
                () -> assertEquals(
                        0xd9f1726d03a02b3fL,
                        SeedDerivation.derive(0x534b59464f524745L, "island.height-detail")));
    }

    @Test
    void rootSeedAndSemanticNamespaceAreIndependentDomains() {
        long baseline = SeedDerivation.derive(42L, "island.height-detail");

        assertAll(
                () -> assertEquals(baseline, SeedDerivation.derive(42L, "island.height-detail")),
                () -> assertNotEquals(baseline, SeedDerivation.derive(43L, "island.height-detail")),
                () -> assertNotEquals(baseline, SeedDerivation.derive(42L, "island.ridge-detail")));
    }

    @Test
    void rejectsNamespacesThatCouldBeSilentlyNormalized() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> SeedDerivation.derive(0L, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> SeedDerivation.derive(0L, "")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SeedDerivation.derive(0L, "Island.Height-Detail")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SeedDerivation.derive(0L, "island..height")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> SeedDerivation.derive(0L, "island_height")));
    }
}
