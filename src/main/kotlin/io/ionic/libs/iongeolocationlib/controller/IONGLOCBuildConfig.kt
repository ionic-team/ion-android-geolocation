package io.ionic.libs.iongeolocationlib.controller

import android.os.Build

/**
 * Build config wrapper object
 */
internal object IONGLOCBuildConfig {
    fun getAndroidSdkVersionCode(): Int = Build.VERSION.SDK_INT
}