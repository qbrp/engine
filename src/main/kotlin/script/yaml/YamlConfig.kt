package org.lain.engine.script.yaml

import com.charleskorn.kaml.InvalidPropertyValueException
import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.io.files.FileNotFoundException
import org.lain.engine.script.compilation.CompilationDiagnostic
import org.lain.engine.script.compilation.CompilationDiagnosticLocation
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.CompilationReportBuilder
import org.lain.engine.util.file.readFile
import java.io.File

fun ParsingErrorCompilationDiagnostics(
    location: CompilationDiagnosticLocation,
    message: String,
    cause: Exception
) =
    CompilationDiagnostic(
        severity = CompilationDiagnosticSeverity.ERROR,
        message = message,
        phase = CompilationPhase.COMPILATION,
        location = location,
        cause = cause
    )

internal inline fun <reified T> Yaml.readFileDiagnostics(
    exceptions: CompilationReportBuilder,
    file: File
): T? {
    return try {
        readFile(file)
    } catch (e: FileNotFoundException) {
        exceptions.report(
            ParsingErrorCompilationDiagnostics(
                message = "Файл $file не найден",
                location = CompilationDiagnosticLocation(
                    source = file.path,
                    path = file.path
                ),
                cause = e
            )
        )
        null
    }
}

private fun InvalidPropertyValueException.findReason(): String {
    val cause = cause

    return if (cause != null) {
        when (cause) {
            is InvalidPropertyValueException -> cause.findReason()
            is UnknownPropertyException -> {
                val available = cause.validPropertyNames.joinToString() { "<gold>$it</gold>" }
                "Поле <gold>${cause.propertyName}</gold> не существует. Доступны: $available"
            }
            else -> reason
        }
    } else {
        reason
    }
}

internal inline fun <reified T> Yaml.readAsConfig(
    exceptions: CompilationReportBuilder,
    file: File
): T? {
    return try {
        readFileDiagnostics(exceptions, file)
    } catch (e: YamlException) {
        exceptions.report(
            ParsingErrorCompilationDiagnostics(
                message = when (e) {
                    is InvalidPropertyValueException -> e.findReason()
                    else -> e.message
                },
                location = CompilationDiagnosticLocation(
                    source = file.path,
                    line = e.line,
                    column = e.column,
                    path = e.path.toHumanReadableString()
                ),
                cause = e
            )
        )
        null
    }
}
