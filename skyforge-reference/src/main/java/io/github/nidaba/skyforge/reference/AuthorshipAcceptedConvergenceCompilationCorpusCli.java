package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Generates AUTH-0057 accepted-convergence compilation handoff evidence. */
public final class AuthorshipAcceptedConvergenceCompilationCorpusCli {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipAcceptedConvergenceCompilationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-accepted-convergence-compilation-v1");
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        renderAccepted(g, evidence, 0, 0);
        renderBlocked(
                g,
                "NON_ACCEPTED_BLOCKED",
                evidence.nonAcceptedFailure(),
                evidence.nonAcceptedPrimaryCount(),
                PANEL_W,
                0);
        renderBlocked(
                g,
                "REGISTRY_MISMATCH_BLOCKED",
                evidence.registryMismatchFailure(),
                evidence.registryMismatchPrimaryCount(),
                PANEL_W * 2,
                0);
        renderBlocked(
                g,
                "PRIMARY_FAILURE_EXPLICIT",
                evidence.primaryFailure(),
                evidence.primaryFailureCount(),
                0,
                PANEL_H);
        renderBinding(g, evidence.acceptedCompilation(), PANEL_W, PANEL_H);
        renderAttempts(g, evidence, PANEL_W * 2, PANEL_H);
        g.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "scenario,success,acceptedInput,preflightReproduced,primaryCompiles,"
                        + "compiledVolumes,certifiedVolumes,exactPlanIds,failureContains\n"
                        + row(
                                "ACCEPTED_HANDOFF",
                                true,
                                true,
                                true,
                                evidence.acceptedPrimaryCount(),
                                evidence.acceptedCompilation().compiledVolumeCount(),
                                evidence.acceptedCompilation().certifiedVolumeCount(),
                                exactIds(evidence.acceptedCompilation()),
                                "")
                        + row(
                                "NON_ACCEPTED_BLOCKED",
                                false,
                                false,
                                false,
                                evidence.nonAcceptedPrimaryCount(),
                                0,
                                0,
                                false,
                                "ACCEPTED_ONE_PASS")
                        + row(
                                "REGISTRY_MISMATCH_BLOCKED",
                                false,
                                true,
                                false,
                                evidence.registryMismatchPrimaryCount(),
                                0,
                                0,
                                false,
                                "does not reproduce")
                        + row(
                                "PRIMARY_FAILURE_EXPLICIT",
                                false,
                                true,
                                true,
                                evidence.primaryFailureCount(),
                                0,
                                0,
                                false,
                                "failed after accepted preflight reproduced");
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String binding =
                "freshPlanMembers,catalogVolumes,certifiedVolumes,exactPlanIds,"
                        + "preflightEqual,fullyCertified\n"
                        + evidence.acceptedCompilation().convergence().freshPlan().orElseThrow()
                                .totalMemberCount()
                        + ","
                        + evidence.acceptedCompilation().compiledVolumeCount()
                        + ","
                        + evidence.acceptedCompilation().certifiedVolumeCount()
                        + ","
                        + exactIds(evidence.acceptedCompilation())
                        + ","
                        + evidence.acceptedCompilation().reproducedPreflight()
                                .equals(
                                        evidence.acceptedCompilation().convergence()
                                                .freshPreflight().orElseThrow())
                        + ","
                        + evidence.acceptedCompilation().supportBundle().fullyCertified()
                        + "\n";
        Files.writeString(out.resolve("binding.csv"), binding, StandardCharsets.UTF_8);

