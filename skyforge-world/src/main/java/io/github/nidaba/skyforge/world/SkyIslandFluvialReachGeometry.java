package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral continuous landform scale for one accepted naturalized channel reach.
 *
 * <p>Horizontal quantities are island-local world units. Vertical quantities are normalized authored
 * elevation potentials and therefore remain backend-neutral rather than Minecraft block dimensions.
 */
public record SkyIslandFluvialReachGeometry(
        SkyIslandNaturalizedChannelPath path,
        double bankfullHalfWidth,
        double wetHalfWidth,
        double valleyHalfWidth,
        double bedDepthPotential,
        double waterDepthPotential,
        double bankReliefPotential,
        double crossSectionExponent) {

    public SkyIslandFluvialReachGeometry {
        path = Objects.requireNonNull(path, "path");
        requirePositive("bankfullHalfWidth", bankfullHalfWidth);
        requirePositive("wetHalfWidth", wetHalfWidth);
        requirePositive("valleyHalfWidth", valleyHalfWidth);
        requirePositive("bedDepthPotential", bedDepthPotential);
        requirePositive("waterDepthPotential", waterDepthPotential);
        requirePositive("bankReliefPotential", bankReliefPotential);
        requirePositive("crossSectionExponent", crossSectionExponent);
        if (wetHalfWidth >= bankfullHalfWidth) {
            throw new IllegalArgumentException("wet corridor must remain inside the bankfull corridor");
        }
        if (bankfullHalfWidth >= valleyHalfWidth) {
            throw new IllegalArgumentException("bankfull corridor must remain inside the valley corridor");
        }
        if (waterDepthPotential >= bankReliefPotential) {
            throw new IllegalArgumentException("water depth must remain below authored bank relief");
        }
        if (bankReliefPotential >= bedDepthPotential) {
            throw new IllegalArgumentException("bank relief must remain below total bed lowering");
        }
    }

    public SkyIslandChannelProfile profile() {
        return path.profile();
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
