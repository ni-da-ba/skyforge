plugins {
    application
}

dependencies {
    implementation(project(":skyforge-kernel"))
    implementation(project(":skyforge-model"))
    implementation(project(":skyforge-recipes"))

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.github.nidaba.skyforge.reference.EvidenceCli")
    applicationDefaultJvmArgs = listOf("-Dskyforge.version=${project.version}")
}
