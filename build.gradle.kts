plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
}

val nativeRoot = file(providers.environmentVariable("MOAMI_NATIVE_RUNTIME").getOrElse(".local/native-runtime"))
tasks.register("verifyNativeRuntime") {
    inputs.file("third_party/native-runtime-lock.json")
    inputs.dir(nativeRoot)
    doLast {
        val lock = groovy.json.JsonSlurper().parse(file("third_party/native-runtime-lock.json")) as Map<*, *>
        val files = lock["files"] as Map<*, *>
        for ((name, expected) in files) {
            val artifact = nativeRoot.resolve(name.toString())
            require(artifact.isFile) { "Build the pinned native runtime first; missing $name" }
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
            require(actual == expected) { "Native runtime differs from lock: $name" }
        }
        require(nativeRoot.resolve("assets/native/runtime.sha256").readText().trim() == files["assets/native/runtime.zip"]) { "Runtime digest file mismatch" }
    }
}
