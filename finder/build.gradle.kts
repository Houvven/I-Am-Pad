plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.houvven.impad.finder"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(libs.dexkit)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)
}