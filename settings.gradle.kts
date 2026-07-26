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
        // Локальный репозиторий, куда положим xray-core.aar после сборки gomobile
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "VlessHub"
include(":app")
