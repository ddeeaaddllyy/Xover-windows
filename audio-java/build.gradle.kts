plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val javafxVersion = "21.0.12"
val javafxPlatform = when {
    System.getProperty("os.name").lowercase().contains("win") -> "win"
    System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch").lowercase().contains("aarch64") -> "mac-aarch64"
    System.getProperty("os.name").lowercase().contains("mac") -> "mac"
    else -> "linux"
}

dependencies {
    implementation(project(":application-java"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
}
