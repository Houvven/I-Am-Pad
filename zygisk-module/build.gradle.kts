import android.databinding.tool.ext.capitalizeUS
import org.apache.commons.codec.binary.Hex
import org.apache.tools.ant.filters.FixCrLfFilter
import org.apache.tools.ant.filters.ReplaceTokens
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

val moduleId = "zygisk_iampad"
val moduleName = "I Am Pad"
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val commitHash: String by rootProject.extra
val abiList: List<String> by rootProject.extra

android {
    namespace = "com.houvven.impad.zygisk"
    defaultConfig {
        ndkVersion = "28.1.13356709"
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++20")
                arguments(
                    "-DANDROID_STL=none",
                    "-DMODULE_NAME=$moduleId"
                )
            }
        }
        multiDexEnabled = false
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":finder"))
    // noinspection Aligned16KB,UseTomlInstead
    implementation("top.canyie.pine:core:0.3.0")
}

androidComponents.onVariants { variant ->
    afterEvaluate {
        val variantLowered = variant.name.lowercase()
        val variantCapped = variant.name.capitalizeUS()
        val buildTypeLowered = variant.buildType?.lowercase()
        val supportedAbis = abiList.joinToString(" ") {
            when (it) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm"
                "x86" -> "x86"
                "x86_64" -> "x64"
                else -> error("unsupported abi $it")
            }
        }

        val moduleDir = layout.buildDirectory.file("outputs/zygisk-module/$variantLowered")
        val zipFileName =
            "$moduleName-$verName-$verCode-$commitHash-$buildTypeLowered.zip".replace(' ', '-')
        val apk = project.tasks.named("package$variantCapped").map { p ->
            p.outputs.files
                .asFileTree
                .filter { f -> f.isFile && f.name.endsWith(".apk") }
                .singleFile
        }.map(project::zipTree)

        val prepareModuleFilesTask = tasks.register<Sync>("prepareModuleFiles$variantCapped") {
            group = "zygisk-module"
            dependsOn("assemble$variantCapped")
            into(moduleDir)
            from(rootProject.layout.projectDirectory.file("README.md"))
            from(layout.projectDirectory.file("template")) {
                exclude("module.prop", "customize.sh", "post-fs-data.sh", "service.sh")
                filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance("lf"))
            }
            from(layout.projectDirectory.file("template")) {
                include("module.prop")
                expand(
                    "moduleId" to moduleId,
                    "moduleName" to moduleName,
                    "versionName" to "$verName ($verCode-$commitHash-$variantLowered)",
                    "versionCode" to verCode
                )
            }
            from(layout.projectDirectory.file("template")) {
                include("customize.sh", "post-fs-data.sh", "service.sh")
                val tokens = mapOf(
                    "DEBUG" to if (buildTypeLowered == "debug") "true" else "false",
                    "SONAME" to moduleId,
                    "SUPPORTED_ABIS" to supportedAbis
                )
                filter<ReplaceTokens>("tokens" to tokens)
                filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance("lf"))
            }
            from(layout.buildDirectory.file("intermediates/stripped_native_libs/$variantLowered/strip${variantCapped}DebugSymbols/out/lib")) {
                into("lib")
            }
            from(apk.get().files.filter { f -> f.name.endsWith(".dex") })
            rename("classes.dex", "impad.dex")

            doLast {
                fileTree(moduleDir).visit {
                    if (isDirectory) return@visit
                    val md = MessageDigest.getInstance("SHA-256")
                    file.forEachBlock(4096) { bytes, size ->
                        md.update(bytes, 0, size)
                    }
                    file(file.path + ".sha256").writeText(
                        Hex.encodeHexString(
                            md.digest()
                        )
                    )
                }
            }
        }

        val zipTask = tasks.register<Zip>("zip$variantCapped") {
            group = "zygisk-module"
            dependsOn(prepareModuleFilesTask)
            archiveFileName.set(zipFileName)
            destinationDirectory.set(layout.projectDirectory.file("release").asFile)
            from(moduleDir)
        }

        val pushTask = tasks.register<Exec>("push$variantCapped") {
            group = "zygisk-module"
            dependsOn(zipTask)
            commandLine(
                "adb",
                "push",
                zipTask.get().outputs.files.singleFile.path,
                "/data/local/tmp"
            )
        }

        val installKsuTask = tasks.register<Exec>("installKsu$variantCapped") {
            group = "zygisk-module"
            dependsOn(pushTask)
            commandLine(
                "adb", "shell", "su", "-c",
                "/data/adb/ksud module install /data/local/tmp/$zipFileName"
            )
        }

        val installMagiskTask = tasks.register<Exec>("installMagisk$variantCapped") {
            group = "zygisk-module"
            dependsOn(pushTask)
            commandLine(
                "adb",
                "shell",
                "su",
                "-M",
                "-c",
                "magisk --install-module /data/local/tmp/$zipFileName"
            )
        }

        tasks.register<Exec>("installKsuAndReboot$variantCapped") {
            group = "zygisk-module"
            dependsOn(installKsuTask)
            commandLine("adb", "reboot")
        }

        tasks.register<Exec>("installMagiskAndReboot$variantCapped") {
            group = "zygisk-module"
            dependsOn(installMagiskTask)
            commandLine("adb", "reboot")
        }
    }
}
