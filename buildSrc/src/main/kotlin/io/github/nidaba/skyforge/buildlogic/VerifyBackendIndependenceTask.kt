package io.github.nidaba.skyforge.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task only validates source files and produces no outputs")
abstract class VerifyBackendIndependenceTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbiddenImport = Regex("""^\s*import\s+(net\.minecraft|net\.neoforged)(\.|;)""")
        val violations = sourceFiles.files
            .sortedBy { source -> source.invariantSeparatorsPath }
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (forbiddenImport.containsMatchIn(line)) {
                        "${source.relativeTo(projectRoot.get().asFile).invariantSeparatorsPath}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        check(violations.isEmpty()) {
            "Backend dependencies found in engine modules:\n${violations.joinToString("\n")}"
        }
    }
}
