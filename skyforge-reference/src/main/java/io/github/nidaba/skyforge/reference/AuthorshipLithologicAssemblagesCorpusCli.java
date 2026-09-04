package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLithologicAssemblage;
import io.github.nidaba.skyforge.world.SkyIslandLithologicAssemblageCell;
import io.github.nidaba.skyforge.world.SkyIslandLithologicAssemblageKind;
import io.github.nidaba.skyforge.world.SkyIslandLithologicAssemblagePlan;
import io.github.nidaba.skyforge.world.SkyIslandLithologicAssemblagePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContact;
import io.github.nidaba.skyforge.world.SkyIslandLithologicContactKind;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyKind;
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

/** Generates deterministic AUTH-0034 coherent lithologic assemblage/contact evidence. */
public final class AuthorshipLithologicAssemblagesCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 132;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipLithologicAssemblagesCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-lithologic-assemblages-v1");
        Files.createDirectories(out);

        List<Selection> selections = List.of(
                new Selection("competent", 2332L),
                new Selection("weak", 653L),
                new Selection("permeable", 1051L),
                new Selection("hydrologic", 2211L),
                new Selection("eroded", 1439L),
                new Selection("spine", 3670L));

        BufferedImage atlas =
                new BufferedImage(2 * SPECIMEN_WIDTH, 3 * SPECIMEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setColor(Color.WHITE);
        ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest = new StringBuilder(
                "role,islandKey,morphology,activeHostCells,assemblages,contacts,smallestUnit,largestUnit,"
                        + "massiveUnits,fabricUnits,alteredUnits,waterUnits,mineralUnits,"
                        + "gradationalContacts,fabricContacts,alterationFronts,hydrologicFronts,mineralizationFronts\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandLithologicAssemblagePlan plan =
                    SkyIslandLithologicAssemblagePlanner.plan(descriptor);
            BufferedImage specimen = renderSpecimen(selection.role(), descriptor, plan);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(selection.role() + "-" + selection.key() + ".png").toFile());
            ag.drawImage(
                    specimen,
                    (n % 2) * SPECIMEN_WIDTH,
                    (n / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(selection.role()).append(',')
                    .append(selection.key()).append(',')
                    .append(descriptor.morphologyFamily().identifier()).append(',')
                    .append(plan.activeHostCells()).append(',')
                    .append(plan.assemblages().size()).append(',')
                    .append(plan.contacts().size()).append(',')
                    .append(plan.smallestAssemblageCellCount()).append(',')
                    .append(plan.largestAssemblageCellCount()).append(',')
                    .append(plan.assemblageCount(SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT)).append(',')
                    .append(plan.assemblageCount(SkyIslandLithologicAssemblageKind.FABRIC_RICH_HOST_UNIT)).append(',')
                    .append(plan.assemblageCount(SkyIslandLithologicAssemblageKind.ALTERED_HOST_UNIT)).append(',')
                    .append(plan.assemblageCount(SkyIslandLithologicAssemblageKind.WATER_CONDITIONED_HOST_UNIT)).append(',')
                    .append(plan.assemblageCount(SkyIslandLithologicAssemblageKind.MINERAL_BEARING_STRUCTURAL_UNIT)).append(',')
                    .append(plan.contactCount(SkyIslandLithologicContactKind.GRADATIONAL_CONTACT)).append(',')
                    .append(plan.contactCount(SkyIslandLithologicContactKind.HOST_FABRIC_CONTACT)).append(',')
                    .append(plan.contactCount(SkyIslandLithologicContactKind.ALTERATION_FRONT)).append(',')
                    .append(plan.contactCount(SkyIslandLithologicContactKind.HYDROLOGIC_FRONT)).append(',')
                    .append(plan.contactCount(SkyIslandLithologicContactKind.MINERALIZATION_FRONT)).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0034</title>"
                        + "<h1>Coherent lithologic assemblages and contacts</h1>"
                        + "<p>PLAN and SECTION show backend-neutral authored unit interpretation. "
                        + "PLAN CONTACTS projects lateral x/z contact traces; SECTION UNITS retains vertical "
                        + "structure. The final three "
                        + "panels retain the underlying AUTH-0033 altered, water-conditioned, and "
                        + "mineral-bearing affinities so unit boundaries can be reviewed against "
                        + "their semantic causes.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static BufferedImage renderSpecimen(
            String role,
            SkyIslandDescriptor descriptor,
            SkyIslandLithologicAssemblagePlan plan) {
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
                        "active=%d units=%d contacts=%d size=%d..%d",
                        plan.activeHostCells(),
                        plan.assemblages().size(),
                        plan.contacts().size(),
                        plan.smallestAssemblageCellCount(),
                        plan.largestAssemblageCellCount()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "units M=%d F=%d A=%d W=%d N=%d",
                        plan.assemblageCount(SkyIslandLithologicAssemblageKind.MASSIVE_HOST_UNIT),
                        plan.assemblageCount(SkyIslandLithologicAssemblageKind.FABRIC_RICH_HOST_UNIT),
                        plan.assemblageCount(SkyIslandLithologicAssemblageKind.ALTERED_HOST_UNIT),
                        plan.assemblageCount(SkyIslandLithologicAssemblageKind.WATER_CONDITIONED_HOST_UNIT),
                        plan.assemblageCount(SkyIslandLithologicAssemblageKind.MINERAL_BEARING_STRUCTURAL_UNIT)),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "PLAN UNITS", 0, MAP, 66);
        centered(g, "SECTION UNITS", MAP, MAP, 66);
        centered(g, "PLAN CONTACTS", 2 * MAP, MAP, 66);
        centered(g, "ALTERATION", 3 * MAP, MAP, 66);
        centered(g, "WATER", 4 * MAP, MAP, 66);
        centered(g, "MINERAL", 5 * MAP, MAP, 66);

        renderPlanUnits(image, 0, plan);
        renderSectionUnits(image, MAP, plan);
        renderContacts(image, 2 * MAP, plan);
        renderFamily(
                image,
                3 * MAP,
                plan,
                SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST,
                new Color(174, 96, 57));
        renderFamily(
                image,
                4 * MAP,
                plan,
                SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST,
                new Color(52, 105, 164));
        renderFamily(
                image,
                5 * MAP,
                plan,
                SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST,
                new Color(137, 87, 151));

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void renderPlanUnits(
            BufferedImage image, int offsetX, SkyIslandLithologicAssemblagePlan plan) {
        int grid = plan.gridSize();
        double[][] best = new double[grid][grid];
        SkyIslandLithologicAssemblageKind[][] kinds =
                new SkyIslandLithologicAssemblageKind[grid][grid];

        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            double support = SkyIslandLithologicAssemblagePlanner.semanticSupport(
                    cell.familyCharacter(), cell.assemblageKind());
            if (support > best[cell.zIndex()][cell.xIndex()]) {
                best[cell.zIndex()][cell.xIndex()] = support;
                kinds[cell.zIndex()][cell.xIndex()] = cell.assemblageKind();
            }
        }
        renderCategorical(image, offsetX, kinds, best);
    }

    private static void renderSectionUnits(
            BufferedImage image, int offsetX, SkyIslandLithologicAssemblagePlan plan) {
        int grid = plan.gridSize();
        int depth = plan.depthSamples();
        int middleZ = grid / 2;
        double[][] strength = new double[depth][grid];
        SkyIslandLithologicAssemblageKind[][] kinds =
                new SkyIslandLithologicAssemblageKind[depth][grid];
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            if (cell.zIndex() != middleZ) {
                continue;
            }
            strength[cell.depthIndex()][cell.xIndex()] =
                    SkyIslandLithologicAssemblagePlanner.semanticSupport(
                            cell.familyCharacter(), cell.assemblageKind());
            kinds[cell.depthIndex()][cell.xIndex()] = cell.assemblageKind();
        }
        renderCategorical(image, offsetX, kinds, strength);
    }

    private static void renderContacts(
            BufferedImage image, int offsetX, SkyIslandLithologicAssemblagePlan plan) {
        int grid = plan.gridSize();
        SkyIslandLithologicContactKind[][] kinds =
                new SkyIslandLithologicContactKind[grid][grid];
        Map<Integer, SkyIslandLithologicAssemblageCell> cells = new HashMap<>();
        Map<Long, SkyIslandLithologicContactKind> contacts = new HashMap<>();
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            cells.put(cell.index(), cell);
        }
        for (SkyIslandLithologicContact contact : plan.contacts()) {
            contacts.put(pair(contact.firstAssemblageId(), contact.secondAssemblageId()), contact.kind());
        }

        int depthSamples = plan.depthSamples();
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            int[][] offsets = {{1, 0, 0}, {0, 0, 1}};
            for (int[] offset : offsets) {
                int x = cell.xIndex() + offset[0];
                int d = cell.depthIndex() + offset[1];
                int z = cell.zIndex() + offset[2];
                if (x < 0 || d < 0 || z < 0 || x >= grid || d >= depthSamples || z >= grid) {
                    continue;
                }
                int neighborIndex = (z * depthSamples + d) * grid + x;
                SkyIslandLithologicAssemblageCell neighbor = cells.get(neighborIndex);
                if (neighbor == null || neighbor.assemblageId() == cell.assemblageId()) {
                    continue;
                }
                SkyIslandLithologicContactKind kind =
                        contacts.get(pair(cell.assemblageId(), neighbor.assemblageId()));
                kinds[cell.zIndex()][cell.xIndex()] = stronger(
                        kinds[cell.zIndex()][cell.xIndex()], kind);
                kinds[neighbor.zIndex()][neighbor.xIndex()] = stronger(
                        kinds[neighbor.zIndex()][neighbor.xIndex()], kind);
            }
        }

        for (int py = 0; py < MAP; py++) {
            int gy = Math.min(grid - 1, (int) Math.round(py * (grid - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int gx = Math.min(grid - 1, (int) Math.round(px * (grid - 1.0) / (MAP - 1.0)));
                SkyIslandLithologicContactKind kind = kinds[gy][gx];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        kind == null ? Color.WHITE.getRGB() : contactColor(kind).getRGB());
            }
        }
    }

    private static SkyIslandLithologicContactKind stronger(
            SkyIslandLithologicContactKind first,
            SkyIslandLithologicContactKind second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return contactPriority(second) > contactPriority(first) ? second : first;
    }

    private static int contactPriority(SkyIslandLithologicContactKind kind) {
        return switch (kind) {
            case GRADATIONAL_CONTACT -> 0;
            case HOST_FABRIC_CONTACT -> 1;
            case HYDROLOGIC_FRONT -> 2;
            case ALTERATION_FRONT -> 3;
            case MINERALIZATION_FRONT -> 4;
        };
    }

    private static void renderFamily(
            BufferedImage image,
            int offsetX,
            SkyIslandLithologicAssemblagePlan plan,
            SkyIslandMaterialFamilyKind family,
            Color high) {
        int grid = plan.gridSize();
        double[][] values = new double[grid][grid];
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            values[cell.zIndex()][cell.xIndex()] =
                    Math.max(
                            values[cell.zIndex()][cell.xIndex()],
                            cell.familyCharacter().membership(family));
        }
        for (int py = 0; py < MAP; py++) {
            for (int px = 0; px < MAP; px++) {
                double value = interpolated(values, px, py);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        value <= 0.0
                                ? Color.WHITE.getRGB()
                                : blend(Color.WHITE, high, 0.18 + 0.82 * value).getRGB());
            }
        }
    }

    private static void renderCategorical(
            BufferedImage image,
            int offsetX,
            SkyIslandLithologicAssemblageKind[][] kinds,
            double[][] strength) {
        int height = kinds.length;
        int width = kinds[0].length;
        for (int py = 0; py < MAP; py++) {
            int gy = Math.min(height - 1, (int) Math.round(py * (height - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int gx = Math.min(width - 1, (int) Math.round(px * (width - 1.0) / (MAP - 1.0)));
                SkyIslandLithologicAssemblageKind kind = kinds[gy][gx];
                if (kind == null) {
                    image.setRGB(offsetX + px, HEADER + py, Color.WHITE.getRGB());
                    continue;
                }
                double value = strength[gy][gx];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        blend(Color.WHITE, unitColor(kind), 0.42 + 0.50 * value).getRGB());
            }
        }
    }

    private static Color unitColor(SkyIslandLithologicAssemblageKind kind) {
        return switch (kind) {
            case MASSIVE_HOST_UNIT -> new Color(119, 104, 91);
            case FABRIC_RICH_HOST_UNIT -> new Color(75, 126, 91);
            case ALTERED_HOST_UNIT -> new Color(174, 96, 57);
            case WATER_CONDITIONED_HOST_UNIT -> new Color(52, 105, 164);
            case MINERAL_BEARING_STRUCTURAL_UNIT -> new Color(137, 87, 151);
        };
    }

    private static Color contactColor(SkyIslandLithologicContactKind kind) {
        return switch (kind) {
            case GRADATIONAL_CONTACT -> new Color(145, 145, 145);
            case HOST_FABRIC_CONTACT -> new Color(49, 49, 49);
            case ALTERATION_FRONT -> new Color(186, 88, 45);
            case HYDROLOGIC_FRONT -> new Color(42, 106, 184);
            case MINERALIZATION_FRONT -> new Color(126, 69, 148);
        };
    }

    private static double interpolated(double[][] values, int px, int py) {
        int height = values.length;
        int width = values[0].length;
        double gy = py * (height - 1.0) / (MAP - 1.0);
        double gx = px * (width - 1.0) / (MAP - 1.0);
        int y0 = Math.min(height - 1, (int) Math.floor(gy));
        int y1 = Math.min(height - 1, y0 + 1);
        int x0 = Math.min(width - 1, (int) Math.floor(gx));
        int x1 = Math.min(width - 1, x0 + 1);
        double tx = gx - x0;
        double ty = gy - y0;
        double top = values[y0][x0] + (values[y0][x1] - values[y0][x0]) * tx;
        double bottom = values[y1][x0] + (values[y1][x1] - values[y1][x0]) * tx;
        return top + (bottom - top) * ty;
    }

    private static Color blend(Color low, Color high, double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return new Color(
                channel(low.getRed() + (high.getRed() - low.getRed()) * t),
                channel(low.getGreen() + (high.getGreen() - low.getGreen()) * t),
                channel(low.getBlue() + (high.getBlue() - low.getBlue()) * t));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static long pair(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Selection(String role, long key) {}
}
