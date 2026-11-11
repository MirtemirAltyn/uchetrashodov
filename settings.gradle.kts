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
        google()
        mavenCentral()
        // Крупный Android-рантайм для Robolectric берём с зеркала Maven Central от Google:
        // сам Maven Central на этот файл иногда отвечает 429 Too Many Requests.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2") { name = "MavenCentralGoogleMirror" }
            }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
    }
}

rootProject.name = "MoiFinansy"
include(":app")
