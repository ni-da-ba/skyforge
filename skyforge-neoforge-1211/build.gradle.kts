import org.gradle.api.tasks.compile.JavaCompile

plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.144"
}

// The workspace itself is validated on JDK 25. Minecraft 1.21.1 targets Java 21, so compile the
// adapter against the Java 21 API/bytecode level without requiring a second local toolchain for
// this first compile-only integration proof. A later game-launch proof must run on Java 21.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
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
