import io.github.nidaba.skyforge.buildlogic.VerifyBackendIndependenceTask
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

val backendNeutralModules = listOf(
    project(":skyforge-kernel"),
    project(":skyforge-model"),
    project(":skyforge-recipes"),
    project(":skyforge-world"),
)

val runtimeJavaRelease = providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt()
backendNeutralModules.forEach { module ->
    module.tasks.withType<JavaCompile>().configureEach {
        // Build with the workspace JDK while emitting runtime artifacts loadable by the first
        // demonstrated backend (Minecraft/NeoForge 1.21.1 on Java 21).
        options.release.set(runtimeJavaRelease)
    }
}

val verifyBackendIndependence = tasks.register<VerifyBackendIndependenceTask>("verifyBackendIndependence") {
    group = "verification"
    description = "Rejects Minecraft or NeoForge imports from backend-neutral modules."
    projectRoot.set(layout.projectDirectory)

    sourceFiles.from(backendNeutralModules.map { module ->
        module.fileTree("src") {
            include("**/*.java")
        }
    })
}

tasks.named("check") {
    dependsOn(verifyBackendIndependence)
}
