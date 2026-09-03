package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Commits one admitted Skyforge volume's Minecraft biome identity into durable chunk biome storage.
 *
 * <p>Skyforge retains exact block-volume ownership, while Minecraft stores biomes on a coarser
 * 4x4x4 quart lattice. A quart is therefore claimed only when the admitted volume owns at least one
 * solid block inside that cell. Unclaimed quart cells are copied from the chunk's existing biome
 * container byte-for-byte in semantic terms. This keeps vertically separated islands independent:
 * changing a high island does not rewrite the biome cells around unrelated native terrain below it.
 *
 * <p>Minecraft cannot encode two independent biome identities in one quart cell. If another
 * Skyforge volume has any semantic solid claim in the same cell, this first implementation leaves
 * the cell unchanged rather than choosing an order-dependent winner. A later backend quantization
 * policy may resolve such cells once Skyforge authors richer continuous biome fields.
 *
 * <p>Stable LevelChunks are marked unsaved and their vanilla biome packet is sent only to players
 * already tracking that chunk. No chunk ticket, neighbor lookup, or custom networking protocol is
 * introduced.
 */
final class SkyforgePersistentBiomePresentationStage {
    static final String PROOF_PROPERTY = "skyforge.dev.biomePresentation";
    private static final int QUART_WIDTH = 4;
    private static final int BLOCKS_PER_QUART = 4;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgePersistentBiomePresentationStage.class.getName());

    private SkyforgePersistentBiomePresentationStage() {}

    static Result present(
            ServerLevel level,
            LevelChunk chunk,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(volumeId, "volumeId");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("biome-presentation chunk belongs to another level");
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
            throw new IllegalStateException("cannot present biome for a non-admitted Skyforge volume");
        }

        Optional<SkyforgeNativeSurfacePopulationPlan> optionalPlan =
                SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId);
        if (optionalPlan.isEmpty()) {
            return new Result(volumeId, chunk.getPos().toLong(), 0, 0, 0, false);
        }
        SkyforgeNativeSurfacePopulationPlan plan = optionalPlan.orElseThrow();

        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var sampler = level.getChunkSource().randomState().sampler();
        int chunkMinimumX = chunk.getPos().getMinBlockX();
        int chunkMinimumZ = chunk.getPos().getMinBlockZ();
        int baseQuartX = Math.floorDiv(chunkMinimumX, BLOCKS_PER_QUART);
        int baseQuartZ = Math.floorDiv(chunkMinimumZ, BLOCKS_PER_QUART);

        int ownedQuartCells = 0;
        int ambiguousQuartCells = 0;
        int changedQuartCells = 0;
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int baseQuartY = Math.multiplyExact(sectionY, QUART_WIDTH);

            List<Holder<Biome>> original = snapshot(section);
            Map<Integer, Holder<Biome>> replacements = new HashMap<>();
            for (int localQuartY = 0; localQuartY < QUART_WIDTH; localQuartY++) {
                int quartY = baseQuartY + localQuartY;
                for (int localQuartZ = 0; localQuartZ < QUART_WIDTH; localQuartZ++) {
                    int quartZ = baseQuartZ + localQuartZ;
                    for (int localQuartX = 0; localQuartX < QUART_WIDTH; localQuartX++) {
                        int quartX = baseQuartX + localQuartX;
                        Optional<BlockPos> sample = firstOwnedSample(volumeId, quartX, quartY, quartZ);
                        if (sample.isEmpty()) {
                            continue;
                        }
                        ownedQuartCells++;
                        if (containsOtherSkyforgeClaim(volumeId, quartX, quartY, quartZ)) {
                            ambiguousQuartCells++;
                            continue;
                        }

                        BlockPos position = sample.orElseThrow();
                        ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                                plan.biomeResolver().resolve(
                                        volumeId,
                                        position.getX(),
                                        position.getY(),
                                        position.getZ()),
                                "exact-volume biome resolver returned null");
                        Holder<Biome> target = biomeRegistry.getHolder(biomeKey)
                                .orElseThrow(() -> new IllegalStateException(
                                        "exact-volume presentation biome absent from final registry: "
                                                + biomeKey.location()));
                        int index = quartIndex(localQuartX, localQuartY, localQuartZ);
                        if (!original.get(index).equals(target)) {
                            replacements.put(index, target);
                            changedQuartCells++;
                        }
                    }
                }
            }

            if (replacements.isEmpty()) {
                continue;
            }
            section.fillBiomesFromNoise(
                    (quartX, quartY, quartZ, ignoredSampler) -> {
                        int localQuartX = quartX - baseQuartX;
                        int localQuartY = quartY - baseQuartY;
                        int localQuartZ = quartZ - baseQuartZ;
                        if (localQuartX < 0 || localQuartX >= QUART_WIDTH
                                || localQuartY < 0 || localQuartY >= QUART_WIDTH
                                || localQuartZ < 0 || localQuartZ >= QUART_WIDTH) {
                            throw new IllegalStateException("Minecraft requested a biome quart outside its section refill");
                        }
                        int index = quartIndex(localQuartX, localQuartY, localQuartZ);
                        return replacements.getOrDefault(index, original.get(index));
                    },
                    sampler,
                    baseQuartX,
                    baseQuartY,
                    baseQuartZ);
            verifyRefill(section, original, replacements);
        }

        boolean broadcast = false;
        if (changedQuartCells > 0) {
            chunk.setUnsaved(true);
            var packet = ClientboundChunksBiomesPacket.forChunks(List.of(chunk));
            for (var player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) {
                player.connection.send(packet);
                broadcast = true;
            }
        }

        if (Boolean.getBoolean(PROOF_PROPERTY)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-0058 BIOME PRESENTATION: volume=" + volumeId.path()
                            + ", chunk=" + chunk.getPos()
                            + ", ownedQuartCells=" + ownedQuartCells
                            + ", ambiguousQuartCells=" + ambiguousQuartCells
                            + ", changedQuartCells=" + changedQuartCells
                            + ", clientPacketSent=" + broadcast);
        }
        return new Result(
                volumeId,
                chunk.getPos().toLong(),
                ownedQuartCells,
                ambiguousQuartCells,
                changedQuartCells,
                broadcast);
    }

    private static List<Holder<Biome>> snapshot(LevelChunkSection section) {
        List<Holder<Biome>> values = new ArrayList<>(QUART_WIDTH * QUART_WIDTH * QUART_WIDTH);
        for (int localQuartY = 0; localQuartY < QUART_WIDTH; localQuartY++) {
            for (int localQuartZ = 0; localQuartZ < QUART_WIDTH; localQuartZ++) {
                for (int localQuartX = 0; localQuartX < QUART_WIDTH; localQuartX++) {
                    values.add(section.getNoiseBiome(localQuartX, localQuartY, localQuartZ));
                }
            }
        }
        return List.copyOf(values);
    }

    private static void verifyRefill(
            LevelChunkSection section,
            List<Holder<Biome>> original,
            Map<Integer, Holder<Biome>> replacements) {
        for (int localQuartY = 0; localQuartY < QUART_WIDTH; localQuartY++) {
            for (int localQuartZ = 0; localQuartZ < QUART_WIDTH; localQuartZ++) {
                for (int localQuartX = 0; localQuartX < QUART_WIDTH; localQuartX++) {
                    int index = quartIndex(localQuartX, localQuartY, localQuartZ);
                    Holder<Biome> expected = replacements.getOrDefault(index, original.get(index));
                    Holder<Biome> actual = section.getNoiseBiome(localQuartX, localQuartY, localQuartZ);
                    if (!actual.equals(expected)) {
                        throw new IllegalStateException(
                                "biome section refill changed an unclaimed cell or lost an owned replacement");
                    }
                }
            }
        }
    }

    private static Optional<BlockPos> firstOwnedSample(
            SkyIslandWorldVolumeId volumeId,
            int quartX,
            int quartY,
            int quartZ) {
        int minimumX = Math.multiplyExact(quartX, BLOCKS_PER_QUART);
        int minimumY = Math.multiplyExact(quartY, BLOCKS_PER_QUART);
        int minimumZ = Math.multiplyExact(quartZ, BLOCKS_PER_QUART);
        for (int offsetY = 0; offsetY < BLOCKS_PER_QUART; offsetY++) {
            for (int offsetZ = 0; offsetZ < BLOCKS_PER_QUART; offsetZ++) {
                for (int offsetX = 0; offsetX < BLOCKS_PER_QUART; offsetX++) {
                    int worldX = minimumX + offsetX;
                    int worldY = minimumY + offsetY;
                    int worldZ = minimumZ + offsetZ;
                    boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volumeId,
                                    worldX,
                                    worldY,
                                    worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during biome presentation"));
                    if (owned) {
                        return Optional.of(new BlockPos(worldX, worldY, worldZ));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean containsOtherSkyforgeClaim(
            SkyIslandWorldVolumeId volumeId,
            int quartX,
            int quartY,
            int quartZ) {
        int minimumX = Math.multiplyExact(quartX, BLOCKS_PER_QUART);
        int minimumY = Math.multiplyExact(quartY, BLOCKS_PER_QUART);
        int minimumZ = Math.multiplyExact(quartZ, BLOCKS_PER_QUART);
        for (int offsetY = 0; offsetY < BLOCKS_PER_QUART; offsetY++) {
            for (int offsetZ = 0; offsetZ < BLOCKS_PER_QUART; offsetZ++) {
                for (int offsetX = 0; offsetX < BLOCKS_PER_QUART; offsetX++) {
                    boolean other = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                                    volumeId,
                                    minimumX + offsetX,
                                    minimumY + offsetY,
                                    minimumZ + offsetZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during biome ambiguity check"));
                    if (other) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int quartIndex(int localQuartX, int localQuartY, int localQuartZ) {
        return localQuartX + QUART_WIDTH * (localQuartZ + QUART_WIDTH * localQuartY);
    }

    record Result(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            int ownedQuartCells,
            int ambiguousQuartCells,
            int changedQuartCells,
            boolean clientPacketSent) {
        Result {
            Objects.requireNonNull(volumeId, "volumeId");
            if (ownedQuartCells < 0
                    || ambiguousQuartCells < 0
                    || ambiguousQuartCells > ownedQuartCells
                    || changedQuartCells < 0
                    || changedQuartCells > ownedQuartCells - ambiguousQuartCells) {
                throw new IllegalArgumentException("invalid biome-presentation quart counts");
            }
        }
    }
}
