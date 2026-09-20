pluginManagement {
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
        // PersianDate (Jalali calendar) is published through JitPack only.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.samanzamani.*") }
        }
    }
}

rootProject.name = "Metra"
include(":app")
