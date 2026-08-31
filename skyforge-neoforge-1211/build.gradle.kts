import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.144"
}

java {
    // Minecraft 1.21.1 ships on Java 21; the adapter must target the backend runtime rather than
    // inheriting the Java 25 toolchain used by backend-neutral Skyforge development.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = "21.1.249"
}

dependencies {
    api(project(":skyforge-world"))

    testImplementation(project(":skyforge-recipes"))
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
