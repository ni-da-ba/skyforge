package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologyField;
import io.github.nidaba.skyforge.world.SkyIslandHydrologySample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/** Generates the deterministic AUTH-0004 hydrological-planning review atlas. */
public final class AuthorshipHydrologyCorpusCli {
    private static final int MAP_SIZE = 320;
    private static final int LABEL_HEIGHT = 60;
    private static final long WORLD_SEED = 0x534B59464F524745L;

    private AuthorshipHydrologyCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        Path output = arguments.length == 1 ? Path.of(arguments[0]) : Path.of("build", "evidence", "authorship-hydrology-v1");
        Files.createDirectories(output);
        List<Candidate> candidates = new ArrayList<>();
        for (long key = 0; key < 4096; key++) {
            candidates.add(new Candidate(key, SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(WORLD_SEED, 4L, 41L, key))));
        }
        Set<Long> used = new HashSet<>();
        List<Selection> selections = List.of(
                select("hydrological", candidates, used, SkyIslandDescriptor::hydrologicalPotential),
                select("wet", candidates, used, SkyIslandDescriptor::moistureTendency),
                select("impermeable", candidates, used, d -> 1.0 - d.permeability()),
                select("basin", candidates, used, d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.BASIN ? 2.0 : 0.0) + d.hydrologicalPotential()),
                select("exposed", candidates, used, SkyIslandDescriptor::exposureTendency),
                select("large", candidates, used, SkyIslandDescriptor::nominalRadius));

        BufferedImage atlas = new BufferedImage(3 * MAP_SIZE, 2 * (MAP_SIZE + LABEL_HEIGHT), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        StringBuilder manifest = new StringBuilder("role,islandKey,morphology,radius,moisture,permeability,hydrology,exposure\n");
        for (int i = 0; i < selections.size(); i++) {
            Selection selection = selections.get(i);
            SkyIslandDescriptor d = selection.candidate().descriptor();
            BufferedImage map = render(d);
            ImageIO.write(map, "png", output.resolve(selection.role() + ".png").toFile());
            int x = (i % 3) * MAP_SIZE;
            int y = (i / 3) * (MAP_SIZE + LABEL_HEIGHT);
            g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.drawString(selection.role() + " / " + d.morphologyFamily().identifier(), x + 8, y + 21);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString("key=" + selection.candidate().islandKey() + " H=" + shortValue(d.hydrologicalPotential()) + " M=" + shortValue(d.moistureTendency()) + " P=" + shortValue(d.permeability()), x + 8, y + 43);
            g.drawImage(map, x, y + LABEL_HEIGHT, null);
            manifest.append(selection.role()).append(',').append(selection.candidate().islandKey()).append(',')
                    .append(d.morphologyFamily().identifier()).append(',').append(d.nominalRadius()).append(',')
                    .append(d.moistureTendency()).append(',').append(d.permeability()).append(',')
                    .append(d.hydrologicalPotential()).append(',').append(d.exposureTendency()).append('\n');
        }
        g.dispose();
        ImageIO.write(atlas, "png", output.resolve("atlas.png").toFile());
        Files.writeString(output.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"), "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0004 hydrology</title><h1>AUTH-0004 hydrological planning atlas</h1><p>Blue = retention, cyan/white = drainage, orange = edge outflow. Direction ticks indicate downhill tendency.</p><img src=\"atlas.png\" style=\"max-width:100%\"><p><a href=\"manifest.csv\">manifest.csv</a></p>", StandardCharsets.UTF_8);
        System.out.println(output.toAbsolutePath());
    }

    private static BufferedImage render(SkyIslandDescriptor d) {
        SkyIslandHydrologyField field = SkyIslandHydrologyField.create(d);
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        double extent = d.nominalRadius() * 1.08;
        for (int py = 0; py < MAP_SIZE; py++) {
            double z = extent - 2.0 * extent * py / (MAP_SIZE - 1.0);
            for (int px = 0; px < MAP_SIZE; px++) {
                double x = -extent + 2.0 * extent * px / (MAP_SIZE - 1.0);
                SkyIslandHydrologySample s = field.sample(new SkyIslandLocalPosition(x, z));
                if (s.runoffPotential() == 0.0 && s.drainagePotential() == 0.0) {
                    image.setRGB(px, py, Color.WHITE.getRGB());
                } else {
                    int r = clamp255(24 + 205 * s.outflowPotential() + 90 * s.drainagePotential());
                    int gr = clamp255(42 + 185 * s.drainagePotential() + 55 * s.retentionPotential());
                    int b = clamp255(58 + 180 * s.retentionPotential() + 150 * s.drainagePotential());
                    image.setRGB(px, py, new Color(r, gr, b).getRGB());
                }
            }
        }
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 20, 20, 170));
        int spacing = 32;
        for (int py = spacing / 2; py < MAP_SIZE; py += spacing) {
            double z = extent - 2.0 * extent * py / (MAP_SIZE - 1.0);
            for (int px = spacing / 2; px < MAP_SIZE; px += spacing) {
                double x = -extent + 2.0 * extent * px / (MAP_SIZE - 1.0);
                SkyIslandHydrologySample s = field.sample(new SkyIslandLocalPosition(x, z));
                if (s.drainagePotential() > 0.12) {
                    int dx = (int) Math.round(s.flowX() * 10.0);
                    int dy = (int) Math.round(-s.flowZ() * 10.0);
                    g.drawLine(px - dx / 2, py - dy / 2, px + dx / 2, py + dy / 2);
                }
            }
        }
        g.dispose();
        return image;
    }

    private static Selection select(String role, List<Candidate> candidates, Set<Long> used, ToDoubleFunction<SkyIslandDescriptor> score) {
        Candidate best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate c : candidates) if (!used.contains(c.key()) && score.applyAsDouble(c.descriptor()) > bestScore) { best = c; bestScore = score.applyAsDouble(c.descriptor()); }
        if (best == null) throw new IllegalStateException("no representative");
        used.add(best.key()); return new Selection(role, best);
    }

    private static int clamp255(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static String shortValue(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    private record Candidate(long key, SkyIslandDescriptor descriptor) {}
    private record Selection(String role, Candidate candidate) {}
}
