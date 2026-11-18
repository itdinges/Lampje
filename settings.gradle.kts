pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*|com\\.google.*|androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*|com\\.google.*|androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Lampje"

include(":core")

// The Android app needs an Android SDK; :core builds and tests with a plain JDK.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (hasAndroidSdk) include(":app")
