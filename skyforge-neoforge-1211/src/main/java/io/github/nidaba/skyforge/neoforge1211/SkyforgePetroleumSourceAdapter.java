package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.content.SkyIslandPetroleumLocalSourcePolicy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Exact-volume petroleum source-field/depletion boundary for the retained CDG pumpjack. */
final class SkyforgePetroleumSourceAdapter {
    record SourceAddress(ResourceLocation volumeId, BlockPos sourceAnchor) implements Comparable<SourceAddress> {
        SourceAddress {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(sourceAnchor, "sourceAnchor");
        }

        boolean matchesWellColumn(BlockPos wellPosition) {
            return sourceAnchor.getX() == wellPosition.getX()
                    && sourceAnchor.getZ() == wellPosition.getZ()
                    && sourceAnchor.getY() < wellPosition.getY();
        }

        @Override
        public int compareTo(SourceAddress other) {
            int volume = volumeId.toString().compareTo(other.volumeId.toString());
            if (volume != 0) return volume;
            int x = Integer.compare(sourceAnchor.getX(), other.sourceAnchor.getX());
            if (x != 0) return x;
            int y = Integer.compare(sourceAnchor.getY(), other.sourceAnchor.getY());
            return y != 0 ? y : Integer.compare(sourceAnchor.getZ(), other.sourceAnchor.getZ());
        }
    }

    /** Fixed Implementation choices; AUTH-0098 magnitude is intentionally absent. */
    record Specification(int initialMillibuckets, int pumpjackPressure) {
        Specification {
            if (initialMillibuckets <= 0 || pumpjackPressure <= 0) {
                throw new IllegalArgumentException("petroleum quantity and pressure must be positive");
            }
        }
    }

    /** C26 exact-cell provenance paired with one realized exact-volume source-field anchor. */
    record AdmissibleSupport(SourceAddress address, SkyIslandPetroleumSystemOpportunityCell cell) {
        AdmissibleSupport {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(cell, "cell");
            if (!SkyIslandPetroleumLocalSourcePolicy.locallyEligible(cell)) {
                throw new IllegalArgumentException("zero-opportunity C26 cell cannot support a petroleum source");
            }
        }
    }

    /** Logical source selected by exact volume and well column; no petroleum block is implied. */
    record PumpjackSource(SourceAddress address, int pressure) {}

    private final Specification specification;
    private final Map<SourceAddress, AdmissibleSupport> supported = new LinkedHashMap<>();
    private final TreeMap<SourceAddress, Integer> remaining = new TreeMap<>();

    SkyforgePetroleumSourceAdapter(Specification specification, Iterable<AdmissibleSupport> support) {
        this.specification = Objects.requireNonNull(specification, "specification");
        for (AdmissibleSupport item : support) {
            if (supported.putIfAbsent(item.address(), item) != null) {
                throw new IllegalArgumentException("duplicate petroleum source address: " + item.address());
            }
        }
        supported.keySet().forEach(address -> remaining.put(address, specification.initialMillibuckets()));
    }

    Optional<PumpjackSource> pumpjackSource(ResourceLocation exactVolume, BlockPos wellPosition, boolean baseWorld) {
        Objects.requireNonNull(exactVolume, "exactVolume");
        Objects.requireNonNull(wellPosition, "wellPosition");
        if (baseWorld) return Optional.empty();
        return supported.keySet().stream()
                .filter(address -> address.volumeId().equals(exactVolume) && address.matchesWellColumn(wellPosition))
                .max(Comparator.comparingInt(address -> address.sourceAnchor().getY()))
                .filter(address -> remaining.getOrDefault(address, 0) > 0)
                .map(address -> new PumpjackSource(address, specification.pumpjackPressure()));
    }

    int extract(PumpjackSource source, int requestedMillibuckets) {
        Objects.requireNonNull(source, "source");
        if (requestedMillibuckets < 0 || !supported.containsKey(source.address())) {
            throw new IllegalArgumentException("pumpjack request is not an admitted Skyforge source");
        }
        int available = remaining.get(source.address());
        int extracted = Math.min(available, requestedMillibuckets);
        remaining.put(source.address(), available - extracted);
        return extracted;
    }

    /** Deterministic save payload for host SavedData. */
    Map<SourceAddress, Integer> save() {
        return Map.copyOf(remaining);
    }

    void reload(Map<SourceAddress, Integer> saved) {
        Objects.requireNonNull(saved, "saved");
        if (!saved.keySet().equals(supported.keySet())) {
            throw new IllegalArgumentException("saved state does not match exact source support");
        }
        saved.forEach((address, amount) -> {
            if (amount < 0 || amount > specification.initialMillibuckets()) {
                throw new IllegalArgumentException("invalid saved amount");
            }
        });
        remaining.clear();
        remaining.putAll(saved);
    }
}
