pluginManagement {
    repositories {
        // AndroidX and the Android tools come from Google's repository only: no other repository
        // may serve an artifact under those groups (dependency confusion).
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "TrashMails"
include(":app")
