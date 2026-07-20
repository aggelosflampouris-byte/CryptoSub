// Root settings.gradle.kts — KMP multi-module project
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
    }
}

rootProject.name = "CryptoSub"

// Shared KMP module (UI + business logic + data layer)
include(":shared")

// Android host application (thin wrapper, bootstraps the shared module)
include(":androidApp")
