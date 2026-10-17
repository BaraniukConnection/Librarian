pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*|com\\.google.*|androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { google(); mavenCentral() }
}

rootProject.name = "Librarian"
include(":app")
