package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import java.util.Objects;

/** Fifteen-member full-amplitude composition corpus reusing the accepted SF-IMP-0018 family matrix. */
public final class ComposedMorphologyReferenceCorpus {
    /** Stable evidence corpus identifier for the first cross-family composition proof. */
    public static final String CORPUS_ID = "composed-morphology-family-suspended-volume-v1";

    private ComposedMorphologyReferenceCorpus() {}

    /** Returns the immutable ordered five-family by three-seed member matrix. */
    public static List<MorphologyFamilyReferenceCorpus.Member> members() {
        return MorphologyFamilyReferenceCorpus.members();
    }

    /** Returns the canonical full-amplitude descriptor for one accepted family member. */
    public static SkyIslandVolumeDescriptor descriptor(MorphologyFamilyReferenceCorpus.Member member) {
        Objects.requireNonNull(member, "member");
        SkyIslandVolumeDescriptor base = MorphologyFamilyReferenceCorpus.descriptor(member);
        return new SkyIslandVolumeDescriptor(
                base.schemaVersion(),
                base.seed(),
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
