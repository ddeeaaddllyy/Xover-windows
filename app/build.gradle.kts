plugins {
    application
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":application-java"))
    implementation(project(":audio-java"))
    implementation(project(":infrastructure-kotlin"))
    implementation(project(":desktop-ui-kotlin"))

    implementation("com.google.dagger:dagger:2.60.1")
    annotationProcessor("com.google.dagger:dagger-compiler:2.60.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass.set("com.xover.music.app.XoverMain")
}

tasks.withType<Tar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
