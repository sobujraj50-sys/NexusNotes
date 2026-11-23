import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has built-in Kotlin support, so the separate kotlin-android plugin is not applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing key comes from CI secrets (see .github/workflows/build.yml). Never commit the key.
val nexusKeystorePath: String? = System.getenv("NEXUS_KEYSTORE_PATH")
val nexusKeystorePassword: String? = System.getenv("NEXUS_KEYSTORE_PASSWORD")
val hasNexusKeystore = !nexusKeystorePath.isNullOrBlank() && !nexusKeystorePassword.isNullOrBlank()

android {
    namespace = "com.nexusnotes"
    // Compose 1.12 (BOM 2026.08.00) requires compileSdk 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexusnotes"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode=<run number> so every build is a valid update.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "0.5.1-m5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasNexusKeystore) {
            create("nexus") {
                storeFile = file(nexusKeystorePath!!)
                storePassword = nexusKeystorePassword
                keyAlias = "nexus"
                keyPassword = nexusKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 is enabled in Milestone 10 together with keep rules and a signing config.
            isMinifyEnabled = false
            if (hasNexusKeystore) {
                signingConfig = signingConfigs.getByName("nexus")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

ksp {
    // Room writes the database schema here (committed so future migrations can be tested).
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
