package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;

/** Development-only live specimen for SF-IMP-0050 underside-contradiction admission. */
final class SkyforgeNeoForge1211UndersideContradictionDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.undersideContradiction";
    static final int INSPECTION_X = 8;
    static final int INSPECTION_Y = 242;
    static final int INSPECTION_Z = 8;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndersideContradictionDevRuntime.class.getName());
    private static final BoundingBox DETACHED_BELOW_PROOF_BOX = new BoundingBox(4, 150, 4, 6, 152, 6);
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211UndersideContradictionDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "cannot install the SF-IMP-0050 contradiction specimen over an existing Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                SkyforgeNeoForge1211AccommodationDevRuntime.adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0050 underside-contradiction specimen enabled. Create a NEW disposable world using "
                        + "the Skyforge Development world type and inspect near x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + ". The development datapack forces the same isolated origin mansion candidate used by "
                        + "the accepted foundation proof. For admission evidence only, this run appends one detached "
                        + "integer geometry box wholly below the exact island underside. The native start must be "
                        + "restored away and emit 'SF-IMP-0050 UNDERSIDE CONTRADICTION REJECTED'. No synthetic piece "
                        + "is serialized or placed into the world.");
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static boolean isProofCandidate(Structure structure, ChunkPos chunkPos) {
        return enabled()
                && structure instanceof WoodlandMansionStructure
                && chunkPos.x == 0
                && chunkPos.z == 0;
    }

    static List<BoundingBox> candidatePieceBoxes(
            StructureStart start,
            Structure structure,
            ChunkPos chunkPos) {
        ArrayList<BoundingBox> result = new ArrayList<>(start.getPieces().size() + 1);
        start.getPieces().forEach(piece -> result.add(copy(piece.getBoundingBox())));
        if (isProofCandidate(structure, chunkPos)) {
            result.add(copy(DETACHED_BELOW_PROOF_BOX));
        }
        return List.copyOf(result);
    }

    static void requireContradiction(BoundingBox actualStartBounds, SkyIslandWorldVolumeId volumeId) {
        if (enabled()) {
            throw new IllegalStateException(
                    "SF-IMP-0050 fixture invalid: forced origin mansion was not rejected by detached underside "
                            + "contradiction; structureBounds=" + actualStartBounds + ", volume=" + volumeId.path());
        }
    }

    static void recordRejected(
            BoundingBox actualStartBounds,
            MinecraftStructureUndersideContradictionPolicy.Contradiction contradiction) {
        if (!enabled()) {
            return;
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0050 UNDERSIDE CONTRADICTION REJECTED: structureBounds=" + actualStartBounds
                        + ", volume=" + contradiction.supportingVolumeId().path()
                        + ", separatedPieceCount=" + contradiction.separatedComponent().size()
                        + ", separatedBounds=" + contradiction.separatedComponent());
    }

    private static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
