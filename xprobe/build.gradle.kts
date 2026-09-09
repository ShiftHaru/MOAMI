plugins {
    id("com.android.application")
}

android {
    sourceSets.getByName("main") {
        val nativeRoot = rootProject.file(providers.environmentVariable("MOAMI_NATIVE_RUNTIME").getOrElse(".local/native-runtime"))
        jniLibs.srcDir(nativeRoot.resolve("jniLibs"))
        assets.srcDir(nativeRoot.resolve("assets"))
    }
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
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(rootProject.tasks.named("verifyNativeRuntime")) }

dependencies {
    implementation("com.squareup:gifencoder:0.10.1")
    testImplementation("junit:junit:4.13.2")
}
