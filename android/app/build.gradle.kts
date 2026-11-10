import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rogozinds.cruxwall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rogozinds.cruxwall"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // The pose model and wasm files are already compressed; keep them as-is.
        noCompress += listOf("task", "wasm")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}

// ---------------------------------------------------------------------------
// Bundle the game (../../web) plus MediaPipe and the pose models into the APK,
// so the app works offline and never sends camera frames anywhere.
// ---------------------------------------------------------------------------
val mediapipeVersion = "0.10.14"
val mediapipeCdn = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@$mediapipeVersion"
val modelUrl = { variant: String ->
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_$variant/float16/1/pose_landmarker_$variant.task"
}

abstract class PrepareWebAssets : DefaultTask() {
    @get:InputDirectory
    abstract val webDir: DirectoryProperty

    @get:Input
    abstract val downloads: MapProperty<String, String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile.resolve("web")
        out.deleteRecursively()
        webDir.get().asFile.copyRecursively(out, overwrite = true)
        downloads.get().forEach { (relPath, url) ->
            val cached = cacheDir.get().asFile.resolve(relPath)
            if (!cached.exists() || cached.length() == 0L) {
                logger.lifecycle("Downloading $url")
                cached.parentFile.mkdirs()
                val tmp = File(cached.path + ".part")
                URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(cached)
            }
            cached.copyTo(out.resolve("vendor/$relPath"), overwrite = true)
        }
    }
}

val prepareWebAssets = tasks.register<PrepareWebAssets>("prepareWebAssets") {
    webDir.set(rootProject.file("../web"))
    cacheDir.set(rootProject.layout.projectDirectory.dir(".mediapipe-cache"))
    downloads.set(
        mapOf(
            "vision_bundle.mjs" to "$mediapipeCdn/vision_bundle.mjs",
            "wasm/vision_wasm_internal.js" to "$mediapipeCdn/wasm/vision_wasm_internal.js",
            "wasm/vision_wasm_internal.wasm" to "$mediapipeCdn/wasm/vision_wasm_internal.wasm",
            "wasm/vision_wasm_nosimd_internal.js" to "$mediapipeCdn/wasm/vision_wasm_nosimd_internal.js",
            "wasm/vision_wasm_nosimd_internal.wasm" to "$mediapipeCdn/wasm/vision_wasm_nosimd_internal.wasm",
            "pose_landmarker_full.task" to modelUrl("full"),
            "pose_landmarker_lite.task" to modelUrl("lite"),
        )
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareWebAssets, PrepareWebAssets::outputDir)
    }
}
