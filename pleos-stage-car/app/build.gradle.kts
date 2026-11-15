plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.teamcarinkaraoke.pleosstage.car"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.teamcarinkaraoke.pleosstage.car"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-PLEOS"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "IS_DEV_BUILD", "true")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "IS_DEV_BUILD", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.*"
        }
    }
}

dependencies {
    // Official Pleos Connect Vehicle SDK from Nexus Playground Maven Repository (latest release 2.3.2)
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    // Embedded Ktor Signaling Server (CIO Engine + WebSockets) for Standalone Car-to-Phone Direct Wi-Fi Pairing
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // High-resolution QR Code generator for Car Dashboard Pairing Card
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
