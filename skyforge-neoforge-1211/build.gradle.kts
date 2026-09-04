import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Properties

plugins {
    `java-library`
    // The settings-level net.neoforged.moddev.repositories plugin already places the
    // ModDevGradle implementation on Gradle's classpath. Repeating a version here makes Gradle
    // try to compare that version with an already-loaded plugin whose classpath version is
    // unknown. Apply the existing plugin implementation by id only.
    id("net.neoforged.moddev")
}

java {
    // Minecraft 1.21.1 and NeoForge require a Java 21 toolchain. The settings-level Foojay
    // resolver allows Gradle to provision this toolchain automatically when it is not installed
    // locally, while the overall Skyforge workspace may continue running Gradle on JDK 25.
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(
            providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt(),
        ),
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // FML initializes the tested mod before JUnit can report individual tests. Keep bootstrap
    // diagnostics visible so a required worldgen mixin failure is actionable in CI rather than
    // collapsing into Gradle's outer InvocationTargetException.
    systemProperty("mixin.debug.verbose", "true")
    systemProperty("mixin.dumpTargetOnFailure", "true")
    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Development-only data/resource pack material for interactive world-generation proofs. This
// source set is attached to the local ModDev mod below but is not part of Java's production jar,
// keeping temporary world presets and UI tags out of distributable Skyforge artifacts.
val development = sourceSets.create("development")

neoForge {
    version = "21.1.249"

    // SF-IMP-0033 promotes the adapter from a test-only exploded mod identity to the real
    // development mod boundary. Production code/resources remain in main. The additional
    // development source set contributes only local-run resources and is deliberately not packed
    // into the production jar.
    mods {
        create("skyforge") {
            sourceSet(sourceSets.main.get())
            sourceSet(development)
        }
    }

    runs {
        // Historical elevated-Massif structure-start visibility specimen.
        create("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0036").asFile
            systemProperty("skyforge.dev.specimen", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0046 uses its own game directory and a self-checking bowl specimen so manual
        // foundation evidence cannot be confused with the earlier naturally supported Massif.
        create("accommodationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0046").asFile
            systemProperty("skyforge.dev.accommodation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0050 reuses the isolated mansion/island fixture but adds development-only detached
        // underside geometry to the admission evidence stream. No synthetic piece is serialized.
        create("undersideContradictionClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0050").asFile
            systemProperty("skyforge.dev.undersideContradiction", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0052 proves that the base-world generation stream completes before Skyforge is
        // physically realized. The origin chunk is self-checking and the forced development mansion
        // should remain native-ground-owned beneath the floating Massif.
        create("domainIsolationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0052").asFile
            systemProperty("skyforge.dev.domainIsolation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0053 places the same native minecraft:oak_checked PlacedFeature on two independent
        // vertically aligned Skyforge volumes in the origin chunk. The runtime self-checks exact
        // surface ownership, independent operation seeds and successful bounded native writes.
        create("populationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0053").asFile
            systemProperty("skyforge.dev.population", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0054 resolves two different final-registry Minecraft biomes for vertically aligned
        // island domains and executes each biome's own VEGETAL_DECORATION feature list. Native
        // placement modifiers choose occurrence positions; no individual tree origin is hard-coded.
        create("biomePopulationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0054").asFile
            systemProperty("skyforge.dev.biomePopulation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0055 reuses the accepted stacked forest/taiga specimen through the reusable native
        // surface-population stage. The fixture immediately replays each volume/chunk/phase request
        // and fails unless the coordinator performs zero duplicate native feature executions.
        create("surfacePopulationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0055").asFile
            systemProperty("skyforge.dev.surfacePopulation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0056 proves whole-volume physical admission against the same forced native mansion
        // environment that exposed the original block-entity overwrite. A lower island must reject
        // without mutation while a clear multi-chunk upper island admits and catches up exactly.
        create("physicalAdmissionClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0056").asFile
            systemProperty("skyforge.dev.physicalAdmission", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0058 layers durable client-visible biome presentation onto the accepted 0056
        // admission specimen. The admitted upper island maps to taiga in Minecraft biome storage;
        // vertically unrelated native cells in the same X/Z column must remain unchanged.
        create("biomePresentationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0058").asFile
            systemProperty("skyforge.dev.physicalAdmission", "true")
            systemProperty("skyforge.dev.biomePresentation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0059 maps Minecraft's native UNDERGROUND_ORES height samples into the admitted
        // high-volume 0056/0058 fixture and proves optimized raw-section writes remain exact-volume
        // isolated from BASE_WORLD terrain.
        create("undergroundPlacementClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0059").asFile
            systemProperty("skyforge.dev.physicalAdmission", "true")
            systemProperty("skyforge.dev.biomePresentation", "true")
            systemProperty("skyforge.dev.undergroundPlacement", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0060 uses its own physically admitted high tableland. Its deterministic development
        // seed causes the unchanged final-registry amethyst-geode rarity gate to exercise owner-rich
        // terrain, proving LOCAL_MODIFICATIONS without coupling the fixture to SF-IMP-0056 state.
        create("localModificationsClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0060").asFile
            systemProperty("skyforge.dev.localModifications", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0061 proves the first exact-volume native AIR-carver execution against an
        // already-admitted high Skyforge tableland. HeightProvider RNG is consumed natively before
        // Y mapping; direct LevelChunk mutations are fenced to exact owner terrain.
        create("nativeCarverClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061").asFile
            systemProperty("skyforge.dev.nativeCarver", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0062 composes the accepted native-carver seam with final-registry
        // UNDERGROUND_DECORATION inside the same exact high tableland.
        create("undergroundDecorationClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0062").asFile
            systemProperty("skyforge.dev.undergroundDecoration", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Self-driving first-pass SF-IMP-0062 acceptance runs. Independent disposable worlds must
        // reproduce the cave/decorating decision stream before persistence/stacked gates are added.
        create("undergroundDecorationAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0062-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.undergroundDecoration", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0062-underground-decoration-a")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0062/decoration-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("undergroundDecorationAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0062-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.undergroundDecorationStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0062-underground-decoration-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0062/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("undergroundDecorationAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0062-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.undergroundDecoration", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0062-underground-decoration-b")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0062/decoration-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Reopen the deterministic B world's saved chunks without reinstalling any Skyforge
        // realization/population binding. Server and actual logical ClientLevel must both observe
        // the machine-selected native cave-decoration sample recorded by run B.
        create("undergroundDecorationAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0062-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.undergroundDecorationReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0062-underground-decoration-reload")
            systemProperty(
                "skyforge.dev.undergroundDecorationExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0062/decoration-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0062/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0063 native FLUID_SPRINGS proof. Each disposable server admits/carves the
        // same high tableland, runs final-registry springs, closes population scope, then allows
        // vanilla fluid ticks to propagate under persisted exact-volume provenance.
        create("fluidSpringsAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0063-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.fluidSprings", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0063-fluid-springs-a")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0063/fluid-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("fluidSpringsAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0063-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.fluidSprings", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0063-fluid-springs-b")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0063/fluid-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Reopen B through the actual client after a full server stop. This restores only
        // deterministic compiled terrain ownership so persisted SavedData provenance can fence a
        // fresh generated-fluid tick; it does not rerun admission, carving, decoration, or springs.
        create("fluidSpringsAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0063-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.fluidSpringsReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0063-fluid-springs-reload")
            systemProperty(
                "skyforge.dev.fluidSpringsExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0063/fluid-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0063/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Same-X/Z stacked exact volumes: persist a generated-fluid provenance entry independently
        // in each volume, then reopen its asynchronous propagation scope and explicitly reject the
        // other volume in both directions.
        create("fluidSpringsAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0063-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.fluidSpringsStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0063-fluid-springs-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0063/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0064 whole-footprint native LAKES proof. Independent disposable worlds must
        // reproduce the same admission decisions and native lake state before final reload/stacked
        // gates are added.
        create("nativeLakesAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0064-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeLakes", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0064-native-lakes-a")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0064/lakes-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeLakesAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0064-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeLakes", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0064-native-lakes-b")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0064/lakes-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Full-stop/client reload of the actual B lake world. Restore only the deterministic
        // native-surface ownership binding; no lake/admission rerun is installed.
        create("nativeLakesAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0064-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeLakesReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0064-native-lakes-reload")
            systemProperty(
                "skyforge.dev.nativeLakesExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0064/lakes-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0064/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeLakesAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0064-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeLakesStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0064-native-lakes-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0064/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0065 sealed authored-cave realization. The accepted isolated AUTH-0026
        // chamber is explicitly paired with an authoritative physical volume and centered near
        // spawn so the entire cave fits inside an already-loaded finite proof footprint.
        create("authoredCaveAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0065-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.authoredCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0065-authored-cave-a")
            systemProperty("skyforge.dev.acceptanceRadius", "3")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0065/cave-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("authoredCaveAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0065-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.authoredCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0065-authored-cave-b")
            systemProperty("skyforge.dev.acceptanceRadius", "3")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0065/cave-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("authoredCaveAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0065-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.authoredCaveReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0065-authored-cave-reload")
            systemProperty(
                "skyforge.dev.authoredCaveExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0065/cave-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0065/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("authoredCaveAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0065-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.authoredCaveStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0065-authored-cave-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0065/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0066 AUTH-0030 exterior-connected authored cave proof.
        create("exteriorConnectedCaveAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0066-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.exteriorConnectedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0066-exterior-cave-a")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0066/exterior-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("exteriorConnectedCaveAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0066-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.exteriorConnectedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0066-exterior-cave-b")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0066/exterior-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("exteriorConnectedCaveAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0066-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.exteriorConnectedCaveReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0066-exterior-cave-reload")
            systemProperty(
                "skyforge.dev.exteriorConnectedCaveExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0066/exterior-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0066/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("exteriorConnectedCaveAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0066-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.exteriorConnectedCaveStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0066-exterior-cave-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0066/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0067 native-first/authored-last composed cave proof.
        create("composedCaveAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0067-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.composedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0067-composed-cave-a")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0067/composed-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("composedCaveAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0067-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.composedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0067-composed-cave-b")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0067/composed-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("composedCaveAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0067-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.composedCaveReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0067-composed-cave-reload")
            systemProperty(
                "skyforge.dev.composedCaveExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0067/composed-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0067/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("composedCaveAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0067-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.composedCaveStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0067-composed-cave-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "6")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0067/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0068 production admitted-volume composed-cave lifecycle proof.
        create("productionComposedCaveAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0068-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionComposedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0068-production-composed-cave-a")
            systemProperty("skyforge.dev.acceptanceRadius", "10")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/production-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionComposedCaveAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0068-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionComposedCave", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0068-production-composed-cave-b")
            systemProperty("skyforge.dev.acceptanceRadius", "10")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/production-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionComposedCaveAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0068-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionComposedCaveReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0068-production-composed-cave-reload")
            systemProperty(
                "skyforge.dev.productionComposedCaveExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/production-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionComposedCaveAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0068-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionComposedCaveStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0068-production-composed-cave-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "10")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Same final-head native-carver proof in an independent game directory for deterministic
        // repeat evidence. This run must produce the same Skyforge transform/carve digests.
        create("nativeCarverRepeatClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-repeat").asFile
            systemProperty("skyforge.dev.nativeCarver", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Reopens the repeat world's saved chunks without reinstalling terrain/admission/carver
        // mutation. Server and logical client must both observe the persisted cave state.
        create("nativeCarverReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-repeat").asFile
            systemProperty("skyforge.dev.nativeCarverReload", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Final stacked-domain gate for the carver execution seam. The accepted two-volume fixture
        // maps one native Y sample independently and explicitly vetoes the other island in both
        // directions at the direct-carver write fence.
        create("nativeCarverStackedClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-stacked").asFile
            systemProperty("skyforge.dev.nativeCarverStacked", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Self-running SF-IMP-0061 acceptance harness. Dedicated-server cases create/load their
        // disposable worlds without a player, warm only the finite proof footprint, emit machine-readable
        // evidence, and stop themselves. One quick-play client reopens the saved repeat world automatically
        // to verify actual ClientLevel persistence, then closes itself.
        create("nativeCarverAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeCarver", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0061-carver-a")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/carver-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeCarverAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeCarver", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0061-carver-b")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/carver-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeCarverAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeCarverReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0061-reload-client")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeCarverAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.nativeCarverStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0061-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeCarverAcceptanceOreRegression") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-0059").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.physicalAdmission", "true")
            systemProperty("skyforge.dev.biomePresentation", "true")
            systemProperty("skyforge.dev.undergroundPlacement", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0059-regression")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/ore-regression.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("nativeCarverAcceptanceLocalModificationRegression") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0061-auto-0060").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.localModifications", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0060-regression")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0061/local-modification-regression.properties")
                    .get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Final SF-IMP-0060 stacked-domain gate. Reuse the accepted 0054 vertically aligned
        // forest/taiga tablelands, map the same LOCAL_MODIFICATIONS height sample independently
        // into each exact solid owner column, and explicitly reject the other island at preflight.
        create("localModificationsStackedClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0060-stacked").asFile
            systemProperty("skyforge.dev.localModificationsStacked", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Final SF-IMP-0059 stacked-domain gate. Reuse the accepted 0054 forest/taiga tablelands,
        // execute UNDERGROUND_ORES independently in both exact Y frames, preserve the other island
        // byte-for-byte during each replay and explicitly reject its solid coordinates at preflight.
        create("undergroundPlacementStackedClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0059-stacked").asFile
            systemProperty("skyforge.dev.undergroundPlacement", "true")
            systemProperty("skyforge.dev.undergroundPlacementStacked", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }
    }

    unitTest {
        enable()
        testedMod.set(mods.named("skyforge"))
    }
}

val sfImp0061AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0061")
val sfImp0061AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600061
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=3
    simulation-distance=3
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0061AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0061AcceptanceServerProperties)
}

val sfImp0061FreshServerRuns = mapOf(
    "runNativeCarverAcceptanceA" to "run-sf-imp-0061-auto-a",
    "runNativeCarverAcceptanceB" to "run-sf-imp-0061-auto-b",
    "runNativeCarverAcceptanceStacked" to "run-sf-imp-0061-auto-stacked",
    "runNativeCarverAcceptanceOreRegression" to "run-sf-imp-0061-auto-0059",
    "runNativeCarverAcceptanceLocalModificationRegression" to "run-sf-imp-0061-auto-0060",
)

sfImp0061FreshServerRuns.forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0061AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runNativeCarverAcceptanceA").configure {
    doFirst {
        delete(sfImp0061AcceptanceResultDirectory)
    }
}
tasks.named("runNativeCarverAcceptanceB").configure {
    mustRunAfter("runNativeCarverAcceptanceA")
}
tasks.named("runNativeCarverAcceptanceReloadClient").configure {
    mustRunAfter("runNativeCarverAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0061-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runNativeCarverAcceptanceStacked").configure {
    mustRunAfter("runNativeCarverAcceptanceReloadClient")
}
tasks.named("runNativeCarverAcceptanceOreRegression").configure {
    mustRunAfter("runNativeCarverAcceptanceStacked")
}
tasks.named("runNativeCarverAcceptanceLocalModificationRegression").configure {
    mustRunAfter("runNativeCarverAcceptanceOreRegression")
}

fun verifySfImp0061AcceptanceResults() {
    fun loadResult(name: String): Properties {
        val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
        check(file.isFile) { "missing SF-IMP-0061 acceptance result: $file" }
        return Properties().also { properties ->
            file.inputStream().use(properties::load)
        }
    }

    fun requirePass(name: String): Properties {
        val properties = loadResult(name)
        check(properties.getProperty("status") == "PASS") {
            "$name did not report PASS: $properties"
        }
        return properties
    }

    val carverA = requirePass("carver-a")
    val carverB = requirePass("carver-b")
    val reload = requirePass("reload")
    val stacked = requirePass("stacked")
    val ore = requirePass("ore-regression")
    val localModification = requirePass("local-modification-regression")

    val carverATransform = carverA.getProperty("transformDigest")
    val carverACarve = carverA.getProperty("carveDigest")
    check(carverATransform == carverB.getProperty("transformDigest")) {
        "SF-IMP-0061 transform digest changed across identical automated runs"
    }
    check(carverACarve == carverB.getProperty("carveDigest")) {
        "SF-IMP-0061 carved-position digest changed across identical automated runs"
    }
    check(carverATransform == "e97b5e7ee026c422") {
        "SF-IMP-0061 transform digest regressed: $carverATransform"
    }
    check(carverACarve == "61f96a61f81c9b55") {
        "SF-IMP-0061 carved-position digest regressed: $carverACarve"
    }
    check(reload.getProperty("reloadServerPass") == "true"
            && reload.getProperty("reloadClientPass") == "true") {
        "SF-IMP-0061 save/reload did not pass on both server and logical client: $reload"
    }
    check(stacked.getProperty("foreignWriteRejected") == "true"
            && stacked.getProperty("ownerWriteAccepted") == "true"
            && stacked.getProperty("lowerMappedY") != stacked.getProperty("upperMappedY")) {
        "SF-IMP-0061 stacked exact-volume isolation failed: $stacked"
    }
    check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
            && ore.getProperty("mappedOutsideVolume") == "0"
            && ore.getProperty("baseColumnPreserved") == "true") {
        "SF-IMP-0059 regression gate failed: $ore"
    }
    check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
            && localModification.getProperty("mappedOutsideVolume") == "0"
            && localModification.getProperty("baseColumnsPreserved") == "true") {
        "SF-IMP-0060 regression gate failed: $localModification"
    }

    println(
        "SF-IMP-0061 AUTOMATED ACCEPTANCE PASS: "
            + "carverTransformDigest=$carverATransform, "
            + "carveDigest=$carverACarve, "
            + "reloadServerClient=true, stackedIsolation=true, "
            + "sfImp0059Digest=" + ore.getProperty("transformDigest") + ", "
            + "sfImp0060Digest=" + localModification.getProperty("transformDigest"),
    )
}

tasks.register("sfImp0061AcceptanceVerify") {
    group = "verification"
    description = "Verify machine-readable results from the self-driving SF-IMP-0061 runtime slate."
    doLast {
        verifySfImp0061AcceptanceResults()
    }
}

tasks.register("sfImp0061Acceptance") {
    group = "verification"
    description = "Run the complete self-driving SF-IMP-0061 Minecraft acceptance slate."
    dependsOn(
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceB",
        "runNativeCarverAcceptanceReloadClient",
        "runNativeCarverAcceptanceStacked",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0061AcceptanceVerify")
}


val sfImp0062AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0062")
val sfImp0062AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600062
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=3
    simulation-distance=3
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0062AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0062AcceptanceServerProperties)
}

mapOf(
    "runUndergroundDecorationAcceptanceA" to "run-sf-imp-0062-auto-a",
    "runUndergroundDecorationAcceptanceB" to "run-sf-imp-0062-auto-b",
    "runUndergroundDecorationAcceptanceStacked" to "run-sf-imp-0062-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0062AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runUndergroundDecorationAcceptanceA").configure {
    doFirst {
        delete(sfImp0062AcceptanceResultDirectory)
    }
}
tasks.named("runUndergroundDecorationAcceptanceB").configure {
    mustRunAfter("runUndergroundDecorationAcceptanceA")
}
tasks.named("runUndergroundDecorationAcceptanceReloadClient").configure {
    mustRunAfter("runUndergroundDecorationAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0062-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runUndergroundDecorationAcceptanceStacked").configure {
    mustRunAfter("runUndergroundDecorationAcceptanceReloadClient")
}

tasks.register("sfImp0062AcceptanceVerify") {
    group = "verification"
    description = "Verify the complete deterministic SF-IMP-0062 underground-decoration acceptance slate."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun loadRegression(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("decoration-a")
        val second = load("decoration-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val carver = loadRegression("carver-a")
        val ore = loadRegression("ore-regression")
        val localModification = loadRegression("local-modification-regression")

        for ((name, result) in listOf(
            "decoration-a" to first,
            "decoration-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        for (key in listOf("carveTransformDigest", "carveDigest", "decorationTransformDigest", "decorationDigest")) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0062 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }

        check(first.getProperty("carveTransformDigest") == "10d4f06df3d8814f") {
            "SF-IMP-0062 carver transform digest regressed: $first"
        }
        check(first.getProperty("carveDigest") == "9432af9ead2c865d") {
            "SF-IMP-0062 carved-position digest regressed: $first"
        }
        check(first.getProperty("decorationTransformDigest") == "1ed8887c547e0911") {
            "SF-IMP-0062 decoration transform digest regressed: $first"
        }
        check(first.getProperty("decorationDigest") == "ce242ec84fb8ccfc") {
            "SF-IMP-0062 decoration digest regressed: $first"
        }
        check(first.getProperty("successfulFeatures") == "33"
                && first.getProperty("changedCarvedAir") == "2031"
                && first.getProperty("mappedOutsideVolume") == "0"
                && first.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0062 native cave-decoration realization evidence regressed: $first"
        }
        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedDecorationPos") == first.getProperty("sampleDecorationPos")
                && reload.getProperty("persistedDecorationState") == first.getProperty("sampleDecorationState")) {
            "SF-IMP-0062 save/reload logical-client evidence failed: $reload"
        }
        check(stacked.getProperty("foreignWriteRejected") == "true"
                && stacked.getProperty("ownerWriteAccepted") == "true"
                && stacked.getProperty("lowerMappedY") == "124"
                && stacked.getProperty("upperMappedY") == "224") {
            "SF-IMP-0062 stacked exact-volume isolation failed: $stacked"
        }

        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0"
                && ore.getProperty("baseColumnPreserved") == "true") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0"
                && localModification.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }

        println(
            "SF-IMP-0062 AUTOMATED ACCEPTANCE PASS: "
                + "carveTransformDigest=" + first.getProperty("carveTransformDigest")
                + ", carveDigest=" + first.getProperty("carveDigest")
                + ", decorationTransformDigest=" + first.getProperty("decorationTransformDigest")
                + ", decorationDigest=" + first.getProperty("decorationDigest")
                + ", successfulFeatures=" + first.getProperty("successfulFeatures")
                + ", changedCarvedAir=" + first.getProperty("changedCarvedAir")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest")
                + ", sfImp0059Digest=" + ore.getProperty("transformDigest")
                + ", sfImp0060Digest=" + localModification.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0062Acceptance") {
    group = "verification"
    description = "Run the complete deterministic SF-IMP-0062 underground-decoration acceptance slate."
    dependsOn(
        "runUndergroundDecorationAcceptanceA",
        "runUndergroundDecorationAcceptanceB",
        "runUndergroundDecorationAcceptanceReloadClient",
        "runUndergroundDecorationAcceptanceStacked",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0062AcceptanceVerify")
}


val sfImp0063AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0063")
val sfImp0063AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600063
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=3
    simulation-distance=3
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0063AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0063AcceptanceServerProperties)
}

mapOf(
    "runFluidSpringsAcceptanceA" to "run-sf-imp-0063-auto-a",
    "runFluidSpringsAcceptanceB" to "run-sf-imp-0063-auto-b",
    "runFluidSpringsAcceptanceStacked" to "run-sf-imp-0063-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0063AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runFluidSpringsAcceptanceA").configure {
    doFirst {
        delete(sfImp0063AcceptanceResultDirectory)
    }
}
tasks.named("runFluidSpringsAcceptanceB").configure {
    mustRunAfter("runFluidSpringsAcceptanceA")
}
tasks.named("runFluidSpringsAcceptanceReloadClient").configure {
    mustRunAfter("runFluidSpringsAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0063-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runFluidSpringsAcceptanceStacked").configure {
    mustRunAfter("runFluidSpringsAcceptanceReloadClient")
}

tasks.register("sfImp0063AcceptanceVerify") {
    group = "verification"
    description = "Verify the complete deterministic SF-IMP-0063 native fluid-springs acceptance slate."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0063AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0063 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0062(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0061(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("fluid-a")
        val second = load("fluid-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val decoration = load0062("decoration-a")
        val carver = load0061("carver-a")
        val ore = load0061("ore-regression")
        val localModification = load0061("local-modification-regression")

        for ((name, result) in listOf(
            "fluid-a" to first,
            "fluid-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0062-decoration" to decoration,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        for (key in listOf("carveTransformDigest", "carveDigest", "springTransformDigest", "provenanceDigest")) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0063 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }

        check(first.getProperty("carveTransformDigest") == "10d4f06df3d8814f"
                && first.getProperty("carveDigest") == "9432af9ead2c865d") {
            "SF-IMP-0063 prerequisite carver invariants changed: $first"
        }
        check(first.getProperty("springTransformDigest") == "c8103b2012e79269"
                && first.getProperty("provenanceDigest") == "2aa9b41371236b93"
                && first.getProperty("successfulFeatures") == "7"
                && first.getProperty("initialTrackedFluids") == "9"
                && first.getProperty("finalTrackedFluids") == "138"
                && first.getProperty("matchingPersistentFluids") == "138"
                && first.getProperty("capturedSchedules") == "325"
                && first.getProperty("propagationTicks") == "256"
                && first.getProperty("hiddenBoundaryReads") == "1661"
                && first.getProperty("mappedOutsideVolume") == "0"
                && first.getProperty("ordinaryVanillaFluidFlowed") == "true"
                && first.getProperty("ordinaryVanillaFluidUntracked") == "true"
                && first.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0063 native spring/propagation invariants regressed: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedFluidPos") == first.getProperty("sampleFluidPos")
                && reload.getProperty("clientFluidPos") == first.getProperty("sampleFluidPos")
                && reload.getProperty("reloadPropagationTicks").toLong() > 0
                && reload.getProperty("persistedTrackedPositions").toInt() > 0) {
            "SF-IMP-0063 save/reload provenance or ClientLevel evidence failed: $reload"
        }

        check(stacked.getProperty("ownerPropagationAccepted") == "true"
                && stacked.getProperty("foreignPropagationRejected") == "true"
                && stacked.getProperty("provenanceVolumeIsolation") == "true"
                && stacked.getProperty("lowerMappedY") == "124"
                && stacked.getProperty("upperMappedY") == "224") {
            "SF-IMP-0063 stacked generated-fluid isolation failed: $stacked"
        }

        check(decoration.getProperty("carveTransformDigest") == "10d4f06df3d8814f"
                && decoration.getProperty("carveDigest") == "9432af9ead2c865d"
                && decoration.getProperty("decorationTransformDigest") == "1ed8887c547e0911"
                && decoration.getProperty("decorationDigest") == "ce242ec84fb8ccfc"
                && decoration.getProperty("successfulFeatures") == "33"
                && decoration.getProperty("changedCarvedAir") == "2031"
                && decoration.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0062 regression gate failed: $decoration"
        }

        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55"
                && carver.getProperty("mappedOutsideTarget") == "0"
                && carver.getProperty("baseColumnPreserved") == "true") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0"
                && ore.getProperty("baseColumnPreserved") == "true") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0"
                && localModification.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }

        println(
            "SF-IMP-0063 AUTOMATED ACCEPTANCE PASS: "
                + "springTransformDigest=" + first.getProperty("springTransformDigest")
                + ", provenanceDigest=" + first.getProperty("provenanceDigest")
                + ", propagationTicks=" + first.getProperty("propagationTicks")
                + ", finalTrackedFluids=" + first.getProperty("finalTrackedFluids")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0062DecorationDigest=" + decoration.getProperty("decorationDigest")
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest")
                + ", sfImp0059Digest=" + ore.getProperty("transformDigest")
                + ", sfImp0060Digest=" + localModification.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0063Acceptance") {
    group = "verification"
    description = "Run the complete deterministic SF-IMP-0063 fluid-springs acceptance slate."
    dependsOn(
        "runFluidSpringsAcceptanceA",
        "runFluidSpringsAcceptanceB",
        "runFluidSpringsAcceptanceReloadClient",
        "runFluidSpringsAcceptanceStacked",
        "runUndergroundDecorationAcceptanceA",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0063AcceptanceVerify")
}

val sfImp0064AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0064")
val sfImp0064AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600064
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=3
    simulation-distance=3
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0064AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0064AcceptanceServerProperties)
}

mapOf(
    "runNativeLakesAcceptanceA" to "run-sf-imp-0064-auto-a",
    "runNativeLakesAcceptanceB" to "run-sf-imp-0064-auto-b",
    "runNativeLakesAcceptanceStacked" to "run-sf-imp-0064-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0064AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runNativeLakesAcceptanceA").configure {
    doFirst {
        delete(sfImp0064AcceptanceResultDirectory)
    }
}
tasks.named("runNativeLakesAcceptanceB").configure {
    mustRunAfter("runNativeLakesAcceptanceA")
}
tasks.named("runNativeLakesAcceptanceReloadClient").configure {
    mustRunAfter("runNativeLakesAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0064-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runNativeLakesAcceptanceStacked").configure {
    mustRunAfter("runNativeLakesAcceptanceReloadClient")
}

tasks.register("sfImp0064AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic first-pass SF-IMP-0064 whole-lake acceptance evidence."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0064AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0064 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0063(name: String): Properties {
            val file = sfImp0063AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0063 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0062(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0061(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("lakes-a")
        val second = load("lakes-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val springs = load0063("fluid-a")
        val decoration = load0062("decoration-a")
        val carver = load0061("carver-a")
        val ore = load0061("ore-regression")
        val localModification = load0061("local-modification-regression")

        for ((name, result) in listOf(
            "lakes-a" to first,
            "lakes-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0063-springs" to springs,
            "sf-imp-0062-decoration" to decoration,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }
        for (key in listOf("admissionDigest", "transformDigest", "provenanceDigest")) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0064 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }
        check(first.getProperty("successfulFeatures").toInt() > 0
                && first.getProperty("admittedConfiguredLakes").toInt() > 0
                && first.getProperty("rejectionProbeRejected") == "true"
                && first.getProperty("rejectionProbeChangedBlocks") == "0"
                && first.getProperty("changedRejectedOnly") == "0"
                && first.getProperty("unsupportedLakeFeatures") == "0"
                && first.getProperty("mappedOutsideVolume") == "0"
                && first.getProperty("propagationTicks").toLong() > 0
                && first.getProperty("matchingPersistentFluids").toInt() > 0
                && first.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0064 first-pass lake evidence is incomplete: $first"
        }

        check(first.getProperty("admissionDigest") == "9b568d83c71c5d04"
                && first.getProperty("transformDigest") == "13c87b04bebea8ea"
                && first.getProperty("provenanceDigest") == "f35dcb47fa1a38ef"
                && first.getProperty("attemptedFeatures") == "42"
                && first.getProperty("successfulFeatures") == "1"
                && first.getProperty("configuredLakeAttempts") == "1"
                && first.getProperty("admittedConfiguredLakes") == "1"
                && first.getProperty("placementChangedBlocks") == "340"
                && first.getProperty("finalTrackedLakeFluids") == "56"
                && first.getProperty("matchingPersistentFluids") == "56"
                && first.getProperty("propagationTicks") == "56") {
            "SF-IMP-0064 stable native-lake invariants changed: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedFluidPos") == first.getProperty("sampleFluidPos")
                && reload.getProperty("clientFluidPos") == first.getProperty("sampleFluidPos")
                && reload.getProperty("reloadPropagationTicks").toLong() > 0
                && reload.getProperty("persistedTrackedPositions").toInt() > 0) {
            "SF-IMP-0064 lake save/reload provenance or ClientLevel evidence failed: $reload"
        }

        check(stacked.getProperty("ownerWholeFootprintAccepted") == "true"
                && stacked.getProperty("foreignWholeFootprintRejected") == "true"
                && stacked.getProperty("provenanceVolumeIsolation") == "true"
                && stacked.getProperty("sameXZIndependent") == "true"
                && stacked.getProperty("lowerOriginY") != stacked.getProperty("upperOriginY")) {
            "SF-IMP-0064 stacked whole-lake isolation failed: $stacked"
        }

        check(springs.getProperty("springTransformDigest") == "c8103b2012e79269"
                && springs.getProperty("provenanceDigest") == "2aa9b41371236b93"
                && springs.getProperty("successfulFeatures") == "7"
                && springs.getProperty("mappedOutsideVolume") == "0"
                && springs.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0063 regression gate failed: $springs"
        }
        check(decoration.getProperty("decorationDigest") == "ce242ec84fb8ccfc"
                && decoration.getProperty("successfulFeatures") == "33"
                && decoration.getProperty("changedCarvedAir") == "2031"
                && decoration.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0062 regression gate failed: $decoration"
        }
        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55"
                && carver.getProperty("mappedOutsideTarget") == "0"
                && carver.getProperty("baseColumnPreserved") == "true") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0"
                && ore.getProperty("baseColumnPreserved") == "true") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0"
                && localModification.getProperty("baseColumnsPreserved") == "true") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }
        println(
            "SF-IMP-0064 AUTOMATED ACCEPTANCE PASS: admissionDigest="
                + first.getProperty("admissionDigest")
                + ", transformDigest=" + first.getProperty("transformDigest")
                + ", provenanceDigest=" + first.getProperty("provenanceDigest")
                + ", admitted=" + first.getProperty("admittedConfiguredLakes")
                + ", rejected=" + first.getProperty("rejectedConfiguredLakes")
                + ", changed=" + first.getProperty("placementChangedBlocks")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0063Digest=" + springs.getProperty("springTransformDigest")
                + ", sfImp0062Digest=" + decoration.getProperty("decorationDigest")
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest")
                + ", sfImp0059Digest=" + ore.getProperty("transformDigest")
                + ", sfImp0060Digest=" + localModification.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0064Acceptance") {
    group = "verification"
    description = "Run complete deterministic SF-IMP-0064 native lakes acceptance."
    dependsOn(
        "runNativeLakesAcceptanceA",
        "runNativeLakesAcceptanceB",
        "runNativeLakesAcceptanceReloadClient",
        "runNativeLakesAcceptanceStacked",
        "runFluidSpringsAcceptanceA",
        "runUndergroundDecorationAcceptanceA",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0064AcceptanceVerify")
}

val sfImp0065AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0065")
val sfImp0065AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600065
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=4
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0065AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0065AcceptanceServerProperties)
}

mapOf(
    "runAuthoredCaveAcceptanceA" to "run-sf-imp-0065-auto-a",
    "runAuthoredCaveAcceptanceB" to "run-sf-imp-0065-auto-b",
    "runAuthoredCaveAcceptanceStacked" to "run-sf-imp-0065-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0065AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runAuthoredCaveAcceptanceA").configure {
    doFirst {
        delete(sfImp0065AcceptanceResultDirectory)
    }
}
tasks.named("runAuthoredCaveAcceptanceB").configure {
    mustRunAfter("runAuthoredCaveAcceptanceA")
}
tasks.named("runAuthoredCaveAcceptanceReloadClient").configure {
    mustRunAfter("runAuthoredCaveAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0065-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runAuthoredCaveAcceptanceStacked").configure {
    mustRunAfter("runAuthoredCaveAcceptanceReloadClient")
}

tasks.register("sfImp0065AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic first-pass SF-IMP-0065 authored-cave realization evidence."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0065AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0065 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0064(name: String): Properties {
            val file = sfImp0064AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0064 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0063(name: String): Properties {
            val file = sfImp0063AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0063 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0062(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0061(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("cave-a")
        val second = load("cave-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val lakes = load0064("lakes-a")
        val springs = load0063("fluid-a")
        val decoration = load0062("decoration-a")
        val carver = load0061("carver-a")
        val ore = load0061("ore-regression")
        val localModification = load0061("local-modification-regression")

        for ((name, result) in listOf(
            "cave-a" to first,
            "cave-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0064-lakes" to lakes,
            "sf-imp-0063-springs" to springs,
            "sf-imp-0062-decoration" to decoration,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        check(first.getProperty("status") == "PASS" && second.getProperty("status") == "PASS") {
            "SF-IMP-0065 authored-cave repeat did not report PASS: A=$first B=$second"
        }
        for (key in listOf("changedDigest", "provenanceDigest")) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0065 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }
        check(first.getProperty("islandKey") == "1439"
                && first.getProperty("positiveAuthoredSamples") == "27379"
                && first.getProperty("ownerAuthorizedSamples") == "27379"
                && first.getProperty("changedBlocks") == "27379"
                && first.getProperty("unsafePositiveSamples") == "0"
                && first.getProperty("changedDigest") == "5e80ba344cffe29"
                && first.getProperty("provenanceDigest") == "eabea7e356033e45"
                && first.getProperty("sealed") == "true"
                && first.getProperty("baseWorldPreserved") == "true"
                && first.getProperty("sampleCaveState") == "Block{minecraft:air}"
                && first.getProperty("sampleSystemId") == "0"
                && first.getProperty("samplePrimitiveKind") == "CHAMBER"
                && first.getProperty("samplePrimitiveId") == "0") {
            "SF-IMP-0065 stable authored-cave evidence changed: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedCavePos") == first.getProperty("sampleCavePos")
                && reload.getProperty("clientCavePos") == first.getProperty("sampleCavePos")
                && reload.getProperty("persistedCaveState") == "Block{minecraft:air}"
                && reload.getProperty("clientCaveState") == "Block{minecraft:air}"
                && reload.getProperty("persistedSolidControlState")
                    == first.getProperty("solidControlState")) {
            "SF-IMP-0065 save/reload or ClientLevel persistence failed: $reload"
        }

        check(stacked.getProperty("sameXZIndependent") == "true"
                && stacked.getProperty("foreignVolumePreserved") == "true"
                && stacked.getProperty("unsafeLower") == "0"
                && stacked.getProperty("unsafeUpper") == "0"
                && stacked.getProperty("lowerChanged").toInt() > 0
                && stacked.getProperty("upperChanged").toInt() > 0
                && stacked.getProperty("lowerCenterY") != stacked.getProperty("upperCenterY")) {
            "SF-IMP-0065 stacked authored-cave isolation failed: $stacked"
        }

        check(lakes.getProperty("admissionDigest") == "9b568d83c71c5d04"
                && lakes.getProperty("transformDigest") == "13c87b04bebea8ea"
                && lakes.getProperty("provenanceDigest") == "f35dcb47fa1a38ef"
                && lakes.getProperty("placementChangedBlocks") == "340"
                && lakes.getProperty("changedRejectedOnly") == "0") {
            "SF-IMP-0064 regression gate failed: $lakes"
        }
        check(springs.getProperty("springTransformDigest") == "c8103b2012e79269"
                && springs.getProperty("provenanceDigest") == "2aa9b41371236b93"
                && springs.getProperty("successfulFeatures") == "7"
                && springs.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0063 regression gate failed: $springs"
        }
        check(decoration.getProperty("decorationDigest") == "ce242ec84fb8ccfc"
                && decoration.getProperty("successfulFeatures") == "33"
                && decoration.getProperty("changedCarvedAir") == "2031"
                && decoration.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0062 regression gate failed: $decoration"
        }
        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55"
                && carver.getProperty("mappedOutsideTarget") == "0") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }

        println(
            "SF-IMP-0065 AUTOMATED ACCEPTANCE PASS: changedDigest="
                + first.getProperty("changedDigest")
                + ", provenanceDigest=" + first.getProperty("provenanceDigest")
                + ", changedBlocks=" + first.getProperty("changedBlocks")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0064Digest=" + lakes.getProperty("admissionDigest")
                + ", sfImp0063Digest=" + springs.getProperty("springTransformDigest")
                + ", sfImp0062Digest=" + decoration.getProperty("decorationDigest")
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest")
                + ", sfImp0059Digest=" + ore.getProperty("transformDigest")
                + ", sfImp0060Digest=" + localModification.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0065Acceptance") {
    group = "verification"
    description = "Run complete deterministic SF-IMP-0065 authored-cave acceptance."
    dependsOn(
        "runAuthoredCaveAcceptanceA",
        "runAuthoredCaveAcceptanceB",
        "runAuthoredCaveAcceptanceReloadClient",
        "runAuthoredCaveAcceptanceStacked",
        "runNativeLakesAcceptanceA",
        "runFluidSpringsAcceptanceA",
        "runUndergroundDecorationAcceptanceA",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0065AcceptanceVerify")
}

val sfImp0066AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0066")
val sfImp0066AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600066
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=7
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0066AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0066AcceptanceServerProperties)
}

mapOf(
    "runExteriorConnectedCaveAcceptanceA" to "run-sf-imp-0066-auto-a",
    "runExteriorConnectedCaveAcceptanceB" to "run-sf-imp-0066-auto-b",
    "runExteriorConnectedCaveAcceptanceStacked" to "run-sf-imp-0066-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0066AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runExteriorConnectedCaveAcceptanceA").configure {
    doFirst {
        delete(sfImp0066AcceptanceResultDirectory)
    }
}
tasks.named("runExteriorConnectedCaveAcceptanceB").configure {
    mustRunAfter("runExteriorConnectedCaveAcceptanceA")
}
tasks.named("runExteriorConnectedCaveAcceptanceReloadClient").configure {
    mustRunAfter("runExteriorConnectedCaveAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0066-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runExteriorConnectedCaveAcceptanceStacked").configure {
    mustRunAfter("runExteriorConnectedCaveAcceptanceReloadClient")
}

tasks.register("sfImp0066AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic first-pass SF-IMP-0066 exterior-connected cave evidence."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0066AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0066 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0065(name: String): Properties {
            val file = sfImp0065AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0065 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0064(name: String): Properties {
            val file = sfImp0064AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0064 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0063(name: String): Properties {
            val file = sfImp0063AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0063 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0062(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0061(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("exterior-a")
        val second = load("exterior-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val authored = load0065("cave-a")
        val lakes = load0064("lakes-a")
        val springs = load0063("fluid-a")
        val decoration = load0062("decoration-a")
        val carver = load0061("carver-a")
        val ore = load0061("ore-regression")
        val localModification = load0061("local-modification-regression")

        for ((name, result) in listOf(
            "exterior-a" to first,
            "exterior-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0065-authored-cave" to authored,
            "sf-imp-0064-lakes" to lakes,
            "sf-imp-0063-springs" to springs,
            "sf-imp-0062-decoration" to decoration,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        check(first.getProperty("status") == "PASS" && second.getProperty("status") == "PASS") {
            "SF-IMP-0066 runtime repeat did not report PASS: A=$first B=$second"
        }
        for (key in listOf("changedDigest", "provenanceDigest")) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0066 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }
        check(first.getProperty("islandKey") == "653"
                && first.getProperty("exposureSide") == "UNDERSIDE"
                && first.getProperty("proofChunks") == "16"
                && first.getProperty("positiveSamples") == "89068"
                && first.getProperty("basePositiveSamples") == "78030"
                && first.getProperty("exposurePositiveSamples") == "11038"
                && first.getProperty("upperExposurePositiveSamples") == "0"
                && first.getProperty("undersideExposurePositiveSamples") == "11038"
                && first.getProperty("unsafePositiveSamples") == "0"
                && first.getProperty("mouthCells") == "663"
                && first.getProperty("changedBlocks") == "89068"
                && first.getProperty("changedDigest") == "f97a685cce4bd5e4"
                && first.getProperty("provenanceDigest") == "3032a41620c93935"
                && first.getProperty("mouthState") == "Block{minecraft:air}"
                && first.getProperty("outwardState") == "Block{minecraft:air}"
                && first.getProperty("baseCaveState") == "Block{minecraft:air}"
                && first.getProperty("componentReachedBase") == "true"
                && first.getProperty("exactOwnerOnly") == "true") {
            "SF-IMP-0066 stable exterior-cave evidence changed: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedMouthPos") == first.getProperty("mouthPos")
                && reload.getProperty("clientMouthPos") == first.getProperty("mouthPos")
                && reload.getProperty("persistedMouthState") == "Block{minecraft:air}"
                && reload.getProperty("clientMouthState") == "Block{minecraft:air}"
                && reload.getProperty("persistedOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("clientOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("persistedBaseCaveState") == "Block{minecraft:air}"
                && reload.getProperty("clientBaseCaveState") == "Block{minecraft:air}") {
            "SF-IMP-0066 save/reload or ClientLevel persistence failed: $reload"
        }

        check(stacked.getProperty("sameXZIndependent") == "true"
                && stacked.getProperty("foreignVolumePreserved") == "true"
                && stacked.getProperty("unsafeLower") == "0"
                && stacked.getProperty("unsafeUpper") == "0"
                && stacked.getProperty("lowerChanged").toInt() > 0
                && stacked.getProperty("upperChanged").toInt() > 0
                && stacked.getProperty("lowerExposure").toInt() > 0
                && stacked.getProperty("upperExposure").toInt() > 0
                && stacked.getProperty("lowerMouthY") != stacked.getProperty("upperMouthY")) {
            "SF-IMP-0066 stacked exterior-cave isolation failed: $stacked"
        }

        check(authored.getProperty("changedDigest") == "5e80ba344cffe29"
                && authored.getProperty("provenanceDigest") == "eabea7e356033e45"
                && authored.getProperty("changedBlocks") == "27379"
                && authored.getProperty("unsafePositiveSamples") == "0"
                && authored.getProperty("sealed") == "true") {
            "SF-IMP-0065 regression gate failed: $authored"
        }
        check(lakes.getProperty("admissionDigest") == "9b568d83c71c5d04"
                && lakes.getProperty("transformDigest") == "13c87b04bebea8ea"
                && lakes.getProperty("provenanceDigest") == "f35dcb47fa1a38ef"
                && lakes.getProperty("placementChangedBlocks") == "340"
                && lakes.getProperty("changedRejectedOnly") == "0") {
            "SF-IMP-0064 regression gate failed: $lakes"
        }
        check(springs.getProperty("springTransformDigest") == "c8103b2012e79269"
                && springs.getProperty("provenanceDigest") == "2aa9b41371236b93"
                && springs.getProperty("successfulFeatures") == "7"
                && springs.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0063 regression gate failed: $springs"
        }
        check(decoration.getProperty("decorationDigest") == "ce242ec84fb8ccfc"
                && decoration.getProperty("successfulFeatures") == "33"
                && decoration.getProperty("changedCarvedAir") == "2031"
                && decoration.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0062 regression gate failed: $decoration"
        }
        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55"
                && carver.getProperty("mappedOutsideTarget") == "0") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }

        println(
            "SF-IMP-0066 AUTOMATED ACCEPTANCE PASS: changedDigest="
                + first.getProperty("changedDigest")
                + ", provenanceDigest=" + first.getProperty("provenanceDigest")
                + ", mouthCells=" + first.getProperty("mouthCells")
                + ", changedBlocks=" + first.getProperty("changedBlocks")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0065Digest=" + authored.getProperty("changedDigest")
                + ", sfImp0064Digest=" + lakes.getProperty("admissionDigest")
                + ", sfImp0063Digest=" + springs.getProperty("springTransformDigest")
                + ", sfImp0062Digest=" + decoration.getProperty("decorationDigest")
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest")
                + ", sfImp0059Digest=" + ore.getProperty("transformDigest")
                + ", sfImp0060Digest=" + localModification.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0066Acceptance") {
    group = "verification"
    description = "Run complete deterministic SF-IMP-0066 exterior cave acceptance."
    dependsOn(
        "runExteriorConnectedCaveAcceptanceA",
        "runExteriorConnectedCaveAcceptanceB",
        "runExteriorConnectedCaveAcceptanceReloadClient",
        "runExteriorConnectedCaveAcceptanceStacked",
        "runAuthoredCaveAcceptanceA",
        "runNativeLakesAcceptanceA",
        "runFluidSpringsAcceptanceA",
        "runUndergroundDecorationAcceptanceA",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0066AcceptanceVerify")
}

val sfImp0067AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0067")
val sfImp0067AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600067
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=7
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0067AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0067AcceptanceServerProperties)
}

mapOf(
    "runComposedCaveAcceptanceA" to "run-sf-imp-0067-auto-a",
    "runComposedCaveAcceptanceB" to "run-sf-imp-0067-auto-b",
    "runComposedCaveAcceptanceStacked" to "run-sf-imp-0067-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0067AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runComposedCaveAcceptanceA").configure {
    doFirst {
        delete(sfImp0067AcceptanceResultDirectory)
    }
}
tasks.named("runComposedCaveAcceptanceB").configure {
    mustRunAfter("runComposedCaveAcceptanceA")
}
tasks.named("runComposedCaveAcceptanceReloadClient").configure {
    mustRunAfter("runComposedCaveAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0067-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runComposedCaveAcceptanceStacked").configure {
    mustRunAfter("runComposedCaveAcceptanceReloadClient")
}

tasks.register("sfImp0067AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic first-pass SF-IMP-0067 native/authored cave union evidence."
    doLast {
        fun load(name: String): Properties {
            val file = sfImp0067AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0067 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        fun load0066(name: String): Properties {
            val file = sfImp0066AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0066 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0065(name: String): Properties {
            val file = sfImp0065AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0065 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0064(name: String): Properties {
            val file = sfImp0064AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0064 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0063(name: String): Properties {
            val file = sfImp0063AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0063 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0062(name: String): Properties {
            val file = sfImp0062AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0062 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0061(name: String): Properties {
            val file = sfImp0061AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing inherited regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load("composed-a")
        val second = load("composed-b")
        val reload = load("reload")
        val stacked = load("stacked")
        val exterior = load0066("exterior-a")
        val authored = load0065("cave-a")
        val lakes = load0064("lakes-a")
        val springs = load0063("fluid-a")
        val decoration = load0062("decoration-a")
        val carver = load0061("carver-a")
        val ore = load0061("ore-regression")
        val localModification = load0061("local-modification-regression")

        for ((name, result) in listOf(
            "composed-a" to first,
            "composed-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0066-exterior" to exterior,
            "sf-imp-0065-authored" to authored,
            "sf-imp-0064-lakes" to lakes,
            "sf-imp-0063-springs" to springs,
            "sf-imp-0062-decoration" to decoration,
            "sf-imp-0061-carver" to carver,
            "sf-imp-0059-ore" to ore,
            "sf-imp-0060-local-modification" to localModification,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        check(first.getProperty("status") == "PASS" && second.getProperty("status") == "PASS") {
            "SF-IMP-0067 composed-cave repeat did not report PASS: A=$first B=$second"
        }
        for (key in listOf(
            "nativeTransformDigest",
            "nativeCarveDigest",
            "authoredChangedDigest",
            "authoredProvenanceDigest",
            "composedDigest",
        )) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0067 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }
        check(first.getProperty("islandKey") == "653"
                && first.getProperty("nativeBiome") == "minecraft:taiga"
                && first.getProperty("composedAttempts") == "1"
                && first.getProperty("selectedNativeChunk") == "-4294967298"
                && first.getProperty("nativeChangedBlocks") == "1400"
                && first.getProperty("nativeSuccessfulCalls") == "73"
                && first.getProperty("nativeOnlyAir") == "584"
                && first.getProperty("nativeAuthoredAirOverlap") == "582"
                && first.getProperty("nativeRejectedWrites") == "0"
                && first.getProperty("nativeMappedOutsideTarget") == "0"
                && first.getProperty("nativeTransformDigest") == "95c046280c7f1c11"
                && first.getProperty("nativeCarveDigest") == "c277e3af5030dd01"
                && first.getProperty("authoredPositive") == "89068"
                && first.getProperty("authoredBasePositive") == "78030"
                && first.getProperty("authoredExposurePositive") == "11038"
                && first.getProperty("authoredUnsafe") == "0"
                && first.getProperty("authoredChangedBlocks") == "88486"
                && first.getProperty("authoredChangedDigest") == "6d2120967a6c73bd"
                && first.getProperty("authoredProvenanceDigest") == "3032a41620c93935"
                && first.getProperty("finalAuthoredAir") == "89068"
                && first.getProperty("composedDigest") == "911b02f4fe5b0518"
                && first.getProperty("baseWorldPreserved") == "true"
                && first.getProperty("finalUnion") == "true") {
            "SF-IMP-0067 stable composed-cave evidence changed: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("persistedNativeOnlyPos") == first.getProperty("nativeOnlyPos")
                && reload.getProperty("clientNativeOnlyPos") == first.getProperty("nativeOnlyPos")
                && reload.getProperty("persistedNativeOnlyState") == "Block{minecraft:air}"
                && reload.getProperty("clientNativeOnlyState") == "Block{minecraft:air}"
                && reload.getProperty("persistedMouthState") == "Block{minecraft:air}"
                && reload.getProperty("clientMouthState") == "Block{minecraft:air}"
                && reload.getProperty("persistedOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("clientOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("persistedBaseState") == "Block{minecraft:air}"
                && reload.getProperty("clientBaseState") == "Block{minecraft:air}") {
            "SF-IMP-0067 save/reload or ClientLevel union persistence failed: $reload"
        }

        check(stacked.getProperty("sameXZIndependent") == "true"
                && stacked.getProperty("foreignVolumePreserved") == "true"
                && stacked.getProperty("unsafeLower") == "0"
                && stacked.getProperty("unsafeUpper") == "0"
                && stacked.getProperty("lowerNativeChanged").toInt() > 0
                && stacked.getProperty("upperNativeChanged").toInt() > 0
                && stacked.getProperty("lowerNativeOnlyAir").toInt() > 0
                && stacked.getProperty("upperNativeOnlyAir").toInt() > 0
                && stacked.getProperty("lowerAuthoredPositive").toInt() > 0
                && stacked.getProperty("upperAuthoredPositive").toInt() > 0
                && stacked.getProperty("lowerAnchorY") != stacked.getProperty("upperAnchorY")) {
            "SF-IMP-0067 stacked union isolation failed: $stacked"
        }

        check(exterior.getProperty("changedDigest") == "f97a685cce4bd5e4"
                && exterior.getProperty("provenanceDigest") == "3032a41620c93935"
                && exterior.getProperty("changedBlocks") == "89068"
                && exterior.getProperty("unsafePositiveSamples") == "0") {
            "SF-IMP-0066 regression gate failed: $exterior"
        }
        check(authored.getProperty("changedDigest") == "5e80ba344cffe29"
                && authored.getProperty("provenanceDigest") == "eabea7e356033e45"
                && authored.getProperty("changedBlocks") == "27379"
                && authored.getProperty("unsafePositiveSamples") == "0") {
            "SF-IMP-0065 regression gate failed: $authored"
        }
        check(lakes.getProperty("admissionDigest") == "9b568d83c71c5d04"
                && lakes.getProperty("placementChangedBlocks") == "340") {
            "SF-IMP-0064 regression gate failed: $lakes"
        }
        check(springs.getProperty("springTransformDigest") == "c8103b2012e79269"
                && springs.getProperty("successfulFeatures") == "7") {
            "SF-IMP-0063 regression gate failed: $springs"
        }
        check(decoration.getProperty("decorationDigest") == "ce242ec84fb8ccfc"
                && decoration.getProperty("changedCarvedAir") == "2031") {
            "SF-IMP-0062 regression gate failed: $decoration"
        }
        check(carver.getProperty("transformDigest") == "e97b5e7ee026c422"
                && carver.getProperty("carveDigest") == "61f96a61f81c9b55"
                && carver.getProperty("mappedOutsideTarget") == "0") {
            "SF-IMP-0061 regression gate failed: $carver"
        }
        check(ore.getProperty("transformDigest") == "3397c516a115d6e4"
                && ore.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0059 regression gate failed: $ore"
        }
        check(localModification.getProperty("transformDigest") == "4fe92d09d07f8002"
                && localModification.getProperty("mappedOutsideVolume") == "0") {
            "SF-IMP-0060 regression gate failed: $localModification"
        }

        println(
            "SF-IMP-0067 AUTOMATED ACCEPTANCE PASS: nativeTransformDigest="
                + first.getProperty("nativeTransformDigest")
                + ", nativeCarveDigest=" + first.getProperty("nativeCarveDigest")
                + ", authoredChangedDigest=" + first.getProperty("authoredChangedDigest")
                + ", authoredProvenanceDigest=" + first.getProperty("authoredProvenanceDigest")
                + ", composedDigest=" + first.getProperty("composedDigest")
                + ", nativeOnlyAir=" + first.getProperty("nativeOnlyAir")
                + ", nativeAuthoredAirOverlap=" + first.getProperty("nativeAuthoredAirOverlap")
                + ", reloadServerClient=true, stackedIsolation=true"
                + ", sfImp0066Digest=" + exterior.getProperty("changedDigest")
                + ", sfImp0061Digest=" + carver.getProperty("transformDigest"),
        )
    }
}

tasks.register("sfImp0067Acceptance") {
    group = "verification"
    description = "Run complete deterministic SF-IMP-0067 native/authored cave union acceptance."
    dependsOn(
        "runComposedCaveAcceptanceA",
        "runComposedCaveAcceptanceB",
        "runComposedCaveAcceptanceReloadClient",
        "runComposedCaveAcceptanceStacked",
        "runExteriorConnectedCaveAcceptanceA",
        "runAuthoredCaveAcceptanceA",
        "runNativeLakesAcceptanceA",
        "runFluidSpringsAcceptanceA",
        "runUndergroundDecorationAcceptanceA",
        "runNativeCarverAcceptanceA",
        "runNativeCarverAcceptanceOreRegression",
        "runNativeCarverAcceptanceLocalModificationRegression",
    )
    finalizedBy("sfImp0067AcceptanceVerify")
}

val sfImp0068AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0068")
val sfImp0068AcceptanceServerProperties = """
    level-name=acceptance
    level-seed=600068
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=7
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSfImp0068AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0068AcceptanceServerProperties)
}

mapOf(
    "runProductionComposedCaveAcceptanceA" to "run-sf-imp-0068-auto-a",
    "runProductionComposedCaveAcceptanceB" to "run-sf-imp-0068-auto-b",
    "runProductionComposedCaveAcceptanceStacked" to "run-sf-imp-0068-auto-stacked",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0068AcceptanceServerDirectory(relativePath)
        }
    }
}

tasks.named("runProductionComposedCaveAcceptanceA").configure {
    doFirst {
        delete(sfImp0068AcceptanceResultDirectory)
    }
}
tasks.named("runProductionComposedCaveAcceptanceB").configure {
    mustRunAfter("runProductionComposedCaveAcceptanceA")
}
tasks.named("runProductionComposedCaveAcceptanceReloadClient").configure {
    mustRunAfter("runProductionComposedCaveAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0068-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}
tasks.named("runProductionComposedCaveAcceptanceStacked").configure {
    mustRunAfter("runProductionComposedCaveAcceptanceReloadClient")
}
tasks.named("runComposedCaveAcceptanceA").configure {
    mustRunAfter("runProductionComposedCaveAcceptanceStacked")
}

tasks.register("sfImp0068AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic SF-IMP-0068 production composed-cave lifecycle evidence."
    doLast {
        fun load0068(name: String): Properties {
            val file = sfImp0068AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0068 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }
        fun load0067(name: String): Properties {
            val file = sfImp0067AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0067 regression result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load0068("production-a")
        val second = load0068("production-b")
        val reload = load0068("reload")
        val stacked = load0068("stacked")
        val composed0067 = load0067("composed-a")

        for ((name, result) in listOf(
            "production-a" to first,
            "production-b" to second,
            "reload" to reload,
            "stacked" to stacked,
            "sf-imp-0067-composed" to composed0067,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        for (key in listOf(
            "islandKey",
            "nativeBiome",
            "initialTotal",
            "initialPending",
            "initialCompleted",
            "requiredChunks",
            "finalPending",
            "finalCompleted",
            "resultChunks",
            "emptyChunks",
            "nativeChangedBlocks",
            "nativeSuccessfulCalls",
            "nativeOnlyAir",
            "nativeRejectedWrites",
            "nativeMappedOutsideTarget",
            "nativeTransformDigest",
            "nativeCarveDigest",
            "authoredPositive",
            "authoredBasePositive",
            "authoredExposurePositive",
            "authoredUnsafe",
            "authoredChangedBlocks",
            "authoredChangedDigest",
            "authoredProvenanceDigest",
            "finalAuthoredAir",
            "nativeOnlyPos",
            "mouthPos",
            "outwardPos",
            "baseCavePos",
            "composedDigest",
        )) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0068 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }

        val required = first.getProperty("requiredChunks").toInt()
        check(first.getProperty("islandKey") == "3670"
                && first.getProperty("nativeBiome") == "minecraft:taiga"
                && first.getProperty("productionStage") == "true"
                && first.getProperty("admittedBeforeCompletion") == "true"
                && first.getProperty("terrainCatchupEmptyBeforeCompletion") == "true"
                && first.getProperty("monotonicPending") == "true"
                && first.getProperty("noReplay") == "true"
                && required > 0
                && first.getProperty("initialTotal").toInt() == required
                && first.getProperty("initialPending").toInt() == required
                && first.getProperty("initialCompleted") == "0"
                && first.getProperty("finalPending") == "0"
                && first.getProperty("finalCompleted").toInt() == required
                && first.getProperty("resultChunks").toInt() > 0
                && first.getProperty("nativeChangedBlocks").toInt() > 0
                && first.getProperty("nativeSuccessfulCalls").toInt() > 0
                && first.getProperty("nativeOnlyAir").toInt() > 0
                && first.getProperty("nativeRejectedWrites") == "0"
                && first.getProperty("nativeMappedOutsideTarget") == "0"
                && first.getProperty("authoredPositive").toInt() > 0
                && first.getProperty("authoredBasePositive").toInt() > 0
                && first.getProperty("authoredExposurePositive").toInt() > 0
                && first.getProperty("authoredUnsafe") == "0"
                && first.getProperty("finalAuthoredAir") == first.getProperty("authoredPositive")) {
            "SF-IMP-0068 production lifecycle evidence incomplete: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("mutationBindingsAbsent") == "true"
                && reload.getProperty("persistedNativeOnlyPos") == first.getProperty("nativeOnlyPos")
                && reload.getProperty("clientNativeOnlyPos") == first.getProperty("nativeOnlyPos")
                && reload.getProperty("persistedNativeOnlyState") == "Block{minecraft:air}"
                && reload.getProperty("clientNativeOnlyState") == "Block{minecraft:air}"
                && reload.getProperty("persistedMouthState") == "Block{minecraft:air}"
                && reload.getProperty("clientMouthState") == "Block{minecraft:air}"
                && reload.getProperty("persistedOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("clientOutwardState") == "Block{minecraft:air}"
                && reload.getProperty("persistedBaseState") == "Block{minecraft:air}"
                && reload.getProperty("clientBaseState") == "Block{minecraft:air}") {
            "SF-IMP-0068 save/reload or ClientLevel persistence failed: $reload"
        }

        check(stacked.getProperty("lowerRequired").toInt() > 0
                && stacked.getProperty("upperRequired").toInt() > 0
                && stacked.getProperty("lowerCompleted") == stacked.getProperty("lowerRequired")
                && stacked.getProperty("upperCompleted") == stacked.getProperty("upperRequired")
                && stacked.getProperty("lowerFinalPending") == "0"
                && stacked.getProperty("upperFinalPending") == "0"
                && stacked.getProperty("lowerNativeChanged").toInt() > 0
                && stacked.getProperty("upperNativeChanged").toInt() > 0
                && stacked.getProperty("lowerAuthoredPositive").toInt() > 0
                && stacked.getProperty("upperAuthoredPositive").toInt() > 0
                && stacked.getProperty("lowerUnsafe") == "0"
                && stacked.getProperty("upperUnsafe") == "0"
                && stacked.getProperty("lowerAnchorY") != stacked.getProperty("upperAnchorY")
                && stacked.getProperty("independentLedgers") == "true"
                && stacked.getProperty("foreignVolumePreserved") == "true"
                && stacked.getProperty("monotonicPending") == "true"
                && stacked.getProperty("noReplay") == "true") {
            "SF-IMP-0068 stacked production isolation failed: $stacked"
        }

        check(composed0067.getProperty("nativeTransformDigest") == "95c046280c7f1c11"
                && composed0067.getProperty("nativeCarveDigest") == "c277e3af5030dd01"
                && composed0067.getProperty("authoredChangedDigest") == "6d2120967a6c73bd"
                && composed0067.getProperty("authoredProvenanceDigest") == "3032a41620c93935"
                && composed0067.getProperty("composedDigest") == "911b02f4fe5b0518"
                && composed0067.getProperty("nativeOnlyAir") == "584"
                && composed0067.getProperty("authoredUnsafe") == "0"
                && composed0067.getProperty("finalUnion") == "true") {
            "SF-IMP-0067 standalone regression gate failed: $composed0067"
        }

        println(
            "SF-IMP-0068 AUTOMATED ACCEPTANCE PASS: obligations="
                + first.getProperty("requiredChunks")
                + ", nativeChanged=" + first.getProperty("nativeChangedBlocks")
                + ", nativeOnlyAir=" + first.getProperty("nativeOnlyAir")
                + ", authoredPositive=" + first.getProperty("authoredPositive")
                + ", composedDigest=" + first.getProperty("composedDigest")
                + ", reloadServerClient=true, stackedIndependent=true"
                + ", sfImp0067Digest=" + composed0067.getProperty("composedDigest"),
        )
    }
}

tasks.register("sfImp0068Acceptance") {
    group = "verification"
    description = "Run complete deterministic SF-IMP-0068 production composed-cave acceptance."
    dependsOn(
        "runProductionComposedCaveAcceptanceA",
        "runProductionComposedCaveAcceptanceB",
        "runProductionComposedCaveAcceptanceReloadClient",
        "runProductionComposedCaveAcceptanceStacked",
        "runComposedCaveAcceptanceA",
    )
    finalizedBy("sfImp0068AcceptanceVerify")
}

dependencies {
    api(project(":skyforge-world"))

    // Minecraft 1.21.1 ModDev runs load Java libraries only when they are explicitly added to the
    // additional runtime classpath. skyforge-world's runtime elements bring the transitive
    // recipes/model/kernel engine modules with it without pretending those modules are mods.
    add("additionalRuntimeClasspath", project(":skyforge-world"))

    // SF-IMP-0035 makes the distributable mod self-contained using NeoForge's supported Jar-in-Jar
    // mechanism. Keep each backend-neutral module as an ordinary Java library: the Minecraft
    // adapter embeds their jars instead of copying/shading their classes or turning them into mods.
    add("jarJar", project(":skyforge-kernel"))
    add("jarJar", project(":skyforge-model"))
    add("jarJar", project(":skyforge-recipes"))
    add("jarJar", project(":skyforge-world"))

    testImplementation(project(":skyforge-recipes"))

    // ModDevGradle's FML-aware JUnit launcher is currently proven against JUnit Platform 5.
    // Isolate this Minecraft integration module on the plugin's own known-good JUnit line rather
    // than forcing the rest of Skyforge away from its independent test stack.
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
