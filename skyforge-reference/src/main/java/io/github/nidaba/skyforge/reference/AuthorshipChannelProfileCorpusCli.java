package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfileKind;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCell;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlan;
import io.github.nidaba.skyforge.world.SkyIslandRiparianCorridorPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlan;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
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

/** Generates deterministic AUTH-0012 channel-profile review evidence. */
public final class AuthorshipChannelProfileCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;

    private AuthorshipChannelProfileCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-channel-profiles-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 68), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,segments,alluvial,incised,cascade,maxWidth,maxDepth,maxIncision,maxGradient\n");
        StringBuilder profilesCsv = new StringBuilder(
                "islandKey,sourceCell,downstreamCell,role,streamOrder,kind,relativeDischarge,gradient,streamPower,width,depth,incision\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandRiparianCorridorPlan riparian = SkyIslandRiparianCorridorPlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, profiles, riparian, waterbodies);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 68);
            ag.setColor(Color.BLACK);
            ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            ag.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 7, y + 18);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            ag.drawString(
                    "A=" + profiles.count(SkyIslandChannelProfileKind.ALLUVIAL)
                            + " I=" + profiles.count(SkyIslandChannelProfileKind.INCISED)
                            + " C=" + profiles.count(SkyIslandChannelProfileKind.CASCADE),
                    x + 7,
                    y + 35);
            ag.drawString(
                    String.format(Locale.ROOT, "max W=%.2f D=%.2f I=%.2f G=%.2f",
                            profiles.maxWidthPotential(),
                            profiles.maxDepthPotential(),
                            profiles.maxIncisionPotential(),
                            profiles.maxGradientPotential()),
                    x + 7,
                    y + 51);
            ag.drawImage(image, x, y + 68, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(profiles.profiles().size()).append(',')
                    .append(profiles.count(SkyIslandChannelProfileKind.ALLUVIAL)).append(',')
                    .append(profiles.count(SkyIslandChannelProfileKind.INCISED)).append(',')
                    .append(profiles.count(SkyIslandChannelProfileKind.CASCADE)).append(',')
                    .append(format(profiles.maxWidthPotential())).append(',')
                    .append(format(profiles.maxDepthPotential())).append(',')
                    .append(format(profiles.maxIncisionPotential())).append(',')
                    .append(format(profiles.maxGradientPotential())).append('\n');

            for (SkyIslandChannelProfile profile : profiles.profiles()) {
                profilesCsv.append(key).append(',')
                        .append(profile.segment().sourceCellIndex()).append(',')
                        .append(profile.segment().downstreamCellIndex()).append(',')
                        .append(profile.segment().role()).append(',')
                        .append(profile.segment().streamOrder()).append(',')
                        .append(profile.kind()).append(',')
                        .append(format(profile.segment().relativeDischarge())).append(',')
                        .append(format(profile.gradientPotential())).append(',')
                        .append(format(profile.streamPowerPotential())).append(',')
                        .append(format(profile.bankfullWidthPotential())).append(',')
                        .append(format(profile.depthPotential())).append(',')
                        .append(format(profile.incisionPotential())).append('\n');
            }
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("profiles.csv"), profilesCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0012</title>"
                        + "<h1>Semantic channel profiles</h1>"
                        + "<p>Teal: alluvial. Purple: incised. Orange: cascade. Stroke width follows normalized bankfull-width potential. "
                        + "Pale green cells show the accepted AUTH-0011 riparian corridor; pale blue cells show accepted standing water. "
                        + "All dimensions remain semantic realization potentials, not block or metric measurements.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"profiles.csv\">profiles.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelProfilePlan profiles,
            SkyIslandRiparianCorridorPlan riparian,
            SkyIslandWaterbodyFootprintPlan waterbodies) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);
        double radius = descriptor.nominalRadius();

        g.setColor(new Color(220, 238, 216));
        for (SkyIslandRiparianCell cell : riparian.cells()) {
            int x = mapX(cell.position(), radius);
            int y = mapY(cell.position(), radius);
            g.fillRect(x - 2, y - 2, 5, 5);
        }
        g.setColor(new Color(198, 226, 244));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.fillRect(x - 2, y - 2, 5, 5);
            }
        }

        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            g.setColor(color(profile.kind()));
            float width = (float) (1.0 + 6.0 * profile.bankfullWidthPotential());
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(
                    mapX(profile.segment().start(), radius),
                    mapY(profile.segment().start(), radius),
                    mapX(profile.segment().end(), radius),
                    mapY(profile.segment().end(), radius));
        }
        g.dispose();
        return image;
    }

    private static Color color(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> new Color(25, 135, 145);
            case INCISED -> new Color(95, 65, 150);
            case CASCADE -> new Color(220, 105, 35);
        };
    }

    private static int mapX(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((position.x() / radius + 1.0) * 0.5 * (MAP - 1));
    }

    private static int mapY(SkyIslandLocalPosition position, double radius) {
        return (int) Math.round((1.0 - (position.z() / radius + 1.0) * 0.5) * (MAP - 1));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
