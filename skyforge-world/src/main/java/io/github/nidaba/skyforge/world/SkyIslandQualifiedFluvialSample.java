package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** One backend-neutral sample from the qualified fluvial realization field. */
public record SkyIslandQualifiedFluvialSample(
        double originalTerrainPotential,
        double targetTerrainPotential,
        double terrainDeltaPotential,
        boolean wet,
        double waterSurfacePotential,
        SkyIslandQualifiedFluvialZone zone,
        Optional<SkyIslandQualifiedFluvialProvenance> provenance) {

    public SkyIslandQualifiedFluvialSample {
        requireNormalized(originalTerrainPotential, "originalTerrainPotential");
        requireNormalized(targetTerrainPotential, "targetTerrainPotential");
        if (!Double.isFinite(terrainDeltaPotential)) {
            throw new IllegalArgumentException("terrainDeltaPotential must be finite");
        }
        if (targetTerrainPotential > originalTerrainPotential + 1.0e-12) {
            throw new IllegalArgumentException("qualified fluvial realization must not raise terrain");
        }
        if (Math.abs(
                        terrainDeltaPotential
                                - (targetTerrainPotential - originalTerrainPotential))
                > 1.0e-10) {
            throw new IllegalArgumentException("terrainDeltaPotential must match target-original");
        }
        if (!Double.isFinite(waterSurfacePotential)
                || waterSurfacePotential < 0.0
                || waterSurfacePotential > 1.0) {
            throw new IllegalArgumentException("waterSurfacePotential must be finite and normalized");
        }
        zone = Objects.requireNonNull(zone, "zone");
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (zone == SkyIslandQualifiedFluvialZone.UNAFFECTED && provenance.isPresent()) {
            throw new IllegalArgumentException("unaffected samples cannot carry fluvial provenance");
        }
        if (zone != SkyIslandQualifiedFluvialZone.UNAFFECTED && provenance.isEmpty()) {
            throw new IllegalArgumentException("affected samples require fluvial provenance");
        }
        if (wet && zone == SkyIslandQualifiedFluvialZone.UNAFFECTED) {
            throw new IllegalArgumentException("unaffected samples cannot be wet");
        }
    }

    public static SkyIslandQualifiedFluvialSample unaffected(double terrainPotential) {
        requireNormalized(terrainPotential, "terrainPotential");
        return new SkyIslandQualifiedFluvialSample(
                terrainPotential,
                terrainPotential,
                0.0,
                false,
                terrainPotential,
                SkyIslandQualifiedFluvialZone.UNAFFECTED,
                Optional.empty());
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0,1]");
        }
    }
}
