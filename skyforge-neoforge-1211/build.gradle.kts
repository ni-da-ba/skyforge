import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion

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
            gameDirectory = project.file("run-sf-imp-0036")
            systemProperty("skyforge.dev.specimen", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0046 uses its own game directory and a self-checking bowl specimen so manual
        // foundation evidence cannot be confused with the earlier naturally supported Massif.
        create("accommodationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0046")
            systemProperty("skyforge.dev.accommodation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0050 reuses the isolated mansion/island fixture but adds development-only detached
        // underside geometry to the admission evidence stream. No synthetic piece is serialized.
        create("undersideContradictionClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0050")
            systemProperty("skyforge.dev.undersideContradiction", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0052 proves that the base-world generation stream completes before Skyforge is
        // physically realized. The origin chunk is self-checking and the forced development mansion
        // should remain native-ground-owned beneath the floating Massif.
        create("domainIsolationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0052")
            systemProperty("skyforge.dev.domainIsolation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0053 places the same native minecraft:oak_checked PlacedFeature on two independent
        // vertically aligned Skyforge volumes in the origin chunk. The runtime self-checks exact
        // surface ownership, independent operation seeds and successful bounded native writes.
        create("populationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0053")
            systemProperty("skyforge.dev.population", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0054 resolves two different final-registry Minecraft biomes for vertically aligned
        // island domains and executes each biome's own VEGETAL_DECORATION feature list. Native
        // placement modifiers choose occurrence positions; no individual tree origin is hard-coded.
        create("biomePopulationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0054")
            systemProperty("skyforge.dev.biomePopulation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0055 reuses the accepted stacked forest/taiga specimen through the reusable native
        // surface-population stage. The fixture immediately replays each volume/chunk/phase request
        // and fails unless the coordinator performs zero duplicate native feature executions.
        create("surfacePopulationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0055")
            systemProperty("skyforge.dev.surfacePopulation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0056 proves whole-volume physical admission against the same forced native mansion
        // environment that exposed the original block-entity overwrite. A lower island must reject
        // without mutation while a clear multi-chunk upper island admits and catches up exactly.
        create("physicalAdmissionClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0056")
            systemProperty("skyforge.dev.physicalAdmission", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0058 layers durable client-visible biome presentation onto the accepted 0056
        // admission specimen. The admitted upper island maps to taiga in Minecraft biome storage;
        // vertically unrelated native cells in the same X/Z column must remain unchanged.
        create("biomePresentationClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0058")
            systemProperty("skyforge.dev.physicalAdmission", "true")
            systemProperty("skyforge.dev.biomePresentation", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // SF-IMP-0059 maps Minecraft's native UNDERGROUND_ORES height samples into the admitted
        // high-volume 0056/0058 fixture and proves optimized raw-section writes remain exact-volume
        // isolated from BASE_WORLD terrain.
        create("undergroundPlacementClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0059")
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
            gameDirectory = project.file("run-sf-imp-0060")
            systemProperty("skyforge.dev.localModifications", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Final SF-IMP-0060 stacked-domain gate. Reuse the accepted 0054 vertically aligned
        // forest/taiga tablelands, map the same LOCAL_MODIFICATIONS height sample independently
        // into each exact solid owner column, and explicitly reject the other island at preflight.
        create("localModificationsStackedClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0060-stacked")
            systemProperty("skyforge.dev.localModificationsStacked", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Final SF-IMP-0059 stacked-domain gate. Reuse the accepted 0054 forest/taiga tablelands,
        // execute UNDERGROUND_ORES independently in both exact Y frames, preserve the other island
        // byte-for-byte during each replay and explicitly reject its solid coordinates at preflight.
        create("undergroundPlacementStackedClient") {
            client()
            gameDirectory = project.file("run-sf-imp-0059-stacked")
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
