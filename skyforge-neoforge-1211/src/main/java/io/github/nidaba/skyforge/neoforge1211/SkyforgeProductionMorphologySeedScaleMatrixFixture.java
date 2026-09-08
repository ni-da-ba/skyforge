package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * Exact SF-IMP-0083 fixtures for the remaining built-in AUTH-0083 seed/scale matrix.
 *
 * <p>All twenty members remain exhaustively available to cheap deterministic tests. Expensive
 * Minecraft lifecycle review carries one exact member at a time in a short development-only
 * dimension so evidence cost scales with the selected risk representative rather than with a
 * four-member vertical stack.
 */
final class SkyforgeProductionMorphologySeedScaleMatrixFixture {
    static final long SKYFORGE_SEED = 0x534b59464f524745L;
    static final int REVIEW_DIMENSION_MIN_Y = 320;
    static final int REVIEW_DIMENSION_HEIGHT = 544;
    static final int REVIEW_DIMENSION_MAX_Y_EXCLUSIVE =
            REVIEW_DIMENSION_MIN_Y + REVIEW_DIMENSION_HEIGHT;

    private static final long ROOT_SEED = 0x5346494d50303083L;
    private static final double SOURCE_SUSPENSION = 512.0;
    private static final int TARGET_MINIMUM_SOLID_Y = 336;
    private static final List<Member> MEMBERS = buildMembers();
    private static final Set<String> REPRESENTATIVE_MEMBER_IDS = Set.of(
            "builtin-massif-medium-seed-skyforge",
            "builtin-tableland-medium-seed-skyforge",
            "builtin-spine-medium-seed-min",
            "builtin-basin-medium-seed-zero",
            "builtin-lobed-medium-seed-skyforge",
            "builtin-massif-large-seed-skyforge",
            "builtin-spine-large-seed-skyforge");

    private SkyforgeProductionMorphologySeedScaleMatrixFixture() {}

    static List<Member> members() {
        return MEMBERS;
    }

    static List<Member> members(MorphologyFamily family) {
        Objects.requireNonNull(family, "family");
        return MEMBERS.stream().filter(member -> member.family() == family).toList();
    }

    static Set<String> representativeMemberIds() {
        return REPRESENTATIVE_MEMBER_IDS;
    }

    static MorphologyFamily family(String id) {
        Objects.requireNonNull(id, "id");
        for (MorphologyFamily family : MorphologyFamily.values()) {
            if (family.identifier().equals(id)) {
                return family;
            }
        }
        throw new IllegalArgumentException("unknown SF-IMP-0083 morphology family: " + id);
    }

    static Member member(MorphologyFamily family, String commandId) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(commandId, "commandId");
        return members(family).stream()
                .filter(member -> member.commandId().equals(commandId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown SF-IMP-0083 member " + family.identifier() + "/" + commandId));
    }

    static FamilyFixture buildMember(MorphologyFamily family, String commandId) {
        Member member = member(family, commandId);
        var compiler = new SkyIslandMorphologySpecCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var profile = SkyIslandTerrainProfile.reference();
        ProviderMorphologySpec morphology =
                ProviderMorphologySpec.full(SkyIslandMorphologyProviders.builtInId(family));

        SkyIslandVolumeDescriptor sourceDescriptor = descriptor(member, SOURCE_SUSPENSION);
        var sourceCompilation = compiler.compileWithSupport(sourceDescriptor, morphology, registry);
        var sourceCertificate = sourceCompilation.supportEnvelope().orElseThrow(
                () -> new IllegalStateException(member.id() + " unexpectedly lost certified support"));
        var sourceSupport = SkyforgeExactVoxelSupportBounds.derive(
                sourceCompilation.volume(), sourceCertificate, profile);

        int sourceMinimumY = toExactInt(sourceSupport.bounds().minimumY());
        int verticalTranslation = Math.subtractExact(TARGET_MINIMUM_SOLID_Y, sourceMinimumY);
        SkyIslandVolumeDescriptor translatedDescriptor =
                descriptor(member, SOURCE_SUSPENSION + verticalTranslation);
        var translatedCompilation =
                compiler.compileWithSupport(translatedDescriptor, morphology, registry);
        var translatedCertificate = translatedCompilation.supportEnvelope().orElseThrow();
        var translatedSupport = SkyforgeExactVoxelSupportBounds.derive(
                translatedCompilation.volume(), translatedCertificate, profile);

        requirePureIntegerTranslation(
                member, sourceSupport.bounds(), translatedSupport.bounds(), verticalTranslation);
        int translatedMinimumY = toExactInt(translatedSupport.bounds().minimumY());
        int translatedMaximumY = toExactInt(translatedSupport.bounds().maximumY());
        if (translatedMinimumY != TARGET_MINIMUM_SOLID_Y) {
            throw new IllegalStateException(member.id() + " did not land on the review-carrier minimum Y");
        }
        if (translatedMinimumY <= REVIEW_DIMENSION_MIN_Y) {
            throw new IllegalStateException(member.id() + " must remain wholly above vanilla Overworld generation");
        }
        if ((long) translatedMaximumY + 1L >= REVIEW_DIMENSION_MAX_Y_EXCLUSIVE) {
            throw new IllegalStateException(
                    member.id() + " does not leave an in-dimension air sample above its exact support");
        }

        int memberIndex = members(family).indexOf(member);
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "sf-imp-0083-morphology-seed-scale-" + family.identifier() + "-" + commandId,
                family.ordinal(),
                memberIndex,
                member.seed());
        SkyIslandWorldVolume worldVolume = new SkyIslandWorldVolume(
                volumeId,
                translatedSupport.bounds(),
                translatedCompilation.volume());
        Set<Long> footprint = translatedSupport.occupiedChunkKeys();
        MemberFixture fixture = new MemberFixture(
                member,
                morphology.stableIdentifier(),
                sourceDescriptor,
                translatedDescriptor,
                verticalTranslation,
                sourceSupport,
                translatedSupport,
                worldVolume,
                footprint);

        return new FamilyFixture(
                family,
                List.of(fixture),
                new SkyIslandWorldCatalog(ROOT_SEED, List.of(worldVolume)),
                footprint);
    }

