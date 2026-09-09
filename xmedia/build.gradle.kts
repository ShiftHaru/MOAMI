plugins { id("com.android.library") }
android {
    namespace = "dev.browserdownloader.xprobe"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 29 }
    sourceSets {
        getByName("main") {
            val nativeRoot = rootProject.file(providers.environmentVariable("MOAMI_NATIVE_RUNTIME").getOrElse(".local/native-runtime"))
            jniLibs.srcDir(nativeRoot.resolve("jniLibs"))
            assets.srcDir(nativeRoot.resolve("assets"))
            java.srcDir("../xprobe/src/main/java")
            res.srcDir("../xprobe/src/main/res")
            assets.srcDir("../xprobe/src/main/assets")
        }
        getByName("test") { java.srcDir("../xprobe/src/test/java") }
    }
    androidResources { ignoreAssetsPattern = "__pycache__:*.pyc" }
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
