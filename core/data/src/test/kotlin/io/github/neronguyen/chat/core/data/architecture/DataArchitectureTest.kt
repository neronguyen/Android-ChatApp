package io.github.neronguyen.chat.core.data.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class DataArchitectureTest {

    @Test
    fun `zero androidx work imports in core data`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/core/data/src/main/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("androidx.work")
                }
            }
    }

    @Test
    fun `repository package never imports sync or realtime`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/core/data/src/main/kotlin/") && it.path.contains("/repository/") }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("io.github.neronguyen.chat.core.data.sync") ||
                        import.name.startsWith("io.github.neronguyen.chat.core.data.realtime")
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
    fun `data layer repository implementations have internal visibility`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withPackage("io.github.neronguyen.chat.core.data.repository..")
            .filter { it.name.endsWith("Impl") }
            .assertTrue { clazz ->
                clazz.hasInternalModifier
            }
    }
}
