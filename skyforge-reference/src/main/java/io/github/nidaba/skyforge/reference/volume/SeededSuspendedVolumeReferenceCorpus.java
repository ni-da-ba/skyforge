package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.reference.FixedSeedReferenceCorpus;
import java.util.List;
import java.util.Objects;

/** Canonical six-member full-amplitude corpus for bounded suspended-volume enrichment. */
public final class SeededSuspendedVolumeReferenceCorpus {
    /** Stable corpus identifier for the SF-VOL-006 acceptance suite. */
    public static final String CORPUS_ID = "seeded-suspended-volume-v1";

    private SeededSuspendedVolumeReferenceCorpus() {}

    /** Reuses the v0.1 six-seed root-seed suite to preserve cross-version seed coverage. */
    public static List<FixedSeedReferenceCorpus.Member> members() {
        return FixedSeedReferenceCorpus.members();
    }

    /** Returns the canonical full-amplitude suspended descriptor for one corpus member. */
    public static SkyIslandVolumeDescriptor descriptor(FixedSeedReferenceCorpus.Member member) {
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
                1.0,
                base.signalScale());
    }
}
