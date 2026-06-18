package org.hdhmc.bilihdpager.core

import android.os.Bundle
import dalvik.system.DexFile
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.MatchType
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.system.measureTimeMillis

internal class FeatureLocator(
    private val classLoader: ClassLoader,
    private val logger: HookLogger,
    private val hostApkPath: String? = null,
) {
    private val dexClassNames: List<String> by lazy { classLoader.dexClassNames() }
    private var videoSourceCandidates: List<String>? = null
    private var downloadControllerCandidates: List<String>? = null
    private val selectorDataProviderCandidates = mutableMapOf<String, List<String>>()

    fun findSourceWrapperClass(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
    ): Class<*>? =
        findFeatureClass(
            description = "source wrapper",
            dexKitCandidates = { findDexKitVideoSourceCandidates(videoDetailClass, videoClass) },
        ) { clazz ->
            clazz.hasNoArgConstructor() &&
                    clazz.hasVideoDetailInit(videoDetailClass) &&
                    clazz.hasVideoListMethods(videoClass) &&
                    clazz.hasSamePackageDelegateField(videoClass)
        }

    fun findNormalSourceClass(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
        sourceWrapperClass: Class<*>?,
    ): Class<*>? =
        findFeatureClass(
            description = "normal source",
            dexKitCandidates = { findDexKitVideoSourceCandidates(videoDetailClass, videoClass) },
        ) { clazz ->
            clazz != sourceWrapperClass &&
                    clazz.hasNoArgConstructor() &&
                    clazz.hasVideoDetailInit(videoDetailClass) &&
                    clazz.hasVideoListMethods(videoClass) &&
                    clazz.hasListField() &&
                    clazz.hasVideoField(videoClass) &&
                    !clazz.hasSamePackageDelegateField(videoClass)
        }

    fun debugLogVideoSourceCandidates(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
    ) {
        if (!logger.isDebugEnabled) return
        val candidates = findDexKitVideoSourceCandidates(videoDetailClass, videoClass)
        val likelyCandidates = candidates.filter(::isLikelyObfuscatedTopLevelClass)
        logger.debug {
            "DexKit video source candidates: total=${candidates.size} " +
                    "likely=${likelyCandidates.size} names=${likelyCandidates.take(20)}"
        }
    }

    fun findDownloadControllerClass(
        videoDetailClass: Class<*>,
        downloadNormalCoreClass: Class<*>,
        downloadSeasonCoreClass: Class<*>,
    ): Class<*>? =
        findFeatureClass(
            description = "download controller",
            dexKitCandidates = {
                findDexKitDownloadControllerCandidates(
                    videoDetailClass = videoDetailClass,
                    downloadNormalCoreClass = downloadNormalCoreClass,
                    downloadSeasonCoreClass = downloadSeasonCoreClass,
                )
            },
        ) { clazz ->
            clazz.findDeclaredMethodOrNull(
                "g",
                videoDetailClass,
                java.lang.Long.TYPE,
                downloadNormalCoreClass,
                downloadSeasonCoreClass,
            ) != null
        }

    fun debugLogDownloadControllerCandidates(
        videoDetailClass: Class<*>,
        downloadNormalCoreClass: Class<*>,
        downloadSeasonCoreClass: Class<*>,
    ) {
        if (!logger.isDebugEnabled) return
        val candidates = findDexKitDownloadControllerCandidates(
            videoDetailClass = videoDetailClass,
            downloadNormalCoreClass = downloadNormalCoreClass,
            downloadSeasonCoreClass = downloadSeasonCoreClass,
        )
        val likelyCandidates = candidates.filter(::isLikelyObfuscatedTopLevelClass)
        logger.debug {
            "DexKit download controller candidates: total=${candidates.size} " +
                    "likely=${likelyCandidates.size} names=${likelyCandidates.take(20)}"
        }
    }

    fun findSelectorDataProviderClass(
        playerServiceClasses: List<Class<*>>,
    ): Class<*>? =
        playerServiceClasses.firstNotNullOfOrNull { serviceClass ->
            findFeatureClass(
                description = "selector data provider (${serviceClass.name})",
                dexKitCandidates = { findDexKitSelectorDataProviderCandidates(serviceClass) },
            ) { clazz ->
                clazz.findDeclaredMethodOrNull("b", serviceClass) != null &&
                        clazz.findDeclaredMethodOrNull("c", serviceClass) != null &&
                        clazz.findDeclaredMethodOrNull("g", serviceClass, Integer.TYPE) != null &&
                        clazz.findDeclaredMethodOrNull("d") != null
            }
        }

    fun debugLogSelectorDataProviderCandidates(
        playerServiceClasses: List<Class<*>>,
    ) {
        if (!logger.isDebugEnabled) return
        playerServiceClasses.forEach { serviceClass ->
            val candidates = findDexKitSelectorDataProviderCandidates(serviceClass)
            val likelyCandidates = candidates.filter(::isLikelyObfuscatedTopLevelClass)
            logger.debug {
                "DexKit selector data provider candidates for ${serviceClass.name}: " +
                        "total=${candidates.size} likely=${likelyCandidates.size} " +
                        "names=${likelyCandidates.take(20)}"
            }
        }
    }

    private fun findFeatureClass(
        description: String,
        dexKitCandidates: (() -> List<String>)? = null,
        predicate: (Class<*>) -> Boolean,
    ): Class<*>? {
        if (dexKitCandidates != null) {
            val matchedByDexKit = findFeatureClassFromCandidates(description, dexKitCandidates, predicate)
            if (matchedByDexKit != null) return matchedByDexKit
        }

        if (dexClassNames.isEmpty()) {
            logger.debug { "Feature scan skipped for $description: no dex class names" }
            return null
        }

        var scanned = 0
        dexClassNames.asSequence()
            .filter(::isLikelyObfuscatedTopLevelClass)
            .forEach { className ->
                val clazz = classLoader.findClassOrNull(className) ?: return@forEach
                scanned += 1
                val matched = runCatching { predicate(clazz) }.getOrDefault(false)
                if (matched) {
                    logger.debug {
                        "Feature matched $description: ${clazz.name} " +
                                "(scanned=$scanned dexClasses=${dexClassNames.size})"
                    }
                    return clazz
                }
            }

        logger.debug {
            "Feature scan found no $description: scanned=$scanned dexClasses=${dexClassNames.size}"
        }
        return null
    }

    private fun findFeatureClassFromCandidates(
        description: String,
        candidatesProvider: () -> List<String>,
        predicate: (Class<*>) -> Boolean,
    ): Class<*>? {
        val candidates = runCatching(candidatesProvider).getOrElse { error ->
            logger.error("DexKit candidate scan failed for $description", error)
            return null
        }
        if (candidates.isEmpty()) {
            logger.debug { "DexKit found no candidates for $description" }
            return null
        }

        var loaded = 0
        candidates.asSequence()
            .distinct()
            .filter(::isLikelyObfuscatedTopLevelClass)
            .forEach { className ->
                val clazz = classLoader.findClassOrNull(className) ?: return@forEach
                loaded += 1
                val matched = runCatching { predicate(clazz) }.getOrDefault(false)
                if (matched) {
                    logger.debug {
                        "DexKit matched $description: ${clazz.name} " +
                                "(loaded=$loaded candidates=${candidates.size})"
                    }
                    return clazz
                }
            }

        logger.debug {
            "DexKit candidates did not validate for $description: " +
                    "loaded=$loaded candidates=${candidates.size}; falling back to reflection scan"
        }
        return null
    }

    private fun findDexKitVideoSourceCandidates(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
    ): List<String> {
        videoSourceCandidates?.let { return it }
        if (!DexKitLoader.ensureLoaded(logger)) return emptyList()

        var candidates = emptyList<String>()
        val elapsedMs = measureTimeMillis {
            createDexKitBridge().use { bridge ->
                val dexNum = runCatching { bridge.getDexNum() }.getOrDefault(-1)
                val knownClasses = (Constants.SOURCE_WRAPPER_CLASSES + Constants.NORMAL_SOURCE_CLASSES)
                    .filter { className -> runCatching { bridge.getClassData(className) != null }.getOrDefault(false) }
                logger.debug {
                    "DexKit bridge ready: source=${if (hostApkPath == null) "classLoader" else "apk"} " +
                            "dexNum=$dexNum knownVideoSources=$knownClasses"
                }
                candidates = bridge.findClass {
                    matcher {
                        methods {
                            matchType(MatchType.Contains)
                            add {
                                paramTypes(videoDetailClass.name, Bundle::class.java.name)
                            }
                        }
                    }
                }.map { it.name }
            }
        }
        logger.debug {
            "DexKit video source query completed: candidates=${candidates.size} elapsed=${elapsedMs}ms"
        }
        videoSourceCandidates = candidates
        return candidates
    }

    private fun findDexKitDownloadControllerCandidates(
        videoDetailClass: Class<*>,
        downloadNormalCoreClass: Class<*>,
        downloadSeasonCoreClass: Class<*>,
    ): List<String> {
        downloadControllerCandidates?.let { return it }
        if (!DexKitLoader.ensureLoaded(logger)) return emptyList()

        var candidates = emptyList<String>()
        val elapsedMs = measureTimeMillis {
            createDexKitBridge().use { bridge ->
                candidates = bridge.findClass {
                    matcher {
                        methods {
                            matchType(MatchType.Contains)
                            add {
                                paramTypes(
                                    videoDetailClass,
                                    java.lang.Long.TYPE,
                                    downloadNormalCoreClass,
                                    downloadSeasonCoreClass,
                                )
                            }
                        }
                    }
                }.map { it.name }
            }
        }
        logger.debug {
            "DexKit download controller query completed: candidates=${candidates.size} elapsed=${elapsedMs}ms"
        }
        downloadControllerCandidates = candidates
        return candidates
    }

    private fun findDexKitSelectorDataProviderCandidates(
        serviceClass: Class<*>,
    ): List<String> {
        selectorDataProviderCandidates[serviceClass.name]?.let { return it }
        if (!DexKitLoader.ensureLoaded(logger)) return emptyList()

        var candidates = emptyList<String>()
        val elapsedMs = measureTimeMillis {
            createDexKitBridge().use { bridge ->
                candidates = bridge.findClass {
                    matcher {
                        methods {
                            matchType(MatchType.Contains)
                            add {
                                paramTypes(serviceClass)
                            }
                            add {
                                paramTypes(serviceClass, Integer.TYPE)
                            }
                            add {
                                paramTypes()
                            }
                        }
                    }
                }.map { it.name }
            }
        }
        logger.debug {
            "DexKit selector data provider query completed for ${serviceClass.name}: " +
                    "candidates=${candidates.size} elapsed=${elapsedMs}ms"
        }
        selectorDataProviderCandidates[serviceClass.name] = candidates
        return candidates
    }

    private fun createDexKitBridge(): DexKitBridge =
        hostApkPath
            ?.takeIf { it.isNotBlank() }
            ?.let { DexKitBridge.create(it) }
            ?: DexKitBridge.create(classLoader, true)
}

