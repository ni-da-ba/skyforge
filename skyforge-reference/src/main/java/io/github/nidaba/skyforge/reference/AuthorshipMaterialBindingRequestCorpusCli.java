package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingDomainKind;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0039 backend-neutral material-binding request evidence. */
public final class AuthorshipMaterialBindingRequestCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 72;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipMaterialBindingRequestCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-material-binding-request-v1");
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
                "role,islandKey,morphology,materialSamples,uniqueRequests,"
                        + "primaryRequests,secondaryRequests,alterationRequests,"
                        + "waterRequests,mineralRequests,contactRequestSamples,"
                        + "maxAssemblageContext,meanRequestsPerMaterial\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
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
                    .append(stats.uniqueRequests()).append(',')
                    .append(stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)).append(',')
                    .append(stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)).append(',')
                    .append(stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT)).append(',')
                    .append(stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING)).append(',')
                    .append(stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE)).append(',')
                    .append(stats.contactRequestSamples()).append(',')
                    .append(stats.maxAssemblageContext()).append(',')
                    .append(format(stats.meanRequestsPerMaterial())).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0039</title>"
                        + "<h1>Backend-neutral material binding request contract</h1>"
                        + "<p>All panels sample semantic depth 0.52. Color identifies the stable "
                        + "AUTH-0039 resolver request associated with the role. Repeated color means "
                        + "the backend should resolve one concrete material once and reuse it. "
                        + "Local AUTH-0037 support and expression ceilings remain separate from "
                        + "request identity. CONTACT shows contact-scoped resolver requests only.</p>"
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
                        "depth=%.2f material=%d unique requests=%d mean requests/material=%.2f",
                        DEPTH,
                        stats.materialSamples(),
                        stats.uniqueRequests(),
                        stats.meanRequestsPerMaterial()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "P/S/A/W/N=%d/%d/%d/%d/%d contact samples=%d max context=%d assemblages",
                        stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX),
                        stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX),
                        stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT),
                        stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING),
                        stats.roleRequests(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE),
                        stats.contactRequestSamples(),
                        stats.maxAssemblageContext()),
                7,
                50);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(graphics, "PRIMARY REQUEST", 0, MAP, 66);
        centered(graphics, "SECONDARY REQUEST", MAP, MAP, 66);
        centered(graphics, "ALTERATION REQUEST", 2 * MAP, MAP, 66);
        centered(graphics, "WATER REQUEST", 3 * MAP, MAP, 66);
        centered(graphics, "MINERAL REQUEST", 4 * MAP, MAP, 66);
        centered(graphics, "CONTACT REQUEST", 5 * MAP, MAP, 66);

        render(image, 0, samples, SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX, false);
        render(image, MAP, samples, SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX, false);
        render(image, 2 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT, false);
        render(image, 3 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING, false);
        render(image, 4 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE, false);
        render(image, 5 * MAP, samples, null, true);

        graphics.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            graphics.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        graphics.dispose();
        return image;
    }

    private static void render(
            BufferedImage image,
            int offsetX,
            SkyIslandMaterialBindingRequestSelection[][] samples,
            SkyIslandSemanticMaterialPaletteRole role,
            boolean contactOnly) {
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
                SkyIslandMaterialBindingRequestSelection sample = samples[sy][sx];
                SkyIslandMaterialBindingRequest request =
                        contactOnly ? firstContactRequest(sample) : roleRequest(sample, role);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        request == null
                                ? Color.WHITE.getRGB()
                                : requestColor(request).getRGB());
            }
        }
    }

    private static SkyIslandMaterialBindingRequest roleRequest(
            SkyIslandMaterialBindingRequestSelection selection,
            SkyIslandSemanticMaterialPaletteRole role) {
        return selection.use(role)
                .map(SkyIslandMaterialBindingRequestUse::request)
                .orElse(null);
    }

    private static SkyIslandMaterialBindingRequest firstContactRequest(
            SkyIslandMaterialBindingRequestSelection selection) {
        return selection.uses().stream()
                .map(SkyIslandMaterialBindingRequestUse::request)
                .filter(request -> request.domainKind()
                        == SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION)
                .findFirst()
                .orElse(null);
    }

    private static Color requestColor(SkyIslandMaterialBindingRequest request) {
        int hash = request.bindingKey().canonicalToken().hashCode();
        int red = 65 + Math.floorMod(hash, 155);
        int green = 65 + Math.floorMod(hash >>> 8, 155);
        int blue = 65 + Math.floorMod(hash >>> 16, 155);
        int context = Math.min(4, request.assemblages().size());
        double contextScale = 0.78 + 0.055 * context;
        return new Color(
                channel(red * contextScale),
                channel(green * contextScale),
                channel(blue * contextScale));
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static Stats stats(SkyIslandMaterialBindingRequestSelection[][] samples) {
        Set<String> all = new HashSet<>();
        Set<String> primary = new HashSet<>();
        Set<String> secondary = new HashSet<>();
        Set<String> alteration = new HashSet<>();
        Set<String> water = new HashSet<>();
        Set<String> mineral = new HashSet<>();
        int material = 0;
        int totalUses = 0;
        int contactSamples = 0;
        int maxContext = 0;

        for (SkyIslandMaterialBindingRequestSelection[] row : samples) {
            for (SkyIslandMaterialBindingRequestSelection selection : row) {
                if (!selection.materialPresent()) {
                    continue;
                }
                material++;
                totalUses += selection.uses().size();
                boolean contact = false;
                for (SkyIslandMaterialBindingRequestUse use : selection.uses()) {
                    SkyIslandMaterialBindingRequest request = use.request();
                    String token = request.bindingKey().canonicalToken();
                    all.add(token);
                    maxContext = Math.max(maxContext, request.assemblages().size());
                    roleSet(
                            request.role(),
                            primary,
                            secondary,
                            alteration,
                            water,
                            mineral)
                            .add(token);
                    contact |= request.domainKind()
                            == SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION;
                }
                if (contact) {
                    contactSamples++;
                }
            }
        }

        return new Stats(
                material,
                totalUses,
                contactSamples,
                maxContext,
                all,
                primary,
                secondary,
                alteration,
                water,
                mineral);
    }

    private static Set<String> roleSet(
            SkyIslandSemanticMaterialPaletteRole role,
            Set<String> primary,
            Set<String> secondary,
            Set<String> alteration,
            Set<String> water,
            Set<String> mineral) {
        return switch (role) {
            case PRIMARY_MATRIX -> primary;
            case SECONDARY_MATRIX -> secondary;
            case ALTERATION_OVERPRINT -> alteration;
            case HYDROLOGIC_CONDITIONING -> water;
            case MINERAL_BEARING_STRUCTURE -> mineral;
        };
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
            int totalUses,
            int contactRequestSamples,
            int maxAssemblageContext,
            Set<String> all,
            Set<String> primary,
            Set<String> secondary,
            Set<String> alteration,
            Set<String> water,
            Set<String> mineral) {

        int uniqueRequests() {
            return all.size();
        }

        int roleRequests(SkyIslandSemanticMaterialPaletteRole role) {
            return switch (role) {
                case PRIMARY_MATRIX -> primary.size();
                case SECONDARY_MATRIX -> secondary.size();
                case ALTERATION_OVERPRINT -> alteration.size();
                case HYDROLOGIC_CONDITIONING -> water.size();
                case MINERAL_BEARING_STRUCTURE -> mineral.size();
            };
        }

        double meanRequestsPerMaterial() {
            return materialSamples == 0 ? 0.0 : (double) totalUses / materialSamples;
        }
    }

    private record Selection(String role, long key) {}
}
