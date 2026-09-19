pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        kotlin("jvm") version "2.4.20"
        kotlin("plugin.serialization") version "2.4.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
        id("org.jetbrains.compose") version "1.12.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Xover"

include(
    "domain-java",
    "application-java",
    "audio-java",
    "infrastructure-kotlin",
    "desktop-ui-kotlin",
    "app",
)
