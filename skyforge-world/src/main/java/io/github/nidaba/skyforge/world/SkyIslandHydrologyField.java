package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Deterministic island-local hydrological planning derived from authored semantic geography.
 *
 * <p>This is not a river-routing or erosion solver. It exposes local causes that later planners can
 * integrate into drainage networks, basins, lakes, wetlands, channels, and edge waterfalls.
 */
public final class SkyIslandHydrologyField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticFieldSet fields;

    private SkyIslandHydrologyField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.fields = SkyIslandSemanticFieldSet.create(descriptor);
    }

    public static SkyIslandHydrologyField create(SkyIslandDescriptor descriptor) {
        return new SkyIslandHydrologyField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandHydrologySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = fields.interiority().sample(position);
        if (interiority <= 0.0) {
            return new SkyIslandHydrologySample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        double radius = descriptor.nominalRadius();
        double step = Math.max(1.0, radius * 0.025);
        double center = fields.elevationTendency().sample(position);
        double east = fields.elevationTendency().sample(new SkyIslandLocalPosition(position.x() + step, position.z()));
        double west = fields.elevationTendency().sample(new SkyIslandLocalPosition(position.x() - step, position.z()));
        double north = fields.elevationTendency().sample(new SkyIslandLocalPosition(position.x(), position.z() + step));
        double south = fields.elevationTendency().sample(new SkyIslandLocalPosition(position.x(), position.z() - step));

        double gradientX = (east - west) * 0.5;
        double gradientZ = (north - south) * 0.5;
        double slope = clamp01(Math.hypot(gradientX, gradientZ) * 5.0);
        double averageNeighbor = (east + west + north + south) * 0.25;
        double depression = clamp01((averageNeighbor - center) * 5.0);

        double moisture = fields.moisture().sample(position);
        double exposure = fields.exposure().sample(position);
        double runoff = clamp01(
                moisture * 0.45
                        + descriptor.hydrologicalPotential() * 0.30
                        + (1.0 - descriptor.permeability()) * 0.20
                        + exposure * 0.05);
        double retention = clamp01(
                depression * 0.48
                        + (1.0 - slope) * 0.18
                        + moisture * 0.16
                        + descriptor.hydrologicalPotential() * 0.18);
        double drainage = clamp01(runoff * (0.38 + 0.62 * slope) * (1.0 - retention * 0.45));
        double edge = 1.0 - interiority;
        double outflow = clamp01(drainage * edge * 1.65);

        double flowMagnitude = Math.hypot(gradientX, gradientZ);
        double flowX = 0.0;
        double flowZ = 0.0;
        if (flowMagnitude > 1.0e-9) {
            flowX = -gradientX / flowMagnitude;
            flowZ = -gradientZ / flowMagnitude;
        } else {
            double radial = Math.hypot(position.x(), position.z());
            if (radial > 1.0e-9) {
                flowX = position.x() / radial;
                flowZ = position.z() / radial;
            }
        }
        return new SkyIslandHydrologySample(runoff, retention, drainage, outflow, flowX, flowZ);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
