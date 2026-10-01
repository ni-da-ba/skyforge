package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Conservative structure-level policy for positive underside contradictions.
 *
 * <p>A native start is contradictory only when it has at least one component rooted at the
 * resolved structure floor and a different, geometrically disconnected component whose every
 * piece is proven wholly at or below the exact supporting Skyforge island underside. Piece boxes
 * that overlap or approach within one Minecraft block on every axis are treated as connected so
 * uncertain near-contact geometry is preserved rather than rejected.
 */
final class MinecraftStructureUndersideContradictionPolicy {
    private MinecraftStructureUndersideContradictionPolicy() {}

    static Optional<Contradiction> evaluate(
            List<BoundingBox> pieceBoxes,
            int structureFloorY,
            SkyIslandWorldVolumeId supportingVolumeId) {
        Objects.requireNonNull(supportingVolumeId, "supportingVolumeId");
        return findContradictoryComponent(
                        pieceBoxes,
                        structureFloorY,
                        box -> MinecraftStructurePieceUndersideSeparationProbe.probe(box, supportingVolumeId).isPresent())
                .map(component -> new Contradiction(supportingVolumeId, component));
    }

    /** Pure geometry/proof seam retained for deterministic regression testing. */
    static Optional<List<BoundingBox>> findContradictoryComponent(
            List<BoundingBox> pieceBoxes,
            int structureFloorY,
            Predicate<BoundingBox> whollyBelowProof) {
        Objects.requireNonNull(pieceBoxes, "pieceBoxes");
        Objects.requireNonNull(whollyBelowProof, "whollyBelowProof");
        if (pieceBoxes.isEmpty()) {
            return Optional.empty();
        }

        List<BoundingBox> boxes = pieceBoxes.stream()
                .map(box -> copy(Objects.requireNonNull(box, "pieceBoxes contains null")))
                .toList();
        List<List<BoundingBox>> components = connectedComponents(boxes);
        boolean hasSurfaceRoot = components.stream()
                .anyMatch(component -> component.stream().anyMatch(box -> box.minY() == structureFloorY));
        if (!hasSurfaceRoot) {
            return Optional.empty();
        }

        for (List<BoundingBox> component : components) {
            boolean surfaceRooted = component.stream().anyMatch(box -> box.minY() == structureFloorY);
            if (surfaceRooted) {
                continue;
            }
            boolean whollySeparatedBelow = true;
            for (BoundingBox box : component) {
                if (!whollyBelowProof.test(box)) {
                    whollySeparatedBelow = false;
                    break;
                }
            }
            if (whollySeparatedBelow) {
                return Optional.of(List.copyOf(component));
            }
        }
        return Optional.empty();
    }

    static List<List<BoundingBox>> connectedComponents(List<BoundingBox> pieceBoxes) {
        Objects.requireNonNull(pieceBoxes, "pieceBoxes");
        List<BoundingBox> boxes = pieceBoxes.stream()
                .map(box -> copy(Objects.requireNonNull(box, "pieceBoxes contains null")))
                .toList();
        boolean[] visited = new boolean[boxes.size()];
        ArrayList<List<BoundingBox>> components = new ArrayList<>();
        for (int start = 0; start < boxes.size(); start++) {
            if (visited[start]) {
                continue;
            }
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            ArrayList<BoundingBox> component = new ArrayList<>();
            visited[start] = true;
            pending.add(start);
            while (!pending.isEmpty()) {
                int current = pending.removeFirst();
                BoundingBox currentBox = boxes.get(current);
                component.add(currentBox);
                for (int candidate = 0; candidate < boxes.size(); candidate++) {
                    if (!visited[candidate] && nearOrTouching(currentBox, boxes.get(candidate))) {
                        visited[candidate] = true;
                        pending.addLast(candidate);
                    }
                }
            }
            components.add(List.copyOf(component));
        }
        return List.copyOf(components);
    }

    private static boolean nearOrTouching(BoundingBox left, BoundingBox right) {
        return nearOrTouching(left.minX(), left.maxX(), right.minX(), right.maxX())
                && nearOrTouching(left.minY(), left.maxY(), right.minY(), right.maxY())
                && nearOrTouching(left.minZ(), left.maxZ(), right.minZ(), right.maxZ());
    }

    private static boolean nearOrTouching(int leftMinimum, int leftMaximum, int rightMinimum, int rightMaximum) {
        return (long) leftMinimum <= (long) rightMaximum + 1L
                && (long) rightMinimum <= (long) leftMaximum + 1L;
    }

    private static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    record Contradiction(
            SkyIslandWorldVolumeId supportingVolumeId,
            List<BoundingBox> separatedComponent) {
        Contradiction {
            Objects.requireNonNull(supportingVolumeId, "supportingVolumeId");
            Objects.requireNonNull(separatedComponent, "separatedComponent");
            if (separatedComponent.isEmpty()) {
                throw new IllegalArgumentException("contradiction requires at least one separated piece box");
            }
            separatedComponent = separatedComponent.stream().map(MinecraftStructureUndersideContradictionPolicy::copy).toList();
        }
    }
}
