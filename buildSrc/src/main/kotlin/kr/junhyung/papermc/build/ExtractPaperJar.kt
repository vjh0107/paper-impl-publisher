package kr.junhyung.papermc.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject

abstract class ExtractPaperJar @Inject constructor(layout: ProjectLayout) : DefaultTask() {

    @get:InputFiles
    abstract val serverArtifacts: ConfigurableFileCollection

    @get:Input
    abstract val paperVersion: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputFile
    abstract val sourcesJar: RegularFileProperty

    init {
        outputJar.convention(layout.buildDirectory.file(paperVersion.map { "paper-impl/paper-impl-$it.jar" }))
        sourcesJar.convention(layout.buildDirectory.file(paperVersion.map { "paper-impl/paper-impl-$it-sources.jar" }))
    }

    @TaskAction
    fun extract() {
        val jars = serverArtifacts.files.filter { it.name.endsWith(".jar") }
        require(jars.isNotEmpty()) {
            "No JAR found in the mojangMappedServer configuration"
        }
        val serverJar = jars.maxByOrNull { it.length() }!!

        val mainJar = outputJar.get().asFile
        val sources = sourcesJar.get().asFile
        mainJar.parentFile.mkdirs()

        var sourceCount = 0
        JarFile(serverJar).use { jar ->
            JarOutputStream(mainJar.outputStream().buffered()).use { classesOut ->
                JarOutputStream(sources.outputStream().buffered()).use { sourcesOut ->
                    jar.entries().asSequence()
                        .filter { !it.isDirectory }
                        .forEach { entry ->
                            val isSource = entry.name.endsWith(".java")
                            val out = if (isSource) sourcesOut else classesOut
                            out.putNextEntry(ZipEntry(entry.name))
                            jar.getInputStream(entry).use { it.copyTo(out) }
                            out.closeEntry()
                            if (isSource) sourceCount++
                        }
                }
            }
        }
        require(sourceCount > 0) {
            "No .java sources found in ${serverJar.name}"
        }
        logger.lifecycle("Repackaged ${serverJar.name}: classes -> ${mainJar.name}, $sourceCount sources -> ${sources.name}")
    }
}
