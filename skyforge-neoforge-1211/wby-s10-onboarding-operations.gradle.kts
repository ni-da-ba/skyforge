import java.util.Properties
import java.util.zip.ZipFile
// S10 candidates remain opt-in and side-aware while S0-S9 stays the accepted profile authority.
val wbyS10PinFile = layout.projectDirectory.file("wby-s10-onboarding-operations.properties")
val wbyS10Pins = Properties().apply { wbyS10PinFile.asFile.inputStream().use(::load) }
fun wbyS10Pin(mod: String, field: String): String =
    requireNotNull(wbyS10Pins.getProperty("$mod.$field")) {
        "missing WBY S10 pin: $mod.$field in ${wbyS10PinFile.asFile}"
    }
fun wbyS10Coordinate(mod: String): String = wbyS10Pin(mod, "coordinate")
fun wbyS10Parts(mod: String): List<String> = wbyS10Coordinate(mod).split(":").also {
    check(it.size == 3) { "invalid WBY S10 coordinate for $mod: ${wbyS10Coordinate(mod)}" }
}

check(wbyS10Pin("minecraft", "version") == "1.21.1")
check(wbyS10Pin("neoforge", "version") == "21.1.249")
check(wbyS10Pin("profile", "base") == "wby-s9-end")
val wbyS10Profile = providers.gradleProperty("wbyS10Profile").orNull?.trim()?.lowercase() ?: "none"
val wbyS10Ftb = listOf("ftbLibrary", "ftbTeams", "ftbQuests")
val wbyS10All = listOf(
    "ftbLibrary", "ftbTeams", "ftbQuests", "patchouli", "createEnchantmentIndustry",
    "simpleVoiceChat", "walkieTalkiePlus", "simpleBackupsStable", "serverCore",
    "trueAdaptiveMusic", "presenceFootsteps", "presenceFootstepsSable",
)
val wbyS10CandidateInventory = wbyS10All + "simpleBackupsBeta"
val wbyS10Profiles = mapOf(
    "none" to emptyList(),
    "quests" to wbyS10Ftb,
    "quests-book" to wbyS10Ftb + "patchouli",
    "enchantment-industry" to listOf("createEnchantmentIndustry"),
    "communications" to listOf("simpleVoiceChat", "walkieTalkiePlus"),
    "operations-stable" to listOf("simpleBackupsStable", "serverCore"),
    "operations-beta" to listOf("simpleBackupsBeta", "serverCore"),
    "adaptive-music" to listOf("trueAdaptiveMusic"),
    "surface-audio" to listOf("presenceFootsteps", "presenceFootstepsSable"),
    "full" to wbyS10All,
)
check(wbyS10Profile in wbyS10Profiles) {
    "WBY S10 profile must be one of: ${wbyS10Profiles.keys.joinToString()}"
}
val wbyS10Mods = wbyS10Profiles.getValue(wbyS10Profile)
val wbyS10ClientOnly = setOf("presenceFootsteps", "presenceFootstepsSable")
val wbyS10ServerOnly = setOf("serverCore")
val wbyS10SourceSets = extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
val wbyS10Client = wbyS10SourceSets.getByName("wbyS1ClientRuntime")
val wbyS10Server = wbyS10SourceSets.getByName("wbyS1ServerRuntime")

// FTB Quests, Library, and Teams declare Architectury 13.0.8 from dev.architectury.
 // The accepted S5 profile already supplies Architectury 13.0.11 from Modrinth. Keep one
 // runtime mod instance and use the accepted inherited API for the optional FTB overlay.
fun addWbyS10Dependency(configurationName: String, mod: String) {
    val dependency = dependencies.create(wbyS10Coordinate(mod))
    if (mod in wbyS10Ftb) {
        (dependency as org.gradle.api.artifacts.ModuleDependency).exclude(
            mapOf("group" to "dev.architectury", "module" to "architectury-neoforge"),
        )
    }
    dependencies.add(configurationName, dependency)
}

wbyS10Mods.forEach { mod ->
    if (mod !in wbyS10ServerOnly) {
        addWbyS10Dependency(wbyS10Client.runtimeOnlyConfigurationName, mod)
    }
    if (mod !in wbyS10ClientOnly) {
        addWbyS10Dependency(wbyS10Server.runtimeOnlyConfigurationName, mod)
    }
}
check(wbyS10Profile == "none" || (
    providers.gradleProperty("wbyS9EndVariant").orNull?.trim()?.lowercase() == "nullscape" &&
        providers.gradleProperty("wbyS8NetherVariant").orNull?.trim()?.lowercase() == "incendium-full" &&
        providers.gradleProperty("wbyS7CombatVariant").orNull?.trim()?.lowercase() == "tacz" &&
        providers.gradleProperty("wbyS7Bomd").orNull?.toBooleanStrictOrNull() == true
)) { "WBY S10 requires the cumulative S0-S9 profile: Incendium full, Nullscape, TaCZ, and BOMD" }

