pluginManagement {
    includeBuild("build-logic/convention")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "MusicApp"
include(":shared")
include(":core:domain")
include(":core:data")
include(":core:presentation")
include(":core:lyrics-core")
include(":core:lyrics-ui")
include(":feature:search")
include(":feature:downloads")
include(":feature:settings")
include(":feature:playlist")
include(":feature:sources")
include(":feature:home")
include(":feature:importing")
include(":feature:onboarding")
include(":feature:queue")
include(":feature:radio")
include(":feature:lyrics")
include(":feature:album")
include(":feature:artist")
include(":feature:browse")
include(":feature:library")
include(":feature:recentlyadded")
include(":feature:recentlyplayed")
include(":source:api")
include(":source:local")
include(":source:webdav")
include(":source:onedrive")
include(":source:smb")
include(":source:openlist")
include(":source:server")
include(":service:playback:domain")
include(":service:playback:presentation")
include(":service:download:data")
include(":service:download:domain")
include(":service:librarysync:domain")
include(":service:librarysync:data")
include(":androidApp")
include(":desktopApp")
