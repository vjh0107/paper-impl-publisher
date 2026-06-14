import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2026.1"

project {
    buildType(BumpPaperVersion)
    buildType(Publish)
}

object BumpPaperVersion : BuildType({
    name = "Bump Paper Version"
    description = "Detects the latest stable Paper and commits the bump to gradle.properties"

    vcs {
        root(DslContext.settingsRoot)
    }

    params {
        password("env.GITHUB_TOKEN", "%github.token%", display = ParameterDisplay.HIDDEN)
    }

    steps {
        script {
            name = "Detect and bump paper.version"
            scriptContent = "bash scripts/bump-paper-version.sh"
        }
    }

    triggers {
        schedule {
            schedulingPolicy = cron {
                minutes = "0"
                hours = "*/6"
            }
            branchFilter = "+:<default>"
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }
})

object Publish : BuildType({
    name = "Publish"
    description = "Publishes paper-impl to Nexus when gradle.properties changes on the default branch"

    vcs {
        root(DslContext.settingsRoot)
    }

    params {
        param("env.NEXUS_USERNAME", "vjh0107")
        password("env.NEXUS_PASSWORD", "credentialsJSON:f702f75c-65dc-4d75-a035-cb0179b7f1ea", display = ParameterDisplay.HIDDEN)
    }

    steps {
        gradle {
            name = "Publish paper-impl"
            tasks = "publishPaperImpl"
            gradleParams = "--no-daemon --stacktrace"
        }
    }

    triggers {
        vcs {
            triggerRules = "+:gradle.properties"
            branchFilter = "+:<default>"
        }
    }
})
