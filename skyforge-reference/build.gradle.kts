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

tasks.register<JavaExec>("fixedSeedCorpus") {
    group = "verification"
    description = "Regenerates and verifies the complete v0.1 fixed-seed evidence corpus."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.FixedSeedCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/fixed-seed-island-v1").get().asFile.absolutePath)
}
