pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
    }
}

plugins {
    // Gradle can provision the Java 21 toolchain required by Minecraft/NeoForge while the
    // developer workstation continues to run the build itself on the configured workspace JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("net.neoforged.moddev.repositories") version "2.0.144"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "skyforge"

include(
    "skyforge-kernel",
    "skyforge-model",
    "skyforge-recipes",
    "skyforge-world",
    "skyforge-reference",
    "skyforge-neoforge-1211",
)
