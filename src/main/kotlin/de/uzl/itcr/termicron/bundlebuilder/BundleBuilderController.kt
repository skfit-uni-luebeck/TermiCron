package de.uzl.itcr.termicron.bundlebuilder

import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import de.uzl.itcr.termicron.logger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import java.lang.IllegalArgumentException
import java.net.URI
import java.util.*
import kotlin.random.Random

@Controller
@RequestMapping("/")
class BundleBuilderController(
    @Autowired val bundleBuilderDefaults: BundleBuilderDefaults
) {

    @GetMapping("/")
    fun redirectRoot() = "redirect:/bundlebuilder"

    @PostMapping("/bundlebuilder/query")
    fun handleQueryRequest(
        model: Model,
        @RequestParam allRequestParam: Map<String, String>
    ): String {
        val endpoints =
            allRequestParam
                .filter { it.key.startsWith("endpoint-") }
                .filter { it.value.isNotBlank() }
                .values
        val validations = endpoints.associateWith { validateAnEndpoint(it) }
        return when (validations.values.contains(false)) {
            true, false -> {
                val defaultEndpoints = endpoints.mapIndexed { k, v -> BundleBuilderDefaults.DefaultServerEntry(k, v) }
                model["validationError"] = true
                renderStartPage(model, defaultEndpoints)
            }
            /*false -> {
                logger.info("Using endpoints: ${endpoints.joinToString(", ")}")
                renderSelect()
            }*/
        }
    }

    @PostMapping("/bundlebuilder/select")
    fun renderSelect(
    ): String {
        return "bundlebuilder_select_resources"
    }

    private fun validateAnEndpoint(endpointString: String): Boolean = try {
        URI.create(endpointString)
        true
    } catch (e: IllegalArgumentException) {
        false
    }


    @GetMapping("/bundlebuilder")
    fun renderStartPage(
        model: Model,
        defaultServers: List<BundleBuilderDefaults.DefaultServerEntry> = bundleBuilderDefaults.defaultServerMap
    ): String {
        model["defaultServers"] = defaultServers
        return "bundlebuilder_start"
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "bundlebuilder.defaults")
class BundleBuilderDefaults(
    defaultServers: List<String> = listOf()
) {
    val defaultServerMap = defaultServers.mapIndexed { index, s ->
        DefaultServerEntry(index + 1, s)
    }

    data class DefaultServerEntry(
        val id: Int,
        val url: String
    )
}