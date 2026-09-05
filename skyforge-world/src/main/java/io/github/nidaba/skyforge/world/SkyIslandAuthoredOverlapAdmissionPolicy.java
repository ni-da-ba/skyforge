package io.github.nidaba.skyforge.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0050 immutable pairwise overlap policy.
 *
 * <p>Pairs without an explicit rule are strict SEPARATE. Explicit rules therefore exist only to
 * document an intentional stack, permit composition, or redundantly make strict separation
 * visible in authored policy.
 */
public final class SkyIslandAuthoredOverlapAdmissionPolicy {
    private final Map<SkyIslandAuthoredOverlapPairKey, SkyIslandAuthoredOverlapPairRule> rules;

    public SkyIslandAuthoredOverlapAdmissionPolicy(
            List<SkyIslandAuthoredOverlapPairRule> rules) {
        Objects.requireNonNull(rules, "rules");
        Map<SkyIslandAuthoredOverlapPairKey, SkyIslandAuthoredOverlapPairRule> built =
                new HashMap<>();
        for (SkyIslandAuthoredOverlapPairRule rule : rules) {
            rule = Objects.requireNonNull(rule, "overlap rule");
            if (built.put(rule.pair(), rule) != null) {
                throw new IllegalArgumentException(
                        "duplicate authored overlap rule for pair " + rule.pair());
            }
        }
        this.rules = Map.copyOf(built);
    }

    public static SkyIslandAuthoredOverlapAdmissionPolicy strict() {
        return new SkyIslandAuthoredOverlapAdmissionPolicy(List.of());
    }

    public SkyIslandAuthoredOverlapPairRule ruleFor(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        SkyIslandAuthoredOverlapPairKey key =
                SkyIslandAuthoredOverlapPairKey.of(first, second);
        return rules.getOrDefault(
                key,
                new SkyIslandAuthoredOverlapPairRule(
                        key, SkyIslandAuthoredOverlapMode.SEPARATE, 0.0));
    }

    public Map<SkyIslandAuthoredOverlapPairKey, SkyIslandAuthoredOverlapPairRule> explicitRules() {
        return rules;
    }
}
