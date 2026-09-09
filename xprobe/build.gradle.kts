plugins {
    id("com.android.application")
}

android {
    namespace = "dev.browserdownloader.xprobe"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "dev.browserdownloader.xprobe"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-xprobe"
        testInstrumentationRunner = "dev.browserdownloader.xprobe.RuntimeTest"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("com.squareup:gifencoder:0.10.1")
    testImplementation("junit:junit:4.13.2")
}
