package org.hdhmc.bilihdpager.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.hdhmc.bilihdpager.BuildConfig

internal object Diagnostics {
    fun moduleSummary(): String =
        "module=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "flavor=${BuildConfig.FLAVOR} build=${BuildConfig.BUILD_TYPE} debug=${BuildConfig.DEBUG}"

    fun hostSummary(packageName: String): String {
        val context = currentApplicationContext()
            ?: return "host=$packageName version=unavailable"
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "host=$packageName version=${packageInfo.versionName ?: "unknown"}($versionCode)"
        }.getOrElse { error ->
            "host=$packageName version=unavailable reason=${error.javaClass.simpleName}"
        }
    }

    fun describeClass(clazz: Class<*>?): String =
        clazz?.name ?: "null"

    fun describeObject(value: Any?): String =
        value?.let { "${it.javaClass.name}@${System.identityHashCode(it).toString(16)}" } ?: "null"

    private fun currentApplicationContext(): Context? =
        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val method = activityThreadClass.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            (method.invoke(null) as? Context)?.applicationContext
        }.getOrNull()
}
