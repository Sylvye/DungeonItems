pluginManagement { repositories { gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { name = "papermc"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    }
}
includeBuild("../../BountySMP/ConfigurableItems")
rootProject.name = "DungeonItems"
