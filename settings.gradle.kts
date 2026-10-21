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
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "DerSchmusigsteFliegt"

include(":app")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:sync")
include(":feature:catalog")
include(":feature:editor")
include(":feature:settings")
include(":feature:transfer")
include(":game:api")
include(":game:swipe")
