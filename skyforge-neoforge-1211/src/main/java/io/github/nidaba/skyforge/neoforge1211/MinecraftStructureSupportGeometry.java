package io.github.nidaba.skyforge.neoforge1211;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Extracts generic support geometry from native Minecraft structure pieces.
 *
 * <p>Only pieces whose minimum Y equals the resolved structure-start minimum Y are treated as
 * floor-contact geometry in this milestone. Higher pieces are ordinary superstructure and do not
 * enlarge the support footprint merely because their enclosing start box projects over the same
 * X/Z area. If a valid native start exposes no such piece, the historical enclosing start box is
 * retained as a conservative fallback rather than guessing structure semantics.
 */
final class MinecraftStructureSupportGeometry {
    private MinecraftStructureSupportGeometry() {}

    static List<BoundingBox> floorContactBoxes(StructureStart start) {
        Objects.requireNonNull(start, "start");
        int floorY = start.getBoundingBox().minY();
        Set<HorizontalBoxKey> seen = new LinkedHashSet<>();
        ArrayList<BoundingBox> result = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.minY() != floorY) {
                continue;
            }
            HorizontalBoxKey key = new HorizontalBoxKey(box.minX(), box.maxX(), box.minZ(), box.maxZ());
            if (seen.add(key)) {
                result.add(copyAtFloor(box, floorY));
            }
        }
        if (result.isEmpty()) {
            BoundingBox box = start.getBoundingBox();
            result.add(copyAtFloor(box, floorY));
        }
        return List.copyOf(result);
    }

    private static BoundingBox copyAtFloor(BoundingBox box, int floorY) {
        return new BoundingBox(
                box.minX(),
                floorY,
                box.minZ(),
                box.maxX(),
                floorY,
                box.maxZ());
    }

    private record HorizontalBoxKey(int minimumX, int maximumX, int minimumZ, int maximumZ) {}
}
