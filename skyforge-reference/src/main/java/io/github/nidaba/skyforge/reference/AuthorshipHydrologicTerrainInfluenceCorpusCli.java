package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainCell;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainInfluencePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainInfluencePlanner;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainResponseKind;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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

/** Generates deterministic AUTH-0014 hydrologic terrain-influence review evidence. */
public final class AuthorshipHydrologicTerrainInfluenceCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;

    private AuthorshipHydrologicTerrainInfluenceCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-hydrologic-terrain-influence-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 83L);
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 68), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "islandKey,morphology,cells,incisionDominant,depositionDominant,floodplainDominant,dropDominant,maxIncision,maxDeposition,maxFloodplain,maxDropShaping\n");
        StringBuilder cellsCsv = new StringBuilder(
                "islandKey,cellIndex,dominantResponse,incision,deposition,floodplain,dropShaping\n");

        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 6L, 61L, key));
            SkyIslandHydrologicTerrainInfluencePlan influence =
                    SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
            BufferedImage image = render(descriptor, influence, profiles, waterbodies);
            ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());

            int x = (n % 3) * MAP;
            int y = (n / 3) * (MAP + 68);
            ag.setColor(Color.BLACK);
            ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            ag.drawString("key=" + key + " / " + descriptor.morphologyFamily().identifier(), x + 7, y + 18);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            ag.drawString(
                    "I=" + influence.count(SkyIslandHydrologicTerrainResponseKind.INCISION)
                            + " D=" + influence.count(SkyIslandHydrologicTerrainResponseKind.DEPOSITION)
                            + " F=" + influence.count(SkyIslandHydrologicTerrainResponseKind.FLOODPLAIN)
                            + " X=" + influence.count(SkyIslandHydrologicTerrainResponseKind.DROP_SHAPING),
                    x + 7,
                    y + 35);
            ag.drawString(
                    String.format(Locale.ROOT, "max I=%.2f D=%.2f F=%.2f X=%.2f",
                            influence.maxIncisionPotential(),
                            influence.maxDepositionPotential(),
                            influence.maxFloodplainPotential(),
                            influence.maxDropShapingPotential()),
                    x + 7,
                    y + 51);
            ag.drawImage(image, x, y + 68, null);

            manifest.append(key).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(influence.cells().size()).append(',')
                    .append(influence.count(SkyIslandHydrologicTerrainResponseKind.INCISION)).append(',')
                    .append(influence.count(SkyIslandHydrologicTerrainResponseKind.DEPOSITION)).append(',')
                    .append(influence.count(SkyIslandHydrologicTerrainResponseKind.FLOODPLAIN)).append(',')
                    .append(influence.count(SkyIslandHydrologicTerrainResponseKind.DROP_SHAPING)).append(',')
                    .append(format(influence.maxIncisionPotential())).append(',')
                    .append(format(influence.maxDepositionPotential())).append(',')
                    .append(format(influence.maxFloodplainPotential())).append(',')
                    .append(format(influence.maxDropShapingPotential())).append('\n');

            for (SkyIslandHydrologicTerrainCell cell : influence.cells()) {
                cellsCsv.append(key).append(',')
                        .append(cell.watershedCellIndex()).append(',')
                        .append(cell.dominantResponse()).append(',')
                        .append(format(cell.incisionPotential())).append(',')
                        .append(format(cell.depositionPotential())).append(',')
                        .append(format(cell.floodplainPotential())).append(',')
                        .append(format(cell.dropShapingPotential())).append('\n');
            }
        }
        ag.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("cells.csv"), cellsCsv.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0014</title>"
                        + "<h1>Hydrologic terrain influence</h1>"
                        + "<p>Red: incision. Gold: deposition. Green: floodplain. Magenta: drop shaping. "
                        + "Cell intensity follows the dominant normalized potential. Pale blue is accepted standing water; gray lines are accepted channel profiles. "
                        + "These are terrain-response semantics, not elevation edits or block geometry.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · <a href=\"cells.csv\">cells.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage render(
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainInfluencePlan influence,
            SkyIslandChannelProfilePlan profiles,
            SkyIslandWaterbodyFootprintPlan waterbodies) {
        BufferedImage image = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, MAP, MAP);
        double radius = descriptor.nominalRadius();

        g.setColor(new Color(198, 226, 244));
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                int x = mapX(cell.position(), radius);
                int y = mapY(cell.position(), radius);
                g.fillRect(x - 2, y - 2, 5, 5);
            }
        }

        for (SkyIslandHydrologicTerrainCell cell : influence.cells()) {
            Color base = color(cell.dominantResponse());
            g.setColor(blendFromWhite(base, 0.35 + 0.65 * cell.dominantPotential()));
            int x = mapX(cell.position(), radius);
            int y = mapY(cell.position(), radius);
            g.fillRect(x - 2, y - 2, 5, 5);
        }

        g.setColor(new Color(110, 110, 110));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            g.drawLine(
                    mapX(profile.segment().start(), radius),
                    mapY(profile.segment().start(), radius),
                    mapX(profile.segment().end(), radius),
                    mapY(profile.segment().end(), radius));
        }
        g.dispose();
        return image;
    }

    private static Color color(SkyIslandHydrologicTerrainResponseKind kind) {
        return switch (kind) {
            case INCISION -> new Color(190, 55, 45);
            case DEPOSITION -> new Color(205, 145, 35);
            case FLOODPLAIN -> new Color(65, 145, 80);
            case DROP_SHAPING -> new Color(175, 55, 150);
        };
    }

    private static Color blendFromWhite(Color color, double strength) {
        double s = Math.max(0.0, Math.min(1.0, strength));
        return new Color(
                (int) Math.round(255.0 + s * (color.getRed() - 255.0)),
                (int) Math.round(255.0 + s * (color.getGreen() - 255.0)),
                (int) Math.round(255.0 + s * (color.getBlue() - 255.0)));
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
