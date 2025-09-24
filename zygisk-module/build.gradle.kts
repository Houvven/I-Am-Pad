plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.java.zygisk)
}

android {
    namespace = "com.houvven.impad"
    compileSdk = 36

    defaultConfig {
        applicationId = namespace
        minSdk = 27
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

zygisk {
    id = "zygsik_i_am_pad"
    name = "I Am Pad"
    archiveName = "zygisk-i-am-pad"
    author = "Houvven"
    description =
        "Enabled Pad login on phone device. Support: WeChat, WeWork, QQ, DingTalk, XHS(RedNote)."
    entrypoint = "com.houvven.impad.zygisk.Main"
    isAttachNativeLibs = true
    isGenerateChecksums = true
    packages("com.tencent.mm", "com.tencent.wework", "com.alibaba.android.rimet", "com.xingin.xhs")
}

dependencies {
    implementation(project(":finder"))
    implementation(libs.androidvmtools)
    implementation(libs.r8.annotations)
}