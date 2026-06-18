package org.hdhmc.bilihdpager.modern

import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.hdhmc.bilihdpager.BuildConfig
import org.hdhmc.bilihdpager.core.Constants
import org.hdhmc.bilihdpager.core.Diagnostics
import org.hdhmc.bilihdpager.core.FeatureLocator
import org.hdhmc.bilihdpager.core.HdMultiPageFix
import org.hdhmc.bilihdpager.core.HookLogger
import org.hdhmc.bilihdpager.core.debug
import org.hdhmc.bilihdpager.core.findClassOrNull
import org.hdhmc.bilihdpager.core.findDeclaredMethodOrNull
import org.hdhmc.bilihdpager.core.findVideoDetailInitMethodOrNull
import org.hdhmc.bilihdpager.core.findFirstClassOrNull
import org.hdhmc.bilihdpager.core.findFirstDeclaredMethodOrNull
import org.hdhmc.bilihdpager.core.getStaticObjectFieldOrNull

class ModernEntry : XposedModule() {
    private var isTargetProcess = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        isTargetProcess = param.processName == Constants.HD_PACKAGE_NAME
        if (BuildConfig.DEBUG && isTargetProcess) {
            log(Log.DEBUG, Constants.TAG, "onModuleLoaded: process=${param.processName} ${Diagnostics.moduleSummary()}")
        }
        if (!isTargetProcess) detach()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!isTargetProcess) return
        if (param.packageName != Constants.HD_PACKAGE_NAME) return
        if (!param.isFirstPackage) return

        val logger = ModernLogger()
        val fix = HdMultiPageFix(logger)
        val classLoader = param.classLoader
        logger.debug {
            "onPackageReady: package=${param.packageName} isFirstPackage=${param.isFirstPackage} " +
                    "${Diagnostics.moduleSummary()} ${Diagnostics.hostSummary(Constants.HD_PACKAGE_NAME)}"
        }

        val videoItemClass = classLoader.findClassOrNull(Constants.VIDEO_ITEM_CLASS)
            ?: return logger.error("Class not found: ${Constants.VIDEO_ITEM_CLASS}")
        val directorClass = classLoader.findClassOrNull(Constants.DIRECTOR_CLASS)
            ?: return logger.error("Class not found: ${Constants.DIRECTOR_CLASS}")
        val videoDetailClass = classLoader.findClassOrNull(Constants.VIDEO_DETAIL_CLASS)
        val videoClass = classLoader.findClassOrNull(Constants.VIDEO_CLASS)
        val featureLocator = if (videoDetailClass != null && videoClass != null) {
            FeatureLocator(classLoader, logger)
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
        val downloadControllerClass =
            classLoader.findFirstClassOrNull(Constants.DOWNLOAD_CONTROLLER_CLASSES)
        val downloadNormalCoreClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_NORMAL_CORE_CLASS)
        val downloadSeasonCoreClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_SEASON_CORE_CLASS)
        val downloadNormalProviderClass =
            classLoader.findClassOrNull(Constants.DOWNLOAD_NORMAL_PROVIDER_CLASS)
        val introductionDetailFragmentClass =
            classLoader.findClassOrNull(Constants.INTRODUCTION_DETAIL_FRAGMENT_CLASS)
        val playerServiceClasses = Constants.PLAYER_SERVICE_CLASSES.mapNotNull { classLoader.findClassOrNull(it) }
        val fullscreenSeasonSelectorClass =
            classLoader.findFirstClassOrNull(Constants.FULLSCREEN_SEASON_SELECTOR_CLASSES)
        val fullscreenSelectorCallbackClass =
            classLoader.findFirstClassOrNull(Constants.FULLSCREEN_SELECTOR_CALLBACK_CLASSES)
        val selectorDataProviderClass =
            classLoader.findFirstClassOrNull(Constants.SELECTOR_DATA_PROVIDER_CLASSES)
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
                hook(directorMethod).intercept { chain ->
                    if (fix.redirectGlobalIndex(chain.thisObject, chain.getArg(0))) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
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
                hook(normalSourceDetailMethod).intercept { chain ->
                    val result = chain.proceed()
                    if (result == false && fix.shouldUseNormalVideoSource(chain.getArg(0))) {
                        true
                    } else {
                        result
                    }
                }
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
                hook(sourceWrapperInitMethod).intercept { chain ->
                    val result = chain.proceed()
                    fix.replaceWrapperWithNormalSource(
                        wrapper = chain.thisObject,
                        detail = chain.getArg(0),
                        bundle = chain.getArg(1) as? Bundle,
                        normalSourceClass = normalSourceClass,
                    )
                    result
                }
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
            runCatching {
                hook(downloadMethod).intercept { chain ->
                    val patch = fix.patchNestedDownloadSections(chain.getArg(0))
                    try {
                        chain.proceed()
                    } finally {
                        patch?.restore()
                    }
                }
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
            downloadSeasonCoreClass != null
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
        if (
            downloadControllerMethod != null &&
            downloadNormalProviderClass != null
        ) {
            runCatching {
                hook(downloadControllerMethod).intercept { chain ->
                    if (
                        fix.useNormalDownloadProviderForNestedPages(
                            controller = chain.thisObject,
                            detail = chain.getArg(0),
                            cid = chain.getArg(1) as Long,
                            normalCore = chain.getArg(2),
                            normalProviderClass = downloadNormalProviderClass,
                        )
                    ) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
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
                        hook(selectorMethod).intercept { chain ->
                            if (fix.shouldUseNormalPageSelector(chain.getArg(0))) {
                                false
                            } else {
                                chain.proceed()
                            }
                        }
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
                hook(selectorItemsMethod).intercept { chain ->
                    val result = chain.proceed()
                    fix.normalPageSelectorItems(
                        provider = chain.thisObject,
                        playerService = chain.getArg(0),
                        normalSourceClass = normalSourceClass,
                        selectorItemClass = selectorItemClass,
                    ) ?: result
                }
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
                hook(selectorIndexMethod).intercept { chain ->
                    val result = chain.proceed()
                    fix.normalPageSelectorIndex(
                        provider = chain.thisObject,
                        playerService = chain.getArg(0),
                        normalSourceClass = normalSourceClass,
                        selectorItemClass = selectorItemClass,
                    ) ?: result
                }
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
                hook(selectorSelectMethod).intercept { chain ->
                    if (
                        fix.selectNormalPageFromFullscreenProvider(
                            provider = chain.thisObject,
                            playerService = chain.getArg(0),
                            index = chain.getArg(1) as Int,
                            normalSourceClass = normalSourceClass,
                        )
                    ) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
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
                hook(fullscreenClickMethod).intercept { chain ->
                    fix.selectNormalPageFromFullscreenCallback(
                        callback = chain.thisObject,
                        index = chain.getArg(0) as Int,
                        normalSourceClass = normalSourceClass,
                    )
                    try {
                        chain.proceed()
                    } finally {
                        fix.clearFullscreenCallbackSelectionMarker()
                    }
                }
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
                hook(fullscreenBindMethod).intercept { chain ->
                    fix.prepareFullscreenSeasonSelector(chain.thisObject, normalSourceType)
                    try {
                        chain.proceed()
                    } finally {
                        fix.patchFullscreenSeasonSelector(
                            panel = chain.thisObject,
                            normalSourceClass = normalSourceClass,
                            selectorItemClass = selectorItemClass,
                            normalSourceType = normalSourceType,
                        )
                    }
                }
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
                hook(fullscreenHeaderMethod).intercept { chain ->
                    val result = chain.proceed()
                    fix.patchFullscreenSeasonSelector(
                        panel = chain.thisObject,
                        normalSourceClass = normalSourceClass,
                        selectorItemClass = selectorItemClass,
                        normalSourceType = normalSourceType,
                    )
                    result
                }
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

    private inner class ModernLogger : HookLogger {
        override val isDebugEnabled: Boolean = BuildConfig.DEBUG

        override fun info(message: String) {
            log(Log.INFO, Constants.TAG, message)
        }

        override fun debug(message: String) {
            if (isDebugEnabled) log(Log.DEBUG, Constants.TAG, message)
        }

        override fun error(message: String, error: Throwable?) {
            if (error == null) {
                log(Log.ERROR, Constants.TAG, message)
            } else {
                log(Log.ERROR, Constants.TAG, message, error)
            }
        }
    }
}
