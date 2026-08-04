plugins {
    `java-library`
}

dependencies {
    api(project(":skyforge-kernel"))
    api(project(":skyforge-model"))

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
