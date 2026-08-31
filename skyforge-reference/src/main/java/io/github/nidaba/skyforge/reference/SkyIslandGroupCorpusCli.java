package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandGroupEvidenceWriter;
import io.github.nidaba.skyforge.reference.volume.SkyIslandGroupReferenceCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the first mixed-provider chain and cluster group-scale evidence atlas. */
public final class SkyIslandGroupCorpusCli {
    public static final String EVIDENCE_ID = "multi-island-group-v1";

    private SkyIslandGroupCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("usage: SkyIslandGroupCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);
        String version = System.getProperty("skyforge.version", "development");

        SkyIslandGroupEvidence chain = generate(SkyIslandGroupReferenceCorpus.chain(
                SkyIslandGroupReferenceCorpus.SKYFORGE_SEED));
        SkyIslandGroupEvidence cluster = generate(SkyIslandGroupReferenceCorpus.cluster(
                SkyIslandGroupReferenceCorpus.SKYFORGE_SEED));
        SkyIslandGroupEvidenceWriter writer = new SkyIslandGroupEvidenceWriter();
        writer.write(chain, output.resolve("chain"), version);
        writer.write(cluster, output.resolve("cluster"), version);
        Files.writeString(output.resolve("index.html"), galleryHtml(), StandardCharsets.UTF_8);

        print("chain", chain);
        print("cluster", cluster);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandGroupEvidence generate(SkyIslandGroupRequest request) {
        SkyIslandGroupPlan plan = new SkyIslandGroupPlanner().plan(request);
        var registry = SkyIslandGroupReferenceCorpus.registry();
        var compiled = new SkyIslandMorphologySpecCompiler().compile(plan, registry);
        return new SkyIslandGroupEvidenceGenerator().generate(plan, compiled);
    }

    private static void print(String id, SkyIslandGroupEvidence evidence) {
        var m = evidence.metrics();
        System.out.printf(
                "%s: members=%d; solid=%d; components=%d; overlaps=%d; faceContacts=%d; minCenterSpacing=%.3f; minReservedGap=%.3f%n",
                id,
                m.memberCount(),
                m.solidSampleCount(),
                m.connectedComponents(),
                m.overlappingSolidSamples(),
                m.faceContacts(),
                m.minimumObservedCenterSpacing(),
                m.minimumReservedGap());
    }

    private static String galleryHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>Skyforge multi-island group atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1800px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}
                figure{margin:0} img{width:100%%;border:1px solid #ccc;background:#fafafa} figcaption{font-weight:650;margin:.35rem 0}
                @media(max-width:1100px){.grid{grid-template-columns:1fr}}
                </style></head><body>
                <h1>Skyforge first multi-island group realization</h1>
                <p>SF-IMP-0026 group-scale evidence. Each group mixes built-in providers, provider blends, and the external reference:crescent provider. Plan images show reservation intent; union images show realized compiled geometry.</p>
                %s
                %s
                </body></html>
                """.formatted(section("chain", "Curved seven-island chain"), section("cluster", "Organic nine-island cluster"));
    }

    private static String section(String id, String title) {
        return """
                <section><h2>%s</h2><div class="grid">
                %s%s%s%s%s%s%s
                </div></section>
                """.formatted(
                title,
                figure(id, "plan.png", "Planner reservation view"),
                figure(id, "top-down-union.png", "Realized top-down union"),
                figure(id, "upper-envelope.png", "Upper elevation envelope"),
                figure(id, "underside-envelope.png", "Underside elevation envelope"),
                figure(id, "east-west.png", "East-west group-center section"),
                figure(id, "north-south.png", "North-south group-center section"),
                figure(id, "isometric.png", "Isometric upper-surface point view"));
    }

    private static String figure(String id, String file, String caption) {
        return "<figure><figcaption>" + caption + "</figcaption><img src=\"" + id + "/" + file
                + "\" alt=\"" + caption + "\"></figure>";
    }
}