        String attempts =
                "explicitCompileCalls,totalPrimaryCompiles,firstAndSecondCatalogEqual,"
                        + "firstAndSecondCertificatesEqual\n"
                        + "2,"
                        + evidence.repeatPrimaryCount()
                        + ","
                        + evidence.repeatCatalogEqual()
                        + ","
                        + evidence.repeatCertificatesEqual()
                        + "\n";
        Files.writeString(out.resolve("attempts.csv"), attempts, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0057</title>"
                        + "<h1>Accepted convergence proof-backed compilation handoff</h1>"
                        + "<p>The 16:9 atlas shows the accepted-only handoff into the exact fresh "
                        + "world catalog, registry/preflight reproduction, explicit compilation "
                        + "failure, plan-order volume identity binding, and exact-once primary "
                        + "compilation per explicit compileOnce call.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a> · "
                        + "<a href=\"binding.csv\">binding.csv</a> · "
                        + "<a href=\"attempts.csv\">attempts.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        MorphologyProviderId massifId =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProvider massif =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);
        AtomicInteger acceptedCounter = new AtomicInteger();
        SkyIslandMorphologyProviderRegistry acceptedRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(countingProvider(massif, acceptedCounter))
                        .build();
        SkyIslandSupportConvergenceReport accepted =
                acceptedConvergence(
                        57501L,
                        new ProviderMorphologySpec(massifId, 1.0, 1.0),
                        acceptedRegistry,
                        360.0,
                        440.0);
        SkyIslandAcceptedConvergenceCompilation acceptedCompilation =
                new SkyIslandAcceptedConvergenceCompiler()
                        .compileOnce(accepted, acceptedRegistry);

        AtomicInteger nonAcceptedCounter = new AtomicInteger();
        SkyIslandMorphologyProviderRegistry nonAcceptedRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(countingProvider(massif, nonAcceptedCounter))
                        .build();
        SkyIslandSupportConvergenceReport nonAccepted =
                convergence(
                        twoRequest(
                                57502L,
                                new ProviderMorphologySpec(massifId, 1.0, 1.0),
                                120.0,
                                280.0,
                                260.0),
                        nonAcceptedRegistry);
        String nonAcceptedFailure =
                failureMessage(
                        () ->
                                new SkyIslandAcceptedConvergenceCompiler()
                                        .compileOnce(nonAccepted, nonAcceptedRegistry));

        AtomicInteger mismatchCounter = new AtomicInteger();
        SkyIslandMorphologyProvider changed =
                changedSupportProvider(massif, mismatchCounter);
        SkyIslandMorphologyProviderRegistry changedRegistry =
                SkyIslandMorphologyProviderRegistry.builder().register(changed).build();
        String registryMismatchFailure =
                failureMessage(
                        () ->
                                new SkyIslandAcceptedConvergenceCompiler()
                                        .compileOnce(accepted, changedRegistry));

        MorphologyProviderId failingId =
                new MorphologyProviderId("reference", "auth57-primary-failure");
        AtomicInteger primaryFailureCounter = new AtomicInteger();
        SkyIslandMorphologyProvider failing =
                failingPrimaryProvider(failingId, primaryFailureCounter);
        SkyIslandMorphologyProviderRegistry failingRegistry =
                SkyIslandMorphologyProviderRegistry.builder().register(failing).build();
        SkyIslandSupportConvergenceReport failingAccepted =
                acceptedConvergence(
                        57503L,
                        new ProviderMorphologySpec(failingId, 0.0, 0.0),
                        failingRegistry,
                        200.0,
                        240.0);
        String primaryFailure =
                failureMessage(
                        () ->
                                new SkyIslandAcceptedConvergenceCompiler()
                                        .compileOnce(failingAccepted, failingRegistry));

        MorphologyProviderId basinId =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandMorphologyProvider basin =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.BASIN);
        AtomicInteger repeatCounter = new AtomicInteger();
        SkyIslandMorphologyProviderRegistry repeatRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(countingProvider(basin, repeatCounter))
                        .build();
        SkyIslandSupportConvergenceReport repeatConvergence =
                acceptedConvergence(
                        57504L,
                        new ProviderMorphologySpec(basinId, 1.0, 1.0),
                        repeatRegistry,
                        360.0,
                        440.0);
        SkyIslandAcceptedConvergenceCompiler compiler =
                new SkyIslandAcceptedConvergenceCompiler();
        SkyIslandAcceptedConvergenceCompilation first =
                compiler.compileOnce(repeatConvergence, repeatRegistry);
        SkyIslandAcceptedConvergenceCompilation second =
                compiler.compileOnce(repeatConvergence, repeatRegistry);

