plugins {
    `java-library`
}

dependencies {
    api(project(":skyforge-kernel"))
    api(project(":skyforge-model"))

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
