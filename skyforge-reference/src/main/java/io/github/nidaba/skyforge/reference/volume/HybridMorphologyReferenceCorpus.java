package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical pairwise hybrid corpora for SF-IMP-0022 numerical and visual acceptance. */
public final class HybridMorphologyReferenceCorpus {
    /** Stable evidence identifier for the first pairwise primary-hybrid proof. */
    public static final String CORPUS_ID = "hybrid-morphology-suspended-volume-v1";

    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final List<SeedMember> SEEDS = List.of(
            new SeedMember("seed-min", Long.MIN_VALUE),
            new SeedMember("seed-zero", 0L),
            new SeedMember("seed-skyforge", SKYFORGE_SEED));
    private static final double[] REVIEW_WEIGHTS = {0.25, 0.50, 0.75};
    private static final List<Pair> PAIRS = buildPairs();
    private static final List<AcceptanceMember> ACCEPTANCE_MEMBERS = buildAcceptanceMembers();
    private static final List<ReviewMember> REVIEW_MEMBERS = buildReviewMembers();

    private HybridMorphologyReferenceCorpus() {}

    /** Returns all ten unordered pairs of distinct built-in morphology families. */
    public static List<Pair> pairs() {
        return PAIRS;
    }

    /** Returns the thirty-member canonical midpoint acceptance corpus. */
    public static List<AcceptanceMember> acceptanceMembers() {
        return ACCEPTANCE_MEMBERS;
    }

    /** Returns the thirty-member 25/50/75-percent Skyforge-seed review corpus. */
    public static List<ReviewMember> reviewMembers() {
        return REVIEW_MEMBERS;
    }

    /** Returns the signal-free schema-1 descriptor carrying the supplied root seed. */
    public static SkyIslandVolumeDescriptor descriptor(long seed) {
        SkyIslandVolumeDescriptor base = SuspendedVolumeReferenceDomain.descriptor();
        return new SkyIslandVolumeDescriptor(
                base.schemaVersion(),
                seed,
                base.centerX(),
                base.centerZ(),
                base.suspensionElevation(),
                base.nominalRadius(),
                base.upperElevation(),
                base.undersideDepth(),
                base.coastalFalloff(),
                base.ridgeAzimuth(),
                base.ridgeStrength(),
                base.undersideTaper(),
                base.undersideAsymmetry(),
                0.0,
                base.signalScale());
    }

    private static List<Pair> buildPairs() {
        List<Pair> result = new ArrayList<>();
        MorphologyFamily[] families = MorphologyFamily.values();
        for (int first = 0; first < families.length; first++) {
            for (int second = first + 1; second < families.length; second++) {
                result.add(new Pair(families[first], families[second]));
            }
        }
        return List.copyOf(result);
    }

    private static List<AcceptanceMember> buildAcceptanceMembers() {
        List<AcceptanceMember> result = new ArrayList<>();
        for (Pair pair : PAIRS) {
            for (SeedMember seed : SEEDS) {
                result.add(new AcceptanceMember(
                        pair.id() + "-midpoint-" + seed.id(),
                        pair,
                        seed.seed(),
                        new MorphologyBlend(pair.first(), pair.second(), 0.5)));
            }
        }
        return List.copyOf(result);
    }

    private static List<ReviewMember> buildReviewMembers() {
        List<ReviewMember> result = new ArrayList<>();
        for (Pair pair : PAIRS) {
            for (double weight : REVIEW_WEIGHTS) {
                int percent = (int) Math.round(100.0 * weight);
                result.add(new ReviewMember(
                        pair.id() + "-second-" + percent,
                        pair,
                        SKYFORGE_SEED,
                        new MorphologyBlend(pair.first(), pair.second(), weight),
                        percent));
            }
        }
        return List.copyOf(result);
    }

    /** One canonical unordered built-in family pair. */
    public record Pair(MorphologyFamily first, MorphologyFamily second) {
        /** Validates canonical enum ordering and distinct parents. */
        public Pair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first == second) {
                throw new IllegalArgumentException("hybrid pair parents must be distinct");
            }
            if (first.ordinal() >= second.ordinal()) {
                throw new IllegalArgumentException("hybrid pairs must use canonical enum ordering");
            }
        }

        /** Stable lowercase pair identifier. */
        public String id() {
            return first.identifier() + "-" + second.identifier();
        }
    }

    /** One full-resolution midpoint acceptance specimen. */
    public record AcceptanceMember(
            String id, Pair pair, long seed, MorphologyBlend blend) {
        /** Validates identity and midpoint blend. */
        public AcceptanceMember {
            requireId(id);
            Objects.requireNonNull(pair, "pair");
            Objects.requireNonNull(blend, "blend");
            if (Double.doubleToLongBits(blend.secondWeight())
                    != Double.doubleToLongBits(0.5)) {
                throw new IllegalArgumentException("acceptance member must use midpoint blend");
            }
        }
    }

    /** One lightweight visual interpolation specimen. */
    public record ReviewMember(
            String id, Pair pair, long seed, MorphologyBlend blend, int secondPercent) {
        /** Validates identity and declared percentage. */
        public ReviewMember {
            requireId(id);
            Objects.requireNonNull(pair, "pair");
            Objects.requireNonNull(blend, "blend");
            if (secondPercent != 25 && secondPercent != 50 && secondPercent != 75) {
                throw new IllegalArgumentException("review percentage must be 25, 50, or 75");
            }
        }
    }

    private static void requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("id must be lowercase hyphenated ASCII");
        }
    }

    private record SeedMember(String id, long seed) {}
}
