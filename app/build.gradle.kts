import java.util.Properties

plugins {
    id("com.android.application")
}

val shareOnly = providers.gradleProperty("moamiShare").orElse("false").get().toBooleanStrict()
// Separate outputs prevent a share build from overwriting the full APK.
if (shareOnly) layout.buildDirectory.set(layout.projectDirectory.dir("build/share-only"))

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
    buildFeatures { buildConfig = true }
    if (shareOnly) sourceSets.getByName("main") {
        manifest.srcFile("src/share/AndroidManifest.xml")
    }
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
        applicationId = if (shareOnly) "dev.browserdownloader.share" else "dev.browserdownloader.probe"
        minSdk = 29
        targetSdk = 36
        versionCode = 36
        versionName = if (shareOnly) "0.33.3-share" else "0.33.3-full"
        buildConfigField("boolean", "SHARE_ONLY", shareOnly.toString())
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "dev.browserdownloader.probe.PreviewTest"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}
