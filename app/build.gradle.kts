import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.jvm.application.tasks.CreateStartScripts
import java.io.File

plugins {
    application
    java
}

val javafxModuleNames = listOf("javafx.graphics", "javafx.media", "javafx.swing")
val javafxClasspathJarPattern = Regex("""javafx-(base|graphics|media|swing)-.+\.jar""")
val proguardVersion = "7.10.0"

val proguard by configurations.creating

fun File.isJavaFxRuntimeJar(): Boolean =
    Regex("""javafx-(base|graphics|media|swing)-.+-(win|mac|mac-aarch64|linux)\.jar""").matches(name)

fun String.classpathJarName(): String =
    substringAfterLast('/').substringAfterLast('\\')

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

    proguard("com.guardsquare:proguard-base:$proguardVersion")
}

application {
    mainClass.set("com.xover.music.app.XoverMain")
}

configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.compose.runtime", module = "runtime-desktop")
    exclude(group = "org.jetbrains.compose.runtime", module = "runtime-saveable-desktop")
}

tasks.withType<JavaExec>().configureEach {
    doFirst {
        val runtimeFiles = classpath.files
        val javafxRuntimeJars = runtimeFiles.filter { it.isJavaFxRuntimeJar() }
        if (javafxRuntimeJars.isNotEmpty()) {
            classpath = files(runtimeFiles.filterNot { it.isJavaFxRuntimeJar() })
            jvmArgs(
                "--module-path",
                javafxRuntimeJars.joinToString(File.pathSeparator) { it.absolutePath },
                "--add-modules",
                javafxModuleNames.joinToString(","),
                "--enable-native-access=${javafxModuleNames.joinToString(",")}",
            )
        }
    }
}

tasks.withType<CreateStartScripts>().configureEach {
    doLast {
        val javafxRuntimeJarNames = classpath?.files.orEmpty()
            .filter { it.isJavaFxRuntimeJar() }
            .map { it.name }

        patchUnixStartScript(unixScript, javafxRuntimeJarNames)
        patchWindowsStartScript(windowsScript, javafxRuntimeJarNames)
    }
}

fun patchUnixStartScript(script: File, javafxRuntimeJarNames: List<String>) {
    val modulePath = javafxRuntimeJarNames.joinToString(":") { "\$APP_HOME/lib/$it" }
    patchStartScript(
        script = script,
        classpathPrefix = "CLASSPATH=",
        classpathSeparator = ":",
        defaultJvmPrefix = "DEFAULT_JVM_OPTS=",
        defaultJvmLine = "DEFAULT_JVM_OPTS='\"--module-path\" \"$modulePath\" \"--add-modules\" \"${javafxModuleNames.joinToString(",")}\" \"--enable-native-access=${javafxModuleNames.joinToString(",")}\"'",
    )
}

fun patchWindowsStartScript(script: File, javafxRuntimeJarNames: List<String>) {
    val modulePath = javafxRuntimeJarNames.joinToString(";") { "%APP_HOME%\\lib\\$it" }
    patchStartScript(
        script = script,
        classpathPrefix = "set CLASSPATH=",
        classpathSeparator = ";",
        defaultJvmPrefix = "set DEFAULT_JVM_OPTS=",
        defaultJvmLine = "set DEFAULT_JVM_OPTS=\"--module-path\" \"$modulePath\" \"--add-modules\" \"${javafxModuleNames.joinToString(",")}\" \"--enable-native-access=${javafxModuleNames.joinToString(",")}\"",
    )
}

fun patchStartScript(
    script: File,
    classpathPrefix: String,
    classpathSeparator: String,
    defaultJvmPrefix: String,
    defaultJvmLine: String,
) {
    val patchedLines = script.readLines().map { line ->
        when {
            line.startsWith(classpathPrefix) -> {
                val patchedClasspath = line
                    .removePrefix(classpathPrefix)
                    .split(classpathSeparator)
                    .filterNot { javafxClasspathJarPattern.matches(it.classpathJarName()) }
                    .joinToString(classpathSeparator)
                classpathPrefix + patchedClasspath
            }

            line.startsWith(defaultJvmPrefix) -> defaultJvmLine
            else -> line
        }
    }
    script.writeText(patchedLines.joinToString(System.lineSeparator()))
}

tasks.withType<Tar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Sync>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Sync>("installDist") {
    doFirst {
        delete(destinationDir)
    }
}