private object DexKitLoader {
    private var loaded: Boolean? = null

    @Synchronized
    fun ensureLoaded(logger: HookLogger): Boolean {
        loaded?.let { return it }
        loaded = runCatching {
            System.loadLibrary("dexkit")
        }.onFailure { error ->
            logger.error("DexKit library load failed; falling back to reflection feature scan", error)
        }.isSuccess
        return loaded == true
    }
}

internal fun Class<*>.findVideoDetailInitMethodOrNull(
    videoDetailClass: Class<*>,
): java.lang.reflect.Method? =
    findFirstDeclaredMethodBySignatureOrNull(
        parameterTypes = listOf(videoDetailClass, Bundle::class.java),
        returnType = java.lang.Void.TYPE,
    )

private fun Class<*>.hasNoArgConstructor(): Boolean =
    runCatching { getDeclaredConstructor() }.isSuccess

private fun Class<*>.hasVideoDetailInit(videoDetailClass: Class<*>): Boolean =
    findVideoDetailInitMethodOrNull(videoDetailClass) != null

private fun Class<*>.hasVideoListMethods(videoClass: Class<*>): Boolean =
    findFirstDeclaredMethodBySignatureOrNull(emptyList(), Integer.TYPE) != null &&
            findFirstDeclaredMethodBySignatureOrNull(listOf(Integer.TYPE), videoClass) != null &&
            findFirstDeclaredMethodBySignatureOrNull(listOf(videoClass), Integer.TYPE) != null &&
            findFirstDeclaredMethodBySignatureOrNull(listOf(videoClass, Integer.TYPE)) != null

