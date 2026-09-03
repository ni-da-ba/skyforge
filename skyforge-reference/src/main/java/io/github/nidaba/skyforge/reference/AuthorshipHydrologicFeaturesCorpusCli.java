package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeature;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeatureKind;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlan;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicFeaturePlanner;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0006 feature-extraction evidence. */
public final class AuthorshipHydrologicFeaturesCorpusCli {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAP = 320;
    private AuthorshipHydrologicFeaturesCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1 ? Path.of(args[0]) : Path.of("build", "evidence", "authorship-hydrologic-features-v1");
        Files.createDirectories(out);
        List<Long> keys = List.of(77L, 118L, 241L, 512L, 811L, 1003L);
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + 54), BufferedImage.TYPE_INT_RGB);
        Graphics2D ag = atlas.createGraphics(); ag.setColor(Color.WHITE); ag.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        StringBuilder manifest = new StringBuilder("islandKey,morphology,channels,retainedWater,waterfalls\n");
        for (int n = 0; n < keys.size(); n++) {
            long key = keys.get(n);
            SkyIslandDescriptor d = SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
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

    private static BufferedImage render(SkyIslandDescriptor d, SkyIslandHydrologicFeaturePlan p) {
        BufferedImage im = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_RGB); Graphics2D g = im.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,MAP,MAP);
        double r = d.nominalRadius();
        for (SkyIslandHydrologicFeature f : p.features()) {
            int x = (int)Math.round((f.position().x()/r + 1) * 0.5 * (MAP-1)); int y = (int)Math.round((1 - (f.position().z()/r + 1) * 0.5) * (MAP-1));
            if (f.kind() == SkyIslandHydrologicFeatureKind.CHANNEL) { g.setColor(new Color(30, 150, 205)); int s = f.significance() > 0.7 ? 5 : 3; g.fillOval(x-s/2,y-s/2,s,s); }
            else if (f.kind() == SkyIslandHydrologicFeatureKind.RETAINED_WATER) { g.setColor(new Color(30,70,190)); g.fillOval(x-5,y-5,10,10); }
            else { g.setColor(new Color(220,110,35)); g.fillOval(x-5,y-5,10,10); }
        }
        g.dispose(); return im;
    }
}
