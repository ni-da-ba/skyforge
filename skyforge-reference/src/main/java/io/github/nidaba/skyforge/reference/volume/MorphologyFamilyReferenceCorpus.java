package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fifteen-member primary morphology-family proof corpus: five families by three root seeds. */
public final class MorphologyFamilyReferenceCorpus {
    /** Stable evidence corpus identifier for the first family proof. */
    public static final String CORPUS_ID = "morphology-family-suspended-volume-v1";

    private static final List<SeedMember> SEEDS = List.of(
            new SeedMember("seed-min", Long.MIN_VALUE),
            new SeedMember("seed-zero", 0L),
            new SeedMember("seed-skyforge", 0x534b59464f524745L));

    private static final List<Member> MEMBERS = buildMembers();

    private MorphologyFamilyReferenceCorpus() {}

    /** Returns the immutable ordered fifteen-member family corpus. */
    public static List<Member> members() {
        return MEMBERS;
    }

    /** Returns the zero-signal canonical descriptor carrying this member's root seed. */
    public static SkyIslandVolumeDescriptor descriptor(Member member) {
        Objects.requireNonNull(member, "member");
        SkyIslandVolumeDescriptor base = SuspendedVolumeReferenceDomain.descriptor();
        return new SkyIslandVolumeDescriptor(
                base.schemaVersion(),
                member.seed(),
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

    private static List<Member> buildMembers() {
        List<Member> result = new ArrayList<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            for (SeedMember seed : SEEDS) {
                result.add(new Member(
                        family.identifier() + "-" + seed.id(),
                        family,
                        seed.seed()));
            }
        }
        return List.copyOf(result);
    }

    /** Stable identity, family, and root seed for one proof specimen. */
    public record Member(String id, MorphologyFamily family, long seed) {
        /** Validates the filesystem-safe identity and family. */
        public Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(family, "family");
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("id must be lowercase hyphenated ASCII");
            }
        }
    }

    private record SeedMember(String id, long seed) {}
}
