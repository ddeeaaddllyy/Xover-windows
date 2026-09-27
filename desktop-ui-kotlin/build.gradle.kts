plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        resources.srcDir(rootProject.file("assets"))
    }
}

dependencies {
    implementation(project(":application-java"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation(compose.desktop.currentOs)
}
