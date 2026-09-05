package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionAllocation;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionAllocator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionSample;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0043 material-expression allocation evidence. */
public final class AuthorshipMaterialExpressionAllocationCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
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

    private AuthorshipMaterialExpressionAllocationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of(
                        "build",
                        "evidence",
                        "authorship-material-expression-allocation-v1");
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
                "role,islandKey,morphology,materialSamples,primaryMean,primaryMin,"
                        + "secondaryMean,secondaryMax,alterationMean,alterationMax,"
                        + "waterMean,waterMax,mineralMean,mineralMax,"
                        + "conditionedOverlapSamples,matrixBudgetViolations,"
                        + "ceilingViolations,uniqueDecisions\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions = new HashMap<>();
            SkyIslandMaterialExpressionSample[][] samples =
                    sample(descriptor, field, decisions);
            Stats stats = stats(samples, decisions.size());
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, samples, stats);

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
                    .append(format(stats.primaryMean())).append(',')
                    .append(format(stats.primaryMin())).append(',')
                    .append(format(stats.secondaryMean())).append(',')
                    .append(format(stats.secondaryMax())).append(',')
                    .append(format(stats.alterationMean())).append(',')
                    .append(format(stats.alterationMax())).append(',')
                    .append(format(stats.waterMean())).append(',')
                    .append(format(stats.waterMax())).append(',')
                    .append(format(stats.mineralMean())).append(',')
                    .append(format(stats.mineralMax())).append(',')
                    .append(stats.conditionedOverlapSamples()).append(',')
                    .append(stats.matrixBudgetViolations()).append(',')
                    .append(stats.ceilingViolations()).append(',')
                    .append(stats.uniqueDecisions()).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0043</title>"
                        + "<h1>Material expression allocation</h1>"
                        + "<p>All panels sample semantic depth 0.52. PRIMARY and SECONDARY form "
                        + "the exact structural matrix partition. ALTERATION, WATER, and MINERAL "
                        + "are independent conditioned expression claims driven by accepted local "
                        + "AUTH-0037 support and ceilings. OVERLAP shows how many conditioned "
                        + "systems claim expression at the same sample. No panel is a block map.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static SkyIslandMaterialExpressionSample[][] sample(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialBindingRequestField field,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        double radius = descriptor.nominalRadius();
        SkyIslandMaterialExpressionSample[][] result =
                new SkyIslandMaterialExpressionSample[SAMPLE][SAMPLE];

        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                SkyIslandMaterialBindingRequestSelection source =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, DEPTH));
                ensureDecisions(source, decisions);
                result[iz][ix] =
                        SkyIslandMaterialExpressionAllocator.allocate(
                                source, decisions);
            }
        }
        return result;
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
            SkyIslandMaterialExpressionSample[][] samples,
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
                        "depth=%.2f material=%d decisions=%d matrix violations=%d ceiling violations=%d",
                        DEPTH,
                        stats.materialSamples(),
                        stats.uniqueDecisions(),
                        stats.matrixBudgetViolations(),
                        stats.ceilingViolations()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "mean P/S/A/W/M=%.3f/%.3f/%.3f/%.3f/%.3f conditioned overlap=%d",
                        stats.primaryMean(),
                        stats.secondaryMean(),
                        stats.alterationMean(),
                        stats.waterMean(),
                        stats.mineralMean(),
                        stats.conditionedOverlapSamples()),
                7,
                50);

        String[] labels = {
            "PRIMARY SHARE",
            "SECONDARY SHARE",
            "ALTERATION CLAIM",
            "WATER CLAIM",
            "MINERAL CLAIM",
            "CONDITIONED OVERLAP"
        };
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 66);
            renderPanel(image, panel * MAP, samples, panel);
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
            SkyIslandMaterialExpressionSample[][] samples,
            int panel) {
        for (int py = 0; py < MAP; py++) {
            int sy = Math.min(
                    SAMPLE - 1,
                    (int) Math.round(py * (SAMPLE - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx = Math.min(
                        SAMPLE - 1,
                        (int) Math.round(px * (SAMPLE - 1.0) / (MAP - 1.0)));
                SkyIslandMaterialExpressionSample sample = samples[sy][sx];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        panelColor(sample, panel).getRGB());
            }
        }
    }

    private static Color panelColor(
            SkyIslandMaterialExpressionSample sample,
            int panel) {
        if (!sample.source().materialPresent()) {
            return Color.WHITE;
        }
        return switch (panel) {
            case 0 -> scalar(
                    sample.primaryMatrixShare(),
                    new Color(92, 92, 92));
            case 1 -> scalar(
                    sample.secondaryMatrixShare(),
                    new Color(104, 128, 86));
            case 2 -> scalar(
                    sample.conditionedClaim(
                            SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT),
                    new Color(156, 98, 88));
            case 3 -> scalar(
                    sample.conditionedClaim(
                            SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING),
                    new Color(74, 112, 156));
            case 4 -> scalar(
                    sample.conditionedClaim(
                            SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE),
                    new Color(134, 112, 72));
            case 5 -> overlap(sample.conditionedClaimCount());
            default -> throw new IllegalArgumentException("unknown panel " + panel);
        };
    }

    private static Color scalar(double value, Color base) {
        if (value <= 0.0) {
            return Color.WHITE;
        }
        double scale = Math.max(0.18, Math.min(1.0, value));
        return new Color(
                channel(255.0 - (255.0 - base.getRed()) * scale),
                channel(255.0 - (255.0 - base.getGreen()) * scale),
                channel(255.0 - (255.0 - base.getBlue()) * scale));
    }

    private static Color overlap(int count) {
        if (count <= 0) {
            return Color.WHITE;
        }
        double scale = Math.min(1.0, 0.28 + 0.22 * count);
        Color base = new Color(110, 82, 142);
        return new Color(
                channel(255.0 - (255.0 - base.getRed()) * scale),
                channel(255.0 - (255.0 - base.getGreen()) * scale),
                channel(255.0 - (255.0 - base.getBlue()) * scale));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Stats stats(
            SkyIslandMaterialExpressionSample[][] samples,
            int uniqueDecisions) {
        int material = 0;
        double primarySum = 0.0;
        double primaryMin = 1.0;
        double secondarySum = 0.0;
        double secondaryMax = 0.0;
        double alterationSum = 0.0;
        double alterationMax = 0.0;
        double waterSum = 0.0;
        double waterMax = 0.0;
        double mineralSum = 0.0;
        double mineralMax = 0.0;
        int overlap = 0;
        int matrixViolations = 0;
        int ceilingViolations = 0;

        for (SkyIslandMaterialExpressionSample[] row : samples) {
            for (SkyIslandMaterialExpressionSample sample : row) {
                if (!sample.source().materialPresent()) {
                    continue;
                }
                material++;
                double primary = sample.primaryMatrixShare();
                double secondary = sample.secondaryMatrixShare();
                double alteration =
                        sample.conditionedClaim(
                                SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT);
                double water =
                        sample.conditionedClaim(
                                SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING);
                double mineral =
                        sample.conditionedClaim(
                                SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE);

                primarySum += primary;
                primaryMin = Math.min(primaryMin, primary);
                secondarySum += secondary;
                secondaryMax = Math.max(secondaryMax, secondary);
                alterationSum += alteration;
                alterationMax = Math.max(alterationMax, alteration);
                waterSum += water;
                waterMax = Math.max(waterMax, water);
                mineralSum += mineral;
                mineralMax = Math.max(mineralMax, mineral);
                if (sample.conditionedClaimCount() >= 2) {
                    overlap++;
                }
                if (Math.abs(primary + secondary - 1.0) > 1.0e-12) {
                    matrixViolations++;
                }
                for (SkyIslandMaterialExpressionAllocation allocation :
                        sample.allocations()) {
                    if (allocation.targetExpression()
                            > allocation.localExpressionCeiling() + 1.0e-12) {
                        ceilingViolations++;
                    }
                }
            }
        }

        double divisor = material == 0 ? 1.0 : material;
        return new Stats(
                material,
                primarySum / divisor,
                material == 0 ? 0.0 : primaryMin,
                secondarySum / divisor,
                secondaryMax,
                alterationSum / divisor,
                alterationMax,
                waterSum / divisor,
                waterMax,
                mineralSum / divisor,
                mineralMax,
                overlap,
                matrixViolations,
                ceilingViolations,
                uniqueDecisions);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private static void centered(
            Graphics2D graphics, String label, int x, int width, int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Stats(
            int materialSamples,
            double primaryMean,
            double primaryMin,
            double secondaryMean,
            double secondaryMax,
            double alterationMean,
            double alterationMax,
            double waterMean,
            double waterMax,
            double mineralMean,
            double mineralMax,
            int conditionedOverlapSamples,
            int matrixBudgetViolations,
            int ceilingViolations,
            int uniqueDecisions) {}

    private record Selection(String role, long key) {}
}
