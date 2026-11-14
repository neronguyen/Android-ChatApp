package io.github.neronguyen.chat.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Assert.assertTrue
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
    fun `domain model classes do not import serialization or infrastructure`() {
        val forbiddenPrefixes = listOf(
            "kotlinx.serialization",
            "io.github.neronguyenvn.nerochat.contract",
            "retrofit2.",
            "okhttp3.",
            "androidx.datastore.",
            "com.google.gson.",
            "com.squareup.moshi.",
            "com.fasterxml.jackson.",
        )

        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/core/model/src/main/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    forbiddenPrefixes.any { prefix -> import.name.startsWith(prefix) }
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

        featureScreenFiles()
            .assertTrue { file ->
                val previewFunctions =
                    file.functions().filter { it.hasAnnotationWithName("Preview") }
                previewFunctions.isNotEmpty() &&
                        previewFunctions.all { function ->
                            chatThemeInvocationRegex.containsMatchIn(
                                stripCommentsAndStrings(function.text),
                            )
                        }
            }
    }

    @Test
    fun `every feature Screen file exposes testTag semantics`() {
        val testTagInvocationRegex = Regex("""\btestTag\s*\(""")

        featureScreenFiles()
            .assertTrue { file ->
                val importsTestTag = file.imports.any {
                    it.name == "androidx.compose.ui.platform.testTag"
                }
                val usesTestTag = testTagInvocationRegex.containsMatchIn(
                    stripCommentsAndStrings(file.text),
                )
                importsTestTag && usesTestTag
            }
    }

    @Test
    fun `every feature Screen file has a matching Robolectric ScreenTest with semantics assertions`() {
        val unitTestFiles = Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") && it.path.contains("/src/test/") }
        assertTrue("Expected feature unit test files in Konsist scope", unitTestFiles.isNotEmpty())

        val composeRuleRegex = Regex("""\bcreate(?:Android)?ComposeRule\s*\(""")
        val semanticsQueryRegex = Regex("""\bon(?:All)?Nodes?With(?:Tag|Text)\s*\(""")

        featureScreenFiles()
            .assertTrue { screenFile ->
                val expectedClassName = "${screenFile.name}Test"
                val testFile = findMatchingFeatureFile(
                    screenFile = screenFile,
                    candidates = unitTestFiles,
                    expectedName = expectedClassName,
                ) ?: return@assertTrue false

                val testClass = testFile.classes().firstOrNull { it.name == expectedClassName }
                    ?: return@assertTrue false
                val hasRobolectricRunner = testClass.annotations.any { annotation ->
                    annotation.name == "RunWith" &&
                            annotation.text.contains("RobolectricTestRunner")
                }
                val hasTestMethod = testClass.functions().any { it.hasAnnotationWithName("Test") }
                val strippedText = stripCommentsAndStrings(testFile.text)
                val usesComposeRule = composeRuleRegex.containsMatchIn(strippedText)
                val assertsSemantics = semanticsQueryRegex.containsMatchIn(strippedText)

                hasRobolectricRunner && hasTestMethod && usesComposeRule && assertsSemantics
            }
    }

    @Test
    fun `every feature Screen file has a matching ScreenshotTest`() {
        val screenshotFiles = Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") && it.path.contains("/src/screenshotTest/") }
        assertTrue(
            "Expected feature screenshotTest files in Konsist scope",
            screenshotFiles.isNotEmpty(),
        )

        featureScreenFiles()
            .assertTrue { screenFile ->
                val expectedClassName = "${screenFile.name}ScreenshotTest"
                val screenshotFile = findMatchingFeatureFile(
                    screenFile = screenFile,
                    candidates = screenshotFiles,
                    expectedName = expectedClassName,
                ) ?: return@assertTrue false

                val screenshotClass = screenshotFile.classes()
                    .firstOrNull { it.name == expectedClassName }
                    ?: return@assertTrue false
                screenshotClass.functions().any { function ->
                    function.hasAnnotationWithName("PreviewTest") &&
                            function.hasAnnotationWithName("Preview")
                }
            }
    }

    private fun featureScreenFiles(): List<KoFileDeclaration> {
        val screens = Konsist
            .scopeFromProject()
            .files
            .filter {
                it.path.contains("/feature/") &&
                        it.path.contains("/src/main/") &&
                        it.path.endsWith("Screen.kt")
            }
        assertTrue("Expected feature Screen.kt files in Konsist scope", screens.isNotEmpty())
        return screens
    }

    private fun findMatchingFeatureFile(
        screenFile: KoFileDeclaration,
        candidates: List<KoFileDeclaration>,
        expectedName: String,
    ): KoFileDeclaration? {
        val screenModule = featureModuleSegment(screenFile.path)
        val screenPackage = screenFile.packagee?.name
        return candidates.firstOrNull { candidate ->
            candidate.name == expectedName &&
                    featureModuleSegment(candidate.path) == screenModule &&
                    candidate.packagee?.name == screenPackage
        }
    }

    private fun featureModuleSegment(path: String): String? =
        Regex("""(/feature/[^/]+/)""").find(path)?.groupValues?.get(1)

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
