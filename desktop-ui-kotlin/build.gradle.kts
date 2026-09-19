plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":application-java"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation(compose.desktop.currentOs)
}
