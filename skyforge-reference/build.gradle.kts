import org.gradle.api.tasks.testing.Test

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

tasks.withType<Test>().configureEach {
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
}

tasks.register<JavaExec>("fixedSeedCorpus") {
    group = "verification"
    description = "Regenerates and verifies the complete v0.1 fixed-seed evidence corpus."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.FixedSeedCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/fixed-seed-island-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("suspendedVolumeEvidence") {
    group = "verification"
    description = "Generates the canonical signal-free suspended-volume evidence package."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.SuspendedVolumeEvidenceCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/signal-free-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("seededSuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates six-seed SF-IMP-0016 suspended-volume visual evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.SeededSuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/seeded-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("secondaryMorphologySuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates six-seed SF-IMP-0017 structured-morphology visual evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.SecondaryMorphologySuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/secondary-morphology-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("morphologyFamilySuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates the fifteen-member SF-IMP-0018 primary morphology-family review atlas."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.MorphologyFamilySuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/morphology-family-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("composedMorphologySuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates the fifteen-member SF-IMP-0019 composed morphology-family review atlas."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.ComposedMorphologySuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/composed-morphology-family-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("familyAwareMorphologySuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates the fifteen-member SF-IMP-0020 family-aware morphology review atlas."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.FamilyAwareMorphologySuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/family-aware-morphology-suspended-volume-v1").get().asFile.absolutePath)
}

tasks.register<JavaExec>("hybridMorphologySuspendedVolumeCorpus") {
    group = "verification"
    description = "Generates the thirty-member SF-IMP-0022 pairwise hybrid progression atlas."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.nidaba.skyforge.reference.HybridMorphologySuspendedVolumeCorpusCli")
    jvmArgs("-Dskyforge.version=${project.version}")
    args(layout.buildDirectory.dir("evidence/hybrid-morphology-suspended-volume-v1").get().asFile.absolutePath)
}
