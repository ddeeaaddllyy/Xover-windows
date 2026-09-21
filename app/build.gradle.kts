import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.application.tasks.CreateStartScripts
import java.io.File

plugins {
    application
    java
}

val javafxModuleNames = listOf("javafx.graphics", "javafx.media", "javafx.swing")
val javafxClasspathJarPattern = Regex("""javafx-(base|graphics|media|swing)-.+\.jar""")

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
}

application {
    mainClass.set("com.xover.music.app.XoverMain")
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
