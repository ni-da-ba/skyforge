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

val engineModules = listOf(
    project(":skyforge-kernel"),
    project(":skyforge-model"),
    project(":skyforge-recipes"),
)

val verifyBackendIndependence = tasks.register<VerifyBackendIndependenceTask>("verifyBackendIndependence") {
    group = "verification"
    description = "Rejects Minecraft or NeoForge imports from backend-neutral modules."
    projectRoot.set(layout.projectDirectory)

    sourceFiles.from(engineModules.map { module ->
        module.fileTree("src") {
            include("**/*.java")
        }
    })
}

tasks.named("check") {
    dependsOn(verifyBackendIndependence)
}
