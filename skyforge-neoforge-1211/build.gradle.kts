Warning: truncated output (original token count: 108804)
Total output lines: 9149

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

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


// #495 real-provider probe must load Aerodynamics4MC as a discovered NeoForge mod. The older C3
// AdditionalRuntimeClasspath profiles are sufficient for loader/dependency preflight but are a
// legacy-library path; public gameplay API classes must live in the run source set runtime.
val waveC3ProbeRuntime = sourceSets.create("waveC3ProbeRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


// #495 final reconstruction/provenance fixture: combine only the already accepted atmosphere
// authority and the two accepted Bootstrap consumer mod stacks. This source set is evidence-only;
// production Skyforge keeps those optional integrations isolated.
val waveC3EvidenceRuntime = sourceSets.create("waveC3EvidenceRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


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


// Wave C7 keeps Reliable Gliders + A4MC isolated from both production Skyforge and the C5/C6 bird
// stack. Optional APIs are consumed reflectively, but FML still requires the candidate jars on a
// real run source-set runtime classpath so they are discovered as NeoForge mods.
val waveC7Runtime = sourceSets.create("waveC7Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


// Wave C9 isolates the computing/avionics specimen from production and from other content waves.
// These jars must be discovered as NeoForge mods, not ordinary legacy libraries.
val waveC9Runtime = sourceSets.create("waveC9Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


// C11 resumes the reserved historical first-flight recipe proof on current main. It loads only the
// exact retained Create/Sable/Aeronautics substrate and remains isolated from production runtime.
val waveC11Runtime = sourceSets.create("waveC11Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// C14 adds an explicit flight-only A/B baseline. It reuses the exact C9 flight substrate but omits
// CC:Tweaked and Create: Avionics so the capability specimen also proves computing remains optional.
val waveC14FlightBaselineRuntime = sourceSets.create("waveC14FlightBaselineRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// C15 keeps the accepted 1:1 Nether datapack under the retained flight stack while exercising
// vanilla portal search/placement. No portal-overhaul mod is introduced.
val waveC15PortalRuntime = sourceSets.create("waveC15PortalRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


// C25 isolates the retained Create + Create: Diesel Generators petroleum stack from every other
// content/implementation wave. The candidate jar is validation-only until its authority boundary
// is accepted and handed to Implementation.
val waveC25Runtime = sourceSets.create("waveC25Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}


// SF-IMP-0084 deliberately keeps AAL outside Skyforge's production configurations and ordinary
// development runs. This source set exists solely to compile/link the exact released artifact in
// CI before any runtime/adaptation claim is made. Its runtime classpath is separate so a future
// disposable AAL run cannot accidentally alter the regular Skyforge runtime.
val aalValidation = sourceSets.create("aalValidation") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// Wave C13 performs a black-box baseline-vs-suppressed Elytra test. Only the suppressed run loads
// the pinned No More Elytra Boosting jar; ordinary Skyforge and the baseline acceptance stay vanilla.
val waveC13Runtime = sourceSets.create("waveC13Runtime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// WBY Wave 1 external mods must be FML-discovered, not merely present on a legacy
// AdditionalRuntimeClasspath. Keep client and server profiles isolated because Sodium and DH are
// client-side while SSRD and the retained flight substrate participate on both sides.
val wbyWave1VisibilityClientRuntime = sourceSets.create("wbyWave1VisibilityClientRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

val wbyWave1VisibilityServerRuntime = sourceSets.create("wbyWave1VisibilityServerRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// #1433 converges the canonical alpha stack without re-running every historical component proof.
// Client/server source sets remain side-aware so client renderers never become server requirements.
val wbyAlphaClientRuntime = sourceSets.create("wbyAlphaClientRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}
val wbyAlphaServerRuntime = sourceSets.create("wbyAlphaServerRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// Isolated FML-discovered runtime for the exact-volume modded-biome experiment.
val moddedBiomePopulationClientRuntime = sourceSets.create("moddedBiomePopulationClientRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// Dedicated-server counterpart for machine acceptance of the same selected modded biome.
val moddedBiomePopulationServerRuntime = sourceSets.create("moddedBiomePopulationServerRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath +=
        sourceSets.main.get().output +
        sourceSets.main.get().runtimeClasspath +
        development.output
}

// S0.5A layers pack-integration authority onto the accepted S0 parent.
val wbyS05ClientRuntime = sourceSets.create("wbyS05ClientRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath + development.output
}
val wbyS05ServerRuntime = sourceSets.create("wbyS05ServerRuntime") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath + development.output
}


// WBY Wave 1 isolates the long-range visibility substrate from shaders and broad optimizers.
val wbyWave1PinFile = layout.projectDirectory.file("wby-wave1-mods.properties")
val wbyWave1Pins = Properties().apply {
    wbyWave1PinFile.asFile.inputStream().use(::load)
}

fun wbyWave1Pin(mod: String, field: String): String =
    requireNotNull(wbyWave1Pins.getProperty("$mod.$field")) {
        "missing WBY Wave 1 pin: $mod.$field in " + wbyWave1PinFile.asFile
    }

check(wbyWave1Pin("minecraft", "version") == "1.21.1") {
    "WBY Wave 1 is defined only for Minecraft 1.21.1"
}
check(wbyWave1Pin("neoforge", "version") == "21.1.249") {
    "WBY Wave 1 NeoForge pin must match the adapter runtime"
}

val wbyWave1FlightMods = listOf("create", "sable", "aeronautics")
val wbyWave1ClientMods = listOf("sodium", "distanthorizons", "ssrd")
val wbyWave1ServerMods = listOf("ssrd")

// PT-02 is the first post-W1 capability overlay. It reuses the already accepted C2 Reliable
// Gliders pin and C3 A4MC authority pin without contaminating the ordinary W1 baseline.
val wbyPt02Glider = providers.gradleProperty("wbyPt02Glider")
    .orNull
    ?.trim()
    ?.equals("true", ignoreCase = true)
    ?: false

// PT-02B is a policy overlay on top of accepted PT-02A. It suppresses only Elytra firework
// propulsion through the already black-box-qualified No More Elytra Boosting artifact.
val wbyPt02ElytraSuppression = providers.gradleProperty("wbyPt02ElytraSuppression")
    .orNull
    ?.trim()
    ?.equals("true", ignoreCase = true)
    ?: false

check(!wbyPt02ElytraSuppression || wbyPt02Glider) {
    "WBY PT-02 Elytra suppression requires the accepted PT-02 glider overlay"
}

// PT-03 is the first engineering-content overlay on the accepted W1 + PT-02 substrate.
// It admits only the already qualified C9/C14 computing and avionics capability.
val wbyPt03ComputingAvionics = providers.gradleProperty("wbyPt03ComputingAvionics")
    .orNull
    ?.trim()
    ?.equals("true", ignoreCase = true)
    ?: false

check(!wbyPt03ComputingAvionics || (wbyPt02Glider && wbyPt02ElytraSuppression)) {
    "WBY PT-03 computing/avionics requires the accepted final PT-02B profile"
}

// W1-B is an opt-in overlay on the accepted W1-A baseline. Ordinary W1-A workflows do not
// resolve or stage any optimizer unless the qualification invocation explicitly supplies
// -PwbyWave1BOptimizer=<candidate>.
val wbyWave1BPinFile = layout.projectDirectory.file("wby-wave1b-optimizer-mods.properties")
val wbyWave1BPins = Properties().apply {
    wbyWave1BPinFile.asFile.inputStream().use(::load)
}

fun wbyWave1BPin(mod: String, field: String): String =
    requireNotNull(wbyWave1BPins.getProperty("$mod.$field")) {
        "missing WBY Wave 1B pin: $mod.$field in " + wbyWave1BPinFile.asFile
    }

check(wbyWave1BPin("minecraft", "version") == "1.21.1") {
    "WBY Wave 1B is defined only for Minecraft 1.21.1"
}
check(wbyWave1BPin("neoforge", "version") == "21.1.249") {
    "WBY Wave 1B NeoForge pin must match the adapter runtime"
}

val wbyWave1BSupportedOptimizers = setOf("lithium", "ferritecore", "immediatelyfast", "dynamicfps")
val wbyWave1BOptimizer = providers.gradleProperty("wbyWave1BOptimizer")
    .orNull
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

check(wbyWave1BOptimizer == null || wbyWave1BOptimizer in wbyWave1BSupportedOptimizers) {
    "unsupported WBY Wave 1B optimizer '$wbyWave1BOptimizer'; expected one of $wbyWave1BSupportedOptimizers"
}

// W1-B admission is cumulative. Each later candidate is tested on top of every earlier accepted
// optimizer so the screen measures the actual retained stack rather than an independent alternative.
val wbyWave1BOptimizerMods = when (wbyWave1BOptimizer) {
    null -> emptyList()
    "lithium" -> listOf("lithium")
    "ferritecore" -> listOf("lithium", "ferritecore")
    "immediatelyfast" -> listOf("lithium", "ferritecore", "immediatelyfast")
    "dynamicfps" -> listOf("lithium", "ferritecore", "immediatelyfast", "dynamicfps")
    else -> error("unreachable WBY Wave 1B optimizer '$wbyWave1BOptimizer'")
}

val wbyWave1BAllowedEnvironments = setOf("client", "server", "client-server")
wbyWave1BOptimizerMods.forEach { mod ->
    check(wbyWave1BPin(mod, "environment") in wbyWave1BAllowedEnvironments) {
        "unsupported WBY Wave 1B environment for $mod: " + wbyWave1BPin(mod, "environment")
    }
}
val wbyWave1BClientOptimizerMods = wbyWave1BOptimizerMods.filter { mod ->
    wbyWave1BPin(mod, "environment") != "server"
}
val wbyWave1BServerOptimizerMods = wbyWave1BOptimizerMods.filter { mod ->
    wbyWave1BPin(mod, "environment") != "client"
}


val waveC9PinFile = layout.projectDirectory.file("wave-c9-mods.properties")
val waveC9Pins = Properties().apply {
    waveC9PinFile.asFile.inputStream().use(::load)
}

fun waveC9Pin(mod: String, field: String): String =
    requireNotNull(waveC9Pins.getProperty("$mod.$field")) {
        "missing Wave C9 pin: $mod.$field in " + waveC9PinFile.asFile
    }

check(waveC9Pin("minecraft", "version") == "1.21.1") {
    "Wave C9 is defined only for Minecraft 1.21.1"
}
check(waveC9Pin("neoforge", "version") == "21.1.249") {
    "Wave C9 NeoForge pin must match the adapter runtime"
}


val waveC25PinFile = layout.projectDirectory.file("wave-c25-mods.properties")
val waveC25Pins = Properties().apply {
    waveC25PinFile.asFile.inputStream().use(::load)
}

fun waveC25Pin(mod: String, field: String): String =
    requireNotNull(waveC25Pins.getProperty("$mod.$field")) {
        "missing Wave C25 pin: $mod.$field in " + waveC25PinFile.asFile
    }

check(waveC25Pin("minecraft", "version") == "1.21.1") {
    "Wave C25 is defined only for Minecraft 1.21.1"
}
check(waveC25Pin("neoforge", "version") == "21.1.249") {
    "Wave C25 NeoForge pin must match the adapter runtime"
}

// The AAL artifact identity is supplied by #441's immutable released-artifact evidence. This is
// intentionally a validation manifest, not a production dependency declaration or API contract.
val sfImp0084AalPinFile = layout.projectDirectory.file("sf-imp-0084-aal.properties")
val sfImp0084AalPins = Properties().apply {
    sfImp0084AalPinFile.asFile.inputStream().use(::load)
}

fun sfImp0084AalPin(field: String): String =
    requireNotNull(sfImp0084AalPins.getProperty("$field")) {
        "missing SF-IMP-0084 AAL pin: $field in " + sfImp0084AalPinFile.asFile
    }

check(sfImp0084AalPin("minecraft.version") == "1.21.1") {
    "SF-IMP-0084 AAL validation is defined only for Minecraft 1.21.1"
}
check(sfImp0084AalPin("neoforge.version") == "21.1.249") {
    "SF-IMP-0084 AAL validation must match the adapter runtime"
}
check(sfImp0084AalPin("coordinate") == "maven.modrinth:73ZXeRfx:EgPi4wq9") {
    "SF-IMP-0084 must use the exact AAL released coordinate authorized by #441"
}

// S0 is deliberately narrow: accepted physical/runtime shell plus JEI,
// long-range visibility, and the already accepted conservative optimizers.
// Later capability/content slices must not leak into this parent profile.
val wbyAlphaEngineeringMods = listOf("create", "sable", "aeronautics", "jei")
val wbyAlphaClientVisibilityMods = listOf("sodium", "distanthorizons", "ssrd")
val wbyAlphaServerVisibilityMods = listOf("ssrd")
val wbyAlphaClientOptimizerMods = listOf("lithium", "ferritecore", "immediatelyfast", "dynamicfps")
val wbyAlphaServerOptimizerMods = listOf("lithium", "ferritecore")

val wbyS05PinFile = layout.projectDirectory.file("wby-s0-5-pack-authority.properties")
val wbyS05Pins = Properties().apply { wbyS05PinFile.asFile.inputStream().use(::load) }
check(wbyS05Pins.getProperty("minecraft.version") == "1.21.1")
check(wbyS05Pins.getProperty("neoforge.version") == "21.1.249")
fun wbyS05Pin(mod: String, field: String): String =
    requireNotNull(wbyS05Pins.getProperty("$mod.$field")) { "missing WBY S0.5 pin: $mod.$field" }
fun wbyS05Token(mod: String): String =
    wbyS05Pins.getProperty("$mod.artifactToken") ?: wbyS05Pin(mod, "coordinate").split(":").let { parts ->
        check(parts.size == 3)
        parts[1] + "-" + parts[2]
    }
val wbyS05PackAuthorityMods = listOf(
    "kubejs", "rhino", "betteradvancedtooltips", "kubejscreate", "lootjs", "paxi", "yungsapi", "almostunified",
)

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

// #1142 R&D only: CI may substitute a source-built A4MC core containing the proposed public
// terrain-provider seam. Ordinary runs omit this property and retain the immutable released pin.
val waveC3ProbeAeroCoreOverrideJar = providers.gradleProperty("skyforgeA4mcProbeCoreOverrideJar")
val waveC3ProbeAeroCoreFiles = if (waveC3ProbeAeroCoreOverrideJar.isPresent) {
    files(waveC3ProbeAeroCoreOverrideJar.get())
} else {
    files(waveC3AeroCoreArtifact)
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

val skyforgeProductionMorphologyAtlasMembers = linkedMapOf(
    "tableland" to "builtin-tableland-small-seed-skyforge",
    "spine" to "builtin-spine-small-seed-skyforge",
    "basin" to "builtin-basin-small-seed-skyforge",
    "lobed" to "builtin-lobed-small-seed-skyforge",
)

fun skyforgeMorphologyAtlasSuffix(family: String): String =
    family.substring(0, 1).uppercase() + family.substring(1)

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

        // F4K visual gate follows the established prepare-then-quick-play pattern. The dedicated
        // server writes a bounded F4H/F4D specimen; the viewer then opens only that persisted world.
        create("f4kHydrologyReviewPrepare") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-f4k-hydrology-review").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("f4k-hydrology-review")
            systemProperty("skyforge.dev.f4kHydrologyReview", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0043-f4k-hydrology-prepare")
            systemProperty("skyforge.dev.acceptanceRadius", "7")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "420")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/f4k-hydrology-review/prepare.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("f4kHydrologyReviewClient") {
            client()
            gameDirectory = layout.projectDirectory.dir("run-f4k-hydrology-review").asFile
            programArgument("--quickPlaySingleplayer")
            programArgument("f4k-hydrology-review")
            systemProperty("skyforge.dev.f4kHydrologyReviewViewer", "true")
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

        // SF-IMP-0054 local review client. Stage the same provider-backed biome stack used by the
        // dedicated-server specimen so downloaded acceptance saves open with matching registries.
        create("moddedBiomePopulationClient") {
            client()
            sourceSet.set(moddedBiomePopulationClientRuntime)
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0054-modded-biome").asFile
            systemProperty("skyforge.dev.biomePopulation", "true")
            systemProperty("skyforge.dev.biomePopulation.upperBiome", "biomesoplenty:rocky_shrubland")
            systemProperty("skyforge.dev.biomePopulation.providerBiome", "true")
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("moddedBiomePopulationServer") {
            server()
            sourceSet.set(moddedBiomePopulationServerRuntime)
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0054-modded-biome-server").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.biomePopulation", "true")
            systemProperty("skyforge.dev.biomePopulation.upperBiome", "biomesoplenty:rocky_shrubland")
            systemProperty("skyforge.dev.biomePopulation.providerBiome", "true")
            systemProperty("skyforge.dev.biomePopulation.waitForRemoteClient", "true")
            systemProperty(
                "skyforge.dev.biomePopulation.clientResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0054-modded-biome/client.properties").get().asFile.absolutePath,
            )
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0054-modded-biome")
            systemProperty("skyforge.dev.acceptanceRadius", "4")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "600")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0054-modded-biome/server.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        // Reopen the same dedicated-server save after the remote client accepted the first boot.
        create("moddedBiomePopulationServerReopen") {
            server()
            sourceSet.set(moddedBiomePopulationServerRuntime)
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0054-modded-biome-server").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.biomePopulation", "true")
            systemProperty("skyforge.dev.biomePopulation.upperBiome", "biomesoplenty:rocky_shrubland")
            systemProperty("skyforge.dev.biomePopulation.providerBiome", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0054-modded-biome-reopen")
            systemProperty("skyforge.dev.acceptanceRadius", "4")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "600")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0054-modded-biome/reopen.properties").get().asFile.absolutePath,
            )
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("moddedBiomePopulationJoinClient") {
            client()
            sourceSet.set(moddedBiomePopulationClientRuntime)
            gameDirectory = layout.projectDirectory.dir("run-sf-imp-0054-modded-biome-join-client").asFile
            systemProperty("skyforge.dev.biomePopulationJoinClient", "true")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "client")
            systemProperty("skyforge.dev.acceptanceCase", "sf-imp-0054-modded-biome-join")
            systemProperty(
                "skyforge.dev.acceptanceResultFile",
                layout.buildDirectory.file("acceptance/sf-imp-0054-modded-biome/client.properties").get().asFile.absolutePath,
            )
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

        // DR-30 CI-only native-structure qualification. Placement and reload share one disposable
        // world; stacked isolation uses a second world with vertically aligned exact volumes.
        create("dr30NativeStructureAcceptancePlace") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-dr30-native-structure").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.dr30NativeStructureAcceptance", "true")
            systemProperty("skyforge.dev.dr30NativeStructureMode", "single")
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "dr-30-native-structure-place")
            systemProperty("skyforge.dev.acceptanceRadius", "8")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "300")
            systemProperty("skyforge.dev.acceptanceResultFile", layout.buildDirectory.file("acceptance/dr-30-native-structure/place.properties").get().asFile.absolutePath)
            taskBefore(tasks.named(development.processResourcesTaskName))
        }

        create("dr30NativeStructureAcceptanceReload") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-dr30-native-structure").asFile
            programArgument("--nogui")
            programArgument("--universe")
            programArgument("saves")
            programArgument("--world")
            programArgument("acceptance")
            systemProperty("skyforge.dev.dr30NativeStructureAcceptance", "true")
            systemProperty("skyforge.dev.dr30NativeStructureMode", "reload")
            systemProperty("skyforge.dev.dr30NativeStructureExpectedResultFile", layout.buildDirectory.file("acceptance/dr-30-native-structure/place.properties").get().asFile.absolutePath)
            systemProperty("skyforge.dev.acceptanceHarness", "true")
            systemProperty("skyforge.dev.acceptanceMode", "server")
            systemProperty("skyforge.dev.acceptanceCase", "dr-30-native-structure-reload")
            systemProperty("skyforge.dev.acceptanceRadius", "0")
            systemProperty("skyforge.dev.acceptanceTimeoutSeconds", "120")
            syst…88804 tokens truncated…mpCalls, " +
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

val skyforgeShowcaseEcologyResultDirectory = layout.buildDirectory.dir("acceptance/showcase-ecology")
val skyforgeShowcaseEcologyServerProperties = """
    level-name=ecology
    // Land-backed deterministic base: Java 1.21 seed with a broad plains surface around (0,0).
    // Native-surface adaptation therefore exercises Minecraft-owned grass/dirt representation
    // instead of inheriting an ocean-floor gravel/sand top that cannot host forest/taiga ecology.
    level-seed=1405130932537311389
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    allow-flight=true
    view-distance=10
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSkyforgeShowcaseEcologyDirectory() {
    val directory = layout.projectDirectory.dir("run-skyforge-showcase-ecology").asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(skyforgeShowcaseEcologyServerProperties)
}

fun requireSkyforgeShowcaseEcologyPreparationPass() {
    val file = skyforgeShowcaseEcologyResultDirectory.get().file("prepare.properties").asFile
    check(file.isFile) { "Skyforge ecology showcase preparation result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("lowerState") == "ADMITTED"
            && properties.getProperty("upperState") == "ADMITTED"
            && properties.getProperty("lowerObserved") == properties.getProperty("lowerRequired")
            && properties.getProperty("upperObserved") == properties.getProperty("upperRequired")
            && properties.getProperty("lowerPopulationChunks") == properties.getProperty("lowerExpectedPopulationChunks")
            && properties.getProperty("upperPopulationChunks") == properties.getProperty("upperExpectedPopulationChunks")
            && properties.getProperty("lowerSuccessful").toInt() > 0
            && properties.getProperty("upperSuccessful").toInt() > 0
            && properties.getProperty("distinctFeatureIdentity") == "true"
            && properties.getProperty("lowerSubstrate").toInt() > 0
            && properties.getProperty("lowerLogs").toInt() > 0
            && properties.getProperty("lowerLeaves").toInt() > 0
            && properties.getProperty("lowerPlants").toInt() > 0
            && properties.getProperty("upperSubstrate").toInt() > 0
            && properties.getProperty("upperLogs").toInt() > 0
            && properties.getProperty("upperLeaves").toInt() > 0
            && properties.getProperty("upperPlants").toInt() > 0
            && properties.getProperty("persistentBiomePresentation") == "true"
            && properties.getProperty("pendingCatchup") == "0"
            && properties.getProperty("pendingBiomePresentation") == "0") {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "Skyforge ecology showcase preparation did not PASS: $detail"
    }
}

fun requireSkyforgeShowcaseEcologyViewerPass() {
    val file = skyforgeShowcaseEcologyResultDirectory.get().file("viewer.properties").asFile
    check(file.isFile) { "Skyforge ecology showcase viewer result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("viewerTerrainOwnershipRestored") == "true"
            && properties.getProperty("viewerMutationBindingsInert") == "true"
            && properties.getProperty("viewerPersistentForest") == "true"
            && properties.getProperty("viewerPersistentTaiga") == "true"
            && properties.getProperty("viewerLowerSubstrate").toInt() > 0
            && properties.getProperty("viewerLowerLogs").toInt() > 0
            && properties.getProperty("viewerLowerLeaves").toInt() > 0
            && properties.getProperty("viewerLowerPlants").toInt() > 0
            && properties.getProperty("viewerUpperSubstrate").toInt() > 0
            && properties.getProperty("viewerUpperLogs").toInt() > 0
            && properties.getProperty("viewerUpperLeaves").toInt() > 0
            && properties.getProperty("viewerUpperPlants").toInt() > 0
            && properties.getProperty("viewerClientPass") == "true") {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "Skyforge ecology showcase viewer did not PASS: $detail"
    }
}

tasks.named("runShowcaseEcologyPrepare").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask and ecology-showcase filesystem orchestration are intentionally runtime-bound.",
    )
    doFirst {
        delete(skyforgeShowcaseEcologyResultDirectory)
        prepareSkyforgeShowcaseEcologyDirectory()
    }
    doLast {
        requireSkyforgeShowcaseEcologyPreparationPass()
    }
}

tasks.named("runShowcaseEcologyClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is interactive and intentionally not configuration-cache serialized.",
    )
    mustRunAfter("runShowcaseEcologyPrepare")
    doFirst {
        val directory = layout.projectDirectory.dir("run-skyforge-showcase-ecology").asFile
        check(directory.resolve("saves/ecology/level.dat").isFile) {
            "Skyforge ecology showcase world is missing; run runShowcaseEcologyPrepare first or use launchShowcaseEcology."
        }
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.named("runShowcaseEcologyViewerAcceptanceClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is an actual quick-play ecology acceptance process.",
    )
    mustRunAfter("runShowcaseEcologyPrepare")
    doFirst {
        requireSkyforgeShowcaseEcologyPreparationPass()
        val directory = layout.projectDirectory.dir("run-skyforge-showcase-ecology").asFile
        check(directory.resolve("saves/ecology/level.dat").isFile) {
            "Skyforge ecology showcase world is missing; prepare it before viewer acceptance."
        }
        delete(skyforgeShowcaseEcologyResultDirectory.get().file("viewer.properties").asFile)
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.register("showcaseEcologyPrepareVerify") {
    group = "verification"
    description = "Verify the persisted SF-IMP-0080 forest/taiga ecology specimen."
    dependsOn("runShowcaseEcologyPrepare")
    doLast {
        requireSkyforgeShowcaseEcologyPreparationPass()
        println(
            "SF-IMP-0080 SHOWCASE ECOLOGY PREPARATION PASS: persisted forest/taiga world is ready for human review.",
        )
    }
}

tasks.register("showcaseEcologyViewerVerify") {
    group = "verification"
    description = "Reopen the SF-IMP-0080 ecology world in an actual client and verify persistence."
    dependsOn("runShowcaseEcologyViewerAcceptanceClient")
    doLast {
        requireSkyforgeShowcaseEcologyViewerPass()
        println(
            "SF-IMP-0080 SHOWCASE ECOLOGY VIEWER PASS: persisted land ecology survived mutation-inert actual-client reopen.",
        )
    }
}

tasks.register("launchShowcaseEcology") {
    group = "application"
    description = "Rebuild the dedicated forest/taiga ecology specimen, then launch Minecraft into it for human review."
    dependsOn("runShowcaseEcologyPrepare", "runShowcaseEcologyClient")
}


val skyforgeProductionMorphologyMassifResultDirectory =
    layout.buildDirectory.dir("acceptance/production-morphology-massif")
val skyforgeProductionMorphologyMassifServerProperties = """
    level-name=morphology-massif
    level-seed=1405130932537311389
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=spectator
    difficulty=peaceful
    allow-flight=true
    view-distance=12
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSkyforgeProductionMorphologyMassifDirectory() {
    val directory = layout.projectDirectory.dir("run-skyforge-production-morphology-massif").asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(skyforgeProductionMorphologyMassifServerProperties)
}

fun requireSkyforgeProductionMorphologyMassifPreparationPass() {
    val file = skyforgeProductionMorphologyMassifResultDirectory.get().file("prepare.properties").asFile
    check(file.isFile) { "Skyforge production morphology preparation result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    val claimed = properties.getProperty("sampledClaims")?.toIntOrNull() ?: 0
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("memberId") == "builtin-massif-small-seed-skyforge"
            && properties.getProperty("state") == "ADMITTED"
            && properties.getProperty("observed") == properties.getProperty("required")
            && properties.getProperty("pendingCatchup") == "0"
            && properties.getProperty("minimumY") == "96"
            && properties.getProperty("maximumY").toInt() < 320
            && claimed > 0
            && properties.getProperty("storedTop").toInt() == claimed
            && properties.getProperty("airAbove").toInt() == claimed
            && properties.getProperty("storedUnderside").toInt() == claimed
            && properties.getProperty("airBelow").toInt() == claimed
            && properties.getProperty("heightMismatches") == "0"
            && properties.getProperty("landTop").toInt() > 0
            && properties.getProperty("grassTop").toInt() > 0
            && !properties.getProperty("surfaceDigest").isNullOrBlank()) {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "Skyforge production morphology preparation did not PASS: $detail"
    }
}

fun requireSkyforgeProductionMorphologyMassifViewerPass() {
    val prepareFile = skyforgeProductionMorphologyMassifResultDirectory.get().file("prepare.properties").asFile
    val viewerFile = skyforgeProductionMorphologyMassifResultDirectory.get().file("viewer.properties").asFile
    check(prepareFile.isFile) { "Skyforge production morphology preparation evidence missing: $prepareFile" }
    check(viewerFile.isFile) { "Skyforge production morphology viewer result missing: $viewerFile" }
    val prepare = Properties()
    val viewer = Properties()
    prepareFile.inputStream().use(prepare::load)
    viewerFile.inputStream().use(viewer::load)
    val claimed = viewer.getProperty("viewerSampledClaims")?.toIntOrNull() ?: 0
    check(viewer.getProperty("status") == "PASS"
            && viewer.getProperty("viewerMemberId") == "builtin-massif-small-seed-skyforge"
            && viewer.getProperty("viewerTerrainOwnershipRestored") == "true"
            && viewer.getProperty("viewerMutationBindingsInert") == "true"
            && viewer.getProperty("viewerClientPass") == "true"
            && claimed > 0
            && viewer.getProperty("viewerStoredTop").toInt() == claimed
            && viewer.getProperty("viewerAirAbove").toInt() == claimed
            && viewer.getProperty("viewerStoredUnderside").toInt() == claimed
            && viewer.getProperty("viewerAirBelow").toInt() == claimed
            && viewer.getProperty("viewerHeightMismatches") == "0"
            && viewer.getProperty("viewerLandTop").toInt() > 0
            && viewer.getProperty("viewerGrassTop").toInt() > 0
            && viewer.getProperty("viewerSurfaceDigest") == prepare.getProperty("surfaceDigest")) {
        val detail = viewer.getProperty("failure") ?: viewer.toString()
        "Skyforge production morphology viewer did not PASS: $detail"
    }
}

tasks.named("runProductionMorphologyMassifPrepare").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask and production-morphology filesystem orchestration are runtime-bound.",
    )
    doFirst {
        delete(skyforgeProductionMorphologyMassifResultDirectory)
        prepareSkyforgeProductionMorphologyMassifDirectory()
    }
    doLast {
        requireSkyforgeProductionMorphologyMassifPreparationPass()
    }
}

tasks.named("runProductionMorphologyMassifClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is interactive and intentionally not configuration-cache serialized.",
    )
    mustRunAfter("runProductionMorphologyMassifPrepare")
    doFirst {
        val directory = layout.projectDirectory.dir("run-skyforge-production-morphology-massif").asFile
        check(directory.resolve("saves/morphology-massif/level.dat").isFile) {
            "Skyforge production morphology world is missing; prepare it first or use launchProductionMorphologyMassif."
        }
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.named("runProductionMorphologyMassifViewerAcceptanceClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is an actual quick-play morphology acceptance process.",
    )
    mustRunAfter("runProductionMorphologyMassifPrepare")
    doFirst {
        requireSkyforgeProductionMorphologyMassifPreparationPass()
        val directory = layout.projectDirectory.dir("run-skyforge-production-morphology-massif").asFile
        check(directory.resolve("saves/morphology-massif/level.dat").isFile) {
            "Skyforge production morphology world is missing; prepare it before viewer acceptance."
        }
        delete(skyforgeProductionMorphologyMassifResultDirectory.get().file("viewer.properties").asFile)
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.register("productionMorphologyMassifPrepareVerify") {
    group = "verification"
    description = "Verify the first exact AUTH-0083 production Massif Minecraft carrier."
    dependsOn("runProductionMorphologyMassifPrepare")
    doLast {
        requireSkyforgeProductionMorphologyMassifPreparationPass()
        println(
            "SF-IMP-0081 PRODUCTION MORPHOLOGY MASSIF PREPARATION PASS: exact AUTH-0083 member persisted.",
        )
    }
}

tasks.register("productionMorphologyMassifViewerVerify") {
    group = "verification"
    description = "Reopen the first production Massif in an actual client and verify persisted geometry."
    dependsOn("runProductionMorphologyMassifViewerAcceptanceClient")
    doLast {
        requireSkyforgeProductionMorphologyMassifViewerPass()
        println(
            "SF-IMP-0081 PRODUCTION MORPHOLOGY MASSIF VIEWER PASS: sampled top/underside geometry survived reopen.",
        )
    }
}

tasks.register("launchProductionMorphologyMassif") {
    group = "application"
    description = "Rebuild the first AUTH-0083 production Massif carrier, then launch Minecraft for #214 review."
    dependsOn("runProductionMorphologyMassifPrepare", "runProductionMorphologyMassifClient")
}


val skyforgeProductionMorphologyAtlasServerProperties = """
    level-seed=1405130932537311389
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=spectator
    difficulty=peaceful
    allow-flight=true
    view-distance=12
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun skyforgeProductionMorphologyAtlasResultDirectory(family: String) =
    layout.buildDirectory.dir("acceptance/production-morphology-atlas/$family")

fun skyforgeProductionMorphologyAtlasGameDirectory(family: String) =
    layout.projectDirectory.dir("run-skyforge-production-morphology-atlas-$family").asFile

fun prepareSkyforgeProductionMorphologyAtlasDirectory(family: String) {
    val directory = skyforgeProductionMorphologyAtlasGameDirectory(family)
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(
        "level-name=morphology-atlas-$family\n" + skyforgeProductionMorphologyAtlasServerProperties,
    )
}

fun requireSkyforgeProductionMorphologyAtlasPreparationPass(
    family: String,
    memberId: String,
) {
    val file =
        skyforgeProductionMorphologyAtlasResultDirectory(family).get().file("prepare.properties").asFile
    check(file.isFile) { "Skyforge production morphology atlas preparation result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    val claimed = properties.getProperty("sampledClaims")?.toIntOrNull() ?: 0
    val footprint = properties.getProperty("footprintChunks")?.toIntOrNull() ?: 0
    val required = properties.getProperty("required")?.toIntOrNull() ?: 0
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("memberId") == memberId
            && properties.getProperty("family") == family
            && properties.getProperty("state") == "ADMITTED"
            && properties.getProperty("observed") == properties.getProperty("required")
            && footprint > 0
            && footprint == required
            && properties.getProperty("pendingCatchup") == "0"
            && properties.getProperty("minimumY") == "96"
            && properties.getProperty("maximumY").toInt() < 320
            && claimed > 0
            && properties.getProperty("storedTop").toInt() == claimed
            && properties.getProperty("airAbove").toInt() == claimed
            && properties.getProperty("storedUnderside").toInt() == claimed
            && properties.getProperty("airBelow").toInt() == claimed
            && properties.getProperty("heightMismatches") == "0"
            && properties.getProperty("landTop").toInt() > 0
            && properties.getProperty("grassTop").toInt() > 0
            && !properties.getProperty("surfaceDigest").isNullOrBlank()) {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "Skyforge production morphology atlas $family preparation did not PASS: $detail"
    }
}

fun requireSkyforgeProductionMorphologyAtlasViewerPass(
    family: String,
    memberId: String,
) {
    val directory = skyforgeProductionMorphologyAtlasResultDirectory(family).get()
    val prepareFile = directory.file("prepare.properties").asFile
    val viewerFile = directory.file("viewer.properties").asFile
    check(prepareFile.isFile) { "Skyforge morphology atlas preparation evidence missing: $prepareFile" }
    check(viewerFile.isFile) { "Skyforge morphology atlas viewer result missing: $viewerFile" }

    val prepare = Properties()
    val viewer = Properties()
    prepareFile.inputStream().use(prepare::load)
    viewerFile.inputStream().use(viewer::load)
    val claimed = viewer.getProperty("viewerSampledClaims")?.toIntOrNull() ?: 0
    check(viewer.getProperty("status") == "PASS"
            && viewer.getProperty("viewerMemberId") == memberId
            && viewer.getProperty("viewerFamily") == family
            && viewer.getProperty("viewerTerrainOwnershipRestored") == "true"
            && viewer.getProperty("viewerMutationBindingsInert") == "true"
            && viewer.getProperty("viewerClientPass") == "true"
            && viewer.getProperty("viewerFootprintChunks") == prepare.getProperty("footprintChunks")
            && claimed > 0
            && viewer.getProperty("viewerStoredTop").toInt() == claimed
            && viewer.getProperty("viewerAirAbove").toInt() == claimed
            && viewer.getProperty("viewerStoredUnderside").toInt() == claimed
            && viewer.getProperty("viewerAirBelow").toInt() == claimed
            && viewer.getProperty("viewerHeightMismatches") == "0"
            && viewer.getProperty("viewerLandTop").toInt() > 0
            && viewer.getProperty("viewerGrassTop").toInt() > 0
            && viewer.getProperty("viewerSurfaceDigest") == prepare.getProperty("surfaceDigest")) {
        val detail = viewer.getProperty("failure") ?: viewer.toString()
        "Skyforge production morphology atlas $family viewer did not PASS: $detail"
    }
}

val skyforgeProductionMorphologyAtlasPrepareTasks = mutableListOf<String>()
val skyforgeProductionMorphologyAtlasViewerTasks = mutableListOf<String>()

for ((family, memberId) in skyforgeProductionMorphologyAtlasMembers) {
    val suffix = skyforgeMorphologyAtlasSuffix(family)
    val prepareRun = "runProductionMorphologyAtlas${suffix}Prepare"
    val clientRun = "runProductionMorphologyAtlas${suffix}Client"
    val viewerRun = "runProductionMorphologyAtlas${suffix}ViewerAcceptanceClient"
    val prepareVerify = "productionMorphologyAtlas${suffix}PrepareVerify"
    val viewerVerify = "productionMorphologyAtlas${suffix}ViewerVerify"
    val launch = "launchProductionMorphologyAtlas$suffix"

    tasks.named(prepareRun).configure {
        notCompatibleWithConfigurationCache(
            "NeoForge ModDev RunGameTask and morphology-atlas filesystem orchestration are runtime-bound.",
        )
        doFirst {
            delete(skyforgeProductionMorphologyAtlasResultDirectory(family))
            prepareSkyforgeProductionMorphologyAtlasDirectory(family)
        }
        doLast {
            requireSkyforgeProductionMorphologyAtlasPreparationPass(family, memberId)
        }
    }

    tasks.named(clientRun).configure {
        notCompatibleWithConfigurationCache(
            "NeoForge ModDev RunGameTask is interactive and intentionally not configuration-cache serialized.",
        )
        mustRunAfter(prepareRun)
        doFirst {
            val directory = skyforgeProductionMorphologyAtlasGameDirectory(family)
            check(directory.resolve("saves/morphology-atlas-$family/level.dat").isFile) {
                "Skyforge morphology atlas $family world is missing; prepare it first or use $launch."
            }
            directory.resolve("options.txt").writeText(
                "onboardAccessibility:false\n"
                    + "narrator:0\n",
            )
        }
    }

    tasks.named(viewerRun).configure {
        notCompatibleWithConfigurationCache(
            "NeoForge ModDev RunGameTask is an actual quick-play morphology-atlas acceptance process.",
        )
        mustRunAfter(prepareRun)
        doFirst {
            requireSkyforgeProductionMorphologyAtlasPreparationPass(family, memberId)
            val directory = skyforgeProductionMorphologyAtlasGameDirectory(family)
            check(directory.resolve("saves/morphology-atlas-$family/level.dat").isFile) {
                "Skyforge morphology atlas $family world is missing before viewer acceptance."
            }
            delete(
                skyforgeProductionMorphologyAtlasResultDirectory(family).get()
                    .file("viewer.properties").asFile,
            )
            directory.resolve("options.txt").writeText(
                "onboardAccessibility:false\n"
                    + "narrator:0\n",
            )
        }
    }

    tasks.register(prepareVerify) {
        group = "verification"
        description = "Verify exact AUTH-0083 $family production morphology carrier."
        dependsOn(prepareRun)
        doLast {
            requireSkyforgeProductionMorphologyAtlasPreparationPass(family, memberId)
            println("SF-IMP-0082 ${family.uppercase()} PREPARATION PASS: $memberId")
        }
    }

    tasks.register(viewerVerify) {
        group = "verification"
        description = "Reopen persisted AUTH-0083 $family carrier and verify exact geometry."
        dependsOn(viewerRun)
        doLast {
            requireSkyforgeProductionMorphologyAtlasViewerPass(family, memberId)
            println("SF-IMP-0082 ${family.uppercase()} VIEWER PASS: $memberId")
        }
    }

    tasks.register(launch) {
        group = "application"
        description = "Rebuild AUTH-0083 $family carrier, then launch Minecraft for #214 review."
        dependsOn(prepareRun, clientRun)
    }

    skyforgeProductionMorphologyAtlasPrepareTasks += prepareVerify
    skyforgeProductionMorphologyAtlasViewerTasks += viewerVerify
}

tasks.register("productionMorphologyAtlasPrepareVerify") {
    group = "verification"
    description = "Verify all four remaining AUTH-0083 built-in production morphology carriers."
    dependsOn(skyforgeProductionMorphologyAtlasPrepareTasks)
}

tasks.register("productionMorphologyAtlasViewerVerify") {
    group = "verification"
    description = "Actual-client persistence verification for all four remaining morphology carriers."
    dependsOn(skyforgeProductionMorphologyAtlasViewerTasks)
}

// DR-30 production native-structure runtime qualification. This boots real NeoForge server
// processes and persists/reopens a disposable world, so it stays outside ordinary unit CI.
val dr30NativeStructureResultDirectory = layout.buildDirectory.dir("acceptance/dr-30-native-structure")
val dr30NativeStructureServerProperties = """
    level-name=acceptance
    level-seed=493030
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=creative
    difficulty=peaceful
    view-distance=5
    simulation-distance=5
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareDr30NativeStructureServerDirectory(relativePath: String, stacked: Boolean = false) {
    val directory = layout.projectDirectory.dir(relativePath).asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(dr30NativeStructureServerProperties)
    if (stacked) {
        val pack = directory.resolve("saves/acceptance/datapacks/dr30-stacked-probe")
        pack.resolve("data/skyforge/worldgen/structure_set").mkdirs()
        pack.resolve("data/minecraft/tags/worldgen/biome/has_structure").mkdirs()
        pack.resolve("pack.mcmeta").writeText(
            """{"pack":{"pack_format":48,"description":"Skyforge DR-30 stacked native-structure probe"}}"""
        )
        // With level-seed 493030, spacing 512 / separation 510 and salt 493082
        // deterministically select random-spread offset (1,0) in placement region (0,0).
        // The existing development mansion set retains the lower probe at (0,0).
        pack.resolve("data/skyforge/worldgen/structure_set/dr30_upper_mansion.json").writeText(
            """
            {
              "placement": {
                "type": "minecraft:random_spread",
                "salt": 493082,
                "separation": 510,
                "spacing": 512
              },
              "structures": [
                {
                  "structure": "minecraft:mansion",
                  "weight": 1
                }
              ]
            }
            """.trimIndent() + "\n"
        )
    }
}

fun requireDr30NativeStructurePass(name: String): Properties {
    val file = dr30NativeStructureResultDirectory.get().file("$name.properties").asFile
    check(file.isFile) { "missing DR-30 native-structure acceptance result: $file" }
    val properties = Properties().also { result -> file.inputStream().use(result::load) }
    check(properties.getProperty("status") == "PASS") {
        "DR-30 native-structure case $name did not PASS: $properties"
    }
    return properties
}

tasks.named("runDr30NativeStructureAcceptancePlace").configure {
    notCompatibleWithConfigurationCache("DR-30 qualification owns a disposable persisted server world.")
    doFirst {
        delete(dr30NativeStructureResultDirectory)
        prepareDr30NativeStructureServerDirectory("run-dr30-native-structure")
    }
    doLast { requireDr30NativeStructurePass("place") }
}

tasks.named("runDr30NativeStructureAcceptanceReload").configure {
    notCompatibleWithConfigurationCache("DR-30 qualification reopens the prior server world.")
    mustRunAfter("runDr30NativeStructureAcceptancePlace")
    doFirst {
        val directory = layout.projectDirectory.dir("run-dr30-native-structure").asFile
        check(directory.resolve("saves/acceptance/level.dat").isFile) {
            "DR-30 placement world is missing before reload"
        }
    }
    doLast { requireDr30NativeStructurePass("reload") }
}

tasks.named("runDr30NativeStructureAcceptanceStacked").configure {
    notCompatibleWithConfigurationCache("DR-30 stacked qualification owns a disposable server world.")
    mustRunAfter("runDr30NativeStructureAcceptanceReload")
    doFirst { prepareDr30NativeStructureServerDirectory("run-dr30-native-structure-stacked", stacked = true) }
    doLast { requireDr30NativeStructurePass("stacked") }
}

tasks.register("dr30NativeStructureAcceptance") {
    group = "verification"
    description = "Qualify DR-30 native placement, reload mutation preservation, and stacked exact-volume identity."
    dependsOn(
        "runDr30NativeStructureAcceptancePlace",
        "runDr30NativeStructureAcceptanceReload",
        "runDr30NativeStructureAcceptanceStacked",
    )
    doLast {
        val place = requireDr30NativeStructurePass("place")
        val reload = requireDr30NativeStructurePass("reload")
        val stacked = requireDr30NativeStructurePass("stacked")
        check(place.getProperty("physicalStructureBlockObserved") == "true")
        check(reload.getProperty("mutationPreserved") == "true")
        check(reload.getProperty("secondProcessingDisposition") == "SKIP_COMPLETED_IDENTITY")
        check(reload.getProperty("mutationX") == place.getProperty("mutationX"))
        check(reload.getProperty("mutationY") == place.getProperty("mutationY"))
        check(reload.getProperty("mutationZ") == place.getProperty("mutationZ"))
        check(stacked.getProperty("exactVolumeIdentitiesDistinct") == "true")
        check(stacked.getProperty("physicalPlacementsDistinct") == "true")
        check(stacked.getProperty("lowerStructureMinY") != stacked.getProperty("upperStructureMinY"))
        println("DR-30 NATIVE STRUCTURE ACCEPTANCE PASS: placement + reload mutation + stacked exact-volume identity")
    }
}


val skyforgeF4kHydrologyReviewResultDirectory =
    layout.buildDirectory.dir("acceptance/f4k-hydrology-review")
val skyforgeF4kHydrologyReviewServerProperties = """
    level-name=f4k-hydrology-review
    level-seed=910077
    level-type=skyforge:development
    online-mode=false
    spawn-protection=0
    gamemode=spectator
    difficulty=peaceful
    allow-flight=true
    view-distance=8
    simulation-distance=4
    max-tick-time=0
    server-port=0
""".trimIndent() + "\n"

fun prepareSkyforgeF4kHydrologyReviewDirectory() {
    val directory = layout.projectDirectory.dir("run-f4k-hydrology-review").asFile
    delete(directory)
    directory.mkdirs()
    directory.resolve("eula.txt").writeText("eula=true\n")
    directory.resolve("server.properties").writeText(skyforgeF4kHydrologyReviewServerProperties)
}

fun requireSkyforgeF4kHydrologyReviewPreparationPass() {
    val file = skyforgeF4kHydrologyReviewResultDirectory.get().file("prepare.properties").asFile
    check(file.isFile) { "F4K hydrology preparation result missing: $file" }
    val properties = Properties()
    file.inputStream().use(properties::load)
    check(properties.getProperty("status") == "PASS"
            && properties.getProperty("surfaceBindingActive") == "true"
            && properties.getProperty("f4hAuthorizedColumns").toInt() > 0
            && properties.getProperty("f4dRemovedSolidBlocks").toInt() > 0
            && properties.getProperty("f4hRefinedReaches").toInt() > 0
            && properties.getProperty("persistedWaterHeads").toInt() > 0) {
        val detail = properties.getProperty("failure") ?: properties.toString()
        "F4K hydrology preparation did not persist a reviewable F4H specimen: $detail"
    }
}

tasks.named("runF4kHydrologyReviewPrepare").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask and F4K prepared-world orchestration are intentionally runtime-bound.",
    )
    doFirst {
        delete(skyforgeF4kHydrologyReviewResultDirectory)
        prepareSkyforgeF4kHydrologyReviewDirectory()
    }
    doLast {
        requireSkyforgeF4kHydrologyReviewPreparationPass()
    }
}

tasks.named("runF4kHydrologyReviewClient").configure {
    notCompatibleWithConfigurationCache(
        "NeoForge ModDev RunGameTask is an interactive quick-play F4K hydrology review.",
    )
    mustRunAfter("runF4kHydrologyReviewPrepare")
    doFirst {
        val directory = layout.projectDirectory.dir("run-f4k-hydrology-review").asFile
        check(directory.resolve("saves/f4k-hydrology-review/level.dat").isFile) {
            "F4K hydrology review world is missing; run runF4kHydrologyReviewPrepare first or use launchF4KHydrologyReview."
        }
        directory.resolve("options.txt").writeText(
            "onboardAccessibility:false\n"
                + "narrator:0\n",
        )
    }
}

tasks.register("f4kHydrologyReviewPrepareVerify") {
    group = "verification"
    description = "Prepare and verify the persisted F4H/F4D hydrology review specimen."
    dependsOn("runF4kHydrologyReviewPrepare")
    doLast {
        requireSkyforgeF4kHydrologyReviewPreparationPass()
        println("F4K HYDROLOGY PREPARATION PASS: persisted exact F4H water heads are ready for human review.")
    }
}

tasks.register("launchF4KHydrologyReview") {
    group = "application"
    description = "Prepare the exact F4H/F4D hydrology specimen, then quick-play it for human review."
    dependsOn("runF4kHydrologyReviewPrepare", "runF4kHydrologyReviewClient")
}

