package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.content.SkyIslandBaseMetalContentPolicy;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Bounded AUTH-0093/C20 Copper realization for one admitted exact Skyforge volume.
 *
 * <p>This deliberately contains one implementation-owned, surface-exposed vanilla Copper Ore
 * block. Its quantity, grade, count, and access are explicit constants rather than
 * interpretations of normalized Authorship opportunity. The terrain block itself is the
 * persisted authority: after a chunk save, deterministic replanning identifies the same owned
 * coordinate and does not add a second deposit.
 */
final class SkyforgeCopperDepositAdapter {
    static final ResourceLocation COPPER_ORE = ResourceLocation.withDefaultNamespace("copper_ore");

    enum Grade { STANDARD }

    enum Accessibility { SURFACE_EXPOSED }

    /** Explicit Implementation choices for this representative seam, not resource policy. */
    record Specification(int blockCount, Grade grade, Accessibility accessibility) {
        Specification {
            if (blockCount != 1) {
                throw new IllegalArgumentException("the bounded Copper representative contains exactly one block");
            }
            grade = Objects.requireNonNull(grade, "grade");
            accessibility = Objects.requireNonNull(accessibility, "accessibility");
        }

        static Specification representative() {
            return new Specification(1, Grade.STANDARD, Accessibility.SURFACE_EXPOSED);
        }
    }

    /** Stable persisted deployment identity; no opportunity magnitude is stored as quantity or grade. */
    record Deployment(SkyIslandWorldVolumeId volumeId, BlockPos position, Specification specification) {
        Deployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            position = Objects.requireNonNull(position, "position");
            specification = Objects.requireNonNull(specification, "specification");
        }

        ChunkPos chunkPos() {
            return new ChunkPos(position);
        }
    }

    private SkyforgeCopperDepositAdapter() {}

    /**
     * Selects the first exact owned top block in stable X/Z order. Zero AUTH-0093 opportunity
     * fails closed; C20's post-flight-province guarantee remains a scope-selection responsibility.
     */
    static Optional<Deployment> plan(
            SkyIslandBaseMetalOpportunityProfile opportunity,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Specification specification) {
        Objects.requireNonNull(opportunity, "opportunity");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(specification, "specification");
        if (!SkyIslandBaseMetalContentPolicy.geologicallyEligible(opportunity, SkyIslandBaseMetalKind.COPPER)) {
            return Optional.empty();
        }

        int minimumX = (int) Math.floor(volume.bounds().minimumX());
        int maximumX = (int) Math.ceil(volume.bounds().maximumX());
        int minimumZ = (int) Math.floor(volume.bounds().minimumZ());
        int maximumZ = (int) Math.ceil(volume.bounds().maximumZ());
        for (int worldX = minimumX; worldX <= maximumX; worldX++) {
            for (int worldZ = minimumZ; worldZ <= maximumZ; worldZ++) {
                var range = terrain.integerSolidRange(volume.id(), worldX, worldZ);
                if (range.isEmpty()) {
                    continue;
                }
                int topY = range.orElseThrow().maximumY();
                BlockPos candidate = new BlockPos(worldX, topY, worldZ);
                if (terrain.isSolidOwnedByOtherVolume(volume.id(), worldX, topY, worldZ)) {
                    continue;
                }
                return Optional.of(new Deployment(volume.id(), candidate, specification));
            }
        }
        return Optional.empty();
    }

    /** Writes the retained vanilla Copper identity once; a saved/reloaded block is an idempotent replay. */
    static Placement apply(ChunkAccess chunk, Deployment deployment) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(deployment, "deployment");
        if (!chunk.getPos().equals(deployment.chunkPos())) {
            throw new IllegalArgumentException("Copper deployment belongs to a different chunk");
        }
        if (chunk.getBlockState(deployment.position()).is(Blocks.COPPER_ORE)) {
            return new Placement(deployment, false);
        }
        if (chunk.getBlockState(deployment.position()).isAir()) {
            throw new IllegalStateException("Copper deployment lost its exact owned surface support");
        }
        chunk.setBlockState(deployment.position(), Blocks.COPPER_ORE.defaultBlockState(), false);
        return new Placement(deployment, true);
    }

    record Placement(Deployment deployment, boolean writtenNow) {
        Placement {
            deployment = Objects.requireNonNull(deployment, "deployment");
        }
    }
}
