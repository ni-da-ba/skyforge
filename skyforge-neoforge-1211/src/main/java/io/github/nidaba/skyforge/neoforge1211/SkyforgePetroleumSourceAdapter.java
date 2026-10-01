package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.content.SkyIslandPetroleumLocalSourcePolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Exact-volume petroleum source/depletion and retained-CDG pumpjack termination boundary. */
final class SkyforgePetroleumSourceAdapter {
    static final ResourceLocation TERMINATION_BLOCK =
            ResourceLocation.fromNamespaceAndPath("skyforge", "petroleum_source");

    record SourceAddress(ResourceLocation volumeId, BlockPos termination) implements Comparable<SourceAddress> {
        SourceAddress {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(termination, "termination");
        }
        @Override public int compareTo(SourceAddress other) {
            int volume = volumeId.toString().compareTo(other.volumeId.toString());
            if (volume != 0) return volume;
            int x = Integer.compare(termination.getX(), other.termination.getX());
            if (x != 0) return x;
            int y = Integer.compare(termination.getY(), other.termination.getY());
            return y != 0 ? y : Integer.compare(termination.getZ(), other.termination.getZ());
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

    /** C26 exact-cell provenance paired with a concrete exact-volume termination position. */
    record AdmissibleSupport(SourceAddress address, SkyIslandPetroleumSystemOpportunityCell cell) {
        AdmissibleSupport {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(cell, "cell");
            if (!SkyIslandPetroleumLocalSourcePolicy.locallyEligible(cell)) {
                throw new IllegalArgumentException("zero-opportunity C26 cell cannot support a petroleum source");
            }
        }
    }

    /** Explicit pumpjack seam; CDG's termination tag contains only {@link #TERMINATION_BLOCK}. */
    record PumpjackTermination(SourceAddress address, int pressure) {}

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

    Optional<PumpjackTermination> pumpjackTermination(ResourceLocation exactVolume, BlockPos termination, boolean baseWorld) {
        if (baseWorld) return Optional.empty();
        SourceAddress address = new SourceAddress(exactVolume, termination);
        return supported.containsKey(address) && remaining.get(address) > 0
                ? Optional.of(new PumpjackTermination(address, specification.pumpjackPressure())) : Optional.empty();
    }

    int extract(PumpjackTermination termination, int requestedMillibuckets) {
        Objects.requireNonNull(termination, "termination");
        if (requestedMillibuckets < 0 || !supported.containsKey(termination.address())) {
            throw new IllegalArgumentException("pumpjack request is not an admitted Skyforge source");
        }
        int available = remaining.get(termination.address());
        int extracted = Math.min(available, requestedMillibuckets);
        remaining.put(termination.address(), available - extracted);
        return extracted;
    }

    /** Deterministic save payload for host SavedData. */
    Map<SourceAddress, Integer> save() { return Map.copyOf(remaining); }

    void reload(Map<SourceAddress, Integer> saved) {
        Objects.requireNonNull(saved, "saved");
        if (!saved.keySet().equals(supported.keySet())) throw new IllegalArgumentException("saved state does not match exact source support");
        saved.forEach((address, amount) -> {
            if (amount < 0 || amount > specification.initialMillibuckets()) throw new IllegalArgumentException("invalid saved amount");
        });
        remaining.clear();
        remaining.putAll(saved);
    }
}
