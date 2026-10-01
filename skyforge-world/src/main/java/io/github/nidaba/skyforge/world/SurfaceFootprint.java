package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral union of axis-aligned X/Z rectangles describing physically relevant support area.
 *
 * <p>The rectangles need not overlap or form one connected component. That distinction is useful
 * for native structures whose actual pieces occupy separated buildings inside one much larger
 * enclosing structure-start box.
 */
public record SurfaceFootprint(List<SurfaceFootprintRectangle> rectangles) {

    /** Freezes a non-empty rectangle union. */
    public SurfaceFootprint {
        Objects.requireNonNull(rectangles, "rectangles");
        if (rectangles.isEmpty()) {
            throw new IllegalArgumentException("surface footprint requires at least one rectangle");
        }
        rectangles = List.copyOf(rectangles);
        rectangles.forEach(rectangle -> Objects.requireNonNull(rectangle, "rectangles contains null"));
    }

    /** Convenience factory for the historical single-rectangle footprint. */
    public static SurfaceFootprint rectangle(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ) {
        return new SurfaceFootprint(List.of(
                new SurfaceFootprintRectangle(minimumX, maximumX, minimumZ, maximumZ)));
    }

    public double minimumX() {
        return rectangles.stream().mapToDouble(SurfaceFootprintRectangle::minimumX).min().orElseThrow();
    }

    public double maximumX() {
        return rectangles.stream().mapToDouble(SurfaceFootprintRectangle::maximumX).max().orElseThrow();
    }

    public double minimumZ() {
        return rectangles.stream().mapToDouble(SurfaceFootprintRectangle::minimumZ).min().orElseThrow();
    }

    public double maximumZ() {
        return rectangles.stream().mapToDouble(SurfaceFootprintRectangle::maximumZ).max().orElseThrow();
    }

    /** Returns whether at least one footprint rectangle contains the supplied sample. */
    public boolean contains(double x, double z) {
        return rectangles.stream().anyMatch(rectangle -> rectangle.contains(x, z));
    }

    /** Returns whether at least one rectangle contains the sample after uniform expansion. */
    public boolean expandedContains(double x, double z, double expansion) {
        return rectangles.stream().anyMatch(rectangle -> rectangle.expandedContains(x, z, expansion));
    }
}
