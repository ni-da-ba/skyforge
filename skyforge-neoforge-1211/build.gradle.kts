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
    // Static development-resource guards must resolve module-local fixtures independently of
    // whichever repository directory a CI workflow chooses as the Test task working directory.
    systemProperty("skyforge.test.projectDirectory", layout.projectDirectory.asFile.absolutePath)
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


// External NeoForge mods must live on a run source set's runtime classpath to be discovered as mods.
// AdditionalRuntimeClasspath is the legacy *library* classpath and is therefore insufficient for
// Fowl Play/A4MC runtime acceptance on Minecraft 1.21.1.
val waveC5Runtime = sourceSets.create("waveC5Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// Wave C1 keeps optional engineering-mod dependencies out of ordinary Skyforge runs. The
// immutable Modrinth version IDs live in one small lock manifest so the development specimen can
// be reproduced without making these R&D candidates production dependencies.
val waveC1PinFile = layout.projectDirectory.file("wave-c1-mods.properties")
val waveC1Pins = Properties().apply {
    waveC1PinFile.asFile.inputStream().use(::load)
}

fun waveC1Pin(mod: String, field: String): String =
    requireNotNull(waveC1Pins.getProperty("$mod.$field")) {
        "missing Wave C1 pin: $mod.$field in " + waveC1PinFile.asFile
    }

check(waveC1Pin("minecraft", "version") == "1.21.1") {
    "Wave C1 is defined only for Minecraft 1.21.1"
}
check(waveC1Pin("neoforge", "version") == "21.1.249") {
    "Wave C1 NeoForge pin must match the adapter runtime"
}

val waveC1BaselineMods = listOf(
    "create",
    "rpl",
    "createbigcannons",
    "createaddition",
    "jei",
)
val waveC1MetallurgyMods = waveC1BaselineMods + "createmetallurgy"
val waveC1PropulsionMods = waveC1BaselineMods + listOf(
    "sable",
    "aeronautics",
    "createpropulsion",
)
val waveC1IntegratedMods = (waveC1MetallurgyMods + listOf(
    "sable",
    "aeronautics",
    "createpropulsion",
)).distinct()

val waveC1RunMods = linkedMapOf(
    "waveC1BaselineClient" to waveC1BaselineMods,
    "waveC1MetallurgyClient" to waveC1MetallurgyMods,
    "waveC1PropulsionClient" to waveC1PropulsionMods,
    "waveC1IntegratedClient" to waveC1IntegratedMods,
)

    
// Wave C2 is a focused mobility-integrity specimen. It keeps the personal-mobility candidate and
// the exact server-side Elytra-boost suppression dependency isolated from production Skyforge and
// from the Wave C1 industrial specimen.
val waveC2PinFile = layout.projectDirectory.file("wave-c2-mods.properties")
val waveC2Pins = Properties().apply {
    waveC2PinFile.asFile.inputStream().use(::load)
}

fun waveC2Pin(mod: String, field: String): String =
    requireNotNull(waveC2Pins.getProperty("$mod.$field")) {
        "missing Wave C2 pin: $mod.$field in " + waveC2PinFile.asFile
    }

check(waveC2Pin("minecraft", "version") == "1.21.1") {
    "Wave C2 is defined only for Minecraft 1.21.1"
}
check(waveC2Pin("neoforge", "version") == "21.1.249") {
    "Wave C2 NeoForge pin must match the adapter runtime"
}

val waveC2PersonalMods = listOf(
    "reliablegliders",
    "noelytraboost",
)
val waveC2RunMods = linkedMapOf(
    "waveC2PersonalMobilityClient" to waveC2PersonalMods,
    "waveC2IntegratedMobilityClient" to waveC2PersonalMods,
)
// The integrated profile intentionally reuses Wave C1's already-audited pins for the minimum
// powered-aircraft comparison substrate rather than duplicating those coordinates in the C2 lock.
val waveC2IntegratedC1Mods = listOf(
    "create",
    "sable",
    "aeronautics",
    "jei",
)


//
// Wave C3 isolates atmosphere authority from presentation content. Aerodynamics4MC publishes its
// core runtime and Create Aeronautics compatibility addon as separate files under one Modrinth
// project, so each is resolved in its own configuration and then attached as a file collection.
// This prevents Gradle's normal module conflict resolution from collapsing the two version IDs.
val waveC3PinFile = layout.projectDirectory.file("wave-c3-mods.properties")
val waveC3Pins = Properties().apply {
    waveC3PinFile.asFile.inputStream().use(::load)
}

fun waveC3Pin(component: String, field: String): String =
    requireNotNull(waveC3Pins.getProperty("$component.$field")) {
        "missing Wave C3 pin: $component.$field in " + waveC3PinFile.asFile
    }

check(waveC3Pin("minecraft", "version") == "1.21.1") {
    "Wave C3 is defined only for Minecraft 1.21.1"
}
check(waveC3Pin("neoforge", "version") == "21.1.249") {
    "Wave C3 NeoForge pin must match the adapter runtime"
}

val waveC3AeroCoreArtifact = configurations.create("waveC3AeroCoreArtifact") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val waveC3AeroCompatArtifact = configurations.create("waveC3AeroCompatArtifact") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val waveC3FlightStackMods = listOf(
    "create",
    "sable",
    "aeronautics",
    "jei",
)
val waveC3AtmosphereRuns = listOf(
    "waveC3AtmosphereCoreClient",
    "waveC3AtmosphereCoreServer",
    "waveC3AircraftWindClient",
    "waveC3AircraftWindServer",
    "waveC3WindTunnelClient",
    "waveC3WindTunnelServer",
)
val waveC3CompatRuns = listOf(
    "waveC3AircraftWindClient",
    "waveC3AircraftWindServer",
    "waveC3WindTunnelClient",
    "waveC3WindTunnelServer",
)
val waveC3WindTunnelRuns = listOf(
    "waveC3WindTunnelClient",
    "waveC3WindTunnelServer",
)


//
// Wave C5 tests the reuse-first soaring-fauna substrate. Fowl Play remains an optional runtime
// candidate: the production adapter must not require it merely for Skyforge to boot.
val waveC5PinFile = layout.projectDirectory.file("wave-c5-mods.properties")
val waveC5Pins = Properties().apply {
    waveC5PinFile.asFile.inputStream().use(::load)
}

fun waveC5Pin(mod: String, field: String): String =
    requireNotNull(waveC5Pins.getProperty("$mod.$field")) {
        "missing Wave C5 pin: $mod.$field in " + waveC5PinFile.asFile
    }

check(waveC5Pin("minecraft", "version") == "1.21.1") {
    "Wave C5 is defined only for Minecraft 1.21.1"
}
check(waveC5Pin("neoforge", "version") == "21.1.249") {
    "Wave C5 NeoForge pin must match the adapter runtime"
}

val waveC5BirdStackMods = listOf(
    "fowlplay",
    "smartbrainlib",
    "yacl",
)

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
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "600")
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
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "600")
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
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "600")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0068/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0069 production post-cave native interior population. The mixed-biome exact
        // volume resolves river on one side and dripstone caves on the other so the production
        // scheduler can exercise accepted LAKES, local geology, ores, cave decoration and springs
        // without any feature-ID forcing.
        create("productionInteriorPopulationAcceptanceA") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0069-auto-a").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionInteriorPopulation", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0069-production-interior-a")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "900")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0069/production-a.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionInteriorPopulationAcceptanceB") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0069-auto-b").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionInteriorPopulation", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0069-production-interior-b")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "900")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0069/production-b.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionInteriorPopulationAcceptanceReloadClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0069-auto-b").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionInteriorPopulationReload", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0069-production-interior-reload")
            systemProperty(
                "skyforge.dev.productionInteriorPopulationExpectedResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0069/production-b.properties").get().asFile.absolutePath,
            )
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0069/reload.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("productionInteriorPopulationAcceptanceStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0069-auto-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionInteriorPopulationStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0069-production-interior-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "900")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0069/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0070 characterizes the same accepted stacked production path with aggregate,
        // opt-in stage timers. No production scheduling or mutation policy changes in this run.
        create("performanceCharacterizationStacked") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0070-performance-stacked").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.productionInteriorPopulationStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0070-performance-stacked")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "900")
            systemProperty("skyforge.dev.performanceMetrics", "true")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0070/stacked.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Current-capability developer showcase. Preparation deliberately reuses the accepted
        // SF-IMP-0069 stacked production runtime unchanged, but writes into a stable presentation
        // world. The viewer restores only deterministic compiled terrain ownership for persisted
        // fluid fencing; all mutation lifecycle bindings remain inert.
        create("showcasePrepare") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("showcase")
            systemProperty("skyforge.dev.productionInteriorPopulationStacked", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "skyforge-current-capability-showcase")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "900")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/showcase/prepare.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("showcaseClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("showcase")
            systemProperty("skyforge.dev.showcaseViewer", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Automated reopen proof for the exact human-viewer lifecycle. It uses the prepared world,
        // restores only immutable compiled terrain ownership, forces a persisted generated-fluid
        // tick, proves all mutation lifecycles remain inert, and closes the quick-play client.
        create("showcaseViewerAcceptanceClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("showcase")
            systemProperty("skyforge.dev.showcaseViewer", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "skyforge-current-capability-showcase-viewer")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "120")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/showcase/viewer.properties").get().asFile.absolutePath,
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

        // Wave C1 content-integration specimens. External engineering mods are attached only to
        // these runs through ModDevGradle's per-run AdditionalRuntimeClasspath configurations.
        // This prevents R&D dependencies from leaking into normal Skyforge implementation proofs.
        create("waveC1BaselineClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c1-baseline").asFile
            systemProperty("skyforge.dev.waveC1Profile", "baseline")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC1MetallurgyClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c1-metallurgy").asFile
            systemProperty("skyforge.dev.waveC1Profile", "metallurgy")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC1PropulsionClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c1-propulsion").asFile
            systemProperty("skyforge.dev.waveC1Profile", "propulsion")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC1IntegratedClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c1-integrated").asFile
            systemProperty("skyforge.dev.waveC1Profile", "integrated")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }


        // Wave C2 personal-mobility specimen: the early glider plus server-side Elytra rocket
        // suppression, with no Create/Aeronautics stack present. This isolates personal traversal.
        create("waveC2PersonalMobilityClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c2-personal-mobility").asFile
            systemProperty("skyforge.dev.waveC2Profile", "personal")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Wave C2 integrated comparison: the same personal mobility rules plus the minimum
        // Create/Sable/Aeronautics substrate needed to compare low-throughput soaring against
        // powered two-way logistics without bringing CBC/Metallurgy/Propulsion into the specimen.
        create("waveC2IntegratedMobilityClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c2-integrated-mobility").asFile
            systemProperty("skyforge.dev.waveC2Profile", "integrated")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }


        // Wave C3 authority-isolation profile. No Create/Aeronautics or official A4MC content addon:
        // this run answers only whether the server-authoritative atmosphere runtime is viable.
        create("waveC3AtmosphereCoreClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-atmosphere-core").asFile
            systemProperty("skyforge.dev.waveC3Profile", "atmosphere-core")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC3AtmosphereCoreServer") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-atmosphere-core-server").asFile
            programArgument("--nogui")
            systemProperty("skyforge.dev.waveC3Profile", "atmosphere-core-server")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // A4MC core + its dedicated Create Aeronautics compatibility jar over Skyforge's already
        // pinned minimum flight substrate. This is the actual relative-airflow candidate.
        create("waveC3AircraftWindClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-aircraft-wind").asFile
            systemProperty("skyforge.dev.waveC3Profile", "aircraft-wind")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC3AircraftWindServer") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-aircraft-wind-server").asFile
            programArgument("--nogui")
            systemProperty("skyforge.dev.waveC3Profile", "aircraft-wind-server")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Development measurement profile. Wind Tunnel is not atmosphere authority and is not a
        // production dependency candidate here; it supplies controlled airflow and force readback
        // so headwind/crosswind/updraft cases can become numerical acceptance rather than eyeballing.
        create("waveC3WindTunnelClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-wind-tunnel").asFile
            systemProperty("skyforge.dev.waveC3Profile", "wind-tunnel")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("waveC3WindTunnelServer") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-wave-c3-wind-tunnel-server").asFile
            programArgument("--nogui")
            systemProperty("skyforge.dev.waveC3Profile", "wind-tunnel-server")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }


        // Wave C5 keeps the bird/AI stack isolated while reusing the already accepted A4MC core.
        // The first gate is intentionally headless: prove Fowl Play + SBL + YACL + A4MC can coexist
        // on the dedicated server before any thermal-soaring compatibility code is admitted.
        create("waveC5SoaringFaunaServer") {
            server()
            sourceSet.set(waveC5Runtime)
            gameDirectory = layout.projectDirectory.dir("run-wave-c5-soaring-fauna-server").asFile
            programArgument("--nogui")
            systemProperty("skyforge.dev.waveC5Profile", "soaring-fauna")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }


        // Wave C6 exercises the actual optional hawk/A4MC compatibility controller. The same
        // retained bird stack is used; the system property activates Skyforge's otherwise inert
        // reflection bridge and event hooks.
        create("waveC6HawkThermalServer") {
            server()
            sourceSet.set(waveC5Runtime)
            gameDirectory = layout.projectDirectory.dir("run-wave-c6-hawk-thermal-server").asFile
            programArgument("--nogui")
            systemProperty("skyforge.dev.waveC6SoaringFauna", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

    }

    unitTest {
        enable()
        testedMod.set(mods.named("skyforge"))
    }
}

