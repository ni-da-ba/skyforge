package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
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
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandEcologyField;
import io.github.nidaba.skyforge.world.SkyIslandEcologySample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandPublishedSurfaceEcologyResolver;
import io.github.nidaba.skyforge.world.SkyIslandPublishedSurfaceEcologySample;
import io.github.nidaba.skyforge.world.SkyIslandSemanticField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0088 exact published surface-ecology projection evidence. */
public final class AuthorshipPublishedSurfaceEcologyCorpusCli {
    public static final String EVIDENCE_ID = "authorship-published-surface-ecology-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303838L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedSurfaceEcologyCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipPublishedSurfaceEcologyCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_AUTH0003", evidence.exactAuth0003(),
                "volume: " + evidence.exact().volumeId().path(),
                "regime: " + evidence.exact().ecologySample().orElseThrow().regime(),
                "resolver sample equals direct AUTH-0003");
        panel(g, PANEL_W, 0, "WORLD_LOCAL", evidence.worldLocalExact(),
                "world X/Z retained exactly",
                "local = world - realized center",
                "no hidden scale or Y transform");
        panel(g, PANEL_W * 2, 0, "UNKNOWN_BLOCKED", evidence.unknownRejected(),
                "unknown volume id -> reject",
                "no nearest/bounds/seed discovery",
                "AUTH-0087 identity remains authoritative");
        panel(g, 0, PANEL_H, "PHYSICAL_GATE", evidence.physicalAbsentRejected(),
                "no compiled horizontal column",
                "no authored surface ecology emitted",
                "backend cannot paint ecology into empty sky");
        panel(g, PANEL_W, PANEL_H, "AUTHORED_GATE", evidence.authoredFringeRejected(),
                "physical column exists",
                "current naturalized interiority = 0",
                "diagnostic COLD_BARREN is not ownership");
        panel(g, PANEL_W * 2, PANEL_H, "NO_Y_AND_DIVERSITY",
                evidence.noYContract() && evidence.regimeDiversity(),
                "public query: volume id + Coordinate2 only",
                "distinct regimes observed: " + evidence.regimes().size(),
                "backend owns Y/quart-biome presentation");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "EXACT_AUTH0003," + evidence.exactAuth0003() + "\n"
                + "WORLD_LOCAL," + evidence.worldLocalExact() + "\n"
                + "UNKNOWN_BLOCKED," + evidence.unknownRejected() + "\n"
                + "PHYSICAL_GATE," + evidence.physicalAbsentRejected() + "\n"
                + "AUTHORED_GATE," + evidence.authoredFringeRejected() + "\n"
                + "NO_Y_CONTRACT," + evidence.noYContract() + "\n"
                + "REGIME_DIVERSITY," + evidence.regimeDiversity() + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        String exact = "volumeId,worldX,worldZ,localX,localZ,physicalColumn,interiority,regime,vegetation,saturation,thermal\n"
                + evidence.exact().volumeId().path() + ","
                + evidence.exact().worldPosition().x() + ","
                + evidence.exact().worldPosition().z() + ","
                + evidence.exact().localPosition().x() + ","
                + evidence.exact().localPosition().z() + ","
                + evidence.exact().physicalColumnPresent() + ","
                + evidence.exact().authoredInteriority() + ","
                + evidence.exact().ecologySample().orElseThrow().regime() + ","
                + evidence.exact().ecologySample().orElseThrow().vegetationPotential() + ","
                + evidence.exact().ecologySample().orElseThrow().saturationPotential() + ","
                + evidence.exact().ecologySample().orElseThrow().thermalSuitability() + "\n";
        Files.writeString(out.resolve("exact.csv"), exact, StandardCharsets.UTF_8);

        StringBuilder regimes = new StringBuilder(
                "islandKey,regime,worldX,worldZ,vegetation,saturation,thermal\n");
        for (RegimeObservation observation : evidence.regimes()) {
            SkyIslandEcologySample ecology = observation.sample().ecologySample().orElseThrow();
            regimes.append(observation.islandKey()).append(',')
                    .append(ecology.regime()).append(',')
                    .append(observation.sample().worldPosition().x()).append(',')
                    .append(observation.sample().worldPosition().z()).append(',')
                    .append(ecology.vegetationPotential()).append(',')
                    .append(ecology.saturationPotential()).append(',')
                    .append(ecology.thermalSuitability()).append('\n');
        }
        Files.writeString(out.resolve("regimes.csv"), regimes, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0088 published surface ecology</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Published surface ecology projection</h1>
                <p>AUTH-0088 projects the unchanged AUTH-0003 ecology field through one exact AUTH-0087 published authored-realization binding. Exact volume identity, world/local translation, compiled horizontal support, and current authored-domain ownership are proven without introducing Minecraft biome keys, physical Y presentation, quart-cell policy, or backend lifecycle behavior.</p>
                <img src="atlas.png" alt="AUTH-0088 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="exact.csv">exact.csv</a> · <a href="regimes.csv">regimes.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(58801L), 1L);

        Fixture exactFixture = fixture(publication, 2332L);
        SkyIslandLocalPosition exactLocal =
                firstSupportedAuthoredPosition(exactFixture.association());
        Coordinate2 exactWorld = toWorld(exactFixture.association(), exactLocal);
        SkyIslandPublishedSurfaceEcologyResolver exactResolver =
                new SkyIslandPublishedSurfaceEcologyResolver(exactFixture.binding());
        SkyIslandPublishedSurfaceEcologySample exact =
                exactResolver.sample(exactFixture.association().realizedVolumeId(), exactWorld);
        SkyIslandEcologySample direct =
                SkyIslandEcologyField.create(exactFixture.association().authoredDescriptor())
                        .sample(exactLocal);
        boolean exactAuth0003 = exact.ecologySample().orElseThrow().equals(direct);
        boolean worldLocalExact =
                exact.worldPosition().equals(exactWorld) && exact.localPosition().equals(exactLocal);

        SkyIslandWorldVolumeId known = exactFixture.association().realizedVolumeId();
        SkyIslandWorldVolumeId unknown = new SkyIslandWorldVolumeId(
                known.archipelagoRootSeed(),
                known.groupIdentifier() + "-unknown",
                known.groupOrdinal(),
                known.memberOrdinal(),
                known.geometrySeed());
        boolean unknownRejected = rejected(
                () -> exactResolver.sample(unknown, exactWorld));

        var realized = exactFixture.association().realizedVolume().compiledVolume().descriptor();
        Coordinate2 farOutside = new Coordinate2(
                realized.centerX() + 3.0 * realized.nominalRadius(),
                realized.centerZ());
        SkyIslandPublishedSurfaceEcologySample physicalAbsent =
                exactResolver.sample(known, farOutside);
        boolean physicalAbsentRejected =
                !physicalAbsent.physicalColumnPresent()
                        && !physicalAbsent.authoredSurfacePresent()
                        && physicalAbsent.ecologySample().isEmpty();

        FringeFixture fringe = firstPhysicalUnownedFringe(publication);
        SkyIslandPublishedSurfaceEcologySample fringeSample =
                new SkyIslandPublishedSurfaceEcologyResolver(fringe.fixture().binding())
                        .sample(
                                fringe.fixture().association().realizedVolumeId(),
                                toWorld(fringe.fixture().association(), fringe.local()));
        boolean authoredFringeRejected =
                fringeSample.physicalColumnPresent()
                        && fringeSample.authoredInteriority() == 0.0
                        && !fringeSample.authoredSurfacePresent();

        Method[] sampleMethods = Arrays.stream(
                        SkyIslandPublishedSurfaceEcologyResolver.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("sample"))
                .toArray(Method[]::new);
        boolean noYContract =
                sampleMethods.length == 1
                        && List.of(sampleMethods[0].getParameterTypes())
                                .equals(List.of(SkyIslandWorldVolumeId.class, Coordinate2.class));

        List<RegimeObservation> regimes = diverseRegimes(publication);
        boolean regimeDiversity = regimes.size() >= 5
                && regimes.stream()
                        .map(observation ->
                                observation.sample().ecologySample().orElseThrow().regime())
                        .distinct()
                        .count() == regimes.size();

        return new Evidence(
                exact,
                exactAuth0003,
                worldLocalExact,
                unknownRejected,
                physicalAbsentRejected,
                authoredFringeRejected,
                noYContract,
                regimeDiversity,
                regimes);
    }

    private static List<RegimeObservation> diverseRegimes(
            SkyIslandCompiledWorldPublication publication) {
        EnumSet<SkyIslandEcologyRegime> seen = EnumSet.noneOf(SkyIslandEcologyRegime.class);
        ArrayList<RegimeObservation> observations = new ArrayList<>();
        SkyIslandWorldVolume selectedVolume = publication.catalog().volumes().getFirst();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(selectedVolume.compiledVolume());

        for (long key = 0L; key < 512L && observations.size() < 6; key++) {
            SkyIslandDescriptor descriptor = authored(key, selectedVolume);
            SkyIslandSemanticField interiority =
                    SkyIslandSemanticFieldSet.create(descriptor).interiority();
            SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
            double radius = descriptor.nominalRadius();

            boolean selected = false;
            for (int iz = 0; iz <= 16 && !selected; iz++) {
                double z = -radius + iz * (2.0 * radius / 16.0);
                for (int ix = 0; ix <= 16; ix++) {
                    double x = -radius + ix * (2.0 * radius / 16.0);
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                    if (columns.columnAt(local).isEmpty() || interiority.sample(local) <= 0.0) {
                        continue;
                    }
                    SkyIslandEcologyRegime regime = ecology.sample(local).regime();
                    if (seen.add(regime)) {
                        Fixture fixture = fixture(publication, key);
                        SkyIslandPublishedSurfaceEcologySample sample =
                                new SkyIslandPublishedSurfaceEcologyResolver(fixture.binding())
                                        .sample(
                                                fixture.association().realizedVolumeId(),
                                                toWorld(fixture.association(), local));
                        observations.add(new RegimeObservation(key, sample));
                        selected = true;
                        break;
                    }
                }
            }
        }
        return List.copyOf(observations);
    }

    private static FringeFixture firstPhysicalUnownedFringe(
            SkyIslandCompiledWorldPublication publication) {
        SkyIslandWorldVolume volume = publication.catalog().volumes().getFirst();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume());
        double radius = volume.compiledVolume().descriptor().nominalRadius();

        for (long key = 0L; key < 128L; key++) {
            SkyIslandDescriptor descriptor = authored(key, volume);
            SkyIslandSemanticField interiority =
                    SkyIslandSemanticFieldSet.create(descriptor).interiority();
            for (int iz = 0; iz <= 40; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix <= 40; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                    if (columns.columnAt(local).isPresent() && interiority.sample(local) == 0.0) {
                        return new FringeFixture(fixture(publication, key), local);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "AUTH-0088 evidence found no physical fringe outside current authored ownership");
    }

    private static SkyIslandLocalPosition firstSupportedAuthoredPosition(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(association.authoredDescriptor()).interiority();
        double radius = association.authoredDescriptor().nominalRadius();

        for (int iz = 0; iz <= 32; iz++) {
            double z = -radius + iz * (2.0 * radius / 32.0);
            for (int ix = 0; ix <= 32; ix++) {
                double x = -radius + ix * (2.0 * radius / 32.0);
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                if (columns.columnAt(local).isPresent() && interiority.sample(local) > 0.0) {
                    return local;
                }
            }
        }
        throw new IllegalStateException(
                "AUTH-0088 evidence fixture has no shared physical/authored surface");
    }

    private static Fixture fixture(
            SkyIslandCompiledWorldPublication publication,
            long selectedIslandKey) {
        SkyIslandWorldVolume selectedVolume = publication.catalog().volumes().getFirst();
        SkyIslandAuthoredRealizationAssociation selected =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored(selectedIslandKey, selectedVolume),
                        selectedVolume);

        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        associations.add(selected);
        int ordinal = 1;
        for (SkyIslandWorldVolume other :
                publication.catalog().volumes().subList(
                        1, publication.catalog().volumes().size())) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(selectedIslandKey + 100_000L + ordinal, other),
                    other));
            ordinal++;
        }

        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations);
        return new Fixture(
                new SkyIslandPublishedAuthoredRealizationBinding(publication, catalog),
                selected);
    }

    private static Coordinate2 toWorld(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandLocalPosition local) {
        var realized = association.realizedVolume().compiledVolume().descriptor();
        return new Coordinate2(
                realized.centerX() + local.x(),
                realized.centerZ() + local.z());
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base =
                io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 88L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        var morphology = realized.hasSemanticMorphologyFamily()
                ? realized.morphologyFamily()
                : base.morphologyFamily();
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                morphology,
                realized.nominalRadius(),
                base.reliefBudget(),
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
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
            throw new IllegalStateException("AUTH-0088 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth88",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                800.0, 0.0, 0.0, 0.0),
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

    private static boolean rejected(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void panel(
            Graphics2D g,
            int x,
            int y,
            String title,
            boolean pass,
            String first,
            String second,
            String third) {
        g.setColor(pass ? new Color(225, 241, 228) : new Color(245, 220, 220));
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(first, x + 18, y + 72);
        g.drawString(second, x + 18, y + 104);
        g.drawString(third, x + 18, y + 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(pass ? "PASS" : "FAIL", x + 18, y + 205);
    }

    private record Fixture(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            SkyIslandAuthoredRealizationAssociation association) {}

    private record FringeFixture(
            Fixture fixture,
            SkyIslandLocalPosition local) {}

    private record RegimeObservation(
            long islandKey,
            SkyIslandPublishedSurfaceEcologySample sample) {}

    private record Evidence(
            SkyIslandPublishedSurfaceEcologySample exact,
            boolean exactAuth0003,
            boolean worldLocalExact,
            boolean unknownRejected,
            boolean physicalAbsentRejected,
            boolean authoredFringeRejected,
            boolean noYContract,
            boolean regimeDiversity,
            List<RegimeObservation> regimes) {}
}
