package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Samples the union of independently compiled group members on one group-scale review grid. */
public final class SkyIslandGroupEvidenceGenerator {
    /** First group-scale review spacing; individual morphology acceptance remains finer. */
    public static final double HORIZONTAL_SPACING = 16.0;
    public static final double VERTICAL_SPACING = 8.0;
    public static final double HORIZONTAL_MARGIN = 96.0;
    public static final double VERTICAL_MARGIN = 96.0;

    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();

    /** Generates member-aware union evidence for one immutable plan. */
    public SkyIslandGroupEvidence generate(
            SkyIslandGroupPlan plan, List<CompiledSkyIslandVolume> compiledMembers) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(compiledMembers, "compiledMembers");
        if (compiledMembers.size() != plan.memberCount()) {
            throw new IllegalArgumentException("compiled member count differs from group plan");
        }
        VolumeGridSpec grid = reviewGrid(plan);
        int horizontalSamples = grid.xSamples() * grid.zSamples();
        byte[] occupancy = new byte[grid.sampleCount()];
        int[] ownerBySample = new int[grid.sampleCount()];
        Arrays.fill(ownerBySample, -1);
        double[] upperEnvelope = new double[horizontalSamples];
        double[] undersideEnvelope = new double[horizontalSamples];
        int[] horizontalOwner = new int[horizontalSamples];
        Arrays.fill(upperEnvelope, Double.NEGATIVE_INFINITY);
        Arrays.fill(undersideEnvelope, Double.POSITIVE_INFINITY);
        Arrays.fill(horizontalOwner, -1);

