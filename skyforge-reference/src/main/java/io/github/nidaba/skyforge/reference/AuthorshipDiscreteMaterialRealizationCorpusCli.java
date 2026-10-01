package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionAllocator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionSample;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionRealizer;
import io.github.nidaba.skyforge.world.SkyIslandMaterialRealizationSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecision;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecisionFactory;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionFrontier;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionSelectionMethod;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingKey;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0044 discrete semantic material-realization evidence. */
public final class AuthorshipDiscreteMaterialRealizationCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SAMPLE = 42;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(
                    new SkyIslandMaterialCapabilityProfile(
                            0.94, 0.20, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.92, 0.94, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.94, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.94, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.20, 0.94),
                    SkyIslandMaterialCapabilityProfile.uniform(0.86));

    private AuthorshipDiscreteMaterialRealizationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of(
                        "build",
                        "evidence",
                        "authorship-discrete-material-realization-v1");
        Files.createDirectories(out);

        List<Selection> selections = List.of(
                new Selection("competent", 2332L),
                new Selection("weak", 653L),
                new Selection("permeable", 1051L),
                new Selection("hydrologic", 2211L),
                new Selection("eroded", 1439L),
                new Selection("spine", 3670L));

        BufferedImage atlas =
                new BufferedImage(
                        2 * SPECIMEN_WIDTH,
                        3 * SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,islandKey,morphology,materialSamples,"
                        + "primaryStructuralWins,secondaryStructuralWins,"
                        + "primaryFinalWins,secondaryFinalWins,alterationWins,"
                        + "waterWins,mineralWins,conditionedWinnerSamples,"
                        + "multiActiveConditionedSamples,determinismMismatches,"
                        + "uniqueWinnerBindings,horizontalNeighborTransitions\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions = new HashMap<>();
            Grid grid = sample(descriptor, field, decisions);
            Stats stats = stats(grid);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, grid, stats);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.key() + ".png").toFile());
            atlasGraphics.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(stats.materialSamples()).append(',')
                    .append(stats.primaryStructuralWins()).append(',')
                    .append(stats.secondaryStructuralWins()).append(',')
                    .append(stats.primaryFinalWins()).append(',')
                    .append(stats.secondaryFinalWins()).append(',')
                    .append(stats.alterationWins()).append(',')
                    .append(stats.waterWins()).append(',')
                    .append(stats.mineralWins()).append(',')
                    .append(stats.conditionedWinnerSamples()).append(',')
                    .append(stats.multiActiveConditionedSamples()).append(',')
                    .append(stats.determinismMismatches()).append(',')
                    .append(stats.uniqueWinnerBindings()).append(',')
                    .append(stats.horizontalNeighborTransitions()).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0044</title>"
                        + "<h1>Deterministic discrete semantic material realization</h1>"
                        + "<p>All panels sample semantic depth 0.52. STRUCTURAL shows the "
                        + "primary/secondary host winner. FINAL ROLE shows the single semantic "
                        + "binding role that wins the exact point after independent conditioned "
                        + "claims are realized. Specialty panels isolate alteration/water/mineral "
                        + "wins. ACTIVE CONDITIONED shows simultaneous conditioned activations. "
                        + "The output remains backend-neutral and contains no concrete block id.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Grid sample(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialBindingRequestField field,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        double radius = descriptor.nominalRadius();
        SkyIslandMaterialRealizationSelection[][] realization =
                new SkyIslandMaterialRealizationSelection[SAMPLE][SAMPLE];
        SkyIslandSubsurfacePosition[][] positions =
                new SkyIslandSubsurfacePosition[SAMPLE][SAMPLE];

        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                SkyIslandSubsurfacePosition position =
                        new SkyIslandSubsurfacePosition(x, z, DEPTH);
                positions[iz][ix] = position;

                SkyIslandMaterialBindingRequestSelection source = field.sample(position);
                ensureDecisions(source, decisions);
                SkyIslandMaterialExpressionSample expression =
                        SkyIslandMaterialExpressionAllocator.allocate(source, decisions);
                realization[iz][ix] =
                        SkyIslandMaterialExpressionRealizer.realize(position, expression);
            }
        }
        return new Grid(realization, positions);
    }

    private static void ensureDecisions(
            SkyIslandMaterialBindingRequestSelection source,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
            decisions.computeIfAbsent(
                    use.request().bindingKey(),
                    ignored -> decision(use.request()));
        }
    }

    private static SkyIslandMaterialResolutionDecision decision(
            SkyIslandMaterialBindingRequest request) {
        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, CANDIDATES);
        return SkyIslandMaterialResolutionDecisionFactory.decide(
                frontier,
                frontier.topRank().profile(),
                SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            Grid grid,
            Stats stats) {
        BufferedImage image =
                new BufferedImage(
                        SPECIMEN_WIDTH,
                        SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(
                role + " / key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "depth=%.2f material=%d conditioned wins=%d multi-active=%d",
                        DEPTH,
                        stats.materialSamples(),
                        stats.conditionedWinnerSamples(),
                        stats.multiActiveConditionedSamples()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "final P/S/A/W/M=%d/%d/%d/%d/%d mismatches=%d transitions=%d",
                        stats.primaryFinalWins(),
                        stats.secondaryFinalWins(),
                        stats.alterationWins(),
                        stats.waterWins(),
                        stats.mineralWins(),
                        stats.determinismMismatches(),
                        stats.horizontalNeighborTransitions()),
                7,
                50);

        String[] labels = {
            "STRUCTURAL",
            "FINAL ROLE",
            "ALTERATION WIN",
            "WATER WIN",
            "MINERAL WIN",
            "ACTIVE CONDITIONED"
        };
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 66);
            renderPanel(image, panel * MAP, grid.realization(), panel);
        }

        graphics.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            graphics.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        graphics.dispose();
        return image;
    }

    private static void renderPanel(
            BufferedImage image,
            int offsetX,
            SkyIslandMaterialRealizationSelection[][] samples,
            int panel) {
        for (int py = 0; py < MAP; py++) {
            int sy = Math.min(
                    SAMPLE - 1,
                    (int) Math.round(py * (SAMPLE - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx = Math.min(
                        SAMPLE - 1,
                        (int) Math.round(px * (SAMPLE - 1.0) / (MAP - 1.0)));
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        panelColor(samples[sy][sx], panel).getRGB());
            }
        }
    }

    private static Color panelColor(
            SkyIslandMaterialRealizationSelection selection,
            int panel) {
        if (!selection.materialPresent()) {
            return Color.WHITE;
        }
        return switch (panel) {
            case 0 -> roleColor(selection.structuralWinner().role());
            case 1 -> roleColor(selection.winner().role());
            case 2 -> binary(
                    selection.winner().role()
                            == SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT,
                    new Color(156, 98, 88));
            case 3 -> binary(
                    selection.winner().role()
                            == SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING,
                    new Color(74, 112, 156));
            case 4 -> binary(
                    selection.winner().role()
                            == SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE,
                    new Color(134, 112, 72));
            case 5 -> activeColor(selection.activeConditionedClaims());
            default -> throw new IllegalArgumentException("unknown panel " + panel);
        };
    }

    private static Color roleColor(SkyIslandSemanticMaterialPaletteRole role) {
        return switch (role) {
            case PRIMARY_MATRIX -> new Color(112, 112, 112);
            case SECONDARY_MATRIX -> new Color(86, 128, 86);
            case ALTERATION_OVERPRINT -> new Color(156, 98, 88);
            case HYDROLOGIC_CONDITIONING -> new Color(74, 112, 156);
            case MINERAL_BEARING_STRUCTURE -> new Color(134, 112, 72);
        };
    }

    private static Color binary(boolean active, Color color) {
        return active ? color : Color.WHITE;
    }

    private static Color activeColor(int count) {
        if (count <= 0) {
            return Color.WHITE;
        }
        Color base = new Color(110, 82, 142);
        double scale = Math.min(1.0, 0.42 + 0.24 * count);
        return new Color(
                channel(255.0 - (255.0 - base.getRed()) * scale),
                channel(255.0 - (255.0 - base.getGreen()) * scale),
                channel(255.0 - (255.0 - base.getBlue()) * scale));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Stats stats(Grid grid) {
        int material = 0;
        int primaryStructural = 0;
        int secondaryStructural = 0;
        int primaryFinal = 0;
        int secondaryFinal = 0;
        int alteration = 0;
        int water = 0;
        int mineral = 0;
        int conditioned = 0;
        int multiActive = 0;
        int mismatches = 0;
        int transitions = 0;
        Set<String> winnerBindings = new HashSet<>();

        for (int iz = 0; iz < SAMPLE; iz++) {
            for (int ix = 0; ix < SAMPLE; ix++) {
                SkyIslandMaterialRealizationSelection selection =
                        grid.realization()[iz][ix];
                if (!selection.materialPresent()) {
                    continue;
                }
                material++;

                if (selection.structuralWinner().role()
                        == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
                    primaryStructural++;
                } else {
                    secondaryStructural++;
                }

                switch (selection.winner().role()) {
                    case PRIMARY_MATRIX -> primaryFinal++;
                    case SECONDARY_MATRIX -> secondaryFinal++;
                    case ALTERATION_OVERPRINT -> alteration++;
                    case HYDROLOGIC_CONDITIONING -> water++;
                    case MINERAL_BEARING_STRUCTURE -> mineral++;
                }
                if (selection.conditionedWinner()) {
                    conditioned++;
                }
                if (selection.activeConditionedClaims() > 1) {
                    multiActive++;
                }
                winnerBindings.add(selection.winner().bindingKey().canonicalToken());

                SkyIslandMaterialRealizationSelection repeat =
                        SkyIslandMaterialExpressionRealizer.realize(
                                grid.positions()[iz][ix],
                                selection.expressionSample());
                if (!repeat.winnerBindingKey().equals(selection.winnerBindingKey())
                        || repeat.activeConditionedClaims()
                                != selection.activeConditionedClaims()) {
                    mismatches++;
                }

                if (ix > 0) {
                    SkyIslandMaterialRealizationSelection left =
                            grid.realization()[iz][ix - 1];
                    if (left.materialPresent()
                            && !left.winnerBindingKey()
                                    .equals(selection.winnerBindingKey())) {
                        transitions++;
                    }
                }
            }
        }

        return new Stats(
                material,
                primaryStructural,
                secondaryStructural,
                primaryFinal,
                secondaryFinal,
                alteration,
                water,
                mineral,
                conditioned,
                multiActive,
                mismatches,
                winnerBindings.size(),
                transitions);
    }

    private static void centered(
            Graphics2D graphics, String label, int x, int width, int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Grid(
            SkyIslandMaterialRealizationSelection[][] realization,
            SkyIslandSubsurfacePosition[][] positions) {}

    private record Stats(
            int materialSamples,
            int primaryStructuralWins,
            int secondaryStructuralWins,
            int primaryFinalWins,
            int secondaryFinalWins,
            int alterationWins,
            int waterWins,
            int mineralWins,
            int conditionedWinnerSamples,
            int multiActiveConditionedSamples,
            int determinismMismatches,
            int uniqueWinnerBindings,
            int horizontalNeighborTransitions) {}

    private record Selection(String role, long key) {}
}
