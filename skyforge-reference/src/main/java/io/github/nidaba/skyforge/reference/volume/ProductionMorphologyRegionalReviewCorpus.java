package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0084 deterministic regional context matrix for issue #214 visual review.
 *
 * <p>This class adds no new planner semantics. Sparse is a deliberately low-density request through
 * the accepted group planner; chain, cluster, Hub, and Arc reuse the already-accepted reference
 * requests exactly.
 */
public final class ProductionMorphologyRegionalReviewCorpus {
    /** Stable evidence identity for the first production regional morphology review. */
    public static final String CORPUS_ID = "production-morphology-regional-review-v1";

    /** Canonical deterministic seed shared with accepted morphology evidence. */
    public static final long SKYFORGE_SEED = 0x534b59464f524745L;

    private static final List<GroupContext> GROUP_CONTEXTS =
            List.of(
                    new GroupContext("sparse", GroupKind.SPARSE, sparse(SKYFORGE_SEED)),
                    new GroupContext(
                            "chain",
                            GroupKind.CHAIN,
                            SkyIslandGroupReferenceCorpus.chain(SKYFORGE_SEED)),
                    new GroupContext(
                            "cluster",
                            GroupKind.CLUSTER,
                            SkyIslandGroupReferenceCorpus.cluster(SKYFORGE_SEED)));

    private static final List<ArchipelagoContext> ARCHIPELAGO_CONTEXTS =
            List.of(
                    new ArchipelagoContext(
                            "hub",
                            ArchipelagoKind.HUB,
                            SkyIslandArchipelagoReferenceCorpus.hub(SKYFORGE_SEED)),
                    new ArchipelagoContext(
                            "arc",
                            ArchipelagoKind.ARC,
                            SkyIslandArchipelagoReferenceCorpus.arc(SKYFORGE_SEED)));

    private ProductionMorphologyRegionalReviewCorpus() {}

    /** Returns the three single-group regional contexts: sparse, chain, cluster. */
    public static List<GroupContext> groupContexts() {
        return GROUP_CONTEXTS;
    }

    /** Returns the two hierarchical regional contexts: Hub and Arc. */
    public static List<ArchipelagoContext> archipelagoContexts() {
        return ARCHIPELAGO_CONTEXTS;
    }

    /** Returns an intentionally low-density five-island group using only accepted planner inputs. */
    public static SkyIslandGroupRequest sparse(long seed) {
        return new SkyIslandGroupRequest(
                seed,
                template(),
                256.0,
                512.0,
                92.0,
                sparseMorphologies(),
                new SkyIslandGroupLayout.Cluster(
                        1600.0,
                        Math.PI / 8.0,
                        0.14,
                        Math.PI / 12.0));
    }

    private static SkyIslandVolumeDescriptor template() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                336.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }

    private static List<SkyIslandMorphologySpec> sparseMorphologies() {
        var massif = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var tableland = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.TABLELAND);
        var spine = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        var basin = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        var lobed = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.LOBED);
        var crescent = ReferenceCrescentMorphologyProvider.ID;
        return List.of(
                ProviderMorphologySpec.full(massif),
                ProviderMorphologySpec.full(crescent),
                ProviderMorphologySpec.full(basin),
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(tableland, lobed, 0.50)),
                ProviderMorphologySpec.full(spine));
    }

    /** Single-group regional-layout role used only by the reference review corpus. */
    public enum GroupKind {
        SPARSE,
        CHAIN,
        CLUSTER
    }

    /** Hierarchical regional-layout role used only by the reference review corpus. */
    public enum ArchipelagoKind {
        HUB,
        ARC
    }

    /** Exact group request associated with one stable review-context ID. */
    public record GroupContext(
            String id,
            GroupKind kind,
            SkyIslandGroupRequest request) {
        public GroupContext {
            requireId(id);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(request, "request");
        }
    }

    /** Exact hierarchical request associated with one stable review-context ID. */
    public record ArchipelagoContext(
            String id,
            ArchipelagoKind kind,
            SkyIslandArchipelagoRequest request) {
        public ArchipelagoContext {
            requireId(id);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(request, "request");
        }
    }

    private static void requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "regional review id must be lowercase hyphenated ASCII");
        }
    }
}
