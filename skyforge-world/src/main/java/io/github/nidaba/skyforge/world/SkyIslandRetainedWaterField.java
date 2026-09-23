package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Arrays;
import java.util.Objects;

/**
 * Continuous backend-neutral sampling of one accepted retained-water footprint.
 *
 * <p>The authored footprint remains the authority. This field only interpolates its existing
 * watershed-node membership, dry-surface and water-depth evidence so a block backend does not turn
 * each coarse planning cell into a rectangular excavation. The 0.5 membership contour is the
 * continuous boundary of the accepted node set; no new basin, outlet or water level is invented.
 */
public final class SkyIslandRetainedWaterField {
    private static final double MEMBERSHIP_THRESHOLD = 0.5;
    private static final double EPSILON = 1.0e-12;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandWaterbodyFootprint footprint;
    private final int gridSize;
    private final double spacing;
    private final double extent;
    private final double[] membership;
    private final double[] surface;
    private final double[] depth;

    private SkyIslandRetainedWaterField(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyFootprint footprint) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.footprint = Objects.requireNonNull(footprint, "footprint");
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        this.gridSize = watershed.gridSize();
        this.spacing = watershed.spacing();
        this.extent = descriptor.nominalRadius();
        this.membership = new double[gridSize * gridSize];
        this.surface = new double[gridSize * gridSize];
        this.depth = new double[gridSize * gridSize];
        Arrays.fill(surface, Double.NaN);

        for (SkyIslandWatershedCell cell : watershed.cells()) {
            if (cell.index() < 0 || cell.index() >= surface.length) {
                throw new IllegalStateException("watershed cell lies outside retained-water interpolation grid");
            }
            surface[cell.index()] = cell.surfacePotential();
        }
        for (double value : surface) {
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("retained-water interpolation grid lost watershed surface evidence");
            }
        }

        for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
            int index = cell.watershedCellIndex();
            if (index < 0 || index >= membership.length) {
                throw new IllegalArgumentException("retained-water cell lies outside watershed grid");
            }
            membership[index] = 1.0;
            depth[index] = cell.waterDepthPotential();

            int gx = index % gridSize;
            int gz = index / gridSize;
            double expectedX = -extent + gx * spacing;
            double expectedZ = -extent + gz * spacing;
            if (Math.abs(expectedX - cell.position().x()) > 1.0e-9
                    || Math.abs(expectedZ - cell.position().z()) > 1.0e-9) {
                throw new IllegalArgumentException(
                        "retained-water footprint cell does not match its watershed lattice identity");
            }
        }
    }

    public static SkyIslandRetainedWaterField create(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyFootprint footprint) {
        return new SkyIslandRetainedWaterField(descriptor, footprint);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandWaterbodyFootprint footprint() {
        return footprint;
    }

    public Sample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        if (position.x() < -extent
                || position.x() > extent
                || position.z() < -extent
                || position.z() > extent) {
            return new Sample(0.0, 0.0, 0.0, footprint.waterSurfacePotential());
        }

        double gx = (position.x() + extent) / spacing;
        double gz = (position.z() + extent) / spacing;
        if (gx < 0.0 || gz < 0.0 || gx > gridSize - 1.0 || gz > gridSize - 1.0) {
            return new Sample(0.0, 0.0, 0.0, footprint.waterSurfacePotential());
        }

        int x0 = Math.min(gridSize - 1, (int) Math.floor(gx));
        int z0 = Math.min(gridSize - 1, (int) Math.floor(gz));
        int x1 = Math.min(gridSize - 1, x0 + 1);
        int z1 = Math.min(gridSize - 1, z0 + 1);
        double tx = smootherstep(gx - x0);
        double tz = smootherstep(gz - z0);

        return new Sample(
                bilinear(membership, x0, z0, x1, z1, tx, tz),
                bilinear(surface, x0, z0, x1, z1, tx, tz),
                bilinear(depth, x0, z0, x1, z1, tx, tz),
                footprint.waterSurfacePotential());
    }

    public boolean contains(SkyIslandLocalPosition position) {
        return sample(position).membershipPotential() >= MEMBERSHIP_THRESHOLD;
    }

    public boolean containsLiteralWater(SkyIslandLocalPosition position) {
        Sample sample = sample(position);
        return sample.membershipPotential() >= MEMBERSHIP_THRESHOLD
                && sample.waterDepthPotential() > EPSILON;
    }

    private double bilinear(
            double[] values,
            int x0,
            int z0,
            int x1,
            int z1,
            double tx,
            double tz) {
        double lower = lerp(values[index(x0, z0)], values[index(x1, z0)], tx);
        double upper = lerp(values[index(x0, z1)], values[index(x1, z1)], tx);
        return lerp(lower, upper, tz);
    }

    private int index(int x, int z) {
        return z * gridSize + x;
    }

    private static double smootherstep(double value) {
        double t = clamp01(value);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Sample(
            double membershipPotential,
            double surfacePotential,
            double waterDepthPotential,
            double waterSurfacePotential) {
        public Sample {
            requireNormalized("membershipPotential", membershipPotential);
            requireNormalized("surfacePotential", surfacePotential);
            requireNormalized("waterDepthPotential", waterDepthPotential);
            requireNormalized("waterSurfacePotential", waterSurfacePotential);
        }

        public boolean insideFootprint() {
            return membershipPotential >= MEMBERSHIP_THRESHOLD;
        }

        public boolean literalWater() {
            return insideFootprint() && waterDepthPotential > EPSILON;
        }

        private static void requireNormalized(String name, double value) {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
            }
        }
    }
}
