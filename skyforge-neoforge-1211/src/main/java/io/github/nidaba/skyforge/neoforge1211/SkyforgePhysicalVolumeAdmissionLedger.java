package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * Transactional physical-admission ledger for independently compiled Skyforge volumes.
 *
 * <p>One observed conflict is sufficient to reject a volume immediately. A volume can become
 * admitted only after every chunk in its finite broad X/Z footprint has supplied clear occupancy
 * evidence. Until then it remains PLANNED and must not be destructively realized.
 */
final class SkyforgePhysicalVolumeAdmissionLedger {
    private final Map<SkyIslandWorldVolumeId, Entry> entries;

    SkyforgePhysicalVolumeAdmissionLedger(Collection<SkyIslandWorldVolume> volumes) {
        Objects.requireNonNull(volumes, "volumes");
        Map<SkyIslandWorldVolumeId, Entry> created = new HashMap<>();
        for (SkyIslandWorldVolume volume : volumes) {
            Objects.requireNonNull(volume, "volume");
            Entry previous = created.put(
                    volume.id(),
                    new Entry(requiredChunkKeys(volume.bounds())));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate physical-admission volume id: " + volume.id().path());
            }
        }
        entries = created;
    }

    synchronized Observation observe(SkyforgeNativeChunkOccupancySurvey.Result survey) {
        Objects.requireNonNull(survey, "survey");
        Entry entry = requireEntry(survey.volumeId());
        if (!entry.requiredChunkKeys.contains(survey.chunkKey())) {
            throw new IllegalArgumentException("occupancy survey chunk is outside planned volume footprint: "
                    + survey.volumeId().path() + "/" + ChunkPos.getX(survey.chunkKey()) + "/" + ChunkPos.getZ(survey.chunkKey()));
        }

        SkyforgeNativeChunkOccupancySurvey.Result previous = entry.surveys.get(survey.chunkKey());
        if (previous != null) {
            if (!previous.equals(survey)) {
                throw new IllegalStateException("native occupancy evidence changed for an already surveyed volume/chunk");
            }
            return snapshot(survey.volumeId(), entry, false);
        }

        if (entry.state != SkyforgePhysicalVolumeAdmissionState.PLANNED) {
            // Terminal decisions are immutable. Additional previously unseen evidence cannot reopen
            // or alter a committed decision.
            return snapshot(survey.volumeId(), entry, false);
        }

        entry.surveys.put(survey.chunkKey(), survey);
        boolean transitioned = false;
        if (survey.conflicts()) {
            entry.state = SkyforgePhysicalVolumeAdmissionState.REJECTED;
            entry.firstConflict = survey.firstConflict();
            transitioned = true;
        } else if (entry.surveys.size() == entry.requiredChunkKeys.size()) {
            entry.state = SkyforgePhysicalVolumeAdmissionState.ADMITTED;
            transitioned = true;
        }
        return snapshot(survey.volumeId(), entry, transitioned);
    }

    synchronized SkyforgePhysicalVolumeAdmissionState state(SkyIslandWorldVolumeId volumeId) {
        return requireEntry(volumeId).state;
    }

    synchronized boolean admitted(SkyIslandWorldVolumeId volumeId) {
        return state(volumeId) == SkyforgePhysicalVolumeAdmissionState.ADMITTED;
    }

    synchronized Observation snapshot(SkyIslandWorldVolumeId volumeId) {
        return snapshot(volumeId, requireEntry(volumeId), false);
    }

    private Entry requireEntry(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Entry entry = entries.get(volumeId);
        if (entry == null) {
            throw new IllegalArgumentException("unknown physical-admission volume: " + volumeId.path());
        }
        return entry;
    }

    private static Observation snapshot(
            SkyIslandWorldVolumeId volumeId,
            Entry entry,
            boolean transitioned) {
        return new Observation(
                volumeId,
                entry.state,
                entry.surveys.size(),
                entry.requiredChunkKeys.size(),
                transitioned,
                entry.firstConflict);
    }

    private static Set<Long> requiredChunkKeys(WorldBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        int minimumBlockX = floorToInt(bounds.minimumX());
        int maximumBlockX = floorToInt(bounds.maximumX());
        int minimumBlockZ = floorToInt(bounds.minimumZ());
        int maximumBlockZ = floorToInt(bounds.maximumZ());
        int minimumChunkX = Math.floorDiv(minimumBlockX, 16);
        int maximumChunkX = Math.floorDiv(maximumBlockX, 16);
        int minimumChunkZ = Math.floorDiv(minimumBlockZ, 16);
        int maximumChunkZ = Math.floorDiv(maximumBlockZ, 16);

        Set<Long> chunks = new HashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        return Set.copyOf(chunks);
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world bound exceeds Minecraft integer coordinates: " + value);
        }
        return (int) floored;
    }

    private static final class Entry {
        private final Set<Long> requiredChunkKeys;
        private final Map<Long, SkyforgeNativeChunkOccupancySurvey.Result> surveys = new HashMap<>();
        private SkyforgePhysicalVolumeAdmissionState state = SkyforgePhysicalVolumeAdmissionState.PLANNED;
        private Optional<SkyforgeNativeChunkOccupancySurvey.Conflict> firstConflict = Optional.empty();

        private Entry(Set<Long> requiredChunkKeys) {
            this.requiredChunkKeys = Set.copyOf(requiredChunkKeys);
            if (this.requiredChunkKeys.isEmpty()) {
                throw new IllegalArgumentException("planned physical volume must intersect at least one Minecraft chunk");
            }
        }
    }

    record Observation(
            SkyIslandWorldVolumeId volumeId,
            SkyforgePhysicalVolumeAdmissionState state,
            int observedChunks,
            int requiredChunks,
            boolean transitionedNow,
            Optional<SkyforgeNativeChunkOccupancySurvey.Conflict> firstConflict) {
        Observation {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(firstConflict, "firstConflict");
            if (observedChunks < 0 || requiredChunks <= 0 || observedChunks > requiredChunks) {
                throw new IllegalArgumentException("invalid physical-admission evidence counts");
            }
            if (state == SkyforgePhysicalVolumeAdmissionState.REJECTED && firstConflict.isEmpty()) {
                throw new IllegalArgumentException("rejected physical volume must retain conflict evidence");
            }
            if (state != SkyforgePhysicalVolumeAdmissionState.REJECTED && firstConflict.isPresent()) {
                throw new IllegalArgumentException("non-rejected physical volume cannot carry conflict evidence");
            }
        }
    }
}