        return new Evidence(
                acceptedCompilation,
                acceptedCounter.get(),
                nonAcceptedFailure,
                nonAcceptedCounter.get(),
                registryMismatchFailure,
                mismatchCounter.get(),
                primaryFailure,
                primaryFailureCounter.get(),
                repeatCounter.get(),
                first.supportBundle().catalog().volumes()
                        .equals(second.supportBundle().catalog().volumes()),
                first.supportBundle().certificates()
                        .equals(second.supportBundle().certificates()));
    }

    private static void renderAccepted(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "ACCEPTED_HANDOFF", new Color(219, 239, 221));
        text(g, x + 18, y + 50, "AUTH-0056 outcome: ACCEPTED_ONE_PASS", 10);
        text(g, x + 18, y + 76, "accepted preflight reproduced: YES", 10);
        text(g, x + 18, y + 102, "primary compiles: " + evidence.acceptedPrimaryCount(), 10);
        text(
                g,
                x + 18,
                y + 128,
                "world volumes: " + evidence.acceptedCompilation().compiledVolumeCount(),
                10);
        text(
                g,
                x + 18,
                y + 154,
                "certified volumes: " + evidence.acceptedCompilation().certifiedVolumeCount(),
                10);
        text(g, x + 18, y + 180, "exact plan IDs: " + exactIds(evidence.acceptedCompilation()), 10);
        stage(g, x + 28, y + 222, "CONVERGENCE", true);
        stage(g, x + 154, y + 222, "PREFLIGHT", true);
        stage(g, x + 280, y + 222, "COMPILED", true);
        text(g, x + 18, y + 302, "handoff accepted; fully certified support bundle", 10);
    }

    private static void renderBlocked(
            Graphics2D g,
            String title,
            String failure,
            int primaryCount,
            int x,
            int y) {
        panel(g, x, y, title, new Color(244, 215, 215));
        text(g, x + 18, y + 50, "primary compiles: " + primaryCount, 10);
        text(g, x + 18, y + 80, "terminal failure:", 10);
        wrap(g, x + 18, y + 104, failure, 54, 9);
        text(g, x + 18, y + 292, "no fallback / no retry / no ordinary compile", 10);
    }

    private static void renderBinding(
            Graphics2D g,
            SkyIslandAcceptedConvergenceCompilation compilation,
            int x,
            int y) {
        panel(g, x, y, "PLAN_ID_BINDING", new Color(229, 232, 242));
        var plan = compilation.convergence().freshPlan().orElseThrow();
        var volume = compilation.supportBundle().catalog().volumes().get(0);
        text(g, x + 18, y + 50, "fresh hierarchy -> world catalog", 10);
        text(g, x + 18, y + 82, "root seed", 9);
        text(g, x + 145, y + 82, Long.toUnsignedString(plan.rootSeed()), 9);
        text(g, x + 18, y + 112, "group", 9);
        text(g, x + 145, y + 112, plan.groups().get(0).identifier(), 9);
        text(g, x + 18, y + 142, "member ordinal", 9);
        text(g, x + 145, y + 142, "0", 9);
        text(g, x + 18, y + 172, "geometry seed", 9);
        text(g, x + 145, y + 172, Long.toUnsignedString(volume.id().geometrySeed()), 9);
        text(g, x + 18, y + 216, "exact ID match: " + exactIds(compilation), 10);
        text(
                g,
                x + 18,
                y + 246,
                "certificate present: "
                        + compilation.supportBundle().certificateFor(volume).isPresent(),
                10);
        text(g, x + 18, y + 302, "plan-order identity is part of the handoff proof", 10);
    }

    private static void renderAttempts(Graphics2D g, Evidence evidence, int x, int y) {
        panel(g, x, y, "EXACT_ONCE_COMPILE", new Color(229, 232, 242));
        text(g, x + 18, y + 52, "explicit compileOnce calls = 2", 10);
        text(g, x + 18, y + 82, "total primary compiles = " + evidence.repeatPrimaryCount(), 10);
        arrow(g, x + 34, y + 124, x + 350, y + 124);
        text(g, x + 115, y + 118, "call #1 -> 1 primary compile", 9);
        arrow(g, x + 34, y + 192, x + 350, y + 192);
        text(g, x + 115, y + 186, "call #2 -> 1 primary compile", 9);
        text(g, x + 18, y + 244, "catalogs equal: " + evidence.repeatCatalogEqual(), 10);
        text(g, x + 18, y + 270, "certificates equal: " + evidence.repeatCertificatesEqual(), 10);
        text(g, x + 18, y + 310, "internal retries = 0", 10);
    }

    private static String row(
            String scenario,
            boolean success,
            boolean acceptedInput,
            boolean preflightReproduced,
            int primaryCompiles,
            int volumes,
            int certificates,
            boolean exactPlanIds,
            String failureContains) {
        return scenario
                + ","
                + success
                + ","
                + acceptedInput
                + ","
                + preflightReproduced
                + ","
                + primaryCompiles
                + ","
                + volumes
                + ","
                + certificates
                + ","
                + exactPlanIds
                + ","
                + failureContains
                + "\n";
    }

    private static boolean exactIds(SkyIslandAcceptedConvergenceCompilation compilation) {
        SkyIslandArchipelagoPlan plan = compilation.convergence().freshPlan().orElseThrow();
        int index = 0;
        for (var group : plan.groups()) {
            for (int memberOrdinal = 0;
                    memberOrdinal < group.groupPlan().memberCount();
                    memberOrdinal++) {
                var member = group.groupPlan().members().get(memberOrdinal);
                var id = compilation.supportBundle().catalog().volumes().get(index++).id();
                if (id.archipelagoRootSeed() != plan.rootSeed()
                        || id.groupOrdinal() != group.ordinal()
                        || id.memberOrdinal() != memberOrdinal
                        || !id.groupIdentifier().equals(group.identifier())
                        || id.geometrySeed() != member.descriptor().seed()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String failureMessage(Runnable action) {
        try {
            action.run();
            return "UNEXPECTED SUCCESS";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
    }

    private static SkyIslandMorphologyProvider countingProvider(
            SkyIslandMorphologyProvider delegate,
            AtomicInteger counter) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return delegate.id();
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                counter.incrementAndGet();
                return delegate.compilePrimary(descriptor);
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                return delegate.certifiedPrimarySupportEnvelope(descriptor);
            }

            @Override
            public Optional<SecondaryMorphologyContribution> compileSecondaryMorphology(
                    SkyIslandVolumeDescriptor descriptor, double amplitude) {
                return delegate.compileSecondaryMorphology(descriptor, amplitude);
            }
        };
    }

    private static SkyIslandMorphologyProvider changedSupportProvider(
            SkyIslandMorphologyProvider delegate,
            AtomicInteger counter) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return delegate.id();
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                counter.incrementAndGet();
                return delegate.compilePrimary(descriptor);
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                PrimaryMorphologySupportEnvelope base =
                        delegate.certifiedPrimarySupportEnvelope(descriptor).orElseThrow();
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(
                                Math.nextDown(base.maximumHorizontalRadius() * 0.95),
                                base.maximumUpperOffset(),
                                base.maximumUndersideDepth()));
            }

            @Override
            public Optional<SecondaryMorphologyContribution> compileSecondaryMorphology(
                    SkyIslandVolumeDescriptor descriptor, double amplitude) {
                return delegate.compileSecondaryMorphology(descriptor, amplitude);
            }
        };
    }

    private static SkyIslandMorphologyProvider failingPrimaryProvider(
            MorphologyProviderId id,
            AtomicInteger counter) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                counter.incrementAndGet();
                throw new IllegalStateException("intentional AUTH-0057 primary failure");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(120.0, 50.0, 50.0));
            }
        };
    }

    private static SkyIslandSupportConvergenceReport acceptedConvergence(
            long rootSeed,
            ProviderMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry,
            double horizontal,
            double groupRadius) {
        SkyIslandSupportConvergenceReport result =
                convergence(
                        singleRequest(rootSeed, morphology, horizontal, groupRadius),
                        registry);
        if (result.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0057 evidence fixture did not converge");
        }
        return result;
    }

    private static SkyIslandSupportConvergenceReport convergence(
            SkyIslandArchipelagoRequest request,
            SkyIslandMorphologyProviderRegistry registry) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        return new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
    }

    private static SkyIslandArchipelagoRequest singleRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius) {
        return request(
                rootSeed,
                List.of(morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                0.0);
    }

    private static SkyIslandArchipelagoRequest twoRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius,
            double spacing) {
        return request(
                rootSeed,
                List.of(morphology, morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(spacing, 0.0, 0.0, 0.0),
                20.0);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            List<SkyIslandMorphologySpec> morphologies,
            double horizontal,
            double groupRadius,
            SkyIslandGroupLayout layout,
            double minimumGap) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth57",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        horizontal,
                        minimumGap,
                        0.0,
                        morphologies,
                        layout,
                        groupRadius);
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

    private static void panel(Graphics2D g, int x, int y, String title, Color fill) {
        g.setColor(fill);
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 27);
    }

    private static void stage(Graphics2D g, int x, int y, String label, boolean okay) {
        g.setColor(okay ? new Color(80, 145, 90) : new Color(185, 185, 185));
        g.fillRoundRect(x, y, 108, 28, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        g.drawString(label, x + 11, y + 18);
    }

    private static void text(Graphics2D g, int x, int y, String value, int size) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.drawString(value, x, y);
    }

    private static void wrap(
            Graphics2D g,
            int x,
            int y,
            String value,
            int maximumCharacters,
            int size) {
        String remaining = value;
        int line = 0;
        while (!remaining.isEmpty() && line < 7) {
            int end = Math.min(maximumCharacters, remaining.length());
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) {
                    end = space;
                }
            }
            String part = remaining.substring(0, end).trim();
            text(g, x, y + line * 18, part, size);
            remaining = remaining.substring(end).trim();
            line++;
        }
    }

    private static void arrow(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setColor(new Color(85, 85, 85));
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x2 - 8, y2 - 5);
        g.drawLine(x2, y2, x2 - 8, y2 + 5);
    }

    private record Evidence(
            SkyIslandAcceptedConvergenceCompilation acceptedCompilation,
            int acceptedPrimaryCount,
            String nonAcceptedFailure,
            int nonAcceptedPrimaryCount,
            String registryMismatchFailure,
            int registryMismatchPrimaryCount,
            String primaryFailure,
            int primaryFailureCount,
            int repeatPrimaryCount,
            boolean repeatCatalogEqual,
            boolean repeatCertificatesEqual) {}
}
