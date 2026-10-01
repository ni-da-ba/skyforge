package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelNetworkPlanner;
import io.github.nidaba.skyforge.world.SkyIslandChannelSegment;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyCandidate;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyPlanner;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0008 retained-waterbody evidence. */
public final class AuthorshipWaterbodyPlanningCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private static final List<Long> KEYS = List.of(83L, 77L, 118L, 241L, 512L, 811L);

    private AuthorshipWaterbodyPlanningCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-waterbodies-v1");
        Files.createDirectories(out);

        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 64), BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,candidates,ponds,lakes,wetlands,maxCatchmentFraction,maxPersistence,maxBasinScale\n");
        StringBuilder candidateDetails = new StringBuilder(
                "islandKey,morphology,ordinal,kind,sinkCell,catchmentCells,catchmentFraction,relativeInflow,retention,saturation,persistence,basinScale\n");

        for (int n = 0; n < KEYS.size(); n++) {
            long key = KEYS.get(n);
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandWaterbodyPlan waterbodies = SkyIslandWaterbodyPlanner.plan(descriptor);
            SkyIslandChannelNetworkPlan channels = SkyIslandChannelNetworkPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, waterbodies, channels);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            double maxCatchment = waterbodies.candidates().stream()
                    .mapToDouble(SkyIslandWaterbodyCandidate::catchmentFraction)
                    .max().orElse(0.0);
            double maxPersistence = waterbodies.candidates().stream()
                    .mapToDouble(SkyIslandWaterbodyCandidate::persistence)
                    .max().orElse(0.0);
            double maxScale = waterbodies.candidates().stream()
                    .mapToDouble(SkyIslandWaterbodyCandidate::basinScale)
                    .max().orElse(0.0);

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 64);
            atlasGraphics.setColor(Color.BLACK);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            atlasGraphics.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 18);
            atlasGraphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            atlasGraphics.drawString(
                    "P=" + waterbodies.count(SkyIslandWaterbodyKind.POND)
                            + " L=" + waterbodies.count(SkyIslandWaterbodyKind.LAKE)
                            + " W=" + waterbodies.count(SkyIslandWaterbodyKind.WETLAND)
                            + " catch=" + format(maxCatchment)
                            + " persist=" + format(maxPersistence),
                    x + 8,
                    y + 38);
            atlasGraphics.drawImage(image, x, y + 64, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(waterbodies.candidates().size()).append(',')
                    .append(waterbodies.count(SkyIslandWaterbodyKind.POND)).append(',')
                    .append(waterbodies.count(SkyIslandWaterbodyKind.LAKE)).append(',')
                    .append(waterbodies.count(SkyIslandWaterbodyKind.WETLAND)).append(',')
                    .append(format(maxCatchment)).append(',')
                    .append(format(maxPersistence)).append(',')
                    .append(format(maxScale)).append('\n');

            for (int ordinal = 0; ordinal < waterbodies.candidates().size(); ordinal++) {
                SkyIslandWaterbodyCandidate candidate = waterbodies.candidates().get(ordinal);
                candidateDetails.append(key).append(',')
                        .append(descriptor.morphologyFamily().identifier()).append(',')
                        .append(ordinal).append(',')
                        .append(candidate.kind()).append(',')
                        .append(candidate.sinkCellIndex()).append(',')
                        .append(candidate.catchmentCellCount()).append(',')
                        .append(format(candidate.catchmentFraction())).append(',')
                        .append(format(candidate.relativeInflow())).append(',')
                        .append(format(candidate.retentionPotential())).append(',')
                        .append(format(candidate.saturationPotential())).append(',')
                        .append(format(candidate.persistence())).append(',')
                        .append(format(candidate.basinScale())).append('\n');
            }
        }

        atlasGraphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("candidates.csv"), candidateDetails.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0008</title>"
                        + "<h1>Retained waterbody planning</h1>"
                        + "<p>Gray lines: accepted AUTH-0007 channels. Cyan: pond. Dark blue: lake. Teal: wetland. "
                        + "Outer symbol size follows semantic basin scale; inner dot follows persistence. "
                        + "Symbols are planning anchors, not literal shorelines or water levels.</p>"
                        + "<p>Key 83 is the retained-basin case; the remaining panels are accepted drainage controls that verify "
                        + "AUTH-0008 does not invent waterbodies where AUTH-0005 retained none.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"candidates.csv\">candidates.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandWaterbodyPlan waterbodies,
            SkyIslandChannelNetworkPlan channels) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);

        double radius = descriptor.nominalRadius();
        g.setColor(new Color(185, 185, 185));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandChannelSegment segment : channels.segments()) {
            g.drawLine(
                    mapX(segment.start(), radius),
                    mapY(segment.start(), radius),
                    mapX(segment.end(), radius),
                    mapY(segment.end(), radius));
        }

        for (SkyIslandWaterbodyCandidate candidate : waterbodies.candidates()) {
            Color color = switch (candidate.kind()) {
                case POND -> new Color(50, 165, 205);
                case LAKE -> new Color(30, 65, 165);
                case WETLAND -> new Color(35, 135, 125);
            };
            int x = mapX(candidate.anchor(), radius);
            int y = mapY(candidate.anchor(), radius);
            int outer = 10 + (int) Math.round(18.0 * candidate.basinScale());
            int inner = 4 + (int) Math.round(8.0 * candidate.persistence());
            g.setColor(color);
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(x - outer / 2, y - outer / 2, outer, outer);
            g.fillOval(x - inner / 2, y - inner / 2, inner, inner);
        }

        g.dispose();
        return image;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
