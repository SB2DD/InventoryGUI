package net.projecttl.inventory.util

import org.bukkit.Bukkit
import org.bukkit.plugin.InvalidPluginException
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.PluginClassLoader

// from https://github.com/monun/heartbeat-coroutines/blob/master/heartbeat-coroutines/src/main/kotlin/io/github/monun/heartbeat/coroutines/Downstream.kt
@Suppress("UnstableApiUsage")
internal object Downstream {
    private val classLoaderFields
        get() = PluginClassLoader::class.java.declaredFields.filter {
            ClassLoader::class.java.isAssignableFrom(it.type)
        }.onEach { field ->
            field.isAccessible = true
        }

    private val PluginClassLoader.internalLoaders: List<ClassLoader>
        get() = classLoaderFields.map { it.get(this) }.filterIsInstance<ClassLoader>()

    fun pullPlugin(): Plugin {
        val classLoader = Downstream::class.java.classLoader

        return Bukkit.getPluginManager().plugins.find { plugin ->
            val pluginClassLoader = plugin.javaClass.classLoader as PluginClassLoader

            pluginClassLoader === classLoader || pluginClassLoader.internalLoaders.any { it === classLoader }
        } ?: throw InvalidPluginException("The library must be loaded from PluginClassLoader")
    }
}