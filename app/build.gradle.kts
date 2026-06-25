import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

val appCompileSdk = 36
val appCompileSdkMinor = 1
val appMinSdk = 24
val appTargetSdk = 36
val appVersionCode = 10
val appVersionName = "1.0.7"
val appCompileSdkDisplay = "$appCompileSdk.$appCompileSdkMinor"

abstract class GenerateBuildStampTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"))
        val escapedTimestamp = timestamp
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val packageDir = outputDir.get().asFile.resolve("com/comicviewer")

        packageDir.mkdirs()
        packageDir.resolve("GeneratedBuildStamp.java").writeText(
            """
            package com.comicviewer;

            public final class GeneratedBuildStamp {
                public static final String BUILD_TIMESTAMP = "$escapedTimestamp";

                private GeneratedBuildStamp() {
                }
            }
            """.trimIndent(),
        )
    }
}

android {
    namespace = "com.comicviewer"
    compileSdk {
        version = release(appCompileSdk) {
            minorApiLevel = appCompileSdkMinor
        }
    }

    defaultConfig {
        applicationId = "com.comicviewer"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("int", "APP_MIN_SDK", appMinSdk.toString())
        buildConfigField("int", "APP_TARGET_SDK", appTargetSdk.toString())
        buildConfigField("String", "APP_COMPILE_SDK", "\"$appCompileSdkDisplay\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalizedVariantName = variant.name.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
        val generateBuildStamp = tasks.register<GenerateBuildStampTask>(
            "generate${capitalizedVariantName}BuildStamp",
        ) {
            outputDir.set(layout.buildDirectory.dir("generated/source/buildStamp/${variant.name}"))
        }

        variant.sources.java?.addGeneratedSourceDirectory(
            generateBuildStamp,
            GenerateBuildStampTask::outputDir,
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.play.billing.ktx)

    constraints {
        implementation(libs.androidx.fragment) {
            because("Play Console flags the transitive androidx.fragment:fragment:1.1.0 SDK as obsolete.")
        }
    }

    testImplementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
