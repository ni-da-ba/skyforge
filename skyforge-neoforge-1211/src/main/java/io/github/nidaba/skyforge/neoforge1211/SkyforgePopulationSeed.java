package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Deterministic seed derivation for one exact-volume native population operation. */
final class SkyforgePopulationSeed {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SkyforgePopulationSeed() {}

    static long derive(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            ResourceLocation nativeDefinitionKey,
            int generationStep,
            int occurrenceIndex) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(originChunk, "originChunk");
        Objects.requireNonNull(nativeDefinitionKey, "nativeDefinitionKey");
        if (generationStep < 0) {
            throw new IllegalArgumentException("generationStep must be non-negative");
        }
        if (occurrenceIndex < 0) {
            throw new IllegalArgumentException("occurrenceIndex must be non-negative");
        }

        long hash = FNV_OFFSET_BASIS;
        hash = mixLong(hash, volumeId.archipelagoRootSeed());
        hash = mixString(hash, volumeId.groupIdentifier());
        hash = mixLong(hash, volumeId.groupOrdinal());
        hash = mixLong(hash, volumeId.memberOrdinal());
        hash = mixLong(hash, volumeId.geometrySeed());
        hash = mixLong(hash, originChunk.x);
        hash = mixLong(hash, originChunk.z);
        hash = mixString(hash, nativeDefinitionKey.toString());
        hash = mixLong(hash, generationStep);
        hash = mixLong(hash, occurrenceIndex);
        return avalanche(hash);
    }

    private static long mixLong(long hash, long value) {
        long mixed = hash;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private static long mixString(long hash, String value) {
        long mixed = hash;
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            mixed ^= element & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private static long avalanche(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return mixed;
    }
}
