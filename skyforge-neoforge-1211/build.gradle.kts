import org.gradle.api.tasks.compile.JavaCompile

plugins {
    `java-library`
    // The settings-level net.neoforged.moddev.repositories plugin already places the
    // ModDevGradle implementation on Gradle's classpath. Repeating a version here makes Gradle
    // try to compare that version with an already-loaded plugin whose classpath version is
    // unknown. Apply the existing plugin implementation by id only.
    id("net.neoforged.moddev")
}

java {
    // The workspace runs on JDK 25, but Minecraft 1.21.1 executes on Java 21. Compile this
    // adapter to the backend runtime API/classfile level without requiring a second local JDK.
    toolchain.languageVersion.set(
        org.gradle.jvm.toolchain.JavaLanguageVersion.of(
            providers.gradleProperty("skyforgeJavaVersion").get().toInt(),
        ),
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt())
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
