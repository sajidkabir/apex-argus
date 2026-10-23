plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.apexpredator.argus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apexpredator.argus"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "dist"

    signingConfigs {
        // Used only by the release workflow, which provides the keystore
        // through environment secrets. Local builds skip signing.
        // Declared before productFlavors: flavors reference it by name.
        create("githubRelease") {
            storeFile = file(System.getenv("GITHUB_KEYSTORE_PATH") ?: "missing.keystore")
            storePassword = System.getenv("GITHUB_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GITHUB_KEY_ALIAS")
            keyPassword = System.getenv("GITHUB_KEY_PASSWORD")
        }
    }

    productFlavors {
        // Distributed through Google Play. Must stay free of any
        // self-update mechanism: Play policy forbids updating a Play
        // app by any means other than Play itself.
        create("play") {
            dimension = "dist"
        }
        // Distributed as a public APK on GitHub Releases. Carries the
        // in-app updater (download + install new releases itself).
        create("github") {
            dimension = "dist"
            if (System.getenv("GITHUB_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("githubRelease")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // OpenStreetMap rendering for the live flight radar (no API key needed).
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // SGP4 satellite propagation for the satellite layer (computes
    // positions on-device from CelesTrak TLE data).
    implementation("com.github.davidmoten:predict4java:1.3.1")
    // OkHttp for the Nostr WebSocket client behind Argus Chat.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Aircraft photos in the detail sheet.
    implementation("io.coil-kt:coil-compose:2.7.0")
    // CameraX for the Spotter tab (point at the sky, name the aircraft).
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