val waveC3SmokeServerProperties = """
    level-name=wave-c3-smoke
    level-seed=600300
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=2
    simulation-distance=2
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareWaveC3SmokeServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(waveC3SmokeServerProperties)
}

mapOf(
    "runWaveC3AtmosphereCoreServer" to "run-wave-c3-atmosphere-core-server",
    "runWaveC3AircraftWindServer" to "run-wave-c3-aircraft-wind-server",
    "runWaveC3WindTunnelServer" to "run-wave-c3-wind-tunnel-server",
).forEach { (taskName, relativePath) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareWaveC3SmokeServerDirectory(relativePath)
        }
    }
}


val waveC5SmokeServerProperties = """
    level-name=wave-c5-smoke
    level-seed=600500
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=2
    simulation-distance=2
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

tasks.named("runWaveC5SoaringFaunaServer").configure {
    doFirst {
        val directory = layout.projectDirectory.dir("run-wave-c5-soaring-fauna-server").asFile
        delete(directory)
        directory.mkdirs()
        directory.resolve("eula.txt").writeText("eula=true\n")
        directory.resolve("server.properties").writeText(waveC5SmokeServerProperties)
    }
}


tasks.named("runWaveC6HawkThermalServer").configure {
    doFirst {
        val directory = layout.projectDirectory.dir("run-wave-c6-hawk-thermal-server").asFile
        delete(directory)
        directory.mkdirs()
        directory.resolve("eula.txt").writeText("eula=true\n")
        directory.resolve("server.properties").writeText(waveC5SmokeServerProperties)
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
                + "narrator:0\n"
                + "renderDistance:12\n"
                + "simulationDistance:8\n",
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

fun requireSfImp0068AcceptancePass(resultName: String) {
    val file = sfImp0068AcceptanceResultDirectory.get().file("$resultName.properties").asFile
    check(file.isFile) { "SF-IMP-0068 acceptance result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS") {
        val detail = properties.getProperty("failure")
            ?: "status=${properties.getProperty("status")}"
        "SF-IMP-0068 acceptance case $resultName did not PASS: $detail"
    }
}

listOf(
    Triple("runProductionComposedCaveAcceptanceA", "run-sf-imp-0068-auto-a", "production-a"),
    Triple("runProductionComposedCaveAcceptanceB", "run-sf-imp-0068-auto-b", "production-b"),
    Triple("runProductionComposedCaveAcceptanceStacked", "run-sf-imp-0068-auto-stacked", "stacked"),
).forEach { (taskName, relativePath, resultName) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0068AcceptanceServerDirectory(relativePath)
        }
        doLast {
            requireSfImp0068AcceptancePass(resultName)
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
    doLast {
        requireSfImp0068AcceptancePass("reload")
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
        check(first.getProperty("islandKey") == "1471"
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


tasks.register("waveC1ResolvePinnedMods") {
    group = "verification"
    description = "Resolve the exact optional-mod artifacts used by the Wave C1 development runs through ModDevGradle resolvable legacy classpaths."
    inputs.file(waveC1PinFile)

    doLast {
        waveC1RunMods.forEach { (runName, mods) ->
            val configurationName = "${runName}LegacyClasspath"
            val files = configurations.getByName(configurationName)
                .resolvedConfiguration
                .resolvedArtifacts
                .map { it.file.name }
                .sorted()
            println("Wave C1 $runName")
            println("  requested=" + mods.joinToString(", "))
            files.forEach { println("  resolved=$it") }
        }
    }
}


tasks.register("waveC2ResolvePinnedMods") {
    group = "verification"
    description = "Resolve the exact optional-mod artifacts used by the Wave C2 mobility runs through ModDevGradle resolvable legacy classpaths."
    inputs.file(waveC2PinFile)
    inputs.file(waveC1PinFile)

    doLast {
        waveC2RunMods.forEach { (runName, c2Mods) ->
            val configurationName = "${runName}LegacyClasspath"
            val requested = if (runName == "waveC2IntegratedMobilityClient") {
                c2Mods + waveC2IntegratedC1Mods
            } else {
                c2Mods
            }
            val files = configurations.getByName(configurationName)
                .resolvedConfiguration
                .resolvedArtifacts
                .map { it.file.name }
                .sorted()
            println("Wave C2 $runName")
            println("  requested=" + requested.joinToString(", "))
            files.forEach { println("  resolved=$it") }
        }
    }
}


tasks.register("waveC3ResolvePinnedMods") {
    group = "verification"
    description = "Resolve the exact Wave C3 atmosphere-authority artifacts and isolated run classpaths."
    inputs.file(waveC3PinFile)
    inputs.file(waveC1PinFile)

    doLast {
        val coreFiles = waveC3AeroCoreArtifact.files.map { it.name }.sorted()
        val compatFiles = waveC3AeroCompatArtifact.files.map { it.name }.sorted()
        check(coreFiles.isNotEmpty()) { "Wave C3 Aerodynamics4MC core artifact did not resolve" }
        check(compatFiles.isNotEmpty()) { "Wave C3 Aerodynamics4MC compat artifact did not resolve" }
        check(coreFiles.toSet().intersect(compatFiles.toSet()).isEmpty()) {
            "Wave C3 core/compat artifact resolution collapsed onto the same file: core=$coreFiles compat=$compatFiles"
        }
        println("Wave C3 isolated Aerodynamics4MC artifacts")
        coreFiles.forEach { println("  core=$it") }
        compatFiles.forEach { println("  compat=$it") }

        waveC3AtmosphereRuns.forEach { runName ->
            val files = configurations.getByName("${runName}LegacyClasspath")
                .files
                .map { it.name }
                .sorted()
            println("Wave C3 $runName")
            files.forEach { println("  resolved=$it") }
        }
    }
}


tasks.register("waveC5ResolvePinnedMods") {
    group = "verification"
    description = "Resolve the exact Wave C5 soaring-fauna runtime specimen."
    inputs.file(waveC5PinFile)
    inputs.file(waveC3PinFile)

    doLast {
        val files = waveC5Runtime.runtimeClasspath.files
            .map { it.name }
            .sorted()

        fun artifactToken(coordinate: String): String {
            val parts = coordinate.split(":")
            check(parts.size == 3) { "expected group:module:version coordinate, got '$coordinate'" }
            return "${parts[1]}-${parts[2]}"
        }

        val requiredTokens = mapOf(
            "Fowl Play" to artifactToken(waveC5Pin("fowlplay", "coordinate")),
            "SmartBrainLib" to artifactToken(waveC5Pin("smartbrainlib", "coordinate")),
            "YACL" to artifactToken(waveC5Pin("yacl", "coordinate")),
            "Aerodynamics4MC core" to artifactToken(waveC3Pin("aerodynamics4mcCore", "coordinate")),
        )
        requiredTokens.forEach { (label, token) ->
            check(files.any { it.contains(token) }) {
                "Wave C5 missing $label artifact token '$token': $files"
            }
        }

        println("Wave C5 soaring-fauna classpath")
        files.forEach { println("  resolved=$it") }
    }
}


tasks.register("waveC6ResolvePinnedMods") {
    group = "verification"
    description = "Resolve and assert the exact Wave C6 hawk-thermal run classpath."
    inputs.file(waveC5PinFile)
    inputs.file(waveC3PinFile)

    doLast {
        val files = waveC5Runtime.runtimeClasspath.files
            .map { it.name }
            .sorted()

        fun artifactToken(coordinate: String): String {
            val parts = coordinate.split(":")
            check(parts.size == 3) { "expected group:module:version coordinate, got '$coordinate'" }
            return "${parts[1]}-${parts[2]}"
        }

        val requiredTokens = mapOf(
            "Fowl Play" to artifactToken(waveC5Pin("fowlplay", "coordinate")),
            "SmartBrainLib" to artifactToken(waveC5Pin("smartbrainlib", "coordinate")),
            "YACL" to artifactToken(waveC5Pin("yacl", "coordinate")),
            "Aerodynamics4MC core" to artifactToken(waveC3Pin("aerodynamics4mcCore", "coordinate")),
        )
        requiredTokens.forEach { (label, token) ->
            check(files.any { it.contains(token) }) {
                "Wave C6 missing $label artifact token '$token': $files"
            }
        }

        println("Wave C6 hawk-thermal classpath")
        files.forEach { println("  resolved=$it") }
    }
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


    // ModDevGradle creates a per-run <runName>AdditionalRuntimeClasspath configuration. On
    // Minecraft 1.21.1, mod jars placed there are discovered by FML while remaining isolated from
    // every other development/acceptance run.
    waveC1RunMods.forEach { (runName, mods) ->
        mods.forEach { mod ->
            add(
                "${runName}AdditionalRuntimeClasspath",
                waveC1Pin(mod, "coordinate"),
            )
        }
    }


    // Wave C2 optional dependencies remain run-scoped. The integrated comparison reuses only the
    // minimum C1-pinned Create/Sable/Aeronautics/JEI substrate.
    waveC2RunMods.forEach { (runName, mods) ->
        mods.forEach { mod ->
            add(
                "${runName}AdditionalRuntimeClasspath",
                waveC2Pin(mod, "coordinate"),
            )
        }
    }
    waveC2IntegratedC1Mods.forEach { mod ->
        add(
            "waveC2IntegratedMobilityClientAdditionalRuntimeClasspath",
            waveC1Pin(mod, "coordinate"),
        )
    }


    // Resolve A4MC core and Create Aeronautics compatibility files independently because Modrinth
    // exposes both under the same project/module identity but different version IDs.
    add("waveC3AeroCoreArtifact", waveC3Pin("aerodynamics4mcCore", "coordinate"))
    add("waveC3AeroCompatArtifact", waveC3Pin("aerodynamics4mcCompat", "coordinate"))

    // Every C3 profile receives the core atmosphere authority.
    waveC3AtmosphereRuns.forEach { runName ->
        add(
            "${runName}AdditionalRuntimeClasspath",
            files(waveC3AeroCoreArtifact),
        )
    }

    // Aircraft profiles reuse the current C1-pinned flight substrate; the compat addon is kept
    // separate from A4MC core so both jars reach FML.
    waveC3CompatRuns.forEach { runName ->
        waveC3FlightStackMods.forEach { mod ->
            add(
                "${runName}AdditionalRuntimeClasspath",
                waveC1Pin(mod, "coordinate"),
            )
        }
        add(
            "${runName}AdditionalRuntimeClasspath",
            files(waveC3AeroCompatArtifact),
        )
    }

    // Wind Tunnel is a test instrument only. Its source-built LDLib floor is pinned explicitly
    // because Modrinth Maven does not carry transitive dependency metadata.
    waveC3WindTunnelRuns.forEach { runName ->
        add(
            "${runName}AdditionalRuntimeClasspath",
            waveC3Pin("windTunnel", "coordinate"),
        )
        add(
            "${runName}AdditionalRuntimeClasspath",
            waveC3Pin("ldlib", "coordinate"),
        )
    }


    // C5/C6 external mods belong on the isolated run source set's runtime classpath so FML
    // discovers them as NeoForge mods rather than treating them as legacy Java libraries.
    waveC5BirdStackMods.forEach { mod ->
        add(
            waveC5Runtime.runtimeOnlyConfigurationName,
            waveC5Pin(mod, "coordinate"),
        )
    }
    add(
        waveC5Runtime.runtimeOnlyConfigurationName,
        files(waveC3AeroCoreArtifact),
    )

    testImplementation(project(":skyforge-recipes"))

    // ModDevGradle's FML-aware JUnit launcher is currently proven against JUnit Platform 5.
    // Isolate this Minecraft integration module on the plugin's own known-good JUnit line rather
    // than forcing the rest of Skyforge away from its independent test stack.
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


val sfImp0069AcceptanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0069")
val sfImp0069AcceptanceServerProperties = """
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

fun prepareSfImp0069AcceptanceServerDirectory(relativePath: String) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(sfImp0069AcceptanceServerProperties)
}

fun requireSfImp0069AcceptancePass(resultName: String) {
    val file = sfImp0069AcceptanceResultDirectory.get().file("$resultName.properties").asFile
    check(file.isFile) { "SF-IMP-0069 acceptance result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS") {
        val detail = properties.getProperty("failure")
            ?: "status=" + properties.getProperty("status")
        "SF-IMP-0069 acceptance case $resultName did not PASS: $detail"
    }
}

listOf(
    Triple("runProductionInteriorPopulationAcceptanceA", "run-sf-imp-0069-auto-a", "production-a"),
    Triple("runProductionInteriorPopulationAcceptanceB", "run-sf-imp-0069-auto-b", "production-b"),
    Triple("runProductionInteriorPopulationAcceptanceStacked", "run-sf-imp-0069-auto-stacked", "stacked"),
).forEach { (taskName, relativePath, resultName) ->
    tasks.named(taskName).configure {
        doFirst {
            prepareSfImp0069AcceptanceServerDirectory(relativePath)
        }
        doLast {
            requireSfImp0069AcceptancePass(resultName)
        }
    }
}

tasks.named("runProductionInteriorPopulationAcceptanceA").configure {
    doFirst {
        delete(sfImp0069AcceptanceResultDirectory)
    }
}
tasks.named("runProductionInteriorPopulationAcceptanceB").configure {
    mustRunAfter("runProductionInteriorPopulationAcceptanceA")
}
tasks.named("runProductionInteriorPopulationAcceptanceReloadClient").configure {
    mustRunAfter("runProductionInteriorPopulationAcceptanceB")
    doFirst {
        val directory = layout.projectDirectory.dir("run-sf-imp-0069-auto-b").asFile
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
    doLast {
        requireSfImp0069AcceptancePass("reload")
    }
}
tasks.named("runProductionInteriorPopulationAcceptanceStacked").configure {
    mustRunAfter("runProductionInteriorPopulationAcceptanceReloadClient")
}

tasks.register("sfImp0069AcceptanceVerify") {
    group = "verification"
    description = "Verify deterministic SF-IMP-0069 production native interior evidence."
    doLast {
        fun load0069(name: String): Properties {
            val file = sfImp0069AcceptanceResultDirectory.get().file("$name.properties").asFile
            check(file.isFile) { "missing SF-IMP-0069 acceptance result: $file" }
            return Properties().also { properties -> file.inputStream().use(properties::load) }
        }

        val first = load0069("production-a")
        val second = load0069("production-b")
        val reload = load0069("reload")
        val stacked = load0069("stacked")
        for ((name, result) in listOf(
            "production-a" to first,
            "production-b" to second,
            "reload" to reload,
            "stacked" to stacked,
        )) {
            check(result.getProperty("status") == "PASS") { "$name did not report PASS: $result" }
        }

        for (key in listOf(
            "islandKey",
            "requiredChunks",
            "initialInteriorTotal",
            "initialInteriorPending",
            "finalInteriorPending",
            "finalInteriorCompleted",
            "resultChunks",
            "emptyChunks",
            "biomes",
            "phaseDigest",
            "lakesAttempted",
            "lakesSuccessful",
            "localModificationsAttempted",
            "localModificationsSuccessful",
            "oresAttempted",
            "oresSuccessful",
            "decorationAttempted",
            "decorationSuccessful",
            "springsAttempted",
            "springsSuccessful",
            "trackedFluids",
            "fluidDigest",
            "sampleFluidPos",
            "sampleFluidState",
            "scheduledOutsideOwner",
            "rejectedBoundaryWrites",
            "successfulFeatureKeys",
        )) {
            check(first.getProperty(key) == second.getProperty(key)) {
                "SF-IMP-0069 deterministic evidence changed for $key: A=" +
                    first.getProperty(key) + " B=" + second.getProperty(key)
            }
        }

        val required = first.getProperty("requiredChunks").toInt()
        check(first.getProperty("productionStage") == "true"
                && first.getProperty("islandKey") == "1471"
                && required > 0
                && first.getProperty("initialInteriorTotal").toInt() == required
                && first.getProperty("initialInteriorPending").toInt() == required
                && first.getProperty("finalInteriorPending") == "0"
                && first.getProperty("finalInteriorCompleted").toInt() == required
                && first.getProperty("resultChunks").toInt() > 0
                && first.getProperty("cavesCompleteBeforeInterior") == "true"
                && first.getProperty("monotonicPending") == "true"
                && first.getProperty("noReplay") == "true"
                && first.getProperty("biomes").contains("minecraft:river")
                && first.getProperty("biomes").contains("minecraft:dripstone_caves")
                && first.getProperty("lakesAttempted").toInt() > 0
                && first.getProperty("lakesSuccessful").toInt() > 0
                && first.getProperty("localModificationsAttempted").toInt() > 0
                && first.getProperty("localModificationsSuccessful").toInt() > 0
                && first.getProperty("oresAttempted").toInt() > 0
                && first.getProperty("oresSuccessful").toInt() > 0
                && first.getProperty("decorationAttempted").toInt() > 0
                && first.getProperty("decorationSuccessful").toInt() > 0
                && first.getProperty("springsAttempted").toInt() > 0
                && first.getProperty("springsSuccessful").toInt() > 0
                && first.getProperty("trackedFluids").toInt() > 0
                && first.getProperty("scheduledOutsideOwner") == "0"
                && first.getProperty("rejectedBoundaryWrites") == "0") {
            "SF-IMP-0069 production interior evidence incomplete: $first"
        }

        check(reload.getProperty("reloadServerPass") == "true"
                && reload.getProperty("reloadClientPass") == "true"
                && reload.getProperty("mutationBindingsAbsent") == "true"
                && reload.getProperty("persistedFluidPos") == second.getProperty("sampleFluidPos")
                && reload.getProperty("persistedInitialFluidState") == second.getProperty("sampleFluidState")
                && reload.getProperty("clientFluidPos") == reload.getProperty("persistedFluidPos")
                && reload.getProperty("clientFluidState") == reload.getProperty("persistedFluidState")
                && reload.getProperty("persistedTrackedPositions").toInt() > 0
                && reload.getProperty("reloadPropagationTicks").toInt() > 0
                && reload.getProperty("scheduledOutsideOwner") == "0"
                && reload.getProperty("rejectedBoundaryWrites") == "0") {
            "SF-IMP-0069 reload/client evidence incomplete: $reload"
        }

        check(stacked.getProperty("lowerRequired").toInt() > 0
                && stacked.getProperty("upperRequired").toInt() > 0
                && stacked.getProperty("lowerCompleted") == stacked.getProperty("lowerRequired")
                && stacked.getProperty("upperCompleted") == stacked.getProperty("upperRequired")
                && stacked.getProperty("lowerResultChunks").toInt() > 0
                && stacked.getProperty("upperResultChunks").toInt() > 0
                && stacked.getProperty("lowerSuccessful").toInt() > 0
                && stacked.getProperty("upperSuccessful").toInt() > 0
                && stacked.getProperty("lowerTrackedFluids").toInt() > 0
                && stacked.getProperty("upperTrackedFluids").toInt() > 0
                && stacked.getProperty("lowerSampleY") != stacked.getProperty("upperSampleY")
                && stacked.getProperty("lowerFinalPending") == "0"
                && stacked.getProperty("upperFinalPending") == "0"
                && stacked.getProperty("independentLedgers") == "true"
                && stacked.getProperty("foreignFluidRejected") == "true"
                && stacked.getProperty("cavesCompleteBeforeInterior") == "true"
                && stacked.getProperty("monotonicPending") == "true"
                && stacked.getProperty("noReplay") == "true") {
            "SF-IMP-0069 stacked production evidence incomplete: $stacked"
        }

        println(
            "SF-IMP-0069 AUTOMATED ACCEPTANCE PASS: obligations=$required" +
                ", phaseDigest=" + first.getProperty("phaseDigest") +
                ", lakes=" + first.getProperty("lakesSuccessful") +
                ", localModifications=" + first.getProperty("localModificationsSuccessful") +
                ", ores=" + first.getProperty("oresSuccessful") +
                ", decoration=" + first.getProperty("decorationSuccessful") +
                ", springs=" + first.getProperty("springsSuccessful"),
        )
    }
}


val sfImp0070PerformanceResultDirectory = layout.buildDirectory.dir("acceptance/sf-imp-0070")

tasks.named("runPerformanceCharacterizationStacked").configure {
    doFirst {
        delete(sfImp0070PerformanceResultDirectory)
        prepareSfImp0069AcceptanceServerDirectory("run-sf-imp-0070-performance-stacked")
    }
    doLast {
        val file = sfImp0070PerformanceResultDirectory.get().file("stacked.properties").asFile
        check(file.isFile) { "SF-IMP-0070 performance result missing: $file" }
        val properties = Properties()
        file.inputStream().use(properties::load)
        check(properties.getProperty("status") == "PASS") {
            val detail = properties.getProperty("failure") ?: properties.toString()
            "SF-IMP-0070 production fixture did not PASS: $detail"
        }
    }
}

tasks.register("sfImp0070PerformanceVerify") {
    group = "verification"
    description = "Verify stage-resolved SF-IMP-0070 production performance evidence."
    dependsOn("runPerformanceCharacterizationStacked")
    doLast {
        val file = sfImp0070PerformanceResultDirectory.get().file("stacked.properties").asFile
        val properties = Properties()
        file.inputStream().use(properties::load)

        val requiredMetrics = listOf(
            "perf.processElapsedNanos",
            "perf.acceptance.warmOriginFootprint.totalNanos",
            "perf.catchup.composedCavePump.totalNanos",
            "perf.admission.nativeOccupancySurvey.totalNanos",
            "perf.terrain.realize.totalNanos",
            "perf.terrain.noCandidatePrefilter.totalNanos",
            "perf.terrain.realizeDeferred.totalNanos",
            "perf.surfacePopulation.coordinator.totalNanos",
            "perf.caves.authoredPreflight.totalNanos",
            "perf.caves.nativeCarver.totalNanos",
            "perf.caves.authoredCommit.totalNanos",
            "perf.interior.LAKES.totalNanos",
            "perf.interior.LOCAL_MODIFICATIONS.totalNanos",
            "perf.interior.UNDERGROUND_ORES.totalNanos",
            "perf.interior.UNDERGROUND_DECORATION.totalNanos",
            "perf.interior.FLUID_SPRINGS.totalNanos",
        )
        for (key in requiredMetrics) {
            val value = properties.getProperty(key)?.toLongOrNull()
            check(value != null && value > 0L) {
                "SF-IMP-0070 missing/nonpositive performance metric $key: $value"
            }
        }

        check(properties.getProperty("lowerCompleted") == properties.getProperty("lowerRequired")
                && properties.getProperty("upperCompleted") == properties.getProperty("upperRequired")
                && properties.getProperty("independentLedgers") == "true"
                && properties.getProperty("foreignFluidRejected") == "true"
                && properties.getProperty("cavesCompleteBeforeInterior") == "true"
                && properties.getProperty("noReplay") == "true") {
            "SF-IMP-0070 timing run lost SF-IMP-0069 correctness evidence: $properties"
        }

        val terrainRealizeCalls = properties.getProperty("perf.terrain.realize.calls").toLong()
        check(terrainRealizeCalls in 1L..<300L) {
            "SF-IMP-0072 non-candidate terrain prefilter did not reduce direct realization calls: " +
                "terrainRealizeCalls=$terrainRealizeCalls"
        }

        val cavePreflightCalls = properties.getProperty("perf.caves.authoredPreflight.calls").toLong()
        val cavePumpCalls = properties.getProperty("perf.catchup.composedCavePump.calls").toLong()
        check(cavePumpCalls > 0L && cavePreflightCalls >= cavePumpCalls * 4L) {
            "SF-IMP-0071 cave catch-up did not materially batch micro-steps: " +
                "preflightCalls=$cavePreflightCalls, pumpCalls=$cavePumpCalls"
        }

        val elapsedMs = properties.getProperty("perf.processElapsedNanos").toLong() / 1_000_000.0
        val warmupMs = properties.getProperty("perf.acceptance.warmOriginFootprint.totalNanos").toLong() / 1_000_000.0
        println(
            "SF-IMP-0070 PERFORMANCE CHARACTERIZATION PASS: processMs=$elapsedMs, warmupMs=$warmupMs, " +
                "terrainRealizeCalls=$terrainRealizeCalls, " +
                "cavePreflightCalls=$cavePreflightCalls, cavePumpCalls=$cavePumpCalls, " +
                "metrics=" + requiredMetrics.size,
        )
    }
}

val skyforgeShowcaseResultDirectory = layout.buildDirectory.dir("acceptance/showcase")
val skyforgeShowcaseServerProperties = """
    level-name=showcase
    level-seed=600068
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    allow-flight=true
    view-distance=7
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSkyforgeShowcaseDirectory() {
    val directory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(skyforgeShowcaseServerProperties)
}

fun requireSkyforgeShowcasePreparationPass() {
    val file = skyforgeShowcaseResultDirectory.get().file("prepare.properties").asFile
    check(file.isFile) { "Skyforge showcase preparation result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS") {
        val detail = properties.getProperty("failure") ?: "status=" + properties.getProperty("status")
        "Skyforge showcase preparation did not PASS: $detail"
    }
    check(properties.getProperty("lowerCompleted") == properties.getProperty("lowerRequired")
            && properties.getProperty("upperCompleted") == properties.getProperty("upperRequired")
            && properties.getProperty("lowerResultChunks").toInt() > 0
            && properties.getProperty("upperResultChunks").toInt() > 0
            && properties.getProperty("lowerSuccessful").toInt() > 0
            && properties.getProperty("upperSuccessful").toInt() > 0
            && properties.getProperty("lowerTrackedFluids").toInt() > 0
            && properties.getProperty("upperTrackedFluids").toInt() > 0
            && properties.getProperty("independentLedgers") == "true"
            && properties.getProperty("foreignFluidRejected") == "true"
            && properties.getProperty("cavesCompleteBeforeInterior") == "true"
            && properties.getProperty("noReplay") == "true") {
        "Skyforge showcase preparation evidence is incomplete: $properties"
    }
}

fun requireSkyforgeShowcaseViewerPass() {
    val file = skyforgeShowcaseResultDirectory.get().file("viewer.properties").asFile
    check(file.isFile) { "Skyforge showcase viewer result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("viewerTerrainOwnershipRestored") == "true"
            && properties.getProperty("viewerMutationBindingsInert") == "true"
            && properties.getProperty("viewerGeneratedFluidPropagation") == "true"
            && properties.getProperty("viewerClientPass") == "true") {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "Skyforge showcase viewer did not PASS: $detail"
    }
}

tasks.named("runShowcasePrepare").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask and showcase filesystem orchestration are intentionally runtime-bound.",
    )
    doFirst {
        delete(skyforgeShowcaseResultDirectory)
        prepareSkyforgeShowcaseDirectory()
    }
    doLast {
        requireSkyforgeShowcasePreparationPass()
    }
}

tasks.named("runShowcaseClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is interactive and intentionally not configuration-cache serialized.",
    )
    mustRunAfter("runShowcasePrepare")
    doFirst {
        val directory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
        check(directory.resolve("saves/showcase/level.dat").isFile) {
            "Skyforge showcase world is missing; run runShowcasePrepare first or use launchShowcase."
        }
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.named("runShowcaseViewerAcceptanceClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is an actual quick-play client acceptance process.",
    )
    mustRunAfter("runShowcasePrepare")
    doFirst {
        requireSkyforgeShowcasePreparationPass()
        val directory = layout.projectDirectory.dir("run-skyforge-showcase").asFile
        check(directory.resolve("saves/showcase/level.dat").isFile) {
            "Skyforge showcase world is missing; prepare it before viewer acceptance."
        }
        delete(skyforgeShowcaseResultDirectory.get().file("viewer.properties").asFile)
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.register("showcasePrepareVerify") {
    group = "verification"
    description = "Verify that the deterministic current-capability showcase world prepared successfully."
    dependsOn("runShowcasePrepare")
    doLast {
        requireSkyforgeShowcasePreparationPass()
        println(
            "SKYFORGE SHOWCASE PREPARATION PASS: persisted stacked production world is ready for human review.",
        )
    }
}

tasks.register("showcaseViewerVerify") {
    group = "verification"
    description = "Reopen the prepared showcase in an actual quick-play client and verify persisted-fluid safety."
    dependsOn("runShowcaseViewerAcceptanceClient")
    doLast {
        requireSkyforgeShowcaseViewerPass()
        println(
            "SKYFORGE SHOWCASE VIEWER PASS: persisted world reopened with ownership-only fluid fencing.",
        )
    }
}

tasks.register("launchShowcase") {
    group = "application"
    description = "Rebuild the deterministic Skyforge showcase world, then launch Minecraft directly into it."
    dependsOn("runShowcasePrepare", "runShowcaseClient")
}
