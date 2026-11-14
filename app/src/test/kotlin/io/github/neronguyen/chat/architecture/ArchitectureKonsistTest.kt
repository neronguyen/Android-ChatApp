package io.github.neronguyen.chat.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureKonsistTest {

    @Test
    fun `presentation modules do not import infrastructure modules`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") || it.path.contains("/core/ui/") }
            .filter { it.path.contains("/src/main/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("io.github.neronguyen.chat.core.network") ||
                        import.name.startsWith("io.github.neronguyen.chat.core.database") ||
                        import.name.startsWith("io.github.neronguyen.chat.core.datastore")
                }
            }
    }

    @Test
    fun `presentation modules do not import wire contract DTOs`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") || it.path.contains("/core/ui/") }
            .filter { it.path.contains("/src/main/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.matches(Regex("io\\.github\\.neronguyenvn\\.nerochat\\.contract\\.(auth|chat|common)\\..*"))
                }
            }
    }

    @Test
    fun `all data layer mapper functions have internal visibility`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/core/data/src/main/kotlin/") && it.path.contains("/mapper/") }
            .flatMap { it.functions() }
            .assertTrue { function ->
                function.hasInternalModifier
            }
    }

    @Test
    fun `data layer implementations have internal visibility`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withPackage("io.github.neronguyen.chat.core.data..")
            .filter { it.name.endsWith("Impl") }
            .assertTrue { clazz ->
                clazz.hasInternalModifier
            }
    }

    @Test
    fun `domain model classes do not import Android framework or Room`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/core/model/src/main/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("android.") ||
                        import.name.startsWith("androidx.room")
                }
            }
    }

    @Test
    fun `presentation and app modules do not hardcode Color literals outside core ui theme`() {
        val colorConstructorRegex = Regex("""\bColor\s*\(""")
        val colorConstantRegex = Regex("""\bColor\.([A-Z]\w*)""")
        val allowedConstants = setOf("Transparent", "Unspecified")

        Konsist
            .scopeFromProject()
            .files
            .filter {
                (it.path.contains("/feature/") || it.path.contains("/core/ui/") || it.path.contains(
                    "/app/"
                )) &&
                        it.path.contains("/src/main/") &&
                        !it.path.contains("/core/ui/src/main/kotlin/io/github/neronguyen/chat/core/ui/theme/")
            }
            .assertTrue { file ->
                val stripped = stripCommentsAndStrings(file.text)
                val hasColorConstructor = colorConstructorRegex.containsMatchIn(stripped)
                val hasDisallowedConstant = colorConstantRegex.findAll(stripped).any { match ->
                    match.groupValues[1] !in allowedConstants
                }
                !hasColorConstructor && !hasDisallowedConstant
            }
    }

    @Test
    fun `feature modules consume theme colors via MaterialTheme instead of raw Chat color tokens`() {
        val materialThemeWrapperRegex = Regex("""\bMaterialTheme\s*[({]""")

        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") && it.path.contains("/src/main/") }
            .assertTrue { file ->
                val hasPreview = file.functions().any { it.hasAnnotationWithName("Preview") }
                val hasWildcardThemeImport = file.imports.any { import ->
                    import.name == "io.github.neronguyen.chat.core.ui.theme.*" ||
                            (import.isWildcard && import.name.startsWith("io.github.neronguyen.chat.core.ui.theme"))
                }
                val hasRawChatTokenImport = file.imports.any { import ->
                    import.name.startsWith("io.github.neronguyen.chat.core.ui.theme.Chat") &&
                            !(hasPreview && import.name == "io.github.neronguyen.chat.core.ui.theme.ChatTheme")
                }
                val hasMaterialThemeWrapper = materialThemeWrapperRegex.containsMatchIn(
                    stripCommentsAndStrings(file.text),
                )
                !hasWildcardThemeImport && !hasRawChatTokenImport && !hasMaterialThemeWrapper
            }
    }

    @Test
    fun `every feature Screen file declares at least one Preview function wrapped in ChatTheme`() {
        val chatThemeInvocationRegex = Regex("""\bChatTheme\s*[({]""")

        Konsist
            .scopeFromProject()
            .files
            .filter {
                it.path.contains("/feature/") &&
                        it.path.contains("/src/main/") &&
                        it.path.endsWith("Screen.kt")
            }
            .assertTrue { file ->
                val previewFunctions =
                    file.functions().filter { it.hasAnnotationWithName("Preview") }
                previewFunctions.isNotEmpty() &&
                        previewFunctions.all { function ->
                            chatThemeInvocationRegex.containsMatchIn(
                                stripCommentsAndStrings(
                                    function.text
                                )
                            )
                        }
            }
    }

    private fun stripCommentsAndStrings(source: String): String {
        val result = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            when {
                // Line comment
                i + 1 < n && source[i] == '/' && source[i + 1] == '/' -> {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                }
                // Block comment (supports Kotlin nested block comments)
                i + 1 < n && source[i] == '/' && source[i + 1] == '*' -> {
                    var depth = 1
                    i += 2
                    while (i < n && depth > 0) {
                        if (i + 1 < n && source[i] == '/' && source[i + 1] == '*') {
                            depth++
                            i += 2
                        } else if (i + 1 < n && source[i] == '*' && source[i + 1] == '/') {
                            depth--
                            i += 2
                        } else {
                            if (source[i] == '\n') result.append('\n')
                            i++
                        }
                    }
                }
                // Triple-quoted raw string
                i + 2 < n && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"' -> {
                    result.append("\"\"")
                    i += 3
                    while (i < n) {
                        if (i + 2 < n && source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                            i += 3
                            break
                        }
                        if (source[i] == '\n') result.append('\n')
                        i++
                    }
                }
                // Regular string literal
                source[i] == '"' -> {
                    result.append("\"\"")
                    i++
                    while (i < n) {
                        if (source[i] == '\\' && i + 1 < n) {
                            i += 2
                        } else if (source[i] == '"') {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                }
                // Char literal
                source[i] == '\'' -> {
                    result.append("''")
                    i++
                    while (i < n) {
                        if (source[i] == '\\' && i + 1 < n) {
                            i += 2
                        } else if (source[i] == '\'') {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                }

                else -> {
                    result.append(source[i])
                    i++
                }
            }
        }
        return result.toString()
    }
}
