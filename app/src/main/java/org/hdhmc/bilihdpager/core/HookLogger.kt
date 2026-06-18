package org.hdhmc.bilihdpager.core

internal interface HookLogger {
    val isDebugEnabled: Boolean

    fun info(message: String)
    fun debug(message: String)
    fun error(message: String, error: Throwable? = null)
}

internal inline fun HookLogger.debug(message: () -> String) {
    if (isDebugEnabled) debug(message())
}
