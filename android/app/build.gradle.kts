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
        versionCode = 2
        versionName = "1.1.0"
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

    signingConfigs {
        // Used only by the release workflow, which provides the keystore
        // through environment secrets. Local builds skip signing.
        create("githubRelease") {
            storeFile = file(System.getenv("GITHUB_KEYSTORE_PATH") ?: "missing.keystore")
            storePassword = System.getenv("GITHUB_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GITHUB_KEY_ALIAS")
            keyPassword = System.getenv("GITHUB_KEY_PASSWORD")
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

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
