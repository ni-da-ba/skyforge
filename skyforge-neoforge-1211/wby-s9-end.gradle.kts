import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.artifacts.Configuration

// S9 keeps every End worldgen/arena candidate opt-in and side-aware. The inherited S0-S8
// profile is still resolved by the existing WBY task; this script adds only the S9 overlay.
val wbyS9PinFile = layout.projectDirectory.file("wby-s9-end.properties")
val wbyS9Pins = Properties().apply { wbyS9PinFile.asFile.inputStream().use(::load) }
fun wbyS9Pin(mod: String, field: String): String =
    requireNotNull(wbyS9Pins.getProperty("$mod.$field")) {
        "missing WBY S9 End pin: $mod.$field in ${wbyS9PinFile.asFile}"
    }
fun wbyS9Coordinate(mod: String): String = wbyS9Pin(mod, "coordinate")
fun wbyS9CoordinateParts(mod: String): List<String> = wbyS9Coordinate(mod).removeSuffix("@zip").split(":").also {
    check(it.size == 3) { "invalid WBY S9 coordinate for $mod: ${wbyS9Coordinate(mod)}" }
}
check(wbyS9Pin("minecraft", "version") == "1.21.1")
check(wbyS9Pin("neoforge", "version") == "21.1.249")
check(wbyS9Pin("profile", "base") == "wby-s8-incendium-full")

val wbyS9EndVariant = providers.gradleProperty("wbyS9EndVariant").orNull?.trim()?.lowercase() ?: "none"
val wbyS9BetterEndMods = listOf(
    "betterEndNewDawn", "bclibNewDawn", "wunderLibNewDawn", "worldWeaverNewDawn",
)
val wbyS9BetterEndFoodMods = listOf("endsDelight", "farmersCuttingBetterEnd")
val wbyS9DragonMods = listOf("enderDragonFightRemastered")
val wbyS9PressureMods = listOf("flyHigher")
val wbyS9StructureMods = listOf(
    "moogsEndStructures", "yungsBetterStrongholds", "yungsBetterEndIsland",
)
val wbyS9EndMods = when (wbyS9EndVariant) {
    "nullscape" -> emptyList()
    "nullscape-betterend" -> wbyS9BetterEndMods
    "nullscape-phantasm" -> listOf("phantasm")
    "nullscape-edr" -> wbyS9DragonMods
    "nullscape-flyhigher" -> wbyS9PressureMods
    "nullscape-unusual" -> listOf("unusualEnd")
    "nullscape-structures" -> wbyS9StructureMods
    "betterend" -> wbyS9BetterEndMods
    "betterend-food" -> wbyS9BetterEndMods + wbyS9BetterEndFoodMods
    "betterend-structures" -> wbyS9BetterEndMods + wbyS9StructureMods
    "control" -> emptyList()
    else -> emptyList()
}
val wbyS9UsesNullscape = wbyS9EndVariant in setOf(
    "nullscape", "nullscape-betterend", "nullscape-phantasm", "nullscape-unusual", "nullscape-structures", "nullscape-edr", "nullscape-flyhigher",
)
val wbyS9UsesBetterEnd = wbyS9EndVariant in setOf("nullscape-betterend", "betterend", "betterend-food", "betterend-structures")
val wbyS9AllMods = (
    wbyS9BetterEndMods + wbyS9BetterEndFoodMods + wbyS9StructureMods +
        wbyS9DragonMods + wbyS9PressureMods + listOf("phantasm", "unusualEnd")
).distinct()
val wbyS9ClientMods = wbyS9EndMods.filter { mod ->
    wbyS9Pin(mod, "environment").split(",").any { it.trim() in setOf("client", "client-server") }
}

check(wbyS9EndVariant in setOf(
    "none", "control", "nullscape", "nullscape-betterend", "nullscape-phantasm",
    "nullscape-unusual", "nullscape-structures", "nullscape-edr", "nullscape-flyhigher",
    "betterend", "betterend-food", "betterend-structures",
)) { "WBY S9 End variant must be none, control, nullscape, nullscape-betterend, nullscape-phantasm, nullscape-unusual, nullscape-structures, nullscape-edr, nullscape-flyhigher, betterend, betterend-food, or betterend-structures" }
check(wbyS9EndVariant == "none" || (
    providers.gradleProperty("wbyS8NetherVariant").orNull?.trim()?.lowercase() == "incendium-full" &&
        providers.gradleProperty("wbyS7CombatVariant").orNull?.trim()?.lowercase() == "tacz" &&
        providers.gradleProperty("wbyS7Bomd").orNull?.toBooleanStrictOrNull() == true
)) { "WBY S9 requires the accepted cumulative S0-S8 profile with Incendium full and TaCZ" }

