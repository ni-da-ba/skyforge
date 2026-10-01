package io.github.nidaba.skyforge.recipes.skyisland.group;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression proof: pairwise spacing is irrelevant when a group contains only one member. */
final class SkyIslandSingleMemberGroupRequestTest {

    @Test
    void singleMemberGroupDoesNotRequirePairwiseCenterSpacing() {
        var morphology = ProviderMorphologySpec.full(new MorphologyProviderId("example", "single"));
        var request = new SkyIslandGroupRequest(
                0L,
                template(),
                256.0,
                96.0,
                0.0,
                List.of(morphology),
                new SkyIslandGroupLayout.Cluster(1.0, 0.0, 0.0, 0.0));

        assertEquals(1, request.memberCount());
        assertEquals(608.0, request.requiredCenterSpacing());
    }

    private static SkyIslandVolumeDescriptor template() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                0.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }
}
