import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing, kept out of the repository: the properties file named by
// TRASHMAILS_KEYSTORE_PROPERTIES, else ~/.trashmails/keystore.properties (a keystore.properties at
// the root, git-ignored, is still read as a last resort). Its storeFile is relative to that file.
// Without one, the release build stays unsigned (as F-Droid builds it).
val keystorePropsFile = listOfNotNull(
    System.getenv("TRASHMAILS_KEYSTORE_PROPERTIES")?.let(::File),
    File(System.getProperty("user.home"), ".trashmails/keystore.properties"),
    rootProject.file("keystore.properties"),
).firstOrNull { it.isFile }
val keystoreProps = Properties().apply {
    keystorePropsFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.usernamealreadytakensht.trashmails"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.usernamealreadytakensht.trashmails"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "0.5.8"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = keystorePropsFile!!.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: code shrinking + resource shrinking (the debug variant stays unoptimised).
            optimization {
                enable = true
            }
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            // No commit hash in the APK: the build does not depend on the checkout it comes from.
            vcsInfo.include = false
        }
    }
    // No Google-encrypted dependency metadata in the signing block (F-Droid rejects the opaque blob).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // kotlinx-coroutines' debug-agent data: unused in the app.
        resources.excludes += "DebugProbesKt.bin"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    // Real org.json for the JVM tests: the one in android.jar is a stub.
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}