        List<ScalarField2> upperFields = new ArrayList<>(compiledMembers.size());
        List<ScalarField2> undersideFields = new ArrayList<>(compiledMembers.size());
        for (CompiledSkyIslandVolume compiled : compiledMembers) {
            upperFields.add(evaluator.field2(compiled.upperSurfaceGraph()));
            undersideFields.add(evaluator.field2(compiled.undersideSurfaceGraph()));
        }
        int[] memberSolidCounts = new int[compiledMembers.size()];
        int overlaps = 0;

        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            double z = grid.zAt(zIndex);
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                double x = grid.xAt(xIndex);
                Coordinate2 point = new Coordinate2(x, z);
                int horizontal = zIndex * grid.xSamples() + xIndex;
                for (int member = 0; member < compiledMembers.size(); member++) {
                    double upper = upperFields.get(member).sample(point);
                    double underside = undersideFields.get(member).sample(point);
                    if (!(upper > underside)) {
                        continue;
                    }
                    if (upper > upperEnvelope[horizontal]) {
                        upperEnvelope[horizontal] = upper;
                        horizontalOwner[horizontal] = member;
                    }
                    undersideEnvelope[horizontal] = Math.min(undersideEnvelope[horizontal], underside);
                    for (int yIndex = 0; yIndex < grid.ySamples(); yIndex++) {
                        double y = grid.yAt(yIndex);
                        if (!(y > underside && y < upper)) {
                            continue;
                        }
                        int index = grid.linearIndex(xIndex, yIndex, zIndex);
                        memberSolidCounts[member]++;
                        if (occupancy[index] != 0) {
                            overlaps++;
                            ownerBySample[index] = -2;
                        } else {
                            occupancy[index] = 1;
                            ownerBySample[index] = member;
                        }
                    }
                }
            }
        }

        OccupancyVolumeGrid union = new OccupancyVolumeGrid(grid, occupancy);
        SkyIslandGroupMetrics metrics = measure(
                plan, union, overlaps, memberSolidCounts);
        return new SkyIslandGroupEvidence(
                plan,
                compiledMembers,
                union,
                ownerBySample,
                upperEnvelope,
                undersideEnvelope,
                horizontalOwner,
                metrics);
    }

    /** Derives a conservative, uniformly spaced review domain from placement reservations. */
    public static VolumeGridSpec reviewGrid(SkyIslandGroupPlan plan) {
        Objects.requireNonNull(plan, "plan");
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (SkyIslandGroupMemberPlan member : plan.members()) {
            var descriptor = member.descriptor();
            minimumX = Math.min(minimumX, descriptor.centerX() - member.reservedHorizontalRadius());
            maximumX = Math.max(maximumX, descriptor.centerX() + member.reservedHorizontalRadius());
            minimumZ = Math.min(minimumZ, descriptor.centerZ() - member.reservedHorizontalRadius());
            maximumZ = Math.max(maximumZ, descriptor.centerZ() + member.reservedHorizontalRadius());
            minimumY = Math.min(minimumY, descriptor.suspensionElevation() - descriptor.undersideDepth());
            maximumY = Math.max(maximumY, descriptor.suspensionElevation() + descriptor.upperElevation());
        }
        minimumX = floorTo(minimumX - HORIZONTAL_MARGIN, HORIZONTAL_SPACING);
        maximumX = ceilTo(maximumX + HORIZONTAL_MARGIN, HORIZONTAL_SPACING);
        minimumZ = floorTo(minimumZ - HORIZONTAL_MARGIN, HORIZONTAL_SPACING);
        maximumZ = ceilTo(maximumZ + HORIZONTAL_MARGIN, HORIZONTAL_SPACING);
        minimumY = floorTo(minimumY - VERTICAL_MARGIN, VERTICAL_SPACING);
        maximumY = ceilTo(maximumY + VERTICAL_MARGIN, VERTICAL_SPACING);
        return new VolumeGridSpec(
                minimumX,
                maximumX,
                minimumY,
                maximumY,
                minimumZ,
                maximumZ,
                samples(minimumX, maximumX, HORIZONTAL_SPACING),
                samples(minimumY, maximumY, VERTICAL_SPACING),
                samples(minimumZ, maximumZ, HORIZONTAL_SPACING));
    }

    private static SkyIslandGroupMetrics measure(
            SkyIslandGroupPlan plan,
            OccupancyVolumeGrid occupancy,
            int overlaps,
            int[] memberSolidCounts) {
        VolumeGridSpec grid = occupancy.specification();
        byte[] solid = occupancy.values();
        int minimumX = grid.xSamples();
        int maximumX = -1;
        int minimumY = grid.ySamples();
        int maximumY = -1;
        int minimumZ = grid.zSamples();
        int maximumZ = -1;
        int faceContacts = 0;
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                for (int x = 0; x < grid.xSamples(); x++) {
                    if (solid[grid.linearIndex(x, y, z)] == 0) {
                        continue;
                    }
                    minimumX = Math.min(minimumX, x);
                    maximumX = Math.max(maximumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumY = Math.max(maximumY, y);
                    minimumZ = Math.min(minimumZ, z);
                    maximumZ = Math.max(maximumZ, z);
                    if (x == 0 || x == grid.xSamples() - 1
                            || y == 0 || y == grid.ySamples() - 1
                            || z == 0 || z == grid.zSamples() - 1) {
                        faceContacts++;
                    }
                }
            }
        }
        if (maximumX < 0) {
            throw new IllegalArgumentException("group realization contains no solid samples");
        }

        ArrayList<Integer> memberCounts = new ArrayList<>(memberSolidCounts.length);
        for (int count : memberSolidCounts) {
            memberCounts.add(count);
        }
        double minimumReservedGap = plan.minimumObservedCenterSpacing()
                - 2.0 * plan.members().get(0).reservedHorizontalRadius();
        return new SkyIslandGroupMetrics(
                plan.memberCount(),
                occupancy.solidSampleCount(),
                connectedComponents(occupancy),
                overlaps,
                faceContacts,
                plan.minimumObservedCenterSpacing(),
                minimumReservedGap,
                new SkyIslandGroupMetrics.Bounds(
                        grid.xAt(minimumX),
                        grid.xAt(maximumX),
                        grid.yAt(minimumY),
                        grid.yAt(maximumY),
                        grid.zAt(minimumZ),
                        grid.zAt(maximumZ)),
                memberCounts);
    }

    private static int connectedComponents(OccupancyVolumeGrid occupancy) {
        VolumeGridSpec grid = occupancy.specification();
        byte[] solid = occupancy.values();
        byte[] visited = new byte[solid.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int components = 0;
        for (int index = 0; index < solid.length; index++) {
            if (solid[index] == 0 || visited[index] != 0) {
                continue;
            }
            components++;
            visited[index] = 1;
            queue.add(index);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int x = current % grid.xSamples();
                int yz = current / grid.xSamples();
                int z = yz % grid.zSamples();
                int y = yz / grid.zSamples();
                visit(x - 1, y, z, grid, solid, visited, queue);
                visit(x + 1, y, z, grid, solid, visited, queue);
                visit(x, y - 1, z, grid, solid, visited, queue);
                visit(x, y + 1, z, grid, solid, visited, queue);
                visit(x, y, z - 1, grid, solid, visited, queue);
                visit(x, y, z + 1, grid, solid, visited, queue);
            }
        }
        return components;
    }

    private static void visit(
            int x,
            int y,
            int z,
            VolumeGridSpec grid,
            byte[] solid,
            byte[] visited,
            ArrayDeque<Integer> queue) {
        if (x < 0 || x >= grid.xSamples()
                || y < 0 || y >= grid.ySamples()
                || z < 0 || z >= grid.zSamples()) {
            return;
        }
        int index = grid.linearIndex(x, y, z);
        if (solid[index] != 0 && visited[index] == 0) {
            visited[index] = 1;
            queue.addLast(index);
        }
    }

    private static double floorTo(double value, double spacing) {
        return Math.floor(value / spacing) * spacing;
    }

    private static double ceilTo(double value, double spacing) {
        return Math.ceil(value / spacing) * spacing;
    }

    private static int samples(double minimum, double maximum, double spacing) {
        return Math.toIntExact(Math.round((maximum - minimum) / spacing) + 1L);
    }
}
