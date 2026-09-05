package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.reference.backend.ReferenceAuthoredMaterial;
import io.github.nidaba.skyforge.reference.backend.ReferenceAuthoredMaterialBindingTable;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingApplicator;
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
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingDomainKind;
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

/** Generates deterministic AUTH-0045 backend material-binding application evidence. */
public final class AuthorshipMaterialBindingApplicationCorpusCli {
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

    private AuthorshipMaterialBindingApplicationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-material-binding-application-v1");
        Files.createDirectories(out);

        List<Selection> selections =
                List.of(
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

        StringBuilder manifest =
                new StringBuilder(
                        "role,islandKey,morphology,materialSamples,appliedSamples,"
                                + "voidSamples,voidApplications,missingBindings,reuseViolations,"
                                + "repeatMismatches,uniqueWinnerBindings,uniqueReferenceMaterials,"
                                + "conditionedApplications\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions = new HashMap<>();

            Grid grid = realizeGrid(descriptor, field, decisions);
            Set<SkyIslandSemanticPaletteBindingKey> winnerKeys = winnerKeys(grid);
            Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> bindings =
                    ReferenceAuthoredMaterialBindingTable.resolve(winnerKeys);
            AppliedGrid applied = applyGrid(grid, bindings);
            Stats stats = stats(grid, applied, bindings);

            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, grid, applied, stats);
            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.key() + ".png")
                            .toFile());
            atlasGraphics.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(stats.materialSamples()).append(',')
                    .append(stats.appliedSamples()).append(',')
                    .append(stats.voidSamples()).append(',')
                    .append(stats.voidApplications()).append(',')
                    .append(stats.missingBindings()).append(',')
                    .append(stats.reuseViolations()).append(',')
                    .append(stats.repeatMismatches()).append(',')
                    .append(stats.uniqueWinnerBindings()).append(',')
                    .append(stats.uniqueReferenceMaterials()).append(',')
                    .append(stats.conditionedApplications()).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0045</title>"
                        + "<h1>Backend material-binding application contract</h1>"
                        + "<p>All panels sample semantic depth 0.52. WINNER ROLE is the exact "
                        + "AUTH-0044 semantic winner. REFERENCE MATERIAL is the adapter-owned "
                        + "concrete token selected only by the stable winner key. BINDING KEY "
                        + "visualizes coherence domains. DOMAIN shows AUTH-0038 key scope. "
                        + "CONDITIONED marks conditioned final winners. APPLICATION is green where "
                        + "material was applied, white for authored absence, and red only on a "
                        + "contract failure. Reference material identity exists only in "
                        + "skyforge-reference.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Grid realizeGrid(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialBindingRequestField field,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        double radius = descriptor.nominalRadius();
        SkyIslandMaterialRealizationSelection[][] realization =
                new SkyIslandMaterialRealizationSelection[SAMPLE][SAMPLE];

        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                SkyIslandSubsurfacePosition position =
                        new SkyIslandSubsurfacePosition(x, z, DEPTH);
                SkyIslandMaterialBindingRequestSelection source = field.sample(position);
                ensureDecisions(source, decisions);
                SkyIslandMaterialExpressionSample expression =
                        SkyIslandMaterialExpressionAllocator.allocate(source, decisions);
                realization[iz][ix] =
                        SkyIslandMaterialExpressionRealizer.realize(position, expression);
            }
        }
        return new Grid(realization);
    }

    private static Set<SkyIslandSemanticPaletteBindingKey> winnerKeys(Grid grid) {
        Set<SkyIslandSemanticPaletteBindingKey> result = new HashSet<>();
        for (SkyIslandMaterialRealizationSelection[] row : grid.realization()) {
            for (SkyIslandMaterialRealizationSelection selection : row) {
                selection.winnerBindingKey().ifPresent(result::add);
            }
        }
        return result;
    }

    private static AppliedGrid applyGrid(
            Grid grid,
            Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> bindings) {
        ReferenceAuthoredMaterial[][] materials =
                new ReferenceAuthoredMaterial[SAMPLE][SAMPLE];
        int[][] status = new int[SAMPLE][SAMPLE];

        for (int iz = 0; iz < SAMPLE; iz++) {
            for (int ix = 0; ix < SAMPLE; ix++) {
                SkyIslandMaterialRealizationSelection selection =
                        grid.realization()[iz][ix];
                if (!selection.materialPresent()) {
                    if (SkyIslandMaterialBindingApplicator.apply(selection, bindings).isPresent()) {
                        status[iz][ix] = 2;
                    }
                    continue;
                }

                try {
                    materials[iz][ix] =
                            SkyIslandMaterialBindingApplicator.apply(selection, bindings)
                                    .orElseThrow();
                    status[iz][ix] = 1;
                } catch (IllegalArgumentException exception) {
                    status[iz][ix] = 2;
                }
            }
        }
        return new AppliedGrid(materials, status);
    }

    private static Stats stats(
            Grid grid,
            AppliedGrid applied,
            Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> bindings) {
        int material = 0;
        int appliedSamples = 0;
        int voidSamples = 0;
        int voidApplications = 0;
        int missing = 0;
        int reuseViolations = 0;
        int repeatMismatches = 0;
        int conditioned = 0;
        Set<SkyIslandSemanticPaletteBindingKey> uniqueKeys = new HashSet<>();
        Set<ReferenceAuthoredMaterial> uniqueMaterials = new HashSet<>();
        Map<SkyIslandSemanticPaletteBindingKey, ReferenceAuthoredMaterial> observed =
                new HashMap<>();

        for (int iz = 0; iz < SAMPLE; iz++) {
            for (int ix = 0; ix < SAMPLE; ix++) {
                SkyIslandMaterialRealizationSelection selection =
                        grid.realization()[iz][ix];
                ReferenceAuthoredMaterial materialValue = applied.materials()[iz][ix];

                if (!selection.materialPresent()) {
                    voidSamples++;
                    if (materialValue != null || applied.status()[iz][ix] != 0) {
                        voidApplications++;
                    }
                    continue;
                }

                material++;
                if (applied.status()[iz][ix] != 1 || materialValue == null) {
                    missing++;
                    continue;
                }

                appliedSamples++;
                SkyIslandSemanticPaletteBindingKey key =
                        selection.winner().bindingKey();
                uniqueKeys.add(key);
                uniqueMaterials.add(materialValue);
                if (selection.conditionedWinner()) {
                    conditioned++;
                }

                ReferenceAuthoredMaterial previous = observed.putIfAbsent(key, materialValue);
                if (previous != null && previous != materialValue) {
                    reuseViolations++;
                }

                ReferenceAuthoredMaterial repeat =
                        SkyIslandMaterialBindingApplicator.apply(selection, bindings)
                                .orElseThrow();
                if (repeat != materialValue) {
                    repeatMismatches++;
                }
            }
        }

        return new Stats(
                material,
                appliedSamples,
                voidSamples,
                voidApplications,
                missing,
                reuseViolations,
                repeatMismatches,
                uniqueKeys.size(),
                uniqueMaterials.size(),
                conditioned);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            Grid grid,
            AppliedGrid applied,
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
                role
                        + " / key="
                        + descriptor.identity().islandKey()
                        + " / "
                        + descriptor.morphologyFamily().identifier(),
                7,
                17);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "depth=%.2f material=%d applied=%d void=%d conditioned=%d",
                        DEPTH,
                        stats.materialSamples(),
                        stats.appliedSamples(),
                        stats.voidSamples(),
                        stats.conditionedApplications()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "missing=%d void-app=%d reuse=%d repeat=%d keys=%d materials=%d",
                        stats.missingBindings(),
                        stats.voidApplications(),
                        stats.reuseViolations(),
                        stats.repeatMismatches(),
                        stats.uniqueWinnerBindings(),
                        stats.uniqueReferenceMaterials()),
                7,
                50);

        String[] labels = {
            "WINNER ROLE",
            "REFERENCE MATERIAL",
            "BINDING KEY",
            "DOMAIN",
            "CONDITIONED",
            "APPLICATION"
        };
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 66);
            renderPanel(image, panel * MAP, grid, applied, panel);
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
            Grid grid,
            AppliedGrid applied,
            int panel) {
        for (int py = 0; py < MAP; py++) {
            int sy =
                    Math.min(
                            SAMPLE - 1,
                            (int) Math.round(py * (SAMPLE - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx =
                        Math.min(
                                SAMPLE - 1,
                                (int) Math.round(px * (SAMPLE - 1.0) / (MAP - 1.0)));
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        panelColor(
                                        grid.realization()[sy][sx],
                                        applied.materials()[sy][sx],
                                        applied.status()[sy][sx],
                                        panel)
                                .getRGB());
            }
        }
    }

    private static Color panelColor(
            SkyIslandMaterialRealizationSelection selection,
            ReferenceAuthoredMaterial material,
            int status,
            int panel) {
        if (!selection.materialPresent()) {
            return Color.WHITE;
        }

        return switch (panel) {
            case 0 -> roleColor(selection.winner().role());
            case 1 -> materialColor(material);
            case 2 -> keyColor(selection.winner().bindingKey());
            case 3 -> domainColor(selection.winner().bindingKey().domainKind());
            case 4 ->
                    selection.conditionedWinner()
                            ? new Color(116, 78, 144)
                            : Color.WHITE;
            case 5 ->
                    status == 1
                            ? new Color(92, 138, 92)
                            : new Color(188, 66, 66);
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

    private static Color materialColor(ReferenceAuthoredMaterial material) {
        if (material == null) {
            return new Color(188, 66, 66);
        }
        return switch (material) {
            case MASSIVE_A -> new Color(102, 102, 108);
            case MASSIVE_B -> new Color(128, 122, 118);
            case FABRIC_A -> new Color(82, 118, 92);
            case FABRIC_B -> new Color(108, 142, 112);
            case ALTERED_A -> new Color(150, 88, 82);
            case ALTERED_B -> new Color(178, 112, 102);
            case HYDRATED_A -> new Color(68, 104, 150);
            case HYDRATED_B -> new Color(92, 132, 176);
            case MINERAL_A -> new Color(130, 106, 62);
            case MINERAL_B -> new Color(164, 138, 82);
        };
    }

    private static Color keyColor(SkyIslandSemanticPaletteBindingKey key) {
        int hash = key.canonicalToken().hashCode();
        float hue = Math.floorMod(hash, 360) / 360.0f;
        return Color.getHSBColor(hue, 0.42f, 0.78f);
    }

    private static Color domainColor(SkyIslandSemanticPaletteBindingDomainKind kind) {
        return switch (kind) {
            case ASSEMBLAGE_REGION -> new Color(104, 116, 126);
            case CONDITIONED_REGION -> new Color(118, 88, 142);
            case CONTACT_TRANSITION -> new Color(168, 128, 72);
        };
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

    private static void centered(
            Graphics2D graphics,
            String label,
            int x,
            int width,
            int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Grid(SkyIslandMaterialRealizationSelection[][] realization) {}

    private record AppliedGrid(
            ReferenceAuthoredMaterial[][] materials,
            int[][] status) {}

    private record Stats(
            int materialSamples,
            int appliedSamples,
            int voidSamples,
            int voidApplications,
            int missingBindings,
            int reuseViolations,
            int repeatMismatches,
            int uniqueWinnerBindings,
            int uniqueReferenceMaterials,
            int conditionedApplications) {}

    private record Selection(String role, long key) {}
}
