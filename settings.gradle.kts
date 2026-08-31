pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
    }
}

plugins {
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
