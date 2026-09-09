import java.util.Properties

plugins {
    id("com.android.application")
}

// Keep private signing material outside the checkout; override for other machines.
val signingPropertiesFile = file(providers.environmentVariable("BROWSERDOWNLOADER_SIGNING_PROPERTIES")
    .getOrElse("${System.getProperty("user.home")}/keyStore/signing.properties"))
val releaseKeys = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation(project(":xmedia"))
    implementation("org.jsoup:jsoup:1.23.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    testImplementation("junit:junit:4.13.2")
}

android {
    signingConfigs {
        create("release") {
            // An absent configuration fails release signing rather than using the debug key.
            if (signingPropertiesFile.isFile) {
                for (key in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) {
                    require(!releaseKeys.getProperty(key).isNullOrBlank()) { "Missing release signing field: $key" }
                }
                storeFile = signingPropertiesFile.parentFile.resolve(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }
    namespace = "dev.browserdownloader.probe"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "dev.browserdownloader.probe"
        minSdk = 29
        targetSdk = 36
        versionCode = 32
        versionName = "0.32.0-chrome-page-info"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "dev.browserdownloader.probe.PreviewTest"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}
