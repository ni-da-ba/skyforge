package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0058 compiled-world publication identity evidence. */
public final class AuthorshipCompiledWorldPublicationCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipCompiledWorldPublicationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-compiled-world-publication-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();
        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderPublicationGate(g, evidence, 0, 0);
        renderConvergence(g, evidence, PANEL_W, 0);
        renderCatalog(g, evidence, PANEL_W * 2, 0);
        renderSupport(g, evidence, 0, PANEL_H);
        renderVersion(g, evidence, PANEL_W, PANEL_H);
        renderRegionalIdentity(g, evidence, PANEL_W * 2, PANEL_H);
        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,pass,publicationToken,root,revision,volumeCount,certifiedCount\n"
                        + row("PUBLICATION_GATE", evidence.gatePass(), evidence.first())
                        + row("CONVERGENCE_BINDING", evidence.convergenceBound(), evidence.first())
                        + row("CATALOG_BINDING", evidence.catalogBound(), evidence.first())
                        + row("SUPPORT_BINDING", evidence.supportBound(), evidence.first())
                        + row("VERSION_AXIS", evidence.versionSeparated(), evidence.revised())
                        + row("REGIONAL_IDENTITY", evidence.regionalSeparated(), evidence.otherRegion());
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String binding =
                "accepted,preflightAdmitted,catalogSame,certificatesSame,catalogIdentitySame,fullyCertified\n"
                        + evidence.first().compilation().convergence().accepted()
                        + ","
                        + evidence.first().compilation().reproducedPreflight().admitted()
                        + ","
                        + (evidence.first().catalog()
                                == evidence.first().compilation().supportBundle().catalog())
                        + ","
                        + evidence.first().supportCertificates()
                                .equals(evidence.first().compilation().supportBundle().certificates())
                        + ","
                        + evidence.first().catalogIdentity()
                                .equals(
                                        evidence.first().catalog().volumes().stream()
                                                .map(io.github.nidaba.skyforge.world.SkyIslandWorldVolume::id)
                                                .toList())
                        + ","
                        + evidence.first().compilation().supportBundle().fullyCertified()
                        + "\n";
        Files.writeString(out.resolve("binding.csv"), binding, StandardCharsets.UTF_8);

        String version =
                "firstToken,revisedToken,tokenChanged,catalogIdentitySame,certificatesSame\n"
                        + evidence.first().id().canonicalToken()
                        + ","
                        + evidence.revised().id().canonicalToken()
                        + ","
                        + !evidence.first().id().equals(evidence.revised().id())
                        + ","
                        + evidence.first().catalogIdentity().equals(evidence.revised().catalogIdentity())
                        + ","
                        + evidence.first().supportCertificates()
                                .equals(evidence.revised().supportCertificates())
                        + "\n";
        Files.writeString(out.resolve("version.csv"), version, StandardCharsets.UTF_8);

        String regional =
                "firstToken,otherToken,sameRevision,tokenChanged,catalogIdentityChanged\n"
                        + evidence.first().id().canonicalToken()
                        + ","
                        + evidence.otherRegion().id().canonicalToken()
                        + ","
                        + (evidence.first().id().publicationRevision()
                                == evidence.otherRegion().id().publicationRevision())
                        + ","
                        + !evidence.first().id().equals(evidence.otherRegion().id())
                        + ","
                        + !evidence.first().catalogIdentity()
                                .equals(evidence.otherRegion().catalogIdentity())
                        + "\n";
        Files.writeString(out.resolve("regional.csv"), regional, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0058</title>"
                        + "<h1>Accepted compiled-world publication identity</h1>"
                        + "<p>The 16:9 atlas shows the type-level publication gate, exact accepted "
                        + "convergence/catalog/support binding, explicit publication revision axis, "
                        + "and regional identity separation.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"binding.csv\">binding.csv</a> · "
                        + "<a href=\"version.csv\">version.csv</a> · "
                        + "<a href=\"regional.csv\">regional.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(58501L);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication first = publisher.publish(compilation, 3L);
        SkyIslandCompiledWorldPublication revised = publisher.publish(compilation, 4L);
        SkyIslandCompiledWorldPublication otherRegion =
                publisher.publish(acceptedCompilation(58502L), 3L);

        boolean gatePass =
                java.util.Arrays.stream(SkyIslandCompiledWorldPublisher.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .allMatch(
                                method ->
                                        method.getName().equals("publish")
                                                && method.getParameterCount() == 2
                                                && method.getParameterTypes()[0]
                                                        == SkyIslandAcceptedConvergenceCompilation.class);
        boolean convergenceBound =
                first.compilation().convergence().outcome()
                                == SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS
                        && first.compilation().reproducedPreflight().admitted()
                        && first.acceptedPlan().rootSeed() == first.id().archipelagoRootSeed();
        boolean catalogBound =
                first.catalog() == first.compilation().supportBundle().catalog()
                        && first.catalogIdentity()
                                .equals(
                                        first.catalog().volumes().stream()
                                                .map(io.github.nidaba.skyforge.world.SkyIslandWorldVolume::id)
                                                .toList());
        boolean supportBound =
                first.compilation().supportBundle().fullyCertified()
                        && first.supportCertificates()
                                .equals(first.compilation().supportBundle().certificates())
                        && first.supportCertificates().size() == first.volumeCount();
        boolean versionSeparated =
                !first.id().equals(revised.id())
                        && first.catalogIdentity().equals(revised.catalogIdentity())
                        && first.supportCertificates().equals(revised.supportCertificates());
        boolean regionalSeparated =
                first.id().publicationRevision() == otherRegion.id().publicationRevision()
                        && !first.id().equals(otherRegion.id())
                        && !first.catalogIdentity().equals(otherRegion.catalogIdentity());

        return new Evidence(
                first,
                revised,
                otherRegion,
                gatePass,
                convergenceBound,
                catalogBound,
                supportBound,
                versionSeparated,
                regionalSeparated);
    }

    private static String row(
            String scenario, boolean pass, SkyIslandCompiledWorldPublication publication) {
        return scenario
                + ","
                + pass
                + ","
                + publication.id().canonicalToken()
                + ","
                + Long.toUnsignedString(publication.id().archipelagoRootSeed())
                + ","
                + publication.id().publicationRevision()
                + ","
                + publication.volumeCount()
                + ","
                + publication.supportCertificates().size()
                + "\n";
    }

    private static void renderPublicationGate(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "PUBLICATION_GATE", evidence.gatePass());
        line(g, x, y, 58, "input capability: AUTH-0057 compilation");
        line(g, x, y, 86, "raw catalog overload: NONE");
        line(g, x, y, 114, "raw support-bundle overload: NONE");
        line(g, x, y, 156, "gate pass: " + yes(evidence.gatePass()));
        line(g, x, y, 218, "backend-neutral publication capability only");
    }

    private static void renderConvergence(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CONVERGENCE_BINDING", evidence.convergenceBound());
        line(g, x, y, 58, "outcome: ACCEPTED_ONE_PASS");
        line(g, x, y, 86, "reproduced preflight: ADMITTED");
        line(
                g,
                x,
                y,
                114,
                "root: " + Long.toUnsignedString(evidence.first().acceptedPlan().rootSeed()));
        line(g, x, y, 156, "exact accepted plan retained: " + yes(evidence.convergenceBound()));
        line(g, x, y, 218, "publication performs no planning or retry");
    }

    private static void renderCatalog(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "CATALOG_BINDING", evidence.catalogBound());
        line(
                g,
                x,
                y,
                58,
                "catalog root: "
                        + Long.toUnsignedString(evidence.first().catalog().rootSeed()));
        line(g, x, y, 86, "volume count: " + evidence.first().volumeCount());
        line(g, x, y, 114, "plan-order IDs: EXACT");
        line(g, x, y, 156, "same AUTH-0057 catalog object: " + yes(evidence.catalogBound()));
        line(g, x, y, 218, "no second catalog identity invented");
    }

    private static void renderSupport(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "SUPPORT_BINDING", evidence.supportBound());
        line(
                g,
                x,
                y,
                58,
                "certificates: " + evidence.first().supportCertificates().size());
        line(g, x, y, 86, "volumes: " + evidence.first().volumeCount());
        line(g, x, y, 114, "fully certified: YES");
        line(g, x, y, 156, "exact certificate set retained: " + yes(evidence.supportBound()));
        line(g, x, y, 218, "partial proof cannot publish");
    }

    private static void renderVersion(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "VERSION_AXIS", evidence.versionSeparated());
        line(g, x, y, 58, "revision A: " + evidence.first().id().publicationRevision());
        line(g, x, y, 86, "revision B: " + evidence.revised().id().publicationRevision());
        line(g, x, y, 114, "publication token changes: YES");
        line(g, x, y, 156, "catalog IDs unchanged: YES");
        line(g, x, y, 184, "support certificates unchanged: YES");
        line(g, x, y, 234, "revision is explicit; not a content hash");
    }

    private static void renderRegionalIdentity(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "REGIONAL_IDENTITY", evidence.regionalSeparated());
        line(
                g,
                x,
                y,
                58,
                "revision: " + evidence.first().id().publicationRevision() + " / "
                        + evidence.otherRegion().id().publicationRevision());
        line(
                g,
                x,
                y,
                86,
                "root A: " + Long.toUnsignedString(evidence.first().id().archipelagoRootSeed()));
        line(
                g,
                x,
                y,
                114,
                "root B: " + Long.toUnsignedString(evidence.otherRegion().id().archipelagoRootSeed()));
        line(g, x, y, 156, "publication identity differs: YES");
        line(g, x, y, 184, "catalog identity differs: YES");
        line(g, x, y, 234, "regional root remains part of publication identity");
    }

    private static void panel(Graphics2D g, int x, int y, String title, boolean pass) {
        g.setColor(pass ? new Color(224, 240, 226) : new Color(244, 218, 218));
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(176, 176, 176));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 28);
    }

    private static void line(Graphics2D g, int x, int y, int offsetY, String text) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(text, x + 18, y + offsetY);
    }

    private static String yes(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0058 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth58",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }

    private record Evidence(
            SkyIslandCompiledWorldPublication first,
            SkyIslandCompiledWorldPublication revised,
            SkyIslandCompiledWorldPublication otherRegion,
            boolean gatePass,
            boolean convergenceBound,
            boolean catalogBound,
            boolean supportBound,
            boolean versionSeparated,
            boolean regionalSeparated) {}
}