private fun Class<*>.hasListField(): Boolean =
    declaredInstanceFields().any { field -> java.util.List::class.java.isAssignableFrom(field.type) }

private fun Class<*>.hasVideoField(videoClass: Class<*>): Boolean =
    declaredInstanceFields().any { field -> field.type == videoClass }

private fun Class<*>.hasSamePackageDelegateField(videoClass: Class<*>): Boolean {
    val ownerPackage = packageNameOf(name)
    return declaredInstanceFields().any { field ->
        val type = field.type
        !type.isPrimitive &&
                type != this &&
                type != videoClass &&
                packageNameOf(type.name) == ownerPackage
    }
}

private fun Class<*>.declaredInstanceFields(): List<Field> =
    declaredFields.filterNot { field -> Modifier.isStatic(field.modifiers) }

private fun isLikelyObfuscatedTopLevelClass(name: String): Boolean {
    if ('$' in name) return false
    if (name.startsWith("android.") || name.startsWith("androidx.")) return false
    if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("kotlin.")) return false
    val packageName = packageNameOf(name)
    if (packageName.isEmpty()) return false
    val firstPackageSegment = packageName.substringBefore('.')
    val simpleName = name.substringAfterLast('.')
    return firstPackageSegment.length <= 5 && simpleName.length <= 4
}

