package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0083 deterministic single-island specimen matrix for production morphology visual review.
 *
 * <p>This is reference evidence, not a new semantic-core abstraction. The exact member identities
 * are intended to be shared with Minecraft-side issue #214 rendering so the reference and in-engine
 * reviews inspect the same morphology intent.
 */
public final class ProductionMorphologyVisualReviewCorpus {
    /** Stable corpus identity for the first production morphology visual-quality gate. */
    public static final String CORPUS_ID = "production-morphology-visual-review-v1";

    /** Canonical project-name seed already used by accepted morphology visual corpora. */
    public static final long SKYFORGE_SEED = 0x534b59464f524745L;

    private static final List<Seed> SEEDS =
            List.of(
                    new Seed("seed-min", Long.MIN_VALUE),
                    new Seed("seed-zero", 0L),
                    new Seed("seed-skyforge", SKYFORGE_SEED));

    private static final List<Member> MEMBERS = buildMembers();

    private ProductionMorphologyVisualReviewCorpus() {}

    /** Returns the immutable 41-member isolated production review matrix. */
    public static List<Member> members() {
        return MEMBERS;
    }

    /**
     * Compiles the exact provider-neutral morphology intent carried by one review member.
     *
     * <p>Compilation exercises the production group morphology compiler and accepted provider seam,
     * rather than calling family-specific proof recipes directly.
     */
    public static CompiledSkyIslandVolume compile(Member member) {
        Objects.requireNonNull(member, "member");
        return new SkyIslandMorphologySpecCompiler()
                .compile(descriptor(member), member.morphology(), registry());
    }

