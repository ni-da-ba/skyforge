package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologyFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandGeologySample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0022 subsurface geological field evidence. */
public final class AuthorshipSubsurfaceGeologyCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final long PROVINCE = 7L;
    private static final long CLUSTER = 71L;
    private static final int MAP = 160;
    private static final int HEADER = 64;
    private static final int SPECIMEN_WIDTH = 4 * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipSubsurfaceGeologyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-subsurface-geology-v1");
        Files.createDirectories(out);

        List<Candidate> candidates = new ArrayList<>();
        for (long key = 0; key < 4096; key++) {
            candidates.add(new Candidate(
                    key,
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, PROVINCE, CLUSTER, key))));
        }

        Set<Long> used = new HashSet<>();
        List<Selection> selections = List.of(
                select("competent", candidates, used, SkyIslandDescriptor::rockCompetence),
                select("weak", candidates, used, d -> 1.0 - d.rockCompetence()),
                select("permeable", candidates, used, SkyIslandDescriptor::permeability),
                select("hydrologic", candidates, used, SkyIslandDescriptor::hydrologicalPotential),
                select("eroded", candidates, used, SkyIslandDescriptor::erosionMaturity),
                select(
                        "spine",
                        candidates,
                        used,
                        d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.SPINE ? 2.0 : 0.0)
                                + normalizedRelief(d)));

        BufferedImage atlas =
                new BufferedImage(2 * SPECIMEN_WIDTH, 3 * SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,islandKey,morphology,rockCompetence,permeability,hydrologicalPotential,"
                        + "erosionMaturity,meanCompetence,meanFracture,meanConnectedPermeability,"
                        + "meanGroundwater,meanVoidSuitability,maxVoidSuitability\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = selection.candidate().descriptor();
            SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
            FieldStats stats = measure(descriptor, geology);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, geology, stats);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.candidate().key() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.candidate().key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(format(descriptor.rockCompetence())).append(',')
                    .append(format(descriptor.permeability())).append(',')
                    .append(format(descriptor.hydrologicalPotential())).append(',')
                    .append(format(descriptor.erosionMaturity())).append(',')
                    .append(format(stats.meanCompetence())).append(',')
                    .append(format(stats.meanFracture())).append(',')
                    .append(format(stats.meanPermeability())).append(',')
                    .append(format(stats.meanGroundwater())).append(',')
                    .append(format(stats.meanVoid())).append(',')
                    .append(format(stats.maxVoid())).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0022</title>"
                        + "<h1>Subsurface geological fields</h1>"
                        + "<p>SHALLOW COMPETENCE is a plan view at semantic depth 0.15. "
                        + "FRACTURE, GROUNDWATER, and VOID SUITABILITY are x/depth sections through "
                        + "the island-local z=0 plane. Depth increases downward. White is outside "
                        + "current naturalized ownership.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Selection select(
            String role,
            List<Candidate> candidates,
            Set<Long> used,
            ToDoubleFunction<SkyIslandDescriptor> score) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : candidates) {
            if (used.contains(candidate.key())) {
                continue;
            }
            double value = score.applyAsDouble(candidate.descriptor());
            if (value > bestScore) {
                best = candidate;
                bestScore = value;
            }
        }
        if (best == null) {
            throw new IllegalStateException("unable to select geological representative");
        }
        used.add(best.key());
        return new Selection(role, best);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            FieldStats stats) {
        BufferedImage image =
                new BufferedImage(SPECIMEN_WIDTH, SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                role + " / key=" + descriptor.identity().islandKey()
                        + " / " + descriptor.morphologyFamily().identifier(),
                7,
                17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "rock=%.3f perm=%.3f hydro=%.3f erosion=%.3f   mean void=%.3f max=%.3f",
                        descriptor.rockCompetence(),
                        descriptor.permeability(),
                        descriptor.hydrologicalPotential(),
                        descriptor.erosionMaturity(),
                        stats.meanVoid(),
                        stats.maxVoid()),
                7,
                35);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "SHALLOW COMPETENCE", 0, MAP, 55);
        centered(g, "FRACTURE SECTION", MAP, MAP, 55);
        centered(g, "GROUNDWATER SECTION", 2 * MAP, MAP, 55);
        centered(g, "VOID SUITABILITY", 3 * MAP, MAP, 55);

        renderPlan(image, 0, descriptor, geology);
        renderSection(image, MAP, descriptor, geology, Field.FRACTURE);
        renderSection(image, 2 * MAP, descriptor, geology, Field.GROUNDWATER);
        renderSection(image, 3 * MAP, descriptor, geology, Field.VOID);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < 4; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void renderPlan(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology) {
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.03;
        for (int py = 0; py < MAP; py++) {
            double z = extent - 2.0 * extent * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandLocalPosition surface = new SkyIslandLocalPosition(x, z);
                if (semantic.interiority().sample(surface) <= 0.0) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                SkyIslandGeologySample sample =
                        geology.sample(new SkyIslandSubsurfacePosition(surface, 0.15));
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        competenceColor(sample.bulkCompetence()).getRGB());
            }
        }
    }

    private static void renderSection(
            BufferedImage image,
            int offsetX,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            Field field) {
        double radius = descriptor.nominalRadius();
        double extent = radius * 1.03;
        for (int py = 0; py < MAP; py++) {
            double depth = py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -extent + 2.0 * extent * px / (MAP - 1.0);
                SkyIslandGeologySample sample =
                        geology.sample(new SkyIslandSubsurfacePosition(x, 0.0, depth));
                if (!sample.owned()) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                double value = switch (field) {
                    case FRACTURE -> sample.fractureIntensity();
                    case GROUNDWATER -> sample.groundwaterPotential();
                    case VOID -> sample.voidFormationPotential();
                };
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        field.color(value).getRGB());
            }
        }
    }

    private static FieldStats measure(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology) {
        double radius = descriptor.nominalRadius();
        double sumCompetence = 0.0;
        double sumFracture = 0.0;
        double sumPermeability = 0.0;
        double sumGroundwater = 0.0;
        double sumVoid = 0.0;
        double maxVoid = 0.0;
        long count = 0L;

        for (int iz = 0; iz < 31; iz++) {
            double z = -radius + 2.0 * radius * iz / 30.0;
            for (int ix = 0; ix < 31; ix++) {
                double x = -radius + 2.0 * radius * ix / 30.0;
                for (int id = 0; id < 9; id++) {
                    double depth = id / 8.0;
                    SkyIslandGeologySample sample =
                            geology.sample(new SkyIslandSubsurfacePosition(x, z, depth));
                    if (!sample.owned()) {
                        continue;
                    }
                    sumCompetence += sample.bulkCompetence();
                    sumFracture += sample.fractureIntensity();
                    sumPermeability += sample.connectedPermeability();
                    sumGroundwater += sample.groundwaterPotential();
                    sumVoid += sample.voidFormationPotential();
                    maxVoid = Math.max(maxVoid, sample.voidFormationPotential());
                    count++;
                }
            }
        }
        if (count == 0L) {
            throw new IllegalStateException("geology specimen had no owned samples");
        }
        return new FieldStats(
                sumCompetence / count,
                sumFracture / count,
                sumPermeability / count,
                sumGroundwater / count,
                sumVoid / count,
                maxVoid);
    }

    private static double normalizedRelief(SkyIslandDescriptor descriptor) {
        return Math.min(1.0, Math.max(0.0, (descriptor.reliefBudget() - 24.0) / (192.0 - 24.0)));
    }

    private static Color competenceColor(double value) {
        return ramp(value, new Color(102, 82, 67), new Color(218, 211, 193));
    }

    private static Color ramp(double value, Color low, Color high) {
        double t = Math.max(0.0, Math.min(1.0, value));
        int red = (int) Math.round(low.getRed() + (high.getRed() - low.getRed()) * t);
        int green = (int) Math.round(low.getGreen() + (high.getGreen() - low.getGreen()) * t);
        int blue = (int) Math.round(low.getBlue() + (high.getBlue() - low.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private enum Field {
        FRACTURE {
            @Override
            Color color(double value) {
                return ramp(value, new Color(45, 54, 64), new Color(221, 126, 74));
            }
        },
        GROUNDWATER {
            @Override
            Color color(double value) {
                return ramp(value, new Color(90, 82, 65), new Color(37, 119, 177));
            }
        },
        VOID {
            @Override
            Color color(double value) {
                return ramp(value, new Color(239, 237, 229), new Color(76, 52, 96));
            }
        };

        abstract Color color(double value);
    }

    private record Candidate(long key, SkyIslandDescriptor descriptor) {}

    private record Selection(String role, Candidate candidate) {}

    private record FieldStats(
            double meanCompetence,
            double meanFracture,
            double meanPermeability,
            double meanGroundwater,
            double meanVoid,
            double maxVoid) {}
}
