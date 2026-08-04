import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

allprojects {
    version = providers.gradleProperty("skyforgeVersion").get()
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(
                JavaLanguageVersion.of(
                    providers.gradleProperty("skyforgeJavaVersion").get().toInt(),
                ),
            )
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

val engineModules = listOf(
    project(":skyforge-kernel"),
    project(":skyforge-model"),
    project(":skyforge-recipes"),
)

val verifyBackendIndependence by tasks.registering {
    group = "verification"
    description = "Rejects Minecraft or NeoForge imports from backend-neutral modules."

    val forbiddenImport = Regex("""^\s*import\s+(net\.minecraft|net\.neoforged)(\.|;)""")
    val sourceTrees = engineModules.map { module ->
        module.fileTree("src") {
            include("**/*.java")
        }
    }

    inputs.files(sourceTrees)

    doLast {
        val violations = sourceTrees
            .flatMap { tree -> tree.files }
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.containsMatchIn(line)) {
                        "${source.relativeTo(rootDir)}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        check(violations.isEmpty()) {
            "Backend dependencies found in engine modules:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyBackendIndependence)
}
