package org.lain.engine.script

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.lain.engine.util.file.FileSystem
import org.lain.engine.util.file.readFile
import java.io.File
import java.nio.file.Files

private const val MODULE_FILE_NAME = "module.yaml"

@Serializable
data class ModuleSet(
    val enabled: Set<NamespaceId>
)

@Serializable
data class Module(
    val namespace: NamespaceId,
    val namespaces: List<NamespaceId> = listOf(),
    val depends: List<ModuleDependency> = listOf(),
    val imports: Map<String, NamespaceId> = mapOf()
) {
    val allNamespaces
        get() = (namespaces + namespace).distinct()
}

data class ModuleLocation(val config: File) {
    val directory: File = config.parentFile
}

@Serializable
@JvmInline
value class ModuleDependency(
    val module: NamespaceId
)

data class Modules(
    val files: Map<ModuleLocation, Module>
)

class ModuleNamespaceOverlapException(
    overlaps: List<Overlap>
) : Exception(
    overlaps.joinToString(separator = "\n") { it.message }
) {
    data class Overlap(
        val namespace: NamespaceId,
        val modules: List<ModuleLocation>
    ) {
        val message: String
            get() = "Пространство имён $namespace принадлежит нескольким модулям: ${
                modules.joinToString { it.config.path }
            }"
    }
}

class ModuleManager {
    lateinit var modules: Modules
        private set

    init {
        composeModules()
    }

    fun composeModules(): Modules {
        return findModuleFiles()
            .withSet(
                Yaml.default
                    .readFile<ModuleSet>(FileSystem.moduleSet)
                    .enabled
            )
            .validated()
            .also { modules = it }
    }

    fun composeModules(scriptEngine: ScriptEngine): Modules {
        return composeModules()
            .also { scriptEngine.updateModules(it) }
    }
}

private fun findModuleFiles(): Modules {
    return Files.find(
        FileSystem.modules.toPath(),
        Int.MAX_VALUE,
        { path, attributes ->
            attributes.isRegularFile &&
                    path.fileName.toString() == MODULE_FILE_NAME
        }
    ).use { paths ->
        Modules(
            paths
                .toList()
                .map { ModuleLocation(it.toFile()) }
                .associate { it to Yaml.default.readFile<Module>(it.config) }
        )
    }
}

private fun Modules.withSet(
    enabled: Set<NamespaceId>
): Modules {
    return Modules(
        files.filterValues { module ->
            module.namespace in enabled
        }
    )
}

private fun Modules.validated(): Modules {
    val ownersByNamespace = mutableMapOf<NamespaceId, MutableList<ModuleLocation>>()

    files.forEach { (file, module) ->
        val ownedNamespaces = module.namespaces + module.namespace
        ownedNamespaces.forEach { namespace ->
            ownersByNamespace
                .computeIfAbsent(namespace) { mutableListOf() }
                .add(file)
        }
    }

    val overlaps = ownersByNamespace
        .filterValues { modules -> modules.size > 1 }
        .map { (namespace, modules) ->
            ModuleNamespaceOverlapException.Overlap(
                namespace = namespace,
                modules = modules
            )
        }

    if (overlaps.isNotEmpty()) {
        throw ModuleNamespaceOverlapException(overlaps)
    }

    return this
}
