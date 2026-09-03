package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeature;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeatureKind;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlanner;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0006 feature-extraction evidence. */
public final class AuthorshipHydrologicFeaturesCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private AuthorshipHydrologicFeaturesCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1 ? Path.of(args[0]) : Path.of("build", "evidence", "authorship-hydrologic-features-v1");
        Files.createDirectories(out);
        List<Long> keys = selectedKeys();
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 54), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics(); ag.setColor(Color.WHITE); ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        StringBuilder manifest = new StringBuilder("islandKey,morphology,channels,retainedWater,waterfalls\n");
        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor d = descriptor(key);
            SkyIslandHydrologicFeaturePlan p = SkyIslandHydrologicFeaturePlanner.plan(d);
            BufferedImage image = render(d, p); ImageIO.write(image, "png", out.resolve("island-" + key + ".png").toFile());
            int x = (n % 3) * MAP, y = (n / 3) * (MAP + 54);
            ag.setColor(Color.BLACK); ag.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); ag.drawString("key=" + key + " / " + d.morphologyFamily().identifier(), x + 8, y + 20);
            ag.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); ag.drawString("channels=" + p.count(SkyIslandHydrologicFeatureKind.CHANNEL) + " water=" + p.count(SkyIslandHydrologicFeatureKind.RETAINED_WATER) + " falls=" + p.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL), x + 8, y + 40);
            ag.drawImage(image, x, y + 54, null);
            manifest.append(key).append(',').append(d.morphologyFamily().identifier()).append(',').append(p.count(SkyIslandHydrologicFeatureKind.CHANNEL)).append(',').append(p.count(SkyIslandHydrologicFeatureKind.RETAINED_WATER)).append(',').append(p.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL)).append('\n');
        }
        ag.dispose(); ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile()); Files.writeString(out.resolve("manifest.csv"), manifest.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("index.html"), "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0006</title><h1>Hydrologic feature candidates</h1><p>Cyan lines: channel corridors. Blue circles: retained water. Orange circles: waterfall/outlet anchors.</p><img src=\"atlas.png\" style=\"max-width:100%\"><p><a href=\"manifest.csv\">manifest.csv</a></p>", StandardCharsets.UTF_8);
    }

    private static List<Long> selectedKeys() {
        List<Long> selected = new ArrayList<>(List.of(77L, 118L, 241L, 512L, 811L));
        long retainedKey = -1L;
        for (long key = 0; key < 4096; key++) {
            SkyIslandHydrologicFeaturePlan plan = SkyIslandHydrologicFeaturePlanner.plan(descriptor(key));
            if (plan.count(SkyIslandHydrologicFeatureKind.RETAINED_WATER) > 0 && !selected.contains(key)) {
                retainedKey = key;
                break;
            }
        }
        if (retainedKey < 0) throw new IllegalStateException("no retained-water evidence case found");
        selected.add(retainedKey);
        return List.copyOf(selected);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static BufferedImage render(SkyIslandDescriptor d, SkyIslandHydrologicFeaturePlan p) {
        BufferedImage im = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, MAP, MAP);
        double r = d.nominalRadius();
        Map<Integer, SkyIslandHydrologicFeature> channels = new HashMap<>();
        for (SkyIslandHydrologicFeature f : p.features()) if (f.kind() == SkyIslandHydrologicFeatureKind.CHANNEL) channels.put(f.sourceCellIndex(), f);
        p.features().stream().filter(f -> f.kind() == SkyIslandHydrologicFeatureKind.CHANNEL).sorted(Comparator.comparingDouble(SkyIslandHydrologicFeature::significance)).forEach(f -> {
            SkyIslandHydrologicFeature downstream = channels.get(f.downstreamCellIndex());
            if (downstream == null) return;
            int x1 = pixelX(f.position().x(), r), y1 = pixelY(f.position().z(), r);
            int x2 = pixelX(downstream.position().x(), r), y2 = pixelY(downstream.position().z(), r);
            g.setColor(new Color(30, 150, 205));
            g.setStroke(new BasicStroke(f.significance() > 0.7 ? 3.0f : f.significance() > 0.4 ? 2.0f : 1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
        });
        for (SkyIslandHydrologicFeature f : p.features()) {
            if (f.kind() == SkyIslandHydrologicFeatureKind.CHANNEL) continue;
            int x = pixelX(f.position().x(), r), y = pixelY(f.position().z(), r);
            if (f.kind() == SkyIslandHydrologicFeatureKind.RETAINED_WATER) { g.setColor(new Color(30,70,190)); g.fillOval(x-6,y-6,12,12); }
            else { g.setColor(new Color(220,110,35)); g.fillOval(x-6,y-6,12,12); }
        }
        g.dispose(); return im;
    }

    private static int pixelX(double x, double radius) { return (int)Math.round((x / radius + 1) * 0.5 * (MAP - 1)); }
    private static int pixelY(double z, double radius) { return (int)Math.round((1 - (z / radius + 1) * 0.5) * (MAP - 1)); }
}
