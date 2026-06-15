import kr.junhyung.papermc.build.ExtractPaperJar
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

plugins {
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

val devBundleVersion: String = providers.gradleProperty("paper.version").get()
val nexusUrl = "https://nexus.junhyung.kr/repository/maven-grayzone"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(devBundleVersion)
}

val mojangMappedServer: Configuration by configurations.named("mojangMappedServer")

val extractPaperImpl = tasks.register<ExtractPaperJar>("extractPaperImpl") {
    group = "publishing"
    serverArtifacts.from(mojangMappedServer)
    paperVersion.set(devBundleVersion)
    onlyIf("paper-impl is not yet published") {
        !paperImplAlreadyPublished(devBundleVersion)
    }
}

publishing {
    publications {
        create<MavenPublication>("paperImpl") {
            groupId = project.group.toString()
            artifactId = "paper-impl"
            version = devBundleVersion

            artifact(extractPaperImpl.flatMap { it.outputJar })
            artifact(extractPaperImpl.flatMap { it.sourcesJar }) {
                classifier = "sources"
            }

            pom.withXml {
                val dependencies = asNode().appendNode("dependencies")
                mojangMappedServer.incoming.resolutionResult.root.dependencies
                    .asSequence()
                    .filterIsInstance<ResolvedDependencyResult>()
                    .flatMap { it.selected.dependencies }
                    .filterIsInstance<ResolvedDependencyResult>()
                    .mapNotNull { it.selected.moduleVersion }
                    .forEach { id ->
                        dependencies.appendNode("dependency").apply {
                            appendNode("groupId", id.group)
                            appendNode("artifactId", id.name)
                            appendNode("version", id.version)
                        }
                    }
            }
        }
    }

    repositories {
        maven {
            name = "nexus"
            url = uri(nexusUrl)
            credentials {
                username = providers.environmentVariable("NEXUS_USERNAME").orNull
                password = providers.environmentVariable("NEXUS_PASSWORD").orNull
            }
        }
    }
}

tasks.register("publishPaperImpl") {
    group = "publishing"
    description = "Publishes paper-impl to Nexus, skipping versions that already exist"
    dependsOn("publishPaperImplPublicationToNexusRepository")
}

tasks.named("publishPaperImplPublicationToNexusRepository").configure {
    onlyIf("paper-impl is not yet published") {
        !paperImplAlreadyPublished(devBundleVersion)
    }
}

fun paperImplAlreadyPublished(version: String): Boolean {
    val groupPath = project.group.toString().replace('.', '/')
    val connection = URI("$nexusUrl/$groupPath/paper-impl/$version/paper-impl-$version.pom")
        .toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "HEAD"
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    val user = providers.environmentVariable("NEXUS_USERNAME").orNull
    val password = providers.environmentVariable("NEXUS_PASSWORD").orNull
    if (user != null && password != null) {
        val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        connection.setRequestProperty("Authorization", "Basic $token")
    }
    return try {
        connection.responseCode == HttpURLConnection.HTTP_OK
    } finally {
        connection.disconnect()
    }
}
