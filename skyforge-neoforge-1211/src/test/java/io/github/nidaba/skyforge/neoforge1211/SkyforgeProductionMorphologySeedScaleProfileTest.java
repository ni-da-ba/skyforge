package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** First SF-IMP-0083 gate: profile exact AUTH-0083 built-in seed/scale support before packaging it. */
final class SkyforgeProductionMorphologySeedScaleProfileTest {
    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final double SOURCE_SUSPENSION = 512.0;
    private static final int MINECRAFT_MIN_Y = -64;
    private static final int MINECRAFT_MAX_Y = 319;

    @Test
    void profilesExactlyTheTwentyRemainingBuiltInAuth0083Members() {
        List<Member> members = members();
        assertEquals(20, members.size());

        var compiler = new SkyIslandMorphologySpecCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var profile = SkyIslandTerrainProfile.reference();

        for (Member member : members) {
            var morphology =
                    ProviderMorphologySpec.full(SkyIslandMorphologyProviders.builtInId(member.family()));
            var compilation = compiler.compileWithSupport(descriptor(member), morphology, registry);
            var certificate = compilation.supportEnvelope().orElseThrow();
            var support =
                    SkyforgeExactVoxelSupportBounds.derive(compilation.volume(), certificate, profile);
            var bounds = support.bounds();

            int minimumY = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.minimumY());
            int maximumY = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.maximumY());
            int verticalSpan = maximumY - minimumY + 1;
            int highestFittingMinimumY = MINECRAFT_MAX_Y - verticalSpan + 1;

            assertTrue(verticalSpan > 0, member.id());
            assertTrue(
                    verticalSpan <= MINECRAFT_MAX_Y - MINECRAFT_MIN_Y + 1,
                    member.id() + " must fit somewhere in the Minecraft build interval");
            assertTrue(support.occupiedColumns() > 0, member.id());
            assertTrue(support.scannedColumns() > support.occupiedColumns(), member.id());

            int minimumChunkX =
                    Math.floorDiv(SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.minimumX()), 16);
            int maximumChunkX =
                    Math.floorDiv(SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.maximumX()), 16);
            int minimumChunkZ =
                    Math.floorDiv(SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.minimumZ()), 16);
            int maximumChunkZ =
                    Math.floorDiv(SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.maximumZ()), 16);
            int footprintChunks =
                    (maximumChunkX - minimumChunkX + 1) * (maximumChunkZ - minimumChunkZ + 1);

            System.out.println(
                    "SF-IMP-0083 PROFILE"
                            + " member=" + member.id()
                            + " family=" + member.family().identifier()
                            + " scale=" + member.scale().id()
                            + " seed=" + Long.toUnsignedString(member.seed())
                            + " bounds=" + bounds
                            + " verticalSpan=" + verticalSpan
                            + " highestFittingMinimumY=" + highestFittingMinimumY
                            + " footprintChunks=" + footprintChunks
                            + " occupiedColumns=" + support.occupiedColumns()
                            + " scannedColumns=" + support.scannedColumns());
        }
    }

    private static List<Member> members() {
        ArrayList<Member> result = new ArrayList<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-min",
                    family,
                    Scale.MEDIUM,
                    Long.MIN_VALUE));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-zero",
                    family,
                    Scale.MEDIUM,
                    0L));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-medium-seed-skyforge",
                    family,
                    Scale.MEDIUM,
                    SKYFORGE_SEED));
            result.add(new Member(
                    "builtin-" + family.identifier() + "-large-seed-skyforge",
                    family,
                    Scale.LARGE,
                    SKYFORGE_SEED));
        }
        return List.copyOf(result);
    }

    private static SkyIslandVolumeDescriptor descriptor(Member member) {
        double scale = member.scale().factor();
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                member.seed(),
                0.0,
                0.0,
                SOURCE_SUSPENSION,
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

    private enum Scale {
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

    private record Member(String id, MorphologyFamily family, Scale scale, long seed) {}
}
