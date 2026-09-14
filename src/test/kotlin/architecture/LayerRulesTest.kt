package net.enthusia.itemsignature.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path

/** Adapted from SPEAR's MIT-licensed LayerRulesTest.kt; adds non-vacuity and framework checks. */
class LayerRulesTest {
    private val root = Path.of("src/main/kotlin/net/enthusia/itemsignature")
    private fun sources(): List<Path> = Files.walk(root).use { paths ->
        paths.filter { it.toString().endsWith(".kt") }.toList()
    }

    @Test fun `all production sources belong to a nonempty SPEAR layer`() {
        val files = sources()
        val layers = setOf("domain", "application", "infrastructure")
        assertTrue(files.isNotEmpty())
        val unclassified = files.filter { root.relativize(it).getName(0).toString() !in layers }
        assertEquals(emptyList<Path>(), unclassified, "Production sources must be assigned to SPEAR layers")
        layers.forEach { layer ->
            assertTrue(files.any { root.relativize(it).getName(0).toString() == layer }, "Missing layer: $layer")
        }
    }

    @Test fun `spear layer dependencies are correct`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("Domain", "net.enthusia.itemsignature.domain..")
            val application = Layer("Application", "net.enthusia.itemsignature.application..")
            val infrastructure = Layer("Infrastructure", "net.enthusia.itemsignature.infrastructure..")
            domain.dependsOnNothing()
            application.dependsOn(domain)
            infrastructure.dependsOn(domain, application)
        }
    }

    @Test fun `inner layers contain no framework imports or annotations`() {
        val violations = mutableListOf<String>()
        sources().forEach { file ->
            val layer = root.relativize(file).getName(0).toString()
            if (layer !in setOf("domain", "application")) return@forEach
            Files.readAllLines(file).forEachIndexed { index, line ->
                if (line.startsWith("import ")) {
                    val symbol = line.removePrefix("import ").trim()
                    val allowed = symbol.startsWith("kotlin.") ||
                        symbol.startsWith("net.enthusia.itemsignature.domain.")
                    if (!allowed) violations += "$file:${index + 1}:$symbol"
                }
                if (Regex("(org\\.bukkit|io\\.papermc|net\\.kyori|com\\.nexomc|org\\.springframework|jakarta\\.persistence|javax\\.persistence|com\\.fasterxml\\.jackson|io\\.micronaut|lombok)\\.").containsMatchIn(line)) {
                    violations += "$file:${index + 1}: forbidden framework reference"
                }
            }
        }
        assertEquals(emptyList<String>(), violations)
    }
}
