plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":application-java"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation("io.ktor:ktor-server-core-jvm:3.6.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.0")
    implementation("io.ktor:ktor-server-websockets-jvm:3.6.0")
    implementation("io.ktor:ktor-client-core-jvm:3.6.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.6.0")
    implementation("io.ktor:ktor-client-websockets-jvm:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
