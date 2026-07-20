import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // ── Android target ──────────────────────────────────────────────────────
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // ── iOS targets (device + simulators) ───────────────────────────────────
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // ── Source sets ─────────────────────────────────────────────────────────
    sourceSets {
        // ── Common ─────────────────────────────────────────────────────────
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Room KMP (entities, DAOs, abstract database)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Lifecycle ViewModels (KMP-compatible since 2.8+)
            implementation(libs.lifecycle.viewmodel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // ── Android ────────────────────────────────────────────────────────
        androidMain.dependencies {
            // Compose for Android
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.activity.compose)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.ktx)
            implementation(libs.androidx.core.ktx)
            implementation(libs.coil.compose)

            // Room Android (SQLCipher encrypted driver)
            implementation(libs.room.runtime.android)
            implementation(libs.sqlcipher.android)
            implementation(libs.sqlite.ktx)

            // Android Keystore secure storage
            implementation(libs.security.crypto)

            // XMTP Android SDK
            implementation(libs.xmtp.android)
            implementation(libs.web3j.core)

            // Firebase Cloud Messaging (push notifications)
            implementation(libs.firebase.messaging)

            // Camera + QR
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.guava)
            implementation(libs.mlkit.barcode)
            implementation(libs.zxing)

            // Networking
            implementation(libs.okhttp)
            implementation(libs.retrofit)
            implementation(libs.retrofit.gson)

            // Coroutines Android dispatcher
            implementation(libs.kotlinx.coroutines.android)
        }

        // ── iOS ────────────────────────────────────────────────────────────
        iosMain.dependencies {
            // Room iOS bundled SQLite driver
            implementation(libs.sqlite.bundled)
        }
    }
}

// ── Android configuration ────────────────────────────────────────────────────
android {
    namespace = "com.privatemessenger.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26 // Android 8.0 — Android Keystore crypto baseline
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ── Room schema directory ────────────────────────────────────────────────────
room {
    schemaDirectory("$projectDir/schemas")
}

// ── KSP source sets for Room code generation ────────────────────────────────
dependencies {
    // Room compiler runs on both Android and iOS targets via KSP
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
