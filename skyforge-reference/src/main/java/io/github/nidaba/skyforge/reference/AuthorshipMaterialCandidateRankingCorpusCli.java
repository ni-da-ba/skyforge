package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCandidateRank;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCandidateRanker;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
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

/** Generates deterministic AUTH-0041 semantic candidate-ranking evidence. */
public final class AuthorshipMaterialCandidateRankingCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final List<DiagnosticCandidate> CANDIDATES = List.of(
            new DiagnosticCandidate(
                    "MATRIX WINS",
                    new SkyIslandMaterialCapabilityProfile(0.94, 0.20, 0.20, 0.20, 0.20),
                    new Color(126, 92, 68)),
            new DiagnosticCandidate(
                    "FABRIC WINS",
                    new SkyIslandMaterialCapabilityProfile(0.92, 0.94, 0.20, 0.20, 0.20),
                    new Color(86, 128, 86)),
            new DiagnosticCandidate(
                    "ALTERATION WINS",
                    new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.94, 0.20, 0.20),
                    new Color(156, 98, 88)),
            new DiagnosticCandidate(
                    "WATER WINS",
                    new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.94, 0.20),
                    new Color(74, 112, 156)),
            new DiagnosticCandidate(
                    "ACCENT WINS",
                    new SkyIslandMaterialCapabilityProfile(0.20, 0.20, 0.20, 0.20, 0.94),
                    new Color(134, 112, 72)),
            new DiagnosticCandidate(
                    "GENERALIST WINS",
                    SkyIslandMaterialCapabilityProfile.uniform(0.86),
                    new Color(104, 92, 136)));

    private AuthorshipMaterialCandidateRankingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of(
                        "build",
                        "evidence",
                        "authorship-material-candidate-ranking-v1");
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
                "role,islandKey,morphology,materialSamples,rankedUses,"
                        + "matrixWins,fabricWins,alterationWins,waterWins,"
                        + "accentWins,generalistWins,semanticTies,uniqueRequests\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor =
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandMaterialBindingRequestField field =
                    SkyIslandMaterialBindingRequestField.create(descriptor);
            SkyIslandMaterialBindingRequestSelection[][] samples =
                    sample(descriptor, field);
            Stats stats = stats(samples);
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
                    .append(stats.rankedUses()).append(',')
                    .append(stats.wins(0)).append(',')
                    .append(stats.wins(1)).append(',')
                    .append(stats.wins(2)).append(',')
                    .append(stats.wins(3)).append(',')
                    .append(stats.wins(4)).append(',')
                    .append(stats.wins(5)).append(',')
                    .append(stats.semanticTies()).append(',')
                    .append(stats.uniqueRequests()).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0041</title>"
                        + "<h1>Compatible-candidate semantic ranking</h1>"
                        + "<p>All panels sample semantic depth 0.52. Each pixel shows how many "
                        + "local AUTH-0039 requests are won by that backend-neutral diagnostic "
                        + "capability profile after AUTH-0040 compatibility filtering. White means "
                        + "the profile wins no local request. The evidence expects matrix/fabric/"
                        + "alteration/water/accent specialists to win their authored functions; "
                        + "GENERALIST should remain a compatible fallback rather than dominate.</p>"
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

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialBindingRequestSelection[][] samples,
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
                        "depth=%.2f material=%d ranked uses=%d unique requests=%d",
                        DEPTH,
                        stats.materialSamples(),
                        stats.rankedUses(),
                        stats.uniqueRequests()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "wins M/F/A/W/X/G=%d/%d/%d/%d/%d/%d semantic ties=%d",
                        stats.wins(0),
                        stats.wins(1),
                        stats.wins(2),
                        stats.wins(3),
                        stats.wins(4),
                        stats.wins(5),
                        stats.semanticTies()),
                7,
                50);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(
                    graphics,
                    CANDIDATES.get(panel).label(),
                    panel * MAP,
                    MAP,
                    66);
            renderPanel(
                    image,
                    panel * MAP,
                    samples,
                    panel,
                    CANDIDATES.get(panel).color());
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
            SkyIslandMaterialBindingRequestSelection[][] samples,
            int candidateIndex,
            Color color) {
        int height = samples.length;
        int width = samples[0].length;
        for (int py = 0; py < MAP; py++) {
            int sy = Math.min(
                    height - 1,
                    (int) Math.round(py * (height - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx = Math.min(
                        width - 1,
                        (int) Math.round(px * (width - 1.0) / (MAP - 1.0)));
                int wins = winnerCounts(samples[sy][sx])[candidateIndex];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        wins == 0 ? Color.WHITE.getRGB() : shade(color, wins).getRGB());
            }
        }
    }

    private static int[] winnerCounts(SkyIslandMaterialBindingRequestSelection selection) {
        int[] counts = new int[CANDIDATES.size()];
        for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
            List<SkyIslandMaterialCandidateRank> ranks =
                    SkyIslandMaterialCandidateRanker.rankCompatible(
                            use.request(),
                            CANDIDATES.stream()
                                    .map(DiagnosticCandidate::profile)
                                    .toList());
            if (ranks.isEmpty()) {
                continue;
            }
            SkyIslandMaterialCandidateRank winner = ranks.get(0);
            int index = candidateIndex(winner.profile());
            counts[index]++;
        }
        return counts;
    }

    private static int candidateIndex(SkyIslandMaterialCapabilityProfile profile) {
        for (int i = 0; i < CANDIDATES.size(); i++) {
            if (CANDIDATES.get(i).profile().equals(profile)) {
                return i;
            }
        }
        throw new IllegalStateException("ranked diagnostic profile is unknown");
    }

    private static Color shade(Color base, int wins) {
        double scale = Math.min(1.0, 0.52 + 0.16 * wins);
        return new Color(
                channel(255.0 - (255.0 - base.getRed()) * scale),
                channel(255.0 - (255.0 - base.getGreen()) * scale),
                channel(255.0 - (255.0 - base.getBlue()) * scale));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Stats stats(
            SkyIslandMaterialBindingRequestSelection[][] samples) {
        int material = 0;
        int rankedUses = 0;
        int semanticTies = 0;
        int[] wins = new int[CANDIDATES.size()];
        Map<String, Boolean> uniqueRequests = new HashMap<>();

        for (SkyIslandMaterialBindingRequestSelection[] row : samples) {
            for (SkyIslandMaterialBindingRequestSelection selection : row) {
                if (!selection.materialPresent()) {
                    continue;
                }
                material++;
                for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                    rankedUses++;
                    uniqueRequests.put(
                            use.request().bindingKey().canonicalToken(), Boolean.TRUE);
                    List<SkyIslandMaterialCandidateRank> ranks =
                            SkyIslandMaterialCandidateRanker.rankCompatible(
                                    use.request(),
                                    CANDIDATES.stream()
                                            .map(DiagnosticCandidate::profile)
                                            .toList());
                    if (ranks.isEmpty()) {
                        throw new IllegalStateException(
                                "accepted AUTH-0039 request has no diagnostic compatible candidate");
                    }
                    wins[candidateIndex(ranks.get(0).profile())]++;
                    if (ranks.size() > 1
                            && ranks.get(0).semanticallyTiedWith(ranks.get(1))) {
                        semanticTies++;
                    }
                }
            }
        }

        return new Stats(
                material, rankedUses, wins, semanticTies, uniqueRequests.size());
    }

    private static void centered(
            Graphics2D graphics, String label, int x, int width, int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Stats(
            int materialSamples,
            int rankedUses,
            int[] wins,
            int semanticTies,
            int uniqueRequests) {

        int wins(int candidateIndex) {
            return wins[candidateIndex];
        }
    }

    private record DiagnosticCandidate(
            String label,
            SkyIslandMaterialCapabilityProfile profile,
            Color color) {}

    private record Selection(String role, long key) {}
}
