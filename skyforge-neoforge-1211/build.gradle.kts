import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
    // The settings-level net.neoforged.moddev.repositories plugin already places the
    // ModDevGradle implementation on Gradle's classpath. Repeating a version here makes Gradle
    // try to compare that version with an already-loaded plugin whose classpath version is
    // unknown. Apply the existing plugin implementation by id only.
    id("net.neoforged.moddev")
}

java {
    // Minecraft 1.21.1 and NeoForge require a Java 21 toolchain. The settings-level Foojay
    // resolver allows Gradle to provision this toolchain automatically when it is not installed
    // locally, while the overall Skyforge workspace may continue running Gradle on JDK 25.
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(
            providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt(),
        ),
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(providers.gradleProperty("skyforgeRuntimeJavaRelease").get().toInt())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

neoForge {
    version = "21.1.249"

    // SF-IMP-0033 promotes the adapter from a test-only exploded mod identity to the real
    // development mod boundary. The production resource set now carries META-INF/neoforge.mods.toml
    // and the actual @Mod entrypoint/lifecycle subscriber.
    mods {
        create("skyforge") {
            sourceSet(sourceSets.main.get())
        }
    }

    // SF-IMP-0034 adds an isolated development client. The system property enables exactly one
    // deterministic specimen; normal packaged Skyforge remains inert unless runtime binding is
    // configured explicitly.
    runs {
        create("client") {
            client()
            gameDirectory = project.file("run-sf-imp-0034")
            systemProperty("skyforge.dev.specimen", "true")
        }
    }

    unitTest {
        enable()
        testedMod.set(mods.named("skyforge"))
    }
}

dependencies {
    api(project(":skyforge-world"))

    // Minecraft 1.21.1 ModDev runs load Java libraries only when they are explicitly added to the
    // additional runtime classpath. skyforge-world's runtime elements bring the transitive
    // recipes/model/kernel engine modules with it without pretending those modules are mods.
    additionalRuntimeClasspath(project(":skyforge-world"))

    testImplementation(project(":skyforge-recipes"))

    // ModDevGradle's FML-aware JUnit launcher is currently proven against JUnit Platform 5.
    // Isolate this Minecraft integration module on the plugin's own known-good JUnit line rather
    // than forcing the rest of Skyforge away from its independent test stack.
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
