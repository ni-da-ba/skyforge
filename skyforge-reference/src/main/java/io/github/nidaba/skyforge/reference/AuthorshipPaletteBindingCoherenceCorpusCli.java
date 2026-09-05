package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingCandidate;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingDomainKind;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingKey;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingSelection;
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
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0038 palette-binding coherence evidence. */
public final class AuthorshipPaletteBindingCoherenceCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final double DEPTH = 0.52;
    private static final int MAP = 126;
    private static final int HEADER = 70;
    private static final int PANELS = 6;
    private static final int SAMPLE = 28;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private AuthorshipPaletteBindingCoherenceCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", "authorship-palette-binding-coherence-v1");
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
                "role,islandKey,morphology,materialSamples,primaryKeys,secondaryKeys,"
                        + "alterationKeys,waterKeys,mineralKeys,contactFallbackSamples,"
                        + "plannedDomains,conditionedCrossAssemblageDomains\n");

        for (int n = 0; n < selections.size(); n++) {
            Selection selection = selections.get(n);
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, selection.key()));
            SkyIslandSemanticPaletteBindingField field =
                    SkyIslandSemanticPaletteBindingField.create(descriptor);
            SkyIslandSemanticPaletteBindingSelection[][] samples =
                    sample(descriptor, field);
            Stats stats = stats(samples);
            BufferedImage specimen =
                    renderSpecimen(selection.role(), descriptor, samples, field, stats);

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
                    .append(stats.materialSamples()).append(',')
                    .append(stats.keyCount(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)).append(',')
                    .append(stats.keyCount(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)).append(',')
                    .append(stats.keyCount(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT)).append(',')
                    .append(stats.keyCount(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING)).append(',')
                    .append(stats.keyCount(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE)).append(',')
                    .append(stats.contactFallbackSamples()).append(',')
                    .append(field.plan().domains().size()).append(',')
                    .append(field.plan().conditionedDomainsCrossingAssemblages()).append('\n');
        }
        ag.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0038</title>"
                        + "<h1>Palette-binding coherence domains and stable keys</h1>"
                        + "<p>All maps sample semantic depth 0.52. Color identifies a stable "
                        + "AUTH-0038 binding key, not a concrete material. Repeated color inside a "
                        + "panel means one backend binding decision should be reused there. CONTACT "
                        + "shows only contact-transition fallback keys. White means the role is "
                        + "ineligible or material is absent.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static SkyIslandSemanticPaletteBindingSelection[][] sample(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticPaletteBindingField field) {
        double radius = descriptor.nominalRadius();
        SkyIslandSemanticPaletteBindingSelection[][] result =
                new SkyIslandSemanticPaletteBindingSelection[SAMPLE][SAMPLE];
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
            SkyIslandSemanticPaletteBindingSelection[][] samples,
            SkyIslandSemanticPaletteBindingField field,
            Stats stats) {
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
                        "depth=%.2f material=%d planned domains=%d cross-unit conditioned=%d",
                        DEPTH,
                        stats.materialSamples(),
                        field.plan().domains().size(),
                        field.plan().conditionedDomainsCrossingAssemblages()),
                7,
                35);
        g.drawString(
                String.format(
                        Locale.ROOT,
                        "keys P/S/A/W/N=%d/%d/%d/%d/%d contact fallback samples=%d",
                        stats.keyCount(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX),
                        stats.keyCount(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX),
                        stats.keyCount(SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT),
                        stats.keyCount(SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING),
                        stats.keyCount(SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE),
                        stats.contactFallbackSamples()),
                7,
                49);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        centered(g, "PRIMARY KEY", 0, MAP, 64);
        centered(g, "SECONDARY KEY", MAP, MAP, 64);
        centered(g, "ALTERATION KEY", 2 * MAP, MAP, 64);
        centered(g, "WATER KEY", 3 * MAP, MAP, 64);
        centered(g, "MINERAL KEY", 4 * MAP, MAP, 64);
        centered(g, "CONTACT KEY", 5 * MAP, MAP, 64);

        render(image, 0, samples, SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX, false);
        render(image, MAP, samples, SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX, false);
        render(image, 2 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.ALTERATION_OVERPRINT, false);
        render(image, 3 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.HYDROLOGIC_CONDITIONING, false);
        render(image, 4 * MAP, samples, SkyIslandSemanticMaterialPaletteRole.MINERAL_BEARING_STRUCTURE, false);
        render(image, 5 * MAP, samples, null, true);

        g.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            g.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        g.dispose();
        return image;
    }

    private static void render(
            BufferedImage image,
            int offsetX,
            SkyIslandSemanticPaletteBindingSelection[][] samples,
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
                SkyIslandSemanticPaletteBindingSelection sample = samples[sy][sx];
                SkyIslandSemanticPaletteBindingKey key =
                        contactOnly ? firstContactKey(sample) : roleKey(sample, role);
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        key == null ? Color.WHITE.getRGB() : keyColor(key).getRGB());
            }
        }
    }

    private static SkyIslandSemanticPaletteBindingKey roleKey(
            SkyIslandSemanticPaletteBindingSelection selection,
            SkyIslandSemanticMaterialPaletteRole role) {
        return selection.binding(role)
                .map(SkyIslandSemanticPaletteBindingCandidate::bindingKey)
                .orElse(null);
    }

    private static SkyIslandSemanticPaletteBindingKey firstContactKey(
            SkyIslandSemanticPaletteBindingSelection selection) {
        return selection.bindings().stream()
                .map(SkyIslandSemanticPaletteBindingCandidate::bindingKey)
                .filter(key -> key.domainKind()
                        == SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION)
                .findFirst()
                .orElse(null);
    }

    private static Color keyColor(SkyIslandSemanticPaletteBindingKey key) {
        int hash = key.canonicalToken().hashCode();
        int red = 70 + Math.floorMod(hash, 150);
        int green = 70 + Math.floorMod(hash >>> 8, 150);
        int blue = 70 + Math.floorMod(hash >>> 16, 150);
        return new Color(red, green, blue);
    }

    private static Stats stats(SkyIslandSemanticPaletteBindingSelection[][] samples) {
        List<Set<String>> keys =
                new ArrayList<>(SkyIslandSemanticMaterialPaletteRole.values().length);
        for (int i = 0; i < SkyIslandSemanticMaterialPaletteRole.values().length; i++) {
            keys.add(new HashSet<>());
        }
        int material = 0;
        int contactFallback = 0;
        for (SkyIslandSemanticPaletteBindingSelection[] row : samples) {
            for (SkyIslandSemanticPaletteBindingSelection selection : row) {
                if (!selection.materialPresent()) {
                    continue;
                }
                material++;
                boolean fallback = false;
                for (SkyIslandSemanticPaletteBindingCandidate binding :
                        selection.bindings()) {
                    keys.get(binding.candidate().role().ordinal())
                            .add(binding.bindingKey().canonicalToken());
                    fallback |= binding.bindingKey().domainKind()
                            == SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION;
                }
                if (fallback) {
                    contactFallback++;
                }
            }
        }
        return new Stats(material, contactFallback, keys);
    }

    private static void centered(Graphics2D g, String label, int x, int width, int y) {
        int textWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y);
    }

    private record Stats(
            int materialSamples,
            int contactFallbackSamples,
            List<Set<String>> keys) {
        int keyCount(SkyIslandSemanticMaterialPaletteRole role) {
            return keys.get(role.ordinal()).size();
        }
    }

    private record Selection(String role, long key) {}
}
