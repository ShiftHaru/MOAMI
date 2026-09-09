plugins {
    id("com.android.application")
}

dependencies {
    implementation(project(":xmedia"))
    testImplementation("junit:junit:4.13.2")
}

android {
    namespace = "dev.browserdownloader.probe"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "dev.browserdownloader.probe"
        minSdk = 29
        targetSdk = 36
        versionCode = 21
        versionName = "0.21.0-service-lifecycle"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "dev.browserdownloader.probe.PreviewTest"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}
