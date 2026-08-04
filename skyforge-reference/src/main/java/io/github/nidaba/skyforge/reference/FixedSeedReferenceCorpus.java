package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Canonical six-member seeded island corpus that closes the v0.1 proof. */
public final class FixedSeedReferenceCorpus {
    /** Stable corpus identifier used by generated reports and artifacts. */
    public static final String CORPUS_ID = "fixed-seed-island-v1";

    private static final List<Member> MEMBERS = List.of(
            new Member("seed-min", Long.MIN_VALUE),
            new Member("seed-negative-one", -1L),
            new Member("seed-zero", 0L),
            new Member("seed-one", 1L),
            new Member("seed-skyforge", 0x534b59464f524745L),
            new Member("seed-max", Long.MAX_VALUE));

    private FixedSeedReferenceCorpus() {}

    /** Returns the immutable ordered corpus membership. */
    public static List<Member> members() {
        return MEMBERS;
    }

    /** Returns the full-amplitude descriptor for one fixed corpus member. */
    public static IslandDescriptor descriptor(Member member) {
        Objects.requireNonNull(member, "member");
        IslandDescriptor base = SignalFreeReferenceCorpus.standardDescriptor();
        return new IslandDescriptor(
                base.schemaVersion(),
                member.seed(),
                base.centerX(),
                base.centerZ(),
                base.nominalRadius(),
                base.maximumElevation(),
                base.coastalFalloff(),
                base.ridgeAzimuth(),
                base.ridgeStrength(),
                1.0,
                base.signalScale());
    }

    /** Stable identifier and root seed for one canonical member. */
    public record Member(String id, long seed) {
        /** Validates the lowercase filesystem-safe member identity. */
        public Member {
            Objects.requireNonNull(id, "id");
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("id must be lowercase hyphenated ASCII");
            }
        }
    }
}
