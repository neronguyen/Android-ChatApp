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
}
