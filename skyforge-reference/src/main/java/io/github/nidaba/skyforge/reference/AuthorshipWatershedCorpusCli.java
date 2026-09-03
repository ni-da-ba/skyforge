package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandWatershedCell;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlan;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner;
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

/** Generates the deterministic AUTH-0005 watershed/flow-accumulation atlas. */
public final class AuthorshipWatershedCorpusCli {
    private static final int MAP = 320;
    private static final int LABEL = 62;
    private static final long SEED = 0x534B59464F524745L;

    private AuthorshipWatershedCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1 ? Path.of(args[0]) : Path.of("build", "evidence", "authorship-watersheds-v1");
        Files.createDirectories(out);
        List<Candidate> candidates = new ArrayList<>();
        for (long key = 0; key < 4096; key++) candidates.add(new Candidate(key,
                SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 5L, 51L, key))));
        Set<Long> used = new HashSet<>();
        List<Selection> selections = List.of(
                select("hydrological", candidates, used, SkyIslandDescriptor::hydrologicalPotential),
                select("wet", candidates, used, SkyIslandDescriptor::moistureTendency),
                select("basin", candidates, used, d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.BASIN ? 2 : 0) + d.hydrologicalPotential()),
                select("spine", candidates, used, d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.SPINE ? 2 : 0) + d.reliefBudget()),
                select("lobed", candidates, used, d -> (d.morphologyFamily() == SkyIslandMorphologyFamily.LOBED ? 2 : 0) + d.hydrologicalPotential()),
                select("large", candidates, used, SkyIslandDescriptor::nominalRadius));
        BufferedImage atlas = new BufferedImage(3 * MAP, 2 * (MAP + LABEL), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,atlas.getWidth(),atlas.getHeight());
        StringBuilder manifest = new StringBuilder("role,islandKey,morphology,radius,hydrology,moisture,outlets,retainedSinks,maxAccumulation\n");
        for (int n = 0; n < selections.size(); n++) {
            Selection s = selections.get(n); SkyIslandDescriptor d = s.candidate().descriptor(); SkyIslandWatershedPlan p = SkyIslandWatershedPlanner.plan(d);
            BufferedImage image = render(p); ImageIO.write(image,"png",out.resolve(s.role()+".png").toFile());
            int x=(n%3)*MAP, y=(n/3)*(MAP+LABEL); g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,17));
            g.drawString(s.role()+" / "+d.morphologyFamily().identifier(),x+8,y+21); g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));
            g.drawString("key="+s.candidate().key()+" outlets="+p.outletCount()+" sinks="+p.retainedSinkCount(),x+8,y+43); g.drawImage(image,x,y+LABEL,null);
            manifest.append(s.role()).append(',').append(s.candidate().key()).append(',').append(d.morphologyFamily().identifier()).append(',').append(d.nominalRadius()).append(',')
                    .append(d.hydrologicalPotential()).append(',').append(d.moistureTendency()).append(',').append(p.outletCount()).append(',').append(p.retainedSinkCount()).append(',').append(p.maxFlowAccumulation()).append('\n');
        }
        g.dispose(); ImageIO.write(atlas,"png",out.resolve("atlas.png").toFile()); Files.writeString(out.resolve("manifest.csv"),manifest.toString(),StandardCharsets.UTF_8);
        Files.writeString(out.resolve("index.html"),"<!doctype html><meta charset=\"utf-8\"><title>AUTH-0005 watersheds</title><h1>AUTH-0005 watershed atlas</h1><p>Dark terrain cells carry local runoff; brighter cyan-to-white cells have greater accumulated upstream flow. Blue circles are retained sinks; orange circles are edge outlets.</p><img src=\"atlas.png\" style=\"max-width:100%\"><p><a href=\"manifest.csv\">manifest.csv</a></p>",StandardCharsets.UTF_8);
    }

    private static BufferedImage render(SkyIslandWatershedPlan plan) {
        BufferedImage im=new BufferedImage(MAP,MAP,BufferedImage.TYPE_INT_RGB); Graphics2D g=im.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,MAP,MAP);
        double r=plan.descriptor().nominalRadius(); double max=Math.max(1e-9,plan.maxFlowAccumulation());
        for (SkyIslandWatershedCell c: plan.cells()) {
            int px=(int)Math.round((c.position().x()/r+1)*0.5*(MAP-1)); int py=(int)Math.round((1-(c.position().z()/r+1)*0.5)*(MAP-1));
            double q=Math.log1p(c.flowAccumulation())/Math.log1p(max); int v=(int)Math.round(45+210*q); g.setColor(new Color(Math.min(235,(int)(35+100*q)),Math.min(250,(int)(65+185*q)),Math.min(255,v)));
            int cell=Math.max(3,(int)Math.ceil(MAP/(double)plan.gridSize())+1); g.fillRect(px-cell/2,py-cell/2,cell,cell);
        }
        for (SkyIslandWatershedCell c:plan.cells()) if(c.retainedSink()||c.edgeOutlet()) { int px=(int)Math.round((c.position().x()/r+1)*0.5*(MAP-1)); int py=(int)Math.round((1-(c.position().z()/r+1)*0.5)*(MAP-1)); g.setColor(c.edgeOutlet()?new Color(220,110,35):new Color(25,70,190)); g.drawOval(px-4,py-4,8,8); }
        g.dispose(); return im;
    }

    private static Selection select(String role,List<Candidate> cs,Set<Long> used,ToDoubleFunction<SkyIslandDescriptor> score){Candidate best=null;double bs=Double.NEGATIVE_INFINITY;for(Candidate c:cs)if(!used.contains(c.key())&&score.applyAsDouble(c.descriptor())>bs){best=c;bs=score.applyAsDouble(c.descriptor());}used.add(best.key());return new Selection(role,best);}    
    private record Candidate(long key,SkyIslandDescriptor descriptor){} private record Selection(String role,Candidate candidate){}
}
