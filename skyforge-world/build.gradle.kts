plugins {
    `java-library`
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Preserve full suite coverage while keeping routine CI test execution bounded.
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

    // Put actionable expected/actual values in CI logs when a regression fails.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    api(project(":skyforge-recipes"))

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
