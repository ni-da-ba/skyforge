package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidence;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandArchipelagoEvidenceWriter;
import io.github.nidaba.skyforge.reference.volume.SkyIslandArchipelagoReferenceCorpus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the first regional-scale Hub and Arc archipelago review atlas. */
public final class SkyIslandArchipelagoCorpusCli {
    public static final String EVIDENCE_ID = "hierarchical-archipelago-v1";

    private SkyIslandArchipelagoCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("usage: SkyIslandArchipelagoCorpusCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);
        String version = System.getProperty("skyforge.version", "development");

        SkyIslandArchipelagoEvidence hub = generate(SkyIslandArchipelagoReferenceCorpus.hub(
                SkyIslandArchipelagoReferenceCorpus.SKYFORGE_SEED));
        SkyIslandArchipelagoEvidence arc = generate(SkyIslandArchipelagoReferenceCorpus.arc(
                SkyIslandArchipelagoReferenceCorpus.SKYFORGE_SEED));
        SkyIslandArchipelagoEvidenceWriter writer = new SkyIslandArchipelagoEvidenceWriter();
        writer.write(hub, output.resolve("hub"), version);
        writer.write(arc, output.resolve("arc"), version);
        Files.writeString(output.resolve("index.html"), galleryHtml(), StandardCharsets.UTF_8);

        print("hub", hub);
        print("arc", arc);
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandArchipelagoEvidence generate(SkyIslandArchipelagoRequest request) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        return new SkyIslandArchipelagoEvidenceGenerator().generate(
                plan, SkyIslandArchipelagoReferenceCorpus.registry());
    }

    private static void print(String id, SkyIslandArchipelagoEvidence evidence) {
        var m = evidence.metrics();
        System.out.printf(
                "%s: groups=%d; islands=%d; solid=%d; components=%d; overlaps=%d; crossGroupOverlaps=%d; faceContacts=%d; minGroupGap=%.3f%n",
                id,
                m.groupCount(),
                m.islandCount(),
                m.solidSampleCount(),
                m.connectedComponents(),
                m.overlappingSolidSamples(),
                m.crossGroupOverlappingSolidSamples(),
                m.faceContacts(),
                m.minimumObservedGroupGap());
    }

    private static String galleryHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>Skyforge hierarchical archipelago atlas</title>
                <style>
                body{font-family:system-ui,sans-serif;max-width:1800px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}
                section{margin:2rem 0;padding:1rem;border:1px solid #bbb;background:white}
                .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}
                figure{margin:0} img{width:100%;border:1px solid #ccc;background:#fafafa} figcaption{font-weight:650;margin:.35rem 0}
                @media(max-width:1100px){.grid{grid-template-columns:1fr}}
                </style></head><body>
                <h1>Skyforge first hierarchical archipelago realization</h1>
                <p>SF-IMP-0027 regional-scale evidence. Large plan envelopes are child groups; realized geometry remains independently compiled island volumes. Colors identify child groups.</p>
                """
                + section("hub", "Hub archipelago — dominant anchor with secondary formations")
                + section("arc", "Arc archipelago — ordered regional corridor")
                + "</body></html>";
    }

    private static String section(String id, String title) {
        return "<section><h2>" + title + "</h2><div class=\"grid\">"
                + figure(id, "plan.png", "Hierarchical planner view")
                + figure(id, "top-down-groups.png", "Realized top-down geometry by child group")
                + figure(id, "upper-envelope.png", "Regional upper elevation envelope")
                + figure(id, "underside-envelope.png", "Regional underside elevation envelope")
                + figure(id, "isometric.png", "Fit-to-scene isometric upper-surface view")
                + "</div></section>";
    }

    private static String figure(String id, String file, String caption) {
        return "<figure><figcaption>" + caption + "</figcaption><img src=\"" + id + "/" + file
                + "\" alt=\"" + caption + "\"></figure>";
    }
}
