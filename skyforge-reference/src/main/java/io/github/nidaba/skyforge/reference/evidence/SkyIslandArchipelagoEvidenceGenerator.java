package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Samples an independently compiled hierarchy on one regional review grid. */
public final class SkyIslandArchipelagoEvidenceGenerator {
    /** Regional review spacing; group and island acceptance remain finer. */
    public static final double HORIZONTAL_SPACING = 32.0;
    public static final double VERTICAL_SPACING = 8.0;
    public static final double HORIZONTAL_MARGIN = 192.0;
    public static final double VERTICAL_MARGIN = 128.0;

    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final SkyIslandMorphologySpecCompiler compiler = new SkyIslandMorphologySpecCompiler();

    /** Compiles every child island independently and samples the realized archipelago union. */
    public SkyIslandArchipelagoEvidence generate(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");

        List<CompiledSkyIslandVolume> compiledMembers = new ArrayList<>(plan.totalMemberCount());
        List<Integer> groupByMember = new ArrayList<>(plan.totalMemberCount());
        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            List<CompiledSkyIslandVolume> compiledGroup = compiler.compile(group.groupPlan(), registry);
            for (CompiledSkyIslandVolume compiled : compiledGroup) {
                compiledMembers.add(compiled);
                groupByMember.add(group.ordinal());
            }
        }

        VolumeGridSpec grid = reviewGrid(plan);
        int horizontalSamples = grid.xSamples() * grid.zSamples();
        byte[] occupancy = new byte[grid.sampleCount()];
        // Temporary compact ownership used only while detecting cross-group overlap. Zero means air,
        // positive values are group ordinal + 1, and -1 denotes an already-overlapped voxel.
        byte[] voxelGroupOwner = new byte[grid.sampleCount()];
        double[] upperEnvelope = new double[horizontalSamples];
        double[] undersideEnvelope = new double[horizontalSamples];
        int[] horizontalGroupOwner = new int[horizontalSamples];
        Arrays.fill(upperEnvelope, Double.NEGATIVE_INFINITY);
        Arrays.fill(undersideEnvelope, Double.POSITIVE_INFINITY);
        Arrays.fill(horizontalGroupOwner, -1);

        List<ScalarField2> upperFields = new ArrayList<>(compiledMembers.size());
        List<ScalarField2> undersideFields = new ArrayList<>(compiledMembers.size());
        for (CompiledSkyIslandVolume compiled : compiledMembers) {
            upperFields.add(evaluator.field2(compiled.upperSurfaceGraph()));
            undersideFields.add(evaluator.field2(compiled.undersideSurfaceGraph()));
        }

        int[] islandSolidCounts = new int[compiledMembers.size()];
        int[] groupSolidCounts = new int[plan.groupCount()];
        int overlaps = 0;
        int crossGroupOverlaps = 0;

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
                    int memberGroup = groupByMember.get(member);
                    if (upper > upperEnvelope[horizontal]) {
                        upperEnvelope[horizontal] = upper;
                        horizontalGroupOwner[horizontal] = memberGroup;
                    }
                    undersideEnvelope[horizontal] = Math.min(undersideEnvelope[horizontal], underside);
                    byte encodedGroup = (byte) (memberGroup + 1);
                    for (int yIndex = 0; yIndex < grid.ySamples(); yIndex++) {
                        double y = grid.yAt(yIndex);
                        if (!(y > underside && y < upper)) {
                            continue;
                        }
                        int index = grid.linearIndex(xIndex, yIndex, zIndex);
                        islandSolidCounts[member]++;
                        groupSolidCounts[memberGroup]++;
                        if (occupancy[index] != 0) {
                            overlaps++;
                            byte existingGroup = voxelGroupOwner[index];
                            if (existingGroup > 0 && existingGroup != encodedGroup) {
                                crossGroupOverlaps++;
                            }
                            voxelGroupOwner[index] = -1;
                        } else {
                            occupancy[index] = 1;
                            voxelGroupOwner[index] = encodedGroup;
                        }
                    }
                }
            }
        }

        OccupancyVolumeGrid union = new OccupancyVolumeGrid(grid, occupancy);
        SkyIslandArchipelagoMetrics metrics = measure(
                plan,
                union,
                overlaps,
                crossGroupOverlaps,
                groupSolidCounts,
                islandSolidCounts);
        return new SkyIslandArchipelagoEvidence(
                plan,
                compiledMembers,
                groupByMember,
                union,
                upperEnvelope,
                undersideEnvelope,
                horizontalGroupOwner,
                metrics);
    }

    /** Conservative regional domain derived from group reservations and child vertical extents. */
    public static VolumeGridSpec reviewGrid(SkyIslandArchipelagoPlan plan) {
        Objects.requireNonNull(plan, "plan");
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            minimumX = Math.min(minimumX, group.centerX() - group.reservedGroupRadius());
            maximumX = Math.max(maximumX, group.centerX() + group.reservedGroupRadius());
            minimumZ = Math.min(minimumZ, group.centerZ() - group.reservedGroupRadius());
            maximumZ = Math.max(maximumZ, group.centerZ() + group.reservedGroupRadius());
            for (SkyIslandGroupMemberPlan member : group.groupPlan().members()) {
                var descriptor = member.descriptor();
                minimumY = Math.min(minimumY, descriptor.suspensionElevation() - descriptor.undersideDepth());
                maximumY = Math.max(maximumY, descriptor.suspensionElevation() + descriptor.upperElevation());
            }
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

    private static SkyIslandArchipelagoMetrics measure(
            SkyIslandArchipelagoPlan plan,
            OccupancyVolumeGrid occupancy,
            int overlaps,
            int crossGroupOverlaps,
            int[] groupSolidCounts,
            int[] islandSolidCounts) {
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
            throw new IllegalArgumentException("archipelago realization contains no solid samples");
        }
        ArrayList<Integer> groupCounts = new ArrayList<>(groupSolidCounts.length);
        for (int count : groupSolidCounts) {
            groupCounts.add(count);
        }
        ArrayList<Integer> islandCounts = new ArrayList<>(islandSolidCounts.length);
        for (int count : islandSolidCounts) {
            islandCounts.add(count);
        }
        return new SkyIslandArchipelagoMetrics(
                plan.groupCount(),
                plan.totalMemberCount(),
                occupancy.solidSampleCount(),
                connectedComponents(occupancy),
                overlaps,
                crossGroupOverlaps,
                faceContacts,
                plan.minimumObservedGroupGap(),
                new SkyIslandArchipelagoMetrics.Bounds(
                        grid.xAt(minimumX),
                        grid.xAt(maximumX),
                        grid.yAt(minimumY),
                        grid.yAt(maximumY),
                        grid.zAt(minimumZ),
                        grid.zAt(maximumZ)),
                groupCounts,
                islandCounts);
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
