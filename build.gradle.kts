plugins {
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.compose") apply false
}

group = "com.xover.music"
version = "0.1.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
