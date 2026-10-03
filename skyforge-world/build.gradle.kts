plugins {
    `java-library`
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Preserve full suite coverage while keeping routine CI test execution bounded.
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
}

dependencies {
    api(project(":skyforge-recipes"))

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