val obfuscatedLibsDirectory = layout.buildDirectory.dir("obfuscated-release/lib")
val proguardConfigFile = layout.buildDirectory.file("obfuscated-release/proguard.generated.pro")
val proguardMappingFile = layout.buildDirectory.file("obfuscated-release/mapping.txt")
val obfuscatedInstallDirectory = layout.buildDirectory.dir("install/app-obfuscated")
val installLibDirectory = layout.buildDirectory.dir("install/app/lib")
val xoverProjectJarNames = listOf(
    "app-${project.version}.jar",
    "domain-java-${project.version}.jar",
    "application-java-${project.version}.jar",
    "audio-java-${project.version}.jar",
    "infrastructure-kotlin-${project.version}.jar",
    "desktop-ui-kotlin-${project.version}.jar",
)

fun proguardPath(file: File): String =
    file.absolutePath.replace('\\', '/')

val writeProguardReleaseConfig = tasks.register("writeProguardReleaseConfig") {
    group = "distribution"
    dependsOn(tasks.named("installDist"))
    inputs.dir(installLibDirectory)
    inputs.file(layout.projectDirectory.file("proguard-release.pro"))
    outputs.file(proguardConfigFile)

    doLast {
        val obfuscatedLibs = obfuscatedLibsDirectory.get().asFile
        val generatedConfig = proguardConfigFile.get().asFile
        val installLib = installLibDirectory.get().asFile
        delete(obfuscatedLibs)
        obfuscatedLibs.mkdirs()
        generatedConfig.parentFile.mkdirs()

        val projectJars = xoverProjectJarNames
            .map { installLib.resolve(it) }
            .onEach { require(it.isFile) { "Expected Xover release jar does not exist: $it" } }
            .sortedBy { it.name }
        val runtimeLibraries = installLib.listFiles { file ->
            file.isFile && file.extension == "jar" && file.name !in xoverProjectJarNames
        }
            .orEmpty()
            .sortedBy { it.name }
        val jmods = File(System.getProperty("java.home"), "jmods")
            .listFiles { file -> file.isFile && file.extension == "jmod" }
            .orEmpty()
            .sortedBy { it.name }

        generatedConfig.writeText(buildString {
            projectJars.forEach { inputJar ->
                appendLine("-injars ${proguardPath(inputJar)}")
                appendLine("-outjars ${proguardPath(obfuscatedLibs.resolve(inputJar.name))}")
            }
            runtimeLibraries.forEach { libraryJar ->
                appendLine("-libraryjars ${proguardPath(libraryJar)}")
            }
            jmods.forEach { jmod ->
                appendLine("-libraryjars ${proguardPath(jmod)}(!**.jar;!module-info.class)")
            }
            appendLine("-include ${proguardPath(layout.projectDirectory.file("proguard-release.pro").asFile)}")
            appendLine("-printmapping ${proguardPath(proguardMappingFile.get().asFile)}")
        })
    }
}

val obfuscateReleaseJars = tasks.register<JavaExec>("obfuscateReleaseJars") {
    group = "distribution"
    description = "Obfuscates Xover project jars for the release distribution."
    dependsOn(writeProguardReleaseConfig)
    classpath = proguard
    mainClass.set("proguard.ProGuard")
    args("@${proguardPath(proguardConfigFile.get().asFile)}")
    inputs.file(proguardConfigFile)
    inputs.dir(installLibDirectory)
    outputs.dir(obfuscatedLibsDirectory)
    outputs.file(proguardMappingFile)
}

val obfuscatedInstallDist = tasks.register<Sync>("obfuscatedInstallDist") {
    group = "distribution"
    description = "Installs a local distribution with obfuscated Xover project jars."
    dependsOn(tasks.named("installDist"), obfuscateReleaseJars)

    from(layout.buildDirectory.dir("install/app")) {
        exclude(xoverProjectJarNames.map { "lib/$it" })
    }
    from(obfuscatedLibsDirectory) {
        into("lib")
    }
    into(obfuscatedInstallDirectory)
}

tasks.register<Zip>("obfuscatedDistZip") {
    group = "distribution"
    description = "Builds a zip distribution with obfuscated Xover project jars."
    dependsOn(obfuscatedInstallDist)
    archiveFileName.set("xover-${project.version}-obfuscated.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(obfuscatedInstallDirectory) {
        into("app")
    }
}

tasks.register("releaseObfuscated") {
    group = "distribution"
    description = "Builds the obfuscated release zip."
    dependsOn(tasks.named("obfuscatedDistZip"))
}