    static SkyIslandVolumeDescriptor sourceDescriptor(Member member) {
        return descriptor(Objects.requireNonNull(member, "member"), SOURCE_SUSPENSION);
    }

    static Set<Long> footprintChunkKeys(WorldBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        int minimumChunkX = Math.floorDiv(toExactInt(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv(toExactInt(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv(toExactInt(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv(toExactInt(bounds.maximumZ()), 16);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                result.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    static int toExactInt(double value) {
        int integer = (int) value;
        if (integer != value) {
            throw new IllegalArgumentException("expected exact integer coordinate, found " + value);
        }
        return integer;
    }

    private static SkyIslandVolumeDescriptor descriptor(Member member, double suspensionElevation) {
        double scale = member.scale().factor();
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                member.seed(),
                0.0,
                0.0,
                suspensionElevation,
                256.0 * scale,
                96.0 * scale,
                128.0 * scale,
                64.0 * scale,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                32.0 * scale);
    }

    private static void requirePureIntegerTranslation(
            Member member,
            WorldBounds source,
            WorldBounds translated,
            int deltaY) {
        if (source.minimumX() != translated.minimumX()
                || source.maximumX() != translated.maximumX()
                || source.minimumZ() != translated.minimumZ()
                || source.maximumZ() != translated.maximumZ()
                || source.minimumY() + deltaY != translated.minimumY()
                || source.maximumY() + deltaY != translated.maximumY()) {
            throw new IllegalStateException(
                    member.id() + " review relocation changed exact AUTH-0083 discrete support");
        }
    }

    private static List<Member> buildMembers() {
        ArrayList<Member> result = new ArrayList<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-min",
                    "medium-seed-min",
                    family,
                    Scale.MEDIUM,
                    Long.MIN_VALUE));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-zero",
                    "medium-seed-zero",
                    family,
                    Scale.MEDIUM,
                    0L));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-skyforge",
                    "medium-seed-skyforge",
                    family,
                    Scale.MEDIUM,
                    SKYFORGE_SEED));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-large-seed-skyforge",
                    "large-seed-skyforge",
                    family,
                    Scale.LARGE,
                    SKYFORGE_SEED));
        }
        return List.copyOf(result);
    }

    enum Scale {
        MEDIUM("medium", 1.0),
        LARGE("large", 1.5);

        private final String id;
        private final double factor;

        Scale(String id, double factor) {
            this.id = id;
            this.factor = factor;
        }

        String id() {
            return id;
        }

        double factor() {
            return factor;
        }
    }

    record Member(String id, String commandId, MorphologyFamily family, Scale scale, long seed) {
        Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(commandId, "commandId");
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(scale, "scale");
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || !commandId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("SF-IMP-0083 member IDs must be lowercase hyphenated ASCII");
            }
        }

        String scaleId() {
            return scale.id();
        }

        String familyId() {
            return family.identifier().toLowerCase(Locale.ROOT);
        }
    }

    record MemberFixture(
            Member member,
            String morphologyIdentifier,
            SkyIslandVolumeDescriptor sourceDescriptor,
            SkyIslandVolumeDescriptor translatedDescriptor,
            int verticalTranslation,
            SkyforgeExactVoxelSupportBounds.Result sourceSupport,
            SkyforgeExactVoxelSupportBounds.Result exactSupport,
            SkyIslandWorldVolume worldVolume,
            Set<Long> footprintChunkKeys) {
        MemberFixture {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(morphologyIdentifier, "morphologyIdentifier");
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            Objects.requireNonNull(translatedDescriptor, "translatedDescriptor");
            Objects.requireNonNull(sourceSupport, "sourceSupport");
            Objects.requireNonNull(exactSupport, "exactSupport");
            Objects.requireNonNull(worldVolume, "worldVolume");
            footprintChunkKeys = Set.copyOf(Objects.requireNonNull(footprintChunkKeys, "footprintChunkKeys"));
            if (footprintChunkKeys.isEmpty()) {
                throw new IllegalArgumentException("SF-IMP-0083 member footprint must not be empty");
            }
        }

        SkyIslandWorldVolumeId volumeId() {
            return worldVolume.id();
        }
    }

    record FamilyFixture(
            MorphologyFamily family,
            List<MemberFixture> members,
            SkyIslandWorldCatalog catalog,
            Set<Long> footprintChunkKeys) {
        FamilyFixture {
            Objects.requireNonNull(family, "family");
            members = List.copyOf(Objects.requireNonNull(members, "members"));
            Objects.requireNonNull(catalog, "catalog");
            footprintChunkKeys = Set.copyOf(Objects.requireNonNull(footprintChunkKeys, "footprintChunkKeys"));
            if (members.size() != 1 || catalog.volumes().size() != 1 || footprintChunkKeys.isEmpty()) {
                throw new IllegalArgumentException("SF-IMP-0083 review fixture must contain one exact volume");
            }
        }

        MemberFixture member() {
            return members.getFirst();
        }
    }
}
