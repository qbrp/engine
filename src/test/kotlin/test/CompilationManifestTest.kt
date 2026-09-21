package org.lain.engine.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.script.InventoryTab
import org.lain.engine.script.Modules
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.compilation.BuildDraft
import org.lain.engine.script.compilation.CompilationDiagnostic
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationManifest
import org.lain.engine.script.compilation.CompilationManifestPhase
import org.lain.engine.script.compilation.CompilationOutcome
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.CompilationReport
import org.lain.engine.script.compilation.NamespaceDraft
import org.lain.engine.script.compilation.SystemPhaseDraft
import org.lain.engine.script.compilation.compilationManifestOf
import org.lain.engine.script.compilation.writeTo
import java.nio.file.Path

class CompilationManifestTest {
    @field:TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun writesDeterministicFailureManifest() {
        val draft = BuildDraft(
            namespaces = linkedMapOf(
                NamespaceId("zeta") to emptyNamespaceDraft(),
                NamespaceId("alpha") to emptyNamespaceDraft(),
            ),
            callbacks = emptyMap(),
            rootPhase = SystemPhaseDraft("root", emptyList()),
            inventoryTab = InventoryTab(emptyList())
        )
        val outcome = CompilationOutcome.Failure(
            CompilationReport(
                listOf(
                    CompilationDiagnostic(
                        severity = CompilationDiagnosticSeverity.ERROR,
                        message = "Missing symbol",
                        phase = CompilationPhase.LINKING,
                        namespace = NamespaceId("alpha"),
                    )
                )
            )
        )

        val manifest = compilationManifestOf(
            outcome = outcome,
            buildDraft = draft,
            modules = Modules(emptyMap()),
        )

        assertFalse(manifest.success)
        assertEquals(listOf("alpha", "zeta"), manifest.namespaces.map { it.id })
        assertEquals("linking", manifest.diagnostics.single().phase)

        val file = temporaryDirectory.resolve("compilation-manifest.json").toFile()
        manifest.writeTo(file)

        assertEquals(
            manifest,
            Json.decodeFromString<CompilationManifest>(file.readText()),
        )
        assertEquals(
            emptyList<String>(),
            temporaryDirectory.toFile().listFiles()
                .orEmpty()
                .filter { it.extension == "tmp" }
                .map { it.name },
        )
    }

    private fun emptyNamespaceDraft() = NamespaceDraft(
        items = emptyMap(),
        sounds = emptyMap(),
        progressionAnimations = emptyMap(),
    )
}
