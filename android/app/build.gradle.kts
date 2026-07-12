plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.privatemessenger"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.privatemessenger"
        minSdk = 26       // Android 8.0 — baseline for strong Keystore crypto APIs
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Room Database — local persistence for messages, sessions, contacts
    // -------------------------------------------------------------------------
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // -------------------------------------------------------------------------
    // SQLCipher — encrypts the Room database at rest using a Keystore-derived
    // passphrase so a device extraction does not expose message history
    // -------------------------------------------------------------------------
    implementation("net.zetetic:sqlcipher-android:4.5.4@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // -------------------------------------------------------------------------
    // Android Security — EncryptedSharedPreferences for small secrets
    // -------------------------------------------------------------------------
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // -------------------------------------------------------------------------
    // Firebase Cloud Messaging
    // -------------------------------------------------------------------------
    implementation("com.google.firebase:firebase-messaging:23.4.0")

    // -------------------------------------------------------------------------
    // Networking (Phase 4 — included now so Gradle resolves cleanly)
    // -------------------------------------------------------------------------
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // -------------------------------------------------------------------------
    // Jetpack Compose UI (Phase 4)
    // -------------------------------------------------------------------------
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("io.coil-kt:coil-compose:2.5.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // -------------------------------------------------------------------------
    // Core Android
    // -------------------------------------------------------------------------
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // -------------------------------------------------------------------------
    // Firebase Cloud Messaging (Phase 4)
    // -------------------------------------------------------------------------
    // implementation("com.google.firebase:firebase-messaging-ktx:23.4.0")
    // -------------------------------------------------------------------------
    // QR Code Scanning & Generation
    // -------------------------------------------------------------------------
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.zxing:core:3.5.1")
    // -------------------------------------------------------------------------
    // Web3 / Blockchain Integration
    // -------------------------------------------------------------------------
    implementation("org.xmtp:android:4.10.0")
    implementation("org.web3j:core:4.10.3")
}
