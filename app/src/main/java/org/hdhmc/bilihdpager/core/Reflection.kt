package org.hdhmc.bilihdpager.core

import java.lang.reflect.Field
import java.lang.reflect.Method

internal fun ClassLoader.findClassOrNull(name: String): Class<*>? =
    runCatching { Class.forName(name, false, this) }.getOrNull()

internal fun ClassLoader.findFirstClassOrNull(names: List<String>): Class<*>? =
    names.firstNotNullOfOrNull { findClassOrNull(it) }

internal fun Class<*>.findDeclaredMethodOrNull(
    name: String,
    vararg parameterTypes: Class<*>,
): Method? = runCatching {
    findMethodInHierarchy(name, *parameterTypes).apply { isAccessible = true }
}.getOrNull()

internal fun Class<*>.findFirstDeclaredMethodOrNull(
    names: List<String>,
    vararg parameterTypes: Class<*>,
): Method? = names.firstNotNullOfOrNull { findDeclaredMethodOrNull(it, *parameterTypes) }

internal fun Class<*>.findFirstDeclaredMethodBySignatureOrNull(
    parameterTypes: List<Class<*>>,
    returnType: Class<*>? = null,
): Method? = runCatching {
    findMethodBySignatureInHierarchy(this, parameterTypes, returnType).apply { isAccessible = true }
}.getOrNull()

internal fun Any.getObjectFieldOrNull(name: String): Any? =
    runCatching { findFieldInHierarchy(javaClass, name).get(this) }.getOrNull()

internal fun Any.getIntFieldOrNull(name: String): Int? =
    runCatching { findFieldInHierarchy(javaClass, name).getInt(this) }.getOrNull()

internal fun Any.getLongFieldOrNull(name: String): Long? =
    runCatching { findFieldInHierarchy(javaClass, name).getLong(this) }.getOrNull()

internal fun Any.getBooleanFieldOrNull(name: String): Boolean? =
    runCatching { findFieldInHierarchy(javaClass, name).getBoolean(this) }.getOrNull()

internal fun Any.setObjectField(name: String, value: Any?) {
    findFieldInHierarchy(javaClass, name).set(this, value)
}

internal fun Class<*>.getStaticObjectFieldOrNull(name: String): Any? =
    runCatching { findFieldInHierarchy(this, name).get(null) }.getOrNull()

internal fun Any.setIntField(name: String, value: Int) {
    findFieldInHierarchy(javaClass, name).setInt(this, value)
}

internal fun Any.setLongField(name: String, value: Long) {
    findFieldInHierarchy(javaClass, name).setLong(this, value)
}

internal fun Any.setBooleanField(name: String, value: Boolean) {
    findFieldInHierarchy(javaClass, name).setBoolean(this, value)
}

internal fun Class<*>.newNoArgInstanceOrNull(): Any? =
    runCatching { getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()

internal fun Class<*>.newInstanceOrNull(vararg args: Any?): Any? =
    runCatching {
        val constructor = declaredConstructors.firstOrNull { constructor ->
            constructor.parameterTypes.size == args.size &&
                    constructor.parameterTypes.zip(args).all { (type, arg) -> type.accepts(arg) }
        } ?: throw NoSuchMethodException("${name}#<init>/${args.size}")
        constructor.isAccessible = true
        constructor.newInstance(*args)
    }.getOrNull()

internal fun Any.callMethodOrNull(name: String, vararg args: Any?): Any? =
    runCatching { callMethod(name, *args) }.getOrNull()

@Suppress("UNCHECKED_CAST")
internal fun <T> Any.callMethodOrNullAs(name: String, vararg args: Any?): T? =
    callMethodOrNull(name, *args) as? T

internal fun Any.callMethodIfExists(name: String, vararg args: Any?): Boolean =
    runCatching {
        callMethod(name, *args)
        true
    }.getOrDefault(false)

internal fun Any.callFirstMethodBySignatureIfExists(
    parameterTypes: List<Class<*>>,
    vararg args: Any?,
): Boolean = runCatching {
    val method = findMethodBySignatureInHierarchy(javaClass, parameterTypes, null)
    method.invoke(this, *args)
    true
}.getOrDefault(false)

private fun Any.callMethod(name: String, vararg args: Any?): Any? {
    val method = findCompatibleMethod(javaClass, name, args)
        ?: throw NoSuchMethodException("${javaClass.name}#$name/${args.size}")
    return method.invoke(this, *args)
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

private fun Class<*>.findMethodInHierarchy(
    name: String,
    vararg parameterTypes: Class<*>,
): Method {
    var current: Class<*>? = this
    while (current != null) {
        runCatching {
            return current.getDeclaredMethod(name, *parameterTypes)
        }
        current = current.superclass
    }
    throw NoSuchMethodException("$name(${parameterTypes.joinToString { it.name }})")
}

private fun findMethodBySignatureInHierarchy(
    clazz: Class<*>,
    parameterTypes: List<Class<*>>,
    returnType: Class<*>?,
): Method {
    var current: Class<*>? = clazz
    while (current != null) {
        current.declaredMethods.firstOrNull { method ->
            method.parameterTypes.toList() == parameterTypes &&
                    (returnType == null || method.returnType == returnType)
        }?.let { method ->
            method.isAccessible = true
            return method
        }
        current = current.superclass
    }
    throw NoSuchMethodException(
        "${clazz.name}#(${parameterTypes.joinToString { it.name }})" +
                (returnType?.let { ":${it.name}" } ?: "")
    )
}

private fun findCompatibleMethod(
    clazz: Class<*>,
    name: String,
    args: Array<out Any?>,
): Method? {
    var current: Class<*>? = clazz
    while (current != null) {
        current.declaredMethods.firstOrNull { method ->
            method.name == name &&
                    method.parameterTypes.size == args.size &&
                    method.parameterTypes.zip(args).all { (type, arg) -> type.accepts(arg) }
        }?.let { method ->
            method.isAccessible = true
            return method
        }
        current = current.superclass
    }
    return null
}

private fun Class<*>.accepts(arg: Any?): Boolean {
    if (arg == null) return !isPrimitive
    return boxed().isAssignableFrom(arg.javaClass)
}

private fun Class<*>.boxed(): Class<*> = when (this) {
    java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
    java.lang.Byte.TYPE -> java.lang.Byte::class.java
    java.lang.Character.TYPE -> java.lang.Character::class.java
    java.lang.Double.TYPE -> java.lang.Double::class.java
    java.lang.Float.TYPE -> java.lang.Float::class.java
    java.lang.Integer.TYPE -> java.lang.Integer::class.java
    java.lang.Long.TYPE -> java.lang.Long::class.java
    java.lang.Short.TYPE -> java.lang.Short::class.java
    java.lang.Void.TYPE -> java.lang.Void::class.java
    else -> this
}
