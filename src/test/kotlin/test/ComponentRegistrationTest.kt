package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.bootstrap
import org.lain.engine.data.asRawEngineId
import org.lain.engine.data.migrate
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

class ComponentRegistrationTest : EngineTest() {
    @Test
    fun commonBootstrapRegistersEveryStaticComponent() {
        val expected = nativeComponents().filter { !it.startsWith(CLIENT_PACKAGE) }.toSet()
        val registered = ComponentTypeRegistry.listEntries().mapTo(mutableSetOf()) { it.key.replace('.', '/') }
        assertEquals(emptySet<String>(), expected - registered, "Components missing from common bootstrap")
    }

    @Test
    fun clientBootstrapExplicitlyRegistersEveryClientComponent() {
        val expected = nativeComponents().filter { it.startsWith(CLIENT_PACKAGE) }.toSet()
        val declared = mutableSetOf<String>()
        val reader = requireNotNull(classes[CLIENT_BOOTSTRAP])
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name != "registerComponentsClient" && name != "registerSceneComponents") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitLdcInsn(value: Any?) {
                        if (value is Type && value.sort == Type.OBJECT) declared += value.internalName
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertEquals(expected, declared.intersect(nativeComponents()))
    }

    @Test
    fun canonicalIdsAreUniqueAndOnlyPersistentComponentsHaveLegacyMigration() {
        val native = nativeComponents()
        val entries = ComponentTypeRegistry.listEntries().filter { it.key.replace('.', '/') in native }
        val scriptIds = CoreScriptComponents.getAll().mapTo(mutableSetOf()) { it.id }
        val ids = entries.map { it.value.type.id }
        assertEquals(ids.size, ids.toSet().size)
        entries.forEach { (className, entry) ->
            assertTrue(entry.type.id.startsWith("core/"), className)
            assertEquals(entry.type.id, EngineId(entry.type.id).full)
            assertFalse(entry.type.id in scriptIds, "Kotlin and Lua share id ${entry.type.id}")
            val legacy = className.substringAfterLast('.').lowercase().asRawEngineId()
            if (entry.meta.savable) {
                assertEquals(entry.type.id, legacy.migrate().id, className)
            } else {
                assertEquals(legacy, legacy.migrate(), className)
            }
            assertEquals(entry.type.id, entry.type.id.asRawEngineId().migrate().id)
        }
        val before = ComponentTypeRegistry.count
        bootstrap()
        assertEquals(before, ComponentTypeRegistry.count)
    }

    private fun nativeComponents(): Set<String> = classes.values.asSequence()
        .filter { it.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT or Opcodes.ACC_SYNTHETIC) == 0 }
        .filter { inherits(it.className, COMPONENT) && !inherits(it.className, SCRIPT_COMPONENT) }
        .map { it.className }
        .toSet()

    private fun inherits(name: String, ancestor: String): Boolean {
        if (name == ancestor) return true
        val reader = classes[name] ?: return false
        return reader.interfaces.any { inherits(it, ancestor) } ||
            reader.superName?.let { inherits(it, ancestor) } == true
    }

    companion object {
        private const val CLIENT_PACKAGE = "org/lain/engine/client/"
        private const val CLIENT_BOOTSTRAP = "org/lain/engine/client/util/ComponentKt"
        private const val COMPONENT = "org/lain/cyberia/ecs/Component"
        private const val SCRIPT_COMPONENT = "org/lain/engine/script/ScriptComponent"

        // Inspect class headers without initializing optional Minecraft adapters.
        private val classes by lazy {
            val roots = listOf("org.lain.engine.BootstrapKt", CLIENT_BOOTSTRAP.replace('/', '.'))
                .map { Class.forName(it, false, ComponentRegistrationTest::class.java.classLoader) }
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
                .toSet()
            buildMap<String, ClassReader> {
                roots.forEach { root ->
                    if (Files.isDirectory(root)) {
                        Files.walk(root).use { paths ->
                            paths.filter { it.toString().endsWith(".class") }.forEach { path ->
                                val reader = ClassReader(Files.readAllBytes(path))
                                put(reader.className, reader)
                            }
                        }
                    } else {
                        JarFile(root.toFile()).use { jar ->
                            jar.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                                jar.getInputStream(entry).use { input ->
                                    val reader = ClassReader(input)
                                    put(reader.className, reader)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
