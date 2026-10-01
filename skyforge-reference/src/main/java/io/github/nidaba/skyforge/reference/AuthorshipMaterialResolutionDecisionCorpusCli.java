package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecision;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecisionFactory;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionFrontier;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionSelectionMethod;
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

/** Generates deterministic AUTH-0042 material-resolution-decision evidence. */
public final class AuthorshipMaterialResolutionDecisionCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final SkyIslandMaterialCapabilityProfile MATRIX =
            new SkyIslandMaterialCapabilityProfile(0.94, 0.20, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile FABRIC =
            new SkyIslandMaterialCapabilityProfile(0.92, 0.94, 0.20, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ALTERATION =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.94, 0.20, 0.20);
    private static final SkyIslandMaterialCapabilityProfile WATER =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.94, 0.20);
    private static final SkyIslandMaterialCapabilityProfile ACCENT =
            new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.20, 0.94);
    private static final SkyIslandMaterialCapabilityProfile GENERALIST =
            SkyIslandMaterialCapabilityProfile.uniform(0.86);

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(
                    MATRIX,
                    FABRIC,
                    ALTERATION,
                    ALTERATION,
                    WATER,
                    ACCENT,
                    ACCENT,
                    GENERALIST);

    private AuthorshipMaterialResolutionDecisionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of(
                        "build",
                        "evidence",
                        "authorship-material-resolution-decision-v1");
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
                "role,islandKey,morphology,materialSamples,resolvedUses,"
                        + "semanticWinnerUses,backendTieBreakUses,maxCompatibleCandidates,"
                        + "maxTopSemanticTieCount,uniqueRequests,unstableRequests,"
                        + "meanSelectedMinHeadroom\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            SkyIslandMaterialBindingRequestSelection[][] samples =
                    sample(descriptor, field);
            DecisionGrid decisions = decisions(samples);
            Stats stats = stats(samples, decisions);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, decisions, stats);

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
                    .append(stats.resolvedUses()).append(',')
                    .append(stats.semanticWinnerUses()).append(',')
                    .append(stats.backendTieBreakUses()).append(',')
                    .append(stats.maxCompatibleCandidates()).append(',')
                    .append(stats.maxTopSemanticTieCount()).append(',')
                    .append(stats.uniqueRequests()).append(',')
                    .append(stats.unstableRequests()).append(',')
                    .append(format(stats.meanSelectedMinHeadroom())).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0042</title>"
                        + "<h1>Material resolution decision contract</h1>"
                        + "<p>All panels sample semantic depth 0.52. SELECTED shows the winning "
                        + "backend-neutral capability profile. SEMANTIC WINNER shows requests made "
                        + "decisive by AUTH-0041 alone. BACKEND TIE-BREAK shows top semantic ties "
                        + "that require adapter-owned stable candidate identity. The remaining "
                        + "panels visualize compatible-candidate breadth, top tie multiplicity, and "
                        + "selected minimum required-capability headroom.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static SkyIslandMaterialBindingRequestSelection[][] sample(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialBindingRequestField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandMaterialBindingRequestSelection[][] result =
                new SkyIslandMaterialBindingRequestSelection[SAMPLE][SAMPLE];
        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                result[iz][ix] =
                        field.sample(new SkyIslandSubsurfacePosition(x, z, DEPTH));
            }
        }
        return result;
    }

    private static DecisionGrid decisions(
            SkyIslandMaterialBindingRequestSelection[][] samples) {
        @SuppressWarnings("unchecked")
        List<SkyIslandMaterialResolutionDecision>[][] result =
                (List<SkyIslandMaterialResolutionDecision>[][])
                        new List<?>[SAMPLE][SAMPLE];

        for (int iz = 0; iz < SAMPLE; iz++) {
            for (int ix = 0; ix < SAMPLE; ix++) {
                SkyIslandMaterialBindingRequestSelection selection = samples[iz][ix];
                java.util.ArrayList<SkyIslandMaterialResolutionDecision> local =
                        new java.util.ArrayList<>();
                for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                    SkyIslandMaterialResolutionFrontier frontier =
                            SkyIslandMaterialResolutionDecisionFactory.frontier(
                                    use.request(), CANDIDATES);
                    SkyIslandMaterialCapabilityProfile selected =
                            frontier.topRank().profile();
                    SkyIslandMaterialResolutionSelectionMethod method =
                            frontier.requiresBackendStableTieBreak()
                                    ? SkyIslandMaterialResolutionSelectionMethod
                                            .BACKEND_STABLE_IDENTITY_TIE_BREAK
                                    : SkyIslandMaterialResolutionSelectionMethod
                                            .SEMANTIC_RANK_WINNER;
                    local.add(
                            SkyIslandMaterialResolutionDecisionFactory.decide(
                                    frontier, selected, method));
                }
                result[iz][ix] = List.copyOf(local);
            }
        }
        return new DecisionGrid(result);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            DecisionGrid decisions,
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
                        "depth=%.2f resolved=%d semantic=%d backend-tie=%d",
                        DEPTH,
                        stats.resolvedUses(),
                        stats.semanticWinnerUses(),
                        stats.backendTieBreakUses()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "unique requests=%d unstable=%d max compatible=%d max ties=%d",
                        stats.uniqueRequests(),
                        stats.unstableRequests(),
                        stats.maxCompatibleCandidates(),
                        stats.maxTopSemanticTieCount()),
                7,
                50);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        String[] labels = {
            "SELECTED",
            "SEMANTIC WINNER",
            "BACKEND TIE-BREAK",
            "COMPATIBLE COUNT",
            "TOP TIE COUNT",
            "MIN HEADROOM"
        };
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 66);
            renderPanel(image, panel * MAP, decisions, panel);
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
            DecisionGrid grid,
            int panel) {
        for (int py = 0; py < MAP; py++) {
            int sy = Math.min(
                    SAMPLE - 1,
                    (int) Math.round(py * (SAMPLE - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx = Math.min(
                        SAMPLE - 1,
                        (int) Math.round(px * (SAMPLE - 1.0) / (MAP - 1.0)));
                List<SkyIslandMaterialResolutionDecision> local =
                        grid.decisions()[sy][sx];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        panelColor(local, panel).getRGB());
            }
        }
    }

    private static Color panelColor(
            List<SkyIslandMaterialResolutionDecision> local,
            int panel) {
        if (local.isEmpty()) {
            return Color.WHITE;
        }
        return switch (panel) {
            case 0 -> selectedColor(local.get(0).selectedProfile());
            case 1 -> countShade(
                    new Color(78, 132, 92),
                    (int)
                            local.stream()
                                    .filter(
                                            decision ->
                                                    !decision.backendStableTieBreakApplied())
                                    .count());
            case 2 -> countShade(
                    new Color(170, 108, 66),
                    (int)
                            local.stream()
                                    .filter(
                                            SkyIslandMaterialResolutionDecision
                                                    ::backendStableTieBreakApplied)
                                    .count());
            case 3 -> heat(
                    local.stream()
                            .mapToInt(
                                    SkyIslandMaterialResolutionDecision
                                            ::compatibleCandidateCount)
                            .max()
                            .orElse(0),
                    8);
            case 4 -> heat(
                    local.stream()
                            .mapToInt(
                                    SkyIslandMaterialResolutionDecision
                                            ::topSemanticTieCount)
                            .max()
                            .orElse(0),
                    2);
            case 5 -> scalar(
                    local.stream()
                            .mapToDouble(
                                    decision ->
                                            decision.selectedRank()
                                                    .minimumRequiredHeadroom())
                            .average()
                            .orElse(0.0));
            default -> throw new IllegalArgumentException("unknown panel " + panel);
        };
    }

    private static Color selectedColor(SkyIslandMaterialCapabilityProfile profile) {
        if (profile.equals(MATRIX)) {
            return new Color(126, 92, 68);
        }
        if (profile.equals(FABRIC)) {
            return new Color(86, 128, 86);
        }
        if (profile.equals(ALTERATION)) {
            return new Color(156, 98, 88);
        }
        if (profile.equals(WATER)) {
            return new Color(74, 112, 156);
        }
        if (profile.equals(ACCENT)) {
            return new Color(134, 112, 72);
        }
        if (profile.equals(GENERALIST)) {
            return new Color(104, 92, 136);
        }
        return Color.BLACK;
    }

    private static Color countShade(Color base, int count) {
        if (count <= 0) {
            return Color.WHITE;
        }
        double scale = Math.min(1.0, 0.52 + 0.16 * count);
        return new Color(
                channel(255.0 - (255.0 - base.getRed()) * scale),
                channel(255.0 - (255.0 - base.getGreen()) * scale),
                channel(255.0 - (255.0 - base.getBlue()) * scale));
    }

    private static Color heat(int value, int maximum) {
        if (value <= 0) {
            return Color.WHITE;
        }
        double normalized = Math.min(1.0, (double) value / maximum);
        return new Color(
                channel(238.0 - 148.0 * normalized),
                channel(238.0 - 104.0 * normalized),
                channel(238.0 - 40.0 * normalized));
    }

    private static Color scalar(double value) {
        double normalized = Math.max(0.0, Math.min(1.0, value));
        return new Color(
                channel(245.0 - 120.0 * normalized),
                channel(245.0 - 120.0 * normalized),
                channel(245.0 - 120.0 * normalized));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Stats stats(
            SkyIslandMaterialBindingRequestSelection[][] samples,
            DecisionGrid decisions) {
        int material = 0;
        int resolved = 0;
        int semantic = 0;
        int backendTie = 0;
        int maxCompatible = 0;
        int maxTies = 0;
        double headroomSum = 0.0;
        Map<String, SkyIslandMaterialResolutionDecision> stableByRequest =
                new HashMap<>();
        int unstable = 0;

        for (int iz = 0; iz < SAMPLE; iz++) {
            for (int ix = 0; ix < SAMPLE; ix++) {
                if (samples[iz][ix].materialPresent()) {
                    material++;
                }
                for (SkyIslandMaterialResolutionDecision decision :
                        decisions.decisions()[iz][ix]) {
                    resolved++;
                    if (decision.backendStableTieBreakApplied()) {
                        backendTie++;
                    } else {
                        semantic++;
                    }
                    maxCompatible =
                            Math.max(
                                    maxCompatible,
                                    decision.compatibleCandidateCount());
                    maxTies =
                            Math.max(maxTies, decision.topSemanticTieCount());
                    headroomSum +=
                            decision.selectedRank().minimumRequiredHeadroom();

                    String token =
                            decision.request().bindingKey().canonicalToken();
                    SkyIslandMaterialResolutionDecision previous =
                            stableByRequest.putIfAbsent(token, decision);
                    if (previous != null && !previous.equals(decision)) {
                        unstable++;
                    }
                }
            }
        }

        return new Stats(
                material,
                resolved,
                semantic,
                backendTie,
                maxCompatible,
                maxTies,
                stableByRequest.size(),
                unstable,
                resolved == 0 ? 0.0 : headroomSum / resolved);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private static void centered(
            Graphics2D graphics, String label, int x, int width, int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record DecisionGrid(
            List<SkyIslandMaterialResolutionDecision>[][] decisions) {}

    private record Stats(
            int materialSamples,
            int resolvedUses,
            int semanticWinnerUses,
            int backendTieBreakUses,
            int maxCompatibleCandidates,
            int maxTopSemanticTieCount,
            int uniqueRequests,
            int unstableRequests,
            double meanSelectedMinHeadroom) {}

    private record Selection(String role, long key) {}
}