val wbyS9NullscapeDataPack = configurations.create("wbyS9NullscapeDataPack") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val wbyS9SourceSets = extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
val wbyS9Client = wbyS9SourceSets.getByName("wbyS1ClientRuntime")
val wbyS9Server = wbyS9SourceSets.getByName("wbyS1ServerRuntime")
wbyS9EndMods.forEach { mod ->
    if (mod in wbyS9ClientMods) {
        dependencies.add(wbyS9Client.runtimeOnlyConfigurationName, wbyS9Coordinate(mod))
    }
    dependencies.add(wbyS9Server.runtimeOnlyConfigurationName, wbyS9Coordinate(mod))
}
if (wbyS9UsesNullscape) {
    dependencies.add(wbyS9NullscapeDataPack.name, wbyS9Coordinate("nullscape"))
}

tasks.register("wbyS9ResolveEndProfile") {
    group = "verification"
    description = "Resolve the selected S9 End overlay and check its exact pinned client/server dependency closure."
    dependsOn("wbyS1ResolvePinnedMods")
    inputs.file(wbyS9PinFile)
    inputs.property("wbyS9EndVariant", wbyS9EndVariant)
    doLast {
        val clientFiles = wbyS9Client.runtimeClasspath.files
        val serverFiles = wbyS9Server.runtimeClasspath.files
        val clientModules = wbyS9Client.runtimeClasspath.incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .map { "${it.group}:${it.name}:${it.version}" }
            .toSet()
        val serverModules = wbyS9Server.runtimeClasspath.incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .map { "${it.group}:${it.name}:${it.version}" }
            .toSet()
        fun expectedCoordinate(mod: String): String = wbyS9CoordinateParts(mod).joinToString(":")
        fun requireModule(modules: Set<String>, mod: String, side: String) {
            val expected = expectedCoordinate(mod)
            check(expected in modules) { "WBY S9 $side resolution missing $mod at $expected" }
        }
        fun forbidModule(modules: Set<String>, mod: String, side: String) {
            val expected = expectedCoordinate(mod)
            check(expected !in modules) { "WBY S9 $side resolution leaked unselected $mod at $expected" }
        }
        wbyS9EndMods.forEach { mod ->
            requireModule(serverModules, mod, "server")
            if (mod in wbyS9ClientMods) {
                requireModule(clientModules, mod, "client")
            } else {
                forbidModule(clientModules, mod, "client")
            }
        }
        (wbyS9AllMods - wbyS9EndMods).forEach { mod ->
            forbidModule(clientModules, mod, "client")
            forbidModule(serverModules, mod, "server")
        }
        if (wbyS9UsesNullscape) {
            val pack = wbyS9NullscapeDataPack.singleFile
            check(pack.isFile && pack.extension.equals("zip", ignoreCase = true)) {
                "WBY S9 Nullscape must resolve to its whole official .zip data pack: $pack"
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(pack.readBytes())
                .joinToString("") { "%02x".format(it) }
            println("WBY S9 NULLSCAPE DATAPACK PASS sha256=$digest file=${pack.name}")
        }
        check(clientFiles.map { it.name }.distinct().size == clientFiles.size) {
            "WBY S9 client dependency filenames are not unique"
        }
        check(serverFiles.map { it.name }.distinct().size == serverFiles.size) {
            "WBY S9 server dependency filenames are not unique"
        }
        println("WBY S9 END RESOLUTION PASS variant=$wbyS9EndVariant")
        println("  selectedMods=${wbyS9EndMods.joinToString()}")
        println("  clientModules=${clientModules.size} serverModules=${serverModules.size}")
        println("  clientFiles=${clientFiles.size} serverFiles=${serverFiles.size}")
        println("  worldgen=Nullscape ${if (wbyS9UsesNullscape) "enabled" else "disabled"}; BetterEnd ${if (wbyS9UsesBetterEnd) "candidate present" else "absent"}")
    }
}

tasks.register<Copy>("wbyS9StageEndDatapacks") {
    group = "verification"
    description = "Stage the selected official End datapack into the isolated fresh-world review profile."
    onlyIf { wbyS9UsesNullscape }
    dependsOn("wbyS8StageNetherDatapack")
    from(wbyS9NullscapeDataPack)
    into(layout.projectDirectory.dir("$wbyS1RunDirectory/datapacks"))
    rename { "nullscape-review.zip" }
    doLast {
        val staged = destinationDir.resolve("nullscape-review.zip")
        check(staged.isFile) { "WBY S9 Nullscape datapack was not staged: $staged" }
        val digest = MessageDigest.getInstance("SHA-256").digest(staged.readBytes())
            .joinToString("") { "%02x".format(it) }
        println("WBY S9 NULLSCAPE STAGING PASS sha256=$digest path=${staged.absolutePath}")
    }
}
