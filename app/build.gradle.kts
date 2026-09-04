import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing credentials live in keystore.properties, which is kept out of
// version control. See keystore.properties.template for the expected keys.
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.zebra.igdemo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zebra.igdemo"
        // Identity Guardian ships on Android 11+ devices, but keep the floor low
        // so the demo installs on as many Zebra devices as possible.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Only wire up the config when credentials are actually present, so
            // debug builds still work on a fresh clone without a keystore.
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Local tests. android.jar only ships org.json stubs that throw at runtime, so
    // local JVM tests need the real implementation on the classpath.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)

    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// Builds the signed release APK, renames it to zebra-ig-demo-<tag>.apk using the
// current git tag, and copies it to the project root for easy distribution.
//
// Usage: ./gradlew releaseApk
val releaseApkDir = layout.buildDirectory.dir("outputs/apk/release")
val distributionDir = rootProject.layout.projectDirectory.asFile
val apkBaseName = "zebra-ig-demo"

tasks.register("releaseApk") {
    group = "release"
    description = "Builds, signs, renames and copies the release APK to the project root"
    dependsOn("assembleRelease")

    // Capture only serializable values so the task stays configuration-cache safe.
    val apkDir = releaseApkDir
    val outputDir = distributionDir
    val baseName = apkBaseName

    doLast {
        // Resolve the tag at execution time rather than configuration time, so a
        // newly created tag is always picked up even with the config cache warm.
        val tag = ProcessBuilder("git", "describe", "--tags", "--always")
            .directory(outputDir)
            .redirectErrorStream(true)
            .start()
            .let { process ->
                val output = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && output.isNotEmpty()) output else "untagged"
            }

        val signedApk = apkDir.get().asFile
            .listFiles { file -> file.isFile && file.extension == "apk" }
            ?.singleOrNull()

        checkNotNull(signedApk) {
            "Expected exactly one release APK in ${apkDir.get().asFile}. " +
                "Make sure keystore.properties points to a valid release key."
        }

        val destination = outputDir.resolve("$baseName-$tag.apk")
        signedApk.copyTo(destination, overwrite = true)
        logger.lifecycle("Release APK copied to: ${destination.absolutePath}")
    }
}
