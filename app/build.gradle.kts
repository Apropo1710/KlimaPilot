plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // The ioBroker energy script ships inside the app so KlimaPilot can install and update it.
    sourceSets["main"].assets.srcDir(rootProject.file("scripts/iobroker"))
    namespace = "de.klimapilot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.klimapilot.app"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 167
        versionName = "0.5.37"
    }

    signingConfigs {
        // Stable test signature for debug builds. The private key is never committed:
        // CI decodes it from the KLIMAPILOT_TEST_* GitHub Actions secrets into RUNNER_TEMP.
        // Without these variables (local builds) the default Android debug key is used;
        // the CI fingerprint check rejects such APKs.
        val testKeystorePath = System.getenv("KLIMAPILOT_TEST_KEYSTORE_PATH")
        if (!testKeystorePath.isNullOrBlank()) {
            create("testDebug") {
                storeFile = file(testKeystorePath)
                storeType = "PKCS12"
                storePassword = System.getenv("KLIMAPILOT_TEST_STORE_PASSWORD")
                keyAlias = System.getenv("KLIMAPILOT_TEST_KEY_ALIAS")
                keyPassword = System.getenv("KLIMAPILOT_TEST_KEY_PASSWORD")
            }
        }
        create("release") {
            storeFile = file(System.getenv("KLIMAPILOT_KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("KLIMAPILOT_STORE_PASSWORD")
            keyAlias = System.getenv("KLIMAPILOT_KEY_ALIAS")
            keyPassword = System.getenv("KLIMAPILOT_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfigs.findByName("testDebug")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
