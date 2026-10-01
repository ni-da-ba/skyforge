package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WorldItemCutPressLifecycleResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("skyforge.test.projectDirectory", "."));

    @Test void sourceKeepsBeltlessWorldItemContractBounded() throws Exception {
        String source = Files.readString(PROJECT.resolve("src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeWorldItemCutPressLifecycleAcceptance.java"));
        assertTrue(source.contains("CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE"));
        assertTrue(source.contains("updateEntityAfterFallOn") == false); // authority is runtime path, not direct invocation
        assertTrue(source.contains("CHUNK_READY"));
        assertTrue(source.contains("isPositionEntityTicking"));
        assertTrue(source.contains("FIXTURE_CHUNK_TICKET_ADD"));
        assertTrue(source.contains("FIXTURE_CHUNK_TICKET_REMOVE"));
        assertTrue(source.contains("SAW_WORLD_INPUT"));
        assertTrue(source.contains("SAW.getY() + 0.90"));
        assertTrue(source.contains("input.setDeltaMovement(0.0, -0.08, 0.0)"));
        assertTrue(source.contains("SAW_ACQUIRED"));
        assertTrue(source.contains("sawInventoryObserved"));
        assertTrue(source.contains("SAW_SHAFT"));
        assertTrue(source.contains("PRESS_SHAFT"));
        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("new BlockPos(2, 200, 0)"));
        assertTrue(source.contains("sawMotorState"));
        assertTrue(source.contains("pressMotorState"));
        assertTrue(source.contains("SAW_CUT_PASS"));
        assertTrue(source.contains("pressWorldModeGroundedObserved=true"));
        assertTrue(source.contains("explicitWorldItemHandoff=true"));
        assertTrue(source.contains("sameTransitionEntityUuid=true"));
        assertTrue(source.contains("belts=false depots=false funnels=false chutes=false arms=false"));
        assertTrue(source.contains("productRecipeQualified=false stochasticOutputQualified=false"));
        assertTrue(source.contains("Create 6.0.10"));
        assertTrue(source.contains("ac0c444d9828da3453ae8cc65338e8de063286fb"));
    }

    @Test void developmentRecipeIsExactlyOneCutThenPressLoop() throws Exception {
        String recipe = Files.readString(PROJECT.resolve("src/main/resources/data/skyforge/recipe/sequenced_assembly/platform_015_cut_press.json"));
        assertTrue(recipe.contains("\"type\": \"create:sequenced_assembly\""));
        assertTrue(recipe.contains("\"loops\": 1"));
        int cut = recipe.indexOf("\"type\": \"create:cutting\"");
        int press = recipe.indexOf("\"type\": \"create:pressing\"");
        assertTrue(cut >= 0 && press > cut);
        assertTrue(recipe.contains("\"transitional_item\""));
        assertTrue(recipe.contains("minecraft:iron_nugget"));
        assertTrue(recipe.contains("minecraft:gold_nugget"));
    }
}
