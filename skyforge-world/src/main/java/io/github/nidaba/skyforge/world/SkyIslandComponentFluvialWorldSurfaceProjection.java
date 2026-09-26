package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.Objects;

/**
 * F4B backend-neutral projection of an F4A semantic terrain delta onto one compiled world surface.
 *
 * <p>The compiled surface remains authoritative for absolute placement and morphology. F4B applies
 * only the already-qualified F4A delta:
 *
 * <pre>
 * targetUpperWorld = compiledUpperWorld + F4A.deltaPotential * descriptor.reliefBudget
 * </pre>
 *
 * <p>No voxel rounding or density reconciliation occurs here.
 */
public final class SkyIslandComponentFluvialWorldSurfaceProjection {
    private static final double EPSILON = 1.0e-9;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan;
    private final CompiledSkyIslandVolume compiledVolume;
    private final SkyIslandTerrainInterpreter terrainInterpreter;

    public SkyIslandComponentFluvialWorldSurfaceProjection(
            SkyIslandDescriptor descriptor,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan,
            CompiledSkyIslandVolume compiledVolume) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        this.compiledVolume = Objects.requireNonNull(compiledVolume, "compiledVolume");
        if (!descriptor.equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4A candidate descriptor must match F4B projection descriptor");
        }

        SkyIslandVolumeDescriptor volumeDescriptor = compiledVolume.descriptor();
        if (Math.abs(volumeDescriptor.nominalRadius() - descriptor.nominalRadius()) > EPSILON) {
            throw new IllegalArgumentException(
                    "compiled volume nominal radius must match authored semantic radius");
        }
        if (volumeDescriptor.hasSemanticMorphologyFamily()
                && volumeDescriptor.morphologyFamily() != descriptor.morphologyFamily()) {
            throw new IllegalArgumentException(
                    "compiled volume morphology family must match authored semantic family");
        }
        this.terrainInterpreter =
                new SkyIslandTerrainInterpreter(
                        compiledVolume, SkyIslandTerrainProfile.reference());
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan() {
        return candidatePlan;
    }

    public CompiledSkyIslandVolume compiledVolume() {
        return compiledVolume;
    }

    public SkyIslandProjectedFluvialTerrainSample sampleWorld(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) {
            throw new IllegalArgumentException("world coordinates must be finite");
        }
        SkyIslandVolumeDescriptor volumeDescriptor = compiledVolume.descriptor();
        SkyIslandLocalPosition local =
                new SkyIslandLocalPosition(
                        worldX - volumeDescriptor.centerX(),
                        worldZ - volumeDescriptor.centerZ());
        SkyIslandQualifiedFluvialSample semantic =
                candidatePlan.terrainField().sampleDetailed(local);

        double upper = terrainInterpreter.upperSurfaceHeight(worldX, worldZ);
        double underside = terrainInterpreter.undersideSurfaceHeight(worldX, worldZ);
        double deltaWorld =
                semantic.terrainDeltaPotential() * descriptor.reliefBudget();
        double targetUpper = upper + deltaWorld;

        return new SkyIslandProjectedFluvialTerrainSample(
                local,
                semantic,
                upper,
                underside,
                deltaWorld,
                targetUpper);
    }
}
