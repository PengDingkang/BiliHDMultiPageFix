package org.hdhmc.bilihdpager.legacy

import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.hdhmc.bilihdpager.BuildConfig
import org.hdhmc.bilihdpager.core.Constants
import org.hdhmc.bilihdpager.core.Diagnostics
import org.hdhmc.bilihdpager.core.FeatureLocator
import org.hdhmc.bilihdpager.core.HdMultiPageFix
import org.hdhmc.bilihdpager.core.HookLogger
import org.hdhmc.bilihdpager.core.RestorablePatch
import org.hdhmc.bilihdpager.core.debug
import org.hdhmc.bilihdpager.core.findClassOrNull
import org.hdhmc.bilihdpager.core.findDeclaredMethodOrNull
import org.hdhmc.bilihdpager.core.findVideoDetailInitMethodOrNull
import org.hdhmc.bilihdpager.core.findFirstClassOrNull
import org.hdhmc.bilihdpager.core.findFirstDeclaredMethodOrNull
import org.hdhmc.bilihdpager.core.getStaticObjectFieldOrNull

class LegacyEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != Constants.HD_PACKAGE_NAME) return
        if (lpparam.processName != Constants.HD_PACKAGE_NAME) return

        val logger = LegacyLogger()
        val fix = HdMultiPageFix(logger)
        val classLoader = lpparam.classLoader
        logger.debug {
            "handleLoadPackage: package=${lpparam.packageName} process=${lpparam.processName} " +
                    "${Diagnostics.moduleSummary()} ${Diagnostics.hostSummary(Constants.HD_PACKAGE_NAME)}"
        }

        val videoItemClass = classLoader.findClassOrNull(Constants.VIDEO_ITEM_CLASS)
            ?: return logger.error("Class not found: ${Constants.VIDEO_ITEM_CLASS}")
        val directorClass = classLoader.findClassOrNull(Constants.DIRECTOR_CLASS)
            ?: return logger.error("Class not found: ${Constants.DIRECTOR_CLASS}")
        val videoDetailClass = classLoader.findClassOrNull(Constants.VIDEO_DETAIL_CLASS)
        val videoClass = classLoader.findClassOrNull(Constants.VIDEO_CLASS)
        val featureLocator = if (videoDetailClass != null && videoClass != null) {
            FeatureLocator(classLoader, logger, lpparam.appInfo?.sourceDir)
        } else {
            null
        }
        val knownSourceWrapperClass = classLoader.findFirstClassOrNull(Constants.SOURCE_WRAPPER_CLASSES)
        val sourceWrapperClass = knownSourceWrapperClass ?: if (
            featureLocator != null &&
            videoDetailClass != null &&
            videoClass != null
        ) {
            featureLocator.findSourceWrapperClass(videoDetailClass, videoClass)
        } else {
            null
        }
        val knownNormalSourceClass = classLoader.findFirstClassOrNull(Constants.NORMAL_SOURCE_CLASSES)
        if (
            featureLocator != null &&
            videoDetailClass != null &&
            videoClass != null &&
            (knownSourceWrapperClass == null || knownNormalSourceClass == null)
        ) {
            featureLocator.debugLogVideoSourceCandidates(videoDetailClass, videoClass)
        }
        val normalSourceClass = knownNormalSourceClass ?: if (
            featureLocator != null &&
            videoDetailClass != null &&
            videoClass != null
        ) {
            featureLocator.findNormalSourceClass(videoDetailClass, videoClass, sourceWrapperClass)
        } else {
            null
        }
        val videoDetailHelperClass = classLoader.findClassOrNull(Constants.VIDEO_DETAIL_HELPER_CLASS)
        val downloadSeasonContainerClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_SEASON_CONTAINER_CLASS)
        val downloadCallbackClass = classLoader.findClassOrNull(Constants.DOWNLOAD_CALLBACK_CLASS)
        val downloadClientClass = classLoader.findClassOrNull(Constants.DOWNLOAD_CLIENT_CLASS)
        val downloadNormalCoreClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_NORMAL_CORE_CLASS)
        val downloadSeasonCoreClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_SEASON_CORE_CLASS)
        val knownDownloadControllerClass =
            classLoader.findFirstClassOrNull(Constants.DOWNLOAD_CONTROLLER_CLASSES)
        val downloadControllerClass = knownDownloadControllerClass ?: if (
            featureLocator != null &&
            videoDetailClass != null &&
            downloadNormalCoreClass != null &&
            downloadSeasonCoreClass != null
        ) {
            featureLocator.findDownloadControllerClass(
                videoDetailClass = videoDetailClass,
                downloadNormalCoreClass = downloadNormalCoreClass,
                downloadSeasonCoreClass = downloadSeasonCoreClass,
            )
        } else {
            null
        }
        if (
            featureLocator != null &&
            videoDetailClass != null &&
            downloadNormalCoreClass != null &&
            downloadSeasonCoreClass != null &&
            knownDownloadControllerClass == null
        ) {
            featureLocator.debugLogDownloadControllerCandidates(
                videoDetailClass = videoDetailClass,
                downloadNormalCoreClass = downloadNormalCoreClass,
                downloadSeasonCoreClass = downloadSeasonCoreClass,
            )
        }
        val downloadNormalProviderClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_NORMAL_PROVIDER_CLASS)
        val introductionDetailFragmentClass =
            classLoader.findClassOrNull(Constants.INTRODUCTION_DETAIL_FRAGMENT_CLASS)
        val playerServiceClasses = Constants.PLAYER_SERVICE_CLASSES.mapNotNull { classLoader.findClassOrNull(it) }
        val fullscreenSeasonSelectorClass =
            classLoader.findFirstClassOrNull(Constants.FULLSCREEN_SEASON_SELECTOR_CLASSES)
        val fullscreenSelectorCallbackClass =
            classLoader.findFirstClassOrNull(Constants.FULLSCREEN_SELECTOR_CALLBACK_CLASSES)
        val knownSelectorDataProviderClass =
            classLoader.findFirstClassOrNull(Constants.SELECTOR_DATA_PROVIDER_CLASSES)
        val selectorDataProviderClass = knownSelectorDataProviderClass ?: if (
            featureLocator != null &&
            playerServiceClasses.isNotEmpty()
        ) {
            featureLocator.findSelectorDataProviderClass(playerServiceClasses)
        } else {
            null
        }
        if (
            featureLocator != null &&
            playerServiceClasses.isNotEmpty() &&
            knownSelectorDataProviderClass == null
        ) {
            featureLocator.debugLogSelectorDataProviderCandidates(playerServiceClasses)
        }
        val selectorItemClass = classLoader.findClassOrNull(Constants.SELECTOR_ITEM_CLASS)
        val sourceTypeClass = classLoader.findClassOrNull(Constants.SOURCE_TYPE_CLASS)
        val normalSourceType = sourceTypeClass?.getStaticObjectFieldOrNull("TypeNormal")
        val playerServiceSummary =
            playerServiceClasses.joinToString(prefix = "[", postfix = "]") { Diagnostics.describeClass(it) }
        logger.debug {
            "Class lookup: videoItem=${Diagnostics.describeClass(videoItemClass)} " +
                    "director=${Diagnostics.describeClass(directorClass)} " +
                    "normalSource=${Diagnostics.describeClass(normalSourceClass)} " +
                    "sourceWrapper=${Diagnostics.describeClass(sourceWrapperClass)} " +
                    "videoDetail=${Diagnostics.describeClass(videoDetailClass)} " +
                    "video=${Diagnostics.describeClass(videoClass)} " +
                    "videoDetailHelper=${Diagnostics.describeClass(videoDetailHelperClass)} " +
                    "downloadSeason=${Diagnostics.describeClass(downloadSeasonContainerClass)} " +
                    "downloadCallback=${Diagnostics.describeClass(downloadCallbackClass)} " +
                    "downloadClient=${Diagnostics.describeClass(downloadClientClass)} " +
                    "downloadController=${Diagnostics.describeClass(downloadControllerClass)} " +
                    "downloadNormalCore=${Diagnostics.describeClass(downloadNormalCoreClass)} " +
                    "downloadSeasonCore=${Diagnostics.describeClass(downloadSeasonCoreClass)} " +
                    "downloadNormalProvider=${Diagnostics.describeClass(downloadNormalProviderClass)} " +
                    "introductionDetail=${Diagnostics.describeClass(introductionDetailFragmentClass)} " +
                    "playerService=$playerServiceSummary " +
                    "fullscreenSeasonSelector=${Diagnostics.describeClass(fullscreenSeasonSelectorClass)} " +
                    "fullscreenSelectorCallback=${Diagnostics.describeClass(fullscreenSelectorCallbackClass)} " +
                    "selectorDataProvider=${Diagnostics.describeClass(selectorDataProviderClass)} " +
                    "selectorItem=${Diagnostics.describeClass(selectorItemClass)} " +
                    "sourceType=${Diagnostics.describeClass(sourceTypeClass)} " +
                    "normalSourceType=${Diagnostics.describeObject(normalSourceType)}"
        }
        if (normalSourceClass == null) {
            logger.error("Class not found: ${Constants.NORMAL_SOURCE_CLASSES}; nested page playback fix disabled")
        }
        if (sourceWrapperClass == null) {
            logger.error("Class not found: ${Constants.SOURCE_WRAPPER_CLASSES}; playback source replacement disabled")
        }
        if (videoClass == null) {
            logger.error("Class not found: ${Constants.VIDEO_CLASS}; feature matching disabled")
        }
        if (videoDetailClass == null || videoDetailHelperClass == null) {
            logger.error("Playback source classification fix disabled: required classes not found")
        }
        if (
            videoDetailClass == null ||
            downloadSeasonContainerClass == null ||
            downloadCallbackClass == null ||
            downloadClientClass == null
        ) {
            logger.error("Download cache page-list fix disabled: required classes not found")
        }
        if (
            downloadControllerClass == null ||
            downloadNormalCoreClass == null ||
            downloadSeasonCoreClass == null ||
            downloadNormalProviderClass == null
        ) {
            logger.error("Download provider redirect disabled: required classes not found")
        }
        if (introductionDetailFragmentClass == null || videoDetailClass == null) {
            logger.error("Online detail page selector fix disabled: required classes not found")
        }
        if (
            fullscreenSeasonSelectorClass == null ||
            fullscreenSelectorCallbackClass == null ||
            selectorDataProviderClass == null ||
            playerServiceClasses.isEmpty() ||
            selectorItemClass == null ||
            normalSourceType == null
        ) {
            logger.error("Fullscreen selector page-list fix disabled: required classes not found")
        }
        fun findSelectorProviderMethod(
            name: String,
            vararg extraParameterTypes: Class<*>,
        ) = selectorDataProviderClass?.let { providerClass ->
            playerServiceClasses.firstNotNullOfOrNull { serviceClass ->
                val parameterTypes = arrayOf(serviceClass, *extraParameterTypes)
                providerClass.findDeclaredMethodOrNull(name, *parameterTypes)
            }
        }

        var installedHooks = 0
        val directorMethod = directorClass.findFirstDeclaredMethodOrNull(
            Constants.DIRECTOR_PLAY_ITEM_METHODS,
            videoItemClass,
        )
        if (directorMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    directorMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (fix.redirectGlobalIndex(param.thisObject, param.args[0])) {
                                param.result = null
                            }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${Constants.DIRECTOR_CLASS}#${directorMethod.name}" }
            }.onFailure { error ->
                logger.error("Failed to hook ${Constants.DIRECTOR_CLASS}#${directorMethod.name}", error)
            }
        } else {
            logger.error(
                "Method not found: ${Constants.DIRECTOR_CLASS}#${Constants.DIRECTOR_PLAY_ITEM_METHODS}; " +
                        "detail page global index redirect disabled"
            )
        }

        val normalSourceDetailMethod = if (videoDetailClass != null && videoDetailHelperClass != null) {
            videoDetailHelperClass.findDeclaredMethodOrNull("V", videoDetailClass)
        } else {
            null
        }
        if (normalSourceDetailMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    normalSourceDetailMethod,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result == false && fix.shouldUseNormalVideoSource(param.args[0])) {
                                param.result = true
                            }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${Constants.VIDEO_DETAIL_HELPER_CLASS}#V" }
            }.onFailure { error ->
                logger.error("Failed to hook ${Constants.VIDEO_DETAIL_HELPER_CLASS}#V", error)
            }
        } else if (videoDetailClass != null && videoDetailHelperClass != null) {
            logger.error("Method not found: ${Constants.VIDEO_DETAIL_HELPER_CLASS}#V")
        }

        val sourceWrapperInitMethod = if (sourceWrapperClass != null && videoDetailClass != null) {
            sourceWrapperClass.findFirstDeclaredMethodOrNull(
                Constants.SOURCE_WRAPPER_INIT_METHODS,
                videoDetailClass,
                Bundle::class.java,
            ) ?: sourceWrapperClass.findVideoDetailInitMethodOrNull(videoDetailClass)
        } else {
            null
        }
        if (sourceWrapperInitMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    sourceWrapperInitMethod,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.replaceWrapperWithNormalSource(
                                wrapper = param.thisObject,
                                detail = param.args[0],
                                bundle = param.args[1] as? Bundle,
                                normalSourceClass = normalSourceClass,
                            )
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug {
                    "Hook installed: ${sourceWrapperInitMethod.declaringClass.name}#${sourceWrapperInitMethod.name}"
                }
            }.onFailure { error ->
                logger.error(
                    "Failed to hook ${sourceWrapperInitMethod.declaringClass.name}#${sourceWrapperInitMethod.name}",
                    error,
                )
            }
        } else if (sourceWrapperClass != null && videoDetailClass != null) {
            logger.error("Method not found: ${sourceWrapperClass.name}#${Constants.SOURCE_WRAPPER_INIT_METHODS}")
        }

        val downloadMethod = if (
            videoDetailClass != null &&
            downloadSeasonContainerClass != null &&
            downloadCallbackClass != null &&
            downloadClientClass != null
        ) {
            downloadSeasonContainerClass.findDeclaredMethodOrNull(
                "c",
                videoDetailClass,
                downloadCallbackClass,
                downloadClientClass,
            )
        } else {
            null
        }
        if (downloadMethod != null) {
            val activePatch = ThreadLocal<RestorablePatch?>()
            runCatching {
                XposedBridge.hookMethod(
                    downloadMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            activePatch.set(fix.patchNestedDownloadSections(param.args[0]))
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            activePatch.get()?.restore()
                            activePatch.remove()
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${Constants.DOWNLOAD_SEASON_CONTAINER_CLASS}#c" }
            }.onFailure { error ->
                logger.error("Failed to hook ${Constants.DOWNLOAD_SEASON_CONTAINER_CLASS}#c", error)
            }
        } else if (downloadSeasonContainerClass != null) {
            logger.debug {
                "Optional method not found: ${Constants.DOWNLOAD_SEASON_CONTAINER_CLASS}#c; " +
                        "download page-list patch skipped"
            }
        }

        val downloadControllerMethod = if (
            videoDetailClass != null &&
            downloadControllerClass != null &&
            downloadNormalCoreClass != null &&
            downloadSeasonCoreClass != null &&
            downloadNormalProviderClass != null
        ) {
            downloadControllerClass.findDeclaredMethodOrNull(
                "g",
                videoDetailClass,
                java.lang.Long.TYPE,
                downloadNormalCoreClass,
                downloadSeasonCoreClass,
            )
        } else {
            null
        }
        if (downloadControllerMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    downloadControllerMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (
                                fix.useNormalDownloadProviderForNestedPages(
                                    controller = param.thisObject,
                                    detail = param.args[0],
                                    cid = param.args[1] as Long,
                                    normalCore = param.args[2],
                                    normalProviderClass = downloadNormalProviderClass,
                                )
                            ) {
                                param.result = null
                            }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${downloadControllerMethod.declaringClass.name}#g" }
            }.onFailure { error ->
                logger.error("Failed to hook ${downloadControllerMethod.declaringClass.name}#g", error)
            }
        } else if (downloadControllerClass != null) {
            logger.error("Method not found: ${downloadControllerClass.name}#g")
        }

        if (introductionDetailFragmentClass != null && videoDetailClass != null) {
            Constants.INTRODUCTION_PAGE_SELECTOR_METHODS.forEach { methodName ->
                val selectorMethod = introductionDetailFragmentClass.findDeclaredMethodOrNull(
                    methodName,
                    videoDetailClass,
                )
                if (selectorMethod != null) {
                    runCatching {
                        XposedBridge.hookMethod(
                            selectorMethod,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (fix.shouldUseNormalPageSelector(param.args[0])) {
                                        param.result = false
                                    }
                                }
                            }
                        )
                    }.onSuccess {
                        installedHooks += 1
                        logger.debug { "Hook installed: ${Constants.INTRODUCTION_DETAIL_FRAGMENT_CLASS}#$methodName" }
                    }.onFailure { error ->
                        logger.error(
                            "Failed to hook ${Constants.INTRODUCTION_DETAIL_FRAGMENT_CLASS}#$methodName",
                            error,
                        )
                    }
                } else {
                    logger.debug {
                        "Optional method not found: ${Constants.INTRODUCTION_DETAIL_FRAGMENT_CLASS}#$methodName"
                    }
                }
            }
        }

        val selectorItemsMethod = findSelectorProviderMethod("c")
        if (selectorItemsMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    selectorItemsMethod,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.normalPageSelectorItems(
                                provider = param.thisObject,
                                playerService = param.args[0],
                                normalSourceClass = normalSourceClass,
                                selectorItemClass = selectorItemClass,
                            )?.let { param.result = it }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${selectorItemsMethod.declaringClass.name}#c" }
            }.onFailure { error ->
                logger.error("Failed to hook ${selectorItemsMethod.declaringClass.name}#c", error)
            }
        } else if (selectorDataProviderClass != null) {
            logger.error("Method not found: ${selectorDataProviderClass.name}#c")
        }

        val selectorIndexMethod = findSelectorProviderMethod("b")
        if (selectorIndexMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    selectorIndexMethod,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.normalPageSelectorIndex(
                                provider = param.thisObject,
                                playerService = param.args[0],
                                normalSourceClass = normalSourceClass,
                                selectorItemClass = selectorItemClass,
                            )?.let { param.result = it }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${selectorIndexMethod.declaringClass.name}#b" }
            }.onFailure { error ->
                logger.error("Failed to hook ${selectorIndexMethod.declaringClass.name}#b", error)
            }
        } else if (selectorDataProviderClass != null) {
            logger.error("Method not found: ${selectorDataProviderClass.name}#b")
        }

        val selectorSelectMethod = findSelectorProviderMethod("g", java.lang.Integer.TYPE)
        if (selectorSelectMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    selectorSelectMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (
                                fix.selectNormalPageFromFullscreenProvider(
                                    provider = param.thisObject,
                                    playerService = param.args[0],
                                    index = param.args[1] as Int,
                                    normalSourceClass = normalSourceClass,
                                )
                            ) {
                                param.result = null
                            }
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${selectorSelectMethod.declaringClass.name}#g" }
            }.onFailure { error ->
                logger.error("Failed to hook ${selectorSelectMethod.declaringClass.name}#g", error)
            }
        } else if (selectorDataProviderClass != null) {
            logger.error("Method not found: ${selectorDataProviderClass.name}#g")
        }

        val fullscreenClickMethod = fullscreenSelectorCallbackClass
            ?.findDeclaredMethodOrNull("c", java.lang.Integer.TYPE)
        if (fullscreenClickMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    fullscreenClickMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            fix.selectNormalPageFromFullscreenCallback(
                                callback = param.thisObject,
                                index = param.args[0] as Int,
                                normalSourceClass = normalSourceClass,
                            )
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.clearFullscreenCallbackSelectionMarker()
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${fullscreenClickMethod.declaringClass.name}#c" }
            }.onFailure { error ->
                logger.error("Failed to hook ${fullscreenClickMethod.declaringClass.name}#c", error)
            }
        } else if (fullscreenSelectorCallbackClass != null) {
            logger.error("Method not found: ${fullscreenSelectorCallbackClass.name}#c")
        }

        val fullscreenBindMethodNames = fullscreenSeasonSelectorClass?.name
            ?.let { Constants.FULLSCREEN_BIND_METHODS_BY_CLASS[it] }
            .orEmpty()
        val fullscreenBindMethod = fullscreenSeasonSelectorClass
            ?.findFirstDeclaredMethodOrNull(fullscreenBindMethodNames)
        if (fullscreenBindMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    fullscreenBindMethod,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            fix.prepareFullscreenSeasonSelector(param.thisObject, normalSourceType)
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.patchFullscreenSeasonSelector(
                                panel = param.thisObject,
                                normalSourceClass = normalSourceClass,
                                selectorItemClass = selectorItemClass,
                                normalSourceType = normalSourceType,
                            )
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${fullscreenBindMethod.declaringClass.name}#${fullscreenBindMethod.name}" }
            }.onFailure { error ->
                logger.error(
                    "Failed to hook ${fullscreenBindMethod.declaringClass.name}#${fullscreenBindMethod.name}",
                    error,
                )
            }
        } else if (fullscreenSeasonSelectorClass != null) {
            logger.error("Method not found: ${fullscreenSeasonSelectorClass.name}#$fullscreenBindMethodNames")
        }

        val fullscreenHeaderMethodNames = fullscreenSeasonSelectorClass?.name
            ?.let { Constants.FULLSCREEN_REFRESH_METHODS_BY_CLASS[it] }
            .orEmpty()
        val fullscreenHeaderMethod = fullscreenSeasonSelectorClass
            ?.findFirstDeclaredMethodOrNull(fullscreenHeaderMethodNames)
        if (fullscreenHeaderMethod != null) {
            runCatching {
                XposedBridge.hookMethod(
                    fullscreenHeaderMethod,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            fix.patchFullscreenSeasonSelector(
                                panel = param.thisObject,
                                normalSourceClass = normalSourceClass,
                                selectorItemClass = selectorItemClass,
                                normalSourceType = normalSourceType,
                            )
                        }
                    }
                )
            }.onSuccess {
                installedHooks += 1
                logger.debug { "Hook installed: ${fullscreenHeaderMethod.declaringClass.name}#${fullscreenHeaderMethod.name}" }
            }.onFailure { error ->
                logger.error(
                    "Failed to hook ${fullscreenHeaderMethod.declaringClass.name}#${fullscreenHeaderMethod.name}",
                    error,
                )
            }
        } else if (fullscreenSeasonSelectorClass != null) {
            logger.error("Method not found: ${fullscreenSeasonSelectorClass.name}#$fullscreenHeaderMethodNames")
        }

        if (installedHooks > 0) {
            logger.info("HD multi-page fix installed: hooks=$installedHooks")
        } else {
            logger.error("HD multi-page fix not installed: no hooks installed")
        }
    }

    private class LegacyLogger : HookLogger {
        override val isDebugEnabled: Boolean = BuildConfig.DEBUG

        override fun info(message: String) {
            XposedBridge.log("${Constants.TAG}: $message")
        }

        override fun debug(message: String) {
            if (isDebugEnabled) XposedBridge.log("${Constants.TAG}: [debug] $message")
        }

        override fun error(message: String, error: Throwable?) {
            XposedBridge.log("${Constants.TAG}: $message")
            if (error != null) XposedBridge.log(error)
        }
    }
}
