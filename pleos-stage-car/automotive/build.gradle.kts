import com.android.build.api.artifact.SingleArtifact
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

@DisableCachingByDefault(because = "Always export latest signed release APK to apk/ directory")
abstract class ExportReleaseApkToApkDirTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDir: DirectoryProperty

    @get:OutputFile
    abstract val destinationApk: RegularFileProperty

    @TaskAction
    fun exportApk() {
        val dir = apkDir.get().asFile
        val sourceApk = dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("No release APK found in ${dir.absolutePath}")
        val dest = destinationApk.get().asFile
        dest.parentFile?.mkdirs()
        Files.copy(sourceApk.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        dest.setLastModified(System.currentTimeMillis())
        logger.lifecycle("Exported automotive release APK to apk/ directory: ${dest.absolutePath} (${dest.length()} bytes)")
    }
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
    implementation(libs.androidx.media)
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

val exportReleaseApkToApkDir = tasks.register<ExportReleaseApkToApkDirTask>("exportReleaseApkToApkDir") {
    destinationApk.set(rootProject.projectDir.parentFile.resolve("apk/팀카인노래방_카인노래방_release.apk"))
    outputs.upToDateWhen { false }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        exportReleaseApkToApkDir.configure {
            apkDir.set(variant.artifacts.get(SingleArtifact.APK))
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(exportReleaseApkToApkDir)
}
