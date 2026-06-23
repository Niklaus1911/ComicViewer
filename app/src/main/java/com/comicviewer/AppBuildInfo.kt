package com.comicviewer

data class AppBuildInfo(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val debug: Boolean,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: String,
    val buildTimestamp: String,
)

fun currentAppBuildInfo(): AppBuildInfo =
    AppBuildInfo(
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        debug = BuildConfig.DEBUG,
        minSdk = BuildConfig.APP_MIN_SDK,
        targetSdk = BuildConfig.APP_TARGET_SDK,
        compileSdk = BuildConfig.APP_COMPILE_SDK,
        buildTimestamp = GeneratedBuildStamp.BUILD_TIMESTAMP,
    )
