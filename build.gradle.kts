import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.io.ByteArrayOutputStream

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.java.zygisk) apply false
    alias(libs.plugins.android.library) apply false
}

subprojects {
    afterEvaluate {
        extensions.configure<com.android.build.gradle.BaseExtension> {
            compileSdkVersion(36)
            defaultConfig {
                minSdk = 27
                targetSdk = 36
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        runCatching {
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        runCatching {
            extensions.configure<com.android.build.gradle.AppExtension> {
            }
        }
    }
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

// also the soname
val verName by extra("v1")
val verCode by extra(gitCommitCount)
val commitHash by extra(gitCommitHash)
val abiList by extra(listOf("arm64-v8a", "armeabi-v7a"))