// Resolve runtime classpaths: *RuntimeOnly configurations are declarable, not resolvable.
val wbyS10ClientConfiguration = configurations.getByName(wbyS10Client.runtimeClasspathConfigurationName)
val wbyS10ServerConfiguration = configurations.getByName(wbyS10Server.runtimeClasspathConfigurationName)
tasks.register("wbyS10ResolveProfile") {
    group = "verification"
    description = "Resolve exact WBY S10 candidate profile dependencies on supported runtime sides."
    dependsOn("wbyS9ResolveEndProfile")
    inputs.file(wbyS10PinFile)
    inputs.property("wbyS10Profile", wbyS10Profile)
    doLast {
        val clientModules = wbyS10ClientConfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}:${it.version}" }.toSet()
        val serverModules = wbyS10ServerConfiguration.incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}:${it.version}" }.toSet()
        wbyS10CandidateInventory.filter { it !in wbyS10Mods }.forEach { mod ->
            val excluded = wbyS10Parts(mod).joinToString(":")
            check(excluded !in clientModules && excluded !in serverModules) {
                "WBY S10 profile $wbyS10Profile leaked unselected candidate $mod: $excluded"
            }
        }
        if (wbyS10Profile == "none") {
            println("WBY S10 profile none: inherited S0-S9 baseline only; no S10 candidates resolved")
            return@doLast
        }
        wbyS10Mods.forEach { mod ->
            val parts = wbyS10Parts(mod)
            val expected = parts.joinToString(":")
            if (mod !in wbyS10ServerOnly) {
                check(expected in clientModules) { "S10 client resolution missing $mod $expected" }
            }
            if (mod !in wbyS10ClientOnly) {
                check(expected in serverModules) { "S10 server resolution missing $mod $expected" }
            }
            println("WBY S10 pinned candidate resolved: $mod $expected")
        }
        check(wbyS10ClientConfiguration.incoming.resolutionResult.allComponents.none {
            it.moduleVersion?.name?.contains("servercore", ignoreCase = true) == true
        }) { "ServerCore leaked onto the client runtime" }
        check(wbyS10ServerConfiguration.incoming.resolutionResult.allComponents.none {
            it.moduleVersion?.name?.contains("presence-footsteps", ignoreCase = true) == true
        }) { "Presence Footsteps leaked onto the server runtime" }
        println("WBY S10 PROFILE RESOLUTION PASS profile=$wbyS10Profile")
    }
}

val wbyS10RunDirectory = providers.gradleProperty("wbyS1RunDirectory")
    .orElse("run-wby-s10-$wbyS10Profile")
    .get()

tasks.register<Copy>("wbyS10StageClientMods") {
    group = "verification"
    description = "Stage the resolved cumulative S0-S10 client mod closure for profile review."
    dependsOn("wbyS1StageClientMods", "wbyS10ResolveProfile")

    val modMetadataPaths = listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    fun isNeoForgeModJar(file: java.io.File): Boolean =
        file.isFile && file.extension.equals("jar", ignoreCase = true) &&
            runCatching {
                ZipFile(file).use { archive -> modMetadataPaths.any { archive.getEntry(it) != null } }
            }.getOrDefault(false)

    // S1 staging intentionally contains only the accepted S0-S8 closure. Copy mod JARs from the
    // fully resolved client runtime so selected S9 and S10 client/server candidates reach the
    // runtime client as well as the server.
    from(wbyS10Client.runtimeClasspath) {
        include { details -> isNeoForgeModJar(details.file) }
    }
    into(layout.projectDirectory.dir("$wbyS10RunDirectory/mods"))

    doLast {
        val expected = wbyS10Client.runtimeClasspath.files
            .filter(::isNeoForgeModJar)
            .map { it.name }
            .toSet()
        val staged = destinationDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        val missing = expected - staged
        check(missing.isEmpty()) {
            "WBY S10 client mod staging is incomplete for $wbyS10Profile: $missing"
        }
        println("WBY S10 CLIENT MOD STAGING PASS profile=$wbyS10Profile modJars=${expected.size}")
    }
}

tasks.register("wbyS10StageProfile") {
    group = "verification"
    description = "Stage the complete cumulative S0-S10 profile for diagnostic launch."
    dependsOn(
        "wbyS10ResolveProfile",
        "wbyS10StageClientMods",
        "wbyS1StageServerMods",
        "wbyS1StagePolicy",
        "wbyS8StageNetherDatapack",
        "wbyS9StageEndDatapacks",
    )
}
