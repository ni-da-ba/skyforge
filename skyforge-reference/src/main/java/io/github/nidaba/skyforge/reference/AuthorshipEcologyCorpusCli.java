package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandEcologyField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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

/** Generates the deterministic AUTH-0003 multi-island semantic ecology atlas. */
public final class AuthorshipEcologyCorpusCli {
    private static final int MAP_SIZE = 320;
    private static final int LABEL_HEIGHT = 58;
    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 3L;
    private static final long CLUSTER_KEY = 31L;

    private AuthorshipEcologyCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("usage: AuthorshipEcologyCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", "authorship-ecology-v1");
        Files.createDirectories(output);

        List<Candidate> candidates = new ArrayList<>();
        for (long islandKey = 0; islandKey < 4096; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, islandKey));
            candidates.add(new Candidate(islandKey, descriptor));
        }

        Set<Long> used = new HashSet<>();
        List<Selection> selections = List.of(
                select("cold", candidates, used, d -> 1.0 - d.temperatureTendency()),
                select("warm", candidates, used, SkyIslandDescriptor::temperatureTendency),
                select("wet", candidates, used, SkyIslandDescriptor::moistureTendency),
                select("dry", candidates, used, d -> 1.0 - d.moistureTendency()),
                select("hydrological", candidates, used, SkyIslandDescriptor::hydrologicalPotential),
                select("large", candidates, used, SkyIslandDescriptor::nominalRadius));

        BufferedImage atlas = new BufferedImage(3 * MAP_SIZE, 2 * (MAP_SIZE + LABEL_HEIGHT), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        StringBuilder manifest = new StringBuilder("role,islandKey,morphology,radius,temperature,moisture,hydrology,ecology\n");
        for (int i = 0; i < selections.size(); i++) {
            Selection selection = selections.get(i);
            SkyIslandDescriptor descriptor = selection.candidate().descriptor();
            BufferedImage map = render(descriptor);
            ImageIO.write(map, "png", output.resolve(selection.role() + ".png").toFile());
            int x = (i % 3) * MAP_SIZE;
            int y = (i / 3) * (MAP_SIZE + LABEL_HEIGHT);
            graphics.setColor(Color.BLACK);
            graphics.drawString(selection.role() + " / " + descriptor.morphologyFamily().identifier(), x + 8, y + 21);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            graphics.drawString("key=" + selection.candidate().islandKey()
                    + "  T=" + shortValue(descriptor.temperatureTendency())
                    + " M=" + shortValue(descriptor.moistureTendency()), x + 8, y + 43);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.drawImage(map, x, y + LABEL_HEIGHT, null);
            manifest.append(selection.role()).append(',')
                    .append(selection.candidate().islandKey()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(descriptor.nominalRadius()).append(',')
                    .append(descriptor.temperatureTendency()).append(',')
                    .append(descriptor.moistureTendency()).append(',')
                    .append(descriptor.hydrologicalPotential()).append(',')
                    .append(descriptor.ecologicalPotential()).append('\n');
        }
        graphics.dispose();
        ImageIO.write(atlas, "png", output.resolve("atlas.png").toFile());
        Files.writeString(output.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"), indexHtml(selections), StandardCharsets.UTF_8);
        System.out.println(output.toAbsolutePath());
    }

    private static Selection select(
            String role,
            List<Candidate> candidates,
            Set<Long> used,
            ToDoubleFunction<SkyIslandDescriptor> score) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : candidates) {
            if (used.contains(candidate.islandKey())) {
                continue;
            }
            double value = score.applyAsDouble(candidate.descriptor());
            if (value > bestScore) {
                best = candidate;
                bestScore = value;
            }
        }
        if (best == null) {
            throw new IllegalStateException("unable to select representative island");
        }
        used.add(best.islandKey());
        return new Selection(role, best);
    }

    private static BufferedImage render(SkyIslandDescriptor descriptor) {
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        double extent = descriptor.nominalRadius() * 1.08;
        for (int py = 0; py < MAP_SIZE; py++) {
            double z = extent - 2.0 * extent * py / (MAP_SIZE - 1.0);
            for (int px = 0; px < MAP_SIZE; px++) {
                double x = -extent + 2.0 * extent * px / (MAP_SIZE - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(position) <= 0.0) {
                    image.setRGB(px, py, Color.WHITE.getRGB());
                } else {
                    image.setRGB(px, py, color(ecology.sample(position).regime()).getRGB());
                }
            }
        }
        return image;
    }

    private static Color color(SkyIslandEcologyRegime regime) {
        return switch (regime) {
            case COLD_BARREN -> new Color(211, 220, 224);
            case ALPINE -> new Color(139, 149, 137);
            case BOREAL_WOODLAND -> new Color(43, 86, 68);
            case TEMPERATE_WOODLAND -> new Color(72, 120, 62);
            case HUMID_WOODLAND -> new Color(31, 111, 76);
            case OPEN_GRASSLAND -> new Color(166, 176, 89);
            case DRY_SCRUB -> new Color(181, 145, 82);
            case WETLAND -> new Color(69, 130, 125);
        };
    }

    private static String shortValue(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String indexHtml(List<Selection> selections) {
        StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>AUTH-0003 ecology</title>");
        html.append("<h1>AUTH-0003 semantic ecology atlas</h1>");
        html.append("<p>Six deterministically selected authored islands spanning climate, moisture, hydrology, and scale extrema.</p>");
        html.append("<p><img src=\"atlas.png\" style=\"max-width:100%\"></p><ul>");
        for (Selection selection : selections) {
            html.append("<li><a href=\"").append(selection.role()).append(".png\">")
                    .append(selection.role()).append("</a></li>");
        }
        html.append("</ul><p><a href=\"manifest.csv\">manifest.csv</a></p>");
        return html.toString();
    }

    private record Candidate(long islandKey, SkyIslandDescriptor descriptor) {}
    private record Selection(String role, Candidate candidate) {}
}