private fun packageNameOf(name: String): String =
    name.substringBeforeLast('.', missingDelimiterValue = "")

private fun ClassLoader.dexClassNames(): List<String> {
    val names = LinkedHashSet<String>()
    var current: ClassLoader? = this
    while (current != null) {
        current.readDexClassNamesInto(names)
        current = current.parent
    }
    return names.toList()
}

@Suppress("DEPRECATION")
private fun ClassLoader.readDexClassNamesInto(output: MutableSet<String>) {
    val pathList = runCatching { findFieldInHierarchy(javaClass, "pathList").get(this) }.getOrNull()
        ?: return
    val elements = runCatching { findFieldInHierarchy(pathList.javaClass, "dexElements").get(pathList) }
        .getOrNull() as? Array<*>
        ?: return

    elements.forEach { element ->
        if (element == null) return@forEach
        val dexFile = runCatching { findFieldInHierarchy(element.javaClass, "dexFile").get(element) }
            .getOrNull() as? DexFile
            ?: return@forEach
        runCatching {
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                output += entries.nextElement()
            }
        }.onFailure { error ->
            // Some ClassLoader implementations expose non-enumerable DexFile instances.
            // The feature locator can still use known class mappings in that case.
            error.message
        }
    }
}

private fun findFieldInHierarchy(clazz: Class<*>, name: String): Field {
    var current: Class<*>? = clazz
    while (current != null) {
        runCatching {
            return current.getDeclaredField(name).apply { isAccessible = true }
        }
        current = current.superclass
    }
    throw NoSuchFieldException("${clazz.name}#$name")
}
