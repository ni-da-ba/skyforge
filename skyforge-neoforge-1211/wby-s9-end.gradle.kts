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
val wbyS9BetterEndFoodMods = listOf("endsDelight")
val wbyS9BetterEndDataPacks = listOf("farmersCuttingBetterEnd")
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
val wbyS9UsesCuttingDataPack = wbyS9EndVariant == "betterend-food"
val wbyS9EndDataPacks = buildList {
    if (wbyS9UsesNullscape) add("nullscape")
    if (wbyS9UsesCuttingDataPack) addAll(wbyS9BetterEndDataPacks)
}
val wbyS9AllDataPacks = listOf("nullscape") + wbyS9BetterEndDataPacks
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
val wbyS9BetterEndFoodDataPack = configurations.create("wbyS9BetterEndFoodDataPack") {
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
if (wbyS9UsesCuttingDataPack) {
    dependencies.add(wbyS9BetterEndFoodDataPack.name, wbyS9Coordinate("farmersCuttingBetterEnd"))
}
val wbyS9RunDirectory = providers.gradleProperty("wbyS1RunDirectory")
    .orElse("run-wby-s9-$wbyS9EndVariant").get()

tasks.register("wbyS9ResolveEndProfile") {
    group = "verification"
    description = "Resolve the selected S9 End overlay and check its exact pinned client/server dependency closure."
    dependsOn("wbyS1ResolvePinnedMods")
    inputs.file(wbyS9PinFile)
    inputs.property("wbyS9EndVariant", wbyS9EndVariant)
    doLast {
        val clientFiles = wbyS9Client.runtimeClasspath.files
        val serverFiles = wbyS9Server.runtimeClasspath.files
        val clientRuntimeConfiguration = configurations.getByName(wbyS9Client.runtimeClasspathConfigurationName)
        val serverRuntimeConfiguration = configurations.getByName(wbyS9Server.runtimeClasspathConfigurationName)
        val clientModules = clientRuntimeConfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { component -> component.moduleVersion }
            .map { module -> "${module.group}:${module.name}:${module.version}" }
            .toSet()
        val serverModules = serverRuntimeConfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { component -> component.moduleVersion }
            .map { module -> "${module.group}:${module.name}:${module.version}" }
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
        wbyS9EndDataPacks.forEach { packId ->
            val configuration = when (packId) {
                "nullscape" -> wbyS9NullscapeDataPack
                "farmersCuttingBetterEnd" -> wbyS9BetterEndFoodDataPack
                else -> error("Unknown WBY S9 End datapack: $packId")
            }
            val pack = configuration.singleFile
            check(pack.isFile && pack.extension.equals("zip", ignoreCase = true)) {
                "WBY S9 $packId must resolve to its whole official .zip data pack: $pack"
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(pack.readBytes())
                .joinToString("") { "%02x".format(it) }
            println("WBY S9 DATAPACK PASS id=$packId sha256=$digest file=${pack.name}")
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
    description = "Stage selected official S9 End datapacks into the isolated fresh-world review profile."
    onlyIf { wbyS9EndDataPacks.isNotEmpty() }
    dependsOn("wbyS8StageNetherDatapack")
    if (wbyS9UsesNullscape) {
        from(wbyS9NullscapeDataPack) { rename { "nullscape-review.zip" } }
    }
    if (wbyS9UsesCuttingDataPack) {
        from(wbyS9BetterEndFoodDataPack) { rename { "farmers-cutting-betterend.zip" } }
    }
    into(layout.projectDirectory.dir("$wbyS9RunDirectory/datapacks"))
    doLast {
        wbyS9EndDataPacks.forEach { packId ->
            val fileName = when (packId) {
                "nullscape" -> "nullscape-review.zip"
                "farmersCuttingBetterEnd" -> "farmers-cutting-betterend.zip"
                else -> error("Unknown WBY S9 staged datapack: $packId")
            }
            val staged = destinationDir.resolve(fileName)
            check(staged.isFile) { "WBY S9 $packId datapack was not staged: $staged" }
            val digest = MessageDigest.getInstance("SHA-256").digest(staged.readBytes())
                .joinToString("") { "%02x".format(it) }
            println("WBY S9 DATAPACK STAGING PASS id=$packId sha256=$digest path=${staged.absolutePath}")
        }
    }
}
