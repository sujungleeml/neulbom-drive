pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google Maven에는 Android·Google 계열만 묻는다(그 외 라이브러리는 Maven Central).
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "NeulbomDrive"

val jvmOnly = providers.gradleProperty("neulbom.jvmOnly").orNull == "true"

include(
    ":core",
    ":vehicle:api",
    ":vehicle:mock",
    ":vehicle:pleos",
    ":domain:consent",
    ":domain:cognitive",
    ":domain:driving",
    ":domain:scheduler",
    ":data:sync",
    ":presentation",
    ":server",
)
if (!jvmOnly) {
    include(":data:local", ":app")
}
