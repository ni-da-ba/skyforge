pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
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
    "skyforge-reference",
)
