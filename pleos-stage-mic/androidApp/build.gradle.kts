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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
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
        logger.lifecycle("Exported androidApp release APK to apk/ directory: ${dest.absolutePath} (${dest.length()} bytes)")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "io.github.teamcarinkaraoke.pleosstage.mic"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.teamcarinkaraoke.pleosstage.mic"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

val exportReleaseApkToApkDir = tasks.register<ExportReleaseApkToApkDirTask>("exportReleaseApkToApkDir") {
    destinationApk.set(rootProject.projectDir.parentFile.resolve("apk/팀카인노래방_카인노래방마이크_release.apk"))
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