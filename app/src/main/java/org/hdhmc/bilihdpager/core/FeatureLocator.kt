package org.hdhmc.bilihdpager.core

import android.os.Bundle
import dalvik.system.DexFile
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal class FeatureLocator(
    private val classLoader: ClassLoader,
    private val logger: HookLogger,
) {
    private val dexClassNames: List<String> by lazy { classLoader.dexClassNames() }

    fun findSourceWrapperClass(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
    ): Class<*>? = findFeatureClass("source wrapper") { clazz ->
        clazz.hasNoArgConstructor() &&
                clazz.hasVideoDetailInit(videoDetailClass) &&
                clazz.hasVideoListMethods(videoClass) &&
                clazz.hasSamePackageDelegateField(videoClass)
    }

    fun findNormalSourceClass(
        videoDetailClass: Class<*>,
        videoClass: Class<*>,
        sourceWrapperClass: Class<*>?,
    ): Class<*>? = findFeatureClass("normal source") { clazz ->
        clazz != sourceWrapperClass &&
                clazz.hasNoArgConstructor() &&
                clazz.hasVideoDetailInit(videoDetailClass) &&
                clazz.hasVideoListMethods(videoClass) &&
                clazz.hasListField() &&
                clazz.hasVideoField(videoClass) &&
                !clazz.hasSamePackageDelegateField(videoClass)
    }

    private fun findFeatureClass(
        description: String,
        predicate: (Class<*>) -> Boolean,
    ): Class<*>? {
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