    /** Returns the scaled semantic descriptor for one isolated review specimen. */
    public static SkyIslandVolumeDescriptor descriptor(Member member) {
        Objects.requireNonNull(member, "member");
        SkyIslandVolumeDescriptor base = SuspendedVolumeReferenceDomain.descriptor();
        double scale = member.scale().factor();
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                member.seed(),
                0.0,
                0.0,
                512.0,
                base.nominalRadius() * scale,
                base.upperElevation() * scale,
                base.undersideDepth() * scale,
                base.coastalFalloff() * scale,
                base.ridgeAzimuth(),
                base.ridgeStrength(),
                base.undersideTaper(),
                base.undersideAsymmetry(),
                0.0,
                base.signalScale() * scale);
    }

    /**
     * Returns a scale-relative review grid with analytical margin beyond accepted built-in support.
     *
     * <p>No aesthetic threshold is encoded here. The grid only ensures the complete isolated object
     * is visible for evidence generation at comparable image resolution.
     */
    public static VolumeGridSpec reviewGrid(Member member) {
        SkyIslandVolumeDescriptor descriptor = descriptor(member);
        double horizontalExtent = descriptor.nominalRadius() * 2.0;
        double minimumY =
                descriptor.suspensionElevation() - descriptor.undersideDepth() * 2.2;
        double maximumY =
                descriptor.suspensionElevation() + descriptor.upperElevation() * 1.3;
        return new VolumeGridSpec(
                -horizontalExtent,
                horizontalExtent,
                minimumY,
                maximumY,
                -horizontalExtent,
                horizontalExtent,
                97,
                65,
                97);
    }

    /** Returns a registry containing all built-ins plus the accepted reference external provider. */
    public static SkyIslandMorphologyProviderRegistry registry() {
        SkyIslandMorphologyProviderRegistry.Builder builder =
                SkyIslandMorphologyProviderRegistry.builder();
        for (SkyIslandMorphologyProvider provider :
                SkyIslandMorphologyProviders.builtInRegistry().providers()) {
            builder.register(provider);
        }
        return builder.register(new ReferenceCrescentMorphologyProvider()).build();
    }

    private static List<Member> buildMembers() {
        ArrayList<Member> members = new ArrayList<>();

        // Every built-in family at three deterministic seeds on the canonical medium scale.
        for (MorphologyFamily family : MorphologyFamily.values()) {
            for (Seed seed : SEEDS) {
                members.add(
                        new Member(
                                "builtin-"
                                        + family.identifier()
                                        + "-medium-"
                                        + seed.id(),
                                Kind.BUILT_IN,
                                Scale.MEDIUM,
                                seed.seed(),
                                ProviderMorphologySpec.full(
                                        SkyIslandMorphologyProviders.builtInId(family))));
            }
        }

        // Every built-in family at small and large scale using the canonical Skyforge seed.
        for (MorphologyFamily family : MorphologyFamily.values()) {
            for (Scale scale : List.of(Scale.SMALL, Scale.LARGE)) {
                members.add(
                        new Member(
                                "builtin-"
                                        + family.identifier()
                                        + "-"
                                        + scale.id()
                                        + "-seed-skyforge",
                                Kind.BUILT_IN,
                                scale,
                                SKYFORGE_SEED,
                                ProviderMorphologySpec.full(
                                        SkyIslandMorphologyProviders.builtInId(family))));
            }
        }

        // All ten unordered built-in pairs at the midpoint, full detail + full secondary morphology.
        for (HybridMorphologyReferenceCorpus.Pair pair :
                HybridMorphologyReferenceCorpus.pairs()) {
            MorphologyProviderBlend blend =
                    new MorphologyProviderBlend(
                            SkyIslandMorphologyProviders.builtInId(pair.first()),
                            SkyIslandMorphologyProviders.builtInId(pair.second()),
                            0.5);
            members.add(
                    new Member(
                            "hybrid-" + pair.id() + "-midpoint",
                            Kind.BUILT_IN_HYBRID,
                            Scale.MEDIUM,
                            SKYFORGE_SEED,
                            ProviderBlendMorphologySpec.full(blend)));
        }

        // One accepted external-provider endpoint.
        members.add(
                new Member(
                        "provider-crescent-medium-seed-skyforge",
                        Kind.EXTERNAL_PROVIDER,
                        Scale.MEDIUM,
                        SKYFORGE_SEED,
                        ProviderMorphologySpec.full(
                                ReferenceCrescentMorphologyProvider.ID)));

        // One external-provider midpoint axis against every accepted built-in family.
        for (MorphologyFamily family : MorphologyFamily.values()) {
            MorphologyProviderBlend blend =
                    new MorphologyProviderBlend(
                            ReferenceCrescentMorphologyProvider.ID,
                            SkyIslandMorphologyProviders.builtInId(family),
                            0.5);
            members.add(
                    new Member(
                            "provider-crescent-to-"
                                    + family.identifier()
                                    + "-midpoint",
                            Kind.EXTERNAL_PROVIDER_BLEND,
                            Scale.MEDIUM,
                            SKYFORGE_SEED,
                            ProviderBlendMorphologySpec.full(blend)));
        }

        return List.copyOf(members);
    }

    /** Review role used only to organize the visual corpus and downstream Minecraft handoff. */
    public enum Kind {
        BUILT_IN,
        BUILT_IN_HYBRID,
        EXTERNAL_PROVIDER,
        EXTERNAL_PROVIDER_BLEND
    }

    /** Three deliberately separated physical scales for long-/medium-/close-range review. */
    public enum Scale {
        SMALL("small", 0.625),
        MEDIUM("medium", 1.0),
        LARGE("large", 1.5);

        private final String id;
        private final double factor;

        Scale(String id, double factor) {
            this.id = id;
            this.factor = factor;
        }

        public String id() {
            return id;
        }

        public double factor() {
            return factor;
        }
    }

    /** Exact identity and provider-neutral morphology intent for one review specimen. */
    public record Member(
            String id,
            Kind kind,
            Scale scale,
            long seed,
            SkyIslandMorphologySpec morphology) {
        public Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(scale, "scale");
            Objects.requireNonNull(morphology, "morphology");
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException(
                        "review member id must be lowercase hyphenated ASCII");
            }
            if (morphology.detailAmplitude() != 1.0
                    || morphology.secondaryMorphologyAmplitude() != 1.0) {
                throw new IllegalArgumentException(
                        "production review members require full detail and full secondary morphology");
            }
        }
    }

    private record Seed(String id, long seed) {}
}
